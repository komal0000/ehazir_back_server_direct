package com.ehazir.companion.ui.dashboard

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ehazir.companion.data.preferences.AppPreferences
import com.ehazir.companion.service.RfidSocketService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val prefs: AppPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

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
