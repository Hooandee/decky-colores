package com.hooandee.colores.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

internal fun stackedPanesNeedPaging(height: Dp): Boolean = height < 600.dp

@Composable
internal fun StackedPanes(
    maxHeight: Dp,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    firstWeight: Float = 1f,
) {
    if (stackedPanesNeedPaging(maxHeight)) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val paneHeight = maxHeight * 0.94f
            first(Modifier.fillMaxWidth().height(paneHeight))
            second(Modifier.fillMaxWidth().height(paneHeight))
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            first(Modifier.fillMaxWidth().weight(firstWeight))
            second(Modifier.fillMaxWidth().weight(2f - firstWeight))
        }
    }
}
