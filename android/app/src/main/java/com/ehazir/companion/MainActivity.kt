package com.ehazir.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ehazir.companion.ui.navigation.AppNavGraph
import com.ehazir.companion.ui.theme.EhazirTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EhazirTheme {
                AppNavGraph()
            }
        }
    }
}
