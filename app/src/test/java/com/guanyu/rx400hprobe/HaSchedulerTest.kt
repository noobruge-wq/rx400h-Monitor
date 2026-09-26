package com.guanyu.rx400hprobe

import org.junit.Assert.*
import org.junit.Test

class HaSchedulerTest {
    @Test fun whitelistAndSlowCadenceStayFrozen() {
        assertEquals(listOf("01040C0D0E10 2", "21CDF3 3", "01050607 1",
            "21C3 6", "21C4 5", "21CF 4", "ATRV"), RequestTable.requests.map { it.command })
        assertEquals(listOf("7E0", "7E0", "7E0", "7E2", "7E2", "7E2", null),
            RequestTable.requests.map { it.header })
        assertEquals(listOf(334L,334L,3000L,334L,334L,5000L,3000L),
            RequestTable.requests.map { it.targetPeriodMs })
    }

    @Test fun fixedOrderThreeHzAndSlowWorkBothMakeProgress() {
        val scheduler = HaScheduler(0)
        var now = 0L
        val requests = mutableListOf<ScheduledRequest>()
        while (now < 60_000L) {
            val next = scheduler.next(now)
            if (next == null) now = scheduler.wakeAt(now)
            else { requests += next; scheduler.dispatched(next, now); now += 10L }
        }
        val core = requests.filter { it.targetPeriodMs == 334L }.map { it.id }
        core.forEachIndexed { i, id -> assertEquals(listOf("std_core","cd_f3","c3","c4")[i % 4], id) }
        for (id in listOf("std_core","cd_f3","c3","c4"))
            assertTrue(requests.count { it.id == id } in 179..180)
        assertEquals(20, requests.count { it.id == "coolant" })
        assertEquals(20, requests.count { it.id == "atrv" })
        assertEquals(12, requests.count { it.id == "cf" })
    }

    @Test fun slowLinkAndSuspendNeverReplayThousandsOfOldReleases() {
        val scheduler = HaScheduler(0)
        repeat(4) { scheduler.dispatched(scheduler.next(it * 900L)!!, it * 900L) }
        var now = 3_600_000L
        val atWake = mutableListOf<String>()
        repeat(7) {
            val next = scheduler.next(now)
            if (next != null) { atWake += next.id; scheduler.dispatched(next, now) }
        }
        assertEquals(atWake.size, atWake.distinct().size)
        assertNull(scheduler.next(now))
        assertTrue(scheduler.wakeAt(now) > now)
    }

    @Test fun noVehicleTimerIgnoresAdapterAndToleratesOneGroupLoss() {
        val health = VehicleDataHealth(100)
        health.valid(null, 10_099)
        assertFalse(health.expired(10_099))
        assertTrue(health.expired(10_100))
        health.valid("7E0", 10_100) // RPM=0 is still a valid sample.
        assertFalse(health.expired(20_099))
        health.valid("7E2", 20_099)
        assertFalse(health.expired(30_098))
        assertTrue(health.expired(30_099))
    }
}
