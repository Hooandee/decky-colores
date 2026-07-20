package com.hooandee.colores.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColoresDarkScheme =
    darkColorScheme(
        primary = Color(0xFF8D83FF),
        onPrimary = Color(0xFF100B3A),
        primaryContainer = Color(0xFF29234E),
        onPrimaryContainer = Color(0xFFE5E0FF),
        secondary = Color(0xFF65D7C6),
        background = Color(0xFF090A0F),
        onBackground = Color(0xFFF2F0F8),
        surface = Color(0xFF121319),
        onSurface = Color(0xFFF2F0F8),
        surfaceVariant = Color(0xFF1A1B23),
        onSurfaceVariant = Color(0xFFB9B7C4),
        outline = Color(0xFF3B3B48),
        error = Color(0xFFFFB4AB),
    )

@Composable
fun ColoresTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColoresDarkScheme,
        content = content,
    )
}
