package com.guanyu.rx400hprobe

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class DashboardReplayTest {
    @Test fun actualPackagedTraceIsPinnedAndFullyLoadable() {
        val relative = "src/debug/assets/renderer_replay/recorded.csv"
        val file = File(relative).takeIf { it.exists() } ?: File("app/$relative")
        val bytes = file.readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("214b1f95151ebc17ea285945fe866721d5a8e5f52dee175ce07e28bb7f97cca9", hash)
        val replay = file.bufferedReader().use { DashboardReplay.recorded(it.lineSequence()) }
        assertEquals(863, replay.samples.size)
        assertEquals(946308L, replay.samples.last().atMs)
        assertTrue(replay.samples.any { (it.snapshot.speedKph ?: 0.0) > 1.0 })
        assertTrue(replay.samples.any { it.snapshot.speedKph == 0.0 && it.snapshot.rpm == 0.0 })
    }

    @Test fun syntheticStressIsFiveHzAndActuallyChangesTargets() {
        val replay = DashboardReplay.synthetic("stress5")
        assertEquals(601, replay.samples.size)
        assertEquals(120000L, replay.samples.last().atMs)
        assertEquals(200L, replay.samples[1].atMs)
        assertNotEquals(replay.samples[0].snapshot.hvPowerKw, replay.samples[1].snapshot.hvPowerKw)
        assertNotEquals(replay.samples[0].snapshot.socPct, replay.samples[1].snapshot.socPct)
        assertEquals(1L, replay.samples[24].snapshot.batteryTempVersion)
        assertEquals(2L, replay.samples[25].snapshot.batteryTempVersion)
    }

    @Test fun wheelOnlyAndParkedAreDistinctControls() {
        val wheel = DashboardReplay.synthetic("wheel")
        val parked = DashboardReplay.synthetic("parked")
        assertTrue(wheel.samples.all { it.snapshot.speedKph == 42.0 && it.snapshot.hvPowerKw == 0.0 })
        assertTrue(parked.samples.all { it.snapshot.speedKph == 0.0 })
        assertEquals(wheel.samples[0].snapshot.socPct, wheel.samples[599].snapshot.socPct)
    }

    @Test fun lateDeliverySkipsToLatestDueWithoutLookingAhead() {
        val replay = DashboardReplay.synthetic("stress5")
        assertEquals(-1, replay.indexAt(-1))
        assertEquals(0, replay.indexAt(199))
        assertEquals(1, replay.indexAt(200))
        assertEquals(6, replay.indexAt(1234))
        assertEquals(600, replay.indexAt(Long.MAX_VALUE))
    }

    private fun trace(at: String = "1000", speed: String = "10") = sequenceOf(
        DashboardReplay.HEADER, "0,,,,,,,,,,,", "$at,$speed,61,27,33,29,-8,1300,86,13.8,18,false"
    )

    @Test fun recordedTracePreservesNullsValuesAndTiming() {
        val replay = DashboardReplay.recorded(trace())
        assertFalse(replay.samples[0].snapshot.speedFresh)
        assertTrue(replay.samples[1].snapshot.speedFresh)
        assertEquals(-8.0, replay.samples[1].snapshot.hvPowerKw!!, 0.0)
        assertEquals(0, replay.indexAt(999))
        assertEquals(1, replay.indexAt(1000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonMonotonicTraceRejected() { DashboardReplay.recorded(trace("0")) }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteTraceRejected() { DashboardReplay.recorded(trace(speed = "NaN")) }

    @Test(expected = IllegalArgumentException::class)
    fun unboundedTraceRejected() { DashboardReplay.recorded(trace("1800001")) }
}
