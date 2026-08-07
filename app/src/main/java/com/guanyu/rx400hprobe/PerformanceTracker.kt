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
        val loggerWriteMs: Long
    )

    private var lastCpuMs: Long = Process.getElapsedCpuTime()
    private var lastAllocCount: Long = Debug.getGlobalAllocCount().toLong()
    private var lastFreedCount: Long = Debug.getGlobalFreedCount().toLong()

    fun sample(cycleMs: Long, renderMs: Long, loggerWriteMs: Long): Sample {
        val now = SystemClock.elapsedRealtime()
        val cpu = Process.getElapsedCpuTime()
        val alloc = Debug.getGlobalAllocCount().toLong()
        val freed = Debug.getGlobalFreedCount().toLong()
        val runtime = Runtime.getRuntime()
        val sample = Sample(
            wallTimeIso = Instant.now().toString(),
            elapsedMs = now,
            pssKb = Debug.getPss().toLong(),
            javaHeapUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L,
            javaHeapTotalKb = runtime.totalMemory() / 1024L,
            cpuDeltaMs = (cpu - lastCpuMs).coerceAtLeast(0L),
            allocDelta = (alloc - lastAllocCount).coerceAtLeast(0L),
            freedDelta = (freed - lastFreedCount).coerceAtLeast(0L),
            cycleMs = cycleMs,
            renderMs = renderMs,
            loggerWriteMs = loggerWriteMs
        )
        lastCpuMs = cpu
        lastAllocCount = alloc
        lastFreedCount = freed
        return sample
    }
}
