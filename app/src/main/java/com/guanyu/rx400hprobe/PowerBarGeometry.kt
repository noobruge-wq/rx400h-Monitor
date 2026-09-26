package com.guanyu.rx400hprobe

import kotlin.math.roundToInt

/** Authored mask bounds define travel; authored cursor position defines zero.
 * Moving a JSON layer never requires changing an absolute Kotlin coordinate.
 */
internal data class PowerBarGeometry(val maskLeft: Int, val maskRight: Int, val cursorZero: Int) {
    init { require(maskRight >= maskLeft) }
    val zeroLocal = (maskLeft + maskRight) / 2
    private fun offset(power: Float): Float {
        val bounded = power.coerceIn(-50f, 50f)
        val span = if (bounded < 0f) zeroLocal - maskLeft else maskRight - zeroLocal
        return bounded / 50f * span
    }
    fun fillX(power: Float): Int = (zeroLocal + offset(power)).roundToInt()
    fun cursorX(power: Float): Float = cursorZero + offset(power)
}
