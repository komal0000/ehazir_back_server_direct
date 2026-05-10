package com.ehazir.companion.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ehazir.companion.service.RfidSocketService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onUnregister: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scroll = rememberScrollState()

    // Socket service state
    val socketRunning    by RfidSocketService.isRunning.collectAsState()
    val connectionStatus by RfidSocketService.connectionStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Device Info", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = state.deviceId,
                onValueChange = {},
                label = { Text("DEVICE_ID (read-only)") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("Backend Server", style = MaterialTheme.typography.titleMedium)

            // MAIN_PROTOCOL dropdown
            var expandedProto by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedProto,
                onExpandedChange = { expandedProto = !expandedProto }
            ) {
                OutlinedTextField(
                    value = state.mainProtocol,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("MAIN_PROTOCOL") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProto) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedProto,
                    onDismissRequest = { expandedProto = false }
                ) {
                    listOf("https", "http").forEach { proto ->
                        DropdownMenuItem(
                            text = { Text(proto) },
                            onClick = {
                                viewModel.update { it.copy(mainProtocol = proto) }
                                expandedProto = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.mainDomain,
                onValueChange = { viewModel.update { s -> s.copy(mainDomain = it) } },
                label = { Text("MAIN_DOMAIN") },
                placeholder = { Text("demo.ehazircloud.com") },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("RFID Reader Device", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.serverHost,
                    onValueChange = { viewModel.update { s -> s.copy(serverHost = it) } },
                    label = { Text("SERVER_HOST") },
                    modifier = Modifier.weight(3f)
                )
                OutlinedTextField(
                    value = state.serverPort,
                    onValueChange = { viewModel.update { s -> s.copy(serverPort = it) } },
                    label = { Text("PORT") },
                    modifier = Modifier.weight(1.5f)
                )
            }

            OutlinedTextField(
                value = state.deviceUpdateTime,
                onValueChange = { viewModel.update { s -> s.copy(deviceUpdateTime = it) } },
                label = { Text("DEVICE_UPDATE_TIME (seconds)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.localToken,
                onValueChange = { viewModel.update { s -> s.copy(localToken = it) } },
                label = { Text("LOCAL_TOKEN") },
                modifier = Modifier.fillMaxWidth()
            )

            // ── RFID Socket Service Control ───────────────────────────────────
            HorizontalDivider()
            Text("RFID Socket Service", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (socketRunning) "Service Running" else "Service Stopped",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (socketRunning) Color(0xFF00D4AA) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (socketRunning && connectionStatus.isNotEmpty()) {
                        val connectedCount = connectionStatus.values.count { it }
                        Text(
                            text = "$connectedCount/${connectionStatus.size} reader(s) connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = {
                        if (socketRunning) viewModel.stopSocketService()
                        else viewModel.startSocketService()
                    },
                    colors = if (socketRunning)
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else
                        ButtonDefaults.buttonColors()
                ) {
                    Text(if (socketRunning) "Stop Service" else "Start Service")
                }
            }

            if (socketRunning && connectionStatus.isNotEmpty()) {
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
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────

            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Changes")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.unregister(onUnregister) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unregister Device (Clear Data)")
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
}

