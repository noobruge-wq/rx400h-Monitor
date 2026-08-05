package com.guanyu.rx400hprobe

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class Elm327Client {
    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null
    private val connected = AtomicBoolean(false)
    private var lastCommandFinishedAt = 0L

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
    fun command(command: String, timeoutMs: Long = 6000, minimumGapMs: Long = 300, quietWindowMs: Long = 120): CommandResult {
        check(isConnected()) { "OBD device is not connected" }
        val gap = System.currentTimeMillis() - lastCommandFinishedAt
        if (gap < minimumGapMs) Thread.sleep(minimumGapMs - gap)
        drainInput(80)

        val clean = command.trim().uppercase()
        val startNs = System.nanoTime()
        output!!.write((clean + "\r").toByteArray(Charsets.US_ASCII))
        output!!.flush()

        val response = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        var promptSeen = false
        var pendingSeen = false
        var firstByteMs: Long? = null
        var promptMs: Long? = null

        while (System.currentTimeMillis() < deadline) {
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
                if (response.toString().replace(" ", "").uppercase().contains(Regex("7F[0-9A-F]{2}78"))) pendingSeen = true
            } else Thread.sleep(10)
        }

        if (promptSeen && quietWindowMs > 0) {
            val quietEnd = System.currentTimeMillis() + quietWindowMs
            while (System.currentTimeMillis() < quietEnd) {
                if (input!!.available() > 0) {
                    val v = input!!.read()
                    if (v >= 0 && v.toChar() != '>') response.append(v.toChar())
                } else Thread.sleep(8)
            }
        }

        val latency = (System.nanoTime() - startNs) / 1_000_000
        val lines = response.toString().replace("\u0000", "")
            .split('\r', '\n').map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(clean, ignoreCase = true) }
        val joined = lines.joinToString(" ").uppercase()
        val normalizedHex = lines.map { it.replace(" ", "").uppercase() }
            .filter { it.length >= 2 && it.matches(Regex("[0-9A-F]+")) }
            .joinToString("")

        val status = classify(joined, normalizedHex, promptSeen, pendingSeen)
        lastCommandFinishedAt = System.currentTimeMillis()
        return CommandResult(clean, lines, normalizedHex, latency, status, promptSeen, pendingSeen, firstByteMs, promptMs, quietWindowMs)
    }

    private fun classify(joined: String, hex: String, prompt: Boolean, pending: Boolean): TransactionStatus = when {
        // Valid payload always outranks intermediate text such as SEARCHING...
        hex.contains(Regex("(?:41|42|43|44|49|61|62|6C)[0-9A-F]{2,}")) -> TransactionStatus.OK
        hex.contains(Regex("7F[0-9A-F]{2}78")) && pending -> TransactionStatus.RESPONSE_PENDING
        hex.contains(Regex("7F[0-9A-F]{4}")) -> TransactionStatus.NEGATIVE_RESPONSE
        !prompt -> TransactionStatus.TIMEOUT
        joined.contains("CAN ERROR") || joined.contains("BUS ERROR") || joined.contains("BUFFER FULL") -> TransactionStatus.BUS_ERROR
        joined.contains("STOPPED") -> TransactionStatus.INTERRUPTED
        joined.contains("NO DATA") || joined.contains("UNABLE TO CONNECT") -> TransactionStatus.NO_DATA
        joined.contains("?") -> TransactionStatus.COMMAND_ERROR
        joined.contains("SEARCHING") && hex.isBlank() -> TransactionStatus.IN_PROGRESS
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

    private fun drainInput(durationMs: Long) {
        val end = System.currentTimeMillis() + durationMs
        while (System.currentTimeMillis() < end) {
            try { if ((input?.available() ?: 0) > 0) input?.read() else Thread.sleep(5) } catch (_: Exception) { break }
        }
    }

    fun close() {
        connected.set(false)
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null; output = null; socket = null
    }
}
