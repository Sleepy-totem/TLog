package com.tlog.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val EXPORT = "tlog_export"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(EXPORT) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        EXPORT,
                        "Timecard export",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Weekly auto-export notifications"
                    }
                )
            }
        }
    }
}
