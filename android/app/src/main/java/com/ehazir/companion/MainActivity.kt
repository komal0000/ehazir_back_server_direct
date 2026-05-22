package com.ehazir.companion

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.ehazir.companion.data.preferences.AppPreferences
import com.ehazir.companion.service.RfidSocketService
import com.ehazir.companion.ui.navigation.AppNavGraph
import com.ehazir.companion.ui.theme.EhazirTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        restoreRfidServiceState()
        setContent {
            EhazirTheme {
                AppNavGraph()
            }
        }
    }

    private fun restoreRfidServiceState() {
        lifecycleScope.launch(Dispatchers.IO) {
            val serviceEnabled = prefs.isServiceEnabled.first()
            val latestRfidHttpEnabled = prefs.isLatestRfidHttpEnabled.first()
            if (!serviceEnabled && !latestRfidHttpEnabled) return@launch

            val intent = if (latestRfidHttpEnabled) {
                Intent(this@MainActivity, RfidSocketService::class.java).apply {
                    action = RfidSocketService.ACTION_START_LATEST_RFID_HTTP
                }
            } else {
                Intent(this@MainActivity, RfidSocketService::class.java)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
}
