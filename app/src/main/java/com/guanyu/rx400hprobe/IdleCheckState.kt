package com.guanyu.rx400hprobe

import kotlin.math.abs

/**
 * Minimal Idle Check eligibility state (experimental).
 *
 * Candidate conditions from recovered HA evidence:
 * warmup active, 900 < RPM < 1100, ICE mechanical power ~0 kW,
 * speed <= 55 km/h, stable for ~1 s.
 *
 * This is NOT the HA S0-S4 reference state machine. It remains
 * experimental until replay validation against E1 logs and natural
 * real-vehicle observations confirm equivalence.
 */
class IdleCheckState(
    private val stabilityMs: Long = 1000L,
    private val rpmMin: Double = 900.0,
    private val rpmMax: Double = 1100.0,
    private val speedMaxKph: Double = 55.0,
    private val icePowerToleranceKw: Double = 0.05
) {
    var active: Boolean = false
        private set

    var stableSinceElapsedMs: Long? = null
        private set

    private var conditionsMet = false

    fun update(
        warmupActive: Boolean?,
        rpm: Double?,
        icePowerKw: Double?,
        speedKph: Double?,
        nowMs: Long
    ) {
        val met = warmupActive == true &&
            rpm != null && rpm > rpmMin && rpm < rpmMax &&
            icePowerKw != null && abs(icePowerKw) <= icePowerToleranceKw &&
            speedKph != null && speedKph <= speedMaxKph
        if (met) {
            if (!conditionsMet) stableSinceElapsedMs = nowMs
            conditionsMet = true
            active = (nowMs - stableSinceElapsedMs!!) >= stabilityMs
        } else {
            conditionsMet = false
            stableSinceElapsedMs = null
            active = false
        }
    }

    fun reset() {
        conditionsMet = false
        stableSinceElapsedMs = null
        active = false
    }
}
