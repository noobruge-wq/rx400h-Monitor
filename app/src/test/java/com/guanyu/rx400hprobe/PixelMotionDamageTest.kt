package com.guanyu.rx400hprobe

import org.junit.Assert.*
import org.junit.Test

class PixelMotionDamageTest {
    @Test fun unchangedPixelsDoNotDirtyEitherDomain() {
        val state = PixelMotionDamage()
        assertEquals(3, state.update(10, 20, 30, 20, 30))
        repeat(100) { assertEquals(0, state.update(10, 20, 30, 20, 30)) }
        assertEquals(1, state.update(11, 20, 30, 20, 30))
        assertEquals(2, state.update(11, 20, 30, 21, 30))
    }
    @Test fun unknownAndSelfTestCursorRemovalAlwaysDirty() {
        val state = PixelMotionDamage()
        state.update(10, 20, 30, 20, 30)
        assertEquals(2, state.update(10, 20, 30, 20, Int.MIN_VALUE))
        assertEquals(3, state.update(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE))
    }
}
