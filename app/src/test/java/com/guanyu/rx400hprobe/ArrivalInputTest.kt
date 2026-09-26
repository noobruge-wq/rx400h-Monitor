package com.guanyu.rx400hprobe

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class ArrivalInputTest {
    private class Feed : InputStream() {
        private val lock = Object()
        private val data = ArrayDeque<Int>()
        private var ended = false
        fun send(text: String) = synchronized(lock) { text.forEach { data.addLast(it.code) }; lock.notifyAll() }
        override fun read(): Int = synchronized(lock) {
            while (data.isEmpty() && !ended) lock.wait()
            if (data.isEmpty()) -1 else data.removeFirst()
        }
        override fun read(target: ByteArray, off: Int, len: Int): Int = synchronized(lock) {
            while (data.isEmpty() && !ended) lock.wait()
            if (data.isEmpty()) return@synchronized -1
            val count = minOf(len, data.size)
            repeat(count) { target[off + it] = data.removeFirst().toByte() }
            count
        }
        override fun close() = synchronized(lock) { ended = true; lock.notifyAll() }
    }

    @Test fun fragmentedReplyCompletesOnlyAtPrompt() {
        val feed = Feed()
        ArrivalInput(feed, { feed.close() }).use { input ->
            val start = System.nanoTime()
            val writer = thread { feed.send("7E8 03 41"); Thread.sleep(20); feed.send(" 0D 2A\r>") }
            val reply = ElmPromptReader(input).receive(start, 1000, 0)
            writer.join(1000)
            assertTrue(reply.prompt)
            assertEquals("7E8 03 41 0D 2A\r", reply.text)
        }
    }

    @Test fun timeoutDoesNotInventPrompt() {
        val feed = Feed()
        ArrivalInput(feed, { feed.close() }).use { input ->
            val start = System.nanoTime()
            feed.send("7E803410D2A")
            val reply = ElmPromptReader(input).receive(start, 50, 0)
            assertFalse(reply.prompt)
        }
    }

    @Test fun closeWakesConsumerAndBlockingReader() {
        val feed = Feed()
        val closed = AtomicInteger()
        val input = ArrivalInput(feed, { closed.incrementAndGet(); feed.close() })
        val entered = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val consumer = thread {
            entered.countDown()
            try { input.read(ByteArray(16), 30_000); fail("close must fail pending read") }
            catch (_: IOException) { finished.countDown() }
        }
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        input.close()
        assertTrue(finished.await(1, TimeUnit.SECONDS))
        consumer.join(1000)
        assertTrue(closed.get() >= 1)
    }

    @Test fun overflowFailsInsteadOfDroppingBytes() {
        val ended = CountDownLatch(1)
        val input = ArrivalInput(ByteArrayInputStream(ByteArray(100)), { ended.countDown() }, 8)
        try {
            assertTrue(ended.await(1, TimeUnit.SECONDS))
            try { input.read(ByteArray(4), 100); fail("overflow must be reported") }
            catch (expected: IOException) { assertTrue(expected.message!!.contains("overflow")) }
        } finally { input.close() }
    }

    @Test fun unexpectedBytesAfterPromptFailClosed() {
        ArrivalInput(ByteArrayInputStream("OK>UNSOLICITED".toByteArray()), {}).use { input ->
            try { ElmPromptReader(input).receive(System.nanoTime(), 1000, 0); fail("trailing response") }
            catch (expected: IOException) { assertTrue(expected.message!!.contains("after ELM prompt")) }
        }
    }

    @Test fun eofWithoutPromptFails() {
        ArrivalInput(ByteArrayInputStream("OK".toByteArray()), {}).use { input ->
            try { ElmPromptReader(input).receive(System.nanoTime(), 1000, 0); fail("EOF") }
            catch (_: IOException) { }
        }
    }

    @Test fun oldConnectionCannotFeedNewConnection() {
        val oldFeed = Feed()
        val newFeed = Feed()
        val oldInput = ArrivalInput(oldFeed, { oldFeed.close() })
        ArrivalInput(newFeed, { newFeed.close() }).use { current ->
            oldInput.close()
            newFeed.send("OK>")
            assertEquals("OK", ElmPromptReader(current).receive(System.nanoTime(), 1000, 0).text)
        }
    }

    @Test fun backToBackRepliesStayOrdered() {
        val feed = Feed()
        ArrivalInput(feed, { feed.close() }).use { input ->
            val reader = ElmPromptReader(input)
            repeat(50) { index ->
                val start = System.nanoTime()
                feed.send("$index\r>\r\n")
                assertEquals("$index\r", reader.receive(start, 1000, 0).text)
            }
        }
    }

    @Test fun latePromptCannotCompleteAnExpiredTransaction() {
        val feed = Feed()
        ArrivalInput(feed, { feed.close() }).use { input ->
            val reader = ElmPromptReader(input)
            val expiredStart = System.nanoTime() - TimeUnit.SECONDS.toNanos(1)
            feed.send("OK>")
            assertFalse(reader.receive(expiredStart, 10, 0).prompt)
        }
    }

    @Test fun bufferedPaddingIsDiscardedButUnsolicitedReplyIsRejected() {
        fun buffered(text: String, verify: (ElmPromptReader) -> Unit) {
            val feed = Feed()
            val queued = CountDownLatch(1)
            val source = object : InputStream() {
                var first = true
                override fun read(): Int = error("bulk only")
                override fun read(target: ByteArray, off: Int, len: Int): Int {
                    if (first) {
                        first = false
                        val bytes = text.toByteArray()
                        bytes.copyInto(target, off)
                        return bytes.size
                    }
                    queued.countDown() // Previous pump iteration has published.
                    return feed.read(target, off, len)
                }
            }
            ArrivalInput(source, { feed.close() }).use { input ->
                assertTrue(queued.await(1, TimeUnit.SECONDS))
                verify(ElmPromptReader(input))
            }
        }
        buffered("\r\n \u0000") { it.discardBufferedPadding(); it.discardBufferedPadding() }
        buffered("OLD>") { reader ->
            try { reader.discardBufferedPadding(); fail("unsolicited reply") }
            catch (expected: IOException) { assertTrue(expected.message!!.contains("between ELM commands")) }
        }
    }

    @Test fun interruptedWaitPreservesInterruptAndFails() {
        val feed = Feed()
        ArrivalInput(feed, { feed.close() }).use { input ->
            try {
                Thread.currentThread().interrupt()
                try { input.read(ByteArray(4), 1000); fail("interrupted") }
                catch (_: IOException) { assertTrue(Thread.currentThread().isInterrupted) }
            } finally { Thread.interrupted() }
        }
    }
}
