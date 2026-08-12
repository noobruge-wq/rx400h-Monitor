package com.guanyu.rx400hprobe

import java.util.ArrayDeque

/**
 * D-046 capacity-aware scheduler.
 *
 * Releases are anchored to one LIVE epoch. The scheduler retains at most one
 * pending release per request, accounts for older releases without catch-up,
 * and returns exactly one dispatch decision at a time. Header locality is an
 * optimization only after every pending deadline is predicted feasible.
 */
internal class DeadlineScheduler(
    val specs: List<ScheduledSpec>,
    private val costModel: SchedulerCostModel
) {
    private data class RequestState(
        var nextReleaseAtMs: Long = 0L,
        var nextSequence: Long = 0L,
        var pending: ScheduledJob? = null,
        var released: Long = 0L,
        var executedOnTime: Long = 0L,
        var executedLate: Long = 0L,
        var capacityRejected: Long = 0L,
        var expiredUnexecuted: Long = 0L,
        var transportUnavailable: Long = 0L,
        var sessionEnded: Long = 0L,
        var consecutiveCapacityRejects: Long = 0L,
        var headerSwitches: Long = 0L,
        var lastCompletionAtMs: Long? = null,
        val queueWait: LatencyWindow = LatencyWindow(64),
        val setup: LatencyWindow = LatencyWindow(64),
        val service: LatencyWindow = LatencyWindow(64),
        val interval: LatencyWindow = LatencyWindow(64),
        val lateness: LatencyWindow = LatencyWindow(64)
    )

    private data class InFlight(
        val dispatch: SchedulerDecision.Dispatch,
        val queueWaitMs: Long
    )

    private data class HeaderInFlight(
        val dispatch: SchedulerDecision.ChangeHeader
    )

    private val states = List(specs.size) { RequestState() }
    private val terminalQueue = ArrayDeque<SchedulerDecision.TerminalBatch>()
    private val observedHeaderSetup = linkedMapOf<String, LatencyWindow>()

    private var running = false
    private var transportAvailable = true
    private var inFlight: InFlight? = null
    private var headerInFlight: HeaderInFlight? = null
    private var nextHeaderToken = 0L
    private var epochMs = 0L
    private var lastObservedAtMs = 0L

    var admission: AdmissionReport = AdmissionReport.unknown("NOT_ASSESSED")
        private set

    var runMode: SchedulerRunMode = SchedulerRunMode.DIAGNOSTIC_BEST_EFFORT
        private set

    init {
        require(specs.isNotEmpty()) { "Scheduler requires at least one request" }
        require(specs.map { it.id }.toSet().size == specs.size) { "Request IDs must be unique" }
        require(specs.all { costModel.requestCosts.containsKey(it.id) }) {
            "Cost model must provide a planning estimate for every request"
        }
    }

    fun startRun(
        epochMs: Long,
        admission: AdmissionReport,
        mode: SchedulerRunMode
    ) {
        require(epochMs >= 0L)
        require(mode != SchedulerRunMode.NORMAL || admission.state == AdmissionState.ADMITTED) {
            "NORMAL mode requires an ADMITTED capacity report"
        }
        this.epochMs = epochMs
        this.lastObservedAtMs = epochMs
        this.admission = admission
        this.runMode = mode
        running = true
        transportAvailable = true
        inFlight = null
        headerInFlight = null
        nextHeaderToken = 0L
        terminalQueue.clear()
        observedHeaderSetup.clear()
        states.forEachIndexed { index, state ->
            state.nextReleaseAtMs = saturatedAdd(epochMs, specs[index].phaseMs)
            state.nextSequence = 0L
            state.pending = null
            state.released = 0L
            state.executedOnTime = 0L
            state.executedLate = 0L
            state.capacityRejected = 0L
            state.expiredUnexecuted = 0L
            state.transportUnavailable = 0L
            state.sessionEnded = 0L
            state.consecutiveCapacityRejects = 0L
            state.headerSwitches = 0L
            state.lastCompletionAtMs = null
            state.queueWait.clear()
            state.setup.clear()
            state.service.clear()
            state.interval.clear()
            state.lateness.clear()
        }
    }

    /**
     * Returns one terminal record, one request dispatch, or the next wake time.
     * [releaseCutoffMs] is used only by deterministic admission replay so the
     * finite horizon can be drained without generating later releases.
     */
    fun next(
        nowMs: Long,
        currentHeader: String?,
        releaseCutoffMs: Long = Long.MAX_VALUE
    ): SchedulerDecision {
        check(running) { "Scheduler run has not started" }
        check(inFlight == null && headerInFlight == null) {
            "The previous transaction has not reached a terminal outcome"
        }
        observeTime(nowMs)

        if (transportAvailable) materializeReleases(nowMs, releaseCutoffMs)
        terminalQueue.pollFirst()?.let { return it }

        if (!transportAvailable) return SchedulerDecision.SleepUntil(Long.MAX_VALUE)

        val pendingIndices = specs.indices.filter { states[it].pending != null }
        if (pendingIndices.isEmpty()) {
            val wake = specs.indices
                .map { states[it].nextReleaseAtMs }
                .filter { it <= releaseCutoffMs }
                .minOrNull() ?: Long.MAX_VALUE
            return SchedulerDecision.SleepUntil(wake)
        }

        val order = bestFeasibleOrder(pendingIndices, nowMs, currentHeader)
        if (order == null) {
            val rejected = chooseCapacityRejection(pendingIndices)
            val job = requireNotNull(states[rejected].pending)
            states[rejected].pending = null
            states[rejected].capacityRejected = saturatedAdd(states[rejected].capacityRejected, 1L)
            states[rejected].consecutiveCapacityRejects = saturatedAdd(
                states[rejected].consecutiveCapacityRejects,
                1L
            )
            return terminal(
                specIndex = rejected,
                firstSequence = job.token.sequence,
                count = 1L,
                firstReleaseAtMs = job.releaseAtMs,
                lastReleaseAtMs = job.releaseAtMs,
                outcome = SchedulerOutcome.REJECTED_CAPACITY,
                reason = SchedulerTerminalReason.NO_FEASIBLE_ORDER,
                recordedAtMs = nowMs
            )
        }

        val specIndex = order.first()
        val state = states[specIndex]
        val job = requireNotNull(state.pending)
        val spec = specs[specIndex]
        val predictedSetupMs = predictedSetupMs(currentHeader, spec.header)
        val predictedServiceMs = predictedRequestMs(specIndex)
        if (spec.header != null && spec.header != currentHeader) {
            val changeHeader = SchedulerDecision.ChangeHeader(
                token = nextHeaderToken++,
                job = job,
                specIndex = specIndex,
                dispatchAtMs = nowMs,
                fromHeader = currentHeader,
                toHeader = spec.header,
                predictedSetupMs = predictedSetupMs
            )
            headerInFlight = HeaderInFlight(changeHeader)
            return changeHeader
        }

        state.pending = null
        val dispatch = SchedulerDecision.Dispatch(
            job = job,
            specIndex = specIndex,
            dispatchAtMs = nowMs,
            fromHeader = currentHeader,
            toHeader = spec.header ?: currentHeader,
            requiresHeaderSwitch = false,
            predictedSetupMs = 0L,
            predictedServiceMs = predictedServiceMs
        )
        inFlight = InFlight(dispatch, (nowMs - job.releaseAtMs).coerceAtLeast(0L))
        return dispatch
    }

    /**
     * Completes exactly one ELM header transaction. The request remains pending,
     * so the next call to [next] re-materializes releases and replans using the
     * actual completion time before any data request is sent.
     */
    fun completeHeader(
        token: Long,
        completedAtMs: Long,
        actualSetupMs: Long
    ): SchedulerHeaderCompletion {
        require(actualSetupMs >= 0L)
        val active = requireHeaderInFlight(token)
        observeTime(completedAtMs)
        val dispatch = active.dispatch
        val state = states[dispatch.specIndex]
        state.headerSwitches = saturatedAdd(state.headerSwitches, 1L)
        observedHeaderSetup
            .getOrPut(headerTransitionKey(dispatch.fromHeader, dispatch.toHeader)) { LatencyWindow(32) }
            .add(actualSetupMs)
        state.setup.add(actualSetupMs)
        headerInFlight = null
        return SchedulerHeaderCompletion(dispatch, completedAtMs, actualSetupMs, true)
    }

    /** Clears a failed header transaction without consuming its request release. */
    fun failHeader(
        token: Long,
        failedAtMs: Long,
        actualSetupMs: Long = 0L
    ): SchedulerHeaderCompletion {
        require(actualSetupMs >= 0L)
        val active = requireHeaderInFlight(token)
        observeTime(failedAtMs)
        headerInFlight = null
        return SchedulerHeaderCompletion(active.dispatch, failedAtMs, actualSetupMs, false)
    }

    fun complete(
        token: ReleaseToken,
        completedAtMs: Long,
        actualSetupMs: Long,
        actualServiceMs: Long
    ): SchedulerCompletion {
        val active = requireInFlight(token)
        observeTime(completedAtMs)
        require(actualSetupMs >= 0L && actualServiceMs >= 0L)
        val dispatch = active.dispatch
        val state = states[dispatch.specIndex]
        val latenessMs = (completedAtMs - dispatch.job.deadlineAtMs).coerceAtLeast(0L)
        val outcome = if (latenessMs == 0L) {
            state.executedOnTime = saturatedAdd(state.executedOnTime, 1L)
            SchedulerOutcome.EXECUTED_ON_TIME
        } else {
            state.executedLate = saturatedAdd(state.executedLate, 1L)
            SchedulerOutcome.EXECUTED_LATE
        }
        state.consecutiveCapacityRejects = 0L
        state.queueWait.add(active.queueWaitMs)
        if (actualSetupMs > 0L) state.setup.add(actualSetupMs)
        state.service.add(actualServiceMs)
        state.lateness.add(latenessMs)
        state.lastCompletionAtMs?.let { previous ->
            state.interval.add((completedAtMs - previous).coerceAtLeast(0L))
        }
        state.lastCompletionAtMs = completedAtMs
        inFlight = null
        return SchedulerCompletion(
            dispatch = dispatch,
            completedAtMs = completedAtMs,
            queueWaitMs = active.queueWaitMs,
            latenessMs = latenessMs,
            actualSetupMs = actualSetupMs,
            actualServiceMs = actualServiceMs,
            outcome = outcome,
            reason = SchedulerTerminalReason.COMPLETED
        )
    }

    /** Terminates a dispatch whose target request could not complete. */
    fun fail(
        token: ReleaseToken,
        failedAtMs: Long,
        outcome: SchedulerOutcome = SchedulerOutcome.TRANSPORT_UNAVAILABLE,
        reason: SchedulerTerminalReason = SchedulerTerminalReason.TRANSPORT_DOWN
    ): SchedulerCompletion {
        require(outcome in setOf(SchedulerOutcome.TRANSPORT_UNAVAILABLE, SchedulerOutcome.SESSION_ENDED))
        val active = requireInFlight(token)
        observeTime(failedAtMs)
        val state = states[active.dispatch.specIndex]
        when (outcome) {
            SchedulerOutcome.TRANSPORT_UNAVAILABLE -> state.transportUnavailable =
                saturatedAdd(state.transportUnavailable, 1L)
            SchedulerOutcome.SESSION_ENDED -> state.sessionEnded = saturatedAdd(state.sessionEnded, 1L)
            else -> error("Unsupported failed-dispatch outcome")
        }
        state.queueWait.add(active.queueWaitMs)
        inFlight = null
        return SchedulerCompletion(
            dispatch = active.dispatch,
            completedAtMs = failedAtMs,
            queueWaitMs = active.queueWaitMs,
            latenessMs = (failedAtMs - active.dispatch.job.deadlineAtMs).coerceAtLeast(0L),
            actualSetupMs = 0L,
            actualServiceMs = 0L,
            outcome = outcome,
            reason = reason
        )
    }

    fun transportDown(atMs: Long) {
        if (!running || !transportAvailable) return
        observeTime(atMs)
        materializeReleases(atMs, Long.MAX_VALUE)
        headerInFlight = null
        inFlight?.let { active ->
            val index = active.dispatch.specIndex
            states[index].transportUnavailable = saturatedAdd(states[index].transportUnavailable, 1L)
            enqueueSingle(active.dispatch.job, SchedulerOutcome.TRANSPORT_UNAVAILABLE, SchedulerTerminalReason.TRANSPORT_DOWN, atMs)
            inFlight = null
        }
        states.forEach { state ->
            state.pending?.let { job ->
                state.pending = null
                state.transportUnavailable = saturatedAdd(state.transportUnavailable, 1L)
                enqueueSingle(job, SchedulerOutcome.TRANSPORT_UNAVAILABLE, SchedulerTerminalReason.TRANSPORT_DOWN, atMs)
            }
        }
        transportAvailable = false
    }

    fun transportUp(atMs: Long) {
        if (!running || transportAvailable) return
        observeTime(atMs)
        // Releases in [down, up) were offered while no transport existed. A
        // release exactly at up is left for the normal available path.
        states.forEachIndexed { index, state ->
            if (state.nextReleaseAtMs >= atMs) return@forEachIndexed
            val spec = specs[index]
            val count = releaseCountThrough(state.nextReleaseAtMs, atMs - 1L, spec.periodMs)
            if (count <= 0L) return@forEachIndexed
            val firstSequence = state.nextSequence
            val firstRelease = state.nextReleaseAtMs
            val lastRelease = saturatedAdd(firstRelease, saturatedMultiply(count - 1L, spec.periodMs))
            state.released = saturatedAdd(state.released, count)
            state.transportUnavailable = saturatedAdd(state.transportUnavailable, count)
            state.nextSequence = saturatedAdd(state.nextSequence, count)
            state.nextReleaseAtMs = saturatedAdd(firstRelease, saturatedMultiply(count, spec.periodMs))
            terminalQueue.addLast(
                terminal(
                    index,
                    firstSequence,
                    count,
                    firstRelease,
                    lastRelease,
                    SchedulerOutcome.TRANSPORT_UNAVAILABLE,
                    SchedulerTerminalReason.RELEASED_WHILE_TRANSPORT_DOWN,
                    atMs
                )
            )
        }
        transportAvailable = true
    }

    fun finishRun(atMs: Long) {
        if (!running) return
        observeTime(atMs)
        // Session lifetime is [epoch, end): a release exactly at End belongs to
        // no live interval, regardless of the transport state at shutdown.
        if (atMs > epochMs) {
            if (transportAvailable) {
                materializeReleases(atMs - 1L, Long.MAX_VALUE)
            } else {
                transportUp(atMs)
            }
        }
        headerInFlight = null
        inFlight?.let { active ->
            val index = active.dispatch.specIndex
            states[index].sessionEnded = saturatedAdd(states[index].sessionEnded, 1L)
            enqueueSingle(active.dispatch.job, SchedulerOutcome.SESSION_ENDED, SchedulerTerminalReason.USER_OR_SESSION_END, atMs)
            inFlight = null
        }
        states.forEach { state ->
            state.pending?.let { job ->
                state.pending = null
                state.sessionEnded = saturatedAdd(state.sessionEnded, 1L)
                enqueueSingle(job, SchedulerOutcome.SESSION_ENDED, SchedulerTerminalReason.USER_OR_SESSION_END, atMs)
            }
        }
        running = false
    }

    fun pollTerminal(): SchedulerDecision.TerminalBatch? = terminalQueue.pollFirst()

    fun snapshot(nowMs: Long = lastObservedAtMs): SchedulerSnapshot {
        val elapsedMs = (nowMs - epochMs).coerceAtLeast(0L)
        val requests = specs.indices.map { index ->
            val state = states[index]
            SchedulerRequestSnapshot(
                id = specs[index].id,
                header = specs[index].header,
                periodMs = specs[index].periodMs,
                released = state.released,
                executedOnTime = state.executedOnTime,
                executedLate = state.executedLate,
                capacityRejected = state.capacityRejected,
                expiredUnexecuted = state.expiredUnexecuted,
                transportUnavailable = state.transportUnavailable,
                sessionEnded = state.sessionEnded,
                pending = if (state.pending == null) 0L else 1L,
                inFlight = if (inFlight?.dispatch?.specIndex == index) 1L else 0L,
                queueWaitP50Ms = state.queueWait.percentile(0.50),
                queueWaitP95Ms = state.queueWait.percentile(0.95),
                queueWaitMaxMs = state.queueWait.max(),
                setupP50Ms = state.setup.percentile(0.50),
                setupP95Ms = state.setup.percentile(0.95),
                setupMaxMs = state.setup.max(),
                serviceP50Ms = state.service.percentile(0.50),
                serviceP95Ms = state.service.percentile(0.95),
                serviceMaxMs = state.service.max(),
                intervalP50Ms = state.interval.percentile(0.50),
                intervalP95Ms = state.interval.percentile(0.95),
                intervalMaxMs = state.interval.max(),
                latenessP50Ms = state.lateness.percentile(0.50),
                latenessP95Ms = state.lateness.percentile(0.95),
                latenessMaxMs = state.lateness.max(),
                headerSwitches = state.headerSwitches,
                elapsedMs = elapsedMs
            )
        }
        return SchedulerSnapshot(admission, runMode, requests)
    }

    val executions: Long get() = states.sumOf { saturatedAdd(it.executedOnTime, it.executedLate) }
    /** Compatibility field: a completed request whose finish deadline was missed. */
    val deadlineMisses: Long get() = states.sumOf { it.executedLate }
    /** Compatibility field: a release that expired without execution. */
    val skippedOverdue: Long get() = states.sumOf { it.expiredUnexecuted }
    val expiredUnexecuted: Long get() = states.sumOf { it.expiredUnexecuted }
    val executedLate: Long get() = states.sumOf { it.executedLate }
    val capacityRejections: Long get() = states.sumOf { it.capacityRejected }
    val transportUnavailableCount: Long get() = states.sumOf { it.transportUnavailable }
    val pendingCount: Long get() =
        states.sumOf { if (it.pending == null) 0L else 1L } + if (inFlight == null) 0L else 1L
    val headerSwitches: Long get() = states.sumOf { it.headerSwitches }

    private fun materializeReleases(nowMs: Long, releaseCutoffMs: Long) {
        val throughMs = minOf(nowMs, releaseCutoffMs)
        if (throughMs < 0L) return
        states.forEachIndexed { index, state ->
            val spec = specs[index]
            if (state.nextReleaseAtMs <= throughMs) {
                val count = releaseCountThrough(state.nextReleaseAtMs, throughMs, spec.periodMs)
                val firstNewSequence = state.nextSequence
                val firstNewRelease = state.nextReleaseAtMs
                state.released = saturatedAdd(state.released, count)
                state.nextSequence = saturatedAdd(state.nextSequence, count)
                state.nextReleaseAtMs = saturatedAdd(
                    state.nextReleaseAtMs,
                    saturatedMultiply(count, spec.periodMs)
                )

                state.pending?.let { old ->
                    state.pending = null
                    state.expiredUnexecuted = saturatedAdd(state.expiredUnexecuted, 1L)
                    terminalQueue.addLast(terminal(
                        index, old.token.sequence, 1L, old.releaseAtMs, old.releaseAtMs,
                        SchedulerOutcome.EXPIRED_UNEXECUTED,
                        SchedulerTerminalReason.SUPERSEDED_BY_NEW_RELEASE, nowMs
                    ))
                }

                if (count > 1L) {
                    val expiredCount = count - 1L
                    val lastExpiredRelease = saturatedAdd(
                        firstNewRelease,
                        saturatedMultiply(expiredCount - 1L, spec.periodMs)
                    )
                    state.expiredUnexecuted = saturatedAdd(state.expiredUnexecuted, expiredCount)
                    terminalQueue.addLast(terminal(
                        index, firstNewSequence, expiredCount, firstNewRelease, lastExpiredRelease,
                        SchedulerOutcome.EXPIRED_UNEXECUTED,
                        SchedulerTerminalReason.COALESCED_OLD_RELEASES, nowMs
                    ))
                }

                val latestOffset = count - 1L
                val latestRelease = saturatedAdd(
                    firstNewRelease,
                    saturatedMultiply(latestOffset, spec.periodMs)
                )
                val latestSequence = saturatedAdd(firstNewSequence, latestOffset)
                state.pending = ScheduledJob(
                    ReleaseToken(index, latestSequence),
                    latestRelease,
                    saturatedAdd(latestRelease, spec.deadlineMs)
                )
            }

            state.pending?.takeIf { nowMs > it.deadlineAtMs }?.let { expired ->
                state.pending = null
                state.expiredUnexecuted = saturatedAdd(state.expiredUnexecuted, 1L)
                terminalQueue.addLast(terminal(
                    index, expired.token.sequence, 1L, expired.releaseAtMs, expired.releaseAtMs,
                    SchedulerOutcome.EXPIRED_UNEXECUTED,
                    SchedulerTerminalReason.DEADLINE_ELAPSED, nowMs
                ))
            }
        }
    }

    private fun bestFeasibleOrder(
        pendingIndices: List<Int>,
        nowMs: Long,
        currentHeader: String?
    ): IntArray? {
        var best: IntArray? = null
        var bestSetup = Long.MAX_VALUE
        val used = BooleanArray(pendingIndices.size)
        val order = IntArray(pendingIndices.size)

        fun visit(depth: Int, timeMs: Long, header: String?, totalSetupMs: Long) {
            if (totalSetupMs > bestSetup) return
            if (depth == pendingIndices.size) {
                val candidate = order.copyOf()
                val currentBest = best
                if (currentBest == null || totalSetupMs < bestSetup ||
                    (totalSetupMs == bestSetup && lexicographicallyEarlier(candidate, currentBest))
                ) {
                    best = candidate
                    bestSetup = totalSetupMs
                }
                return
            }

                val candidates = pendingIndices.indices
                .filter { !used[it] }
                .sortedWith(compareBy(
                    { requireNotNull(states[pendingIndices[it]].pending).deadlineAtMs },
                    { pendingIndices[it] }
                ))
            for (candidatePosition in candidates) {
                val specIndex = pendingIndices[candidatePosition]
                val spec = specs[specIndex]
                val job = requireNotNull(states[specIndex].pending)
                val setup = predictedSetupMs(header, spec.header)
                val finish = saturatedAdd(timeMs, saturatedAdd(setup, predictedRequestMs(specIndex)))
                if (finish > job.deadlineAtMs) continue
                used[candidatePosition] = true
                order[depth] = specIndex
                visit(depth + 1, finish, spec.header ?: header, saturatedAdd(totalSetupMs, setup))
                used[candidatePosition] = false
            }
        }

        visit(0, nowMs, currentHeader, 0L)
        return best
    }

    private fun lexicographicallyEarlier(candidate: IntArray, existing: IntArray): Boolean {
        for (position in candidate.indices) {
            val leftIndex = candidate[position]
            val rightIndex = existing[position]
            val leftJob = requireNotNull(states[leftIndex].pending)
            val rightJob = requireNotNull(states[rightIndex].pending)
            val deadlineCompare = leftJob.deadlineAtMs.compareTo(rightJob.deadlineAtMs)
            if (deadlineCompare != 0) return deadlineCompare < 0
            if (leftIndex != rightIndex) return leftIndex < rightIndex
        }
        return false
    }

    private fun chooseCapacityRejection(indices: List<Int>): Int = indices.sortedWith(
        compareByDescending<Int> { specs[it].priority.ordinal }
            .thenBy { states[it].consecutiveCapacityRejects }
            .thenByDescending { requireNotNull(states[it].pending).deadlineAtMs }
            .thenByDescending { it }
    ).first()

    private fun predictedRequestMs(index: Int): Long = maxOf(
        costModel.requestMs(specs[index]),
        states[index].service.percentile(0.95)
    )

    private fun predictedSetupMs(currentHeader: String?, requiredHeader: String?): Long {
        if (requiredHeader == null || requiredHeader == currentHeader) return 0L
        val observed = observedHeaderSetup[headerTransitionKey(currentHeader, requiredHeader)]
            ?.percentile(0.95) ?: 0L
        return maxOf(costModel.headerSetupMs(), observed)
    }

    private fun requireInFlight(token: ReleaseToken): InFlight {
        val active = checkNotNull(inFlight) { "No scheduler dispatch is in flight" }
        require(active.dispatch.job.token == token) { "Completion token does not match in-flight dispatch" }
        return active
    }

    private fun requireHeaderInFlight(token: Long): HeaderInFlight {
        val active = checkNotNull(headerInFlight) { "No header transaction is in flight" }
        require(active.dispatch.token == token) { "Completion token does not match in-flight header" }
        return active
    }

    private fun observeTime(timeMs: Long) {
        require(timeMs >= lastObservedAtMs) { "Scheduler monotonic time moved backwards" }
        lastObservedAtMs = timeMs
    }

    private fun enqueueSingle(
        job: ScheduledJob,
        outcome: SchedulerOutcome,
        reason: SchedulerTerminalReason,
        atMs: Long
    ) {
        terminalQueue.addLast(terminal(
            job.token.specIndex, job.token.sequence, 1L, job.releaseAtMs, job.releaseAtMs,
            outcome, reason, atMs
        ))
    }

    private fun terminal(
        specIndex: Int,
        firstSequence: Long,
        count: Long,
        firstReleaseAtMs: Long,
        lastReleaseAtMs: Long,
        outcome: SchedulerOutcome,
        reason: SchedulerTerminalReason,
        recordedAtMs: Long
    ): SchedulerDecision.TerminalBatch = SchedulerDecision.TerminalBatch(
        specIndex, firstSequence, count, firstReleaseAtMs, lastReleaseAtMs,
        outcome, reason, recordedAtMs
    )

    private fun headerTransitionKey(from: String?, to: String?): String =
        "${from ?: "NONE"}->${to ?: "NONE"}"

    companion object {
        private fun releaseCountThrough(firstMs: Long, throughMs: Long, periodMs: Long): Long {
            if (throughMs < firstMs) return 0L
            return saturatedAdd(1L, (throughMs - firstMs) / periodMs)
        }

        internal fun saturatedAdd(left: Long, right: Long): Long {
            if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE
            if (right < 0L && left < Long.MIN_VALUE - right) return Long.MIN_VALUE
            return left + right
        }

        internal fun saturatedMultiply(left: Long, right: Long): Long {
            if (left == 0L || right == 0L) return 0L
            if (left < 0L || right < 0L) return Math.multiplyExact(left, right)
            if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE
            return left * right
        }
    }
}

