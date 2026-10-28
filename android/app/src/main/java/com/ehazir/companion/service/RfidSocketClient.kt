package com.ehazir.companion.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TCP socket client that connects to a single RFID reader device.
 * Ported from ANDROID_JAVA_IMPLEMENTATION.md RFIDSocketClient.java
 *
 * Connects → sends init command → reads hex data → extracts 24-char RFID
 * → notifies listener → auto-reconnects on disconnect.
 */
class RfidSocketClient(
    private val serverHost: String,
    private val serverPort: Int,
    private val listener: RfidDataListener
) : Thread("RfidSocket-$serverHost") {
    private val endpoint = "$serverHost:$serverPort"

    companion object {
        private const val TAG = "RfidSocketClient"
        private const val INIT_HEX = "cfff00720017a5"
        private const val RECONNECT_DELAY_MS = 5000L
        private const val SOCKET_TIMEOUT_MS = 60000
    }

    interface RfidDataListener {
        fun onRfidReceived(rfid: String, date: String, time: String, host: String)
        fun onConnectionStatusChanged(connected: Boolean, host: String)
    }

    @Volatile
    private var isRunning = false
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun run() {
        isRunning = true
        connectLoop()
    }

    private fun connectLoop() {
        while (isRunning) {
            try {
                Log.d(TAG, "Connecting to $serverHost:$serverPort")

                socket = Socket(serverHost, serverPort).apply {
                    keepAlive = true
                    soTimeout = SOCKET_TIMEOUT_MS
                }
                inputStream = socket!!.getInputStream()
                outputStream = socket!!.getOutputStream()

                // Send init command
                val initCommand = hexStringToByteArray(INIT_HEX)
                outputStream!!.write(initCommand)
                outputStream!!.flush()

                Log.d(TAG, "Connected to $serverHost, init sent: $INIT_HEX")
                notifyConnectionStatus(true)

                // Read data loop
                val buffer = ByteArray(1024)
                var bytesRead: Int

                while (isRunning) {
                    bytesRead = inputStream!!.read(buffer)
                    if (bytesRead == -1) break // Server closed connection
                    processReceivedData(buffer, bytesRead)
                }

            } catch (e: IOException) {
                Log.e(TAG, "Connection error for $serverHost: ${e.message}")
                notifyConnectionStatus(false)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error for $serverHost: ${e.message}")
                notifyConnectionStatus(false)
            } finally {
                closeConnection()

                if (isRunning) {
                    Log.d(TAG, "Reconnecting to $serverHost in ${RECONNECT_DELAY_MS / 1000}s...")
                    try {
                        sleep(RECONNECT_DELAY_MS)
                    } catch (_: InterruptedException) {
                        // Interrupted during sleep — check isRunning
                    }
                }
            }
        }
    }

    private fun processReceivedData(data: ByteArray, length: Int) {
        val hexMessage = bytesToHex(data, length)

        // Extract RFID: remove last 4 hex digits, take last 24 characters
        if (hexMessage.length >= 28) {
            val processedHex = hexMessage.dropLast(4)
            if (processedHex.length >= 24) {
                val rfid = processedHex.takeLast(24)

                val now = Date()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val currentDate = dateFormat.format(now)
                val currentTime = timeFormat.format(now)

                Log.d(TAG, "RFID from $serverHost: $rfid")
                listener.onRfidReceived(rfid, currentDate, currentTime, endpoint)
            }
        }
    }

    private fun notifyConnectionStatus(connected: Boolean) {
        mainHandler.post {
            listener.onConnectionStatusChanged(connected, endpoint)
        }
    }

    fun stopClient() {
        isRunning = false
        closeConnection()
        interrupt()
    }

    private fun closeConnection() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.takeIf { !it.isClosed }?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing connection for $serverHost: ${e.message}")
        }
        inputStream = null
        outputStream = null
        socket = null
    }

    // --- Hex utilities ---

    private fun bytesToHex(bytes: ByteArray, length: Int): String {
        val sb = StringBuilder(length * 2)
        for (i in 0 until length) {
            sb.append(String.format("%02x", bytes[i]))
        }
        return sb.toString()
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
}
