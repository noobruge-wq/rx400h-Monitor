package com.guanyu.rx400hprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WallClockAdjustmentDetectorTest {

    @Test
    fun firstSampleAndMatchingProgressionDoNotReport() {
        val detector = WallClockAdjustmentDetector()

        assertNull(detector.observe(elapsedRealtimeMs = 1_000L, wallTimeMs = 100_000L))
        assertNull(detector.observe(elapsedRealtimeMs = 2_500L, wallTimeMs = 101_500L))
    }

    @Test
    fun driftBelowThresholdDoesNotReportInEitherDirection() {
        val forward = WallClockAdjustmentDetector()
        forward.observe(elapsedRealtimeMs = 1_000L, wallTimeMs = 100_000L)
        assertNull(forward.observe(elapsedRealtimeMs = 2_000L, wallTimeMs = 102_999L))

        val backward = WallClockAdjustmentDetector()
        backward.observe(elapsedRealtimeMs = 1_000L, wallTimeMs = 100_000L)
        assertNull(backward.observe(elapsedRealtimeMs = 2_000L, wallTimeMs = 99_001L))
    }

    @Test
    fun exactThresholdForwardStepReportsExpectedContext() {
        val detector = WallClockAdjustmentDetector()
        detector.observe(elapsedRealtimeMs = 1_000L, wallTimeMs = 100_000L)

        val report = checkNotNull(
            detector.observe(elapsedRealtimeMs = 2_000L, wallTimeMs = 103_000L)
        )

        assertEquals(WallClockAdjustmentDirection.FORWARD, report.direction)
        assertEquals(2_000L, report.adjustmentMs)
        assertEquals(1_000L, report.anchorElapsedRealtimeMs)
        assertEquals(2_000L, report.observedElapsedRealtimeMs)
        assertEquals(100_000L, report.anchorWallTimeMs)
        assertEquals(101_000L, report.expectedWallTimeMs)
        assertEquals(103_000L, report.observedWallTimeMs)
    }

    @Test
    fun exactThresholdBackwardStepReportsExpectedContext() {
        val detector = WallClockAdjustmentDetector()
        detector.observe(elapsedRealtimeMs = 1_000L, wallTimeMs = 100_000L)

        val report = checkNotNull(
            detector.observe(elapsedRealtimeMs = 2_000L, wallTimeMs = 99_000L)
        )

        assertEquals(WallClockAdjustmentDirection.BACKWARD, report.direction)
        assertEquals(-2_000L, report.adjustmentMs)
        assertEquals(101_000L, report.expectedWallTimeMs)
        assertEquals(99_000L, report.observedWallTimeMs)
    }

    @Test
    fun materialReportRebasesAndDoesNotRepeatTheSameStep() {
        val detector = WallClockAdjustmentDetector()
        detector.observe(elapsedRealtimeMs = 1_000L, wallTimeMs = 100_000L)
        checkNotNull(detector.observe(elapsedRealtimeMs = 2_000L, wallTimeMs = 103_000L))

        assertNull(detector.observe(elapsedRealtimeMs = 3_000L, wallTimeMs = 104_000L))
    }

    @Test
    fun endAndFinalizeBoundarySamplesReportWithoutChangingMonotonicOrder() {
        val detector = WallClockAdjustmentDetector()
        val sessionAnchorMs = 1_000L
        val beforeEndMs = 2_000L
        val endBoundaryMs = 2_100L
        val finalizeBoundaryMs = 2_600L

        assertNull(detector.observe(sessionAnchorMs, 100_000L))
        assertNull(detector.observe(beforeEndMs, 101_000L))

        val endStep = checkNotNull(detector.observe(endBoundaryMs, 104_100L))
        assertEquals(WallClockAdjustmentDirection.FORWARD, endStep.direction)
        assertEquals(3_000L, endStep.adjustmentMs)

        val finalizeStep = checkNotNull(detector.observe(finalizeBoundaryMs, 101_600L))
        assertEquals(WallClockAdjustmentDirection.BACKWARD, finalizeStep.direction)
        assertEquals(-3_000L, finalizeStep.adjustmentMs)
        assertEquals(endBoundaryMs, endStep.observedElapsedRealtimeMs)
        assertEquals(finalizeBoundaryMs, finalizeStep.observedElapsedRealtimeMs)
    }

    @Test
    fun resetMakesTheNextObservationANewFirstSample() {
        val detector = WallClockAdjustmentDetector()
        detector.observe(elapsedRealtimeMs = 1_000L, wallTimeMs = 100_000L)
        detector.reset()

        assertNull(detector.observe(elapsedRealtimeMs = 10L, wallTimeMs = 500_000L))
        assertNull(detector.observe(elapsedRealtimeMs = 20L, wallTimeMs = 500_010L))
    }

    @Test
    fun monotonicRegressionIsRejected() {
        val detector = WallClockAdjustmentDetector()
        detector.observe(elapsedRealtimeMs = 1_000L, wallTimeMs = 100_000L)

        assertThrows(IllegalArgumentException::class.java) {
            detector.observe(elapsedRealtimeMs = 999L, wallTimeMs = 100_001L)
        }
    }

    @Test
    fun nonPositiveThresholdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WallClockAdjustmentDetector(thresholdMs = 0L)
        }
    }
}
