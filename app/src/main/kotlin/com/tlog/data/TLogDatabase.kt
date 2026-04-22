package com.tlog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TimeEntry::class, SavedJob::class],
    version = 2,
    exportSchema = true
)
abstract class TLogDatabase : RoomDatabase() {
    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun savedJobDao(): SavedJobDao

    companion object {
        fun create(context: Context): TLogDatabase =
            Room.databaseBuilder(context.applicationContext, TLogDatabase::class.java, "tlog.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
