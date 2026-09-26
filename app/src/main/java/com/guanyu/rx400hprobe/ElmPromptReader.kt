package com.guanyu.rx400hprobe

import java.io.EOFException
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Reused bulk buffer. A prompt is a strict transaction boundary, not a timer guess. */
internal class ElmPromptReader(private val input: ArrivalInput) {
    private val buffer = ByteArray(2048)
    data class Reply(val text: String, val prompt: Boolean)

    /** Zero-wait boundary check; delayed line endings are not a new reply. */
    fun discardBufferedPadding() {
        while (true) {
            val count = input.read(buffer, 0)
            if (count < 0) throw EOFException("ELM stream ended between commands")
            if (count == 0) return
            for (i in 0 until count) {
                val ch = (buffer[i].toInt() and 255).toChar()
                if (ch != '\r' && ch != '\n' && ch != '\u0000' && ch != ' ')
                    throw IOException("Unexpected data between ELM commands")
            }
        }
    }

    fun drain(durationMs: Long) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMs)
        while (true) {
            val left = remainingMs(deadline)
            if (left <= 0) return
            if (input.read(buffer, left) < 0) throw EOFException("ELM stream ended while draining")
        }
    }

    fun receive(startNs: Long, timeoutMs: Long, quietMs: Long): Reply {
        require(timeoutMs > 0 && quietMs >= 0)
        val deadline = startNs + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        val response = StringBuilder(256)
        var promptAt: Long? = null
        while (promptAt == null) {
            val left = remainingMs(deadline)
            if (left <= 0) break
            val count = input.read(buffer, left)
            if (count < 0) throw EOFException("ELM stream ended before prompt")
            if (count == 0) continue
            if (input.lastReadAtNs > deadline) break
            for (i in 0 until count) {
                val ch = (buffer[i].toInt() and 255).toChar()
                if (promptAt != null) {
                    if (ch != '\r' && ch != '\n' && ch != '\u0000' && ch != ' ')
                        throw IOException("Unexpected data after ELM prompt")
                } else if (ch == '>') promptAt = input.lastReadAtNs
                else if (ch != '\u0000') response.append(ch)
            }
            if (response.length > 16_384) throw IOException("ELM response too large")
        }
        if (promptAt != null && quietMs > 0) {
            val quietEnd = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(quietMs)
            while (true) {
                val left = remainingMs(quietEnd)
                if (left <= 0) break
                val count = input.read(buffer, left)
                if (count < 0) throw EOFException("ELM stream ended after prompt")
                for (i in 0 until count) {
                    val ch = (buffer[i].toInt() and 255).toChar()
                    if (ch != '>' && ch != '\u0000') response.append(ch)
                }
                if (response.length > 16_384) throw IOException("ELM response too large")
            }
        }
        return Reply(response.toString(), promptAt != null)
    }

    private fun remainingMs(deadline: Long): Long {
        val ns = deadline - System.nanoTime()
        return if (ns <= 0) 0 else (ns + 999_999L) / 1_000_000L
    }
}
