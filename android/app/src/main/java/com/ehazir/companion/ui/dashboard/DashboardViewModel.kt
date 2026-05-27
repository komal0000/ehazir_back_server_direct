package com.ehazir.companion.ui.dashboard

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ehazir.companion.data.preferences.AppPreferences
import com.ehazir.companion.data.remote.EhazirApiService
import com.ehazir.companion.data.remote.TodayAttendanceGradeDto
import com.ehazir.companion.data.remote.TodayAttendanceRequest
import com.ehazir.companion.service.RfidSocketService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AttendanceSummaryCounts(
    val present: Int = 0,
    val absent: Int = 0,
    val half: Int = 0,
    val leave: Int = 0
)

data class DashboardState(
    val studentCounts: AttendanceSummaryCounts = AttendanceSummaryCounts(),
    val teacherCounts: AttendanceSummaryCounts = AttendanceSummaryCounts(),
    val staffCounts: AttendanceSummaryCounts = AttendanceSummaryCounts(),
    val gradeAttendance: List<GradeAttendanceSummary> = emptyList(),
    val isAttendanceSummaryLoading: Boolean = false,
    val attendanceSummaryError: String? = null
)

data class GradeAttendanceSummary(
    val gradeId: Int,
    val gradeName: String,
    val sectionId: Int,
    val sectionName: String,
    val counts: AttendanceSummaryCounts
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val api: EhazirApiService,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()
    private var attendanceSummaryRefreshJob: Job? = null

    init {
        startAttendanceSummaryRefresh()
    }

    private fun startAttendanceSummaryRefresh() {
        attendanceSummaryRefreshJob?.cancel()
        attendanceSummaryRefreshJob = viewModelScope.launch {
            while (isActive) {
                loadAttendanceSummary()
                delay(5_000)
            }
        }
    }

    fun loadAttendanceSummary() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isAttendanceSummaryLoading = true, attendanceSummaryError = null) }
            try {
                val token = prefs.localToken.first()
                val deviceId = prefs.deviceId.first()
                if (token.isBlank() || deviceId.isBlank()) {
                    _state.update {
                        it.copy(
                            isAttendanceSummaryLoading = false,
                            attendanceSummaryError = "Token or device id missing"
                        )
                    }
                    return@launch
                }

                val response = api.getTodayAttendanceData(
                    TodayAttendanceRequest(token = token, deviceId = deviceId)
                )
                val body = response.body()
                val data = body?.data
                if (response.isSuccessful && body?.status == true && data != null) {
                    val studentCounts = data.student.toAttendanceSummaryCounts()
                    val gradeAttendance = data.grades.toGradeAttendanceSummaries(studentCounts)
                    _state.update {
                        it.copy(
                            studentCounts = studentCounts,
                            teacherCounts = data.teacher.toAttendanceSummaryCounts(),
                            staffCounts = data.staff.toAttendanceSummaryCounts(),
                            gradeAttendance = gradeAttendance,
                            isAttendanceSummaryLoading = false,
                            attendanceSummaryError = null
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isAttendanceSummaryLoading = false,
                            attendanceSummaryError = body?.msg?.joinToString().orEmpty()
                                .ifBlank { "Unable to load attendance summary" }
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isAttendanceSummaryLoading = false,
                        attendanceSummaryError = e.message ?: "Unable to load attendance summary"
                    )
                }
            }
        }
    }

    fun startSocketService() {
        viewModelScope.launch { prefs.setServiceEnabled(true) }
        val intent = Intent(appContext, RfidSocketService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    fun stopSocketService() {
        viewModelScope.launch { prefs.setServiceEnabled(false) }
        val intent = Intent(appContext, RfidSocketService::class.java)
        appContext.stopService(intent)
    }

    fun startLatestRfidHttpServer() {
        viewModelScope.launch { prefs.setLatestRfidHttpEnabled(true) }
        val intent = Intent(appContext, RfidSocketService::class.java).apply {
            action = RfidSocketService.ACTION_START_LATEST_RFID_HTTP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    fun stopLatestRfidHttpServer() {
        viewModelScope.launch { prefs.setLatestRfidHttpEnabled(false) }
        if (RfidSocketService.isRunning.value) {
            val intent = Intent(appContext, RfidSocketService::class.java).apply {
                action = RfidSocketService.ACTION_STOP_LATEST_RFID_HTTP
            }
            appContext.startService(intent)
        }
    }
}

private fun Map<String, Int>.toAttendanceSummaryCounts(): AttendanceSummaryCounts =
    AttendanceSummaryCounts(
        present = this["1"] ?: 0,
        absent = this["2"] ?: 0,
        half = this["3"] ?: 0,
        leave = this["4"] ?: 0
    )

private fun List<TodayAttendanceGradeDto>.toGradeAttendanceSummaries(
    studentCounts: AttendanceSummaryCounts
): List<GradeAttendanceSummary> {
    val summaries = flatMap { grade ->
        grade.sections.map { section ->
            GradeAttendanceSummary(
                gradeId = grade.gradeId,
                gradeName = grade.gradeName,
                sectionId = section.sectionId,
                sectionName = section.sectionName,
                counts = AttendanceSummaryCounts(
                    present = section.status1,
                    absent = section.status2,
                    half = section.status3,
                    leave = section.status4
                )
            )
        }
    }
    val totals = summaries.sumCounts()
    val shouldSwapPresentAbsent =
        totals.present == studentCounts.absent &&
            totals.present != studentCounts.present

    return if (shouldSwapPresentAbsent) {
        summaries.map { summary ->
            summary.copy(
                counts = summary.counts.copy(
                    present = summary.counts.absent,
                    absent = summary.counts.present
                )
            )
        }
    } else {
        summaries
    }
}

private fun List<GradeAttendanceSummary>.sumCounts(): AttendanceSummaryCounts =
    fold(AttendanceSummaryCounts()) { total, summary ->
        AttendanceSummaryCounts(
            present = total.present + summary.counts.present,
            absent = total.absent + summary.counts.absent,
            half = total.half + summary.counts.half,
            leave = total.leave + summary.counts.leave
        )
    }
