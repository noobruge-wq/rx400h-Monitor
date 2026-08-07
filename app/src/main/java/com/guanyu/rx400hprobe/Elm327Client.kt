package com.guanyu.rx400hprobe

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class Elm327Client {
    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val TEXT_OK_COMMANDS = setOf("ATI", "STI", "AT@1", "ATDP", "ATDPN")
    }

    private var socket: BluetoothSocket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null
    private val connected = AtomicBoolean(false)
    private var lastCommandFinishedAtMs = 0L

    @Throws(IOException::class, SecurityException::class)
    fun connect(device: BluetoothDevice) {
        close()
        val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
        s.connect()
        socket = s
        input = BufferedInputStream(s.inputStream)
        output = BufferedOutputStream(s.outputStream)
        connected.set(true)
        drainInput(400)
    }

    fun isConnected(): Boolean = connected.get() && socket?.isConnected == true

    @Synchronized
    @Throws(IOException::class)
    fun command(
        command: String,
        timeoutMs: Long = 6000,
        minimumGapMs: Long = 300,
        quietWindowMs: Long = 120,
        preDrainMs: Long = 80
    ): CommandResult {
        check(isConnected()) { "OBD device is not connected" }
        val now = SystemClock.elapsedRealtime()
        val gap = now - lastCommandFinishedAtMs
        if (gap < minimumGapMs) Thread.sleep(minimumGapMs - gap)
        if (preDrainMs > 0L) drainInput(preDrainMs)

        val clean = command.trim().uppercase()
        val startNs = System.nanoTime()
        output!!.write((clean + "\r").toByteArray(Charsets.US_ASCII))
        output!!.flush()

        val response = StringBuilder()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var promptSeen = false
        var firstByteMs: Long? = null
        var promptMs: Long? = null

        while (SystemClock.elapsedRealtime() < deadline) {
            if (input!!.available() > 0) {
                val v = input!!.read()
                if (v < 0) break
                if (firstByteMs == null) firstByteMs = (System.nanoTime() - startNs) / 1_000_000
                val ch = v.toChar()
                if (ch == '>') {
                    promptSeen = true
                    promptMs = (System.nanoTime() - startNs) / 1_000_000
                    break
                }
                response.append(ch)
            } else {
                Thread.sleep(10)
            }
        }

        if (promptSeen && quietWindowMs > 0) {
            val quietEnd = SystemClock.elapsedRealtime() + quietWindowMs
            while (SystemClock.elapsedRealtime() < quietEnd) {
                if (input!!.available() > 0) {
                    val v = input!!.read()
                    if (v >= 0 && v.toChar() != '>') response.append(v.toChar())
                } else {
                    Thread.sleep(8)
                }
            }
        }

        val latency = (System.nanoTime() - startNs) / 1_000_000
        val lines = response.toString().replace("\u0000", "")
            .split('\r', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(clean, ignoreCase = true) }
        val joined = lines.joinToString(" ").uppercase()
        val normalizedHex = lines.map { it.replace(" ", "").uppercase() }
            .filter { it.length >= 2 && it.matches(Regex("[0-9A-F]+")) }
            .joinToString("")

        val service = requestService(clean)
        val canPayloads = canPayloadStarts(lines)
        val pendingSeen = service != null && canPayloads.any { payload ->
            payload.size >= 3 && payload[0] == 0x7F && payload[1] == service && payload[2] == 0x78
        }
        val negativeSeen = service != null && canPayloads.any { payload ->
            payload.size >= 3 && payload[0] == 0x7F && payload[1] == service
        }
        val positiveSeen = service != null && canPayloads.any { payload -> payload.firstOrNull() == positiveService(service) }

        val status = classify(clean, joined, normalizedHex, promptSeen, positiveSeen, negativeSeen, pendingSeen)
        lastCommandFinishedAtMs = SystemClock.elapsedRealtime()
        return CommandResult(
            command = clean,
            rawLines = lines,
            normalizedHex = normalizedHex,
            latencyMs = latency,
            status = status,
            promptSeen = promptSeen,
            responsePendingSeen = pendingSeen,
            firstByteLatencyMs = firstByteMs,
            promptLatencyMs = promptMs,
            quietWindowMs = quietWindowMs,
            preDrainMs = preDrainMs
        )
    }

    private fun classify(
        command: String,
        joined: String,
        hex: String,
        prompt: Boolean,
        positiveSeen: Boolean,
        negativeSeen: Boolean,
        pendingSeen: Boolean
    ): TransactionStatus = when {
        positiveSeen -> TransactionStatus.OK
        pendingSeen -> TransactionStatus.RESPONSE_PENDING
        negativeSeen -> TransactionStatus.NEGATIVE_RESPONSE
        !prompt -> TransactionStatus.TIMEOUT
        joined.contains("CAN ERROR") || joined.contains("BUS ERROR") || joined.contains("BUFFER FULL") -> TransactionStatus.BUS_ERROR
        joined.contains("STOPPED") -> TransactionStatus.INTERRUPTED
        joined.contains("NO DATA") || joined.contains("UNABLE TO CONNECT") -> TransactionStatus.NO_DATA
        joined.contains("?") -> TransactionStatus.COMMAND_ERROR
        joined.contains("SEARCHING") && hex.isBlank() -> TransactionStatus.IN_PROGRESS
        command in TEXT_OK_COMMANDS && joined.isNotBlank() -> TransactionStatus.OK
        hex.isNotBlank() || joined.contains("OK") || joined.contains("ELM") || joined.matches(Regex(".*\\d+\\.?\\d*V.*")) -> TransactionStatus.OK
        else -> TransactionStatus.UNKNOWN
    }

    fun initialize(): List<CommandResult> = listOf(
        command("ATZ", 10_000, 500),
        command("ATE0", 3000, 300),
        command("ATL0", 3000, 250),
        command("ATS0", 3000, 250),
        command("ATH1", 3000, 250),
        command("ATCAF1", 3000, 250),
        command("ATAT1", 3000, 250),
        command("ATAL", 3000, 250)
    )

    private fun requestService(command: String): Int? {
        if (command.startsWith("AT")) return null
        val compact = command.filterNot(Char::isWhitespace)
        return compact.take(2).toIntOrNull(16)
    }

    private fun positiveService(service: Int): Int = (service + 0x40) and 0xFF

    private fun canPayloadStarts(lines: List<String>): List<List<Int>> = lines.mapNotNull { line ->
        val compact = line.replace(Regex("\\s+"), "").uppercase()
        if (!compact.matches(Regex("[0-9A-F]{5,}"))) return@mapNotNull null
        if (compact.length < 5) return@mapNotNull null
        val body = compact.drop(3)
        if (body.length % 2 != 0) return@mapNotNull null
        val bytes = body.chunked(2).mapNotNull { it.toIntOrNull(16) }
        if (bytes.isEmpty()) return@mapNotNull null
        when (bytes[0] ushr 4) {
            0 -> {
                val len = bytes[0] and 0x0F
                if (bytes.size >= len + 1) bytes.subList(1, 1 + len) else null
            }
            1 -> if (bytes.size >= 3) bytes.drop(2) else null
            else -> null
        }
    }

    private fun drainInput(durationMs: Long) {
        val end = SystemClock.elapsedRealtime() + durationMs
        while (SystemClock.elapsedRealtime() < end) {
            try {
                if ((input?.available() ?: 0) > 0) input?.read() else Thread.sleep(5)
            } catch (_: Exception) {
                break
            }
        }
    }

    fun close() {
        connected.set(false)
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null
        output = null
        socket = null
    }
}
