package com.guanyu.rx400hprobe

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.max
import kotlin.math.min

/**
 * Small dashboard-specific ViewGroups. They keep one stable View tree and
 * reflow during normal Android measurement, including freeform live resize.
 */
internal class ResponsiveCardGrid(context: Context) : ViewGroup(context) {
    private var plan = ResponsiveLayout.gridPlan(0, 0)
    private var rowHeights = IntArray(0)
    private var cachedContentWidth = -1
    private var cachedChildCount = -1
    private var cachedDensityBits = 0

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (rowHeights.size < childCount) {
            rowHeights = IntArray(childCount)
        }
        cachedChildCount = -1
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = measuredWidthFor(widthMeasureSpec)
        val contentWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val structureChanged =
            contentWidth != cachedContentWidth || childCount != cachedChildCount ||
                density.toBits() != cachedDensityBits
        if (structureChanged) {
            cachedContentWidth = contentWidth
            cachedChildCount = childCount
            cachedDensityBits = density.toBits()
            plan = ResponsiveLayout.gridPlan(
                availableWidth = contentWidth,
                itemCount = childCount,
                minItemWidth = dp(ResponsiveLayout.CARD_MIN_WIDTH_DP),
                maxItemWidth = dp(ResponsiveLayout.CARD_MAX_WIDTH_DP),
                gap = dp(ResponsiveLayout.ITEM_GAP_DP)
            )
        }
        val gapPx = dp(ResponsiveLayout.ITEM_GAP_DP)
        val minHeightPx = dp(ResponsiveLayout.CARD_MIN_HEIGHT_DP)

        plan.rows.forEachIndexed { rowIndex, row ->
            val itemWidth = row.itemWidth
            var rowHeight = minHeightPx
            for (index in row.indices) {
                val child = getChildAt(index)
                if (child.visibility == GONE) continue
                child.measure(exactly(itemWidth), unspecified())
                rowHeight = max(rowHeight, child.measuredHeight)
            }
            rowHeights[rowIndex] = rowHeight
            for (index in row.indices) {
                val child = getChildAt(index)
                if (child.visibility == GONE) continue
                if (child.measuredHeight != rowHeight) {
                    child.measure(exactly(itemWidth), exactly(rowHeight))
                }
            }
        }

