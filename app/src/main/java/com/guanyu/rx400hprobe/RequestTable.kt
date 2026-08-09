package com.guanyu.rx400hprobe

/**
 * V0.2.0 fixed request table.
 *
 * Describes the whitelist with target periods and priorities so V0.3.0 can
 * implement deadline/priority scheduling. V0.2.0 deliberately keeps the
 * V0.1.10 polling periods unchanged.
 */
enum class RequestPriority { FAST, MEDIUM, SLOW, ADAPTER }

data class ScheduledRequest(
    val id: String,
    val header: String?,
    val command: String,
    val targetPeriodMs: Long,
    val priority: RequestPriority,
    val timeoutMs: Long = 5000L,
    val minimumGapMs: Long = 120L,
    val quietWindowMs: Long = 80L,
    val preDrainMs: Long = 80L
)

object RequestTable {
    const val CORE_CYCLE_MS = 800L

    val requests: List<ScheduledRequest> = listOf(
        ScheduledRequest("std_core", "7E0", "01040C0D0E10 2", 800L, RequestPriority.FAST),
        ScheduledRequest("cd_f3", "7E0", "21CDF3 3", 1000L, RequestPriority.FAST),
        ScheduledRequest("coolant", "7E0", "01050607 1", 3000L, RequestPriority.SLOW),
        ScheduledRequest("c3", "7E2", "21C3 6", 800L, RequestPriority.FAST, timeoutMs = 6000L),
        ScheduledRequest("c4", "7E2", "21C4 5", 1500L, RequestPriority.MEDIUM, timeoutMs = 6000L),
        ScheduledRequest("cf", "7E2", "21CF 4", 5000L, RequestPriority.SLOW, timeoutMs = 6000L),
        ScheduledRequest("atrv", null, "ATRV", 3000L, RequestPriority.ADAPTER, timeoutMs = 4000L)
    )

    fun period(id: String): Long = requests.first { it.id == id }.targetPeriodMs

    fun spec(id: String): ScheduledRequest = requests.first { it.id == id }
}
