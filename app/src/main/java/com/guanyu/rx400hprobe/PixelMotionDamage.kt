package com.guanyu.rx400hprobe

/** Compare final integer raster geometry, never approximate floating values. */
internal class PixelMotionDamage {
    private val previous = IntArray(5) { Int.MIN_VALUE }
    fun update(soc: Int, leftFill: Int, rightFill: Int, leftCursor: Int, rightCursor: Int): Int {
        val socChanged = previous[0] != soc
        val powerChanged = previous[1] != leftFill || previous[2] != rightFill ||
            previous[3] != leftCursor || previous[4] != rightCursor
        previous[0] = soc
        previous[1] = leftFill
        previous[2] = rightFill
        previous[3] = leftCursor
        previous[4] = rightCursor
        return (if (socChanged) 1 else 0) or (if (powerChanged) 2 else 0)
    }
}
