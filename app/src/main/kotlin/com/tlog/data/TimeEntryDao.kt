package com.tlog.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM time_entry WHERE clockOutEpochMillis IS NULL ORDER BY clockInEpochMillis DESC LIMIT 1")
    fun observeOpen(): Flow<TimeEntry?>

    @Query("SELECT * FROM time_entry WHERE clockInEpochMillis >= :startMillis AND clockInEpochMillis < :endMillis ORDER BY clockInEpochMillis ASC")
    fun observeRange(startMillis: Long, endMillis: Long): Flow<List<TimeEntry>>

    @Query("SELECT * FROM time_entry WHERE clockInEpochMillis >= :startMillis AND clockInEpochMillis < :endMillis ORDER BY clockInEpochMillis ASC")
    suspend fun getRange(startMillis: Long, endMillis: Long): List<TimeEntry>

    @Query("SELECT * FROM time_entry ORDER BY clockInEpochMillis ASC")
    suspend fun getAll(): List<TimeEntry>

    @Query("SELECT * FROM time_entry WHERE id = :id")
    suspend fun getById(id: Long): TimeEntry?

    @Query("SELECT MAX(clockOutEpochMillis) FROM time_entry")
    suspend fun latestClockOut(): Long?

    @Query("SELECT MAX(clockInEpochMillis) FROM time_entry")
    suspend fun latestClockIn(): Long?

    @Query("SELECT COUNT(*) FROM time_entry WHERE isLunch = 1 AND clockInEpochMillis >= :startMillis AND clockInEpochMillis < :endMillis")
    suspend fun lunchCountInRange(startMillis: Long, endMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: TimeEntry): Long

    @Update
    suspend fun update(entry: TimeEntry)

    @Delete
    suspend fun delete(entry: TimeEntry)

    @Query("DELETE FROM time_entry")
    suspend fun clearAll()
}

@Dao
interface SavedJobDao {
    @Query("SELECT * FROM saved_job ORDER BY isDefault DESC, jobSite ASC")
    fun observeAll(): Flow<List<SavedJob>>

    @Query("SELECT * FROM saved_job ORDER BY isDefault DESC, jobSite ASC")
    suspend fun getAll(): List<SavedJob>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: SavedJob): Long

    @Update
    suspend fun update(job: SavedJob)

    @Delete
    suspend fun delete(job: SavedJob)

    @Query("UPDATE saved_job SET isDefault = 0")
    suspend fun clearDefaults()

    @Query("DELETE FROM saved_job")
    suspend fun clearAll()
}
