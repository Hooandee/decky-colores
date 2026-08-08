package com.hooandee.colores.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.hooandee.colores.settings.AppAppearance
import com.hooandee.colores.settings.ThemeMode

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

private val ColoresLightScheme =
    lightColorScheme(
        primary = Color(0xFF574EB8),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE5E0FF),
        onPrimaryContainer = Color(0xFF211B52),
        secondary = Color(0xFF087A6D),
        background = Color(0xFFF7F5FA),
        onBackground = Color(0xFF22202A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF22202A),
        surfaceVariant = Color(0xFFEAE7EF),
        onSurfaceVariant = Color(0xFF625F6B),
        outline = Color(0xFF7A7682),
        error = Color(0xFFBA1A1A),
    )

@Composable
fun ColoresTheme(
    appearance: AppAppearance = AppAppearance(),
    content: @Composable () -> Unit,
) {
    val dark =
        when (appearance.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val roles = accentRoles(appearance.accent, dark)
    val colors =
        (if (dark) ColoresDarkScheme else ColoresLightScheme).copy(
            primary = roles.primary.toComposeColor(),
            onPrimary = roles.onPrimary.toComposeColor(),
            primaryContainer = roles.primaryContainer.toComposeColor(),
            onPrimaryContainer = roles.onPrimaryContainer.toComposeColor(),
        )
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalLedPreviewStyle provides SmokyGlassLedPreviewStyle) {
        MaterialTheme(
            colorScheme = colors,
            content = content,
        )
    }
}
