package com.guanyu.rx400hprobe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleCheckStateTest {

    @Test fun duplicatePowerSampleCannotCompleteEntryOrExit() {
        val state = IdleCheckState()
        state.update(true, 1000.0, 0.0, 20.0, 0L, 0L)
        state.update(true, 1000.0, 0.0, 20.0, 1200L, 0L)
        assertFalse(state.active)
        state.update(true, 1000.0, 0.0, 20.0, 1300L, 1300L)
        assertTrue(state.active)
        state.update(true, 1300.0, 2.0, 20.0, 1400L, 1400L)
        state.update(true, 1300.0, 2.0, 20.0, 2500L, 1400L)
        assertTrue(state.active)
        state.update(true, 1300.0, 2.0, 20.0, 2600L, 2600L)
        assertFalse(state.active)
    }

    @Test fun widerHoldSuppressesShortExcursionButWarmupLossIsImmediate() {
        val state = IdleCheckState()
        state.update(true, 1000.0, 0.0, 20.0, 0L)
        state.update(true, 1000.0, 0.0, 20.0, 1000L)
        state.update(true, 889.75, 0.2, 20.0, 1200L)
        assertTrue(state.active)
        state.update(true, 1300.0, 1.0, 20.0, 1400L)
        state.update(true, 1000.0, 0.0, 20.0, 1800L)
        assertTrue(state.active)
        state.update(false, 1000.0, 0.0, 20.0, 1850L, 1800L)
        assertFalse(state.active)
    }

    @Test fun missingOrNonfiniteInputsImmediatelyExit() {
        val state = IdleCheckState()
        state.update(true, 1000.0, 0.0, 20.0, 0L)
        state.update(true, 1000.0, 0.0, 20.0, 1000L)
        state.update(true, Double.NaN, 0.0, 20.0, 1100L)
        assertFalse(state.active)
    }

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
        assertFalse(state.active)
        state.update(true, 1000.0, 0.0, 20.0, 3200L)
        assertTrue(state.active)
        state.reset()
        assertFalse(state.active)
    }
}
