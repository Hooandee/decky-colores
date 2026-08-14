package com.hooandee.colores.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Immutable
internal data class PrismaticStyle(
    val backgroundStart: Color,
    val backgroundMiddle: Color,
    val backgroundEnd: Color,
    val glowCool: Color,
    val glowWarm: Color,
    val atmosphericBeam: Color,
    val vignette: Color,
    val panelSurface: Color,
    val panelSurfaceStrong: Color,
    val panelOutline: Color,
    val panelOutlineStrong: Color,
    val panelSpecular: Color,
    val panelLowerEdge: Color,
    val accentGlow: Color,
)

internal val DarkPrismaticStyle =
    PrismaticStyle(
        backgroundStart = Color(0xFF07111A),
        backgroundMiddle = Color(0xFF080D16),
        backgroundEnd = Color(0xFF05070C),
        glowCool = Color(0x5C266B8F),
        glowWarm = Color(0x3D6F526F),
        atmosphericBeam = Color(0x143EE2FF),
        vignette = Color(0xB8000205),
        panelSurface = Color(0xC9141D27),
        panelSurfaceStrong = Color(0xD51A2530),
        panelOutline = Color(0x32E8F7FF),
        panelOutlineStrong = Color(0x46F2FBFF),
        panelSpecular = Color(0xB5FFFFFF),
        panelLowerEdge = Color(0x66000000),
        accentGlow = Color(0x528FD8F7),
    )

internal val LightPrismaticStyle =
    PrismaticStyle(
        backgroundStart = Color(0xFFEAF5FA),
        backgroundMiddle = Color(0xFFF3F4F8),
        backgroundEnd = Color(0xFFE9E5EF),
        glowCool = Color(0x7077C5E8),
        glowWarm = Color(0x4FBB8FB7),
        atmosphericBeam = Color(0x24FFFFFF),
        vignette = Color(0x26708293),
        panelSurface = Color(0xE8F7FAFC),
        panelSurfaceStrong = Color(0xF4FBFCFD),
        panelOutline = Color(0x3871899A),
        panelOutlineStrong = Color(0x5271899A),
        panelSpecular = Color(0xFFFFFFFF),
        panelLowerEdge = Color(0x36708293),
        accentGlow = Color(0x45176A8C),
    )

internal val LocalPrismaticStyle = staticCompositionLocalOf { DarkPrismaticStyle }

@Composable
internal fun PrismaticBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = LocalPrismaticStyle.current
    Surface(
        modifier = modifier,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val base =
                            Brush.linearGradient(
                                colors = listOf(style.backgroundStart, style.backgroundMiddle, style.backgroundEnd),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height),
                            )
                        val cool =
                            Brush.radialGradient(
                                colors = listOf(style.glowCool, Color.Transparent),
                                center = Offset(size.width * 0.04f, size.height * 0.02f),
                                radius = size.maxDimension * 0.58f,
                            )
                        val warm =
                            Brush.radialGradient(
                                colors = listOf(style.glowWarm, Color.Transparent),
                                center = Offset(size.width * 0.95f, size.height * 0.9f),
                                radius = size.maxDimension * 0.52f,
                            )
                        val accentRefraction =
                            Brush.radialGradient(
                                colors = listOf(style.accentGlow.copy(alpha = style.accentGlow.alpha * 0.58f), Color.Transparent),
                                center = Offset(size.width * 0.72f, -size.height * 0.08f),
                                radius = size.maxDimension * 0.38f,
                            )
                        val beam =
                            Brush.linearGradient(
                                colorStops =
                                    arrayOf(
                                        0f to Color.Transparent,
                                        0.42f to Color.Transparent,
                                        0.5f to style.atmosphericBeam,
                                        0.56f to Color.Transparent,
                                        1f to Color.Transparent,
                                    ),
                                start = Offset(-size.width * 0.1f, size.height),
                                end = Offset(size.width * 0.88f, 0f),
                            )
                        val vignette =
                            Brush.radialGradient(
                                colors = listOf(Color.Transparent, style.vignette),
                                center = Offset(size.width * 0.5f, size.height * 0.46f),
                                radius = size.maxDimension * 0.76f,
                            )
                        onDrawBehind {
                            drawRect(base)
                            drawRect(cool)
                            drawRect(warm)
                            drawRect(accentRefraction)
                            drawRect(beam)
                            drawRect(vignette)
                            drawLine(
                                brush = Brush.linearGradient(listOf(Color.Transparent, style.panelSpecular.copy(alpha = 0.11f), Color.Transparent)),
                                start = Offset(size.width * 0.18f, size.height * 0.02f),
                                end = Offset(size.width * 0.83f, size.height * 0.92f),
                                strokeWidth = 0.7.dp.toPx(),
                            )
                        }
                    },
            content = content,
        )
    }
}

@Composable
internal fun Modifier.prismaticPanel(
    shape: Shape = RoundedCornerShape(28.dp),
    strong: Boolean = false,
): Modifier {
    val style = LocalPrismaticStyle.current
    val surfaceColor = if (strong) style.panelSurfaceStrong else style.panelSurface
    val outlineColor = if (strong) style.panelOutlineStrong else style.panelOutline
    val lightSurface = style.backgroundMiddle.luminance() > 0.5f
    val elevation = if (lightSurface) 3.dp else if (strong) 16.dp else 8.dp
    val shadowAlpha = if (lightSurface) 0.12f else if (strong) 0.38f else 0.26f
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = shadowAlpha),
            spotColor = Color.Black.copy(alpha = shadowAlpha * 0.72f),
        )
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val glassEdge =
                Brush.linearGradient(
                    colorStops =
                        arrayOf(
                            0f to style.panelSpecular.copy(alpha = if (strong) 0.68f else 0.5f),
                            0.16f to outlineColor,
                            0.72f to outlineColor.copy(alpha = outlineColor.alpha * 0.72f),
                            1f to style.panelLowerEdge,
                        ),
                    start = Offset(size.width * 0.5f, 0f),
                    end = Offset(size.width * 0.5f, size.height),
                )
            onDrawBehind {
                drawOutline(outline, color = surfaceColor)
                drawOutline(
                    outline,
                    color = outlineColor.copy(alpha = outlineColor.alpha * if (lightSurface) 0.28f else 0.42f),
                    style = Stroke(width = if (lightSurface) 1.15.dp.toPx() else 1.8.dp.toPx()),
                )
                drawOutline(outline, brush = glassEdge, style = Stroke(width = if (lightSurface) 0.6.dp.toPx() else 0.75.dp.toPx()))
            }
        }
}
