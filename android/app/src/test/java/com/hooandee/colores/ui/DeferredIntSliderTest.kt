package com.hooandee.colores.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeferredIntSliderTest {
    @Test
    fun `dragging changes the staged value without requesting a commit`() {
        val initial = DeferredIntSliderState(25)

        val update = reduceDeferredIntSlider(initial, DeferredIntSliderEvent.Drag(70))

        assertEquals(70, update.state.stagedValue)
        assertEquals(25, update.state.committedValue)
        assertNull(update.commitValue)
    }

    @Test
    fun `finishing requests one commit for the staged value`() {
        val dragged =
            reduceDeferredIntSlider(
                DeferredIntSliderState(25),
                DeferredIntSliderEvent.Drag(70),
            ).state

        val finished = reduceDeferredIntSlider(dragged, DeferredIntSliderEvent.Finish)
        val repeated = reduceDeferredIntSlider(finished.state, DeferredIntSliderEvent.Finish)

        assertEquals(70, finished.commitValue)
        assertEquals(70, finished.state.committedValue)
        assertNull(repeated.commitValue)
    }
}
