package com.ehazir.companion.ui.manageattendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ehazir.companion.data.remote.StaffDto
import com.ehazir.companion.data.remote.StaffSectionDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageStudentAttendanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManageStudentAttendanceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val permittedGrades = viewModel.permittedGrades()
    val permittedSections = viewModel.permittedSections()
    val selectedGrade = permittedGrades.firstOrNull { it.id == state.selectedGradeId }
    val selectedSection = permittedSections.firstOrNull { it.sectionId == state.selectedSectionId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Student Attendance") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refreshStaffData,
                        enabled = !state.isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Update Cache")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Staff Selection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        state.isLoading -> "Loading staff data..."
                        state.loadedFromCache -> "Loaded from cache"
                        state.staffs.isNotEmpty() -> "Loaded from server"
                        else -> "No staff data loaded"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            StaffSearchDropdown(
                staffs = state.staffs,
                query = state.staffQuery,
                isLoading = state.isLoading,
                onQueryChange = viewModel::updateStaffQuery,
                onStaffSelected = viewModel::selectStaff
            )

            GradeDropdown(
                grades = permittedGrades,
                selectedGrade = selectedGrade,
                enabled = state.selectedStaffId != null,
                onGradeSelected = { viewModel.selectGrade(it.id) }
            )

            SectionDropdown(
                sections = permittedSections,
                selectedSection = selectedSection,
                enabled = state.selectedGradeId != null,
                onSectionSelected = { viewModel.selectSection(it.sectionId) }
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::updatePassword,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = viewModel::loadData,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load Data")
            }

            OutlinedButton(
                onClick = viewModel::refreshStaffData,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text("Update Cache", modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffSearchDropdown(
    staffs: List<StaffDto>,
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onStaffSelected: (StaffDto) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredStaffs = remember(staffs, query) {
        val search = query.trim()
        if (search.isBlank()) {
            staffs
        } else {
            staffs.filter {
                it.name.contains(search, ignoreCase = true) ||
                    it.id.toString().contains(search)
            }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                expanded = true
            },
            enabled = !isLoading,
            label = { Text("Staff") },
            placeholder = { Text("Search staff by name or id") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp)
        ) {
            if (filteredStaffs.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No staff found") },
                    enabled = false,
                    onClick = {}
                )
            } else {
                filteredStaffs.forEach { staff ->
                    DropdownMenuItem(
                        text = { Text("${staff.name} (${staff.id})") },
                        onClick = {
                            onStaffSelected(staff)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeDropdown(
    grades: List<StaffGradeOption>,
    selectedGrade: StaffGradeOption?,
    enabled: Boolean,
    onGradeSelected: (StaffGradeOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedGrade?.name.orEmpty(),
            onValueChange = {},
            enabled = enabled,
            readOnly = true,
            label = { Text("Grade") },
            placeholder = { Text("Select staff first") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp)
        ) {
            if (grades.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No permitted grades") },
                    enabled = false,
                    onClick = {}
                )
            } else {
                grades.forEach { grade ->
                    DropdownMenuItem(
                        text = { Text(grade.name) },
                        onClick = {
                            onGradeSelected(grade)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionDropdown(
    sections: List<StaffSectionDto>,
    selectedSection: StaffSectionDto?,
    enabled: Boolean,
    onSectionSelected: (StaffSectionDto) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedSection?.sectionName.orEmpty(),
            onValueChange = {},
            enabled = enabled,
            readOnly = true,
            label = { Text("Section") },
            placeholder = { Text("Select grade first") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp)
        ) {
            if (sections.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No permitted sections") },
                    enabled = false,
                    onClick = {}
                )
            } else {
                sections.forEach { section ->
                    DropdownMenuItem(
                        text = { Text(section.sectionName) },
                        onClick = {
                            onSectionSelected(section)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
