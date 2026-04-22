package com.tlog.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tlog.MainActivity
import com.tlog.TLogApp
import com.tlog.data.AppSettings
import com.tlog.export.DayRow
import com.tlog.export.ExportData
import com.tlog.export.XlsxExporter
import com.tlog.notify.NotificationChannels
import com.tlog.util.WeekHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AutoExportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as TLogApp
        val settings = app.settings.get()
        if (!settings.autoExportEnabled) return Result.success()

        return runCatching {
            val uri = exportCurrentWeek(app, settings)
            notifyDone(uri, settings)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private suspend fun exportCurrentWeek(app: TLogApp, s: AppSettings): Uri {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = WeekHelper.weekStartDate(today, s.weekStart)
        val (startMs, endMs) = WeekHelper.weekBounds(today, s.weekStart)
        val entries = app.repository.getRange(startMs, endMs).filterNot { it.isLunch }

        val byDay = entries.groupBy {
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
            weekEndingDate = weekStart.plusDays(6),
            days = days,
            exportDate = today,
            totalHours = entries.sumOf { it.durationHours }
        )
        return XlsxExporter.export(app, data)
    }

    private fun notifyDone(uri: Uri, s: AppSettings) {
        NotificationChannels.ensure(applicationContext)
        val tap = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPI = PendingIntent.getActivity(
            applicationContext, 0, tap,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val email = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(s.autoExportEmail))
            putExtra(Intent.EXTRA_SUBJECT, "TLog weekly timecard")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val emailChooser = Intent.createChooser(email, "Email timecard").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val emailPI = PendingIntent.getActivity(
            applicationContext, 1, emailChooser,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(applicationContext, NotificationChannels.EXPORT)
            .setContentTitle("Weekly timecard ready")
            .setContentText("Saved to Documents/TLog — tap to email to ${s.autoExportEmail}")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(emailPI)
            .addAction(0, "Open app", tapPI)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(1001, notif)
        }
    }
}
