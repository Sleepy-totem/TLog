package com.tlog.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class WeekStart { MONDAY, SUNDAY }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val oledBlack: Boolean = false,
    val weekStart: WeekStart = WeekStart.MONDAY,
    val employeeName: String = "Jordan Belsito",
    val employeeSignature: String = "Jordan Alan Belsito",
    val clientName: String = "Bachner Electro USA Inc",
    val stateOfWork: String = "South Carolina",
    val defaultJobSite: String = "Spartanburg, SC (MOC1)",
    val defaultProjectNumber: String = "012506200 - Q3125",
    val defaultLunchHours: Double = 0.0,
    val autoClockOutHour: Int = -1,
    val confirmOnClockInOut: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val biometricLock: Boolean = false,
    val hourlyRate: Double = 25.0,
    val overtimeThreshold: Double = 40.0,
    val overtimeMultiplier: Double = 1.5,
    val autoExportEnabled: Boolean = true,
    val autoExportEmail: String = "me@jordanbelsito.com"
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val oledBlack = booleanPreferencesKey("oled_black")
        val weekStart = stringPreferencesKey("week_start")
        val empName = stringPreferencesKey("employee_name")
        val empSig = stringPreferencesKey("employee_sig")
        val clientName = stringPreferencesKey("client_name")
        val stateOfWork = stringPreferencesKey("state_of_work")
        val jobSite = stringPreferencesKey("default_job_site")
        val projectNumber = stringPreferencesKey("default_project_number")
        val lunchHours = stringPreferencesKey("default_lunch_hours")
        val autoClockOut = stringPreferencesKey("auto_clock_out_hour")
        val confirm = booleanPreferencesKey("confirm_clock")
        val haptics = booleanPreferencesKey("haptics_enabled")
        val biometric = booleanPreferencesKey("biometric_lock")
        val rate = stringPreferencesKey("hourly_rate")
        val otThreshold = stringPreferencesKey("ot_threshold")
        val otMultiplier = stringPreferencesKey("ot_multiplier")
        val autoExport = booleanPreferencesKey("auto_export_enabled")
        val autoExportEmail = stringPreferencesKey("auto_export_email")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun get(): AppSettings = flow.first()

    private fun Preferences.toSettings() = AppSettings(
        themeMode = runCatching { ThemeMode.valueOf(this[Keys.themeMode] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM),
        useDynamicColor = this[Keys.dynamicColor] ?: true,
        oledBlack = this[Keys.oledBlack] ?: false,
        weekStart = runCatching { WeekStart.valueOf(this[Keys.weekStart] ?: WeekStart.MONDAY.name) }.getOrDefault(WeekStart.MONDAY),
        employeeName = this[Keys.empName] ?: "Jordan Belsito",
        employeeSignature = this[Keys.empSig] ?: "Jordan Alan Belsito",
        clientName = this[Keys.clientName] ?: "Bachner Electro USA Inc",
        stateOfWork = this[Keys.stateOfWork] ?: "South Carolina",
        defaultJobSite = this[Keys.jobSite] ?: "Spartanburg, SC (MOC1)",
        defaultProjectNumber = this[Keys.projectNumber] ?: "012506200 - Q3125",
        defaultLunchHours = (this[Keys.lunchHours] ?: "0.0").toDoubleOrNull() ?: 0.0,
        autoClockOutHour = (this[Keys.autoClockOut] ?: "-1").toIntOrNull() ?: -1,
        confirmOnClockInOut = this[Keys.confirm] ?: false,
        hapticsEnabled = this[Keys.haptics] ?: true,
        biometricLock = this[Keys.biometric] ?: false,
        hourlyRate = (this[Keys.rate] ?: "25.0").toDoubleOrNull() ?: 25.0,
        overtimeThreshold = (this[Keys.otThreshold] ?: "40.0").toDoubleOrNull() ?: 40.0,
        overtimeMultiplier = (this[Keys.otMultiplier] ?: "1.5").toDoubleOrNull() ?: 1.5,
        autoExportEnabled = this[Keys.autoExport] ?: true,
        autoExportEmail = this[Keys.autoExportEmail] ?: "me@jordanbelsito.com"
    )

    suspend fun update(mutator: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val next = mutator(prefs.toSettings())
            prefs[Keys.themeMode] = next.themeMode.name
            prefs[Keys.dynamicColor] = next.useDynamicColor
            prefs[Keys.oledBlack] = next.oledBlack
            prefs[Keys.weekStart] = next.weekStart.name
            prefs[Keys.empName] = next.employeeName
            prefs[Keys.empSig] = next.employeeSignature
            prefs[Keys.clientName] = next.clientName
            prefs[Keys.stateOfWork] = next.stateOfWork
            prefs[Keys.jobSite] = next.defaultJobSite
            prefs[Keys.projectNumber] = next.defaultProjectNumber
            prefs[Keys.lunchHours] = next.defaultLunchHours.toString()
            prefs[Keys.autoClockOut] = next.autoClockOutHour.toString()
            prefs[Keys.confirm] = next.confirmOnClockInOut
            prefs[Keys.haptics] = next.hapticsEnabled
            prefs[Keys.biometric] = next.biometricLock
            prefs[Keys.rate] = next.hourlyRate.toString()
            prefs[Keys.otThreshold] = next.overtimeThreshold.toString()
            prefs[Keys.otMultiplier] = next.overtimeMultiplier.toString()
            prefs[Keys.autoExport] = next.autoExportEnabled
            prefs[Keys.autoExportEmail] = next.autoExportEmail
        }
    }
}
