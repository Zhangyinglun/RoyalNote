package com.example.royalnote.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteRecordDaoTest {
    private lateinit var database: RoyalNoteDatabase
    private lateinit var dao: NoteRecordDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RoyalNoteDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.noteRecordDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun dateRangeIsInclusiveExclusiveAndOrderedDeterministically() = runBlocking {
        dao.insertAll(
            listOf(
                record(
                    id = 1,
                    eventText = "较晚创建",
                    eventDate = "2026-07-10",
                    startedAt = 1_000L,
                    createdAt = 20L,
                ),
                record(
                    id = 2,
                    eventText = "较早创建",
                    eventDate = "2026-07-10",
                    startedAt = 1_000L,
                    createdAt = 10L,
                ),
                record(
                    id = 3,
                    eventText = "结束边界",
                    eventDate = "2026-07-11",
                    startedAt = 500L,
                    createdAt = 1L,
                ),
            )
        )

        val rows = dao.recordsInDateRange("2026-07-10", "2026-07-11")

        assertEquals(listOf(2L, 1L), rows.map { it.id })
    }

    @Test
    fun observeRecordsUsesReverseStableOrder() = runBlocking {
        dao.insertAll(
            listOf(
                record(id = 1, eventText = "一", eventDate = "2026-07-10", startedAt = 1_000L, createdAt = 10L),
                record(id = 2, eventText = "二", eventDate = "2026-07-10", startedAt = 1_000L, createdAt = 20L),
            )
        )

        val rows = dao.observeRecords().first()

        assertEquals(listOf(2L, 1L), rows.map { it.id })
    }

    @Test
    fun duplicateImportKeyAbortsTheWholeBatch() = runBlocking {
        dao.insert(importRecord(id = 1, ordinal = 0))
        assertTrue(dao.hasImportBatch("batch-1"))

        val duplicateAndNew = listOf(
            importRecord(id = 2, ordinal = 1),
            importRecord(id = 3, ordinal = 0),
        )
        assertThrowsConstraint { dao.insertAll(duplicateAndNew) }

        val rows = dao.recordsInDateRange("2026-07-10", "2026-07-11")
        assertEquals(listOf(1L), rows.map { it.id })
    }

    @Test
    fun emptyManualImportKeyDoesNotConflict() = runBlocking {
        dao.insert(record(id = 1, eventText = "一"))
        dao.insert(record(id = 2, eventText = "二"))

        assertFalse(dao.hasImportBatch("not-present"))
        assertEquals(2, dao.recordsInDateRange("2026-07-10", "2026-07-11").size)
    }

    @Test
    fun updateAndDeleteReturnAffectedRowCount() = runBlocking {
        val original = record(id = 1, eventText = "原文")
        dao.insert(original)

        val updated = original.copy(eventText = "修订")
        assertEquals(1, dao.update(updated))
        assertEquals(1, dao.delete(updated))
        assertEquals(0, dao.delete(updated))
    }

    @Test
    fun dateRangeQueryUsesTheDateOrderingIndex() {
        val details = database.openHelper.readableDatabase.query(
            """
            EXPLAIN QUERY PLAN
            SELECT * FROM note_records
            WHERE eventDate >= '2026-07-10' AND eventDate < '2026-07-11'
            ORDER BY eventDate ASC, startedAt ASC, createdAt ASC, id ASC
            """.trimIndent()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(3))
            }
        }

        assertTrue(
            details.any {
                it.contains("index_note_records_eventDate_startedAt_createdAt_id")
            }
        )
    }

    private fun record(
        id: Long,
        eventText: String,
        eventDate: String = "2026-07-10",
        startedAt: Long = 1_000L,
        createdAt: Long = 1L,
    ) = NoteRecord(
        id = id,
        eventText = eventText,
        moodTag = null,
        moodNote = null,
        startedAt = startedAt,
        endedAt = startedAt,
        eventDate = eventDate,
        zoneId = ZoneId.of("UTC").id,
        source = RecordSources.MANUAL,
        importBatchId = null,
        importOrdinal = null,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun importRecord(id: Long, ordinal: Int) = record(
        id = id,
        eventText = "导入$ordinal",
    ).copy(
        source = RecordSources.IMPORT,
        importBatchId = "batch-1",
        importOrdinal = ordinal,
    )

    private suspend fun assertThrowsConstraint(block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("Expected SQLite constraint failure")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // Expected: the unique import key rejects the entire batch.
        }
    }
}
