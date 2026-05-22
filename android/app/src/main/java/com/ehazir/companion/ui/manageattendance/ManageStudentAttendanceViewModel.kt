package com.ehazir.companion.ui.manageattendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ehazir.companion.data.preferences.AppPreferences
import com.ehazir.companion.data.preferences.StaffCacheManager
import com.ehazir.companion.data.remote.EhazirApiService
import com.ehazir.companion.data.remote.GradeDto
import com.ehazir.companion.data.remote.StaffListRequest
import com.ehazir.companion.data.remote.StaffDto
import com.ehazir.companion.data.remote.StaffSectionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageStudentAttendanceState(
    val staffs: List<StaffDto> = emptyList(),
    val grades: List<GradeDto> = emptyList(),
    val staffQuery: String = "",
    val selectedStaffId: Int? = null,
    val selectedGradeId: Int? = null,
    val selectedSectionId: Int? = null,
    val password: String = "",
    val isLoading: Boolean = false,
    val loadedFromCache: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class ManageStudentAttendanceViewModel @Inject constructor(
    private val api: EhazirApiService,
    private val prefs: AppPreferences,
    private val staffCacheManager: StaffCacheManager
) : ViewModel() {

    private val _state = MutableStateFlow(ManageStudentAttendanceState())
    val state: StateFlow<ManageStudentAttendanceState> = _state.asStateFlow()

    init {
        loadStaffData()
    }

    fun loadStaffData() {
        val cached = staffCacheManager.load()
        if (cached != null) {
            _state.update {
                it.copy(
                    staffs = cached.staffs,
                    grades = cached.grades,
                    loadedFromCache = true
                )
            }
        } else {
            refreshStaffData()
        }
    }

    fun refreshStaffData() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, message = null) }
            try {
                val token = prefs.localToken.first()
                val deviceId = prefs.deviceId.first()

                if (token.isBlank() || deviceId.isBlank()) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = "Token or device id missing. Please check registration/settings."
                        )
                    }
                    return@launch
                }

                val response = api.getStaffList(
                    StaffListRequest(
                        token = token,
                        deviceId = deviceId,
                        provideGrade = true
                    )
                )
                val body = response.body()
                val data = body?.data

                if (response.isSuccessful && body?.status == true && data != null) {
                    staffCacheManager.save(data)
                    _state.update {
                        it.copy(
                            staffs = data.staffs,
                            grades = data.grades,
                            selectedStaffId = null,
                            selectedGradeId = null,
                            selectedSectionId = null,
                            staffQuery = "",
                            loadedFromCache = false,
                            isLoading = false,
                            message = body.msg ?: "Staff data updated"
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = body?.msg ?: "Unable to load staff data"
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        message = e.message ?: "Unable to load staff data"
                    )
                }
            }
        }
    }

    fun updateStaffQuery(query: String) {
        _state.update { it.copy(staffQuery = query) }
    }

    fun selectStaff(staff: StaffDto) {
        _state.update {
            it.copy(
                selectedStaffId = staff.id,
                selectedGradeId = null,
                selectedSectionId = null,
                staffQuery = "${staff.name} (${staff.id})"
            )
        }
    }

    fun selectGrade(gradeId: Int) {
        _state.update {
            it.copy(
                selectedGradeId = gradeId,
                selectedSectionId = null
            )
        }
    }

    fun selectSection(sectionId: Int) {
        _state.update { it.copy(selectedSectionId = sectionId) }
    }

    fun updatePassword(password: String) {
        _state.update { it.copy(password = password) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun loadData() {
        val current = _state.value
        val message = when {
            current.selectedStaffId == null -> "Select staff"
            current.selectedGradeId == null -> "Select grade"
            current.selectedSectionId == null -> "Select section"
            current.password.isBlank() -> "Enter password"
            else -> "Selection ready. Student attendance loading will be added next."
        }
        _state.update { it.copy(message = message) }
    }

    fun permittedGrades(): List<StaffGradeOption> {
        val current = _state.value
        val staff = current.staffs.firstOrNull { it.id == current.selectedStaffId } ?: return emptyList()
        return staff.sections
            .distinctBy { it.gradeId }
            .map { StaffGradeOption(id = it.gradeId, name = it.gradeName) }
    }

    fun permittedSections(): List<StaffSectionDto> {
        val current = _state.value
        val staff = current.staffs.firstOrNull { it.id == current.selectedStaffId } ?: return emptyList()
        val gradeId = current.selectedGradeId ?: return emptyList()
        return staff.sections
            .filter { it.gradeId == gradeId }
            .distinctBy { it.sectionId }
    }
}

data class StaffGradeOption(
    val id: Int,
    val name: String
)
