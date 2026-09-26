package com.guanyu.rx400hprobe

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure presentation math for the fixed V0.3.5 pixel dashboard. */
internal object PixelDashboardModel {
    const val LOGICAL_WIDTH = 640
    const val LOGICAL_HEIGHT = 360
    const val OUTPUT_WIDTH = 1280
    const val OUTPUT_HEIGHT = 720
    const val LOGICAL_SCALE = 2
    const val POWER_LIMIT_KW = 50f

    fun newSelfTestSource(armedVersion: Long?, version: Long, previousFresh: Boolean?, fresh: Boolean): Boolean =
        fresh && (armedVersion == null || version != armedVersion || previousFresh == false)

    fun socVisualFraction(socPercent: Float): Float {
        val value = finiteOrZero(socPercent).coerceIn(0f, 100f)
        return when {
            value <= 30f -> value / 30f * 0.15f
            value <= 80f -> 0.15f + (value - 30f) / 50f * 0.75f
            else -> 0.90f + (value - 80f) / 20f * 0.10f
        }
    }

    fun powerSegments(powerKw: Float, out: PowerSegments = PowerSegments.unknown()): PowerSegments {
        if (!powerKw.isFinite()) return out.set(0f, 0f, false)
        val value = powerKw.coerceIn(-POWER_LIMIT_KW, POWER_LIMIT_KW)
        return out.set(
            leftKw = if (value < 0f) value else 0f,
            rightKw = if (value > 0f) value else 0f,
            known = true
        )
    }

    fun powerSelfTestSegments(elapsedMs: Long, goalKw: Float, out: PowerSegments = PowerSegments.unknown()): PowerSegments {
        val boundedGoal = finiteOrZero(goalKw).coerceIn(-POWER_LIMIT_KW, POWER_LIMIT_KW)
        if (elapsedMs <= 600L) {
            val fraction = (elapsedMs.coerceAtLeast(0L) / 600f).coerceIn(0f, 1f)
            return out.set(-POWER_LIMIT_KW * fraction, POWER_LIMIT_KW * fraction, true)
        }
        val fraction = ((elapsedMs - 600L) / 400f).coerceIn(0f, 1f)
        return when {
            boundedGoal < 0f -> out.set(
                leftKw = -POWER_LIMIT_KW + (boundedGoal + POWER_LIMIT_KW) * fraction,
                rightKw = POWER_LIMIT_KW * (1f - fraction),
                known = true
            )
            boundedGoal > 0f -> out.set(
                leftKw = -POWER_LIMIT_KW * (1f - fraction),
                rightKw = POWER_LIMIT_KW + (boundedGoal - POWER_LIMIT_KW) * fraction,
                known = true
            )
            else -> out.set(
                leftKw = -POWER_LIMIT_KW * (1f - fraction),
                rightKw = POWER_LIMIT_KW * (1f - fraction),
                known = true
            )
        }
    }

    fun startupSoc(elapsedMs: Long, goalPercent: Float): Float {
        val goal = finiteOrZero(goalPercent).coerceIn(0f, 100f)
        return if (elapsedMs <= 600L) {
            100f * (elapsedMs.coerceAtLeast(0L) / 600f).coerceIn(0f, 1f)
        } else {
            100f + (goal - 100f) * ((elapsedMs - 600L) / 400f).coerceIn(0f, 1f)
        }
    }

    fun wheelRevolutionsPerSecond(speedKph: Float): Float {
        if (!speedKph.isFinite() || speedKph <= 0f) return 0f
        return speedKph.coerceIn(0f, 120f) / 120f * 2.5f
    }

    fun animationFrameNeeded(
        settingsOpen: Boolean,
        contentAnimationActive: Boolean,
        wheelTransitionActive: Boolean,
        wheelRps: Float
    ): Boolean = contentAnimationActive ||
        (!settingsOpen && (wheelTransitionActive || wheelRps > 0.001f))

    fun wheelAlpha(speedKph: Double?, fresh: Boolean, movingAlpha: Float): Float =
        if (fresh && speedKph != null && speedKph.isFinite() && speedKph > 0.0) movingAlpha else 0.40f

    fun smoothStep(from: Float, to: Float, elapsedMs: Long, durationMs: Long): Float {
        if (!to.isFinite()) return Float.NaN
        if (!from.isFinite() || durationMs <= 0L) return to
        val progress = (elapsedMs.coerceAtLeast(0L) / durationMs.toFloat()).coerceIn(0f, 1f)
        val eased = progress * progress * (3f - 2f * progress)
        return from + (to - from) * eased
    }

    fun formatSoc(value: Double?, fresh: Boolean): String =
        ifKnown(value, fresh, "--.- %") { String.format(Locale.US, "%.1f %%", it) }

    fun formatTemperature(value: Double?, fresh: Boolean): String =
        ifKnown(value, fresh, "--.- °C") { String.format(Locale.US, "%.1f °C", it) }

    fun formatTemperatureRange(maximum: Double?, minimum: Double?, fresh: Boolean): String =
        if (fresh && finite(maximum) != null && finite(minimum) != null) {
            String.format(Locale.US, "%.1f / %.1f°", maximum, minimum)
        } else {
            "--.- / --.-°"
        }

    fun formatSpeed(value: Double?, fresh: Boolean): String =
        ifKnown(value, fresh, "-- km/h") { "${it.roundToInt()} km/h" }

    fun formatCoolant(value: Double?, fresh: Boolean): String =
        ifKnown(value, fresh, "-- °C") { "${it.roundToInt()} °C" }

    fun formatVoltage(value: Double?, fresh: Boolean): String =
        ifKnown(value, fresh, "--.- V") { String.format(Locale.US, "%.1f V", it) }

    fun formatRpm(value: Double?, fresh: Boolean): String =
        ifKnown(value, fresh, "---- rpm") { "${it.roundToInt()} rpm" }

    fun formatPower(value: Double?, fresh: Boolean): String =
        ifKnown(value, fresh, "--.- kW") {
            val sign = if (it < 0.0) "−" else " "
            String.format(Locale.US, "%s%.1f kW", sign, abs(it))
        }

    private inline fun ifKnown(
        value: Double?,
        fresh: Boolean,
        fallback: String,
        block: (Double) -> String
    ): String = finite(value).takeIf { fresh }?.let(block) ?: fallback

    private fun finite(value: Double?): Double? = value?.takeIf { it.isFinite() }

    private fun finiteOrZero(value: Float): Float = if (value.isFinite()) value else 0f
}

internal data class PowerSegments(
    var leftKw: Float,
    var rightKw: Float,
    var known: Boolean
) {
    fun set(leftKw: Float, rightKw: Float, known: Boolean): PowerSegments {
        this.leftKw = leftKw
        this.rightKw = rightKw
        this.known = known
        return this
    }
    companion object {
        fun unknown() = PowerSegments(0f, 0f, false)
    }
}

internal data class CrtEffectSettings(
    val scanlineOpacity: Int = 75,
    val scanlineWidthPx: Int = 1,
    val scanlineGapPx: Int = 1,
    val glowIntensity: Int = 100,
    val glowRadiusPx: Int = 8
) {
    fun bounded(): CrtEffectSettings = copy(
        scanlineOpacity = scanlineOpacity.coerceIn(0, 100),
        scanlineWidthPx = scanlineWidthPx.coerceIn(1, 4),
        scanlineGapPx = scanlineGapPx.coerceIn(1, 12),
        glowIntensity = glowIntensity.coerceIn(0, 100),
        glowRadiusPx = glowRadiusPx.coerceIn(0, 8)
    )
}
