package com.example.royalnote.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RoyalNoteDatabaseMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrateVersion1To4PreservesRecordsAndInitializesRecordTimes() {
        createLegacyDatabase(version = 1, includeRecordTimes = false)

        val database = openMigratedDatabase()
        try {
            database.openHelper.writableDatabase.query(
                "SELECT eventText, moodTag, moodNote, startedAt, endedAt, eventDate, zoneId, " +
                    "source, importBatchId, importOrdinal, createdAt, updatedAt " +
                    "FROM note_records WHERE id = 7"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("旧日记录", cursor.getString(0))
                assertEquals("平静", cursor.getString(1))
                assertEquals("仍值得保留", cursor.getString(2))
                assertEquals(CREATED_AT, cursor.getLong(3))
                assertEquals(CREATED_AT, cursor.getLong(4))
                assertEquals(NoteRepository.eventDateFor(CREATED_AT, TEST_ZONE.id), cursor.getString(5))
                assertEquals(TEST_ZONE.id, cursor.getString(6))
                assertEquals(RecordSources.MANUAL, cursor.getString(7))
                assertTrue(cursor.isNull(8))
                assertTrue(cursor.isNull(9))
                assertEquals(CREATED_AT, cursor.getLong(10))
                assertEquals(UPDATED_AT, cursor.getLong(11))
            }
            assertReflectionTablesExist(database)
            assertRecordIndexesExist(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun migrateVersion2To4PreservesExistingRecordTimes() {
        createLegacyDatabase(version = 2, includeRecordTimes = true)

        val database = openMigratedDatabase()
        try {
            database.openHelper.writableDatabase.query(
                "SELECT startedAt, endedAt, eventDate, zoneId FROM note_records WHERE id = 7"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(STARTED_AT, cursor.getLong(0))
                assertEquals(ENDED_AT, cursor.getLong(1))
                assertEquals(NoteRepository.eventDateFor(STARTED_AT, TEST_ZONE.id), cursor.getString(2))
                assertEquals(TEST_ZONE.id, cursor.getString(3))
            }
            assertReflectionTablesExist(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun migrateVersion3To4PreservesExistingRecordTimesAndReflectionTables() {
        createLegacyDatabase(version = 3, includeRecordTimes = true)

        val database = openMigratedDatabase()
        try {
            database.openHelper.writableDatabase.query(
                "SELECT startedAt, endedAt, eventDate, zoneId FROM note_records WHERE id = 7"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(STARTED_AT, cursor.getLong(0))
                assertEquals(ENDED_AT, cursor.getLong(1))
                assertEquals(NoteRepository.eventDateFor(STARTED_AT, TEST_ZONE.id), cursor.getString(2))
                assertEquals(TEST_ZONE.id, cursor.getString(3))
            }
            assertReflectionTablesExist(database)
            assertRecordIndexesExist(database)
        } finally {
            database.close()
        }
    }

    private fun createLegacyDatabase(version: Int, includeRecordTimes: Boolean) {
        val path = context.getDatabasePath(TEST_DATABASE)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            val timeColumns = if (includeRecordTimes) {
                "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL,"
            } else {
                ""
            }
            database.execSQL(
                """
                CREATE TABLE `note_records` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `eventText` TEXT NOT NULL,
                    `moodTag` TEXT,
                    `moodNote` TEXT,
                    $timeColumns
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            val values = ContentValues().apply {
                put("id", 7L)
                put("eventText", "旧日记录")
                put("moodTag", "平静")
                put("moodNote", "仍值得保留")
                if (includeRecordTimes) {
                    put("startedAt", STARTED_AT)
                    put("endedAt", ENDED_AT)
                }
                put("createdAt", CREATED_AT)
                put("updatedAt", UPDATED_AT)
            }
            database.insertOrThrow("note_records", null, values)
            if (version >= 3) createVersion3ReflectionTables(database)
            database.version = version
        }
    }

    private fun createVersion3ReflectionTables(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE `daily_reflections` (
                `threadDate` TEXT NOT NULL,
                `periodStartDate` TEXT NOT NULL,
                `periodEndDate` TEXT NOT NULL,
                `reflectionJson` TEXT NOT NULL,
                `recordSnapshotJson` TEXT NOT NULL,
                `promptVersion` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`threadDate`)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE `reflection_threads` (
                `threadDate` TEXT NOT NULL,
                `conversationSummary` TEXT NOT NULL,
                `summarizedThroughMessageId` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`threadDate`)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE `reflection_messages` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `threadDate` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `deliveryState` TEXT NOT NULL,
                `isSafetyMessage` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX `index_reflection_messages_threadDate_createdAt` " +
                "ON `reflection_messages` (`threadDate`, `createdAt`)"
        )
        database.execSQL(
            """
            CREATE TABLE `memory_candidates` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `threadDate` TEXT NOT NULL,
                `sourceMessageId` INTEGER NOT NULL,
                `category` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `policy` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `memoryEntryId` TEXT,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX `index_memory_candidates_threadDate_sourceMessageId` " +
                "ON `memory_candidates` (`threadDate`, `sourceMessageId`)"
        )
        database.execSQL(
            """
            CREATE TABLE `reflection_experiments` (
                `threadDate` TEXT NOT NULL,
                `experimentId` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `action` TEXT NOT NULL,
                `frequency` TEXT NOT NULL,
                `observation` TEXT NOT NULL,
                `memoryEntryId` TEXT,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`threadDate`, `experimentId`)
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX `index_reflection_experiments_memoryEntryId` " +
                "ON `reflection_experiments` (`memoryEntryId`)"
        )
    }

    private fun openMigratedDatabase(): RoyalNoteDatabase = Room.databaseBuilder(
        context,
        RoyalNoteDatabase::class.java,
        TEST_DATABASE,
    ).addMigrations(
        RoyalNoteDatabase.MIGRATION_1_2,
        RoyalNoteDatabase.MIGRATION_2_3,
        RoyalNoteDatabase.migration3To4(TEST_ZONE),
    ).allowMainThreadQueries()
        .build()

    private fun assertReflectionTablesExist(database: RoyalNoteDatabase) {
        database.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'daily_reflections'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
    }

    private fun assertRecordIndexesExist(database: RoyalNoteDatabase) {
        database.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_note_records_%'"
        ).use { cursor ->
            var count = 0
            while (cursor.moveToNext()) count++
            assertEquals(3, count)
        }
    }

    private companion object {
        const val TEST_DATABASE = "royal-note-migration-test.db"
        const val STARTED_AT = 1_700_000_000_000L
        const val ENDED_AT = 1_700_000_900_000L
        const val CREATED_AT = 1_600_000_000_000L
        const val UPDATED_AT = 1_600_000_100_000L
        val TEST_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
