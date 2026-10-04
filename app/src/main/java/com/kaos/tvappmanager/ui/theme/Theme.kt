package com.kaos.tvappmanager.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TvColorScheme = darkColorScheme(
    primary = Color(0xFF12B76A),
    onPrimary = Color(0xFF06281A),
    secondary = Color(0xFF8AA4B8),
    background = Color(0xFF0B1F33),
    onBackground = Color(0xFFF2F6F9),
    surface = Color(0xFF152B42),
    onSurface = Color(0xFFF2F6F9),
    surfaceVariant = Color(0xFF2A3A4A),
    onSurfaceVariant = Color(0xFFC3D2DE),
    error = Color(0xFFFF6B6B),
)

@Composable
fun TvAppManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TvColorScheme, content = content)
}
