package com.santosxd.diario.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9D7BFF),
    onPrimary = Color(0xFF0B0A12),
    secondary = Color(0xFF5B8CFF),
    onSecondary = Color(0xFF0B0A12),
    background = Color(0xFF0B0A12),
    onBackground = Color(0xFFECEAF6),
    surface = Color(0xFF14121F),
    onSurface = Color(0xFFECEAF6),
    surfaceVariant = Color(0xFF1D1A2E),
    onSurfaceVariant = Color(0xFFA9A5C4),
    error = Color(0xFFFF6B8A),
    onError = Color(0xFF0B0A12),
    outline = Color(0xFF4A4466)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D4AE0),
    onPrimary = Color.White,
    secondary = Color(0xFF2F5FD8),
    onSecondary = Color.White,
    background = Color(0xFFF7F6FC),
    onBackground = Color(0xFF1A1730),
    surface = Color.White,
    onSurface = Color(0xFF1A1730),
    surfaceVariant = Color(0xFFEDEAF8),
    onSurfaceVariant = Color(0xFF55507A),
    error = Color(0xFFC2214A),
    onError = Color.White,
    outline = Color(0xFF8C86B0)
)

/** mode: "dark" (por defecto), "light" o "system". */
@Composable
fun DiaryTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
