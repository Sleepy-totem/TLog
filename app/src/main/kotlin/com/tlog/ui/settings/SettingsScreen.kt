package com.tlog.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.tlog.data.AppSettings
import com.tlog.data.SavedJob
import com.tlog.data.ThemeMode
import com.tlog.data.WeekStart
import com.tlog.viewmodel.TLogViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TLogViewModel,
    onBack: () -> Unit,
    onOpenPastWeeks: () -> Unit = {}
) {
    val settings by viewModel.settingsFlow.collectAsState(initial = AppSettings())
    val jobs by viewModel.savedJobs.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportDbLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                val json = viewModel.exportDatabaseJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.onSuccess {
                Toast.makeText(context, "Database exported", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importDbLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not read file")
                viewModel.importDatabaseJson(String(bytes))
            }.onSuccess {
                Toast.makeText(context, "Database imported", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Import failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section("Appearance") {
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.updateSettings { it.copy(themeMode = mode) } },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.titlecase() }) }
                        )
                    }
                }
                ToggleRow(
                    label = "Use device dynamic color (Android 12+)",
                    checked = settings.useDynamicColor,
                    onChange = { v -> viewModel.updateSettings { it.copy(useDynamicColor = v) } }
                )
                ToggleRow(
                    label = "OLED true-black (dark mode)",
                    checked = settings.oledBlack,
                    onChange = { v -> viewModel.updateSettings { it.copy(oledBlack = v) } }
                )
            }

            Section("Week") {
                Text("Week start", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeekStart.entries.forEach { ws ->
                        FilterChip(
                            selected = settings.weekStart == ws,
                            onClick = { viewModel.updateSettings { it.copy(weekStart = ws) } },
                            label = { Text(ws.name.lowercase().replaceFirstChar { it.titlecase() }) }
                        )
                    }
                }
            }

            Section("Profile (used on export)") {
                TextRow("Your name", settings.employeeName) { v ->
                    viewModel.updateSettings { it.copy(employeeName = v) }
                }
                TextRow("Signature name", settings.employeeSignature) { v ->
                    viewModel.updateSettings { it.copy(employeeSignature = v) }
                }
                TextRow("Client name", settings.clientName) { v ->
                    viewModel.updateSettings { it.copy(clientName = v) }
                }
                TextRow("State of work location", settings.stateOfWork) { v ->
                    viewModel.updateSettings { it.copy(stateOfWork = v) }
                }
                TextRow("Default job site", settings.defaultJobSite) { v ->
                    viewModel.updateSettings { it.copy(defaultJobSite = v) }
                }
                TextRow("Default project #", settings.defaultProjectNumber) { v ->
                    viewModel.updateSettings { it.copy(defaultProjectNumber = v) }
                }
            }

            Section("Saved jobs") {
                if (jobs.isEmpty()) {
                    Text("None saved — add one below.", style = MaterialTheme.typography.bodySmall)
                } else {
                    jobs.forEach { job ->
                        SavedJobRow(
                            job = job,
                            onMakeDefault = {
                                viewModel.saveJob(job.copy(isDefault = true))
                                viewModel.updateSettings {
                                    it.copy(
                                        defaultJobSite = job.jobSite,
                                        defaultProjectNumber = job.projectNumber
                                    )
                                }
                            },
                            onDelete = { viewModel.deleteJob(job) }
                        )
                    }
                }
                AddSavedJobRow(onAdd = { site, proj ->
                    viewModel.saveJob(SavedJob(jobSite = site, projectNumber = proj, isDefault = jobs.isEmpty()))
                })
            }

            Section("Clock") {
                ToggleRow(
                    label = "Confirm before clock in/out",
                    checked = settings.confirmOnClockInOut,
                    onChange = { v -> viewModel.updateSettings { it.copy(confirmOnClockInOut = v) } }
                )
                ToggleRow(
                    label = "Haptic feedback on clock actions",
                    checked = settings.hapticsEnabled,
                    onChange = { v -> viewModel.updateSettings { it.copy(hapticsEnabled = v) } }
                )
            }

            Section("Security") {
                ToggleRow(
                    label = "Require fingerprint / device credential to open",
                    checked = settings.biometricLock,
                    onChange = { v -> viewModel.updateSettings { it.copy(biometricLock = v) } }
                )
            }

            Section("Pay estimate") {
                TextRow("Hourly rate (USD)", settings.hourlyRate.toString()) { v ->
                    v.toDoubleOrNull()?.let { n ->
                        viewModel.updateSettings { it.copy(hourlyRate = n) }
                    }
                }
                TextRow("Overtime threshold (hours/week)", settings.overtimeThreshold.toString()) { v ->
                    v.toDoubleOrNull()?.let { n ->
                        viewModel.updateSettings { it.copy(overtimeThreshold = n) }
                    }
                }
                TextRow("Overtime multiplier", settings.overtimeMultiplier.toString()) { v ->
                    v.toDoubleOrNull()?.let { n ->
                        viewModel.updateSettings { it.copy(overtimeMultiplier = n) }
                    }
                }
            }

            Section("Export") {
                Text(
                    "Timecards are saved to Documents/TLog as Standard_TS_Mon-Sun filled xlsx files.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        scope.launch {
                            runCatching { viewModel.exportCurrentWeek(context) }
                                .onSuccess {
                                    Toast.makeText(context, "Exported to Documents/TLog", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export current week") }
                OutlinedButton(onClick = onOpenPastWeeks, modifier = Modifier.fillMaxWidth()) {
                    Text("Past weeks…")
                }
                ToggleRow(
                    label = "Auto-export Sunday 5 pm + notify",
                    checked = settings.autoExportEnabled,
                    onChange = { v -> viewModel.updateSettings { it.copy(autoExportEnabled = v) } }
                )
                TextRow("Auto-email recipient", settings.autoExportEmail) { v ->
                    viewModel.updateSettings { it.copy(autoExportEmail = v) }
                }
            }

            Section("Database") {
                Text(
                    "Back up or restore all entries and saved jobs as a JSON file. " +
                            "Importing replaces the current database.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { exportDbLauncher.launch("tlog-backup.json") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export database (JSON)") }
                OutlinedButton(
                    onClick = { importDbLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import database (JSON)") }
            }

            Section("About") {
                Text(
                    "TLog ${com.tlog.BuildConfig.VERSION_NAME} (build ${com.tlog.BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium
                )
                UpdateCheckRow()
            }
        }
    }
}

@Composable
private fun UpdateCheckRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<com.tlog.update.UpdateChecker.Available?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            enabled = !busy,
            onClick = {
                busy = true; status = "Checking…"; found = null
                scope.launch {
                    val a = com.tlog.update.UpdateChecker.check()
                    busy = false
                    if (a == null) status = "You're up to date."
                    else { found = a; status = "Update available: ${a.versionName}" }
                }
            },
            modifier = Modifier.weight(1f)
        ) { Text("Check for updates") }
    }
    if (status.isNotEmpty()) {
        Text(status, style = MaterialTheme.typography.bodySmall)
    }
    found?.let { a ->
        Button(
            enabled = !busy,
            onClick = {
                busy = true; status = "Downloading ${a.versionName}…"
                scope.launch {
                    try {
                        val file = com.tlog.update.UpdateChecker.download(context, a)
                        status = "Opening installer…"
                        com.tlog.update.UpdateChecker.install(context, file)
                    } catch (e: Exception) {
                        status = "Update failed: ${e.message}"
                    } finally { busy = false }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Download & install ${a.versionName}") }
        if (a.notes.isNotBlank()) {
            Text(a.notes, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SavedJobRow(job: SavedJob, onMakeDefault: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(job.jobSite, fontWeight = FontWeight.SemiBold)
            Text(job.projectNumber, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onMakeDefault) {
            Icon(
                if (job.isDefault) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "Make default"
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
private fun AddSavedJobRow(onAdd: (site: String, project: String) -> Unit) {
    var site by remember { mutableStateOf("") }
    var project by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = site, onValueChange = { site = it },
            label = { Text("Job site") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = project, onValueChange = { project = it },
            label = { Text("Project #") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedButton(
            onClick = {
                if (site.isNotBlank()) {
                    onAdd(site.trim(), project.trim())
                    site = ""; project = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add job") }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TextRow(label: String, value: String, onChange: (String) -> Unit) {
    var local by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != local) local = value }
    LaunchedEffect(local) {
        if (local != value) {
            delay(400)
            if (local != value) onChange(local)
        }
    }
    OutlinedTextField(
        value = local,
        onValueChange = { local = it },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
