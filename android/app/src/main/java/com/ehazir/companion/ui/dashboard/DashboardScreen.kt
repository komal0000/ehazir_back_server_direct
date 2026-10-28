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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ehazir.companion.R
import com.ehazir.companion.service.RfidSocketService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onManageConnection: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenAppLauncher: () -> Unit,
    onOpenManageStudentAttendance: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val socketRunning by RfidSocketService.isRunning.collectAsState()
    val connectionStatus by RfidSocketService.connectionStatus.collectAsState()
    val lastScannedRfid by RfidSocketService.lastScannedRfid.collectAsState()
    val latestRfidHttpRunning by RfidSocketService.isLatestRfidHttpRunning.collectAsState()
    val lastAdmsRequest by RfidSocketService.lastAdmsRequest.collectAsState()
    val admsLog by RfidSocketService.admsLog.collectAsState()
    val context = LocalContext.current
    val homeSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }
    val homeLauncherIntent = remember { createHomeLauncherRequestIntent(context) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var showStudentAttendanceDetails by remember { mutableStateOf(false) }

    BackHandler {
        // Dashboard is the app's home surface; ignore Back here.
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(120.dp),
                title = { EhazirLogo() },
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
                                text = { Text("Manage Student Attendance") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                onClick = {
                                    actionsExpanded = false
                                    onOpenManageStudentAttendance()
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
            val attendanceSummaryError = state.attendanceSummaryError
            when {
                state.isAttendanceSummaryLoading -> Text(
                    "Loading today attendance...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                attendanceSummaryError != null -> Text(
                    attendanceSummaryError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isTablet = maxWidth >= 720.dp
                if (isTablet && showStudentAttendanceDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummarySection(
                            title = "Students",
                            counts = state.studentCounts,
                            isTablet = isTablet,
                            modifier = Modifier.fillMaxWidth(),
                            showDetailButton = state.gradeAttendance.isNotEmpty(),
                            showDetails = showStudentAttendanceDetails,
                            onToggleDetails = {
                                showStudentAttendanceDetails = !showStudentAttendanceDetails
                            },
                            detailContent = {
                                GradeAttendanceTable(
                                    totalCounts = state.gradeAttendance.aggregateCounts(),
                                    gradeAttendance = state.gradeAttendance
                                )
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SummarySection(
                                title = "Teachers",
                                counts = state.teacherCounts,
                                isTablet = isTablet,
                                modifier = Modifier.weight(1f)
                            )
                            SummarySection(
                                title = "Staffs",
                                counts = state.staffCounts,
                                isTablet = isTablet,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else if (isTablet) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummarySection(
                            title = "Students",
                            counts = state.studentCounts,
                            isTablet = isTablet,
                            modifier = Modifier.weight(1f),
                            showDetailButton = state.gradeAttendance.isNotEmpty(),
                            showDetails = showStudentAttendanceDetails,
                            onToggleDetails = {
                                showStudentAttendanceDetails = !showStudentAttendanceDetails
                            },
                            detailContent = {
                                GradeAttendanceTable(
                                    totalCounts = state.gradeAttendance.aggregateCounts(),
                                    gradeAttendance = state.gradeAttendance
                                )
                            }
                        )
                        SummarySection(
                            title = "Teachers",
                            counts = state.teacherCounts,
                            isTablet = isTablet,
                            modifier = Modifier.weight(1f)
                        )
                        SummarySection(
                            title = "Staffs",
                            counts = state.staffCounts,
                            isTablet = isTablet,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummarySection(
                            title = "Students",
                            counts = state.studentCounts,
                            isTablet = isTablet,
                            modifier = Modifier.fillMaxWidth(),
                            showDetailButton = state.gradeAttendance.isNotEmpty(),
                            showDetails = showStudentAttendanceDetails,
                            onToggleDetails = {
                                showStudentAttendanceDetails = !showStudentAttendanceDetails
                            },
                            detailContent = {
                                GradeAttendanceTable(
                                    totalCounts = state.gradeAttendance.aggregateCounts(),
                                    gradeAttendance = state.gradeAttendance
                                )
                            }
                        )
                        SummarySection(
                            title = "Teachers",
                            counts = state.teacherCounts,
                            isTablet = isTablet,
                            modifier = Modifier.fillMaxWidth()
                        )
                        SummarySection(
                            title = "Staffs",
                            counts = state.staffCounts,
                            isTablet = isTablet,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            RfidPanel(
                socketRunning = socketRunning,
                connectionStatus = connectionStatus,
                lastScannedRfid = lastScannedRfid,
                latestRfidHttpRunning = latestRfidHttpRunning,
                lastAdmsRequest = lastAdmsRequest,
                onToggleSocketService = {
                    if (socketRunning) viewModel.stopSocketService() else viewModel.startSocketService()
                },
                onToggleWebServer = {
                    if (latestRfidHttpRunning) viewModel.stopLatestRfidHttpServer()
                    else viewModel.startLatestRfidHttpServer()
                }
            )
            AdmsLogPanel(admsLog = admsLog)
            Spacer(
                modifier = Modifier
                    .height(12.dp)
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun EhazirLogo() {
    Image(
        painter = painterResource(id = R.drawable.ehazir_logo),
        contentDescription = "eHazir",
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(width = 340.dp, height = 108.dp)
    )
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
private fun GradeAttendanceTable(
    totalCounts: AttendanceSummaryCounts,
    gradeAttendance: List<GradeAttendanceSummary>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GradeAttendanceTableHeader()
        GradeAttendanceTotalRow(totalCounts)
        gradeAttendance.forEachIndexed { index, summary ->
            GradeAttendanceTableRow(
                summary = summary,
                isAlternate = index % 2 == 1
            )
        }
    }
}

@Composable
private fun GradeAttendanceTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TableText("Grade", Modifier.weight(1.4f), isHeader = true)
        TableText("Sec", Modifier.weight(0.8f), isHeader = true)
        TableText("P", Modifier.weight(0.7f), isHeader = true, textAlign = TextAlign.End)
        TableText("A", Modifier.weight(0.7f), isHeader = true, textAlign = TextAlign.End)
        TableText("H", Modifier.weight(0.7f), isHeader = true, textAlign = TextAlign.End)
        TableText("L", Modifier.weight(0.7f), isHeader = true, textAlign = TextAlign.End)
        TableText("Total", Modifier.weight(0.9f), isHeader = true, textAlign = TextAlign.End)
    }
}

@Composable
private fun GradeAttendanceTotalRow(
    counts: AttendanceSummaryCounts
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TableText("Total", Modifier.weight(1.4f), isEmphasis = true)
        TableText("-", Modifier.weight(0.8f), isEmphasis = true)
        TableText(counts.present.toString(), Modifier.weight(0.7f), isEmphasis = true, textAlign = TextAlign.End)
        TableText(counts.absent.toString(), Modifier.weight(0.7f), isEmphasis = true, textAlign = TextAlign.End)
        TableText(counts.half.toString(), Modifier.weight(0.7f), isEmphasis = true, textAlign = TextAlign.End)
        TableText(counts.leave.toString(), Modifier.weight(0.7f), isEmphasis = true, textAlign = TextAlign.End)
        TableText(counts.total.toString(), Modifier.weight(0.9f), isEmphasis = true, textAlign = TextAlign.End)
    }
}

@Composable
private fun GradeAttendanceTableRow(
    summary: GradeAttendanceSummary,
    isAlternate: Boolean
) {
    val counts = summary.counts
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isAlternate) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                },
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        TableText(summary.gradeName, Modifier.weight(1.4f))
        TableText(summary.sectionName.ifBlank { "-" }, Modifier.weight(0.8f))
        TableText(counts.present.toString(), Modifier.weight(0.7f), textAlign = TextAlign.End)
        TableText(counts.absent.toString(), Modifier.weight(0.7f), textAlign = TextAlign.End)
        TableText(counts.half.toString(), Modifier.weight(0.7f), textAlign = TextAlign.End)
        TableText(counts.leave.toString(), Modifier.weight(0.7f), textAlign = TextAlign.End)
        TableText(counts.total.toString(), Modifier.weight(0.9f), textAlign = TextAlign.End)
    }
}

@Composable
private fun TableText(
    text: String,
    modifier: Modifier = Modifier,
    isHeader: Boolean = false,
    isEmphasis: Boolean = false,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        fontSize = 15.sp,
        fontWeight = when {
            isHeader || isEmphasis -> FontWeight.SemiBold
            else -> FontWeight.Normal
        },
        color = when {
            isHeader -> MaterialTheme.colorScheme.onPrimary
            isEmphasis -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        textAlign = textAlign
    )
}

private val AttendanceSummaryCounts.total: Int
    get() = present + absent + half + leave

private fun List<GradeAttendanceSummary>.aggregateCounts(): AttendanceSummaryCounts =
    fold(AttendanceSummaryCounts()) { total, summary ->
        AttendanceSummaryCounts(
            present = total.present + summary.counts.present,
            absent = total.absent + summary.counts.absent,
            half = total.half + summary.counts.half,
            leave = total.leave + summary.counts.leave
        )
}

@Composable
private fun AdmsLogPanel(
    admsLog: List<RfidSocketService.AdmsLogEntry>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "ADMS Log",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Recent data received from the biometric device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            if (admsLog.isEmpty()) {
                Text(
                    "No ADMS requests received yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                admsLog.forEach { entry ->
                    AdmsLogEntryRow(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun AdmsLogEntryRow(
    entry: RfidSocketService.AdmsLogEntry
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${entry.receivedAt}  ${entry.method} ${entry.path}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                entry.remoteAddress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            "Parsed records: ${entry.parsedRecords.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        entry.parsedRecords.forEach { record ->
            Text(
                "userId=${record.userId}, punchTime=${record.punchTime}, inOutMode=${record.inOutMode ?: "-"}, verifyMode=${record.verifyMode ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }

        if (entry.bodyPreview.isNotBlank()) {
            Text(
                "Raw: ${entry.bodyPreview}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "Raw: empty body",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RfidPanel(
    socketRunning: Boolean,
    connectionStatus: Map<String, Boolean>,
    lastScannedRfid: String?,
    latestRfidHttpRunning: Boolean,
    lastAdmsRequest: String?,
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
                        Text(if (latestRfidHttpRunning) "Stop ADMS" else "Start ADMS", fontSize = 12.sp)
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
                text = if (latestRfidHttpRunning) "ADMS server: port 9000 running" else "ADMS server: stopped",
                color = if (latestRfidHttpRunning) Color(0xFF008A6A) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Last ADMS: ${lastAdmsRequest ?: "None"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummarySection(
    title: String,
    counts: AttendanceSummaryCounts,
    isTablet: Boolean,
    modifier: Modifier = Modifier,
    showDetailButton: Boolean = false,
    showDetails: Boolean = false,
    onToggleDetails: () -> Unit = {},
    detailContent: @Composable () -> Unit = {}
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = if (isTablet) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (showDetailButton) {
                    TextButton(onClick = onToggleDetails) {
                        Text(if (showDetails) "Hide detail" else "Show detail")
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatusCard("Present", counts.present.toString(), Color(0xFF1F7A3D), Color(0xFFFFFFFF), isTablet, Modifier.weight(1f))
                StatusCard("Absent", counts.absent.toString(), Color(0xFFC62828), Color(0xFFFFFFFF), isTablet, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatusCard("Half", counts.half.toString(), Color(0xFFB26A00), Color(0xFFFFFFFF), isTablet, Modifier.weight(1f))
                StatusCard("Leave", counts.leave.toString(), Color(0xFFE56B1F), Color(0xFFFFFFFF), isTablet, Modifier.weight(1f))
            }
            if (showDetails) {
                Spacer(modifier = Modifier.height(12.dp))
                detailContent()
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
