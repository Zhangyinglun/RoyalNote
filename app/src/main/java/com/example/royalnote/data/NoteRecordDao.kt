package com.example.royalnote.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteRecordDao {
    @Query("SELECT * FROM note_records ORDER BY startedAt DESC, createdAt DESC, id DESC")
    fun observeRecords(): Flow<List<NoteRecord>>

    @Query(
        """
        SELECT * FROM note_records
        WHERE eventDate >= :startDateInclusive AND eventDate < :endDateExclusive
        ORDER BY eventDate ASC, startedAt ASC, createdAt ASC, id ASC
        """
    )
    suspend fun recordsInDateRange(
        startDateInclusive: String,
        endDateExclusive: String,
    ): List<NoteRecord>

    @Query("SELECT EXISTS(SELECT 1 FROM note_records WHERE importBatchId = :importBatchId)")
    suspend fun hasImportBatch(importBatchId: String): Boolean

    @Insert
    suspend fun insert(record: NoteRecord)

    @Insert
    suspend fun insertAll(records: List<NoteRecord>)

    @Update
    suspend fun update(record: NoteRecord): Int

    @Delete
    suspend fun delete(record: NoteRecord): Int
}
