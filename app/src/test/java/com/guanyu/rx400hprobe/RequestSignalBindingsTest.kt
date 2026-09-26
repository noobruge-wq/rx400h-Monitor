package com.guanyu.rx400hprobe

import org.junit.Assert.*
import org.junit.Test

class RequestSignalBindingsTest {
    @Test fun failureInvalidatesOnlyItsOwnSignalsWithoutErasingLastValue() {
        val store = SignalStore { 100L }
        val bindings = RequestSignalBindings(store)
        val ok = CommandResult("TEST", emptyList(), TransactionStatus.OK, true)
        store.update(store.baseline.rpm, 1000.0, "TEST", ok)
        store.update(store.hybrid.socPct, 60.0, "TEST", ok)
        for (outcome in listOf(TransactionStatus.TIMEOUT, TransactionStatus.BUS_ERROR, TransactionStatus.NO_DATA)) {
            store.markDecodeFailure(bindings.forRequest("std_core"), "TEST", ok.copy(status = outcome))
            assertFalse(DashboardFreshness.fresh(true, true,
                store.baseline.rpm.status == SignalStatus.VALID, store.baseline.rpm.updatedAtElapsedMs, 101L))
            assertEquals(1000.0, store.baseline.rpm.value!!, 0.0)
            assertEquals(SignalStatus.VALID, store.hybrid.socPct.status)
        }
    }

    @Test fun everyWhitelistedRequestHasExclusiveBindings() {
        val bindings = RequestSignalBindings(SignalStore { 0L })
        val signals = RequestTable.requests.flatMap { bindings.forRequest(it.id) }
        assertEquals(11, signals.size)
        val identities = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<SignalValue<*>, Boolean>())
        identities.addAll(signals)
        assertEquals(signals.size, identities.size)
    }
}
