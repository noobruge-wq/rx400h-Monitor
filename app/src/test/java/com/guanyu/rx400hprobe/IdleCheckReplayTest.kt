package com.guanyu.rx400hprobe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic replay of the first natural real-vehicle Idle Check activation
 * captured in `RX400h_20260808_234255` (decoder 002).
 *
 * Frames idx 140-147, elapsed ms relative to idx 140. The log's
 * `idle_check_active` column lags one frame because `frames.csv` is written
 * before the derived state update in the scheduler loop; this test asserts the
 * state-machine timing directly.
 */
class IdleCheckReplayTest {

    @Test
    fun replayNaturalIdleCheckActivationAndExit() {
        val state = IdleCheckState()

        // idx 140: conditions met, stability window starts -> not yet active
        state.update(true, 908.5, 0.0, 13.0, 0L)
        assertFalse(state.active)

        // idx 141 update (t=2960): stable > 1 s -> active
        state.update(true, 910.75, 0.0, 15.0, 2960L)
        assertTrue(state.active)

        // active persists through the logged activation frames
        state.update(true, 903.0, 0.0, 13.0, 6306L)
        assertTrue(state.active)
        state.update(true, 901.5, 0.0, 10.0, 9266L)
        assertTrue(state.active)
        state.update(true, 903.0, 0.0, 9.0, 11780L)
        assertTrue(state.active)

        // idx 145 (t=14802): RPM falls to 889 (< 900) -> exit
        state.update(true, 889.0, 0.0, 9.0, 14802L)
        assertFalse(state.active)

        // stays inactive on the following frame
        state.update(true, 889.0, 0.0, 9.0, 17658L)
        assertFalse(state.active)

        // warmup ends -> remains inactive
        state.update(false, 874.0, 0.0, 5.0, 20169L)
        assertFalse(state.active)
    }
}
