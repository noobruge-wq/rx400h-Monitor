package com.guanyu.rx400hprobe

/**
 * V0.3.0 fixed request table.
 *
 * Describes the whitelist with target periods and priorities so V0.3.0 can
 * implement deadline/priority scheduling. Periods stay at the V0.1.10/V0.2.0
 * values until staged frequency tests provide evidence to change them.
 */
enum class RequestPriority { FAST, MEDIUM, SLOW, ADAPTER }

data class ScheduledRequest(
    val id: String,
    val header: String?,
    val command: String,
    val targetPeriodMs: Long,
    val priority: RequestPriority,
    val phaseMs: Long = 0L,
    val deadlineMs: Long = targetPeriodMs,
    val timeoutMs: Long = 5000L,
    val minimumGapMs: Long = 0L,
    val quietWindowMs: Long = 0L,
    val preDrainMs: Long = 0L
)

object RequestTable {
    val requests: List<ScheduledRequest> = listOf(
        // The four HA/HCI core requests start together so the planner can form the
        // proven 7E0 pair -> 7E2 pair. Slow/adapter work is phase-spread to avoid
        // an artificial seven-request burst at the LIVE boundary.
        ScheduledRequest("std_core", "7E0", "01040C0D0E10 2", 800L, RequestPriority.FAST),
        ScheduledRequest("cd_f3", "7E0", "21CDF3 3", 1000L, RequestPriority.FAST),
        ScheduledRequest(
            "coolant", "7E0", "01050607 1", 3000L, RequestPriority.SLOW,
            phaseMs = 1500L
        ),
        ScheduledRequest(
            "c3", "7E2", "21C3 6", 800L, RequestPriority.FAST,
            timeoutMs = 6000L
        ),
        ScheduledRequest(
            "c4", "7E2", "21C4 5", 1500L, RequestPriority.MEDIUM,
            timeoutMs = 6000L
        ),
        ScheduledRequest(
            "cf", "7E2", "21CF 4", 5000L, RequestPriority.SLOW,
            phaseMs = 2500L, timeoutMs = 6000L
        ),
        ScheduledRequest(
            "atrv", null, "ATRV", 3000L, RequestPriority.ADAPTER,
            phaseMs = 1500L, timeoutMs = 4000L
        )
    )

    internal val schedulerSpecs: List<ScheduledSpec> = requests.map { request ->
        ScheduledSpec(
            id = request.id,
            header = request.header,
            periodMs = request.targetPeriodMs,
            priority = request.priority,
            deadlineMs = request.deadlineMs,
            phaseMs = request.phaseMs
        )
    }

    /**
     * Conservative planning seed, not an admission claim. HA/HCI proves that a
     * six-command serial core can complete in ~159 ms, but does not provide the
     * per-command p95 samples required for ADMITTED. Runtime observations may
     * raise these planning costs; the rate ladder stays blocked while UNKNOWN.
     */
    internal val diagnosticCostModel: SchedulerCostModel = SchedulerCostModel(
        modelId = "ha_hci_159ms_conservative_seed_v1",
        sourceEvidenceId = "rx400h_ha_hci_20260805_002:aggregate_only",
        requestCosts = requests.associate { request ->
            request.id to SchedulerCostEstimate(p95Ms = 120L, sampleCount = 0, trusted = false)
        },
        headerSetupCost = SchedulerCostEstimate(p95Ms = 100L, sampleCount = 0, trusted = false)
    )
}