internal data class ScheduledSpec(
    val id: String,
    val header: String?,
    val periodMs: Long,
    val priority: RequestPriority,
    val deadlineMs: Long = periodMs,
    val phaseMs: Long = 0L
) {
    init {
        require(id.isNotBlank())
        require(periodMs > 0L)
        require(deadlineMs in 1L..periodMs)
        require(phaseMs in 0L until periodMs)
    }
}

internal data class ReleaseToken(val specIndex: Int, val sequence: Long)

internal data class ScheduledJob(
    val token: ReleaseToken,
    val releaseAtMs: Long,
    val deadlineAtMs: Long
)

internal enum class SchedulerOutcome {
    EXECUTED_ON_TIME, EXECUTED_LATE, REJECTED_CAPACITY, EXPIRED_UNEXECUTED,
    TRANSPORT_UNAVAILABLE, SESSION_ENDED
}

internal enum class SchedulerTerminalReason {
    COMPLETED, NO_FEASIBLE_ORDER, DEADLINE_ELAPSED, SUPERSEDED_BY_NEW_RELEASE,
    COALESCED_OLD_RELEASES, TRANSPORT_DOWN, RELEASED_WHILE_TRANSPORT_DOWN,
    USER_OR_SESSION_END
}

internal sealed class SchedulerDecision {
    data class ChangeHeader(
        val token: Long,
        val job: ScheduledJob,
        val specIndex: Int,
        val dispatchAtMs: Long,
        val fromHeader: String?,
        val toHeader: String,
        val predictedSetupMs: Long
    ) : SchedulerDecision()

