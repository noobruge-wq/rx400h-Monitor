package com.guanyu.rx400hprobe

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** One blocking reader per socket; one timed consumer, fixed memory, no polling.
 * The transport closer must capture THIS socket, never a mutable current socket.
 */
internal class ArrivalInput(
    private val source: InputStream,
    private val closeTransport: () -> Unit,
    capacity: Int = 16_384
) : Closeable {
    private val monitor = Object()
    private val bytes = ByteArray(capacity.also { require(it > 0) })
    private val arrivalNs = LongArray(capacity)
    private var head = 0
    private var size = 0
    private var closed = false
    private var eof = false
    private var failure: IOException? = null
    var lastReadAtNs = 0L
        private set
    private val worker = Thread({ pump() }, "RX400h-BluetoothInput").apply {
        isDaemon = true
        start()
    }

    private fun pump() {
        val chunk = ByteArray(minOf(2048, bytes.size))
        try {
            while (true) {
                val count = source.read(chunk)
                val received = System.nanoTime()
                synchronized(monitor) {
                    if (closed) return
                    if (count < 0) { eof = true; monitor.notifyAll(); return }
                    if (count == 0) throw IOException("Bluetooth stream returned an empty blocking read")
                    if (count > bytes.size - size) throw IOException("ELM receive buffer overflow")
                    for (i in 0 until count) {
                        val tail = (head + size) % bytes.size
                        bytes[tail] = chunk[i]
                        arrivalNs[tail] = received
                        size++
                    }
                    monitor.notifyAll()
                }
            }
        } catch (problem: Exception) {
            synchronized(monitor) {
                if (!closed) failure = if (problem is IOException) problem else IOException("ELM input failed", problem)
                monitor.notifyAll()
            }
            // Overflow/failure must stop this connection, not silently lose bytes.
            runCatching(closeTransport)
        }
    }

    /** 0 = deadline, -1 = EOF. Failure/close never serves potentially partial data. */
    fun read(target: ByteArray, timeoutMs: Long): Int {
        require(target.isNotEmpty() && timeoutMs >= 0)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        synchronized(monitor) {
            while (true) {
                if (closed) throw IOException("ELM input closed")
                failure?.let { throw it }
                if (size > 0) {
                    val count = minOf(size, target.size)
                    for (i in 0 until count) {
                        target[i] = bytes[head]
                        lastReadAtNs = arrivalNs[head]
                        head = (head + 1) % bytes.size
                    }
                    size -= count
                    return count
                }
                if (eof) return -1
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) return 0
                try { TimeUnit.NANOSECONDS.timedWait(monitor, remaining) }
                catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("ELM input wait interrupted", interrupted)
                }
            }
        }
    }

    override fun close() {
        synchronized(monitor) {
            if (closed) return
            closed = true
            size = 0
            monitor.notifyAll()
        }
        // Closing the socket first unblocks the blocking Bluetooth read.
        runCatching(closeTransport)
        runCatching { source.close() }
    }
}
