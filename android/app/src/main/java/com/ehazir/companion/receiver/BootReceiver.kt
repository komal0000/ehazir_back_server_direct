package com.ehazir.companion.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ehazir.companion.data.preferences.AppPreferences
import com.ehazir.companion.service.RfidSocketService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receiver to start the RfidSocketService on device boot, app update, 
 * or user present, provided the user had previously enabled the service.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prefs: AppPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_USER_PRESENT) {

            Log.d("BootReceiver", "Received intent: ${intent.action}")

            // We must goAsync since DataStore reads are suspending
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val isServiceEnabled = prefs.isServiceEnabled.first()
                    val isLatestRfidHttpEnabled = prefs.isLatestRfidHttpEnabled.first()
                    Log.d(
                        "BootReceiver",
                        "isServiceEnabled=$isServiceEnabled, isLatestRfidHttpEnabled=$isLatestRfidHttpEnabled"
                    )
                    if (isServiceEnabled || isLatestRfidHttpEnabled) {
                        Log.d("BootReceiver", "Starting RfidSocketService...")
                        val serviceIntent = Intent(context, RfidSocketService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error reading prefs or starting service", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
