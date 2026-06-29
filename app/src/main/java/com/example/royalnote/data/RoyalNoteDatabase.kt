package com.example.royalnote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NoteRecord::class], version = 1, exportSchema = false)
abstract class RoyalNoteDatabase : RoomDatabase() {
    abstract fun noteRecordDao(): NoteRecordDao

    companion object {
        @Volatile
        private var instance: RoyalNoteDatabase? = null

        fun getInstance(context: Context): RoyalNoteDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RoyalNoteDatabase::class.java,
                    "royal_note.db",
                ).build().also { instance = it }
            }
        }
    }
}
