package com.example.royalnote.data

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.royalnote.reflection.DailyReflectionEntity
import com.example.royalnote.reflection.MemoryCandidateEntity
import com.example.royalnote.reflection.ReflectionDao
import com.example.royalnote.reflection.ReflectionExperimentStateEntity
import com.example.royalnote.reflection.ReflectionMessageEntity
import com.example.royalnote.reflection.ReflectionThreadEntity
import java.time.ZoneId

@Database(
    entities = [
        NoteRecord::class,
        DailyReflectionEntity::class,
        ReflectionThreadEntity::class,
        ReflectionMessageEntity::class,
        MemoryCandidateEntity::class,
        ReflectionExperimentStateEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class RoyalNoteDatabase : RoomDatabase() {
    abstract fun noteRecordDao(): NoteRecordDao
    abstract fun reflectionDao(): ReflectionDao

    companion object {
        @Volatile
        private var instance: RoyalNoteDatabase? = null

        fun getInstance(context: Context): RoyalNoteDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RoyalNoteDatabase::class.java,
                    "royal_note.db",
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    migration3To4(ZoneId.systemDefault()),
                )
                    .build()
                    .also { instance = it }
            }
        }

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `note_records_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `eventText` TEXT NOT NULL,
                        `moodTag` TEXT,
                        `moodNote` TEXT,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `note_records_new` (
                        `id`, `eventText`, `moodTag`, `moodNote`,
                        `startedAt`, `endedAt`, `createdAt`, `updatedAt`
                    )
                    SELECT
                        `id`, `eventText`, `moodTag`, `moodNote`,
                        `createdAt`, `createdAt`, `createdAt`, `updatedAt`
                    FROM `note_records`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `note_records`")
                db.execSQL("ALTER TABLE `note_records_new` RENAME TO `note_records`")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_reflections` (
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
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reflection_threads` (
                        `threadDate` TEXT NOT NULL,
                        `conversationSummary` TEXT NOT NULL,
                        `summarizedThroughMessageId` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`threadDate`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reflection_messages` (
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
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reflection_messages_threadDate_createdAt` ON `reflection_messages` (`threadDate`, `createdAt`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memory_candidates` (
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
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_memory_candidates_threadDate_sourceMessageId` ON `memory_candidates` (`threadDate`, `sourceMessageId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reflection_experiments` (
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
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reflection_experiments_memoryEntryId` ON `reflection_experiments` (`memoryEntryId`)"
                )
            }
        }

        internal fun migration3To4(zoneId: ZoneId): Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val oldCount = db.query("SELECT COUNT(*) FROM note_records").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getLong(0)
                }
                db.execSQL(
                    """
                    CREATE TABLE `note_records_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `eventText` TEXT NOT NULL,
                        `moodTag` TEXT,
                        `moodNote` TEXT,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER NOT NULL,
                        `eventDate` TEXT NOT NULL,
                        `zoneId` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `importBatchId` TEXT,
                        `importOrdinal` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.query(
                    """
                    SELECT id, eventText, moodTag, moodNote,
                        startedAt, endedAt, createdAt, updatedAt
                    FROM note_records
                    ORDER BY id ASC
                    """.trimIndent()
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val startedAt = cursor.getLong(4)
                        val values = ContentValues().apply {
                            put("id", cursor.getLong(0))
                            put("eventText", cursor.getString(1))
                            if (cursor.isNull(2)) putNull("moodTag") else put("moodTag", cursor.getString(2))
                            if (cursor.isNull(3)) putNull("moodNote") else put("moodNote", cursor.getString(3))
                            put("startedAt", startedAt)
                            put("endedAt", cursor.getLong(5))
                            put("eventDate", NoteRepository.eventDateFor(startedAt, zoneId.id))
                            put("zoneId", zoneId.id)
                            put("source", RecordSources.MANUAL)
                            putNull("importBatchId")
                            putNull("importOrdinal")
                            put("createdAt", cursor.getLong(6))
                            put("updatedAt", cursor.getLong(7))
                        }
                        check(
                            db.insert("note_records_new", SQLiteDatabase.CONFLICT_ABORT, values) != -1L
                        ) { "failed to copy note record ${cursor.getLong(0)}" }
                    }
                }

                val newCount = db.query("SELECT COUNT(*) FROM note_records_new").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getLong(0)
                }
                check(oldCount == newCount) { "note record count changed during migration" }
                db.execSQL("DROP TABLE `note_records`")
                db.execSQL("ALTER TABLE `note_records_new` RENAME TO `note_records`")
                db.execSQL(
                    "CREATE INDEX `index_note_records_eventDate_startedAt_createdAt_id` " +
                        "ON `note_records` (`eventDate`, `startedAt`, `createdAt`, `id`)"
                )
                db.execSQL(
                    "CREATE INDEX `index_note_records_startedAt_createdAt_id` " +
                        "ON `note_records` (`startedAt`, `createdAt`, `id`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX `index_note_records_importBatchId_importOrdinal` " +
                        "ON `note_records` (`importBatchId`, `importOrdinal`)"
                )
            }
        }
    }
}
