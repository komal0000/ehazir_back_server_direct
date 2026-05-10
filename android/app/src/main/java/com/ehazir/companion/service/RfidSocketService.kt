package com.ehazir.companion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.ehazir.companion.MainActivity
import com.ehazir.companion.R
import com.ehazir.companion.data.local.AttendanceDao
import com.ehazir.companion.data.local.AttendanceEntity
import com.ehazir.companion.data.preferences.AppPreferences
import com.ehazir.companion.data.repository.AttendanceRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Foreground service that connects to RFID reader(s) via TCP,
 * processes scanned data, stores attendance in Room, and syncs.
 *
 * This is the Kotlin equivalent of the Java AttendanceService from
 * ANDROID_JAVA_IMPLEMENTATION.md, integrated with the existing Hilt/Room stack.
 */
@AndroidEntryPoint
class RfidSocketService : LifecycleService(), RfidSocketClient.RfidDataListener {

    companion object {
        private const val TAG = "RfidSocketService"
        private const val CHANNEL_ID = "rfid_service_channel"
        private const val NOTIFICATION_ID = 1001
        private const val FLUSH_INTERVAL_MS = 1000L

        // Observable state for the UI — set from the service instance
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _connectionStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
        val connectionStatus: StateFlow<Map<String, Boolean>> = _connectionStatus.asStateFlow()

        private val _lastScannedRfid = MutableStateFlow<String?>(null)
        val lastScannedRfid: StateFlow<String?> = _lastScannedRfid.asStateFlow()
    }

    @Inject lateinit var dao: AttendanceDao
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var repository: AttendanceRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socketClients = mutableListOf<RfidSocketClient>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // In-memory attendance cache keyed by RFID (same pattern as main.js)
    private data class CachedRecord(
        val date: String,
        val intime: String,
        var outtime: String,
        var isNew: Boolean,
        var updated: Boolean
    )

    private val attendanceCache = ConcurrentHashMap<String, CachedRecord>()
    private var deviceUpdateTime = 30 // seconds

    private val flushRunnable = object : Runnable {
        override fun run() {
            serviceScope.launch { flushCache() }
            mainHandler.postDelayed(this, FLUSH_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Initializing..."))
        _isRunning.value = true

        serviceScope.launch {
            try {
                // Read config
                val serverHost = prefs.serverHost.first()
                val serverPortStr = prefs.serverPort.first()
                val serverPort = serverPortStr.toIntOrNull() ?: 2022
                
                val deviceUpdateTimeStr = prefs.deviceUpdateTime.first()
                deviceUpdateTime = deviceUpdateTimeStr.toIntOrNull() ?: 30
                
                val hosts = serverHost.split(",").map { it.trim() }.filter { it.isNotBlank() }

                if (hosts.isEmpty()) {
                    Log.e(TAG, "No server hosts configured")
                    updateNotification("Error: No server hosts configured")
                    stopSelf()
                    return@launch
                }

                // Load today's attendance from DB into the cache
                loadAttendanceCache()

                // Start socket clients for each host
                val statusMap = mutableMapOf<String, Boolean>()
                for (host in hosts) {
                    Log.d(TAG, "Starting socket client for $host:$serverPort")
                    val client = RfidSocketClient(host, serverPort, this@RfidSocketService)
                    client.start()
                    socketClients.add(client)
                    statusMap[host] = false
                }
                _connectionStatus.value = statusMap

                // Start periodic cache flush (every 1 second)
                mainHandler.postDelayed(flushRunnable, FLUSH_INTERVAL_MS)

                updateNotification("Connecting to ${hosts.size} reader(s)...")

            } catch (e: Exception) {
                Log.e(TAG, "Error starting service: ${e.message}")
                updateNotification("Error: ${e.message}")
                stopSelf()
            }
        }
    }

    /**
     * Load today's attendance records into the in-memory cache
     * (mirrors main.js loadAttendanceCache)
     */
    private suspend fun loadAttendanceCache() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val records = dao.getTodayRecords(today).first()
        for (record in records) {
            attendanceCache[record.rfid] = CachedRecord(
                date = record.date,
                intime = record.checkIn,
                outtime = record.checkOut,
                isNew = false,
                updated = false
            )
        }
        Log.d(TAG, "Loaded ${attendanceCache.size} cached records for $today")
    }

    // --- RfidDataListener callbacks ---

    override fun onRfidReceived(rfid: String, date: String, time: String, host: String) {
        _lastScannedRfid.value = rfid

        val existing = attendanceCache[rfid]

        if (existing != null) {
            // Check if the date changed (new day)
            if (existing.date != date) {
                attendanceCache.clear()
                // Fall through to create new record
            } else {
                // Check if enough time has passed to update outtime
                val timeDiffSeconds = computeTimeDiffSeconds(
                    "${existing.date} ${existing.outtime}",
                    "$date $time"
                )
                if (timeDiffSeconds > deviceUpdateTime) {
                    existing.outtime = time
                    existing.updated = true
                }
                return
            }
        }

        // New RFID for today
        attendanceCache[rfid] = CachedRecord(
            date = date,
            intime = time,
            outtime = time,
            isNew = true,
            updated = false
        )
    }

    override fun onConnectionStatusChanged(connected: Boolean, host: String) {
        val updated = _connectionStatus.value.toMutableMap()
        updated[host] = connected
        _connectionStatus.value = updated

        val connectedCount = updated.values.count { it }
        val total = updated.size
        updateNotification("$connectedCount/$total reader(s) connected")
    }

    // --- Cache flush (mirrors main.js flushCache) ---

    private suspend fun flushCache() {
        if (attendanceCache.isEmpty()) return

        var flushedCount = 0

        for ((rfid, record) in attendanceCache) {
            if (record.isNew) {
                dao.insertIgnore(
                    AttendanceEntity(
                        rfid = rfid,
                        date = record.date,
                        checkIn = record.intime,
                        checkOut = record.outtime,
                        isSynced = 0
                    )
                )
                record.isNew = false
                record.updated = false
                flushedCount++
            } else if (record.updated) {
                val existing = dao.findByDateAndRfid(record.date, rfid)
                if (existing != null) {
                    dao.updateCheckOut(existing.id, record.outtime)
                }
                record.updated = false
                flushedCount++
            }
        }

        if (flushedCount > 0) {
            Log.d(TAG, "Flushed $flushedCount records to DB")
            // Trigger sync after flush (mirrors main.js behavior)
            try {
                repository.syncPendingRecords()
            } catch (e: Exception) {
                Log.e(TAG, "Sync error: ${e.message}")
            }
        }
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RFID Socket Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when eHazir is listening for RFID scans"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("eHazir — RFID Listener")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    // --- Lifecycle ---

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")

        // Stop all socket clients
        for (client in socketClients) {
            client.stopClient()
        }
        socketClients.clear()

        // Stop periodic flush
        mainHandler.removeCallbacksAndMessages(null)

        // Final flush
        runBlocking {
            flushCache()
        }

        serviceScope.cancel()

        _isRunning.value = false
        _connectionStatus.value = emptyMap()
        _lastScannedRfid.value = null

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    // --- Utility ---

    private fun computeTimeDiffSeconds(dateTime1: String, dateTime2: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val t1 = sdf.parse(dateTime1)?.time ?: 0L
            val t2 = sdf.parse(dateTime2)?.time ?: 0L
            (t2 - t1) / 1000
        } catch (e: Exception) {
            0L
        }
    }
}
