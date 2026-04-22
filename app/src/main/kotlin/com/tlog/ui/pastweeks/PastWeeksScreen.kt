package com.tlog.ui.pastweeks

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tlog.viewmodel.TLogViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastWeeksScreen(viewModel: TLogViewModel, onBack: () -> Unit) {
    var weeks by remember { mutableStateOf<List<LocalDate>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) { weeks = viewModel.listWeeksWithData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Past weeks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (weeks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No weeks with entries yet.")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(weeks) { weekStart ->
                WeekRow(
                    weekStart = weekStart,
                    onExport = {
                        scope.launch {
                            runCatching { viewModel.exportWeek(context, weekStart) }
                                .onSuccess {
                                    Toast.makeText(context, "Saved to Documents/TLog", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                    },
                    onEmail = {
                        scope.launch {
                            runCatching {
                                val uri = viewModel.exportWeek(context, weekStart)
                                val email = viewModel.settings.get().autoExportEmail
                                val subject = "TLog timecard — week ending ${weekStart.plusDays(6)}"
                                context.startActivity(
                                    Intent.createChooser(
                                        viewModel.buildEmailIntent(uri, email, subject),
                                        "Send timecard"
                                    )
                                )
                            }.onFailure {
                                Toast.makeText(context, "Email failed: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun WeekRow(weekStart: LocalDate, onExport: () -> Unit, onEmail: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
    val end = weekStart.plusDays(6)
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Week ending ${end.format(fmt)}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${weekStart.format(fmt)} – ${end.format(fmt)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.FileDownload, contentDescription = "Export")
            }
            IconButton(onClick = onEmail) {
                Icon(Icons.Filled.Email, contentDescription = "Email")
            }
        }
    }
}
