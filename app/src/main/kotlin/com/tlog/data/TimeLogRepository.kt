package com.tlog.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class TimeLogRepository(
    private val dao: TimeEntryDao,
    private val jobDao: SavedJobDao
) {
    fun observeOpen(): Flow<TimeEntry?> = dao.observeOpen()
    fun observeRange(startMillis: Long, endMillis: Long): Flow<List<TimeEntry>> =
        dao.observeRange(startMillis, endMillis)

    suspend fun getRange(startMillis: Long, endMillis: Long): List<TimeEntry> =
        dao.getRange(startMillis, endMillis)

    suspend fun getAll(): List<TimeEntry> = dao.getAll()
    suspend fun clearAll() = dao.clearAll()

    suspend fun getById(id: Long): TimeEntry? = dao.getById(id)

    /**
     * Throws IllegalStateException if a clock in/out was performed within the last 2 minutes.
     */
    suspend fun guardRapidToggle(now: Long = System.currentTimeMillis()) {
        val latestIn = dao.latestClockIn() ?: Long.MIN_VALUE
        val latestOut = dao.latestClockOut() ?: Long.MIN_VALUE
        val latest = maxOf(latestIn, latestOut)
        val deltaMs = now - latest
        if (deltaMs in 0 until 120_000L) {
            val seconds = (120_000L - deltaMs) / 1000
            error("Too fast — wait ${seconds}s before toggling again")
        }
    }

    suspend fun clockIn(now: Long = System.currentTimeMillis(), isLunch: Boolean = false): Long {
        guardRapidToggle(now)
        return dao.insert(
            TimeEntry(
                clockInEpochMillis = now,
                clockOutEpochMillis = null,
                isLunch = isLunch
            )
        )
    }

    suspend fun clockOut(entry: TimeEntry, now: Long = System.currentTimeMillis()) {
        guardRapidToggle(now)
        dao.update(entry.copy(clockOutEpochMillis = now))
    }

    suspend fun hasLunchToday(now: Long = System.currentTimeMillis()): Boolean {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.lunchCountInRange(start, end) > 0
    }

    suspend fun save(entry: TimeEntry) {
        if (entry.id == 0L) dao.insert(entry) else dao.update(entry)
    }

    suspend fun delete(entry: TimeEntry) = dao.delete(entry)

    // Saved jobs
    fun observeJobs(): Flow<List<SavedJob>> = jobDao.observeAll()
    suspend fun getAllJobs(): List<SavedJob> = jobDao.getAll()
    suspend fun saveJob(job: SavedJob): Long {
        if (job.isDefault) jobDao.clearDefaults()
        return if (job.id == 0L) jobDao.insert(job) else { jobDao.update(job); job.id }
    }
    suspend fun deleteJob(job: SavedJob) = jobDao.delete(job)
    suspend fun clearJobs() = jobDao.clearAll()
}