    data class Dispatch(
        val job: ScheduledJob,
        val specIndex: Int,
        val dispatchAtMs: Long,
        val fromHeader: String?,
        val toHeader: String?,
        val requiresHeaderSwitch: Boolean,
        val predictedSetupMs: Long,
        val predictedServiceMs: Long
    ) : SchedulerDecision()

    data class TerminalBatch(
        val specIndex: Int,
        val firstSequence: Long,
        val count: Long,
        val firstReleaseAtMs: Long,
        val lastReleaseAtMs: Long,
        val outcome: SchedulerOutcome,
        val reason: SchedulerTerminalReason,
        val recordedAtMs: Long
    ) : SchedulerDecision()

    data class SleepUntil(val timeMs: Long) : SchedulerDecision()
}

internal data class SchedulerHeaderCompletion(
    val dispatch: SchedulerDecision.ChangeHeader,
    val completedAtMs: Long,
    val actualSetupMs: Long,
    val succeeded: Boolean
)

internal data class SchedulerCompletion(
    val dispatch: SchedulerDecision.Dispatch,
    val completedAtMs: Long,
    val queueWaitMs: Long,
    val latenessMs: Long,
    val actualSetupMs: Long,
    val actualServiceMs: Long,
    val outcome: SchedulerOutcome,
    val reason: SchedulerTerminalReason
)

