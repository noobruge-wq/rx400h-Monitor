package com.guanyu.rx400hprobe

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** Fixed V0.3.1 session phases used as the single dashboard-control source. */
internal enum class MonitorSessionPhase {
    IDLE,
    WAITING_PERMISSION,
    CONNECTING,
    INITIALIZING,
    LIVE,
    STOPPING,
    SAVING,
    SAVE_FAILED
}

/** Process-local ownership boundary for one Bluetooth/session worker at a time. */
internal class ExclusiveSessionLease {
    private val permit = Semaphore(1, true)

    fun <T> withLease(block: () -> T): T {
        permit.acquireUninterruptibly()
        return try {
            block()
        } finally {
            permit.release()
        }
    }

    fun withCancellableLease(shouldContinue: () -> Boolean, block: () -> Unit): Boolean {
        while (shouldContinue()) {
            val acquired = try {
                permit.tryAcquire(50L, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
            if (!acquired) continue
            return try {
                if (!shouldContinue()) false else {
                    block()
                    true
                }
            } finally {
                permit.release()
            }
        }
        return false
    }
}

internal data class MonitorControlState(
    val deviceEnabled: Boolean,
    val startEnabled: Boolean,
    val endEnabled: Boolean
)

internal object MonitorSessionPolicy {
    fun controls(phase: MonitorSessionPhase): MonitorControlState = when (phase) {
        MonitorSessionPhase.IDLE -> MonitorControlState(
            deviceEnabled = true,
            startEnabled = true,
            endEnabled = false
        )
        MonitorSessionPhase.WAITING_PERMISSION,
        MonitorSessionPhase.CONNECTING,
        MonitorSessionPhase.INITIALIZING,
        MonitorSessionPhase.LIVE -> MonitorControlState(
            deviceEnabled = false,
            startEnabled = false,
            endEnabled = true
        )
        MonitorSessionPhase.STOPPING,
        MonitorSessionPhase.SAVING -> MonitorControlState(
            deviceEnabled = false,
            startEnabled = false,
            endEnabled = false
        )
        MonitorSessionPhase.SAVE_FAILED -> MonitorControlState(
            deviceEnabled = false,
            startEnabled = false,
            endEnabled = true
        )
    }

    fun modeCode(phase: MonitorSessionPhase): String = when (phase) {
        MonitorSessionPhase.IDLE -> "IDLE"
        MonitorSessionPhase.WAITING_PERMISSION -> "PERMISSION"
        MonitorSessionPhase.CONNECTING -> "CONNECTING"
        MonitorSessionPhase.INITIALIZING -> "INITIALIZING"
        MonitorSessionPhase.LIVE -> "LIVE"
        MonitorSessionPhase.STOPPING -> "STOPPING"
        MonitorSessionPhase.SAVING -> "SAVING"
        MonitorSessionPhase.SAVE_FAILED -> "SAVE_FAILED"
    }
}
