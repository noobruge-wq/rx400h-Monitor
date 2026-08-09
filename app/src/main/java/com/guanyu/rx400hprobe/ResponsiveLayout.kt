package com.guanyu.rx400hprobe

/**
 * V0.3.0 responsive layout rules (D-039).
 *
 * Pure, unit-testable layout math: the dashboard derives its column count and
 * row plan from the current window width instead of a fixed resolution or
 * aspect ratio. Cards reflow (more columns when wide, fewer when narrow) and
 * the data area scrolls when height is insufficient.
 */
internal object ResponsiveLayout {

    /** Minimum width (dp) one data card needs to stay readable. */
    const val CARD_MIN_WIDTH_DP = 240

    /** Upper bound on columns; the current product has three domain cards. */
    const val MAX_COLUMNS = 3

    /**
     * Number of columns that fit at [availableWidthDp]. Returns at least 1 and
     * never more than [maxColumns], so a very narrow window degrades to a
     * single column and an ultra-wide window does not invent empty columns.
     */
    fun columnCount(
        availableWidthDp: Int,
        minCardWidthDp: Int = CARD_MIN_WIDTH_DP,
        maxColumns: Int = MAX_COLUMNS
    ): Int {
        if (availableWidthDp <= 0) return 1
        return (availableWidthDp / minCardWidthDp).coerceIn(1, maxColumns)
    }

    /**
     * Row plan for [cardCount] cards in [columns] columns: a list of inclusive
     * index ranges, e.g. 3 cards in 2 columns -> [0..1, 2..2].
     */
    fun rowPlan(cardCount: Int, columns: Int): List<IntRange> {
        if (cardCount <= 0) return emptyList()
        val cols = columns.coerceAtLeast(1)
        val rows = mutableListOf<IntRange>()
        var start = 0
        while (start < cardCount) {
            val end = minOf(start + cols, cardCount)
            rows.add(start until end)
            start = end
        }
        return rows
    }
}