internal data class SchedulerCostEstimate(
    val p95Ms: Long,
    val sampleCount: Int,
    val trusted: Boolean
) {
    init {
        require(p95Ms > 0L)
        require(sampleCount >= 0)
    }
}

internal data class SchedulerCostModel(
    val modelId: String,
    val sourceEvidenceId: String,
    val requestCosts: Map<String, SchedulerCostEstimate>,
    val headerSetupCost: SchedulerCostEstimate
) {
    fun requestMs(spec: ScheduledSpec): Long = requireNotNull(requestCosts[spec.id]) {
        "Missing request cost for ${spec.id}"
    }.p95Ms

    fun headerSetupMs(): Long = headerSetupCost.p95Ms

    fun isTrustedFor(specs: List<ScheduledSpec>, minimumSamples: Int): Boolean =
        headerSetupCost.trusted && headerSetupCost.sampleCount >= minimumSamples &&
            specs.all { spec ->
                requestCosts[spec.id]?.let { it.trusted && it.sampleCount >= minimumSamples } == true
            }
}

internal enum class AdmissionState { UNKNOWN, ADMITTED, OVERLOADED }
internal enum class SchedulerRunMode { NORMAL, DIAGNOSTIC_BEST_EFFORT }

internal data class AdmissionReport(
    val state: AdmissionState,
    val modelId: String,
    val sourceEvidenceId: String,
    val requestUtilization: Double?,
    val projectedUtilization: Double?,
    val projectedDeadlineMisses: Long?,
    val projectedCapacityRejections: Long?,
    val horizonMs: Long,
    val reason: String
) {
    companion object {
        fun unknown(reason: String): AdmissionReport = AdmissionReport(
            AdmissionState.UNKNOWN, "unavailable", "unavailable", null, null,
            null, null, 60_000L, reason
        )
    }
}

