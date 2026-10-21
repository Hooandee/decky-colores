package com.hooandee.colores.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.hooandee.colores.R

internal val LocalSmartBrightnessChange = staticCompositionLocalOf<((Boolean) -> Unit)?> { null }

@Composable
internal fun LedBrightnessSlider(
    state: ColoresUiState,
    onBrightnessChange: (Int) -> Unit,
) {
    val onSmartBrightnessChange = LocalSmartBrightnessChange.current
    DeferredIntSlider(
        label = stringResource(if (state.smartBrightness) R.string.brightness_max_title else R.string.brightness_title),
        committedValue = state.ledState.brightness,
        valueLabel = { stringResource(R.string.brightness_value, it) },
        onValueCommit = onBrightnessChange,
        valueRange = 0..100,
        enabled = state.canWrite,
        labelAccessory =
            onSmartBrightnessChange?.let { onChange ->
                { SmartBrightnessToggle(state.smartBrightness, state.canWrite, onChange) }
            },
    )
}

@Composable
private fun SmartBrightnessToggle(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val description = stringResource(R.string.smart_brightness)
    val status = stringResource(if (checked) R.string.smart_brightness_on else R.string.smart_brightness_off)
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier =
            Modifier.semantics {
                contentDescription = description
                stateDescription = status
                role = Role.Switch
            },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_screen_brightness),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint =
                if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
        )
    }
}
