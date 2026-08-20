package com.guanyu.rx400hprobe

internal enum class WallClockAdjustmentDirection {
    FORWARD,
    BACKWARD
}

/**
 * A material divergence between wall-clock and monotonic progression.
 *
 * [adjustmentMs] is signed: positive values are forward wall-clock steps and
 * negative values are backward steps. The remaining fields retain the bounded
 * before/expected/observed context needed for an evidence event.
 */
internal data class WallClockAdjustment(
    val direction: WallClockAdjustmentDirection,
    val adjustmentMs: Long,
    val anchorElapsedRealtimeMs: Long,
    val observedElapsedRealtimeMs: Long,
    val anchorWallTimeMs: Long,
    val expectedWallTimeMs: Long,
    val observedWallTimeMs: Long
)

/**
 * Detects wall-clock movement without using wall time as control truth.
 *
 * The first observation establishes an anchor. Later observations compare the
 * observed wall time with the wall time implied by monotonic elapsed time. A
 * material report rebases the anchor so the same adjustment is not emitted
 * repeatedly. Normal observations use primitive state only and allocate no
 * report object; this class owns no timer or scheduler.
 */
internal class WallClockAdjustmentDetector(
    private val thresholdMs: Long = DEFAULT_THRESHOLD_MS
) {
    private var initialized = false
    private var anchorElapsedRealtimeMs = 0L
    private var anchorWallTimeMs = 0L

    init {
        require(thresholdMs > 0L) { "thresholdMs must be positive" }
    }

    fun observe(elapsedRealtimeMs: Long, wallTimeMs: Long): WallClockAdjustment? {
        if (!initialized) {
            rebase(elapsedRealtimeMs, wallTimeMs)
            return null
        }

        require(elapsedRealtimeMs >= anchorElapsedRealtimeMs) {
            "Monotonic elapsed time moved backwards"
        }

        val elapsedDeltaMs = elapsedRealtimeMs - anchorElapsedRealtimeMs
        val expectedWallTimeMs = anchorWallTimeMs + elapsedDeltaMs
        val adjustmentMs = wallTimeMs - expectedWallTimeMs
        if (adjustmentMs < thresholdMs && adjustmentMs > -thresholdMs) return null

        val report = WallClockAdjustment(
            direction = if (adjustmentMs > 0L) {
                WallClockAdjustmentDirection.FORWARD
            } else {
                WallClockAdjustmentDirection.BACKWARD
            },
            adjustmentMs = adjustmentMs,
            anchorElapsedRealtimeMs = anchorElapsedRealtimeMs,
            observedElapsedRealtimeMs = elapsedRealtimeMs,
            anchorWallTimeMs = anchorWallTimeMs,
            expectedWallTimeMs = expectedWallTimeMs,
            observedWallTimeMs = wallTimeMs
        )
        rebase(elapsedRealtimeMs, wallTimeMs)
        return report
    }

    /** Clears session-local history; the next observation becomes the anchor. */
    fun reset() {
        initialized = false
        anchorElapsedRealtimeMs = 0L
        anchorWallTimeMs = 0L
    }

    private fun rebase(elapsedRealtimeMs: Long, wallTimeMs: Long) {
        anchorElapsedRealtimeMs = elapsedRealtimeMs
        anchorWallTimeMs = wallTimeMs
        initialized = true
    }

    companion object {
        const val DEFAULT_THRESHOLD_MS = 2_000L
    }
}
