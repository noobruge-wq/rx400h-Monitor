package com.guanyu.rx400hprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalStoreTest {

    @Test
    fun update_incrementsVersionAndMarksValid() {
        var now = 0L
        val store = SignalStore(clock = { now })
        val result = CommandResult("TEST", emptyList(), "", 0L, TransactionStatus.OK, true)
        store.update(store.baseline.rpm, 1000.0, "TEST", result)
        assertEquals(1L, store.baseline.rpm.version)
        assertEquals(SignalStatus.VALID, store.baseline.rpm.status)
        assertEquals(0L, store.baseline.rpm.updatedAtElapsedMs!!)
    }

    @Test
    fun refreshStaleStates_marksStaleAfterThreshold() {
        var now = 0L
        val store = SignalStore(clock = { now })
        val result = CommandResult("TEST", emptyList(), "", 0L, TransactionStatus.OK, true)
        store.update(store.baseline.rpm, 1000.0, "TEST", result)
        now = 6000L
        store.refreshStaleStates(now)
        assertEquals(SignalStatus.STALE, store.baseline.rpm.status)
    }

    @Test
    fun setDerived_doesNotBumpVersionForUnchangedValue() {
        var now = 0L
        val store = SignalStore(clock = { now })
        store.setDerived(store.hybrid.idleCheckActive, true, "IDLE_CHECK")
        assertEquals(1L, store.hybrid.idleCheckActive.version)
        store.setDerived(store.hybrid.idleCheckActive, true, "IDLE_CHECK")
        assertEquals(1L, store.hybrid.idleCheckActive.version)
        store.setDerived(store.hybrid.idleCheckActive, false, "IDLE_CHECK")
        assertEquals(2L, store.hybrid.idleCheckActive.version)
    }

    @Test
    fun markDecodeFailure_setsDecodeErrorWithoutValue() {
        var now = 0L
        val store = SignalStore(clock = { now })
        val result = CommandResult("TEST", emptyList(), "", 0L, TransactionStatus.UNKNOWN, true)
        store.markDecodeFailure(listOf(store.baseline.rpm), "TEST", result)
        assertEquals(SignalStatus.DECODE_ERROR, store.baseline.rpm.status)
        assertNull(store.baseline.rpm.value)
    }
}
