package com.guanyu.rx400hprobe

import org.junit.Assert.*
import org.junit.Test

class RealtimeClosureTest {
    @Test fun ambiguousOrSavedSessionsNeverAuthorizeDeletion() {
        fun eligible(status: String = "active", id: String = "RX400h_test", app: String = "com.guanyu.rx400hprobe.debug",
            noArchive: Boolean = true, noEnd: Boolean = true, incomplete: Boolean = true) =
            LegacySessionCleanup.positivelyUnsaved("RX400h_test", id, app, status, noArchive, noEnd, incomplete)
        assertTrue(eligible())
        for (status in listOf("", "completed", "finalized", "interrupted", "finalize_failed", "finalizing"))
            assertFalse(eligible(status))
        assertFalse(eligible(id = "another"))
        assertFalse(eligible(app = ""))
        assertFalse(eligible(noArchive = false))
        assertFalse(eligible(noEnd = false))
        assertFalse(eligible(incomplete = false))
    }

    @Test fun threeHzSameValueSamplesStillRefreshSourceTruth() {
        var now = 0L
        val store = SignalStore { now }
        val ok = CommandResult("TEST", emptyList(), TransactionStatus.OK, true)
        repeat(40) {
            now = it * 334L
            store.update(store.baseline.rpm, 1000.0, "TEST", ok)
        }
        assertEquals(40L, store.baseline.rpm.version)
        assertEquals(now, store.baseline.rpm.updatedAtElapsedMs)
        assertEquals(SignalStatus.VALID, store.baseline.rpm.status)
    }

    @Test fun idleThreeHzEntersHoldsAndExitsWithoutFlicker() {
        val idle = IdleCheckState()
        repeat(4) { idle.update(true, 1000.0, 0.0, 0.0, it * 334L) }
        assertTrue(idle.active)
        idle.update(true, 1150.0, 0.2, 0.0, 1336L)
        assertTrue(idle.active)
        idle.update(true, 1250.0, 0.0, 0.0, 1670L)
        idle.update(true, 1000.0, 0.0, 0.0, 2004L)
        assertTrue(idle.active)
        repeat(4) { idle.update(true, 1250.0, 0.0, 0.0, 2338L + it * 334L) }
        assertFalse(idle.active)
        repeat(4) { idle.update(true, 1000.0, 0.0, 0.0, 3674L + it * 334L) }
        assertTrue(idle.active)
        idle.update(null, null, null, null, 5000L)
        assertFalse(idle.active)
    }
}
