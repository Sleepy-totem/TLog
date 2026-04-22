package com.tlog.ui.timecard

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tlog.data.TimeEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun EditEntryDialog(
    entry: TimeEntry,
    onDismiss: () -> Unit,
    onSave: (TimeEntry) -> Unit,
    onDelete: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    var date by remember {
        mutableStateOf(
            Instant.ofEpochMilli(entry.clockInEpochMillis).atZone(zone).toLocalDate()
        )
    }
    var clockIn by remember {
        mutableStateOf(
            Instant.ofEpochMilli(entry.clockInEpochMillis).atZone(zone).toLocalTime()
        )
    }
    var clockOut by remember {
        mutableStateOf(
            entry.clockOutEpochMillis?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalTime()
            }
        )
    }
    var note by remember { mutableStateOf(entry.note) }
    var jobSite by remember { mutableStateOf(entry.jobSite) }
    var project by remember { mutableStateOf(entry.projectNumber) }

    val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d yyyy")
    val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry.id == 0L) "New entry" else "Edit entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                        date.year, date.monthValue - 1, date.dayOfMonth
                    ).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Date: ${date.format(dateFmt)}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m -> clockIn = LocalTime.of(h, m) },
                                clockIn.hour, clockIn.minute, false
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("In: ${clockIn.format(timeFmt)}") }
                    OutlinedButton(
                        onClick = {
                            val base = clockOut ?: LocalTime.of(17, 0)
                            TimePickerDialog(
                                context,
                                { _, h, m -> clockOut = LocalTime.of(h, m) },
                                base.hour, base.minute, false
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Out: " + (clockOut?.format(timeFmt) ?: "—")
                        )
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
                OutlinedTextField(
                    value = jobSite, onValueChange = { jobSite = it },
                    label = { Text("Job site (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = project, onValueChange = { project = it },
                    label = { Text("Project # (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(4.dp))
                if (entry.id != 0L) {
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Delete entry") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val inMs = ZonedDateTime.of(date, clockIn, zone).toInstant().toEpochMilli()
                val outMs = clockOut?.let {
                    var end = ZonedDateTime.of(date, it, zone)
                    if (end.toLocalTime().isBefore(clockIn)) end = end.plusDays(1)
                    end.toInstant().toEpochMilli()
                }
                onSave(
                    entry.copy(
                        clockInEpochMillis = inMs,
                        clockOutEpochMillis = outMs,
                        note = note,
                        jobSite = jobSite,
                        projectNumber = project
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
