package com.guanyu.rx400hprobe

import kotlin.math.abs

/** Read-side freshness: never mutate single-writer store from the UI thread. */
internal object DashboardFreshness {
    const val FAST_AGE_MS = 5000L
    const val TEMPERATURE_AGE_MS = 12000L
    // Shorter of the frozen RPM/torque periods, not a decoder constant.
    const val POWER_PAIR_SKEW_MS = 800L

    fun fresh(live: Boolean, connected: Boolean, valid: Boolean,
              sampledAtMs: Long?, nowMs: Long, maxAgeMs: Long = FAST_AGE_MS): Boolean {
        if (!live || !connected || !valid || sampledAtMs == null) return false
        return sampledAtMs <= nowMs && nowMs - sampledAtMs <= maxAgeMs
    }

    fun powerPair(rpmMs: Long?, torqueMs: Long?): Boolean =
        rpmMs != null && torqueMs != null && abs(rpmMs - torqueMs) <= POWER_PAIR_SKEW_MS
}
