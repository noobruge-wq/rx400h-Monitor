package com.guanyu.rx400hprobe

/**
 * Fixed HA order, one outstanding transaction and no catch-up queue, statistics or cost model.
 * Releases stay anchored to LIVE epoch. One slow request per eligible gap/cycle.
 */
internal class HaScheduler(private val epochMs: Long) {
    private val requests = RequestTable.requests
    private val core = intArrayOf(0, 1, 3, 4)
    private val nextRelease = LongArray(requests.size) { epochMs + requests[it].phaseMs }
    private var position = 0
    private var inserted = 0

    fun next(nowMs: Long): ScheduledRequest? {
        if (position == 2 && inserted and 1 == 0 && due(2, nowMs)) return requests[2]
        if (position == 0) {
            if (inserted and 2 == 0 && due(5, nowMs)) return requests[5]
            if (inserted and 4 == 0 && due(6, nowMs)) return requests[6]
        }
        return requests[core[position]].takeIf { due(core[position], nowMs) }
    }

    fun dispatched(request: ScheduledRequest, nowMs: Long) {
        val index = requests.indexOf(request)
        check(index >= 0 && next(nowMs) === request) { "Out-of-order HA dispatch" }
        val period = request.targetPeriodMs
        val first = epochMs + request.phaseMs
        // Skip every old release, even after a long suspend; no replay burst.
        nextRelease[index] = first + ((nowMs - first) / period + 1L) * period
        if (index == core[position]) {
            position = (position + 1) % core.size
            inserted = 0
        } else inserted = inserted or when (index) { 2 -> 1; 5 -> 2; else -> 4 }
    }

    fun wakeAt(nowMs: Long): Long {
        var wake = nextRelease[core[position]]
        if (position == 2 && inserted and 1 == 0) wake = minOf(wake, nextRelease[2])
        if (position == 0) {
            if (inserted and 2 == 0) wake = minOf(wake, nextRelease[5])
            if (inserted and 4 == 0) wake = minOf(wake, nextRelease[6])
        }
        return maxOf(nowMs + 1, wake)
    }
    private fun due(index: Int, nowMs: Long) = nowMs >= nextRelease[index]
}

/** ATRV/adapter replies can never prove that either vehicle ECU is alive. */
internal class VehicleDataHealth(startMs: Long) {
    private var engineAt = startMs
    private var hybridAt = startMs
    fun valid(header: String?, nowMs: Long) {
        when (header) { "7E0" -> engineAt = nowMs; "7E2" -> hybridAt = nowMs }
    }
    fun expired(nowMs: Long) = nowMs - maxOf(engineAt, hybridAt) >= 10_000L
}
