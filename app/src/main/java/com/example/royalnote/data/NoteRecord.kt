package com.example.royalnote.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId

@Entity(
    tableName = "note_records",
    indices = [
        Index(
            name = "index_note_records_eventDate_startedAt_createdAt_id",
            value = ["eventDate", "startedAt", "createdAt", "id"],
        ),
        Index(
            name = "index_note_records_startedAt_createdAt_id",
            value = ["startedAt", "createdAt", "id"],
        ),
        Index(
            name = "index_note_records_importBatchId_importOrdinal",
            value = ["importBatchId", "importOrdinal"],
            unique = true,
        ),
    ],
)
data class NoteRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventText: String,
    val moodTag: String?,
    val moodNote: String?,
    val startedAt: Long,
    val endedAt: Long,
    val eventDate: String = defaultEventDate(startedAt),
    val zoneId: String = ZoneId.systemDefault().id,
    val source: String = RecordSources.MANUAL,
    val importBatchId: String? = null,
    val importOrdinal: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

object RecordSources {
    const val MANUAL = "manual"
    const val IMPORT = "import"
    val ALL = setOf(MANUAL, IMPORT)
}

private fun defaultEventDate(startedAt: Long): String = Instant.ofEpochMilli(startedAt)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .toString()

object MoodLabels {
    val ALL = listOf("开心", "满足", "平静", "疲惫", "烦躁", "低落", "焦虑")
}
