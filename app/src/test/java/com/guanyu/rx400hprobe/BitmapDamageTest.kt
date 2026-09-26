package com.guanyu.rx400hprobe

import org.junit.Assert.*
import org.junit.Test

class BitmapDamageTest {
    @Test fun clipsAndUsesExclusiveRightBottomEdges() {
        val damage = BitmapDamage()
        damage.mark(-12, -12, 160, 90)
        assertTrue(damage.contains(0, 0))
        assertFalse(damage.contains(1, 0))
        assertFalse(damage.contains(0, 1))
        damage.mark(1279, 719, 1400, 800)
        assertTrue(damage.contains(7, 7))
    }

    @Test fun includesBothSidesOfATileBoundary() {
        val damage = BitmapDamage()
        damage.mark(159, 89, 161, 91)
        for (row in 0..1) for (column in 0..1) assertTrue(damage.contains(column, row))
        damage.clear()
        assertFalse(damage.any)
        for (row in 0..7) for (column in 0..7) assertFalse(damage.contains(column, row))
    }

    @Test fun fullInvalidationAndOffscreenEmptyRectangles() {
        val damage = BitmapDamage()
        damage.mark(1300, 0, 1400, 20)
        damage.mark(0, 800, 10, 900)
        damage.mark(20, 20, 20, 21)
        assertFalse(damage.any)
        damage.all()
        for (row in 0..7) for (column in 0..7) assertTrue(damage.contains(column, row))
    }
}
