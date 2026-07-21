package com.hooandee.colores.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun renderColorWheelPixels(
    size: Int,
    projection: LedColorProjection,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): IntArray = withContext(dispatcher) { calibratedColorWheelPixels(size, projection) }
