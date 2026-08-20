package com.guanyu.rx400hprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MonitorSessionStateTest {

    @Test
    fun recoveryIsExplicitAndRejectsAllActions() {
        assertEquals(
            MonitorControlState(deviceEnabled = false, startEnabled = false, endEnabled = false),
            MonitorSessionPolicy.controls(MonitorSessionPhase.RECOVERING)
        )
        assertEquals("RECOVERING", MonitorSessionPolicy.modeCode(MonitorSessionPhase.RECOVERING))
    }

    @Test
    fun idleExposesOnlyDeviceAndStart() {
        val controls = MonitorSessionPolicy.controls(MonitorSessionPhase.IDLE)
        assertTrue(controls.deviceEnabled)
        assertTrue(controls.startEnabled)
        assertFalse(controls.endEnabled)
    }

    @Test
    fun endIsTheOnlyAvailableActionWhileStartingOrLive() {
        val cancellable = listOf(
            MonitorSessionPhase.WAITING_PERMISSION,
            MonitorSessionPhase.CONNECTING,
            MonitorSessionPhase.INITIALIZING,
            MonitorSessionPhase.LIVE
        )
        cancellable.forEach { phase ->
            val controls = MonitorSessionPolicy.controls(phase)
            assertFalse(phase.name, controls.deviceEnabled)
            assertFalse(phase.name, controls.startEnabled)
            assertTrue(phase.name, controls.endEnabled)
        }
    }

    @Test
    fun stoppingAndSavingRejectDuplicateActions() {
        listOf(MonitorSessionPhase.STOPPING, MonitorSessionPhase.SAVING).forEach { phase ->
            val controls = MonitorSessionPolicy.controls(phase)
            assertEquals(phase.name, MonitorControlState(false, false, false), controls)
        }
    }

    @Test
    fun saveFailureOffersOnlyRetryThroughEnd() {
        assertEquals(
            MonitorControlState(deviceEnabled = false, startEnabled = false, endEnabled = true),
            MonitorSessionPolicy.controls(MonitorSessionPhase.SAVE_FAILED)
        )
        assertEquals("SAVE_FAILED", MonitorSessionPolicy.modeCode(MonitorSessionPhase.SAVE_FAILED))
    }

    @Test
    fun processLeaseSerializesReplacementSessionOwners() {
        val lease = ExclusiveSessionLease()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondAttempted = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)

        val first = thread(start = true) {
            lease.withLease {
                firstEntered.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
            }
        }
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS))

        val second = thread(start = true) {
            secondAttempted.countDown()
            lease.withLease { secondEntered.countDown() }
        }
        assertTrue(secondAttempted.await(2, TimeUnit.SECONDS))
        assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))

        releaseFirst.countDown()
        assertTrue(secondEntered.await(2, TimeUnit.SECONDS))
        first.join(2_000)
        second.join(2_000)
        assertFalse(first.isAlive)
        assertFalse(second.isAlive)
    }

    @Test
    fun processLeaseReleasesAfterOwnerFailure() {
        val lease = ExclusiveSessionLease()
        assertTrue(runCatching { lease.withLease<Unit> { error("expected") } }.isFailure)
        var entered = false
        lease.withLease { entered = true }
        assertTrue(entered)
    }

    @Test
    fun waitingReplacementOwnerCanBeCancelledWithoutEntering() {
        val lease = ExclusiveSessionLease()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondWaiting = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val continueWaiting = AtomicBoolean(true)
        val secondEntered = AtomicBoolean(false)
        val secondRan = AtomicBoolean(true)

        val first = thread(start = true) {
            lease.withLease {
                firstEntered.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
            }
        }
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS))

        val second = thread(start = true) {
            secondWaiting.countDown()
            secondRan.set(
                lease.withCancellableLease(continueWaiting::get) {
                    secondEntered.set(true)
                }
            )
            secondFinished.countDown()
        }
        assertTrue(secondWaiting.await(2, TimeUnit.SECONDS))
        continueWaiting.set(false)
        assertTrue(secondFinished.await(2, TimeUnit.SECONDS))
        assertFalse(secondRan.get())
        assertFalse(secondEntered.get())

        releaseFirst.countDown()
        first.join(2_000)
        second.join(2_000)
        assertFalse(first.isAlive)
        assertFalse(second.isAlive)
    }

    @Test
    fun firstTerminalIntentWinsAcrossEndAndDestroy() {
        val latch = SessionFinalizationLatch()
        val userEnd = SessionFinalizationIntent(
            LogCompletionKind.COMPLETED,
            "USER_END",
            requestedAtWallMs = 100L,
            requestedAtElapsedMs = 50L
        )
        val destroyed = SessionFinalizationIntent(
            LogCompletionKind.INTERRUPTED,
            "ACTIVITY_DESTROYED",
            requestedAtWallMs = 101L,
            requestedAtElapsedMs = 51L
        )

        assertEquals(userEnd, latch.claim(userEnd))
        repeat(100) { assertEquals(userEnd, latch.claim(destroyed)) }
        assertEquals(userEnd, latch.current())
    }

    @Test
    fun destroyWinsWhenItIsTheFirstTerminalIntent() {
        val latch = SessionFinalizationLatch()
        val destroyed = SessionFinalizationIntent(
            LogCompletionKind.INTERRUPTED,
            "ACTIVITY_DESTROYED_BEFORE_LIVE",
            requestedAtWallMs = 200L,
            requestedAtElapsedMs = 80L
        )
        val lateEnd = SessionFinalizationIntent(
            LogCompletionKind.COMPLETED,
            "USER_END",
            requestedAtWallMs = 201L,
            requestedAtElapsedMs = 81L
        )

        assertEquals(destroyed, latch.claim(destroyed))
        assertEquals(destroyed, latch.claim(lateEnd))
    }

    @Test
    fun concurrentTerminalClaimsConvergeOnOneIntent() {
        val latch = SessionFinalizationLatch()
        val start = CountDownLatch(1)
        val finished = CountDownLatch(32)
        val results = Collections.synchronizedList(mutableListOf<SessionFinalizationIntent>())
        val userEnd = SessionFinalizationIntent(
            LogCompletionKind.COMPLETED,
            "USER_END",
            requestedAtWallMs = 300L,
            requestedAtElapsedMs = 100L
        )
        val destroyed = SessionFinalizationIntent(
            LogCompletionKind.INTERRUPTED,
            "ACTIVITY_DESTROYED",
            requestedAtWallMs = 301L,
            requestedAtElapsedMs = 101L
        )

        val workers = List(32) { index ->
            thread(start = true) {
                start.await(2, TimeUnit.SECONDS)
                results += latch.claim(if (index % 2 == 0) userEnd else destroyed)
                finished.countDown()
            }
        }
        start.countDown()

        assertTrue(finished.await(2, TimeUnit.SECONDS))
        workers.forEach { it.join(2_000) }
        val winner = checkNotNull(latch.current())
        assertEquals(1, results.distinct().size)
        assertTrue(results.all { it == winner })
        assertTrue(winner == userEnd || winner == destroyed)
    }

    @Test
    fun interruptedPendingConnectionCloseReturnsPreSessionPhasesToIdle() {
        assertEquals(
            MonitorSessionPhase.IDLE,
            phaseAfterPendingConnectionCloseFailure(
                MonitorSessionPhase.CONNECTING,
                destroying = false
            )
        )
        assertEquals(
            MonitorSessionPhase.IDLE,
            phaseAfterPendingConnectionCloseFailure(
                MonitorSessionPhase.STOPPING,
                destroying = false
            )
        )
    }

    @Test
    fun pendingConnectionCloseFailureDoesNotRewriteDestroyOrUnrelatedPhases() {
        assertEquals(
            MonitorSessionPhase.CONNECTING,
            phaseAfterPendingConnectionCloseFailure(
                MonitorSessionPhase.CONNECTING,
                destroying = true
            )
        )
        assertEquals(
            MonitorSessionPhase.LIVE,
            phaseAfterPendingConnectionCloseFailure(
                MonitorSessionPhase.LIVE,
                destroying = false
            )
        )
    }
}
