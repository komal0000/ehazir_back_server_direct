package com.ehazir.companion.ui.attendance

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    onNavigateSettings: () -> Unit,
    viewModel: AttendanceViewModel = hiltViewModel()
) {
    val state         by viewModel.state.collectAsState()
    val recentRecords by viewModel.recentRecords.collectAsState(initial = emptyList())
    val pendingCount  by viewModel.pendingCount.collectAsState(initial = 0)

    // Socket service state
    val socketRunning    by viewModel.socketRunning.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val lastScannedRfid  by viewModel.lastScannedRfid.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester    = remember { FocusRequester() }

    // Keep RFID field focused for fast barcode/RFID scanning
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("eHazir — Student Attendance") },
                actions = {
                    // Socket service toggle button
                    IconButton(onClick = {
                        if (socketRunning) viewModel.stopSocketService()
                        else viewModel.startSocketService()
                    }) {
                        Icon(
                            if (socketRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (socketRunning) "Stop RFID" else "Start RFID",
                            tint = if (socketRunning) Color(0xFF00D4AA) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            SyncBar(pendingCount = pendingCount, onSyncClick = { viewModel.syncNow() })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Socket Connection Status Bar ─────────────────────────────────
            AnimatedVisibility(visible = socketRunning) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        connectionStatus.forEach { (host, connected) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (connected) "🟢" else "🔴",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = host,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = if (connected) "Connected" else "Disconnected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (connected) Color(0xFF00D4AA) else Color(0xFFFF6B6B)
                                )
                            }
                        }
                        lastScannedRfid?.let { rfid ->
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Last scan: $rfid",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ── Auto-displayed current date/time ──────────────────────────────
            val nowFormat = SimpleDateFormat("EEE, dd MMM yyyy  •  HH:mm:ss", Locale.getDefault())
            var nowStr by remember { mutableStateOf(nowFormat.format(Date())) }
            LaunchedEffect(Unit) {
                while (true) {
                    nowStr = nowFormat.format(Date())
                    kotlinx.coroutines.delay(1000)
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = nowStr,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ── RFID Input ────────────────────────────────────────────────────
            OutlinedTextField(
                value = state.rfid,
                onValueChange = { viewModel.updateRfid(it) },
                label = { Text("Student RFID / ID Number") },
                placeholder = { Text("Scan or enter RFID…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { viewModel.submitAttendance() }
                )
            )

            // ── Optional: Check-Out Time Override ─────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (state.checkOutOverride != null)
                        "Check-Out: ${state.checkOutOverride}"
                    else
                        "Check-Out: auto (current time)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { viewModel.showTimePicker() }) {
                    Text(if (state.checkOutOverride != null) "Change" else "Set Time")
                }
                if (state.checkOutOverride != null) {
                    TextButton(onClick = { viewModel.setCheckOutOverride(null) }) {
                        Text("Clear")
                    }
                }
            }

            // ── Submit Button ─────────────────────────────────────────────────
            Button(
                onClick = { viewModel.submitAttendance() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = state.rfid.isNotBlank() && !state.isSubmitting
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Record Attendance", fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider()

            // ── Recent Records ────────────────────────────────────────────────
            Text("Recent Records", style = MaterialTheme.typography.titleMedium)

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(recentRecords) { record ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = record.rfid,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${record.date}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "In: ${record.checkIn}  •  Out: ${record.checkOut}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val syncColor = if (record.isSynced == 1) Color(0xFF00D4AA) else Color(0xFFFFB74D)
                            val syncText  = if (record.isSynced == 1) "Synced" else "Pending"
                            Text(text = syncText, style = MaterialTheme.typography.labelSmall, color = syncColor)
                        }
                    }
                }
            }
        }
    }

    // ── Check-Out Time Picker Dialog ──────────────────────────────────────────
    if (state.showTimePicker) {
        val timePickerState = rememberTimePickerState(is24Hour = true)
        AlertDialog(
            onDismissRequest = { viewModel.dismissTimePicker() },
            title = { Text("Set Check-Out Time") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val h = timePickerState.hour.toString().padStart(2, '0')
                    val m = timePickerState.minute.toString().padStart(2, '0')
                    viewModel.setCheckOutOverride("$h:$m:00")
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissTimePicker() }) { Text("Cancel") }
            }
        )
    }

    // ── Snackbar messages ─────────────────────────────────────────────────────
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
}

@Composable
fun SyncBar(pendingCount: Int, onSyncClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pendingCount > 0) {
                Text(
                    text = "⚠️  $pendingCount record(s) pending sync",
                    color = Color(0xFFFFB74D),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = onSyncClick) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Sync Now")
                }
            } else {
                Text("✅ All synced", color = Color(0xFF00D4AA), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
