package com.example.royalnote.data

import com.example.royalnote.ui.RecordOperations
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

class NoteRepository(private val dao: NoteRecordDao) : RecordOperations {
    override fun observeRecords(): Flow<List<NoteRecord>> = dao.observeRecords()

    override suspend fun addRecord(
        eventText: String,
        moodTag: String?,
        moodNote: String?,
        startedAt: Long,
        endedAt: Long,
        nowMillis: Long,
        zoneId: String,
    ) {
        val normalizedZoneId = validateZoneId(zoneId)
        requireValidRange(startedAt, endedAt)
        val normalizedText = normalizeEventText(eventText)
        val normalizedMoodTag = normalizeMoodTag(moodTag)
        dao.insert(
            NoteRecord(
                eventText = normalizedText,
                moodTag = normalizedMoodTag,
                moodNote = normalizeMoodNote(moodNote),
                startedAt = startedAt,
                endedAt = endedAt,
                eventDate = eventDateFor(startedAt, normalizedZoneId),
                zoneId = normalizedZoneId,
                source = RecordSources.MANUAL,
                importBatchId = null,
                importOrdinal = null,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            )
        )
    }

    override suspend fun updateRecord(record: NoteRecord) {
        val normalized = normalizeRecord(record)
        check(dao.update(normalized) == 1) { "record not found: ${record.id}" }
    }

    override suspend fun deleteRecord(record: NoteRecord) {
        check(dao.delete(record) == 1) { "record not found: ${record.id}" }
    }

    override suspend fun importRecords(records: List<NoteRecord>) {
        require(records.isNotEmpty()) { "records must not be empty" }
        val normalized = records.map { normalizeRecord(it) }
        require(normalized.all { it.source == RecordSources.IMPORT }) {
            "import records must use source=import"
        }
        require(normalized.map { it.importBatchId }.distinct().size == 1) {
            "import records must belong to one batch"
        }
        require(normalized.all { it.importBatchId != null && it.importOrdinal != null }) {
            "import records require a batch and ordinal"
        }
        require(normalized.map { it.importBatchId to it.importOrdinal }.distinct().size == normalized.size) {
            "duplicate import ordinal"
        }
        val batchId = normalized.first().importBatchId!!
        if (dao.hasImportBatch(batchId)) throw DuplicateImportException(batchId)
        dao.insertAll(normalized)
    }

    private fun normalizeRecord(record: NoteRecord): NoteRecord {
        requireValidRange(record.startedAt, record.endedAt)
        val zoneId = validateZoneId(record.zoneId)
        val normalizedSource = record.source.trim()
        require(normalizedSource in RecordSources.ALL) { "unsupported record source" }
        val normalizedBatchId = record.importBatchId?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedOrdinal = record.importOrdinal
        if (normalizedSource == RecordSources.MANUAL) {
            require(normalizedBatchId == null && normalizedOrdinal == null) {
                "manual records cannot have import metadata"
            }
        } else {
            require(normalizedBatchId != null && normalizedOrdinal != null && normalizedOrdinal >= 0) {
                "import records require a valid batch and ordinal"
            }
        }
        return record.copy(
            eventText = normalizeEventText(record.eventText),
            moodTag = normalizeMoodTag(record.moodTag),
            moodNote = normalizeMoodNote(record.moodNote),
            eventDate = eventDateFor(record.startedAt, zoneId),
            zoneId = zoneId,
            source = normalizedSource,
            importBatchId = normalizedBatchId,
            importOrdinal = normalizedOrdinal,
        )
    }

    private fun validateZoneId(zoneId: String): String {
        val trimmed = zoneId.trim()
        require(trimmed.isNotEmpty()) { "zoneId must not be blank" }
        return ZoneId.of(trimmed).id
    }

    private fun normalizeEventText(eventText: String): String {
        val normalized = eventText.trim()
        require(normalized.isNotEmpty()) { "eventText must not be blank" }
        return normalized
    }

    private fun normalizeMoodTag(moodTag: String?): String? = moodTag
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.also { require(it in MoodLabels.ALL) { "unsupported mood tag" } }

    private fun normalizeMoodNote(moodNote: String?): String? = moodNote
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private fun requireValidRange(startedAt: Long, endedAt: Long) {
        require(endedAt >= startedAt) { "endedAt must be greater than or equal to startedAt" }
    }

    companion object {
        fun eventDateFor(startedAt: Long, zoneId: String): String {
            requireValidZone(zoneId)
            return Instant.ofEpochMilli(startedAt)
                .atZone(ZoneId.of(zoneId))
                .toLocalDate()
                .toString()
        }

        private fun requireValidZone(zoneId: String) {
            require(zoneId.isNotBlank()) { "zoneId must not be blank" }
            ZoneId.of(zoneId)
        }
    }
}

class DuplicateImportException(batchId: String) : IllegalStateException(
    "import batch already exists: $batchId"
)
