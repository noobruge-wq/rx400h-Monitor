package com.guanyu.rx400hprobe

/**
 * Bounded latency ring buffer for scheduler percentiles (D-040).
 *
 * Adds are allocation-free; only the ~5 s performance sample copies and sorts
 * the values, so the hot request path stays lean.
 */
internal class LatencyWindow(private val capacity: Int = 64) {
    private val values = LongArray(capacity)
    private var head = 0
    private var size = 0

    fun add(ms: Long) {
        values[head] = ms
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun percentile(p: Double): Long {
        if (size == 0) return 0L
        val sorted = values.copyOf(size)
        sorted.sort()
        return sorted[((size - 1) * p).toInt()]
    }

    fun size(): Int = size

    fun max(): Long {
        var maximum = 0L
        for (index in 0 until size) if (values[index] > maximum) maximum = values[index]
        return maximum
    }

    fun clear() {
        head = 0
        size = 0
    }
}
