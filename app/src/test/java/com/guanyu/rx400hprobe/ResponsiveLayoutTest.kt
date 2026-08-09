package com.guanyu.rx400hprobe

import org.junit.Assert.assertEquals
import org.junit.Test

class ResponsiveLayoutTest {

    @Test
    fun columnCountAdaptsToAvailableWidth() {
        assertEquals(1, ResponsiveLayout.columnCount(0))
        assertEquals(1, ResponsiveLayout.columnCount(120))
        assertEquals(1, ResponsiveLayout.columnCount(239))
        assertEquals(1, ResponsiveLayout.columnCount(320))
        assertEquals(2, ResponsiveLayout.columnCount(480))
        assertEquals(2, ResponsiveLayout.columnCount(600))
        assertEquals(2, ResponsiveLayout.columnCount(719))
        assertEquals(3, ResponsiveLayout.columnCount(720))
        assertEquals(3, ResponsiveLayout.columnCount(1280))
        assertEquals(3, ResponsiveLayout.columnCount(4000))
    }

    @Test
    fun rowPlanArrangesCardsInComputedRows() {
        assertEquals(listOf(0 until 3), ResponsiveLayout.rowPlan(3, 3))
        assertEquals(listOf(0 until 2, 2 until 3), ResponsiveLayout.rowPlan(3, 2))
        assertEquals(listOf(0 until 1, 1 until 2, 2 until 3), ResponsiveLayout.rowPlan(3, 1))
        assertEquals(emptyList<IntRange>(), ResponsiveLayout.rowPlan(0, 2))
        assertEquals(listOf(0 until 3, 3 until 5), ResponsiveLayout.rowPlan(5, 3))
    }
}
