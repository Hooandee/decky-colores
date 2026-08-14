package com.hooandee.colores.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.hooandee.colores.R
import com.hooandee.colores.settings.AppAppearance
import com.hooandee.colores.settings.ThemeMode

private val ColoresDarkScheme =
    darkColorScheme(
        primary = Color(0xFF8FD8F7),
        onPrimary = Color(0xFF042633),
        primaryContainer = Color(0xFF203C52),
        onPrimaryContainer = Color(0xFFD8F2FF),
        secondary = Color(0xFF9C8CFF),
        onSecondary = Color(0xFF1B104C),
        secondaryContainer = Color(0xFF30285C),
        onSecondaryContainer = Color(0xFFE7E0FF),
        background = Color(0xFF070B12),
        onBackground = Color(0xFFF4F8FB),
        surface = Color(0xFF111A25),
        onSurface = Color(0xFFF4F8FB),
        surfaceVariant = Color(0xFF182431),
        onSurfaceVariant = Color(0xFFB8C8D2),
        surfaceContainer = Color(0xFF121D28),
        surfaceContainerHigh = Color(0xFF192633),
        outline = Color(0xFF60717E),
        outlineVariant = Color(0xFF344653),
        error = Color(0xFFFFB4AB),
    )

private val ColoresLightScheme =
    lightColorScheme(
        primary = Color(0xFF176A8C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC8ECFB),
        onPrimaryContainer = Color(0xFF073548),
        secondary = Color(0xFF6254B6),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE5DFFF),
        onSecondaryContainer = Color(0xFF2A2164),
        background = Color(0xFFF1F8FC),
        onBackground = Color(0xFF17232B),
        surface = Color(0xFFF8FCFF),
        onSurface = Color(0xFF17232B),
        surfaceVariant = Color(0xFFE5EEF3),
        onSurfaceVariant = Color(0xFF52616B),
        surfaceContainer = Color(0xFFEDF5F9),
        surfaceContainerHigh = Color(0xFFE4EEF4),
        outline = Color(0xFF6E7F89),
        outlineVariant = Color(0xFFC3D1D9),
        error = Color(0xFFBA1A1A),
    )

private val BarlowFamily =
    FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_medium, FontWeight.Medium),
        Font(R.font.barlow_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_bold, FontWeight.Bold),
    )

private val DefaultTypography = Typography()
private val ColoresTypography =
    Typography(
        displayLarge = DefaultTypography.displayLarge.copy(fontFamily = BarlowFamily),
        displayMedium = DefaultTypography.displayMedium.copy(fontFamily = BarlowFamily),
        displaySmall = DefaultTypography.displaySmall.copy(fontFamily = BarlowFamily),
        headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = BarlowFamily),
        headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = BarlowFamily),
        headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = BarlowFamily),
        titleLarge = DefaultTypography.titleLarge.copy(fontFamily = BarlowFamily),
        titleMedium = DefaultTypography.titleMedium.copy(fontFamily = BarlowFamily),
        titleSmall = DefaultTypography.titleSmall.copy(fontFamily = BarlowFamily),
        bodyLarge = DefaultTypography.bodyLarge.copy(fontFamily = BarlowFamily),
        bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = BarlowFamily),
        bodySmall = DefaultTypography.bodySmall.copy(fontFamily = BarlowFamily),
        labelLarge = DefaultTypography.labelLarge.copy(fontFamily = BarlowFamily),
        labelMedium = DefaultTypography.labelMedium.copy(fontFamily = BarlowFamily),
        labelSmall = DefaultTypography.labelSmall.copy(fontFamily = BarlowFamily),
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
    val atmosphere = atmosphereRoles(appearance.accent, dark)
    val primary = roles.primary.toComposeColor().animatedThemeColor()
    val onPrimary = roles.onPrimary.toComposeColor().animatedThemeColor()
    val primaryContainer = roles.primaryContainer.toComposeColor().animatedThemeColor()
    val onPrimaryContainer = roles.onPrimaryContainer.toComposeColor().animatedThemeColor()
    val backgroundStart = atmosphere.backgroundStart.toComposeColor().animatedThemeColor()
    val backgroundMiddle = atmosphere.backgroundMiddle.toComposeColor().animatedThemeColor()
    val backgroundEnd = atmosphere.backgroundEnd.toComposeColor().animatedThemeColor()
    val coolGlow = atmosphere.coolGlow.toComposeColor().animatedThemeColor()
    val warmGlow = atmosphere.warmGlow.toComposeColor().animatedThemeColor()
    val beam = atmosphere.beam.toComposeColor().animatedThemeColor()
    val panelSurface = atmosphere.panelSurface.toComposeColor().animatedThemeColor()
    val panelSurfaceStrong = atmosphere.panelSurfaceStrong.toComposeColor().animatedThemeColor()
    val panelOutline = atmosphere.panelOutline.toComposeColor().animatedThemeColor()
    val panelOutlineStrong = atmosphere.panelOutlineStrong.toComposeColor().animatedThemeColor()
    val colors =
        (if (dark) ColoresDarkScheme else ColoresLightScheme).copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = primaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            background = backgroundMiddle,
            surface = panelSurface,
            surfaceVariant = panelSurfaceStrong,
            surfaceContainer = panelSurface,
            surfaceContainerHigh = panelSurfaceStrong,
            outline = panelOutline,
            outlineVariant = panelOutlineStrong,
        )
    val prismaticStyle =
        (if (dark) DarkPrismaticStyle else LightPrismaticStyle).copy(
            backgroundStart = backgroundStart,
            backgroundMiddle = backgroundMiddle,
            backgroundEnd = backgroundEnd,
            glowCool = coolGlow.copy(alpha = if (dark) 0.3f else 0.23f),
            glowWarm = warmGlow.copy(alpha = if (dark) 0.19f else 0.16f),
            atmosphericBeam = beam.copy(alpha = if (dark) 0.09f else 0.075f),
            panelSurface = panelSurface.copy(alpha = if (dark) 0.79f else 0.91f),
            panelSurfaceStrong = panelSurfaceStrong.copy(alpha = if (dark) 0.84f else 0.96f),
            panelOutline = panelOutline.copy(alpha = if (dark) 0.2f else 0.16f),
            panelOutlineStrong = panelOutlineStrong.copy(alpha = if (dark) 0.28f else 0.23f),
            accentGlow = primary.copy(alpha = if (dark) 0.32f else 0.23f),
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
    CompositionLocalProvider(
        LocalLedPreviewStyle provides if (dark) SmokyGlassLedPreviewStyle else PearlCeramicLedPreviewStyle,
        LocalPrismaticStyle provides prismaticStyle,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = ColoresTypography,
            content = content,
        )
    }
}

@Composable
private fun Color.animatedThemeColor(): Color =
    animateColorAsState(
        targetValue = this,
        animationSpec = tween(durationMillis = 420),
        label = "theme-atmosphere",
    ).value
