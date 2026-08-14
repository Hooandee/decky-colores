package com.hooandee.colores.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hooandee.colores.R
import com.hooandee.colores.control.AppMode

@Composable
fun ModeNav(
    modes: List<AppMode>,
    selected: AppMode,
    enabled: Boolean,
    onModeChange: (AppMode) -> Unit,
) {
    if (modes.size <= 1) return
    val navSelected = if (selected.isSensor) AppMode.BATTERY else selected
    val entries = modes.filterNot { it.isSensor && it != AppMode.BATTERY }
    val outerShape = RoundedCornerShape(20.dp)

    Surface(
        modifier = Modifier.fillMaxWidth().height(58.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.66f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = outerShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEach { mode ->
                ModeNavItem(
                    mode = mode,
                    selected = mode == navSelected,
                    enabled = enabled,
                    onClick = { onModeChange(mode) },
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ModeNavItem(
    mode: AppMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val expanded = selected || focused || hovered
    val weight by
        animateFloatAsState(
            targetValue = if (expanded) 1.56f else 0.82f,
            animationSpec = tween(180),
            label = "mode-nav-width",
        )
    val label = navLabel(mode)
    val shape = RoundedCornerShape(15.dp)
    val contentColor =
        when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f)
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            hovered || focused -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val borderColor =
        when {
            focused -> MaterialTheme.colorScheme.primary
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.44f)
            hovered -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            else -> Color.Transparent
        }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .weight(weight)
                .fillMaxHeight()
                .hoverable(interactionSource = interactionSource, enabled = enabled)
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    contentDescription = label
                    this.selected = selected
                    role = Role.Tab
                },
        color = Color.Transparent,
        contentColor = contentColor,
        shape = shape,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(
                        if (selected) {
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                ),
                            )
                        } else if (hovered || focused) {
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                                ),
                            )
                        } else {
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        },
                    ).border(if (focused) 2.dp else 1.dp, borderColor, shape)
                    .animateContentSize()
                    .padding(horizontal = if (expanded) 11.dp else 8.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeGlyph(mode = mode, tint = contentColor, modifier = Modifier.size(20.dp))
                if (expanded) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeGlyph(
    mode: AppMode,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
            when (mode) {
                AppMode.COLOR -> drawCircle(tint, 10f, Offset(12f, 12f), style = stroke)
                AppMode.GRADIENT -> {
                    drawCircle(tint, 7f, Offset(9f, 9f), style = stroke)
                    drawCircle(tint, 7f, Offset(15f, 15f), style = stroke)
                }
                AppMode.EFFECT -> drawSparkles(tint, stroke)
                AppMode.BATTERY, AppMode.TEMPERATURE, AppMode.PERFORMANCE -> {
                    drawArc(
                        color = tint,
                        startAngle = 150f,
                        sweepAngle = 240f,
                        useCenter = false,
                        topLeft = Offset(2f, 2f),
                        size = Size(20f, 20f),
                        style = stroke,
                    )
                    drawLine(tint, Offset(12f, 14f), Offset(16f, 10f), 2f, StrokeCap.Round)
                }
                AppMode.CLOCK -> {
                    drawCircle(tint, 10f, Offset(12f, 12f), style = stroke)
                    drawLine(tint, Offset(12f, 6f), Offset(12f, 12f), 2f, StrokeCap.Round)
                    drawLine(tint, Offset(12f, 12f), Offset(16f, 14f), 2f, StrokeCap.Round)
                }
                AppMode.AUDIO -> listOf(2f to 3f, 6f to 11f, 10f to 18f, 14f to 7f, 18f to 13f, 22f to 3f).forEach { (x, height) ->
                    drawLine(tint, Offset(x, 12f - height / 2f), Offset(x, 12f + height / 2f), 2f, StrokeCap.Round)
                }
                AppMode.AMBIENT -> {
                    drawRoundRect(tint, Offset(2f, 7f), Size(20f, 15f), CornerRadius(2f), style = stroke)
                    drawLine(tint, Offset(7f, 2f), Offset(12f, 7f), 2f, StrokeCap.Round)
                    drawLine(tint, Offset(17f, 2f), Offset(12f, 7f), 2f, StrokeCap.Round)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparkles(
    color: Color,
    stroke: Stroke,
) {
    val main =
        Path().apply {
            moveTo(12f, 2f)
            lineTo(14.2f, 9.8f)
            lineTo(22f, 12f)
            lineTo(14.2f, 14.2f)
            lineTo(12f, 22f)
            lineTo(9.8f, 14.2f)
            lineTo(2f, 12f)
            lineTo(9.8f, 9.8f)
            close()
        }
    drawPath(main, color, style = stroke)
    drawLine(color, Offset(20f, 2f), Offset(20f, 6f), 1.7f, StrokeCap.Round)
    drawLine(color, Offset(18f, 4f), Offset(22f, 4f), 1.7f, StrokeCap.Round)
}

@Composable
private fun navLabel(mode: AppMode): String =
    when (mode) {
        AppMode.COLOR -> stringResource(R.string.nav_color)
        AppMode.GRADIENT -> stringResource(R.string.nav_gradient)
        AppMode.EFFECT -> stringResource(R.string.nav_effects)
        AppMode.BATTERY, AppMode.TEMPERATURE, AppMode.PERFORMANCE -> stringResource(R.string.nav_sensors)
        AppMode.CLOCK -> stringResource(R.string.nav_clock)
        AppMode.AUDIO -> stringResource(R.string.nav_audio)
        AppMode.AMBIENT -> stringResource(R.string.nav_ambient)
    }
