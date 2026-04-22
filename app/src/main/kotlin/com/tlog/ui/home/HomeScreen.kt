package com.tlog.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.PunchClock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tlog.data.AppSettings
import com.tlog.pay.PayCalc
import com.tlog.viewmodel.TLogViewModel
import com.tlog.viewmodel.WeekViewState
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TLogViewModel,
    onOpenClock: () -> Unit,
    onOpenTimeCard: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val open by viewModel.openEntry.collectAsState()
    val weekState by viewModel.week.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState(initial = AppSettings())

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(open) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    // live week total: stored totalHours + elapsed on an open non-lunch shift
    val liveWeekHours = run {
        val stored = weekState?.totalHours ?: 0.0
        val o = open
        if (o == null || o.isLunch) stored
        else stored + ((now - o.clockInEpochMillis) / 3_600_000.0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TLog") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusCard(
                isClockedIn = open != null,
                isOnLunch = open?.isLunch == true,
                currentShiftMillis = open?.let { now - it.clockInEpochMillis } ?: 0L,
                weekHours = liveWeekHours,
                settings = settings
            )
            weekState?.let { MiniWeekChart(state = it, weekHours = liveWeekHours, openEntryLiveHours = run {
                val o = open
                if (o == null || o.isLunch) 0.0 else (now - o.clockInEpochMillis) / 3_600_000.0
            }) }

            HomeTile(
                title = "Clock In / Out",
                subtitle = when {
                    open?.isLunch == true -> "On lunch — tap to resume"
                    open != null -> "Running — tap to stop"
                    else -> "Start a shift"
                },
                icon = Icons.Filled.PunchClock,
                onClick = onOpenClock
            )
            HomeTile(
                title = "Timecard & Summary",
                subtitle = "View, edit, and export this week",
                icon = Icons.Filled.Assessment,
                onClick = onOpenTimeCard
            )
        }
    }
}

@Composable
private fun StatusCard(
    isClockedIn: Boolean,
    isOnLunch: Boolean,
    currentShiftMillis: Long,
    weekHours: Double,
    settings: AppSettings
) {
    val pay = PayCalc.split(weekHours, settings)
    val overtime = pay.overtimeHours > 0.0
    val container = when {
        overtime -> MaterialTheme.colorScheme.tertiaryContainer
        isOnLunch -> MaterialTheme.colorScheme.secondaryContainer
        isClockedIn -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = when {
                    isOnLunch -> "ON LUNCH"
                    isClockedIn -> "ON SHIFT"
                    else -> "Off"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isClockedIn) formatDuration(currentShiftMillis) else "--",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Text("Week total: ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatHours(weekHours),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (overtime) {
                    Text(
                        text = "   OT ${formatHours(pay.overtimeHours)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(
                text = "Pay estimate: $${"%.2f".format(pay.totalPay)}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MiniWeekChart(state: WeekViewState, weekHours: Double, openEntryLiveHours: Double) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    // per-day hours (adding live open shift to today's bucket if today is in this week)
    val dayHours = (0..6).map { offset ->
        val date = state.weekStart.plusDays(offset.toLong())
        val dow = date.dayOfWeek
        val stored = state.byDay[dow].orEmpty()
            .filter {
                Instant.ofEpochMilli(it.clockInEpochMillis).atZone(zone).toLocalDate() == date
            }
            .sumOf { it.durationHours }
        val live = if (date == today) openEntryLiveHours else 0.0
        date to (stored + live)
    }
    val target = 8.0
    val max = (dayHours.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(target)
    val barColor = MaterialTheme.colorScheme.primary
    val todayColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Week at a glance", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(formatHours(weekHours), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Canvas(
                modifier = Modifier.fillMaxWidth().height(80.dp)
            ) {
                val n = dayHours.size
                val gap = 8f
                val barW = (size.width - gap * (n - 1)) / n
                dayHours.forEachIndexed { i, (date, h) ->
                    val x = i * (barW + gap)
                    val frac = (h / max).toFloat().coerceIn(0f, 1f)
                    val barH = size.height * frac
                    // track
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(x, 0f),
                        size = Size(barW, size.height),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    // filled
                    drawRoundRect(
                        color = if (date == today) todayColor else barColor,
                        topLeft = Offset(x, size.height - barH),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                dayHours.forEach { (date, _) ->
                    Text(
                        text = date.dayOfWeek.name.first().toString(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Box(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val d = Duration.ofMillis(millis)
    val h = d.toHours()
    val m = d.toMinutes() % 60
    val s = d.seconds % 60
    return "%d:%02d:%02d".format(h, m, s)
}

internal fun formatHours(hours: Double): String {
    val totalMinutes = (hours * 60).toLong()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "${h}h ${m}m"
}
