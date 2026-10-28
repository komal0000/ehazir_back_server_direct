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
import com.ehazir.companion.data.remote.EhazirApiService
import com.ehazir.companion.data.remote.StaffAttendanceRecord
import com.ehazir.companion.data.remote.StaffAttendanceRequest
import com.ehazir.companion.data.repository.AttendanceRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
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
        private const val ADMS_BATCH_INTERVAL_MS = 60000L
        private const val ADMS_DEFAULT_BUFFER_MINUTES = 30L
        const val LATEST_RFID_HTTP_PORT = 9000
        const val ACTION_START_LATEST_RFID_HTTP =
            "com.ehazir.companion.action.START_LATEST_RFID_HTTP"
        const val ACTION_STOP_LATEST_RFID_HTTP =
            "com.ehazir.companion.action.STOP_LATEST_RFID_HTTP"

        // Observable state for the UI — set from the service instance
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _connectionStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
        val connectionStatus: StateFlow<Map<String, Boolean>> = _connectionStatus.asStateFlow()

        private val _lastScannedRfid = MutableStateFlow<String?>(null)
        val lastScannedRfid: StateFlow<String?> = _lastScannedRfid.asStateFlow()

        private val _isLatestRfidHttpRunning = MutableStateFlow(false)
        val isLatestRfidHttpRunning: StateFlow<Boolean> = _isLatestRfidHttpRunning.asStateFlow()

        private val _latestRfidHttpError = MutableStateFlow<String?>(null)
        val latestRfidHttpError: StateFlow<String?> = _latestRfidHttpError.asStateFlow()

        private val _lastAdmsRequest = MutableStateFlow<String?>(null)
        val lastAdmsRequest: StateFlow<String?> = _lastAdmsRequest.asStateFlow()

        data class AdmsLogEntry(
            val receivedAt: String,
            val method: String,
            val path: String,
            val remoteAddress: String,
            val bodyPreview: String,
            val parsedRecords: List<StaffAttendanceRecord>
        )

        private const val ADMS_LOG_LIMIT = 20
        private val admsLogLock = Any()
        private val _admsLog = MutableStateFlow<List<AdmsLogEntry>>(emptyList())
        val admsLog: StateFlow<List<AdmsLogEntry>> = _admsLog.asStateFlow()
    }

    @Inject lateinit var dao: AttendanceDao
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var repository: AttendanceRepository
    @Inject lateinit var apiService: EhazirApiService

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socketClients = mutableListOf<RfidSocketClient>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var latestRfidHttpJob: Job? = null
    private var latestRfidHttpServerSocket: ServerSocket? = null
    private var admsFlushJob: Job? = null

    // In-memory attendance cache keyed by RFID (same pattern as main.js)
    private data class CachedRecord(
        val rfid: String,
        val date: String,
        val intime: String,
        var outtime: String,
        var isNew: Boolean,
        var updated: Boolean
    )

    private val attendanceCache = ConcurrentHashMap<String, CachedRecord>()
    private var deviceUpdateTime = 30 // seconds

    private data class AdmsCachedRecord(
        val record: StaffAttendanceRecord,
        val timestamp: Long,
        var sent: Boolean
    )

    private data class HttpRequest(
        val method: String,
        val path: String,
        val body: String
    )

    private val admsAttendanceCache = ConcurrentHashMap<String, AdmsCachedRecord>()
    private var admsBufferTimeMs = ADMS_DEFAULT_BUFFER_MINUTES * 60 * 1000

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
                val readers = prefs.rfidReaders.first()
                
                val deviceUpdateTimeStr = prefs.deviceUpdateTime.first()
                deviceUpdateTime = deviceUpdateTimeStr.toIntOrNull() ?: 30
                admsBufferTimeMs = (deviceUpdateTimeStr.toLongOrNull() ?: ADMS_DEFAULT_BUFFER_MINUTES) * 60 * 1000
                val latestRfidHttpEnabled = prefs.isLatestRfidHttpEnabled.first()
                if (latestRfidHttpEnabled) {
                    startLatestRfidHttpServer()
                }
                
                if (readers.isEmpty()) {
                    Log.e(TAG, "No server hosts configured")
                    if (latestRfidHttpEnabled) {
                        updateNotification("Latest RFID HTTP active; no reader configured")
                    } else {
                        updateNotification("Error: No server hosts configured")
                        stopSelf()
                    }
                    return@launch
                }

                // Load today's attendance from DB into the cache
                loadAttendanceCache()

                // Start socket clients for each host
                val statusMap = mutableMapOf<String, Boolean>()
                for (reader in readers) {
                    val port = reader.port.toIntOrNull()
                    if (reader.host.isBlank() || port == null || port !in 1..65535) {
                        Log.e(TAG, "Skipping invalid RFID reader: ${reader.host}:${reader.port}")
                        continue
                    }
                    val endpoint = "${reader.host}:$port"
                    Log.d(TAG, "Starting socket client for $endpoint")
                    val client = RfidSocketClient(reader.host, port, this@RfidSocketService)
                    client.start()
                    socketClients.add(client)
                    statusMap[endpoint] = false
                }
                _connectionStatus.value = statusMap

                // Start periodic cache flush (every 1 second)
                mainHandler.postDelayed(flushRunnable, FLUSH_INTERVAL_MS)

                updateNotification("Connecting to ${statusMap.size} reader(s)...")

            } catch (e: Exception) {
                Log.e(TAG, "Error starting service: ${e.message}")
                updateNotification("Error: ${e.message}")
                stopSelf()
            }
        }
    }

    // --- Latest RFID HTTP server ---

    @Synchronized
    private fun startLatestRfidHttpServer() {
        if (latestRfidHttpJob?.isActive == true) {
            _latestRfidHttpError.value = null
            return
        }

        latestRfidHttpJob = serviceScope.launch {
            try {
                val serverSocket = ServerSocket(LATEST_RFID_HTTP_PORT)
                latestRfidHttpServerSocket = serverSocket
                _latestRfidHttpError.value = null
                _isLatestRfidHttpRunning.value = true
                startAdmsFlushLoop()
                Log.d(
                    TAG,
                    "HTTP server listening on port $LATEST_RFID_HTTP_PORT; ADMS buffer=${admsBufferTimeMs}ms"
                )

                while (isActive && !serverSocket.isClosed) {
                    val client = try {
                        serverSocket.accept()
                    } catch (e: SocketException) {
                        if (isActive) throw e else break
                    }

                    launch {
                        handleLatestRfidHttpClient(client)
                    }
                }
            } catch (e: IOException) {
                if (isActive) {
                    val message = e.message ?: "Unable to start HTTP server"
                    Log.e(TAG, "Latest RFID HTTP server error: $message", e)
                    _latestRfidHttpError.value = message
                }
            } finally {
                admsFlushJob?.cancel()
                admsFlushJob = null
                runCatching { latestRfidHttpServerSocket?.close() }
                latestRfidHttpServerSocket = null
                _isLatestRfidHttpRunning.value = false
            }
        }
    }

    @Synchronized
    private fun stopLatestRfidHttpServer() {
        latestRfidHttpJob?.cancel()
        latestRfidHttpJob = null
        admsFlushJob?.cancel()
        admsFlushJob = null
        runCatching { latestRfidHttpServerSocket?.close() }
        latestRfidHttpServerSocket = null
        _isLatestRfidHttpRunning.value = false
        _latestRfidHttpError.value = null
        Log.d(TAG, "HTTP server stopped")
    }

    private fun handleLatestRfidHttpClient(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 2000

            val request = readHttpRequest(socket)
            val remoteAddress = socket.inetAddress?.hostAddress.orEmpty()
            if (request != null) {
                val preview = request.body.take(500).replace("\n", "\\n")
                val message = "${request.method} ${request.path} from $remoteAddress body=\"$preview\""
                _lastAdmsRequest.value = message
                Log.d(TAG, "ADMS request: $message")
            } else {
                val message = "Invalid HTTP request from $remoteAddress"
                _lastAdmsRequest.value = message
                Log.w(TAG, message)
            }

            val output = socket.getOutputStream()
            if (request?.method?.equals("OPTIONS", ignoreCase = true) == true) {
                appendAdmsLog(request, remoteAddress, emptyList())
                val response = "HTTP/1.1 204 No Content\r\n" +
                    commonCorsHeaders() +
                    "Connection: close\r\n" +
                    "Content-Length: 0\r\n\r\n"
                output.write(response.toByteArray(Charsets.UTF_8))
                output.flush()
                return
            }

            if (request?.path == "/iclock/getrequest" || request?.path == "/iclock/registry") {
                appendAdmsLog(request, remoteAddress, emptyList())
                writeTextResponse(output, "OK")
                return
            }

            if (request?.path == "/iclock/cdata") {
                val records = parseAdmsAttendance(request.body)
                appendAdmsLog(request, remoteAddress, records)
                records.forEach(::cacheAdmsAttendance)
                Log.d(TAG, "Received ${records.size} ADMS attendance record(s)")
                writeTextResponse(output, "OK")
                return
            }

            if (request != null) {
                appendAdmsLog(request, remoteAddress, emptyList())
            }

            val latestRfid = _lastScannedRfid.value?.takeIf { it.isNotBlank() }
            val body = latestRfidJsonBody(latestRfid).toByteArray(Charsets.UTF_8)
            val headers = "HTTP/1.1 200 OK\r\n" +
                commonCorsHeaders() +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
            output.write(headers.toByteArray(Charsets.UTF_8))
            output.write(body)
            output.flush()
        }
    }

    private fun appendAdmsLog(
        request: HttpRequest,
        remoteAddress: String,
        parsedRecords: List<StaffAttendanceRecord>
    ) {
        val entry = AdmsLogEntry(
            receivedAt = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
            method = request.method,
            path = request.path,
            remoteAddress = remoteAddress,
            bodyPreview = request.body.take(1000),
            parsedRecords = parsedRecords
        )
        synchronized(admsLogLock) {
            _admsLog.value = (listOf(entry) + _admsLog.value).take(ADMS_LOG_LIMIT)
        }
    }

    private fun readHttpRequest(socket: Socket): HttpRequest? {
        return runCatching {
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            val requestLine = reader.readLine() ?: return@runCatching null
            val requestParts = requestLine.split(" ")
            val method = requestParts.getOrNull(0).orEmpty()
            val path = requestParts.getOrNull(1).orEmpty().substringBefore("?")

            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator <= 0) continue
                val name = line.substring(0, separator).trim()
                if (name.equals("Content-Length", ignoreCase = true)) {
                    contentLength = line.substring(separator + 1).trim().toIntOrNull() ?: 0
                }
            }

            val body = if (contentLength > 0) {
                val chars = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = reader.read(chars, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                String(chars, 0, totalRead)
            } else {
                ""
            }

            HttpRequest(method = method, path = path, body = body)
        }.getOrElse { e ->
            Log.e(TAG, "HTTP request parse error: ${e.message}", e)
            null
        }
    }

    private fun writeTextResponse(output: java.io.OutputStream, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 200 OK\r\n" +
            commonCorsHeaders() +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun latestRfidJsonBody(rfid: String?): String {
        val escapedRfid = rfid?.replace("\\", "\\\\")?.replace("\"", "\\\"")
        return if (escapedRfid == null) {
            """{"data":null,"status":true}"""
        } else {
            """{"data":"$escapedRfid","status":true}"""
        }
    }

    private fun commonCorsHeaders(): String =
        "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: *\r\n" +
            "Access-Control-Max-Age: 86400\r\n"

    private fun parseAdmsAttendance(raw: String): List<StaffAttendanceRecord> {
        if (raw.isBlank()) return emptyList()

        return raw
            .trim()
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val cols = line.split("\t")
                val userId = cols.getOrNull(0)?.trim().orEmpty()
                val punchTime = cols.getOrNull(1)?.trim().orEmpty()
                if (userId.isBlank() || punchTime.isBlank()) {
                    null
                } else {
                    StaffAttendanceRecord(
                        userId = userId,
                        punchTime = punchTime,
                        inOutMode = cols.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() },
                        verifyMode = cols.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() }
                    )
                }
            }
            .toList()
    }

    private fun cacheAdmsAttendance(record: StaffAttendanceRecord) {
        val now = System.currentTimeMillis()
        val cached = admsAttendanceCache[record.userId]

        if (cached != null && now - cached.timestamp < admsBufferTimeMs) {
            Log.d(
                TAG,
                "Duplicate ADMS attendance ignored for user ${record.userId} within ${(now - cached.timestamp) / 1000}s"
            )
            return
        }

        admsAttendanceCache[record.userId] = AdmsCachedRecord(
            record = record,
            timestamp = now,
            sent = false
        )
        Log.d(TAG, "Cached ADMS attendance for user ${record.userId}")
    }

    @Synchronized
    private fun startAdmsFlushLoop() {
        if (admsFlushJob?.isActive == true) return

        admsFlushJob = serviceScope.launch {
            while (isActive) {
                delay(ADMS_BATCH_INTERVAL_MS)
                flushAdmsAttendanceCache()
            }
        }
    }

    private suspend fun flushAdmsAttendanceCache() {
        val now = System.currentTimeMillis()
        val unsent = admsAttendanceCache.values
            .filter { !it.sent }
            .map { it.record }

        if (unsent.isNotEmpty()) {
            Log.d(TAG, "Sending ${unsent.size} ADMS attendance record(s)")
            try {
                val response = apiService.submitStaffAttendance(
                    StaffAttendanceRequest(records = unsent)
                )
                if (response.isSuccessful) {
                    admsAttendanceCache.values.forEach { cached ->
                        if (!cached.sent) cached.sent = true
                    }
                } else {
                    Log.e(
                        TAG,
                        "ADMS backend error: ${response.errorBody()?.string() ?: response.code().toString()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "ADMS backend error: ${e.message}", e)
            }
        }

        admsAttendanceCache.entries.removeIf { entry ->
            now - entry.value.timestamp >= admsBufferTimeMs
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
            val cacheKey = "${record.rfid}|${record.date}"
            attendanceCache[cacheKey] = CachedRecord(
                rfid = record.rfid,
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

        val cacheKey = "$rfid|$date"
        val existing = attendanceCache[cacheKey]

        if (existing != null) {
            // Check if enough time has passed to update outtime
            val timeDiffSeconds = computeTimeDiffSeconds(
                "${existing.date} ${existing.outtime}",
                "$date $time"
            )
            if (timeDiffSeconds > deviceUpdateTime) {
                synchronized(existing) {
                    existing.outtime = time
                    existing.updated = true
                }
            }
            return
        }

        // It is a new record for this (rfid, date)
        // Find and force-flush any older cached records for this rfid (RFID date transition)
        val prefix = "$rfid|"
        val oldKeys = attendanceCache.keys().asSequence().filter { it.startsWith(prefix) && it != cacheKey }.toList()
        if (oldKeys.isNotEmpty()) {
            serviceScope.launch {
                forceFlushAndRemove(oldKeys)
            }
        }

        // Create new cache record
        attendanceCache[cacheKey] = CachedRecord(
            rfid = rfid,
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

    private suspend fun flushSingleRecord(record: CachedRecord): Boolean {
        val isNewToFlush: Boolean
        val isUpdatedToFlush: Boolean
        synchronized(record) {
            isNewToFlush = record.isNew
            isUpdatedToFlush = record.updated
            if (isNewToFlush) {
                record.isNew = false
            }
            if (isUpdatedToFlush) {
                record.updated = false
            }
        }

        var flushed = false
        if (isNewToFlush) {
            dao.insertIgnore(
                AttendanceEntity(
                    rfid = record.rfid,
                    date = record.date,
                    checkIn = record.intime,
                    checkOut = record.outtime,
                    isSynced = 0
                )
            )
            flushed = true
        } else if (isUpdatedToFlush) {
            val existing = dao.findByDateAndRfid(record.date, record.rfid)
            if (existing != null) {
                dao.updateCheckOut(existing.id, record.outtime)
            }
            flushed = true
        }
        return flushed
    }

    private suspend fun forceFlushAndRemove(keys: List<String>) {
        var flushedAny = false
        for (key in keys) {
            val record = attendanceCache[key]
            if (record != null) {
                if (flushSingleRecord(record)) {
                    flushedAny = true
                }
                attendanceCache.remove(key)
            }
        }
        if (flushedAny) {
            Log.d(TAG, "Force-flushed and removed old records for keys: $keys")
            try {
                repository.syncPendingRecords()
            } catch (e: Exception) {
                Log.e(TAG, "Sync error: ${e.message}")
            }
        }
    }

    // --- Cache flush (mirrors main.js flushCache) ---

    private suspend fun flushCache() {
        if (attendanceCache.isEmpty()) return

        var flushedCount = 0

        for ((_, record) in attendanceCache) {
            if (flushSingleRecord(record)) {
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

        // Stop Latest RFID HTTP server
        stopLatestRfidHttpServer()

        // Final flush
        runBlocking {
            flushCache()
            flushAdmsAttendanceCache()
        }

        serviceScope.cancel()

        _isRunning.value = false
        _connectionStatus.value = emptyMap()
        _lastScannedRfid.value = null
        _latestRfidHttpError.value = null

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_LATEST_RFID_HTTP -> {
                serviceScope.launch {
                    prefs.setLatestRfidHttpEnabled(true)
                    startLatestRfidHttpServer()
                }
            }
            ACTION_STOP_LATEST_RFID_HTTP -> {
                serviceScope.launch {
                    prefs.setLatestRfidHttpEnabled(false)
                    stopLatestRfidHttpServer()
                }
            }
        }
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
