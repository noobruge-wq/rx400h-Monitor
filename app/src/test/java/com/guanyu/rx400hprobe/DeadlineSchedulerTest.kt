package com.guanyu.rx400hprobe

import org.junit.Assert.assertEquals
import org.junit.Test

class DeadlineSchedulerTest {

    private data class RequestStep(
        val decision: SchedulerDecision.Dispatch,
        val nowMs: Long,
        val header: String?
    )

    private val specs = listOf(
        ScheduledSpec("std_core", "7E0", 800L, RequestPriority.FAST),
        ScheduledSpec("cd_f3", "7E0", 1000L, RequestPriority.FAST),
        ScheduledSpec("c3", "7E2", 800L, RequestPriority.FAST),
        ScheduledSpec("c4", "7E2", 1500L, RequestPriority.MEDIUM)
    )

    private fun model(
        requestMs: Long = 50L,
        headerMs: Long = 30L,
        trusted: Boolean = false,
        samples: Int = if (trusted) 40 else 0
    ) = SchedulerCostModel(
        modelId = "test",
        sourceEvidenceId = "synthetic",
        requestCosts = specs.associate { it.id to SchedulerCostEstimate(requestMs, samples, trusted) },
        headerSetupCost = SchedulerCostEstimate(headerMs, samples, trusted)
    )

    private fun scheduler(
        selectedSpecs: List<ScheduledSpec> = specs,
        costModel: SchedulerCostModel = model()
    ): DeadlineScheduler = DeadlineScheduler(selectedSpecs, costModel).also {
        it.startRun(
            0L,
            AdmissionReport.unknown("TEST"),
            SchedulerRunMode.DIAGNOSTIC_BEST_EFFORT
        )
    }

    private fun nextRequest(
        scheduler: DeadlineScheduler,
        startMs: Long,
        initialHeader: String?
    ): RequestStep {
        var nowMs = startMs
        var header = initialHeader
        while (true) {
            when (val decision = scheduler.next(nowMs, header)) {
                is SchedulerDecision.ChangeHeader -> {
                    nowMs += decision.predictedSetupMs
                    scheduler.completeHeader(decision.token, nowMs, decision.predictedSetupMs)
                    header = decision.toHeader
                }
                is SchedulerDecision.Dispatch -> return RequestStep(decision, nowMs, header)
                is SchedulerDecision.TerminalBatch -> Unit
                is SchedulerDecision.SleepUntil -> error("Expected request, sleeping until ${decision.timeMs}")
            }
        }
    }

    @Test
    fun completionDoesNotMoveTheAbsoluteReleaseTimeline() {
        val spec = listOf(specs[0])
        val costs = SchedulerCostModel(
            "one", "synthetic",
            mapOf(spec[0].id to SchedulerCostEstimate(50L, 0, false)),
            SchedulerCostEstimate(30L, 0, false)
        )
        val s = scheduler(spec, costs)
        val first = nextRequest(s, 0L, null)
        s.complete(first.decision.job.token, 500L, 0L, 470L)

        assertEquals(800L, (s.next(500L, "7E0") as SchedulerDecision.SleepUntil).timeMs)
        val second = s.next(800L, "7E0") as SchedulerDecision.Dispatch
        assertEquals(800L, second.job.releaseAtMs)
    }

    @Test
    fun deadlineBoundaryIsOnTimeAndOneMillisecondLaterIsLate() {
        val one = listOf(specs[0])
        val costs = SchedulerCostModel(
            "one", "synthetic",
            mapOf(one[0].id to SchedulerCostEstimate(50L, 0, false)),
            SchedulerCostEstimate(30L, 0, false)
        )
        val onTime = scheduler(one, costs)
        val first = nextRequest(onTime, 0L, null)
        assertEquals(
            SchedulerOutcome.EXECUTED_ON_TIME,
            onTime.complete(first.decision.job.token, 800L, 0L, 770L).outcome
        )

        val late = scheduler(one, costs)
        val second = nextRequest(late, 0L, null)
        assertEquals(
            SchedulerOutcome.EXECUTED_LATE,
            late.complete(second.decision.job.token, 801L, 0L, 771L).outcome
        )
    }

    @Test
    fun longStallAccountsEveryReleaseButRetainsOnlyLatest() {
        val one = listOf(ScheduledSpec("x", "7E0", 1000L, RequestPriority.FAST))
        val costs = SchedulerCostModel(
            "one", "synthetic", mapOf("x" to SchedulerCostEstimate(50L, 0, false)),
            SchedulerCostEstimate(30L, 0, false)
        )
        val s = scheduler(one, costs)
        val first = s.next(3500L, null) as SchedulerDecision.TerminalBatch
        assertEquals(3L, first.count)
        assertEquals(SchedulerOutcome.EXPIRED_UNEXECUTED, first.outcome)
        val dispatch = nextRequest(s, 3500L, null).decision
        assertEquals(3L, dispatch.job.token.sequence)
        assertEquals(3000L, dispatch.job.releaseAtMs)
        assertEquals(4L, s.snapshot().requests.single().released)
    }

