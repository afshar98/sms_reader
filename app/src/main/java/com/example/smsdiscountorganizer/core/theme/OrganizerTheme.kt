package com.example.smsdiscountorganizer.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B58),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9DF2D7),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4A6359),
    background = Color(0xFFF6F8F5),
    surface = Color(0xFFF6F8F5),
    surfaceVariant = Color(0xFFE2E9E4),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81D5BB),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF00513F),
    onPrimaryContainer = Color(0xFF9DF2D7),
    secondary = Color(0xFFB3CCBF),
    background = Color(0xFF101512),
    surface = Color(0xFF101512),
    surfaceVariant = Color(0xFF3F4944),
    error = Color(0xFFFFB4AB),
)

@Composable
fun OrganizerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
