package com.guanyu.rx400hprobe

import android.os.Debug
import android.os.Process
import android.os.SystemClock
import java.time.Instant

/**
 * V0.2.0 constant-space performance sampler.
 *
 * Produces one bounded sample row every ~5 s: PSS, Java heap, process CPU
 * delta, alloc/freed counters (where available), scheduler cycle duration,
 * UI render duration and logger write duration.
 */
class PerformanceTracker {
    data class LoggerMetrics(
        val writeTotalMs: Long,
        val checkpointTotalMs: Long,
        val syncTotalMs: Long,
        val checkpointMaxMs: Long,
        val checkpointLockWaitMs: Long
    )

    data class SchedulerMetrics(
        val requestHz: Double,
        val signalUpdateHz: Double,
        val deadlineMisses: Long,
        val skippedOverdue: Long,
        val latencyP50Ms: Long,
        val latencyP95Ms: Long,
        val latencyP99Ms: Long,
        val noData: Long,
        val timeout: Long,
        val busError: Long
    )

    data class Sample(
        val wallTimeIso: String,
        val elapsedMs: Long,
        val pssKb: Long,
        val javaHeapUsedKb: Long,
        val javaHeapTotalKb: Long,
        val cpuDeltaMs: Long,
        val allocDelta: Long,
        val freedDelta: Long,
        val cycleMs: Long,
        val renderMs: Long,
        val frameLogMs: Long,
        val requestHz: Double,
        val signalUpdateHz: Double,
        val deadlineMisses: Long,
        val skippedOverdue: Long,
        val latencyP50Ms: Long,
        val latencyP95Ms: Long,
        val latencyP99Ms: Long,
        val noData: Long,
        val timeout: Long,
        val busError: Long,
        val loggerWriteTotalMs: Long,
        val loggerCheckpointTotalMs: Long,
        val loggerSyncTotalMs: Long,
        val loggerCheckpointMaxMs: Long,
        val loggerCheckpointLockWaitMs: Long
    )

    private var lastCpuMs: Long = Process.getElapsedCpuTime()
    private var lastAllocCount: Long = Debug.getGlobalAllocCount().toLong()
    private var lastFreedCount: Long = Debug.getGlobalFreedCount().toLong()
    private var sessionStartElapsedMs: Long = SystemClock.elapsedRealtime()

    fun reset() {
        sessionStartElapsedMs = SystemClock.elapsedRealtime()
        lastCpuMs = Process.getElapsedCpuTime()
        lastAllocCount = Debug.getGlobalAllocCount().toLong()
        lastFreedCount = Debug.getGlobalFreedCount().toLong()
    }

    fun sample(
        cycleMs: Long,
        renderMs: Long,
        frameLogMs: Long,
        scheduler: SchedulerMetrics,
        logger: LoggerMetrics
    ): Sample {
        val now = SystemClock.elapsedRealtime()
        val cpu = Process.getElapsedCpuTime()
        val alloc = Debug.getGlobalAllocCount().toLong()
        val freed = Debug.getGlobalFreedCount().toLong()
        val runtime = Runtime.getRuntime()
        val sample = Sample(
            wallTimeIso = Instant.now().toString(),
            elapsedMs = (now - sessionStartElapsedMs).coerceAtLeast(0L),
            pssKb = Debug.getPss().toLong(),
            javaHeapUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L,
            javaHeapTotalKb = runtime.totalMemory() / 1024L,
            cpuDeltaMs = (cpu - lastCpuMs).coerceAtLeast(0L),
            allocDelta = (alloc - lastAllocCount).coerceAtLeast(0L),
            freedDelta = (freed - lastFreedCount).coerceAtLeast(0L),
            cycleMs = cycleMs,
            renderMs = renderMs,
            frameLogMs = frameLogMs,
            requestHz = scheduler.requestHz,
            signalUpdateHz = scheduler.signalUpdateHz,
            deadlineMisses = scheduler.deadlineMisses,
            skippedOverdue = scheduler.skippedOverdue,
            latencyP50Ms = scheduler.latencyP50Ms,
            latencyP95Ms = scheduler.latencyP95Ms,
            latencyP99Ms = scheduler.latencyP99Ms,
            noData = scheduler.noData,
            timeout = scheduler.timeout,
            busError = scheduler.busError,
            loggerWriteTotalMs = logger.writeTotalMs,
            loggerCheckpointTotalMs = logger.checkpointTotalMs,
            loggerSyncTotalMs = logger.syncTotalMs,
            loggerCheckpointMaxMs = logger.checkpointMaxMs,
            loggerCheckpointLockWaitMs = logger.checkpointLockWaitMs
        )
        lastCpuMs = cpu
        lastAllocCount = alloc
        lastFreedCount = freed
        return sample
    }
}
