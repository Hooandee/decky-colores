package com.hooandee.colores.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

internal data class DeferredIntSliderState(
    val stagedValue: Int,
    val committedValue: Int = stagedValue,
)

internal sealed interface DeferredIntSliderEvent {
    data class Drag(val value: Int) : DeferredIntSliderEvent

    data object Finish : DeferredIntSliderEvent
}

internal data class DeferredIntSliderUpdate(
    val state: DeferredIntSliderState,
    val commitValue: Int? = null,
)

internal fun reduceDeferredIntSlider(
    state: DeferredIntSliderState,
    event: DeferredIntSliderEvent,
): DeferredIntSliderUpdate =
    when (event) {
        is DeferredIntSliderEvent.Drag ->
            DeferredIntSliderUpdate(state.copy(stagedValue = event.value))

        DeferredIntSliderEvent.Finish -> {
            if (state.stagedValue == state.committedValue) {
                DeferredIntSliderUpdate(state)
            } else {
                DeferredIntSliderUpdate(
                    state = state.copy(committedValue = state.stagedValue),
                    commitValue = state.stagedValue,
                )
            }
        }
    }

@Composable
internal fun DeferredIntSlider(
    label: String,
    committedValue: Int,
    valueLabel: @Composable (Int) -> String,
    onValueCommit: (Int) -> Unit,
    valueRange: IntRange,
    enabled: Boolean,
    resetKey: Any? = null,
    steps: Int = 0,
) {
    var slider by remember(resetKey, committedValue) {
        mutableStateOf(DeferredIntSliderState(committedValue))
    }
    ValueSlider(
        label = label,
        valueLabel = valueLabel(slider.stagedValue),
        value = slider.stagedValue.toFloat(),
        onValueChange = {
            slider = reduceDeferredIntSlider(slider, DeferredIntSliderEvent.Drag(it.roundToInt())).state
        },
        onValueChangeFinished = {
            val update = reduceDeferredIntSlider(slider, DeferredIntSliderEvent.Finish)
            slider = update.state
            update.commitValue?.let(onValueCommit)
        },
        valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
        steps = steps,
        enabled = enabled,
    )
}
