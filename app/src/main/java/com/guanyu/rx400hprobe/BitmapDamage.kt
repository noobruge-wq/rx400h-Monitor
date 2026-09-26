package com.guanyu.rx400hprobe

/** Damage to an offscreen 720p bitmap, never to the Android window surface.
 * Fixed storage; callers still present the entire composed bitmap every frame. */
internal class BitmapDamage {
    private val tiles = BooleanArray(COLUMNS * ROWS)
    var any: Boolean = false
        private set

    fun all() { tiles.fill(true); any = true }
    fun clear() { tiles.fill(false); any = false }
    fun contains(column: Int, row: Int): Boolean = tiles[row * COLUMNS + column]

    fun mark(left: Int, top: Int, right: Int, bottom: Int) {
        val l = left.coerceAtLeast(0)
        val t = top.coerceAtLeast(0)
        val r = right.coerceAtMost(1280)
        val b = bottom.coerceAtMost(720)
        if (l >= r || t >= b) return
        for (row in t / HEIGHT..(b - 1) / HEIGHT) {
            for (column in l / WIDTH..(r - 1) / WIDTH) tiles[row * COLUMNS + column] = true
        }
        any = true
    }

    companion object {
        const val COLUMNS = 8
        const val ROWS = 8
        const val WIDTH = 160
        const val HEIGHT = 90
    }
}
