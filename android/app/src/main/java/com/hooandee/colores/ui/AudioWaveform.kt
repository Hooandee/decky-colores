package com.hooandee.colores.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hooandee.colores.R
import com.hooandee.colores.engine.AudioScale
import com.hooandee.colores.led.RgbColor
import kotlinx.coroutines.delay

@Composable
internal fun AudioLevelBars(
    level: Double,
    active: Boolean,
    scale: AudioScale,
    modifier: Modifier = Modifier,
    height: Dp = 118.dp,
) {
    val tracker = remember { AdaptiveAudioBarTracker() }
    val latestLevel = rememberUpdatedState(level.toFloat())
    val bars =
        remember {
            mutableStateListOf<AudioBarPoint>().apply {
                repeat(AUDIO_BAR_COUNT) { add(AudioBarPoint(level = 0f, visualLevel = 0f)) }
            }
        }
    LaunchedEffect(active) {
        if (!active) {
            tracker.reset()
            bars.resetAudioBars()
            return@LaunchedEffect
        }
        while (true) {
            bars.appendTrackedAudioBar(latestLevel.value, tracker)
            delay(AUDIO_BAR_SAMPLE_MILLIS)
        }
    }
    val description =
        stringResource(
            if (active) R.string.audio_waveform_active else R.string.audio_waveform_inactive,
        )
    Surface(
        modifier = modifier.fillMaxWidth().height(height).semantics { contentDescription = description },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val horizontalInset = 14.dp.toPx()
            val verticalInset = 12.dp.toPx()
            val availableWidth = size.width - horizontalInset * 2f
            val availableHeight = size.height - verticalInset * 2f
            val slotWidth = availableWidth / AUDIO_BAR_COUNT
            val barWidth = (slotWidth * 0.58f).coerceIn(3.dp.toPx(), 9.dp.toPx())
            val minimumHeight = 6.dp.toPx()
            val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            val baseline = size.height - verticalInset

            bars.forEachIndexed { index, bar ->
                val visualHeight = minimumHeight + bar.visualLevel * (availableHeight - minimumHeight)
                val left = horizontalInset + index * slotWidth + (slotWidth - barWidth) / 2f
                val top = baseline - visualHeight
                val color = audioBarColor(bar, scale, active).toComposeColor()
                val ageAlpha = audioBarAgeAlpha(index, bars.size)
                val glowAlpha = if (active) ageAlpha * bar.visualLevel * 0.20f else 0f

                if (glowAlpha > 0f) {
                    val glowInset = 2.dp.toPx()
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left - glowInset, top - glowInset),
                        size = Size(barWidth + glowInset * 2f, visualHeight + glowInset * 2f),
                        cornerRadius = CornerRadius(barWidth, barWidth),
                        alpha = glowAlpha,
                    )
                }
                drawRoundRect(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    color.copy(alpha = ageAlpha),
                                    color.copy(alpha = ageAlpha * 0.48f),
                                ),
                            startY = top,
                            endY = baseline,
                        ),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, visualHeight),
                    cornerRadius = cornerRadius,
                )
            }
        }
    }
}

internal data class AudioBarPoint(
    val level: Float,
    val visualLevel: Float,
)

internal class AdaptiveAudioBarTracker {
    private var initialized = false
    private var floor = 0f
    private var peak = MINIMUM_VISUAL_RANGE

    fun track(rawLevel: Float): Float {
        val level = rawLevel.coerceIn(0f, 1f)
        if (!initialized) {
            initialized = true
            floor = level
            peak = (level + MINIMUM_VISUAL_RANGE).coerceAtMost(1f)
        } else {
            val floorRate = if (level < floor) FLOOR_FALL_RATE else FLOOR_RISE_RATE
            floor += (level - floor) * floorRate
            val peakTarget = maxOf(level, floor + MINIMUM_VISUAL_RANGE).coerceAtMost(1f)
            val peakRate = if (peakTarget > peak) PEAK_RISE_RATE else PEAK_FALL_RATE
            peak += (peakTarget - peak) * peakRate
        }
        val relative = ((level - floor) / (peak - floor).coerceAtLeast(MINIMUM_VISUAL_RANGE)).coerceIn(0f, 1f)
        return (level * ABSOLUTE_LEVEL_WEIGHT + relative * RELATIVE_LEVEL_WEIGHT).coerceIn(0f, 1f)
    }

    fun reset() {
        initialized = false
        floor = 0f
        peak = MINIMUM_VISUAL_RANGE
    }
}

internal fun MutableList<AudioBarPoint>.appendTrackedAudioBar(
    level: Float,
    tracker: AdaptiveAudioBarTracker,
) {
    val bounded = level.coerceIn(0f, 1f)
    add(AudioBarPoint(level = bounded, visualLevel = tracker.track(bounded)))
    while (size > AUDIO_BAR_COUNT) removeAt(0)
}

private fun MutableList<AudioBarPoint>.resetAudioBars() {
    clear()
    repeat(AUDIO_BAR_COUNT) { add(AudioBarPoint(level = 0f, visualLevel = 0f)) }
}

internal fun adaptiveAudioBarLevels(levels: List<Float>): List<Float> {
    val tracker = AdaptiveAudioBarTracker()
    return levels.map(tracker::track)
}

internal fun audioBarColor(
    bar: AudioBarPoint,
    scale: AudioScale,
    active: Boolean,
): RgbColor =
    if (active) {
        scale.colorAt(bar.level.toDouble().coerceIn(0.0, 1.0))
    } else {
        INACTIVE_AUDIO_BAR_COLOR
    }

internal fun audioBarAgeAlpha(
    index: Int,
    count: Int,
): Float {
    if (count <= 1) return 1f
    val recency = index.coerceIn(0, count - 1).toFloat() / (count - 1)
    return OLDEST_BAR_ALPHA + recency * (1f - OLDEST_BAR_ALPHA)
}

private const val AUDIO_BAR_COUNT = 24
private const val AUDIO_BAR_SAMPLE_MILLIS = 70L
private const val OLDEST_BAR_ALPHA = 0.30f
private const val MINIMUM_VISUAL_RANGE = 0.08f
private const val ABSOLUTE_LEVEL_WEIGHT = 0.30f
private const val RELATIVE_LEVEL_WEIGHT = 0.70f
private const val FLOOR_FALL_RATE = 0.18f
private const val FLOOR_RISE_RATE = 0.012f
private const val PEAK_RISE_RATE = 0.65f
private const val PEAK_FALL_RATE = 0.035f
private val INACTIVE_AUDIO_BAR_COLOR = RgbColor(92, 94, 108)
