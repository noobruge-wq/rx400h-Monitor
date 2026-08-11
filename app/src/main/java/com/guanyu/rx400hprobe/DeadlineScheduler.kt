package com.guanyu.rx400hprobe

/**
 * V0.3.0 deadline/priority scheduler (D-040).
 *
 * Pure and unit-testable. Each request has an independent target period and
 * deadline. Due requests are emitted in a stable order that minimizes ELM
 * header switches; a request overdue beyond its deadline is skipped and
 * re-based on now, so there is never a catch-up request avalanche.
 */
internal class DeadlineScheduler(
    val specs: List<ScheduledSpec>,
    nowMs: Long = 0L
) {
    private val nextDueAt = LongArray(specs.size) { nowMs }
    private val order: IntArray = specs.indices
        .sortedWith(
            compareBy(
                { headerGroupRank(specs[it].header) },
                { specs[it].priority.ordinal },
                { it }
            )
        )
        .toIntArray()
    private val dueBuffer = IntArray(specs.size)

    var executions: Long = 0
        private set

    var deadlineMisses: Long = 0
        private set

    var skippedOverdue: Long = 0
        private set

    /**
     * Fills [out] with due spec indices in header/priority order and returns
     * the count. Overdue requests are skipped and re-based on [timeMs].
     */
    fun dueRequests(timeMs: Long, out: IntArray = dueBuffer): Int {
        var count = 0
        for (i in order) {
            val dueAt = nextDueAt[i]
            if (timeMs < dueAt) continue
            val deadline = specs[i].deadlineMs
            if (timeMs - dueAt > deadline) {
                deadlineMisses++
                skippedOverdue++
                nextDueAt[i] = timeMs + specs[i].periodMs
                continue
            }
            out[count++] = i
        }
        return count
    }

    fun markExecuted(index: Int, timeMs: Long) {
        if (index !in specs.indices) return
        executions++
        nextDueAt[index] = timeMs + specs[index].periodMs
    }

    /** Earliest time the caller should wake to service a due request. */
    fun nextWakeMs(timeMs: Long): Long {
        var min = Long.MAX_VALUE
        for (i in specs.indices) {
            val dueAt = nextDueAt[i]
            if (dueAt < min) min = dueAt
        }
        return maxOf(timeMs, min)
    }

    /** Makes every request due immediately (connection/reconnect boundary). */
    fun reset(timeMs: Long) {
        for (i in specs.indices) nextDueAt[i] = timeMs
    }

    /** Starts a new evidence run with fresh counters and all requests due. */
    fun startRun(timeMs: Long) {
        executions = 0
        deadlineMisses = 0
        skippedOverdue = 0
        reset(timeMs)
    }

    private fun headerGroupRank(header: String?): Int = when (header) {
        "7E0" -> 0
        "7E2" -> 1
        else -> 2
    }
}

internal data class ScheduledSpec(
    val id: String,
    val header: String?,
    val periodMs: Long,
    val priority: RequestPriority,
    val deadlineMs: Long = (periodMs / 2L).coerceAtLeast(300L)
)
