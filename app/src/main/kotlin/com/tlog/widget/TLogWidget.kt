package com.tlog.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.action.actionStartActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.action.ActionCallback
import com.tlog.MainActivity
import com.tlog.TLogApp
import com.tlog.util.WeekHelper
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate

private object WidgetKeys {
    val CLOCKED_IN = androidx.datastore.preferences.core.booleanPreferencesKey("clocked_in")
    val ON_LUNCH = androidx.datastore.preferences.core.booleanPreferencesKey("on_lunch")
    val OPEN_START_MS = androidx.datastore.preferences.core.longPreferencesKey("open_start")
    val WEEK_MILLIS = androidx.datastore.preferences.core.longPreferencesKey("week_millis")
}

class TLogWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        refreshState(context, id)
        provideContent {
            GlanceTheme {
                val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
                val clockedIn = prefs[WidgetKeys.CLOCKED_IN] ?: false
                val onLunch = prefs[WidgetKeys.ON_LUNCH] ?: false
                val startMs = prefs[WidgetKeys.OPEN_START_MS] ?: 0L
                val weekMs = prefs[WidgetKeys.WEEK_MILLIS] ?: 0L

                val openLiveMs = if (clockedIn && !onLunch && startMs > 0)
                    (System.currentTimeMillis() - startMs) else 0L
                val liveWeekMs = weekMs + openLiveMs

                Column(
                    modifier = GlanceModifier.fillMaxSize()
                        .padding(12.dp)
                        .cornerRadius(20.dp)
                        .background(GlanceTheme.colors.surface)
                        .clickable(actionStartActivity<MainActivity>())
                ) {
                    Text(
                        "TLog",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                    Text(
                        when {
                            onLunch -> "On lunch"
                            clockedIn -> "On shift"
                            else -> "Off"
                        },
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                    Text(
                        formatHoursShort(liveWeekMs),
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                    Row(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = if (clockedIn) "Clock out" else "Clock in",
                            modifier = GlanceModifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .background(
                                    if (clockedIn) GlanceTheme.colors.errorContainer
                                    else GlanceTheme.colors.primaryContainer
                                )
                                .cornerRadius(16.dp)
                                .clickable(actionRunCallback<ToggleClockAction>()),
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (clockedIn) GlanceTheme.colors.onErrorContainer
                                else GlanceTheme.colors.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }
    }

    companion object {
        suspend fun refreshState(context: Context, id: GlanceId) {
            val app = context.applicationContext as TLogApp
            val settings = app.settings.get()
            val open = app.repository.observeOpen().first()
            val (startMs, endMs) = WeekHelper.weekBounds(LocalDate.now(), settings.weekStart)
            val entries = app.repository.getRange(startMs, endMs).filterNot { it.isLunch }
            val weekMs = entries.sumOf { (it.clockOutEpochMillis ?: System.currentTimeMillis()) - it.clockInEpochMillis }
            updateAppWidgetState(context, id) { prefs ->
                prefs[WidgetKeys.CLOCKED_IN] = open != null
                prefs[WidgetKeys.ON_LUNCH] = open?.isLunch == true
                prefs[WidgetKeys.OPEN_START_MS] = open?.clockInEpochMillis ?: 0L
                prefs[WidgetKeys.WEEK_MILLIS] = weekMs
            }
        }
    }
}

class TLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TLogWidget()
}

class ToggleClockAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as TLogApp
        runCatching {
            val open = app.repository.observeOpen().first()
            if (open == null) app.repository.clockIn() else app.repository.clockOut(open)
        }
        TLogWidget.refreshState(context, glanceId)
        TLogWidget().updateAll(context)
    }
}

private fun formatHoursShort(millis: Long): String {
    val d = Duration.ofMillis(millis)
    val h = d.toHours()
    val m = (d.toMinutes() % 60)
    return "${h}h ${m}m"
}
