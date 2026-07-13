package com.example.royalnote.data

import com.example.royalnote.ui.RecordOperations
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteRecordDao) : RecordOperations {
    override fun observeRecords(): Flow<List<NoteRecord>> = dao.observeRecords()

    override suspend fun addRecord(
        eventText: String,
        moodTag: String?,
        moodNote: String?,
        startedAt: Long,
        endedAt: Long,
        nowMillis: Long,
    ) {
        requireValidRange(startedAt, endedAt)
        dao.insert(
            NoteRecord(
                eventText = eventText,
                moodTag = moodTag,
                moodNote = moodNote,
                startedAt = startedAt,
                endedAt = endedAt,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            )
        )
    }

    override suspend fun updateRecord(record: NoteRecord) {
        requireValidRange(record.startedAt, record.endedAt)
        dao.update(record)
    }

    override suspend fun deleteRecord(record: NoteRecord) {
        dao.delete(record)
    }

    override suspend fun importRecords(records: List<NoteRecord>) {
        records.forEach { requireValidRange(it.startedAt, it.endedAt) }
        dao.insertAll(records)
    }

    private fun requireValidRange(startedAt: Long, endedAt: Long) {
        require(endedAt >= startedAt) { "endedAt must be greater than or equal to startedAt" }
    }
}
