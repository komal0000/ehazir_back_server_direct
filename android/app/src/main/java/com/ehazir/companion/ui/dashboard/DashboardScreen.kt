package com.ehazir.companion.ui.dashboard

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ehazir.companion.service.RfidSocketService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onManageConnection: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenAppLauncher: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val socketRunning by RfidSocketService.isRunning.collectAsState()
    val connectionStatus by RfidSocketService.connectionStatus.collectAsState()
    val lastScannedRfid by RfidSocketService.lastScannedRfid.collectAsState()
    val latestRfidHttpRunning by RfidSocketService.isLatestRfidHttpRunning.collectAsState()
    val context = LocalContext.current
    val homeSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }
    val homeLauncherIntent = remember { createHomeLauncherRequestIntent(context) }
    var actionsExpanded by remember { mutableStateOf(false) }

    BackHandler {
        // Dashboard is the app's home surface; ignore Back here.
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        OutlinedButton(onClick = { actionsExpanded = true }) {
                            Icon(Icons.Default.Menu, contentDescription = null)
                            Text("Menu", modifier = Modifier.padding(start = 6.dp))
                        }
                        DropdownMenu(
                            expanded = actionsExpanded,
                            onDismissRequest = { actionsExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Manage Connection") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    actionsExpanded = false
                                    onManageConnection()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Attendance") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null) },
                                onClick = {
                                    actionsExpanded = false
                                    onOpenAttendance()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Set as Home") },
                                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                                onClick = {
                                    actionsExpanded = false
                                    openHomeLauncherRequest(
                                        context = context,
                                        primaryIntent = homeLauncherIntent,
                                        launch = homeSettingsLauncher::launch
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Apps") },
                                leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null) },
                                onClick = {
                                    actionsExpanded = false
                                    onOpenAppLauncher()
                                }
                            )
                        }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Attendance Summary",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val sections = listOf("Students", "Teachers", "Staffs")
                val isTablet = maxWidth >= 720.dp
                if (maxWidth >= 720.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        sections.forEach { title ->
                            SummarySection(title = title, isTablet = isTablet, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        sections.forEach { title ->
                            SummarySection(title = title, isTablet = isTablet, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            RfidPanel(
                socketRunning = socketRunning,
                connectionStatus = connectionStatus,
                lastScannedRfid = lastScannedRfid,
                latestRfidHttpRunning = latestRfidHttpRunning,
                onToggleSocketService = {
                    if (socketRunning) viewModel.stopSocketService() else viewModel.startSocketService()
                },
                onToggleWebServer = {
                    if (latestRfidHttpRunning) viewModel.stopLatestRfidHttpServer()
                    else viewModel.startLatestRfidHttpServer()
                }
            )
        }
    }
}

private fun createHomeLauncherRequestIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true &&
            !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        ) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }
    } else {
        Intent(Settings.ACTION_HOME_SETTINGS)
    }
}

private fun openHomeLauncherRequest(
    context: Context,
    primaryIntent: Intent,
    launch: (Intent) -> Unit
) {
    val fallbackIntents = listOf(
        primaryIntent,
        Intent(Settings.ACTION_HOME_SETTINGS),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    )

    for (intent in fallbackIntents) {
        try {
            launch(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next system settings screen.
        } catch (_: Exception) {
            // Try the next system settings screen.
        }
    }

    Toast.makeText(
        context,
        "Open Android Settings and choose eHazir as the Home app.",
        Toast.LENGTH_LONG
    ).show()
}

@Composable
private fun RfidPanel(
    socketRunning: Boolean,
    connectionStatus: Map<String, Boolean>,
    lastScannedRfid: String?,
    latestRfidHttpRunning: Boolean,
    onToggleSocketService: () -> Unit,
    onToggleWebServer: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "RFID Panel",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onToggleSocketService) {
                        Text(if (socketRunning) "Stop Reader" else "Start Reader", fontSize = 12.sp)
                    }
                    TextButton(onClick = onToggleWebServer) {
                        Text(if (latestRfidHttpRunning) "Stop Web" else "Start Web", fontSize = 12.sp)
                    }
                }
            }
            HorizontalDivider()
            Text(
                text = if (socketRunning) "Reader service running" else "Reader service stopped",
                color = if (socketRunning) Color(0xFF008A6A) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            val connected = connectionStatus.values.count { it }
            Text("$connected/${connectionStatus.size} reader(s) connected")
            Text("Latest RFID: ${lastScannedRfid ?: "None"}")
            Text(
                text = if (latestRfidHttpRunning) "HTTP server: port 9000 running" else "HTTP server: stopped",
                color = if (latestRfidHttpRunning) Color(0xFF008A6A) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummarySection(title: String, isTablet: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                style = if (isTablet) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatusCard("Present", "0", Color(0xFF1F7A3D), Color(0xFFFFFFFF), isTablet, Modifier.weight(1f))
                StatusCard("Absent", "0", Color(0xFFC62828), Color(0xFFFFFFFF), isTablet, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatusCard("Half", "0", Color(0xFFB26A00), Color(0xFFFFFFFF), isTablet, Modifier.weight(1f))
                StatusCard("Leave", "0", Color(0xFFE56B1F), Color(0xFFFFFFFF), isTablet, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusCard(
    label: String,
    count: String,
    background: Color,
    foreground: Color,
    isTablet: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(if (isTablet) 78.dp else 58.dp)
            .background(background, shape = MaterialTheme.shapes.small)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = foreground,
            fontSize = if (isTablet) 18.sp else 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            count,
            color = foreground,
            fontSize = if (isTablet) 18.sp else 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
