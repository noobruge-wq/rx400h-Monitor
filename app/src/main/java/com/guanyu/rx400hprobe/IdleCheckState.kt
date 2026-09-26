package com.guanyu.rx400hprobe

import kotlin.math.abs

/** D-059: strict entry, wider hold, immediate loss of valid prerequisites.
 * Only a new power-source sample advances numerical entry/exit timers.
 * This remains an experimental current-activity indicator, not HA S0–S4.
 */
class IdleCheckState(
    private val stabilityMs: Long = 1000L,
    private val rpmMin: Double = 900.0,
    private val rpmMax: Double = 1100.0,
    private val speedMaxKph: Double = 55.0,
    private val icePowerToleranceKw: Double = 0.05,
    private val exitDelayMs: Long = 1000L
) {
    var active: Boolean = false
        private set

    private var stableSinceElapsedMs: Long? = null

    private var outsideSinceMs: Long? = null
    private var lastPowerSampleMs: Long? = null

    fun update(
        warmupActive: Boolean?,
        rpm: Double?,
        icePowerKw: Double?,
        speedKph: Double?,
        nowMs: Long,
        powerSampleMs: Long = nowMs
    ) {
        if (warmupActive != true || rpm?.isFinite() != true ||
            icePowerKw?.isFinite() != true || speedKph?.isFinite() != true
        ) {
            clear()
            return
        }
        val previousSample = lastPowerSampleMs
        if (powerSampleMs > nowMs || (previousSample != null && powerSampleMs < previousSample)) {
            clear()
            return
        }
        if (nowMs - powerSampleMs > 5000L) {
            clear()
            return
        }
        if (powerSampleMs == previousSample) return
        if (previousSample != null && powerSampleMs - previousSample > 5000L) clear()
        lastPowerSampleMs = powerSampleMs
        if (!active) {
            val enters = rpm > rpmMin && rpm < rpmMax &&
                abs(icePowerKw) <= icePowerToleranceKw && speedKph <= speedMaxKph
            if (!enters) {
                stableSinceElapsedMs = null
            } else {
                if (stableSinceElapsedMs == null) stableSinceElapsedMs = powerSampleMs
                active = powerSampleMs - stableSinceElapsedMs!! >= stabilityMs
            }
            return
        }
        val outside = rpm <= 850.0 || rpm >= 1200.0 ||
            abs(icePowerKw) > 0.30 || speedKph > speedMaxKph
        if (!outside) {
            outsideSinceMs = null
        } else {
            if (outsideSinceMs == null) outsideSinceMs = powerSampleMs
            if (powerSampleMs - outsideSinceMs!! >= exitDelayMs) {
                active = false
                stableSinceElapsedMs = null
                outsideSinceMs = null
            }
        }
    }

    fun reset() {
        clear()
    }

    private fun clear() {
        stableSinceElapsedMs = null
        active = false
        outsideSinceMs = null
        lastPowerSampleMs = null
    }
}
