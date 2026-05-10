package com.ehazir.companion.ui.attendance

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ehazir.companion.data.local.AttendanceEntity
import com.ehazir.companion.data.preferences.AppPreferences
import com.ehazir.companion.data.repository.AttendanceRepository
import com.ehazir.companion.data.repository.Result
import com.ehazir.companion.service.RfidSocketService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class AttendanceState(
    /** RFID / Student number — only field entered manually */
    val rfid: String = "",
    /** Optional override for check_out time; null = use current time */
    val checkOutOverride: String? = null,
    /** Whether the check_out time picker dialog is showing */
    val showTimePicker: Boolean = false,
    val message: String? = null,
    val isSubmitting: Boolean = false
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val repository: AttendanceRepository,
    private val prefs: AppPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(AttendanceState())
    val state: StateFlow<AttendanceState> = _state.asStateFlow()

    val recentRecords = repository.getRecentRecords()
    val pendingCount  = repository.getPendingCount()

    // Socket service observables
    val socketRunning = RfidSocketService.isRunning
    val connectionStatus = RfidSocketService.connectionStatus
    val lastScannedRfid = RfidSocketService.lastScannedRfid

    fun updateRfid(id: String) = _state.update { it.copy(rfid = id) }

    fun setCheckOutOverride(time: String?) = _state.update { it.copy(checkOutOverride = time, showTimePicker = false) }
    fun showTimePicker() = _state.update { it.copy(showTimePicker = true) }
    fun dismissTimePicker() = _state.update { it.copy(showTimePicker = false) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun submitAttendance() {
        val rfid = _state.value.rfid.trim()
        if (rfid.isEmpty() || _state.value.isSubmitting) return

        _state.update { it.copy(isSubmitting = true) }

        val now        = Date()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val nowTime    = timeFormat.format(now)

        val record = AttendanceEntity(
            rfid     = rfid,
            date     = dateFormat.format(now),     // auto: today's date
            checkIn  = nowTime,                     // auto: current time
            checkOut = _state.value.checkOutOverride ?: nowTime, // optional override
            isSynced = 0
        )

        viewModelScope.launch {
            repository.insertAttendance(record)
            _state.update {
                it.copy(
                    rfid             = "",   // clear input for next scan
                    checkOutOverride = null,
                    isSubmitting     = false,
                    message          = "✅ Attendance recorded: $rfid"
                )
            }
            syncNow()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            when (val result = repository.syncPendingRecords()) {
                is Result.Success -> {
                    if (result.data > 0)
                        _state.update { it.copy(message = "☁️ Synced ${result.data} records") }
                }
                is Result.Error -> {
                    _state.update { it.copy(message = result.message) }
                }
            }
        }
    }

    // --- Socket Service control ---

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
}
