package com.tlog.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_entry")
data class TimeEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clockInEpochMillis: Long,
    val clockOutEpochMillis: Long?,
    val note: String = "",
    val jobSite: String = "",
    val projectNumber: String = "",
    @ColumnInfo(defaultValue = "0") val isLunch: Boolean = false,
    @ColumnInfo(defaultValue = "0") val perDiem: Boolean = false
) {
    val isOpen: Boolean get() = clockOutEpochMillis == null

    val durationMillis: Long
        get() = (clockOutEpochMillis ?: System.currentTimeMillis()) - clockInEpochMillis

    val durationHours: Double get() = durationMillis / 3_600_000.0
}

@Entity(tableName = "saved_job")
data class SavedJob(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobSite: String,
    val projectNumber: String,
    val isDefault: Boolean = false
)
