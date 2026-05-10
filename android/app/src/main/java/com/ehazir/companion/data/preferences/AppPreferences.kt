package com.ehazir.companion.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ehazir_prefs")

data class DeviceConfig(
    val localToken: String = "",
    val deviceId: String = "",
    val serverHost: String = "",
    val serverPort: String = "2022",
    val mainDomain: String = "",
    val mainProtocol: String = "https",
    val deviceUpdateTime: String = "30"
)

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val LOCAL_TOKEN       = stringPreferencesKey("local_token")
        val DEVICE_ID         = stringPreferencesKey("device_id")
        val SERVER_HOST       = stringPreferencesKey("server_host")
        val SERVER_PORT       = stringPreferencesKey("server_port")
        val MAIN_DOMAIN       = stringPreferencesKey("main_domain")
        val MAIN_PROTOCOL     = stringPreferencesKey("main_protocol")
        val DEVICE_UPDATE_TIME = stringPreferencesKey("device_update_time")
        val IS_REGISTERED     = stringPreferencesKey("is_registered")
        val IS_SERVICE_ENABLED = stringPreferencesKey("is_service_enabled")
    }

    val localToken: Flow<String>        = context.dataStore.data.map { it[LOCAL_TOKEN]       ?: "" }
    val deviceId: Flow<String>          = context.dataStore.data.map { it[DEVICE_ID]         ?: "" }
    val serverHost: Flow<String>        = context.dataStore.data.map { it[SERVER_HOST]       ?: "" }
    val serverPort: Flow<String>        = context.dataStore.data.map { it[SERVER_PORT]       ?: "2022" }
    val mainDomain: Flow<String>        = context.dataStore.data.map { it[MAIN_DOMAIN]       ?: "" }
    val mainProtocol: Flow<String>      = context.dataStore.data.map { it[MAIN_PROTOCOL]     ?: "https" }
    val deviceUpdateTime: Flow<String>  = context.dataStore.data.map { it[DEVICE_UPDATE_TIME]?: "30" }
    val isRegistered: Flow<Boolean>     = context.dataStore.data.map { it[IS_REGISTERED]     == "true" }
    val isServiceEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_SERVICE_ENABLED] == "true" }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_SERVICE_ENABLED] = if (enabled) "true" else "false"
        }
    }

    // Convenience: the full server URL assembled from mainProtocol + mainDomain
    val serverUrl: Flow<String> = context.dataStore.data.map {
        val proto  = it[MAIN_PROTOCOL] ?: "https"
        val domain = it[MAIN_DOMAIN]   ?: ""
        if (domain.isBlank()) "" else "$proto://$domain"
    }

    suspend fun saveFullConfig(config: DeviceConfig) {
        context.dataStore.edit { prefs ->
            prefs[LOCAL_TOKEN]        = config.localToken
            prefs[DEVICE_ID]          = config.deviceId
            prefs[SERVER_HOST]        = config.serverHost
            prefs[SERVER_PORT]        = config.serverPort
            prefs[MAIN_DOMAIN]        = config.mainDomain
            prefs[MAIN_PROTOCOL]      = config.mainProtocol
            prefs[DEVICE_UPDATE_TIME] = config.deviceUpdateTime
            prefs[IS_REGISTERED]      = "true"
        }
    }

    suspend fun getFullConfig(): DeviceConfig {
        val prefs = context.dataStore.data.first()
        return DeviceConfig(
            localToken       = prefs[LOCAL_TOKEN]        ?: "",
            deviceId         = prefs[DEVICE_ID]          ?: "",
            serverHost       = prefs[SERVER_HOST]        ?: "",
            serverPort       = prefs[SERVER_PORT]        ?: "2022",
            mainDomain       = prefs[MAIN_DOMAIN]        ?: "",
            mainProtocol     = prefs[MAIN_PROTOCOL]      ?: "https",
            deviceUpdateTime = prefs[DEVICE_UPDATE_TIME] ?: "30"
        )
    }

    suspend fun clearRegistration() {
        context.dataStore.edit { prefs ->
            prefs[LOCAL_TOKEN]    = ""
            prefs[DEVICE_ID]      = ""
            prefs[IS_REGISTERED]  = "false"
        }
    }
}

