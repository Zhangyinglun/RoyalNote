package com.example.royalnote.data

import com.example.royalnote.ui.RecordOperations
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteRecordDao) : RecordOperations {
    override fun observeRecords(): Flow<List<NoteRecord>> = dao.observeRecords()

    override suspend fun addRecord(
        eventText: String,
        moodTag: String?,
        moodNote: String?,
        nowMillis: Long,
    ) {
        dao.insert(
            NoteRecord(
                eventText = eventText,
                moodTag = moodTag,
                moodNote = moodNote,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            )
        )
    }

    override suspend fun updateRecord(record: NoteRecord) {
        dao.update(record)
    }

    override suspend fun deleteRecord(record: NoteRecord) {
        dao.delete(record)
    }

    override suspend fun importRecords(records: List<NoteRecord>) {
        dao.insertAll(records)
    }
}
