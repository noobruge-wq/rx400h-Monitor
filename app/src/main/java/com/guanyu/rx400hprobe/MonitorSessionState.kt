package com.guanyu.rx400hprobe

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

internal enum class MonitorSessionPhase { IDLE, WAITING_PERMISSION, CONNECTING, INITIALIZING, LIVE, STOPPING }

/** Shared across Activities so recreation cannot create two Bluetooth owners. */
internal class ExclusiveSessionLease {
    private val permit = Semaphore(1, true)
    fun withCancellableLease(shouldContinue: () -> Boolean, block: () -> Unit): Boolean {
        while (shouldContinue()) {
            val acquired = try { permit.tryAcquire(50L, TimeUnit.MILLISECONDS) }
            catch (_: InterruptedException) { Thread.currentThread().interrupt(); return false }
            if (!acquired) continue
            return try {
                if (!shouldContinue()) false else { block(); true }
            } finally { permit.release() }
        }
        return false
    }
}
