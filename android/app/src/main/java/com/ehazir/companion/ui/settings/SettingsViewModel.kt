package com.ehazir.companion.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ehazir.companion.data.preferences.AppPreferences
import com.ehazir.companion.data.preferences.ConfigManager
import com.ehazir.companion.data.preferences.DeviceConfig
import com.ehazir.companion.data.preferences.DomainUtils
import com.ehazir.companion.data.preferences.RfidReaderConfig
import com.ehazir.companion.service.RfidSocketService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val deviceId: String = "",
    val localToken: String = "",
    val rfidReaders: List<RfidReaderConfig> = listOf(RfidReaderConfig()),
    val mainDomain: String = "",
    val mainProtocol: String = "https",
    val deviceUpdateTime: String = "30",
    val isLatestRfidHttpEnabled: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val configManager: ConfigManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadConfig()
        observeLatestRfidHttpEnabled()
    }

    private fun loadConfig() {
        // Try JSON first (source of truth), fall back to DataStore
        val config = configManager.load()
        if (config != null) {
            _state.update {
                it.copy(
                    deviceId         = config.deviceId,
                    localToken       = config.localToken,
                    rfidReaders      = config.configuredReaders()
                        .ifEmpty { listOf(RfidReaderConfig()) },
                    mainDomain       = DomainUtils.toTenant(config.mainDomain),
                    mainProtocol     = config.mainProtocol,
                    deviceUpdateTime = config.deviceUpdateTime
                )
            }
        } else {
            viewModelScope.launch {
                val saved = prefs.getFullConfig()
                _state.update {
                    it.copy(
                        deviceId         = saved.deviceId,
                        localToken       = saved.localToken,
                        rfidReaders      = saved.configuredReaders()
                            .ifEmpty { listOf(RfidReaderConfig()) },
                        mainDomain       = DomainUtils.toTenant(saved.mainDomain),
                        mainProtocol     = saved.mainProtocol,
                        deviceUpdateTime = saved.deviceUpdateTime
                    )
                }
            }
        }
    }

    // Generic field update called from the UI
    fun update(block: (SettingsState) -> SettingsState) = _state.update(block)

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun addRfidReader() = _state.update {
        it.copy(rfidReaders = it.rfidReaders + RfidReaderConfig())
    }

    fun removeRfidReader(index: Int) = _state.update {
        if (it.rfidReaders.size <= 1) it
        else it.copy(rfidReaders = it.rfidReaders.filterIndexed { i, _ -> i != index })
    }

    fun updateRfidReader(index: Int, reader: RfidReaderConfig) = _state.update {
        it.copy(rfidReaders = it.rfidReaders.mapIndexed { i, current ->
            if (i == index) reader else current
        })
    }

    private fun observeLatestRfidHttpEnabled() {
        viewModelScope.launch {
            prefs.isLatestRfidHttpEnabled.collect { enabled ->
                _state.update { it.copy(isLatestRfidHttpEnabled = enabled) }
            }
        }
    }

    fun saveSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val s = _state.value
            val readers = s.rfidReaders.map {
                it.copy(host = it.host.trim(), port = it.port.trim())
            }
            if (readers.any { it.host.isBlank() || it.port.toIntOrNull() !in 1..65535 }) {
                _state.update { it.copy(message = "Each RFID reader needs a host and a valid port") }
                return@launch
            }
            if (readers.distinctBy { "${it.host.lowercase()}:${it.port}" }.size != readers.size) {
                _state.update { it.copy(message = "Duplicate RFID reader host and port") }
                return@launch
            }
            val normalizedDomain = DomainUtils.normalizeTenantDomain(s.mainDomain)
            if (!DomainUtils.validateDomainExists(normalizedDomain)) {
                _state.update { it.copy(message = "Domain does not exist: $normalizedDomain") }
                return@launch
            }

            val config = DeviceConfig(
                localToken       = s.localToken,
                deviceId         = s.deviceId,
                serverHost       = readers.first().host,
                serverPort       = readers.first().port,
                rfidReaders      = readers,
                mainDomain       = normalizedDomain,
                mainProtocol     = s.mainProtocol,
                deviceUpdateTime = s.deviceUpdateTime
            )
            configManager.save(config)
            prefs.saveFullConfig(config)
            _state.update { it.copy(message = "Settings saved ✅") }
        }
    }

    fun unregister(onSuccess: () -> Unit) {
        viewModelScope.launch {
            // Stop socket service if running
            stopSocketService()
            configManager.delete()
            prefs.clearRegistration()
            onSuccess()
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

    fun startLatestRfidHttpServer() {
        viewModelScope.launch {
            prefs.setLatestRfidHttpEnabled(true)
            _state.update {
                it.copy(
                    isLatestRfidHttpEnabled = true,
                    message = "HTTP ADMS server enabled on port ${RfidSocketService.LATEST_RFID_HTTP_PORT}"
                )
            }
            val intent = Intent(appContext, RfidSocketService::class.java).apply {
                action = RfidSocketService.ACTION_START_LATEST_RFID_HTTP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }
    }

    fun stopLatestRfidHttpServer() {
        viewModelScope.launch {
            prefs.setLatestRfidHttpEnabled(false)
            _state.update {
                it.copy(
                    isLatestRfidHttpEnabled = false,
                    message = "HTTP ADMS server disabled"
                )
            }
            if (RfidSocketService.isRunning.value) {
                val intent = Intent(appContext, RfidSocketService::class.java).apply {
                    action = RfidSocketService.ACTION_STOP_LATEST_RFID_HTTP
                }
                appContext.startService(intent)
            }
        }
    }
}
