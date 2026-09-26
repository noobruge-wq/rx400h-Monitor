package com.guanyu.rx400hprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelDashboardModelTest {
    @Test fun selfTestAcceptsFirstFreshSampleAfterSessionVersionResetButNotOldLiveData() {
        assertTrue(PixelDashboardModel.newSelfTestSource(1, 1, false, true))
        assertFalse(PixelDashboardModel.newSelfTestSource(1, 1, true, true))
        assertFalse(PixelDashboardModel.newSelfTestSource(1, 2, false, false))
        assertTrue(PixelDashboardModel.newSelfTestSource(1, 2, true, true))
    }
    @Test
    fun socUsesTheConfirmedToyotaDisplayCompression() {
        assertEquals(0f, PixelDashboardModel.socVisualFraction(0f), EPSILON)
        assertEquals(0.15f, PixelDashboardModel.socVisualFraction(30f), EPSILON)
        assertEquals(0.525f, PixelDashboardModel.socVisualFraction(55f), EPSILON)
        assertEquals(0.90f, PixelDashboardModel.socVisualFraction(80f), EPSILON)
        assertEquals(1f, PixelDashboardModel.socVisualFraction(100f), EPSILON)
        assertEquals(1f, PixelDashboardModel.socVisualFraction(120f), EPSILON)
    }

    @Test
    fun normalPowerUsesOnlyTheRealSideAndKeepsZeroKnown() {
        val charge = PixelDashboardModel.powerSegments(-10f)
        assertTrue(charge.known)
        assertEquals(-10f, charge.leftKw, EPSILON)
        assertEquals(0f, charge.rightKw, EPSILON)

        val discharge = PixelDashboardModel.powerSegments(12f)
        assertEquals(0f, discharge.leftKw, EPSILON)
        assertEquals(12f, discharge.rightKw, EPSILON)

        val zero = PixelDashboardModel.powerSegments(0f)
        assertTrue(zero.known)
        assertEquals(0f, zero.leftKw, EPSILON)
        assertEquals(0f, zero.rightKw, EPSILON)

        assertFalse(PixelDashboardModel.powerSegments(Float.NaN).known)
    }

    @Test
    fun powerSelfTestExpandsBothSidesThenCollapsesUnusedSide() {
        val midpoint = PixelDashboardModel.powerSelfTestSegments(300L, 10f)
        assertEquals(-25f, midpoint.leftKw, EPSILON)
        assertEquals(25f, midpoint.rightKw, EPSILON)

        val full = PixelDashboardModel.powerSelfTestSegments(600L, 10f)
        assertEquals(-50f, full.leftKw, EPSILON)
        assertEquals(50f, full.rightKw, EPSILON)

        val finishedPositive = PixelDashboardModel.powerSelfTestSegments(1_000L, 10f)
        assertEquals(0f, finishedPositive.leftKw, EPSILON)
        assertEquals(10f, finishedPositive.rightKw, EPSILON)

        val finishedNegative = PixelDashboardModel.powerSelfTestSegments(1_000L, -8f)
        assertEquals(-8f, finishedNegative.leftKw, EPSILON)
        assertEquals(0f, finishedNegative.rightKw, EPSILON)
    }

    @Test
    fun startupSocRunsZeroToFullThenReturnsToActual() {
        assertEquals(0f, PixelDashboardModel.startupSoc(0L, 60f), EPSILON)
        assertEquals(50f, PixelDashboardModel.startupSoc(300L, 60f), EPSILON)
        assertEquals(100f, PixelDashboardModel.startupSoc(600L, 60f), EPSILON)
        assertEquals(80f, PixelDashboardModel.startupSoc(800L, 60f), EPSILON)
        assertEquals(60f, PixelDashboardModel.startupSoc(1_000L, 60f), EPSILON)
    }

    @Test
    fun wheelMappingIsDirectionNeutralBoundedAndStopsAtZero() {
        assertEquals(0f, PixelDashboardModel.wheelRevolutionsPerSecond(-1f), EPSILON)
        assertEquals(0f, PixelDashboardModel.wheelRevolutionsPerSecond(0f), EPSILON)
        assertEquals(1.25f, PixelDashboardModel.wheelRevolutionsPerSecond(60f), EPSILON)
        assertEquals(2.5f, PixelDashboardModel.wheelRevolutionsPerSecond(120f), EPSILON)
        assertEquals(2.5f, PixelDashboardModel.wheelRevolutionsPerSecond(200f), EPSILON)
    }

    @Test
    fun settingsSuppressesWheelOnlyFramesButKeepsContentTransitions() {
        assertTrue(PixelDashboardModel.animationFrameNeeded(false, false, false, 1f))
        assertTrue(PixelDashboardModel.animationFrameNeeded(false, false, true, 0f))
        assertFalse(PixelDashboardModel.animationFrameNeeded(true, false, false, 1f))
        assertFalse(PixelDashboardModel.animationFrameNeeded(true, false, true, 0f))
        assertTrue(PixelDashboardModel.animationFrameNeeded(true, true, true, 1f))
    }

    @Test
    fun dynamicFormatsMatchThePixelGlyphContractWithoutInventingZero() {
        assertEquals("61.5 %", PixelDashboardModel.formatSoc(61.5, true))
        assertEquals("--.- %", PixelDashboardModel.formatSoc(61.5, false))
        assertEquals("42 km/h", PixelDashboardModel.formatSpeed(42.4, true))
        assertEquals("−8.4 kW", PixelDashboardModel.formatPower(-8.4, true))
        assertEquals(" 8.4 kW", PixelDashboardModel.formatPower(8.4, true))
        assertEquals("--.- kW", PixelDashboardModel.formatPower(null, false))
        assertEquals("33.0 / 27.0°", PixelDashboardModel.formatTemperatureRange(33.0, 27.0, true))
    }

    @Test
    fun effectSettingsStayInsideTheUserExposedRanges() {
        val bounded = CrtEffectSettings(-2, 9, 0, 150, -1).bounded()
        assertEquals(CrtEffectSettings(0, 4, 1, 100, 0), bounded)
    }

    @Test fun stationaryAndUnknownWheelsAreDimButMovingWheelsAreBright() {
        assertEquals(0.4f, PixelDashboardModel.wheelAlpha(0.0, true, 1f), EPSILON)
        assertEquals(0.4f, PixelDashboardModel.wheelAlpha(60.0, false, 1f), EPSILON)
        assertEquals(1f, PixelDashboardModel.wheelAlpha(60.0, true, 1f), EPSILON)
        assertEquals("---- rpm", PixelDashboardModel.formatRpm(null, false))
        assertEquals("-- °C", PixelDashboardModel.formatCoolant(null, false))
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
