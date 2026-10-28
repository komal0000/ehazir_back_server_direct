package com.ehazir.companion.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ehazir.companion.data.preferences.AppPreferences
import com.ehazir.companion.data.preferences.ConfigManager
import com.ehazir.companion.data.preferences.DeviceConfig
import com.ehazir.companion.data.preferences.DomainUtils
import com.ehazir.companion.data.preferences.RfidReaderConfig
import com.ehazir.companion.data.remote.EhazirApiService
import com.ehazir.companion.data.remote.RegisterRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import javax.inject.Inject

data class RegisterState(
    // Step 1 - Fetch token via Telegram
    val isFetchingToken: Boolean = false,
    val tokenInstructions: String = "", // message returned by ehazircloud URL
    val tokenFetched: Boolean = false,

    // Step 2 - Config fields
    val localToken: String = "",       // user pastes received Telegram token here
    val deviceId: String = "",         // auto-generated, shown read-only
    val rfidReaders: List<RfidReaderConfig> = listOf(RfidReaderConfig()),
    val mainDomain: String = "",       // e.g. demo.ehazircloud.com
    val mainProtocol: String = "https",
    val deviceUpdateTime: String = "30",

    // Flow state
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val configManager: ConfigManager,
    private val apiService: EhazirApiService
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterState())
    val state: StateFlow<RegisterState> = _state.asStateFlow()

    init {
        // If a config already exists load pre-fill data for editing
        val existing = configManager.load()
        val autoDeviceId = existing?.deviceId ?: generateDeviceId()
        _state.update {
            it.copy(
                deviceId         = autoDeviceId,
                rfidReaders      = existing?.configuredReaders().orEmpty()
                    .ifEmpty { listOf(RfidReaderConfig()) },
                mainDomain       = DomainUtils.toTenant(existing?.mainDomain ?: ""),
                mainProtocol     = existing?.mainProtocol ?: "https",
                deviceUpdateTime = existing?.deviceUpdateTime ?: "30"
            )
        }
    }

    private fun generateDeviceId(): String =
        UUID.randomUUID().toString().replace("-", "").take(16)

    /** Step 1: Hit ehazircloud to trigger Telegram token delivery */
    fun fetchToken() {
        if (_state.value.isFetchingToken) return
        _state.update { it.copy(isFetchingToken = true, error = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://files.ehazircloud.com/ask.php?ask=token")
                    .build()
                val body = client.newCall(request).execute().use { it.body?.string()?.trim() ?: "" }
                // The server either returns a message/link or empty — show it to the user
                val msg = if (body.isBlank()) {
                    "A token has been sent to your Telegram. Please paste it below."
                } else {
                    body
                }
                _state.update {
                    it.copy(isFetchingToken = false, tokenInstructions = msg, tokenFetched = true)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isFetchingToken = false, error = "Failed to reach eHazir server: ${e.message}")
                }
            }
        }
    }

    fun updateLocalToken(v: String)       = _state.update { it.copy(localToken = v) }
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
    fun updateMainDomain(v: String)       = _state.update { it.copy(mainDomain = v) }
    fun updateMainProtocol(v: String)     = _state.update { it.copy(mainProtocol = v) }
    fun updateDeviceUpdateTime(v: String) = _state.update { it.copy(deviceUpdateTime = v) }
    fun clearError()                      = _state.update { it.copy(error = null) }

    /** Step 2: Save config to JSON + DataStore, then register with server */
    fun submitConfig() {
        if (_state.value.isSubmitting) return
        val s = _state.value
        if (s.localToken.isBlank() || s.mainDomain.isBlank()) {
            _state.update { it.copy(error = "Token and Server Domain are required") }
            return
        }
        val readers = s.rfidReaders.map {
            it.copy(host = it.host.trim(), port = it.port.trim())
        }
        if (readers.any { it.host.isBlank() || it.port.toIntOrNull() !in 1..65535 }) {
            _state.update { it.copy(error = "Each RFID reader needs a host and a valid port") }
            return
        }
        if (readers.distinctBy { "${it.host.lowercase()}:${it.port}" }.size != readers.size) {
            _state.update { it.copy(error = "Duplicate RFID reader host and port") }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch(Dispatchers.IO) {
            val normalizedDomain = DomainUtils.normalizeTenantDomain(s.mainDomain)
            if (!DomainUtils.validateDomainExists(normalizedDomain)) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        error = "Domain does not exist: $normalizedDomain"
                    )
                }
                return@launch
            }

            val config = DeviceConfig(
                localToken       = s.localToken.trim(),
                deviceId         = s.deviceId,
                serverHost       = readers.first().host,
                serverPort       = readers.first().port,
                rfidReaders      = readers,
                mainDomain       = normalizedDomain,
                mainProtocol     = s.mainProtocol.trim(),
                deviceUpdateTime = s.deviceUpdateTime.trim()
            )

            // 1. Persist to JSON file
            configManager.save(config)

            // 2. Persist to DataStore (for reactive UI flows)
            prefs.saveFullConfig(config)

            // 3. Register device with backend
            try {
                val response = apiService.registerDevice(
                    RegisterRequest(
                        token       = s.localToken.trim(),
                        localToken  = s.localToken.trim(),
                        deviceId    = s.deviceId
                    )
                )
                if (response.isSuccessful) {
                    _state.update { it.copy(isSubmitting = false, isSuccess = true) }
                } else {
                    // Even if server returns error, we still saved locally.
                    // Some setups may not have the register endpoint, so treat non-200 as a warning.
                    val errBody = response.errorBody()?.string() ?: ""
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            // Proceed anyway — config is saved locally
                            isSuccess = true,
                            error = if (errBody.isNotBlank()) "Warning: $errBody" else null
                        )
                    }
                }
            } catch (e: Exception) {
                // Network unavailable — still proceed since config is saved locally
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        isSuccess = true,
                        error = "Saved locally. Server unreachable: ${e.message}"
                    )
                }
            }
        }
    }
}
