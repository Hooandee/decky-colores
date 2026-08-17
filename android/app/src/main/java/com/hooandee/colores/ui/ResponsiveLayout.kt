package com.hooandee.colores.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalCompactDashboard = staticCompositionLocalOf { false }

internal fun isUsableLandscape(
    width: Dp,
    height: Dp,
): Boolean = width >= 560.dp && width > height

internal fun shouldUseCompactDashboardDensity(
    width: Dp,
    height: Dp,
): Boolean = isUsableLandscape(width, height) && height < 480.dp

internal fun shouldUseTwoPaneLayout(
    width: Dp,
    height: Dp,
    expandedWidth: Dp,
): Boolean = width >= expandedWidth || isUsableLandscape(width, height)

internal fun previewRingDiameter(
    availableWidth: Dp,
    groupCount: Int,
    spacing: Dp,
    preferredDiameter: Dp,
): Dp {
    if (groupCount <= 0) return preferredDiameter
    val totalSpacing = spacing * (groupCount - 1)
    return ((availableWidth - totalSpacing) / groupCount).coerceIn(40.dp, preferredDiameter)
}
