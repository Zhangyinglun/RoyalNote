package com.example.royalnote.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class NoteRepositoryTest {
    @Test
    fun addRecordStoresActivityRangeAndMetadataTime() = runTest {
        val dao = CapturingNoteRecordDao()
        val repository = NoteRepository(dao)

        repository.addRecord(
            eventText = "整理书桌",
            moodTag = "平静",
            moodNote = null,
            startedAt = 1_000L,
            endedAt = 4_000L,
            nowMillis = 9_000L,
            zoneId = "UTC",
        )

        assertEquals(1_000L, dao.inserted?.startedAt)
        assertEquals(4_000L, dao.inserted?.endedAt)
        assertEquals(9_000L, dao.inserted?.createdAt)
        assertEquals(9_000L, dao.inserted?.updatedAt)
    }

    @Test
    fun addRecordNormalizesFieldsAndComputesStableDate() = runTest {
        val dao = CapturingNoteRecordDao()
        val repository = NoteRepository(dao)

        repository.addRecord(
            eventText = "  夜读  ",
            moodTag = " 平静 ",
            moodNote = "  心安  ",
            startedAt = Instant.parse("2026-07-14T06:30:00Z").toEpochMilli(),
            endedAt = Instant.parse("2026-07-14T07:00:00Z").toEpochMilli(),
            nowMillis = 9_000L,
            zoneId = "America/Los_Angeles",
        )

        assertEquals("夜读", dao.inserted?.eventText)
        assertEquals("平静", dao.inserted?.moodTag)
        assertEquals("心安", dao.inserted?.moodNote)
        assertEquals("2026-07-13", dao.inserted?.eventDate)
        assertEquals("America/Los_Angeles", dao.inserted?.zoneId)
        assertEquals(RecordSources.MANUAL, dao.inserted?.source)
        assertNull(dao.inserted?.importBatchId)
    }

    @Test
    fun addRecordRejectsBlankTextAndUnknownMood() = runTest {
        val dao = CapturingNoteRecordDao()
        val repository = NoteRepository(dao)

        assertThrowsIllegalArgument {
            repository.addRecord(
                eventText = "   ",
                moodTag = null,
                moodNote = null,
                startedAt = 1_000L,
                endedAt = 1_000L,
                nowMillis = 9_000L,
                zoneId = "UTC",
            )
        }
        assertThrowsIllegalArgument {
            repository.addRecord(
                eventText = "事件",
                moodTag = "未知",
                moodNote = null,
                startedAt = 1_000L,
                endedAt = 1_000L,
                nowMillis = 9_000L,
                zoneId = "UTC",
            )
        }

        assertNull(dao.inserted)
    }

    @Test
    fun addRecordRejectsEndBeforeStart() = runTest {
        val dao = CapturingNoteRecordDao()
        val repository = NoteRepository(dao)

        try {
            repository.addRecord(
                eventText = "整理书桌",
                moodTag = "平静",
                moodNote = null,
                startedAt = 4_000L,
            endedAt = 1_000L,
            nowMillis = 9_000L,
            zoneId = "UTC",
            )
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected: an invalid range must not reach the DAO.
        }

        assertNull(dao.inserted)
    }

    @Test
    fun addRecordAcceptsEqualStartAndEnd() = runTest {
        val dao = CapturingNoteRecordDao()
        val repository = NoteRepository(dao)

        repository.addRecord(
            eventText = "整理书桌",
            moodTag = "平静",
            moodNote = null,
            startedAt = 4_000L,
            endedAt = 4_000L,
            nowMillis = 9_000L,
            zoneId = "UTC",
        )

        assertEquals(4_000L, dao.inserted?.startedAt)
        assertEquals(4_000L, dao.inserted?.endedAt)
    }

    @Test
    fun updateRecordRejectsEndBeforeStartWithoutCallingDao() = runTest {
        val dao = CapturingNoteRecordDao()
        val repository = NoteRepository(dao)
        val record = NoteRecord(
            eventText = "整理书桌",
            moodTag = "平静",
            moodNote = null,
            startedAt = 4_000L,
            endedAt = 1_000L,
            eventDate = "1970-01-01",
            zoneId = "UTC",
            source = RecordSources.IMPORT,
            importBatchId = "batch",
            importOrdinal = 0,
            createdAt = 9_000L,
            updatedAt = 9_000L,
        )

        try {
            repository.updateRecord(record)
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected: an invalid range must not reach the DAO.
        }

        assertNull(dao.updated)
    }

    @Test
    fun importRecordsRejectsEndBeforeStartWithoutCallingDao() = runTest {
        val dao = CapturingNoteRecordDao()
        val repository = NoteRepository(dao)
        val records = listOf(
            NoteRecord(
                eventText = "整理书桌",
                moodTag = "平静",
                moodNote = null,
            startedAt = 4_000L,
            endedAt = 1_000L,
            eventDate = "1970-01-01",
            zoneId = "UTC",
            source = RecordSources.IMPORT,
            importBatchId = "batch",
            importOrdinal = 0,
            createdAt = 9_000L,
                updatedAt = 9_000L,
            )
        )

        try {
            repository.importRecords(records)
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected: an invalid range must not reach the DAO.
        }

        assertNull(dao.insertedAll)
    }

    @Test
    fun importRecordsRejectsAnExistingBatchBeforeInsert() = runTest {
        val dao = CapturingNoteRecordDao().apply { existingImportBatches += "batch" }
        val repository = NoteRepository(dao)
        val record = NoteRecord(
            eventText = "事件",
            moodTag = null,
            moodNote = null,
            startedAt = 1_000L,
            endedAt = 1_000L,
            eventDate = "1970-01-01",
            zoneId = "UTC",
            source = RecordSources.IMPORT,
            importBatchId = "batch",
            importOrdinal = 0,
            createdAt = 9_000L,
            updatedAt = 9_000L,
        )

        try {
            repository.importRecords(listOf(record))
            fail("Expected DuplicateImportException")
        } catch (_: DuplicateImportException) {
            // Expected: duplicate source text must not be inserted again.
        }

        assertNull(dao.insertedAll)
    }

    @Test
    fun updateRecomputesDateAndNormalizesFields() = runTest {
        val dao = CapturingNoteRecordDao()
        val repository = NoteRepository(dao)
        val record = NoteRecord(
            id = 7L,
            eventText = "  事件  ",
            moodTag = "平静",
            moodNote = "  ",
            startedAt = Instant.parse("2026-07-14T01:00:00Z").toEpochMilli(),
            endedAt = Instant.parse("2026-07-14T01:30:00Z").toEpochMilli(),
            eventDate = "1900-01-01",
            zoneId = "UTC",
            source = RecordSources.MANUAL,
            createdAt = 8_000L,
            updatedAt = 9_000L,
        )

        repository.updateRecord(record)

        assertEquals("事件", dao.updated?.eventText)
        assertNull(dao.updated?.moodNote)
        assertEquals("2026-07-14", dao.updated?.eventDate)
        assertEquals(7L, dao.updated?.id)
    }

    private fun assertThrowsIllegalArgument(block: suspend () -> Unit) = runTest {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected: invalid records must not reach the DAO.
        }
    }
}

private class CapturingNoteRecordDao : NoteRecordDao {
    var inserted: NoteRecord? = null
    var insertedAll: List<NoteRecord>? = null
    var updated: NoteRecord? = null
    val existingImportBatches = mutableSetOf<String>()

    override fun observeRecords(): Flow<List<NoteRecord>> = flowOf(emptyList())

    override suspend fun recordsInDateRange(
        startDateInclusive: String,
        endDateExclusive: String,
    ): List<NoteRecord> = emptyList()

    override suspend fun hasImportBatch(importBatchId: String): Boolean =
        importBatchId in existingImportBatches

    override suspend fun insert(record: NoteRecord) {
        inserted = record
    }

    override suspend fun insertAll(records: List<NoteRecord>) {
        insertedAll = records
    }

    override suspend fun update(record: NoteRecord): Int {
        updated = record
        return 1
    }

    override suspend fun delete(record: NoteRecord): Int = 1
}
