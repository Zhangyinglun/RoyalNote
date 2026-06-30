package com.example.royalnote.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteRecordDao {
    @Query("SELECT * FROM note_records ORDER BY createdAt DESC")
    fun observeRecords(): Flow<List<NoteRecord>>

    @Insert
    suspend fun insert(record: NoteRecord)

    @Insert
    suspend fun insertAll(records: List<NoteRecord>)

    @Update
    suspend fun update(record: NoteRecord)

    @Delete
    suspend fun delete(record: NoteRecord)
}
