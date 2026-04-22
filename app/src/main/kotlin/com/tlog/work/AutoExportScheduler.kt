package com.tlog.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AutoExportScheduler {
    private const val NAME = "tlog_auto_export"

    fun schedule(context: Context) {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        var next = now.with(DayOfWeek.SUNDAY).withHour(17).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0L)

        val req = PeriodicWorkRequestBuilder<AutoExportWorker>(java.time.Duration.ofDays(7))
            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    @Suppress("unused")
    private fun LocalTime.fivePm() = LocalTime.of(17, 0)

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}