    @Test
    fun plannerUsesHaLikeHeaderPairsWhenAllCoreRequestsAreDue() {
        val s = scheduler()
        var now = 0L
        var header: String? = null
        val transactions = mutableListOf<String>()
        repeat(6) {
            when (val decision = s.next(now, header)) {
                is SchedulerDecision.ChangeHeader -> {
                    transactions += "ATSH${decision.toHeader}"
                    now += decision.predictedSetupMs
                    s.completeHeader(decision.token, now, decision.predictedSetupMs)
                    header = decision.toHeader
                }
                is SchedulerDecision.Dispatch -> {
                    transactions += specs[decision.specIndex].id
                    now += decision.predictedServiceMs
                    s.complete(decision.job.token, now, 0L, decision.predictedServiceMs)
                }
                else -> error("Unexpected scheduler decision $decision")
            }
        }
        assertEquals(
            listOf("ATSH7E0", "std_core", "cd_f3", "ATSH7E2", "c3", "c4"),
            transactions
        )
        assertEquals(2L, s.headerSwitches)
    }

    @Test
    fun headerCompletionReplansBeforeSendingTheRequest() {
        val one = listOf(ScheduledSpec("x", "7E0", 100L, RequestPriority.FAST))
        val costs = SchedulerCostModel(
            "one", "synthetic", mapOf("x" to SchedulerCostEstimate(20L, 0, false)),
            SchedulerCostEstimate(10L, 0, false)
        )
        val s = scheduler(one, costs)
        val header = s.next(0L, null) as SchedulerDecision.ChangeHeader
        s.completeHeader(header.token, 90L, 90L)

        val terminal = s.next(90L, "7E0") as SchedulerDecision.TerminalBatch
        assertEquals(SchedulerOutcome.REJECTED_CAPACITY, terminal.outcome)
        assertEquals(0L, s.executions)
    }

    @Test
    fun atrvDoesNotChangeOrClearCurrentHeader() {
        val one = listOf(ScheduledSpec("atrv", null, 3000L, RequestPriority.ADAPTER))
        val costs = SchedulerCostModel(
            "one", "synthetic", mapOf("atrv" to SchedulerCostEstimate(50L, 0, false)),
            SchedulerCostEstimate(30L, 0, false)
        )
        val dispatch = scheduler(one, costs).next(0L, "7E2") as SchedulerDecision.Dispatch
        assertEquals("7E2", dispatch.toHeader)
        assertEquals(false, dispatch.requiresHeaderSwitch)
        assertEquals(0L, dispatch.predictedSetupMs)
    }

    @Test
    fun capacitySheddingRejectsLowerPriorityFirst() {
        val overloaded = model(requestMs = 700L, headerMs = 300L)
        val s = scheduler(costModel = overloaded)
        val terminal = s.next(0L, null) as SchedulerDecision.TerminalBatch
        assertEquals("c4", specs[terminal.specIndex].id)
        assertEquals(SchedulerOutcome.REJECTED_CAPACITY, terminal.outcome)
    }

    @Test
    fun transportDowntimeKeepsEpochAndAccountsUnavailableReleases() {
        val one = listOf(ScheduledSpec("x", "7E0", 1000L, RequestPriority.FAST))
        val costs = SchedulerCostModel(
            "one", "synthetic", mapOf("x" to SchedulerCostEstimate(50L, 0, false)),
            SchedulerCostEstimate(30L, 0, false)
        )
        val s = scheduler(one, costs)
        s.transportDown(100L)
        s.transportUp(3500L)
        val snapshot = s.snapshot(3500L).requests.single()
        assertEquals(4L, snapshot.released)
        assertEquals(4L, snapshot.transportUnavailable)
        assertEquals(true, snapshot.isConserved())
        assertEquals(100L, (s.next(3500L, null) as SchedulerDecision.TerminalBatch).recordedAtMs)
        assertEquals(3500L, (s.next(3500L, null) as SchedulerDecision.TerminalBatch).recordedAtMs)
        assertEquals(4000L, (s.next(3500L, null) as SchedulerDecision.SleepUntil).timeMs)
    }

    @Test
    fun everyReleaseHasExactlyOneTerminalOrLiveState() {
        val s = scheduler()
        var now = 0L
        var header: String? = null
        repeat(20) {
            when (val decision = s.next(now, header)) {
                is SchedulerDecision.Dispatch -> {
                    now += decision.predictedServiceMs
                    header = decision.toHeader
                    s.complete(
                        decision.job.token,
                        now,
                        0L,
                        decision.predictedServiceMs
                    )
                }
                is SchedulerDecision.ChangeHeader -> {
                    now += decision.predictedSetupMs
                    header = decision.toHeader
                    s.completeHeader(decision.token, now, decision.predictedSetupMs)
                }
                is SchedulerDecision.SleepUntil -> now = decision.timeMs
                is SchedulerDecision.TerminalBatch -> Unit
            }
        }
        assertEquals(true, s.snapshot(now).requests.all { it.isConserved() })
    }

