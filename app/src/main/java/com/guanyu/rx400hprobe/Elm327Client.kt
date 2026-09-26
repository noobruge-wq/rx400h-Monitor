package com.guanyu.rx400hprobe

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class Elm327Client {
    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var input: ArrivalInput? = null
    private var reader: ElmPromptReader? = null
    private var output: BufferedOutputStream? = null
    private val connectionLock = Any()
    private val connected = AtomicBoolean(false)
    private var lastCommandFinishedAtMs = 0L
    private var promptBoundarySynchronized = false

    @Throws(IOException::class, SecurityException::class)
    fun connect(device: BluetoothDevice, shouldContinue: () -> Boolean = { true }) {
        close()
        if (!shouldContinue()) throw IOException("OBD connection was cancelled")
        val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
        synchronized(connectionLock) {
            socket = s
            input = null
            reader = null
            output = null
            connected.set(false)
        }
        var openedInput: ArrivalInput? = null
        var openedOutput: BufferedOutputStream? = null
        try {
            if (!shouldContinue()) throw IOException("OBD connection was cancelled")
            s.connect()
            openedInput = ArrivalInput(s.inputStream, { s.close() })
            val openedReader = ElmPromptReader(openedInput)
            openedOutput = BufferedOutputStream(s.outputStream)
            val accepted = synchronized(connectionLock) {
                if (socket !== s || !shouldContinue()) {
                    false
                } else {
                    input = openedInput
                    reader = openedReader
                    output = openedOutput
                    connected.set(true)
                    promptBoundarySynchronized = false
                    lastCommandFinishedAtMs = 0L
                    true
                }
            }
            if (!accepted) throw IOException("OBD connection was cancelled")
            openedReader.drain(400)
        } catch (e: Exception) {
            synchronized(connectionLock) {
                if (socket === s) {
                    socket = null
                    input = null
                    reader = null
                    output = null
                    connected.set(false)
                }
            }
            runCatching { openedInput?.close() }
            runCatching { openedOutput?.close() }
            runCatching { s.close() }
            throw e
        }
    }

    fun isConnected(): Boolean = synchronized(connectionLock) {
        connected.get() && socket?.isConnected == true
    }

    @Synchronized
    @Throws(IOException::class)
    fun command(
        command: String,
        timeoutMs: Long = 6000,
        minimumGapMs: Long = 300,
        quietWindowMs: Long = 120,
        preDrainMs: Long = 80
    ): CommandResult {
        lateinit var activeInput: ArrivalInput
        lateinit var activeReader: ElmPromptReader
        lateinit var activeOutput: BufferedOutputStream
        synchronized(connectionLock) {
            check(connected.get() && socket?.isConnected == true) { "OBD device is not connected" }
            activeInput = input ?: error("OBD input stream is unavailable")
            activeReader = reader ?: error("OBD reader is unavailable")
            activeOutput = output ?: error("OBD output stream is unavailable")
        }
        val now = SystemClock.elapsedRealtime()
        val gap = now - lastCommandFinishedAtMs
        val gapWaitMs = (minimumGapMs - gap).coerceAtLeast(0L)
        if (gapWaitMs > 0L) Thread.sleep(gapWaitMs)

        // A normal runtime transaction is delimited by the ELM prompt. Never
        // guess that a timed drain recovered a missing boundary: once a command
        // was sent, prompt loss invalidates this socket and reconnect is required.
        if (lastCommandFinishedAtMs > 0L && !promptBoundarySynchronized) {
            throw IOException("ELM prompt boundary is unknown; reconnect required")
        }
        val effectivePreDrainMs = when {
            preDrainMs > 0L -> preDrainMs
            else -> 0L
        }
        try {
            if (effectivePreDrainMs > 0L) activeReader.drain(effectivePreDrainMs)
            else activeReader.discardBufferedPadding()
        } catch (failure: Exception) {
            invalidateBoundary()
            activeInput.close()
            throw failure
        }

        val clean = command.trim().uppercase()
        val startNs = System.nanoTime()
        try {
            activeOutput.write((clean + "\r").toByteArray(Charsets.US_ASCII))
            activeOutput.flush()
        } catch (failure: Exception) {
            invalidateBoundary()
            activeInput.close()
            throw failure
        }

        val reply = try {
            activeReader.receive(startNs, timeoutMs, quietWindowMs)
        } catch (failure: Exception) {
            invalidateBoundary()
            activeInput.close()
            throw failure
        }

        val lines = reply.text.split('\r', '\n').map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(clean, ignoreCase = true) }
        val hasHex = lines.any { line ->
            var digits = 0
            var valid = true
            for (ch in line) {
                if (ch == ' ') continue
                if (ch !in '0'..'9' && ch !in 'a'..'f' && ch !in 'A'..'F') { valid = false; break }
                digits++
            }
            valid && digits >= 2
        }
        val frames = ObdParsers.parseCanFrames(lines)
        val status = ElmResponseParser.status(clean, lines, frames, hasHex, reply.prompt)
        promptBoundarySynchronized = reply.prompt
        lastCommandFinishedAtMs = SystemClock.elapsedRealtime()
        if (!reply.prompt) { connected.set(false); activeInput.close() }
        return CommandResult(
            command = clean,
            rawLines = lines,
            status = status,
            promptSeen = reply.prompt,
            canFrames = frames
        )
    }

    fun initialize(shouldContinue: () -> Boolean = { true }): List<CommandResult> {
        val commands = listOf(
            InitCommand("ATZ", 10_000, 500),
            InitCommand("ATE0", 3000, 300),
            InitCommand("ATL0", 3000, 250),
            InitCommand("ATS0", 3000, 250),
            InitCommand("ATH1", 3000, 250),
            InitCommand("ATCAF1", 3000, 250),
            InitCommand("ATAT1", 3000, 250),
            InitCommand("ATAL", 3000, 250)
        )
        val results = ArrayList<CommandResult>(commands.size)
        for (spec in commands) {
            if (!shouldContinue()) break
            results += command(spec.command, spec.timeoutMs, spec.minimumGapMs)
        }
        return results
    }

    private fun invalidateBoundary() {
        promptBoundarySynchronized = false
        connected.set(false)
    }

    fun close() {
        val detached = synchronized(connectionLock) {
            connected.set(false)
            val handles = Triple(socket, input, output)
            socket = null
            input = null
            reader = null
            output = null
            promptBoundarySynchronized = false
            lastCommandFinishedAtMs = 0L
            handles
        }
        try { detached.first?.close() } catch (_: Exception) {}
        try { detached.second?.close() } catch (_: Exception) {}
        try { detached.third?.close() } catch (_: Exception) {}
    }

    private data class InitCommand(
        val command: String,
        val timeoutMs: Long,
        val minimumGapMs: Long
    )
}
