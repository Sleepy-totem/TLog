package com.tlog.ui.timecard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Switch
import com.tlog.data.TimeEntry
import com.tlog.pay.PayCalc
import com.tlog.viewmodel.TLogViewModel
import com.tlog.viewmodel.WeekViewState
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeCardScreen(
    viewModel: TLogViewModel,
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit = {},
    onOpenPastWeeks: () -> Unit = {}
) {
    val weekState by viewModel.week.collectAsState()
    var editing by remember { mutableStateOf<TimeEntry?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.focusToday() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timecard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPastWeeks) {
                        Icon(Icons.Filled.History, contentDescription = "Past weeks")
                    }
                    IconButton(onClick = { viewModel.focusToday() }) {
                        Icon(Icons.Filled.Today, contentDescription = "Today")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            runCatching { viewModel.exportCurrentWeek(context) }
                                .onSuccess { uri ->
                                    Toast.makeText(context, "Saved to Documents/TLog", Toast.LENGTH_SHORT).show()
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent.createChooser(
                                                viewModel.buildShareIntent(uri), "Share timecard"
                                            )
                                        )
                                    }
                                }
                                .onFailure { e ->
                                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                    },
                    icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    text = { Text("Export") }
                )
                FloatingActionButton(onClick = { showNew = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add entry")
                }
            }
        }
    ) { padding ->
        val w = weekState
        if (w == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…")
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            WeekHeader(
                state = w,
                onPrev = { viewModel.shiftWeek(-1) },
                onNext = { viewModel.shiftWeek(1) }
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = (0..6).map { w.weekStart.plusDays(it.toLong()) }) { date ->
                    val dow = date.dayOfWeek
                    val dayEntries = w.byDay[dow].orEmpty()
                    val perDiem = dayEntries.any { it.perDiem }
                    DayCard(
                        date = date,
                        entries = dayEntries,
                        perDiem = perDiem,
                        onPerDiemChange = { viewModel.togglePerDiem(date, it) },
                        onOpenDay = { onOpenDay(date) }
                    )
                }
                item { Spacer(Modifier.height(96.dp)) }
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
        val stubDate = (weekState?.weekStart ?: LocalDate.now()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val stubEntry = TimeEntry(
            clockInEpochMillis = stubDate + 8 * 3600_000L,
            clockOutEpochMillis = stubDate + 17 * 3600_000L
        )
        EditEntryDialog(
            entry = stubEntry,
            onDismiss = { showNew = false },
            onSave = { updated -> viewModel.saveEntry(updated); showNew = false },
            onDelete = { showNew = false }
        )
    }
}

@Composable
private fun WeekHeader(
    state: WeekViewState,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val fmt = DateTimeFormatter.ofPattern("MMM d")
    val end = state.weekStart.plusDays(6)
    val pay = PayCalc.split(state.totalHours, state.settings)
    val ot = pay.overtimeHours > 0.0
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ot) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${state.weekStart.format(fmt)} – ${end.format(fmt)}, ${state.weekStart.year}",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Total: ${com.tlog.ui.home.formatHours(state.totalHours)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (ot) {
                    Text(
                        "OT: ${com.tlog.ui.home.formatHours(pay.overtimeHours)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "Est pay: $${"%.2f".format(pay.totalPay)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next week")
            }
        }
    }
}

@Composable
private fun DayCard(
    date: LocalDate,
    entries: List<TimeEntry>,
    perDiem: Boolean,
    onPerDiemChange: (Boolean) -> Unit,
    onOpenDay: () -> Unit
) {
    val totalHours = entries.sumOf { it.durationHours }
    val isToday = date == LocalDate.now()
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenDay() },
        colors = CardDefaults.cardColors(
            containerColor =
                if (isToday) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = DateTimeFormatter.ofPattern("MMM d").format(date),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.padding(horizontal = 8.dp))
                Text(
                    text = com.tlog.ui.home.formatHours(totalHours),
                    fontWeight = FontWeight.Bold
                )
            }
            val fmt = DateTimeFormatter.ofPattern("h:mm a")
            val zone = ZoneId.systemDefault()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Text(
                    "Per Diem",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = perDiem,
                    onCheckedChange = onPerDiemChange,
                    enabled = entries.isNotEmpty()
                )
            }
            if (entries.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "No entries — tap to add",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Spacer(Modifier.height(6.dp))
                entries.forEach { e ->
                    val inTime = Instant.ofEpochMilli(e.clockInEpochMillis).atZone(zone).format(fmt)
                    val outTime = e.clockOutEpochMillis?.let {
                        Instant.ofEpochMilli(it).atZone(zone).format(fmt)
                    } ?: "… running"
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$inTime → $outTime", modifier = Modifier.weight(1f))
                        Text(
                            com.tlog.ui.home.formatHours(e.durationHours),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// Helpers exposed for DayOfWeek use
private fun DayOfWeek.short() = getDisplayName(TextStyle.SHORT, Locale.getDefault())
