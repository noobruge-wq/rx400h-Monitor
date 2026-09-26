package com.guanyu.rx400hprobe

import org.junit.Assert.assertEquals
import org.junit.Test

class PowerBarGeometryTest {
    @Test fun authoredTravelAndCursorStayIndependentOfAbsolutePageCoordinates() {
        val original = PowerBarGeometry(8, 192, 530)
        assertEquals(100, original.zeroLocal)
        assertEquals(8, original.fillX(-50f))
        assertEquals(192, original.fillX(50f))
        assertEquals(530f, original.cursorX(0f), 0.001f)
        val moved = original.copy(cursorZero = 570)
        assertEquals(original.cursorX(10f) + 40f, moved.cursorX(10f), 0.001f)
        val resized = PowerBarGeometry(5, 105, 300)
        assertEquals(55, resized.zeroLocal)
        assertEquals(350f, resized.cursorX(50f), 0.001f)
        assertEquals(250f, resized.cursorX(-999f), 0.001f)
    }
}
