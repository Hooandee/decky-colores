package com.hooandee.colores.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hooandee.colores.R
import com.hooandee.colores.led.RgbColor

@Composable
fun DeviceScene(
    leftColor: RgbColor,
    rightColor: RgbColor,
    selectedTarget: EditTarget,
    power: Boolean,
    enabled: Boolean,
    perZone: Boolean,
    onTargetChange: (EditTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF101116),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.preview_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.preview_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.weight(1f))
            Surface(
                color = Color(0xFF181920),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StickTarget(
                        label = stringResource(R.string.stick_left),
                        color = leftColor,
                        selected = selectedTarget == EditTarget.LEFT,
                        power = power,
                        enabled = enabled && perZone,
                        onClick = { onTargetChange(EditTarget.LEFT) },
                    )
                    StickTarget(
                        label = stringResource(R.string.stick_right),
                        color = rightColor,
                        selected = selectedTarget == EditTarget.RIGHT,
                        power = power,
                        enabled = enabled && perZone,
                        onClick = { onTargetChange(EditTarget.RIGHT) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = { onTargetChange(EditTarget.BOTH) },
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
                color =
                    if (selectedTarget == EditTarget.BOTH) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color(0xFF181920)
                    },
                contentColor =
                    if (selectedTarget == EditTarget.BOTH) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                shape = RoundedCornerShape(999.dp),
                border =
                    BorderStroke(
                        width = if (selectedTarget == EditTarget.BOTH) 2.dp else 1.dp,
                        color =
                            if (selectedTarget == EditTarget.BOTH) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.White.copy(alpha = 0.08f)
                            },
                    ),
            ) {
                Text(
                    text = stringResource(R.string.target_both),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun StickTarget(
    label: String,
    color: RgbColor,
    selected: Boolean,
    power: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier =
                Modifier
                    .size(112.dp)
                    .semantics {
                        contentDescription = label
                        this.selected = selected
                    },
            color = Color(0xFF0B0C10),
            shape = CircleShape,
            border =
                BorderStroke(
                    width = if (selected) 3.dp else 1.dp,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White.copy(alpha = 0.08f)
                        },
                ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier =
                        Modifier
                            .size(72.dp)
                            .border(
                                width = 11.dp,
                                color = color.toComposeColor().copy(alpha = if (power) 1f else 0.18f),
                                shape = CircleShape,
                            )
                            .background(Color(0xFF16171D), CircleShape),
                )
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .background(Color(0xFF252630), CircleShape),
                )
            }
        }
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

internal fun RgbColor.toComposeColor(): Color = Color(red, green, blue)