internal object CapacityAdmission {
    private const val MIN_TRUSTED_SAMPLES = 20

    fun assess(
        specs: List<ScheduledSpec>,
        model: SchedulerCostModel,
        horizonMs: Long = 60_000L
    ): AdmissionReport {
        require(horizonMs > 0L)
        if (!model.isTrustedFor(specs, MIN_TRUSTED_SAMPLES)) {
            return AdmissionReport(
                AdmissionState.UNKNOWN, model.modelId, model.sourceEvidenceId,
                null, null, null, null, horizonMs,
                "TRUSTED_P95_COSTS_INCOMPLETE"
            )
        }
        val requestUtilization = specs.sumOf { model.requestMs(it).toDouble() / it.periodMs.toDouble() }
        if (requestUtilization >= 1.0) {
            return AdmissionReport(
                AdmissionState.OVERLOADED, model.modelId, model.sourceEvidenceId,
                requestUtilization, requestUtilization, null, null, horizonMs,
                "REQUEST_UTILIZATION_NOT_BELOW_ONE"
            )
        }

        val placeholder = AdmissionReport(
            AdmissionState.UNKNOWN, model.modelId, model.sourceEvidenceId,
            requestUtilization, null, null, null, horizonMs, "SIMULATION_IN_PROGRESS"
        )
        val scheduler = DeadlineScheduler(specs, model)
        scheduler.startRun(0L, placeholder, SchedulerRunMode.DIAGNOSTIC_BEST_EFFORT)
        var nowMs = 0L
        var currentHeader: String? = null
        var busyMs = 0L
        var iterations = 0
        val cutoff = horizonMs - 1L
        while (iterations++ < 250_000) {
            when (val decision = scheduler.next(nowMs, currentHeader, cutoff)) {
                is SchedulerDecision.TerminalBatch -> Unit
                is SchedulerDecision.ChangeHeader -> {
                    val cost = decision.predictedSetupMs
                    busyMs = DeadlineScheduler.saturatedAdd(busyMs, cost)
                    nowMs = DeadlineScheduler.saturatedAdd(nowMs, cost)
                    currentHeader = decision.toHeader
                    scheduler.completeHeader(decision.token, nowMs, cost)
                }
                is SchedulerDecision.Dispatch -> {
                    val cost = DeadlineScheduler.saturatedAdd(
                        decision.predictedSetupMs, decision.predictedServiceMs
                    )
                    busyMs = DeadlineScheduler.saturatedAdd(busyMs, cost)
                    nowMs = DeadlineScheduler.saturatedAdd(nowMs, cost)
                    currentHeader = decision.toHeader
                    scheduler.complete(
                        decision.job.token, nowMs,
                        decision.predictedSetupMs, decision.predictedServiceMs
                    )
                }
                is SchedulerDecision.SleepUntil -> {
                    if (decision.timeMs == Long.MAX_VALUE) break
                    nowMs = maxOf(nowMs, decision.timeMs)
                }
            }
        }
        check(iterations < 250_000) { "Capacity replay did not converge" }
        val snapshot = scheduler.snapshot(nowMs)
        val deadlineMisses = snapshot.requests.sumOf { it.executedLate + it.expiredUnexecuted }
        val capacityRejects = snapshot.requests.sumOf { it.capacityRejected }
        val projectedUtilization = busyMs.toDouble() / horizonMs.toDouble()
        val carriesBeyondHorizon = nowMs > horizonMs || snapshot.requests.any { it.pending + it.inFlight > 0L }
        val admitted = deadlineMisses == 0L && capacityRejects == 0L &&
            projectedUtilization < 1.0 && !carriesBeyondHorizon
        return AdmissionReport(
            if (admitted) AdmissionState.ADMITTED else AdmissionState.OVERLOADED,
            model.modelId, model.sourceEvidenceId, requestUtilization,
            projectedUtilization, deadlineMisses, capacityRejects, horizonMs,
            if (admitted) {
                "ZERO_MISS_PRODUCTION_POLICY_REPLAY"
            } else if (carriesBeyondHorizon) {
                "PRODUCTION_POLICY_REPLAY_HORIZON_CARRY"
            } else {
                "PRODUCTION_POLICY_REPLAY_FAILED"
            }
        )
    }
}