        var rowsHeight = 0
        for (rowIndex in plan.rows.indices) rowsHeight += rowHeights[rowIndex]
        val desiredHeight = paddingTop + paddingBottom + rowsHeight +
            (plan.rows.size - 1).coerceAtLeast(0) * gapPx
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val gapPx = dp(ResponsiveLayout.ITEM_GAP_DP)
        var y = paddingTop
        plan.rows.forEachIndexed { rowIndex, row ->
            val itemWidth = row.itemWidth
            var x = paddingLeft + row.leadingSpace
            for (index in row.indices) {
                val child = getChildAt(index)
                if (child.visibility != GONE) {
                    child.layout(x, y, x + itemWidth, y + rowHeights[rowIndex])
                }
                x += itemWidth + gapPx
            }
            y += rowHeights[rowIndex] + gapPx
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    private fun measuredWidthFor(spec: Int): Int = when (MeasureSpec.getMode(spec)) {
        MeasureSpec.UNSPECIFIED -> {
            val columns = minOf(childCount.coerceAtLeast(1), ResponsiveLayout.MAX_COLUMNS)
            paddingLeft + paddingRight + columns * dp(ResponsiveLayout.CARD_PREFERRED_WIDTH_DP) +
                (columns - 1) * dp(ResponsiveLayout.ITEM_GAP_DP)
        }
        else -> MeasureSpec.getSize(spec)
    }.coerceAtLeast(suggestedMinimumWidth)

    private fun dp(value: Int): Int =
        ResponsiveLayout.dpToPixels(value, resources.displayMetrics.density)
            .coerceAtLeast(if (value > 0) 1 else 0)

    private fun exactly(value: Int): Int = MeasureSpec.makeMeasureSpec(value.coerceAtLeast(0), MeasureSpec.EXACTLY)
    private fun unspecified(): Int = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
}

/**
 * Header with three native modes:
 *
 *  * INLINE: title / three controls / status in one row when every component fits.
 *  * SPLIT: title + status first row, wrapped controls below.
 *  * STACKED: title, status and wrapped controls in separate rows.
 */
internal class ResponsiveHeaderLayout(
    context: Context,
    private val titleView: View,
    private val statusView: View,
    private val controls: List<View>
) : ViewGroup(context) {
    private var mode = ResponsiveLayout.HeaderMode.STACKED
    private var gapPx = 0
    private var titleWidth = 0
    private var statusWidth = 0
    private var leadingHeight = 0
    private var controlsWidth = 0
    private var controlsHeight = 0
    private var controlPlan = ResponsiveLayout.gridPlan(0, 0)
    private var controlRowHeights = IntArray(controls.size)
    private var cachedHeaderContentWidth = -1
    private var cachedHeaderDensityBits = 0
    private var hasMeasuredHeaderMode = false
    private var cachedControlWidth = -1
    private var cachedControlDensityBits = 0

    init {
        addView(titleView)
        addView(statusView)
        controls.forEach { addView(it) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = measuredWidthFor(widthMeasureSpec)
        val contentWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        if (contentWidth != cachedHeaderContentWidth || density.toBits() != cachedHeaderDensityBits) {
            cachedHeaderContentWidth = contentWidth
            cachedHeaderDensityBits = density.toBits()
            mode = ResponsiveLayout.headerPlanUnits(
                availableWidth = contentWidth,
                controlCount = controls.size,
                titleMinWidth = dp(ResponsiveLayout.HEADER_TITLE_MIN_WIDTH_DP),
                statusMinWidth = dp(ResponsiveLayout.HEADER_STATUS_MIN_WIDTH_DP),
                splitTitleMinWidth = dp(ResponsiveLayout.HEADER_SPLIT_TITLE_MIN_WIDTH_DP),
                splitStatusMinWidth = dp(ResponsiveLayout.HEADER_SPLIT_STATUS_MIN_WIDTH_DP),
                controlMinWidth = dp(ResponsiveLayout.CONTROL_MIN_WIDTH_DP),
                gap = dp(ResponsiveLayout.ITEM_GAP_DP),
                previousMode = if (hasMeasuredHeaderMode) mode else null,
                hysteresis = dp(ResponsiveLayout.HEADER_HYSTERESIS_DP)
            ).mode
            hasMeasuredHeaderMode = true
        }
        gapPx = dp(ResponsiveLayout.ITEM_GAP_DP)

        val desiredHeight = when (mode) {
            ResponsiveLayout.HeaderMode.INLINE -> measureInline(contentWidth)
            ResponsiveLayout.HeaderMode.SPLIT -> measureSplit(contentWidth)
            ResponsiveLayout.HeaderMode.STACKED -> measureStacked(contentWidth)
        }
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(paddingTop + desiredHeight + paddingBottom, heightMeasureSpec)
        )
    }

    private fun measureInline(contentWidth: Int): Int {
        titleWidth = bounded(
            value = contentWidth * 18 / 100,
            minimum = dp(ResponsiveLayout.HEADER_TITLE_MIN_WIDTH_DP),
            maximum = dp(200)
        )
        statusWidth = bounded(
            value = contentWidth * 27 / 100,
            minimum = dp(ResponsiveLayout.HEADER_STATUS_MIN_WIDTH_DP),
            maximum = dp(360)
        )
        controlsWidth = (contentWidth - titleWidth - statusWidth - 2 * gapPx).coerceAtLeast(0)
        measureExactWidth(titleView, titleWidth)
        measureExactWidth(statusView, statusWidth)
        controlsHeight = measureControls(controlsWidth)
        leadingHeight = maxOf(titleView.measuredHeight, statusView.measuredHeight, controlsHeight)
        return leadingHeight
    }

    private fun measureSplit(contentWidth: Int): Int {
        val minTitle = dp(ResponsiveLayout.HEADER_SPLIT_TITLE_MIN_WIDTH_DP)
        val maxTitleByStatus =
            (contentWidth - gapPx - dp(ResponsiveLayout.HEADER_SPLIT_STATUS_MIN_WIDTH_DP)).coerceAtLeast(minTitle)
        titleWidth = bounded(
            value = contentWidth * 32 / 100,
            minimum = minTitle,
            maximum = min(dp(220), maxTitleByStatus)
        )
        statusWidth = (contentWidth - gapPx - titleWidth).coerceAtLeast(0)
        measureExactWidth(titleView, titleWidth)
        measureExactWidth(statusView, statusWidth)
        leadingHeight = max(titleView.measuredHeight, statusView.measuredHeight)
        controlsWidth = contentWidth
        controlsHeight = measureControls(controlsWidth)
        return leadingHeight + gapPx + controlsHeight
    }

    private fun measureStacked(contentWidth: Int): Int {
        titleWidth = contentWidth
        statusWidth = contentWidth
        measureExactWidth(titleView, titleWidth)
        measureExactWidth(statusView, statusWidth)
        controlsWidth = contentWidth
        controlsHeight = measureControls(controlsWidth)
        return titleView.measuredHeight + gapPx + statusView.measuredHeight + gapPx + controlsHeight
    }

    private fun measureControls(availableWidth: Int): Int {
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        if (availableWidth != cachedControlWidth || density.toBits() != cachedControlDensityBits) {
            cachedControlWidth = availableWidth
            cachedControlDensityBits = density.toBits()
            controlPlan = ResponsiveLayout.gridPlan(
                availableWidth = availableWidth,
                itemCount = controls.size,
                minItemWidth = dp(ResponsiveLayout.CONTROL_MIN_WIDTH_DP),
                maxItemWidth = dp(ResponsiveLayout.CONTROL_MAX_WIDTH_DP),
                gap = dp(ResponsiveLayout.ITEM_GAP_DP),
                maxColumns = controls.size.coerceAtLeast(1)
            )
        }
        val minHeight = dp(ResponsiveLayout.CONTROL_MIN_HEIGHT_DP)
        controlPlan.rows.forEachIndexed { rowIndex, row ->
            val itemWidth = row.itemWidth
            var rowHeight = minHeight
            for (index in row.indices) {
                val child = controls[index]
                child.measure(exactly(itemWidth), unspecified())
                rowHeight = max(rowHeight, child.measuredHeight)
            }
            controlRowHeights[rowIndex] = rowHeight
            for (index in row.indices) {
                if (controls[index].measuredHeight != rowHeight) {
                    controls[index].measure(exactly(itemWidth), exactly(rowHeight))
                }
            }
        }
        var rowsHeight = 0
        for (rowIndex in controlPlan.rows.indices) rowsHeight += controlRowHeights[rowIndex]
        return rowsHeight + (controlPlan.rows.size - 1).coerceAtLeast(0) * gapPx
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val x = paddingLeft
        val y = paddingTop
        when (mode) {
            ResponsiveLayout.HeaderMode.INLINE -> {
                layoutCenteredVertically(titleView, x, y, titleWidth, leadingHeight)
                val controlsLeft = x + titleWidth + gapPx
                layoutControls(controlsLeft, y + (leadingHeight - controlsHeight) / 2)
                layoutCenteredVertically(
                    statusView,
                    controlsLeft + controlsWidth + gapPx,
                    y,
                    statusWidth,
                    leadingHeight
                )
            }
            ResponsiveLayout.HeaderMode.SPLIT -> {
                layoutCenteredVertically(titleView, x, y, titleWidth, leadingHeight)
                layoutCenteredVertically(statusView, x + titleWidth + gapPx, y, statusWidth, leadingHeight)
                layoutControls(x, y + leadingHeight + gapPx)
            }
            ResponsiveLayout.HeaderMode.STACKED -> {
                titleView.layout(x, y, x + titleWidth, y + titleView.measuredHeight)
                val statusTop = y + titleView.measuredHeight + gapPx
                statusView.layout(x, statusTop, x + statusWidth, statusTop + statusView.measuredHeight)
                layoutControls(x, statusTop + statusView.measuredHeight + gapPx)
            }
        }
    }

    private fun layoutControls(left: Int, top: Int) {
        var y = top
        controlPlan.rows.forEachIndexed { rowIndex, row ->
            val itemWidth = row.itemWidth
            var x = left + row.leadingSpace
            for (index in row.indices) {
                val child = controls[index]
                child.layout(x, y, x + itemWidth, y + controlRowHeights[rowIndex])
                x += itemWidth + gapPx
            }
            y += controlRowHeights[rowIndex] + gapPx
        }
    }

    private fun layoutCenteredVertically(view: View, left: Int, top: Int, width: Int, areaHeight: Int) {
        val childTop = top + ((areaHeight - view.measuredHeight) / 2).coerceAtLeast(0)
        view.layout(left, childTop, left + width, childTop + view.measuredHeight)
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    private fun measureExactWidth(view: View, width: Int) {
        view.measure(exactly(width), unspecified())
    }

    private fun measuredWidthFor(spec: Int): Int = when (MeasureSpec.getMode(spec)) {
        MeasureSpec.UNSPECIFIED -> {
            val controlGaps = (controls.size - 1).coerceAtLeast(0) * dp(ResponsiveLayout.ITEM_GAP_DP)
            paddingLeft + paddingRight +
                dp(ResponsiveLayout.HEADER_TITLE_MIN_WIDTH_DP) +
                controls.size * dp(ResponsiveLayout.CONTROL_PREFERRED_WIDTH_DP) + controlGaps +
                dp(ResponsiveLayout.HEADER_STATUS_MIN_WIDTH_DP) + 2 * dp(ResponsiveLayout.ITEM_GAP_DP)
        }
        else -> MeasureSpec.getSize(spec)
    }.coerceAtLeast(suggestedMinimumWidth)

    private fun bounded(value: Int, minimum: Int, maximum: Int): Int {
        val safeMaximum = max(minimum, maximum)
        return value.coerceIn(minimum, safeMaximum)
    }

    private fun dp(value: Int): Int =
        ResponsiveLayout.dpToPixels(value, resources.displayMetrics.density)
            .coerceAtLeast(if (value > 0) 1 else 0)

    private fun exactly(value: Int): Int = MeasureSpec.makeMeasureSpec(value.coerceAtLeast(0), MeasureSpec.EXACTLY)
    private fun unspecified(): Int = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
}
