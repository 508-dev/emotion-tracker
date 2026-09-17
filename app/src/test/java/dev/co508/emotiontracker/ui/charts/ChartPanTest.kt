package dev.co508.emotiontracker.ui.charts

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartPanTest {
    @Test
    fun `one diagonal delta moves both scroll positions`() {
        val horizontal = ScrollState(0)
        val vertical = ScrollState(0)
        val state = chartPanState(horizontal, vertical)

        assertEquals(Offset(-100f, -60f), state.dispatchRawDelta(Offset(-100f, -60f)))
        assertEquals(100, horizontal.value)
        assertEquals(60, vertical.value)
    }

    @Test
    fun `hitting one edge still allows movement on the other axis`() {
        val horizontal = ScrollState(0)
        val vertical = ScrollState(50)
        val state = chartPanState(horizontal, vertical)

        val first = state.dispatchRawDelta(Offset(30f, 30f))
        assertEquals(0f, first.x, 0.001f)
        assertEquals(30f, first.y, 0.001f)
        assertEquals(0, horizontal.value)
        assertEquals(20, vertical.value)
        val second = state.dispatchRawDelta(Offset(30f, 30f))
        assertEquals(0f, second.x, 0.001f)
        assertEquals(20f, second.y, 0.001f)
        assertEquals(0, vertical.value)
    }

    @Test
    fun `right to left layout reverses horizontal consumption only`() {
        val horizontal = ScrollState(0)
        val vertical = ScrollState(0)
        val state = chartPanState(horizontal, vertical, LayoutDirection.Rtl)

        assertEquals(Offset(80f, -40f), state.dispatchRawDelta(Offset(80f, -40f)))
        assertEquals(80, horizontal.value)
        assertEquals(40, vertical.value)
    }
}
