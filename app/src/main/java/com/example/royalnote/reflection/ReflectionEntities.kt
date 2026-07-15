package com.example.royalnote.reflection

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "daily_reflections")
data class DailyReflectionEntity(
    @PrimaryKey val threadDate: String,
    val periodStartDate: String,
    val periodEndDate: String,
    val reflectionJson: String,
    val recordSnapshotJson: String,
    val promptVersion: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "reflection_threads")
data class ReflectionThreadEntity(
    @PrimaryKey val threadDate: String,
    val conversationSummary: String = "",
    val summarizedThroughMessageId: Long = 0,
    val updatedAt: Long,
)

@Entity(
    tableName = "reflection_messages",
    indices = [Index(value = ["threadDate", "createdAt"])],
)
data class ReflectionMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadDate: String,
    val role: String,
    val content: String,
    val deliveryState: String,
    val isSafetyMessage: Boolean = false,
    val createdAt: Long,
)

@Entity(
    tableName = "memory_candidates",
    indices = [Index(value = ["threadDate", "sourceMessageId"])],
)
data class MemoryCandidateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadDate: String,
    val sourceMessageId: Long,
    val category: String,
    val content: String,
    val policy: String,
    val status: String,
    val memoryEntryId: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "reflection_experiments",
    primaryKeys = ["threadDate", "experimentId"],
    indices = [Index(value = ["memoryEntryId"])],
)
data class ReflectionExperimentStateEntity(
    val threadDate: String,
    val experimentId: String,
    val status: String,
    val title: String,
    val action: String,
    val frequency: String,
    val observation: String,
    val memoryEntryId: String? = null,
    val updatedAt: Long,
)

data class ReflectionHistoryRow(
    val threadDate: String,
    val periodStartDate: String,
    val periodEndDate: String,
    val messageCount: Int,
)
