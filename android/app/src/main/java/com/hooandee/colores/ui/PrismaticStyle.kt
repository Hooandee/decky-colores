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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.random.Random

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
    val accentFill: Color,
    val grain: Float,
) {
    val light: Boolean get() = backgroundMiddle.luminance() > 0.5f
}

internal val DarkPrismaticStyle =
    PrismaticStyle(
        backgroundStart = Color(0xFF07111A),
        backgroundMiddle = Color(0xFF080D16),
        backgroundEnd = Color(0xFF05070C),
        glowCool = Color(0x5C266B8F),
        glowWarm = Color(0x3D6F526F),
        atmosphericBeam = Color(0x143EE2FF),
        vignette = Color(0x8C000205),
        panelSurface = Color(0x9E141D27),
        panelSurfaceStrong = Color(0xAD1A2530),
        panelOutline = Color(0x32E8F7FF),
        panelOutlineStrong = Color(0x46F2FBFF),
        panelSpecular = Color(0xFFFFFFFF),
        panelLowerEdge = Color(0x40000000),
        accentGlow = Color(0x528FD8F7),
        accentFill = Color(0xFF2C7FA3),
        grain = 0.045f,
    )

internal val LightPrismaticStyle =
    PrismaticStyle(
        backgroundStart = Color(0xFFEAF5FA),
        backgroundMiddle = Color(0xFFF3F4F8),
        backgroundEnd = Color(0xFFE9E5EF),
        glowCool = Color(0x7077C5E8),
        glowWarm = Color(0x4FBB8FB7),
        atmosphericBeam = Color(0x24FFFFFF),
        vignette = Color(0x1C708293),
        panelSurface = Color(0xA8FFFFFF),
        panelSurfaceStrong = Color(0xC2FFFFFF),
        panelOutline = Color(0x3871899A),
        panelOutlineStrong = Color(0x5271899A),
        panelSpecular = Color(0xFFFFFFFF),
        panelLowerEdge = Color(0x2E4A5A6A),
        accentGlow = Color(0x45176A8C),
        accentFill = Color(0xFF176A8C),
        grain = 0.03f,
    )

internal val LocalPrismaticStyle = staticCompositionLocalOf { DarkPrismaticStyle }