    @Test
    fun incompleteCostEvidenceIsUnknown() {
        assertEquals(AdmissionState.UNKNOWN, CapacityAdmission.assess(specs, model()).state)
    }

    @Test
    fun missingCostEntryIsUnknownInsteadOfThrowing() {
        val incomplete = SchedulerCostModel(
            "missing", "synthetic", emptyMap(), SchedulerCostEstimate(10L, 40, true)
        )
        assertEquals(AdmissionState.UNKNOWN, CapacityAdmission.assess(specs, incomplete).state)
    }

    @Test
    fun necessaryUtilizationAboveOneIsOverloaded() {
        val report = CapacityAdmission.assess(specs, model(requestMs = 900L, trusted = true))
        assertEquals(AdmissionState.OVERLOADED, report.state)
        assertEquals("REQUEST_UTILIZATION_NOT_BELOW_ONE", report.reason)
    }

    @Test
    fun feasibleTrustedModelPassesDeterministicHyperperiodReplay() {
        val report = CapacityAdmission.assess(specs, model(requestMs = 35L, headerMs = 25L, trusted = true))
        assertEquals(AdmissionState.ADMITTED, report.state)
        assertEquals(0L, report.projectedDeadlineMisses)
        assertEquals(0L, report.projectedCapacityRejections)
    }

    @Test
    fun horizonCarryCannotBeAdmitted() {
        val boundarySpecs = listOf(
            ScheduledSpec("a", null, 4L, RequestPriority.FAST, deadlineMs = 4L),
            ScheduledSpec("b", null, 8L, RequestPriority.FAST, deadlineMs = 8L, phaseMs = 7L)
        )
        val costs = SchedulerCostModel(
            "boundary", "synthetic",
            mapOf(
                "a" to SchedulerCostEstimate(1L, 40, true),
                "b" to SchedulerCostEstimate(5L, 40, true)
            ),
            SchedulerCostEstimate(1L, 40, true)
        )
        val report = CapacityAdmission.assess(boundarySpecs, costs, horizonMs = 8L)
        assertEquals(AdmissionState.OVERLOADED, report.state)
        assertEquals("PRODUCTION_POLICY_REPLAY_HORIZON_CARRY", report.reason)
    }

    @Test
    fun endBoundaryIsExclusiveWithTransportUpOrDown() {
        val one = listOf(ScheduledSpec("x", "7E0", 1000L, RequestPriority.FAST))
        val costs = SchedulerCostModel(
            "one", "synthetic", mapOf("x" to SchedulerCostEstimate(50L, 0, false)),
            SchedulerCostEstimate(30L, 0, false)
        )
        val available = scheduler(one, costs)
        available.finishRun(1000L)
        val availableSnapshot = available.snapshot(1000L).requests.single()

        val unavailable = scheduler(one, costs)
        unavailable.transportDown(100L)
        unavailable.finishRun(1000L)
        val unavailableSnapshot = unavailable.snapshot(1000L).requests.single()

        assertEquals(1L, availableSnapshot.released)
        assertEquals(1L, unavailableSnapshot.released)
        assertEquals(true, availableSnapshot.isConserved())
        assertEquals(true, unavailableSnapshot.isConserved())
    }

    @Test
    fun legacyDeadlineAndSkipColumnsAreMutuallyExclusive() {
        val one = listOf(ScheduledSpec("x", null, 100L, RequestPriority.FAST))
        val costs = SchedulerCostModel(
            "one", "synthetic", mapOf("x" to SchedulerCostEstimate(20L, 0, false)),
            SchedulerCostEstimate(10L, 0, false)
        )
        val expired = scheduler(one, costs)
        expired.next(250L, null)
        assertEquals(0L, expired.deadlineMisses)
        assertEquals(2L, expired.skippedOverdue)

        val late = scheduler(one, costs)
        val dispatch = late.next(0L, null) as SchedulerDecision.Dispatch
        late.complete(dispatch.job.token, 101L, 0L, 101L)
        assertEquals(1L, late.deadlineMisses)
        assertEquals(0L, late.skippedOverdue)
    }

    @Test
    fun latencyWindowPercentilesAndMaxRemainBounded() {
        val w = LatencyWindow(4)
        w.add(10); w.add(20); w.add(30); w.add(40)
        assertEquals(10L, w.percentile(0.0))
        assertEquals(20L, w.percentile(0.5))
        assertEquals(40L, w.percentile(1.0))
        assertEquals(40L, w.max())
        w.add(5)
        assertEquals(40L, w.max())
        w.clear()
        assertEquals(0L, w.percentile(0.5))
    }
}