internal data class SchedulerRequestSnapshot(
    val id: String,
    val header: String?,
    val periodMs: Long,
    val released: Long,
    val executedOnTime: Long,
    val executedLate: Long,
    val capacityRejected: Long,
    val expiredUnexecuted: Long,
    val transportUnavailable: Long,
    val sessionEnded: Long,
    val pending: Long,
    val inFlight: Long,
    val queueWaitP50Ms: Long,
    val queueWaitP95Ms: Long,
    val queueWaitMaxMs: Long,
    val setupP50Ms: Long,
    val setupP95Ms: Long,
    val setupMaxMs: Long,
    val serviceP50Ms: Long,
    val serviceP95Ms: Long,
    val serviceMaxMs: Long,
    val intervalP50Ms: Long,
    val intervalP95Ms: Long,
    val intervalMaxMs: Long,
    val latenessP50Ms: Long,
    val latenessP95Ms: Long,
    val latenessMaxMs: Long,
    val headerSwitches: Long,
    val elapsedMs: Long
) {
    val executions: Long get() = executedOnTime + executedLate
    val effectiveHz: Double get() =
        if (elapsedMs <= 0L) 0.0 else executions * 1000.0 / elapsedMs

    fun isConserved(): Boolean = released ==
        executedOnTime + executedLate + capacityRejected + expiredUnexecuted +
        transportUnavailable + sessionEnded + pending + inFlight
}

internal data class SchedulerSnapshot(
    val admission: AdmissionReport,
    val runMode: SchedulerRunMode,
    val requests: List<SchedulerRequestSnapshot>
) {
    init {
        require(requests.all { it.isConserved() }) { "Scheduler release accounting is not conserved" }
    }
}
