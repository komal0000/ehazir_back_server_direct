package com.ehazir.companion.data.preferences

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves and loads the full device configuration as a JSON file
 * in the app's private internal storage.
 *
 * File location: /data/data/com.ehazir.companion/files/device_config.json
 * (Not accessible outside the app without root — safe for tokens.)
 */
@Singleton
class ConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val configFile: File
        get() = File(context.filesDir, "device_config.json")

    fun save(config: DeviceConfig) {
        val readers = config.configuredReaders()
        val firstReader = readers.firstOrNull() ?: RfidReaderConfig()
        configFile.writeText(
            gson.toJson(
                config.copy(
                    serverHost = firstReader.host,
                    serverPort = firstReader.port,
                    rfidReaders = readers
                )
            )
        )
    }

    fun load(): DeviceConfig? {
        return try {
            if (configFile.exists()) {
                gson.fromJson(configFile.readText(), DeviceConfig::class.java)
            } else null
        } catch (e: JsonSyntaxException) {
            null
        }
    }

    fun exists(): Boolean = configFile.exists()

    fun delete() {
        configFile.delete()
    }
}
