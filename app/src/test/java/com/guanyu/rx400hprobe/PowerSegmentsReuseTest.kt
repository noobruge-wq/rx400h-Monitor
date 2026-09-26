package com.guanyu.rx400hprobe

import org.junit.Assert.*
import org.junit.Test

class PowerSegmentsReuseTest {
    @Test fun sharedMutableOutputHasSameResultsAndClearsPreviousSide() {
        val out = PowerSegments.unknown()
        for (goal in listOf(-60f, -10f, 0f, 10f, 60f, Float.NaN)) {
            for (ms in 0L..1100L step 10) {
                val expected = PixelDashboardModel.powerSelfTestSegments(ms, goal)
                assertSame(out, PixelDashboardModel.powerSelfTestSegments(ms, goal, out))
                assertEquals(expected, out)
            }
            val expected = PixelDashboardModel.powerSegments(goal)
            assertSame(out, PixelDashboardModel.powerSegments(goal, out))
            assertEquals(expected, out)
        }
        PixelDashboardModel.powerSegments(-10f, out)
        assertEquals(PowerSegments(-10f, 0f, true), out)
        PixelDashboardModel.powerSegments(10f, out)
        assertEquals(PowerSegments(0f, 10f, true), out)
        PixelDashboardModel.powerSegments(0f, out)
        assertEquals(PowerSegments(0f, 0f, true), out)
        PixelDashboardModel.powerSegments(Float.NaN, out)
        assertEquals(PowerSegments.unknown(), out)
    }
}
