package com.guanyu.rx400hprobe

/**
 * Pure size-independent layout policy for the V0.3.1 dashboard (D-041/D-043).
 *
 * The policy consumes integer geometry tokens in one caller-selected unit.
 * Android passes exact pixels after converting the component dp contracts;
 * JVM tests use dp or the same exact pixel tokens. It deliberately has no
 * Android dependency, fixed resolution, aspect-ratio assumption, or
 * whole-screen scale factor.
 */
internal object ResponsiveLayout {

    const val CARD_COUNT = 3
    const val CONTROL_COUNT = 3

    const val OUTER_HORIZONTAL_DP = 16
    const val OUTER_VERTICAL_DP = 12
    const val OUTER_VERTICAL_COMPACT_DP = 8
    const val ITEM_GAP_DP = 8
    const val SECTION_GAP_DP = 8
    const val SECTION_GAP_COMPACT_DP = 6

    const val CARD_MIN_WIDTH_DP = 240
    const val CARD_PREFERRED_WIDTH_DP = 320
    const val CARD_MAX_WIDTH_DP = 480
    const val CARD_MIN_HEIGHT_DP = 264
    const val CARD_PADDING_DP = 12
    const val MAX_COLUMNS = CARD_COUNT

    const val CONTROL_MIN_WIDTH_DP = 92
    const val CONTROL_PREFERRED_WIDTH_DP = 112
    const val CONTROL_MAX_WIDTH_DP = 160
    const val CONTROL_MIN_HEIGHT_DP = 56

    const val HEADER_TITLE_MIN_WIDTH_DP = 144
    const val HEADER_STATUS_MIN_WIDTH_DP = 220
    const val HEADER_SPLIT_TITLE_MIN_WIDTH_DP = 128
    const val HEADER_SPLIT_STATUS_MIN_WIDTH_DP = 200
    const val HEADER_HYSTERESIS_DP = 8

    const val COMPACT_HEIGHT_DP = 480

    enum class HeaderMode {
        INLINE,
        SPLIT,
        STACKED
    }

    /** One centered row; every geometry field uses the caller's input unit. */
    data class RowPlan(
        val indices: IntRange,
        val itemWidth: Int,
        val usedWidth: Int,
        val leadingSpace: Int
    )

    data class GridPlan(
        val columns: Int,
        val rows: List<RowPlan>,
        val emergencyNarrow: Boolean
    )

    data class HeaderPlan(val mode: HeaderMode)

    data class VerticalSpacing(
        val compactHeight: Boolean,
        val outerVerticalDp: Int,
        val sectionGapDp: Int
    )

    data class TypographyBounds(
        val cardTitleMinSp: Int = 20,
        val cardTitleMaxSp: Int = 28,
        val labelMinSp: Int = 16,
        val labelMaxSp: Int = 22,
        val valueMinSp: Int = 28,
        val valueMaxSp: Int = 44,
        val detailMinSp: Int = 18,
        val detailMaxSp: Int = 26,
        val headerTitleMinSp: Int = 24,
        val headerTitleMaxSp: Int = 32,
        val headerSubtitleMinSp: Int = 13,
        val headerSubtitleMaxSp: Int = 18,
        val statusMinSp: Int = 12,
        val statusMaxSp: Int = 16,
        val buttonMinSp: Int = 14,
        val buttonMaxSp: Int = 17
    )

    /** Exact runtime height/inset path shared with JVM boundary tests. */
    fun verticalSpacingUnits(
        windowHeight: Int,
        insetTop: Int,
        insetBottom: Int,
        density: Float
    ): VerticalSpacing {
        require(windowHeight >= 0) { "windowHeight must not be negative" }
        require(insetTop >= 0 && insetBottom >= 0) { "safe-area insets must not be negative" }
        val safeHeightPx = (windowHeight.toLong() - insetTop - insetBottom)
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val safeHeightDp = pixelsToDp(safeHeightPx, density)
        val compactHeight = safeHeightDp < COMPACT_HEIGHT_DP
        return VerticalSpacing(
            compactHeight = compactHeight,
            outerVerticalDp = if (compactHeight) OUTER_VERTICAL_COMPACT_DP else OUTER_VERTICAL_DP,
            sectionGapDp = if (compactHeight) SECTION_GAP_COMPACT_DP else SECTION_GAP_DP
        )
    }

    /**
     * Produces centered rows with bounded item widths. If even one minimum-width
     * item cannot fit, the single item contracts to the viewport instead of
     * overflowing horizontally; the plan marks that emergency explicitly.
     */
    fun gridPlan(
        availableWidth: Int,
        itemCount: Int,
        minItemWidth: Int = CARD_MIN_WIDTH_DP,
        maxItemWidth: Int = CARD_MAX_WIDTH_DP,
        gap: Int = ITEM_GAP_DP,
        maxColumns: Int = MAX_COLUMNS
    ): GridPlan {
        require(maxItemWidth >= minItemWidth) { "maxItemWidth must be >= minItemWidth" }
        val width = availableWidth.coerceAtLeast(0)
        val columns = columnCount(width, itemCount, minItemWidth, gap, maxColumns)
        if (itemCount == 0) return GridPlan(columns = 0, rows = emptyList(), emergencyNarrow = false)

        val ranges = rowPlan(itemCount, columns)
        val rows = ranges.map { indices ->
            val count = indices.count()
            val gaps = (count - 1) * gap
            val rawItemWidth = ((width - gaps).coerceAtLeast(0) / count).coerceAtLeast(0)
            val itemWidth = minOf(rawItemWidth, maxItemWidth)
            val usedWidth = itemWidth * count + gaps
            RowPlan(
                indices = indices,
                itemWidth = itemWidth,
                usedWidth = usedWidth,
                leadingSpace = ((width - usedWidth) / 2).coerceAtLeast(0)
            )
        }
        return GridPlan(
            columns = columns,
            rows = rows,
            emergencyNarrow = width < minItemWidth
        )
    }

