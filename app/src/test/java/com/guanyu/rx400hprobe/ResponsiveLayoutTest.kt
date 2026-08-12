package com.guanyu.rx400hprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutTest {

    @Test
    fun representativeWindowsReflowWithoutAspectRatioAssumptions() {
        data class Case(val name: String, val width: Int, val height: Int, val columns: Int)
        val cases = listOf(
            Case("extreme narrow", 200, 700, 1),
            Case("phone portrait", 360, 800, 1),
            Case("phone landscape", 640, 360, 2),
            Case("split window", 500, 720, 1),
            Case("4:3", 800, 600, 3),
            Case("16:9", 1280, 720, 3),
            Case("16:10", 1280, 800, 3),
            Case("tablet portrait", 800, 1280, 3),
            Case("tablet landscape", 1024, 768, 3),
            Case("ultra-wide short", 2560, 360, 3),
            Case("extreme wide", 4096, 1200, 3)
        )

        for (case in cases) {
            val cards = cardPlanForWindow(case.width)
            val spacing = ResponsiveLayout.verticalSpacingUnits(case.height, 0, 0, 1f)
            val contentWidth = contentWidthForWindow(case.width)
            assertEquals(case.name, case.columns, cards.columns)
            assertEquals(case.name, listOf(0, 1, 2), flattenedIndices(cards))
            assertTrue(case.name, cards.rows.all { it.usedWidth <= contentWidth })
            assertTrue(
                case.name,
                cards.rows.all { it.itemWidth <= ResponsiveLayout.CARD_MAX_WIDTH_DP }
            )
            assertTrue(case.name, spacing.outerVerticalDp > 0)
        }
    }

    @Test
    fun cardColumnBreakpointsIncludeOuterPaddingAndGaps() {
        assertEquals(1, cardPlanForWindow(519).columns)
        assertEquals(2, cardPlanForWindow(520).columns)
        assertEquals(2, cardPlanForWindow(767).columns)
        assertEquals(3, cardPlanForWindow(768).columns)

        val twoColumns = cardPlanForWindow(520)
        assertEquals(240, twoColumns.rows.first().itemWidth)
        assertEquals(480, twoColumns.rows.last().itemWidth)
        assertEquals(4, twoColumns.rows.last().leadingSpace)
    }

    @Test
    fun headerModesUseComponentFitAndControlsWrap() {
        assertEquals(ResponsiveLayout.HeaderMode.STACKED, headerPlanForWindow(367).mode)
        assertEquals(ResponsiveLayout.HeaderMode.SPLIT, headerPlanForWindow(368).mode)
        assertEquals(ResponsiveLayout.HeaderMode.SPLIT, headerPlanForWindow(703).mode)
        assertEquals(ResponsiveLayout.HeaderMode.INLINE, headerPlanForWindow(704).mode)

        assertEquals(1, controlPlanForWindow(223).columns)
        assertEquals(2, controlPlanForWindow(224).columns)
        assertEquals(2, controlPlanForWindow(323).columns)
        assertEquals(3, controlPlanForWindow(324).columns)
        assertEquals(3, controlPlanForWindow(424).columns)
        assertEquals(3, controlPlanForWindow(804).columns)
        assertEquals(3, ResponsiveLayout.CONTROL_COUNT)
    }

    @Test
    fun headerHysteresisPreventsBoundaryOscillation() {
        val h = ResponsiveLayout.HEADER_HYSTERESIS_DP
        assertEquals(
            ResponsiveLayout.HeaderMode.SPLIT,
            ResponsiveLayout.headerPlan(679, previousMode = ResponsiveLayout.HeaderMode.SPLIT, hysteresisDp = h).mode
        )
        assertEquals(
            ResponsiveLayout.HeaderMode.INLINE,
            ResponsiveLayout.headerPlan(680, previousMode = ResponsiveLayout.HeaderMode.SPLIT, hysteresisDp = h).mode
        )
        assertEquals(
            ResponsiveLayout.HeaderMode.INLINE,
            ResponsiveLayout.headerPlan(672, previousMode = ResponsiveLayout.HeaderMode.INLINE, hysteresisDp = h).mode
        )
        assertEquals(
            ResponsiveLayout.HeaderMode.SPLIT,
            ResponsiveLayout.headerPlan(671, previousMode = ResponsiveLayout.HeaderMode.INLINE, hysteresisDp = h).mode
        )
        assertEquals(
            ResponsiveLayout.HeaderMode.STACKED,
            ResponsiveLayout.headerPlan(335, previousMode = ResponsiveLayout.HeaderMode.SPLIT, hysteresisDp = h).mode
        )
        assertEquals(
            ResponsiveLayout.HeaderMode.SPLIT,
            ResponsiveLayout.headerPlan(344, previousMode = ResponsiveLayout.HeaderMode.STACKED, hysteresisDp = h).mode
        )
    }

    @Test
    fun ultraWideCardsStopAtMaximumAndRemainCentered() {
        val below = cardPlanForWindow(1487).rows.single()
        val exact = cardPlanForWindow(1488).rows.single()
        val above = cardPlanForWindow(2000).rows.single()

        assertEquals(479, below.itemWidth)
        assertEquals(480, exact.itemWidth)
        assertEquals(0, exact.leadingSpace)
        assertEquals(480, above.itemWidth)
        assertTrue(above.leadingSpace > 0)
        assertEquals(1456, above.usedWidth)
    }

    @Test
    fun emergencyNarrowContractsToViewportInsteadOfOverflowing() {
        val plan = cardPlanForWindow(160)
        assertTrue(plan.emergencyNarrow)
        assertEquals(1, plan.columns)
        assertEquals(128, plan.rows.first().itemWidth)
        assertEquals(128, plan.rows.first().usedWidth)
        assertTrue(plan.rows.first().usedWidth <= contentWidthForWindow(160))
    }

    @Test
    fun safeInsetsAndCompactSpacingUseTheRuntimeUnitPath() {
        val contentWidth = contentWidthForWindow(windowWidth = 800, insetLeft = 24, insetRight = 16)
        val cards = ResponsiveLayout.gridPlan(contentWidth, ResponsiveLayout.CARD_COUNT)
        val spacing = ResponsiveLayout.verticalSpacingUnits(
            windowHeight = 600,
            insetTop = 20,
            insetBottom = 28,
            density = 1f
        )

        assertEquals(728, contentWidth)
        assertEquals(2, cards.columns)
        assertFalse(spacing.compactHeight)
    }

    @Test
    fun runtimePixelRoundingUsesTheSameConservativePolicyAcrossDensities() {
        val densities = listOf(1f, 1.3f, 2.625f, 3f)
        for (density in densities) {
            val minCardPx = ResponsiveLayout.dpToPixels(ResponsiveLayout.CARD_MIN_WIDTH_DP, density)
            val maxCardPx = ResponsiveLayout.dpToPixels(ResponsiveLayout.CARD_MAX_WIDTH_DP, density)
            val gapPx = ResponsiveLayout.dpToPixels(ResponsiveLayout.ITEM_GAP_DP, density)
            for (expectedColumns in 2..3) {
                val thresholdPx = expectedColumns * minCardPx + (expectedColumns - 1) * gapPx
                val at = ResponsiveLayout.gridPlan(
                    thresholdPx,
                    ResponsiveLayout.CARD_COUNT,
                    minCardPx,
                    maxCardPx,
                    gapPx
                )
                val below = ResponsiveLayout.gridPlan(
                    thresholdPx - 1,
                    ResponsiveLayout.CARD_COUNT,
                    minCardPx,
                    maxCardPx,
                    gapPx
                )
                assertEquals("density=$density columns=$expectedColumns", expectedColumns, at.columns)
                assertEquals("density=$density below=$expectedColumns", expectedColumns - 1, below.columns)
                assertTrue(at.rows.all { it.usedWidth <= thresholdPx })
                assertTrue(below.rows.all { it.usedWidth <= thresholdPx - 1 })
            }

            val inlineThresholdPx =
                ResponsiveLayout.dpToPixels(ResponsiveLayout.HEADER_TITLE_MIN_WIDTH_DP, density) +
                    ResponsiveLayout.dpToPixels(ResponsiveLayout.HEADER_STATUS_MIN_WIDTH_DP, density) +
                    ResponsiveLayout.CONTROL_COUNT *
                    ResponsiveLayout.dpToPixels(ResponsiveLayout.CONTROL_MIN_WIDTH_DP, density) +
                    (ResponsiveLayout.CONTROL_COUNT + 1) * gapPx
            val headerAt = runtimeHeaderPlan(inlineThresholdPx, density)
            val headerBelow = runtimeHeaderPlan(inlineThresholdPx - 1, density)
            assertEquals(ResponsiveLayout.HeaderMode.INLINE, headerAt.mode)
            assertEquals(ResponsiveLayout.HeaderMode.SPLIT, headerBelow.mode)
        }
    }

    @Test
    fun heightChangesSpacingButNeverCardColumnsOrTypographyBounds() {
        val short = ResponsiveLayout.verticalSpacingUnits(479, 0, 0, 1f)
        val regular = ResponsiveLayout.verticalSpacingUnits(480, 0, 0, 1f)
        val tall = ResponsiveLayout.verticalSpacingUnits(1600, 0, 0, 1f)
        val cards = cardPlanForWindow(1024)

        assertTrue(short.compactHeight)
        assertEquals(ResponsiveLayout.OUTER_VERTICAL_COMPACT_DP, short.outerVerticalDp)
        assertFalse(regular.compactHeight)
        assertEquals(ResponsiveLayout.OUTER_VERTICAL_DP, regular.outerVerticalDp)
        assertFalse(tall.compactHeight)
        assertEquals(3, cards.columns)
        assertTrue(ResponsiveLayout.TypographyBounds().valueMinSp >= 28)
    }

    @Test
    fun insetAdjustedHeightBoundaryIsDeterministic() {
        assertTrue(ResponsiveLayout.verticalSpacingUnits(519, 24, 16, 1f).compactHeight)
        assertFalse(ResponsiveLayout.verticalSpacingUnits(520, 24, 16, 1f).compactHeight)
    }

    @Test
    fun continuousWidthSweepPreservesAllLayoutInvariants() {
        var previousColumns = 0
        for (width in 0..4096) {
            val contentWidth = contentWidthForWindow(width)
            val cards = cardPlanForWindow(width)
            assertTrue("columns at $width", cards.columns in 1..ResponsiveLayout.MAX_COLUMNS)
            assertTrue("monotonic columns at $width", cards.columns >= previousColumns)
            assertEquals("card identity at $width", listOf(0, 1, 2), flattenedIndices(cards))
            for (row in cards.rows) {
                assertTrue("row width at $width", row.usedWidth <= contentWidth)
                assertTrue("non-negative width at $width", row.itemWidth >= 0)
                assertTrue("max card width at $width", row.itemWidth <= ResponsiveLayout.CARD_MAX_WIDTH_DP)
                assertTrue("non-negative inset at $width", row.leadingSpace >= 0)
                if (!cards.emergencyNarrow) {
                    assertTrue("minimum card width at $width", row.itemWidth >= ResponsiveLayout.CARD_MIN_WIDTH_DP)
                }
            }
            assertTrue("touch target contract", ResponsiveLayout.CONTROL_MIN_HEIGHT_DP >= 48)
            val typography = ResponsiveLayout.TypographyBounds()
            assertTrue("value typography lower bound", typography.valueMinSp >= 28)
            assertTrue("value typography ordering", typography.valueMaxSp >= typography.valueMinSp)
            previousColumns = cards.columns
        }
    }

    @Test
    fun continuousHeightSweepDoesNotChangeHorizontalStructure() {
        val baselineCards = cardPlanForWindow(1024)
        val baselineHeader = headerPlanForWindow(1024)
        for (height in 120..2160) {
            val spacing = ResponsiveLayout.verticalSpacingUnits(height, 0, 0, 1f)
            assertEquals("columns at height $height", baselineCards.columns, cardPlanForWindow(1024).columns)
            assertEquals("rows at height $height", baselineCards.rows, cardPlanForWindow(1024).rows)
            assertEquals("header at height $height", baselineHeader, headerPlanForWindow(1024))
            assertTrue("vertical padding at $height", spacing.outerVerticalDp > 0)
        }
    }

    @Test
    fun genericRowPlanKeepsEveryItemExactlyOnce() {
        assertEquals(emptyList<IntRange>(), ResponsiveLayout.rowPlan(0, 2))
        assertEquals(listOf(0 until 3), ResponsiveLayout.rowPlan(3, 3))
        assertEquals(listOf(0 until 2, 2 until 3), ResponsiveLayout.rowPlan(3, 2))
        assertEquals(listOf(0 until 1, 1 until 2, 2 until 3), ResponsiveLayout.rowPlan(3, 1))
        assertEquals(listOf(0 until 3, 3 until 5), ResponsiveLayout.rowPlan(5, 3))
    }

    @Test
    fun invalidGridContractsFailFast() {
        assertThrows(IllegalArgumentException::class.java) {
            ResponsiveLayout.gridPlan(500, 3, minItemWidth = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResponsiveLayout.gridPlan(500, 3, minItemWidth = 240, maxItemWidth = 200)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResponsiveLayout.gridPlan(500, 3, gap = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResponsiveLayout.gridPlan(500, 3, maxColumns = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResponsiveLayout.gridPlan(500, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResponsiveLayout.rowPlan(-1, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResponsiveLayout.rowPlan(3, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResponsiveLayout.headerPlan(500, controlCount = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResponsiveLayout.verticalSpacingUnits(700, insetTop = -1, insetBottom = 0, density = 1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResponsiveLayout.pixelsToDp(500, 0f)
        }
    }

    private fun flattenedIndices(plan: ResponsiveLayout.GridPlan): List<Int> =
        plan.rows.flatMap { it.indices.toList() }

    private fun contentWidthForWindow(
        windowWidth: Int,
        insetLeft: Int = 0,
        insetRight: Int = 0
    ): Int = (windowWidth.toLong() - insetLeft - insetRight - 2L * ResponsiveLayout.OUTER_HORIZONTAL_DP)
        .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private fun cardPlanForWindow(windowWidth: Int): ResponsiveLayout.GridPlan =
        ResponsiveLayout.gridPlan(
            availableWidth = contentWidthForWindow(windowWidth),
            itemCount = ResponsiveLayout.CARD_COUNT
        )

    private fun headerPlanForWindow(windowWidth: Int): ResponsiveLayout.HeaderPlan =
        ResponsiveLayout.headerPlan(contentWidthForWindow(windowWidth))

    private fun controlPlanForWindow(windowWidth: Int): ResponsiveLayout.GridPlan =
        ResponsiveLayout.gridPlan(
            availableWidth = contentWidthForWindow(windowWidth),
            itemCount = ResponsiveLayout.CONTROL_COUNT,
            minItemWidth = ResponsiveLayout.CONTROL_MIN_WIDTH_DP,
            maxItemWidth = ResponsiveLayout.CONTROL_MAX_WIDTH_DP,
            gap = ResponsiveLayout.ITEM_GAP_DP,
            maxColumns = ResponsiveLayout.CONTROL_COUNT
        )

    private fun runtimeHeaderPlan(widthPx: Int, density: Float): ResponsiveLayout.HeaderPlan =
        ResponsiveLayout.headerPlanUnits(
            availableWidth = widthPx,
            controlCount = ResponsiveLayout.CONTROL_COUNT,
            titleMinWidth = ResponsiveLayout.dpToPixels(ResponsiveLayout.HEADER_TITLE_MIN_WIDTH_DP, density),
            statusMinWidth = ResponsiveLayout.dpToPixels(ResponsiveLayout.HEADER_STATUS_MIN_WIDTH_DP, density),
            splitTitleMinWidth =
                ResponsiveLayout.dpToPixels(ResponsiveLayout.HEADER_SPLIT_TITLE_MIN_WIDTH_DP, density),
            splitStatusMinWidth =
                ResponsiveLayout.dpToPixels(ResponsiveLayout.HEADER_SPLIT_STATUS_MIN_WIDTH_DP, density),
            controlMinWidth = ResponsiveLayout.dpToPixels(ResponsiveLayout.CONTROL_MIN_WIDTH_DP, density),
            gap = ResponsiveLayout.dpToPixels(ResponsiveLayout.ITEM_GAP_DP, density)
        )
}
