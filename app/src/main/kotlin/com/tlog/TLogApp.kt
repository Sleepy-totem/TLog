package com.tlog

import android.app.Application
import com.tlog.data.SettingsStore
import com.tlog.data.TLogDatabase
import com.tlog.data.TimeLogRepository

class TLogApp : Application() {
    val database: TLogDatabase by lazy { TLogDatabase.create(this) }
    val repository: TimeLogRepository by lazy {
        TimeLogRepository(database.timeEntryDao(), database.savedJobDao())
    }
    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        com.tlog.notify.NotificationChannels.ensure(this)
        com.tlog.work.AutoExportScheduler.schedule(this)
    }
}
