package com.hooandee.colores.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hooandee.colores.R

@Composable
internal fun ScrollablePanelContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val showContinuation by remember { derivedStateOf { scrollState.canScrollForward && scrollState.value == 0 } }
    val scope = rememberCoroutineScope()
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .scrollFadeEdges(scrollState)
                    .verticalScroll(scrollState)
                    .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
        if (showContinuation) {
            val label = stringResource(R.string.more_settings_below)
            Surface(
                onClick = { scope.launch { scrollState.animateScrollBy(scrollState.viewportSize * 0.7f) } },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 10.dp)
                        .size(36.dp)
                        .prismaticPanel(CircleShape, strong = true)
                        .semantics { contentDescription = label },
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("↓", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

internal fun Modifier.scrollFadeEdges(scrollState: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (scrollState.canScrollBackward) {
                val fade = 28.dp.toPx().coerceAtMost(size.height / 4f)
                drawRect(
                    brush = Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black), startY = 0f, endY = fade),
                    size = Size(size.width, fade),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (scrollState.canScrollForward) {
                val fade = 56.dp.toPx().coerceAtMost(size.height / 3f)
                drawRect(
                    brush = Brush.verticalGradient(colors = listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
                    topLeft = Offset(0f, size.height - fade),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