private val GrainBrush: ShaderBrush by lazy {
    val side = 96
    val random = Random(0xC0105)
    val pixels =
        IntArray(side * side) {
            val shade = random.nextInt(256)
            (0xFF shl 24) or (shade shl 16) or (shade shl 8) or shade
        }
    val texture: ImageBitmap =
        android.graphics.Bitmap
            .createBitmap(pixels, side, side, android.graphics.Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    ShaderBrush(ImageShader(texture, TileMode.Repeated, TileMode.Repeated))
}

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
                        val span = size.maxDimension
                        val base =
                            Brush.linearGradient(
                                colors = listOf(style.backgroundStart, style.backgroundMiddle, style.backgroundEnd),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height),
                            )

                        fun orb(
                            color: Color,
                            x: Float,
                            y: Float,
                            radius: Float,
                        ) = Brush.radialGradient(
                            colorStops =
                                arrayOf(
                                    0f to color,
                                    0.45f to color.copy(alpha = color.alpha * 0.42f),
                                    1f to Color.Transparent,
                                ),
                            center = Offset(size.width * x, size.height * y),
                            radius = span * radius,
                        )
                        val accentOrb = orb(style.accentGlow, 0.8f, -0.02f, 0.46f)
                        val coolOrb = orb(style.glowCool, 0.04f, 0.3f, 0.5f)
                        val warmOrb = orb(style.glowWarm, 0.52f, 1.08f, 0.52f)
                        val sheen =
                            Brush.linearGradient(
                                colorStops =
                                    arrayOf(
                                        0f to Color.Transparent,
                                        0.38f to Color.Transparent,
                                        0.5f to style.atmosphericBeam,
                                        0.64f to Color.Transparent,
                                        1f to Color.Transparent,
                                    ),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, 0f),
                            )
                        val vignette =
                            Brush.radialGradient(
                                colors = listOf(Color.Transparent, style.vignette),
                                center = Offset(size.width * 0.5f, size.height * 0.42f),
                                radius = span * 0.8f,
                            )
                        onDrawBehind {
                            drawRect(base)
                            drawRect(coolOrb)
                            drawRect(warmOrb)
                            drawRect(accentOrb)
                            drawRect(sheen)
                            drawRect(vignette)
                            drawRect(GrainBrush, alpha = style.grain)
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
    val light = style.light
    val shadowed =
        if (light) {
            shadow(
                elevation = if (strong) 10.dp else 4.dp,
                shape = shape,
                clip = false,
                ambientColor = style.panelLowerEdge.copy(alpha = 0.18f),
                spotColor = style.panelLowerEdge.copy(alpha = 0.22f),
            )
        } else {
            this
        }
    return shadowed.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val body =
            Brush.verticalGradient(
                listOf(
                    surfaceColor.copy(alpha = (surfaceColor.alpha + if (light) 0.1f else 0.04f).coerceAtMost(1f)),
                    surfaceColor.copy(alpha = surfaceColor.alpha * if (light) 0.9f else 0.94f),
                ),
            )
        val frost =
            Brush.linearGradient(
                colorStops =
                    arrayOf(
                        0f to style.panelSpecular.copy(alpha = if (light) 0.34f else 0.075f),
                        0.42f to style.panelSpecular.copy(alpha = if (light) 0.06f else 0.012f),
                        1f to Color.Transparent,
                    ),
                start = Offset.Zero,
                end = Offset(size.width * 0.72f, size.height),
            )
        val refraction =
            Brush.radialGradient(
                colors = listOf(style.accentGlow.copy(alpha = style.accentGlow.alpha * if (strong) 0.42f else 0.3f), Color.Transparent),
                center = Offset(size.width, 0f),
                radius = size.maxDimension * 0.62f,
            )
        val rim =
            Brush.linearGradient(
                colorStops =
                    arrayOf(
                        0f to style.panelSpecular.copy(alpha = if (light) 0.95f else if (strong) 0.34f else 0.26f),
                        0.3f to outlineColor.copy(alpha = outlineColor.alpha * 0.55f),
                        0.7f to outlineColor.copy(alpha = outlineColor.alpha * 0.28f),
                        1f to if (light) style.panelLowerEdge else style.panelSpecular.copy(alpha = 0.07f),
                    ),
                start = Offset.Zero,
                end = Offset(size.width * 0.28f, size.height),
            )
        val rimWidth = 1.dp.toPx()
        onDrawBehind {
            drawOutline(outline, brush = body)
            drawOutline(outline, brush = frost)
            drawOutline(outline, brush = refraction)
            drawOutline(outline, brush = rim, style = Stroke(width = rimWidth))
        }
    }
}

@Composable
internal fun Modifier.glassTint(
    tint: Color,
    shape: Shape,
    emphasis: Float = 1f,
): Modifier {
    val style = LocalPrismaticStyle.current
    val light = style.light
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val body =
            Brush.verticalGradient(
                listOf(
                    tint.copy(alpha = (if (light) 0.2f else 0.36f) * emphasis),
                    tint.copy(alpha = (if (light) 0.1f else 0.16f) * emphasis),
                ),
            )
        val rim =
            Brush.verticalGradient(
                listOf(
                    style.panelSpecular.copy(alpha = (if (light) 0.9f else 0.34f) * emphasis),
                    tint.copy(alpha = 0.3f * emphasis),
                    tint.copy(alpha = 0.12f * emphasis),
                ),
            )
        val rimWidth = 1.dp.toPx()
        onDrawBehind {
            drawOutline(outline, brush = body)
            drawOutline(outline, brush = rim, style = Stroke(width = rimWidth))
        }
    }
}
