package com.guanyu.rx400hprobe

import org.junit.Assert.*
import org.junit.Test

class DashboardFreshnessTest {
    @Test fun endOrDisconnectRejectsEvenAValidRecentValue() {
        assertTrue(DashboardFreshness.fresh(true, true, true, 1000L, 1100L))
        assertFalse(DashboardFreshness.fresh(false, true, true, 1000L, 1100L))
        assertFalse(DashboardFreshness.fresh(true, false, true, 1000L, 1100L))
    }
    @Test fun ageExpiresWithoutASingleWriterTick() {
        assertTrue(DashboardFreshness.fresh(true, true, true, 1000L, 6000L))
        assertFalse(DashboardFreshness.fresh(true, true, true, 1000L, 6001L))
        assertFalse(DashboardFreshness.fresh(true, true, true, 1001L, 1000L))
        assertFalse(DashboardFreshness.fresh(true, true, false, 1000L, 1001L))
        assertTrue(DashboardFreshness.fresh(true, true, true, 1000L, 13000L, 12000L))
    }
    @Test fun powerRequiresTemporallyPairedInputs() {
        assertTrue(DashboardFreshness.powerPair(1000L, 1800L))
        assertFalse(DashboardFreshness.powerPair(1000L, 1801L))
        assertFalse(DashboardFreshness.powerPair(null, 1000L))
    }
}
