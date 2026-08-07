package com.guanyu.rx400hprobe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleCheckStateTest {

    @Test
    fun idleCheck_requiresStabilityWindow() {
        val state = IdleCheckState()
        state.update(true, 1000.0, 0.0, 20.0, 0L)
        assertFalse(state.active)
        state.update(true, 1000.0, 0.0, 20.0, 999L)
        assertFalse(state.active)
        state.update(true, 1000.0, 0.0, 20.0, 1000L)
        assertTrue(state.active)
    }

    @Test
    fun idleCheck_requiresWarmup() {
        val state = IdleCheckState()
        state.update(false, 1000.0, 0.0, 20.0, 0L)
        state.update(false, 1000.0, 0.0, 20.0, 2000L)
        assertFalse(state.active)
    }

    @Test
    fun idleCheck_resetsStabilityWindowOnConditionLoss() {
        val state = IdleCheckState()
        state.update(true, 1000.0, 0.0, 20.0, 0L)
        state.update(true, 1000.0, 0.0, 20.0, 500L)
        state.update(true, 1200.0, 0.0, 20.0, 600L)
        state.update(true, 1000.0, 0.0, 20.0, 700L)
        assertFalse(state.active)
        state.update(true, 1000.0, 0.0, 20.0, 1700L)
        assertTrue(state.active)
    }

    @Test
    fun idleCheck_rejectsMissingSignals() {
        val state = IdleCheckState()
        state.update(null, null, null, null, 0L)
        state.update(true, 1000.0, 0.0, 20.0, 2000L)
        assertTrue(state.active)
        state.reset()
        assertFalse(state.active)
    }
}
