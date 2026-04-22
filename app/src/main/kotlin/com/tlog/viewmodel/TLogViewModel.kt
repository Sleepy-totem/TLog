package com.tlog.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tlog.TLogApp
import com.tlog.data.AppSettings
import com.tlog.data.SavedJob
import com.tlog.data.SettingsStore
import com.tlog.data.TimeEntry
import com.tlog.data.TimeLogRepository
import com.tlog.export.DayRow
import com.tlog.export.ExportData
import com.tlog.export.XlsxExporter
import com.tlog.util.WeekHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class WeekViewState(
    val weekStart: LocalDate,
    val entries: List<TimeEntry>,
    val settings: AppSettings
) {
    /** Work entries only (excludes lunch). */
    val workEntries: List<TimeEntry> = entries.filterNot { it.isLunch }

    val byDay: Map<DayOfWeek, List<TimeEntry>> = workEntries.groupBy { entry ->
        Instant.ofEpochMilli(entry.clockInEpochMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate().dayOfWeek
    }

    val totalHours: Double = workEntries.sumOf { it.durationHours }
}

class TLogViewModel(
    app: Application,
    private val repo: TimeLogRepository,
    val settings: SettingsStore
) : AndroidViewModel(app) {

    private val focusedWeek = MutableStateFlow(LocalDate.now())

    val settingsFlow: Flow<AppSettings> = settings.flow

    val openEntry: StateFlow<TimeEntry?> = repo.observeOpen()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val savedJobs: StateFlow<List<SavedJob>> = repo.observeJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val week: StateFlow<WeekViewState?> =
        combine(focusedWeek, settingsFlow) { d, s -> d to s }
            .flatMapLatest { (d, s) ->
                val start = WeekHelper.weekStartDate(d, s.weekStart)
                val (startMs, endMs) = WeekHelper.weekBounds(d, s.weekStart)
                repo.observeRange(startMs, endMs).map { entries ->
                    WeekViewState(start, entries, s)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shiftWeek(deltaWeeks: Long) {
        focusedWeek.value = focusedWeek.value.plusWeeks(deltaWeeks)
    }

    fun focusToday() { focusedWeek.value = LocalDate.now() }
    fun focusWeekOf(date: LocalDate) { focusedWeek.value = date }

    fun toggleClock(onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching {
            val open = repo.observeOpen().first()
            if (open == null) repo.clockIn() else repo.clockOut(open)
        }.onFailure { onError(it.message ?: "Error") }
    }

    /**
     * Start lunch: close the current work shift, open a lunch entry.
     * End lunch: close the lunch entry, start a new work shift.
     * Max one lunch per day.
     */
    fun toggleLunch(onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching {
            val open = repo.observeOpen().first()
            val now = System.currentTimeMillis()
            if (open == null) {
                error("Not clocked in")
            } else if (open.isLunch) {
                // End lunch → resume work
                repo.clockOut(open, now)
                repo.clockIn(now + 1, isLunch = false)
            } else {
                // Start lunch
                if (repo.hasLunchToday(now)) error("Already took lunch today")
                repo.clockOut(open, now)
                repo.clockIn(now + 1, isLunch = true)
            }
        }.onFailure { onError(it.message ?: "Error") }
    }

    fun saveEntry(entry: TimeEntry) = viewModelScope.launch { repo.save(entry) }
    fun deleteEntry(entry: TimeEntry) = viewModelScope.launch { repo.delete(entry) }

    fun togglePerDiem(date: LocalDate, on: Boolean) = viewModelScope.launch {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val entries = repo.getRange(start, end).filterNot { it.isLunch }
        entries.forEach { repo.save(it.copy(perDiem = on)) }
    }

    fun updateSettings(mutator: (AppSettings) -> AppSettings) =
        viewModelScope.launch { settings.update(mutator) }

    // Saved jobs
    fun saveJob(job: SavedJob) = viewModelScope.launch { repo.saveJob(job) }
    fun deleteJob(job: SavedJob) = viewModelScope.launch { repo.deleteJob(job) }

    suspend fun exportCurrentWeek(context: Context): Uri = exportWeek(context, focusedWeek.value)

    suspend fun exportWeek(context: Context, anyDayInWeek: LocalDate): Uri {
        val s = settings.get()
        val zone = ZoneId.systemDefault()
        val weekStart = WeekHelper.weekStartDate(anyDayInWeek, s.weekStart)
        val weekEnding = weekStart.plusDays(6)
        val (startMs, endMs) = WeekHelper.weekBounds(anyDayInWeek, s.weekStart)
        val allEntries = repo.getRange(startMs, endMs)
        val workEntries = allEntries.filterNot { it.isLunch }
        val byDay = workEntries.groupBy {
            Instant.ofEpochMilli(it.clockInEpochMillis).atZone(zone).toLocalDate().dayOfWeek
        }

        val days = (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val dow = date.dayOfWeek
            val dayEntries = byDay[dow].orEmpty()
            val earliestIn = dayEntries.minOfOrNull { it.clockInEpochMillis }
            val latestOut = dayEntries.mapNotNull { it.clockOutEpochMillis }.maxOrNull()
            val hours = dayEntries.sumOf { it.durationHours }
            val perDiem = dayEntries.any { it.perDiem }
            DayRow(
                dayOfWeek = dow,
                date = date,
                clockIn = earliestIn?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() },
                clockOut = latestOut?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() },
                hours = hours,
                jobSite = s.defaultJobSite,
                projectNumber = s.defaultProjectNumber,
                notes = dayEntries.mapNotNull { it.note.takeIf(String::isNotBlank) }.joinToString("; "),
                lunchHours = 0.0,
                clientApproval = "",
                perDiem = perDiem
            )
        }

        val data = ExportData(
            employeeName = s.employeeName,
            employeeSignature = s.employeeSignature,
            clientName = s.clientName,
            stateOfWork = s.stateOfWork,
            weekEndingDate = weekEnding,
            days = days,
            exportDate = LocalDate.now(),
            totalHours = workEntries.sumOf { it.durationHours }
        )
        return XlsxExporter.export(context, data)
    }

    /** Returns list of week-start dates that contain entries, most recent first. */
    suspend fun listWeeksWithData(): List<LocalDate> {
        val all = repo.getAll().filterNot { it.isLunch }
        val s = settings.get()
        val zone = ZoneId.systemDefault()
        return all.map {
            val d = Instant.ofEpochMilli(it.clockInEpochMillis).atZone(zone).toLocalDate()
            WeekHelper.weekStartDate(d, s.weekStart)
        }.distinct().sortedDescending()
    }

    fun buildShareIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun buildEmailIntent(uri: Uri, to: String, subject: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun buildViewIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(
            uri,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // ---------- DB import/export JSON ----------

    suspend fun exportDatabaseJson(): String {
        val entries = repo.getAll()
        val jobs = repo.getAllJobs()
        val obj = JSONObject()
        obj.put("version", 2)
        obj.put("entries", JSONArray().apply {
            entries.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("clockInEpochMillis", it.clockInEpochMillis)
                    put("clockOutEpochMillis", it.clockOutEpochMillis ?: JSONObject.NULL)
                    put("note", it.note)
                    put("jobSite", it.jobSite)
                    put("projectNumber", it.projectNumber)
                    put("isLunch", it.isLunch)
                    put("perDiem", it.perDiem)
                })
            }
        })
        obj.put("jobs", JSONArray().apply {
            jobs.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("jobSite", it.jobSite)
                    put("projectNumber", it.projectNumber)
                    put("isDefault", it.isDefault)
                })
            }
        })
        return obj.toString(2)
    }

    suspend fun importDatabaseJson(json: String) {
        val obj = JSONObject(json)
        repo.clearAll()
        repo.clearJobs()
        val entries = obj.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            repo.save(
                TimeEntry(
                    clockInEpochMillis = e.getLong("clockInEpochMillis"),
                    clockOutEpochMillis = if (e.isNull("clockOutEpochMillis")) null else e.getLong("clockOutEpochMillis"),
                    note = e.optString("note", ""),
                    jobSite = e.optString("jobSite", ""),
                    projectNumber = e.optString("projectNumber", ""),
                    isLunch = e.optBoolean("isLunch", false),
                    perDiem = e.optBoolean("perDiem", false)
                )
            )
        }
        val jobs = obj.optJSONArray("jobs") ?: JSONArray()
        for (i in 0 until jobs.length()) {
            val j = jobs.getJSONObject(i)
            repo.saveJob(
                SavedJob(
                    jobSite = j.getString("jobSite"),
                    projectNumber = j.optString("projectNumber", ""),
                    isDefault = j.optBoolean("isDefault", false)
                )
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as TLogApp
                TLogViewModel(app, app.repository, app.settings)
            }
        }
    }
}