    /** Maximum fitting column count without building row objects. */
    fun columnCount(
        availableWidth: Int,
        itemCount: Int,
        minItemWidth: Int,
        gap: Int,
        maxColumns: Int
    ): Int {
        require(minItemWidth > 0) { "minItemWidth must be positive" }
        require(gap >= 0) { "gap must not be negative" }
        require(maxColumns > 0) { "maxColumns must be positive" }
        require(itemCount >= 0) { "itemCount must not be negative" }
        if (itemCount == 0) return 0
        val width = availableWidth.coerceAtLeast(0)
        val columnLimit = minOf(itemCount, maxColumns)
        var columns = 1
        for (candidate in 2..columnLimit) {
            val required = candidate.toLong() * minItemWidth + (candidate - 1).toLong() * gap
            if (required <= width.toLong()) columns = candidate else break
        }
        return columns
    }

    /** Header structure is selected by component fit, not a device breakpoint. */
    fun headerPlan(
        availableWidthDp: Int,
        controlCount: Int = CONTROL_COUNT,
        previousMode: HeaderMode? = null,
        hysteresisDp: Int = 0
    ): HeaderPlan = headerPlanUnits(
        availableWidth = availableWidthDp,
        controlCount = controlCount,
        titleMinWidth = HEADER_TITLE_MIN_WIDTH_DP,
        statusMinWidth = HEADER_STATUS_MIN_WIDTH_DP,
        splitTitleMinWidth = HEADER_SPLIT_TITLE_MIN_WIDTH_DP,
        splitStatusMinWidth = HEADER_SPLIT_STATUS_MIN_WIDTH_DP,
        controlMinWidth = CONTROL_MIN_WIDTH_DP,
        gap = ITEM_GAP_DP,
        previousMode = previousMode,
        hysteresis = hysteresisDp
    )

    /** Same header policy in any integer unit; Android passes exact px tokens. */
    fun headerPlanUnits(
        availableWidth: Int,
        controlCount: Int,
        titleMinWidth: Int,
        statusMinWidth: Int,
        splitTitleMinWidth: Int,
        splitStatusMinWidth: Int,
        controlMinWidth: Int,
        gap: Int,
        previousMode: HeaderMode? = null,
        hysteresis: Int = 0
    ): HeaderPlan {
        require(controlCount >= 0) { "controlCount must not be negative" }
        require(titleMinWidth > 0 && statusMinWidth > 0) { "inline widths must be positive" }
        require(splitTitleMinWidth > 0 && splitStatusMinWidth > 0) { "split widths must be positive" }
        require(controlMinWidth > 0) { "controlMinWidth must be positive" }
        require(gap >= 0) { "gap must not be negative" }
        require(hysteresis >= 0) { "hysteresis must not be negative" }
        val width = availableWidth.coerceAtLeast(0)
        val controlsRequiredInline =
            controlCount.toLong() * controlMinWidth +
                (controlCount - 1).coerceAtLeast(0).toLong() * gap
        val inlineRequired =
            titleMinWidth.toLong() + statusMinWidth + controlsRequiredInline + 2L * gap
        val splitRequired =
            splitTitleMinWidth.toLong() + splitStatusMinWidth + gap
        val desiredMode = when {
            width.toLong() >= inlineRequired -> HeaderMode.INLINE
            width.toLong() >= splitRequired -> HeaderMode.SPLIT
            else -> HeaderMode.STACKED
        }
        val hysteresisUnits = hysteresis.toLong()
        val mode = when (previousMode) {
            null -> desiredMode
            HeaderMode.INLINE ->
                if (width.toLong() >= inlineRequired) HeaderMode.INLINE else desiredMode
            HeaderMode.SPLIT -> when {
                width.toLong() >= inlineRequired + hysteresisUnits -> HeaderMode.INLINE
                width.toLong() < splitRequired -> HeaderMode.STACKED
                else -> HeaderMode.SPLIT
            }
            HeaderMode.STACKED -> when {
                width.toLong() >= inlineRequired + hysteresisUnits -> HeaderMode.INLINE
                width.toLong() >= splitRequired + hysteresisUnits -> HeaderMode.SPLIT
                else -> HeaderMode.STACKED
            }
        }
        return HeaderPlan(mode = mode)
    }

    /** Runtime and tests share one conservative px-to-dp rounding rule. */
    fun pixelsToDp(pixels: Int, density: Float): Int {
        require(density.isFinite() && density > 0f) { "density must be finite and positive" }
        return (pixels.coerceAtLeast(0) / density).toInt()
    }

    fun dpToPixels(dp: Int, density: Float): Int {
        require(dp >= 0) { "dp must not be negative" }
        require(density.isFinite() && density > 0f) { "density must be finite and positive" }
        return (dp * density + 0.5f).toInt()
    }

    /** Inclusive card index ranges, one per row. */
    fun rowPlan(itemCount: Int, columns: Int): List<IntRange> {
        require(itemCount >= 0) { "itemCount must not be negative" }
        require(columns > 0) { "columns must be positive" }
        if (itemCount == 0) return emptyList()
        val rows = mutableListOf<IntRange>()
        var start = 0
        while (start < itemCount) {
            val endExclusive = minOf(start.toLong() + columns, itemCount.toLong()).toInt()
            rows.add(start until endExclusive)
            start = endExclusive
        }
        return rows
    }
}
