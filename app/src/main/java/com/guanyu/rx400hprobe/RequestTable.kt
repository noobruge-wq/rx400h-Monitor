package com.guanyu.rx400hprobe

/** D064 whitelist. Four core blocks target ~3Hz; slow periods are unchanged. */
data class ScheduledRequest(
    val id: String, val header: String?, val command: String,
    val targetPeriodMs: Long, val phaseMs: Long = 0L, val timeoutMs: Long = 5000L
)
object RequestTable {
    // Configured target ~2.994Hz; missed releases are discarded, not caught up.
    const val CORE_PERIOD_MS = 334L
    val requests = listOf(
        ScheduledRequest("std_core", "7E0", "01040C0D0E10 2", CORE_PERIOD_MS),
        ScheduledRequest("cd_f3", "7E0", "21CDF3 3", CORE_PERIOD_MS),
        ScheduledRequest("coolant", "7E0", "01050607 1", 3000L, 1500L),
        ScheduledRequest("c3", "7E2", "21C3 6", CORE_PERIOD_MS, timeoutMs = 6000L),
        ScheduledRequest("c4", "7E2", "21C4 5", CORE_PERIOD_MS, timeoutMs = 6000L),
        ScheduledRequest("cf", "7E2", "21CF 4", 5000L, 2500L, 6000L),
        ScheduledRequest("atrv", null, "ATRV", 3000L, 1500L, 4000L)
    )
}
