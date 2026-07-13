package com.example.royalnote.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

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
        )

        assertEquals(1_000L, dao.inserted?.startedAt)
        assertEquals(4_000L, dao.inserted?.endedAt)
        assertEquals(9_000L, dao.inserted?.createdAt)
        assertEquals(9_000L, dao.inserted?.updatedAt)
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
}

private class CapturingNoteRecordDao : NoteRecordDao {
    var inserted: NoteRecord? = null
    var insertedAll: List<NoteRecord>? = null
    var updated: NoteRecord? = null

    override fun observeRecords(): Flow<List<NoteRecord>> = flowOf(emptyList())

    override suspend fun insert(record: NoteRecord) {
        inserted = record
    }

    override suspend fun insertAll(records: List<NoteRecord>) {
        insertedAll = records
    }

    override suspend fun update(record: NoteRecord) {
        updated = record
    }

    override suspend fun delete(record: NoteRecord) = Unit
}
