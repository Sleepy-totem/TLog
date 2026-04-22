package com.tlog.ui.clock

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tlog.viewmodel.TLogViewModel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen(viewModel: TLogViewModel, onBack: () -> Unit) {
    val open by viewModel.openEntry.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState(initial = com.tlog.data.AppSettings())

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(open) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    var confirmingToggle by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    fun buzz() {
        if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val onError: (String) -> Unit = { msg ->
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val isOnLunch = open?.isLunch == true
    val isOnShift = open != null && !isOnLunch

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clock") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = when {
                    isOnLunch -> "ON LUNCH"
                    isOnShift -> "ON SHIFT"
                    else -> "OFF"
                },
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(8.dp))
            val elapsed = open?.let { now - it.clockInEpochMillis } ?: 0L
            Text(
                text = if (open != null) formatDuration(elapsed) else "--:--:--",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            open?.let {
                val fmt = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a")
                val started = Instant.ofEpochMilli(it.clockInEpochMillis)
                    .atZone(ZoneId.systemDefault()).format(fmt)
                Text(
                    if (isOnLunch) "Lunch started $started" else "Started $started",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = {
                    if (settings.confirmOnClockInOut) confirmingToggle = true
                    else {
                        buzz()
                        viewModel.toggleClock(onError)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(84.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (open != null) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primary,
                    contentColor = if (open != null) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (open != null) "CLOCK OUT" else "CLOCK IN",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    buzz()
                    viewModel.toggleLunch(onError)
                },
                enabled = open != null,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = CircleShape
            ) {
                Text(
                    text = if (isOnLunch) "BACK FROM LUNCH" else "GO TO LUNCH",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (confirmingToggle) {
        AlertDialog(
            onDismissRequest = { confirmingToggle = false },
            title = { Text(if (open != null) "End shift?" else "Start shift?") },
            text = {
                Text(
                    if (open != null) "You'll clock out now and your shift will be saved."
                    else "You'll clock in at the current time."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    buzz()
                    viewModel.toggleClock(onError)
                    confirmingToggle = false
                }) { Text(if (open != null) "Clock out" else "Clock in") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingToggle = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatDuration(millis: Long): String {
    val d = Duration.ofMillis(millis)
    val h = d.toHours()
    val m = d.toMinutes() % 60
    val s = d.seconds % 60
    return "%d:%02d:%02d".format(h, m, s)
}

