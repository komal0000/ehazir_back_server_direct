package com.ehazir.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6C63FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D3591),
    onPrimaryContainer = Color(0xFFE3DFFF),
    secondary = Color(0xFF00D4AA),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004D3B),
    onSecondaryContainer = Color(0xFFB8F0E0),
    background = Color(0xFF0F0F1A),
    onBackground = Color(0xFFE8E8F0),
    surface = Color(0xFF1A1A2E),
    onSurface = Color(0xFFE8E8F0),
    surfaceVariant = Color(0xFF252540),
    onSurfaceVariant = Color(0xFFBBBBD0),
    error = Color(0xFFFF6B6B),
    outline = Color(0xFF444466)
)

@Composable
fun EhazirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
