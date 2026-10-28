package com.ehazir.companion.ui.register

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scroll = rememberScrollState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "eHazir Device Setup",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Configure this device to record attendance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            // ── STEP 1: Fetch Telegram Token ──────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Step 1 — Get your token",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Tap the button below. A token will be sent to you via Telegram from the eHazir bot.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Button(
                        onClick = { viewModel.fetchToken() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isFetchingToken
                    ) {
                        if (state.isFetchingToken) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Request Token via Telegram")
                    }

                    AnimatedVisibility(
                        visible = state.tokenInstructions.isNotBlank(),
                        enter = fadeIn() + expandVertically()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = state.tokenInstructions,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── STEP 2: Configuration Fields ──────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Step 2 — Device Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Fill in the details matching your .env setup",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    // LOCAL_TOKEN
                    OutlinedTextField(
                        value = state.localToken,
                        onValueChange = { viewModel.updateLocalToken(it) },
                        label = { Text("LOCAL_TOKEN (from Telegram)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        singleLine = true
                    )

                    // DEVICE_ID (read-only, auto-generated)
                    OutlinedTextField(
                        value = state.deviceId,
                        onValueChange = { },
                        label = { Text("DEVICE_ID (auto-generated)") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "RFID Reader Devices",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    state.rfidReaders.forEachIndexed { index, reader ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Reader ${index + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (state.rfidReaders.size > 1) {
                                    TextButton(onClick = { viewModel.removeRfidReader(index) }) {
                                        Text("Remove")
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = reader.host,
                                    onValueChange = {
                                        viewModel.updateRfidReader(index, reader.copy(host = it))
                                    },
                                    label = { Text("Host / IP") },
                                    placeholder = { Text("192.168.1.20") },
                                    modifier = Modifier.weight(3f).padding(bottom = 12.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = reader.port,
                                    onValueChange = {
                                        viewModel.updateRfidReader(index, reader.copy(port = it))
                                    },
                                    label = { Text("Port") },
                                    modifier = Modifier.weight(1.5f).padding(bottom = 12.dp),
                                    singleLine = true
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.addRfidReader() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add RFID Reader")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "eHazir Backend Server",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // MAIN_PROTOCOL + MAIN_DOMAIN
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Protocol dropdown
                        var expandedProto by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedProto,
                            onExpandedChange = { expandedProto = !expandedProto },
                            modifier = Modifier.weight(1.3f)
                        ) {
                            OutlinedTextField(
                                value = state.mainProtocol,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Protocol") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProto) },
                                modifier = Modifier.menuAnchor().padding(bottom = 12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedProto,
                                onDismissRequest = { expandedProto = false }
                            ) {
                                listOf("https", "http").forEach { proto ->
                                    DropdownMenuItem(
                                        text = { Text(proto) },
                                        onClick = {
                                            viewModel.updateMainProtocol(proto)
                                            expandedProto = false
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = state.mainDomain,
                            onValueChange = { viewModel.updateMainDomain(it) },
                            label = { Text("Tenant") },
                            placeholder = { Text("xyz") },
                            modifier = Modifier.weight(2.5f).padding(bottom = 12.dp),
                            singleLine = true
                        )
                    }

                    // DEVICE_UPDATE_TIME
                    OutlinedTextField(
                        value = state.deviceUpdateTime,
                        onValueChange = { viewModel.updateDeviceUpdateTime(it) },
                        label = { Text("DEVICE_UPDATE_TIME (seconds)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        singleLine = true
                    )

                    // Submit
                    if (state.isSubmitting) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Button(
                            onClick = { viewModel.submitConfig() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.localToken.isNotBlank() && state.mainDomain.isNotBlank()
                        ) {
                            Text("Save & Register Device", modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Show errors in snackbar
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Navigate on success
    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) onRegisterSuccess()
    }
}
