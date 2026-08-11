package com.guanyu.rx400hprobe

import org.junit.Assert.assertEquals
import org.junit.Test

class DeadlineSchedulerTest {

    private val specs = listOf(
        ScheduledSpec("std_core", "7E0", 800L, RequestPriority.FAST),
        ScheduledSpec("coolant", "7E0", 3000L, RequestPriority.SLOW),
        ScheduledSpec("c3", "7E2", 800L, RequestPriority.FAST)
    )

    @Test
    fun allRequestsDueImmediatelyAtStart() {
        val s = DeadlineScheduler(specs)
        val out = IntArray(specs.size)
        assertEquals(3, s.dueRequests(0L, out))
        assertEquals(listOf(0, 1, 2), out.toList())
    }

    @Test
    fun independentPeriodsSelectOnlyDueRequests() {
        val s = DeadlineScheduler(specs, nowMs = 0L)
        val out = IntArray(specs.size)
        for (i in specs.indices) s.markExecuted(i, 0L)
        assertEquals(0, s.dueRequests(100L, out))
        assertEquals(2, s.dueRequests(800L, out))
        assertEquals(1, s.dueRequests(3000L, out))
    }

    @Test
    fun headerGroupOrderBeforePriority() {
        val s = DeadlineScheduler(specs, nowMs = 0L)
        val out = IntArray(specs.size)
        assertEquals(3, s.dueRequests(0L, out))
        assertEquals(0, out[0]) // 7E0 std_core
        assertEquals(1, out[1]) // 7E0 coolant
        assertEquals(2, out[2]) // 7E2 c3
    }

    @Test
    fun overdueRequestIsSkippedAndRebasedWithoutCatchUp() {
        val s = DeadlineScheduler(listOf(specs[0]), nowMs = 0L)
        val out = IntArray(1)
        s.markExecuted(0, 0L) // next due 800
        // Visit at 2000: lateness 1200 > deadline 400 -> skip, rebase to 2800.
        assertEquals(0, s.dueRequests(2000L, out))
        assertEquals(1, s.skippedOverdue)
        assertEquals(1, s.deadlineMisses)
        assertEquals(0, s.dueRequests(2400L, out))
        assertEquals(1, s.dueRequests(2800L, out))
    }

    @Test
    fun nextWakeReturnsNearestDue() {
        val s = DeadlineScheduler(specs, nowMs = 0L)
        for (i in specs.indices) s.markExecuted(i, 0L)
        assertEquals(800L, s.nextWakeMs(100L))
    }

    @Test
    fun resetMakesAllDueAgain() {
        val s = DeadlineScheduler(specs, nowMs = 1000L)
        val out = IntArray(specs.size)
        for (i in specs.indices) s.markExecuted(i, 1000L)
        assertEquals(0, s.dueRequests(1200L, out))
        s.reset(1200L)
        assertEquals(3, s.dueRequests(1200L, out))
    }

    @Test
    fun startRunClearsEvidenceCountersAndMakesAllRequestsDue() {
        val s = DeadlineScheduler(listOf(specs[0]), nowMs = 0L)
        val out = IntArray(1)
        s.markExecuted(0, 0L)
        assertEquals(0, s.dueRequests(2000L, out))
        assertEquals(1L, s.executions)
        assertEquals(1L, s.deadlineMisses)
        assertEquals(1L, s.skippedOverdue)

        s.startRun(3000L)

        assertEquals(0L, s.executions)
        assertEquals(0L, s.deadlineMisses)
        assertEquals(0L, s.skippedOverdue)
        assertEquals(1, s.dueRequests(3000L, out))
    }

    @Test
    fun latencyWindowPercentiles() {
        val w = LatencyWindow(4)
        w.add(10); w.add(20); w.add(30); w.add(40)
        assertEquals(10L, w.percentile(0.0))
        assertEquals(20L, w.percentile(0.5))
        assertEquals(40L, w.percentile(1.0))
        assertEquals(0L, LatencyWindow(4).percentile(0.5))
        w.clear()
        assertEquals(0L, w.percentile(0.5))
    }
}
