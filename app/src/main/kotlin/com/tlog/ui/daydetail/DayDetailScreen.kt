package com.tlog.ui.daydetail

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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tlog.data.TimeEntry
import com.tlog.ui.timecard.EditEntryDialog
import com.tlog.viewmodel.TLogViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    viewModel: TLogViewModel,
    dateIso: String,
    onBack: () -> Unit
) {
    val weekState by viewModel.week.collectAsState()
    val date = remember(dateIso) { LocalDate.parse(dateIso) }
    val zone = ZoneId.systemDefault()

    var editing by remember { mutableStateOf<TimeEntry?>(null) }
    var showNew by remember { mutableStateOf(false) }

    val entries = weekState?.byDay?.get(date.dayOfWeek).orEmpty()
        .filter {
            Instant.ofEpochMilli(it.clockInEpochMillis)
                .atZone(zone).toLocalDate() == date
        }
    val totalHours = entries.sumOf { it.durationHours }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                        Text(
                            date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add entry")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Total: ${com.tlog.ui.home.formatHours(totalHours)} h",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            DayTimeline(
                date = date,
                entries = entries,
                onEntryClick = { editing = it }
            )

            if (entries.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No entries — tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries) { e ->
                        EntryCard(entry = e, onClick = { editing = e })
                    }
                }
            }
        }
    }

    val current = editing
    if (current != null) {
        EditEntryDialog(
            entry = current,
            onDismiss = { editing = null },
            onSave = { updated -> viewModel.saveEntry(updated); editing = null },
            onDelete = { viewModel.deleteEntry(current); editing = null }
        )
    }
    if (showNew) {
        val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val stub = TimeEntry(
            clockInEpochMillis = startMs + 8 * 3600_000L,
            clockOutEpochMillis = startMs + 17 * 3600_000L
        )
        EditEntryDialog(
            entry = stub,
            onDismiss = { showNew = false },
            onSave = { updated -> viewModel.saveEntry(updated); showNew = false },
            onDelete = { showNew = false }
        )
    }
}

@Composable
private fun DayTimeline(
    date: LocalDate,
    entries: List<TimeEntry>,
    onEntryClick: (TimeEntry) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayMs = 24 * 3600_000L

    data class Bar(val startFrac: Float, val endFrac: Float, val entry: TimeEntry)
    val bars = entries.mapNotNull { e ->
        val inMs = max(e.clockInEpochMillis, dayStartMs)
        val outMs = min(e.clockOutEpochMillis ?: System.currentTimeMillis(), dayStartMs + dayMs)
        if (outMs <= inMs) null
        else Bar(
            startFrac = (inMs - dayStartMs).toFloat() / dayMs,
            endFrac = (outMs - dayStartMs).toFloat() / dayMs,
            entry = e
        )
    }

    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.outline
    val nowLineColor = MaterialTheme.colorScheme.error

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("12a", "3a", "6a", "9a", "12p", "3p", "6p", "9p", "12a").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(4.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .pointerInput(bars) {
                        detectTapGestures { offset: Offset ->
                            val frac = offset.x / size.width
                            val hit = bars.firstOrNull { frac in it.startFrac..it.endFrac }
                            hit?.let { onEntryClick(it.entry) }
                        }
                    }
            ) {
                val h = size.height
                val w = size.width
                val track = h * 0.7f
                val top = (h - track) / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, top),
                    size = androidx.compose.ui.geometry.Size(w, track),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                )
                for (hr in 0..24 step 3) {
                    val x = w * (hr / 24f)
                    drawLine(
                        color = tickColor.copy(alpha = 0.35f),
                        start = Offset(x, top - 4f),
                        end = Offset(x, top + track + 4f),
                        strokeWidth = 1f
                    )
                }
                bars.forEach { b ->
                    val x0 = w * b.startFrac
                    val x1 = w * b.endFrac
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x0, top),
                        size = androidx.compose.ui.geometry.Size((x1 - x0).coerceAtLeast(2f), track),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                    )
                }
                // "now" vertical line when looking at today
                if (date == LocalDate.now()) {
                    val nowMs = System.currentTimeMillis()
                    val nowFrac = ((nowMs - dayStartMs).toFloat() / dayMs).coerceIn(0f, 1f)
                    val x = w * nowFrac
                    drawLine(
                        color = nowLineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 3f
                    )
                    drawCircle(
                        color = nowLineColor,
                        radius = 6f,
                        center = Offset(x, top)
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryCard(entry: TimeEntry, onClick: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("h:mm a")
    val zone = ZoneId.systemDefault()
    val inTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(entry.clockInEpochMillis), zone).format(fmt)
    val outTime = entry.clockOutEpochMillis?.let {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zone).format(fmt)
    } ?: "running"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(entry.id) { detectTapGestures { onClick() } }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$inTime → $outTime", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(com.tlog.ui.home.formatHours(entry.durationHours), fontWeight = FontWeight.Bold)
            }
            if (entry.note.isNotBlank()) {
                Text(entry.note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
