package com.guanyu.rx400hprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestTableTest {

    @Test
    fun runtimeWhitelistIsExactlyTheFrozenSevenRequests() {
        val actual = RequestTable.requests.map { Triple(it.header, it.command, it.timeoutMs) }
        assertEquals(
            listOf(
                Triple("7E0", "01040C0D0E10 2", 5000L),
                Triple("7E0", "21CDF3 3", 5000L),
                Triple("7E0", "01050607 1", 5000L),
                Triple("7E2", "21C3 6", 6000L),
                Triple("7E2", "21C4 5", 6000L),
                Triple("7E2", "21CF 4", 6000L),
                Triple(null, "ATRV", 4000L)
            ),
            actual
        )
        assertEquals(setOf("7E0", "7E2", null), RequestTable.requests.map { it.header }.toSet())
    }

    @Test
    fun forbiddenFamiliesCannotEnterTheFixedTable() {
        RequestTable.requests.forEach { request ->
            val command = request.command.uppercase()
            assertFalse(command.startsWith("22"))
            assertFalse(command.startsWith("2C"))
            assertFalse(command == "10 02" || command == "10 03")
            assertFalse(request.header in setOf("7E1", "7E3", "7E4"))
            assertTrue(request.targetPeriodMs > 0)
        }
    }

    @Test
    fun d046KeepsFrozenPeriodsAndUsesPromptDelimitedRuntimeTransactions() {
        assertEquals(
            listOf(800L, 1000L, 3000L, 800L, 1500L, 5000L, 3000L),
            RequestTable.requests.map { it.targetPeriodMs }
        )
        RequestTable.requests.forEach { request ->
            assertEquals(request.targetPeriodMs, request.deadlineMs)
            assertTrue(request.phaseMs in 0L until request.targetPeriodMs)
            assertEquals(0L, request.minimumGapMs)
            assertEquals(0L, request.preDrainMs)
            assertEquals(0L, request.quietWindowMs)
        }
    }
}
