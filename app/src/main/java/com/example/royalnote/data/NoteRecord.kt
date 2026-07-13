package com.example.royalnote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "note_records")
data class NoteRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventText: String,
    val moodTag: String?,
    val moodNote: String?,
    val startedAt: Long,
    val endedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

object MoodLabels {
    val ALL = listOf("开心", "满足", "平静", "疲惫", "烦躁", "低落", "焦虑")
}
