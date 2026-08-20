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

/**
 * Pure runtime facts used to decide whether a hardware-trained scheduler cost
 * model applies. Android Build/Bluetooth APIs stay outside this policy type.
 */
internal data class SchedulerCostApplicabilityContext(
    val apiLevel: Int,
    val manufacturer: String,
    val model: String,
    val device: String,
    val adapterName: String?
)

object RequestTable {
    private const val TRUSTED_API_LEVEL = 27
    private const val TRUSTED_MANUFACTURER = "sprd"
    private const val TRUSTED_MODEL = "sp7731e_1h10_native"
    private const val TRUSTED_DEVICE = "sp7731e_1h10"
    private val trustedAdapterName = Regex("OBDLink MX\\+(?: [A-Za-z0-9_-]+)?")

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
     * D-051 target-head-unit API 27 p95 model. Values are the conservative
     * maximum of the per-session empirical p95 values from the two clean,
     * exact-commit 2026-08-15 same-period runs. The normal header directions
     * have 4,500 and 4,498 samples. The separate 154 ms cold-start maximum has
     * only two observations; it bounds the first setup without claiming
     * statistical p95 trust or inflating every later header switch.
     */
    internal val trustedCostModel: SchedulerCostModel = SchedulerCostModel(
        modelId = "api27_sp7731e_obdlink_v030_capacity_002_p95_v1",
        sourceEvidenceId = listOf(
            "zip_sha256=0dc6b6a71f40365b18febe6a815eeb13f2a6bb32ee0a4dcb6237091421a245f7",
            "zip_sha256=d304e9dfabb1f1d4a28eda9b9c0fc674c04276ce119e8ea5a94e8cdf783432d7",
            "commit=8e55c6afae20ca64b9ea9bba5861bc85d8017c62",
            "apk_sha256=841b1a4adb9f9e4a1834d2830dd6e94754a54cbcc3b2b3209023061da1969e9b",
            "app=0.3.2_v24",
            "api=27",
            "manufacturer=sprd",
            "model=sp7731e_1h10_native",
            "device=sp7731e_1h10",
            "adapter_name=OBDLink MX+ 99905",
            "cold_none_to_7e2=154ms_engineering_bound_not_empirical_p95",
            "method=scheduler_events_sorted_floor_index_(n-1)*0.95_per_session_then_max"
        ).joinToString(";"),
        requestCosts = mapOf(
            "std_core" to SchedulerCostEstimate(141L, 5_631, true),
            "cd_f3" to SchedulerCostEstimate(139L, 4_503, true),
            "coolant" to SchedulerCostEstimate(139L, 1_501, true),
            "c3" to SchedulerCostEstimate(162L, 5_630, true),
            "c4" to SchedulerCostEstimate(169L, 3_000, true),
            "cf" to SchedulerCostEstimate(151L, 900, true),
            "atrv" to SchedulerCostEstimate(81L, 1_498, true)
        ),
        // Conservative trusted fallback for any normal direction not covered
        // by this frozen two-header table. Exact normal directions override it.
        headerSetupCost = SchedulerCostEstimate(116L, 8_998, true),
        periodicHeaderCosts = mapOf(
            SchedulerHeaderTransition("7E0", "7E2") to
                SchedulerCostEstimate(65L, 4_500, true),
            SchedulerHeaderTransition("7E2", "7E0") to
                SchedulerCostEstimate(116L, 4_498, true)
        ),
        coldStartHeaderCosts = mapOf(
            "7E0" to SchedulerCostEstimate(154L, 2, false),
            // No direct NONE -> 7E2 sample exists. Use the observed cold
            // maximum as an explicit fail-safe engineering bound (0 samples),
            // never as statistical p95 evidence or periodic trust.
            "7E2" to SchedulerCostEstimate(154L, 0, false)
        )
    )

    /** Original fail-closed seed for hardware outside the pinned D-051 scope. */
    internal val diagnosticCostModel: SchedulerCostModel = SchedulerCostModel(
        modelId = "ha_hci_159ms_conservative_seed_v1",
        sourceEvidenceId = "rx400h_ha_hci_20260805_002:aggregate_only",
        requestCosts = requests.associate { request ->
            request.id to SchedulerCostEstimate(p95Ms = 120L, sampleCount = 0, trusted = false)
        },
        headerSetupCost = SchedulerCostEstimate(p95Ms = 100L, sampleCount = 0, trusted = false)
    )

    /**
     * Selects the trusted model only for the exact target head-unit identity
     * and case-sensitive OBDLink MX+ family name recorded by D-051.
     */
    internal fun schedulerCostModelFor(
        context: SchedulerCostApplicabilityContext
    ): SchedulerCostModel = if (trustedCostModelApplies(context)) {
        trustedCostModel
    } else {
        diagnosticCostModel
    }

    internal fun trustedCostModelApplies(
        context: SchedulerCostApplicabilityContext
    ): Boolean = context.apiLevel == TRUSTED_API_LEVEL &&
        context.manufacturer == TRUSTED_MANUFACTURER &&
        context.model == TRUSTED_MODEL &&
        context.device == TRUSTED_DEVICE &&
        context.adapterName?.let(trustedAdapterName::matches) == true
}
