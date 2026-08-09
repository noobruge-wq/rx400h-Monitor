package com.guanyu.rx400hprobe

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class SessionState { IDLE, ACTIVE, FINALIZING, FINALIZED, FINALIZE_FAILED }

class ProbeLogger(private val context: Context) {
    companion object {
        const val APP_VERSION = "0.3.0"
        const val PROFILE_VERSION = "rx400h_ha_hci_20260805_002"
        const val SCHEDULER_PROFILE = "v030_deadline_001"
    }

    private val root: File = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("probe_sessions")
    private var sessionDir: File? = null
    private var finalZip: File? = null
    private var rawWriter: BufferedWriter? = null
    private var eventWriter: BufferedWriter? = null
    private var frameWriter: BufferedWriter? = null
    private var connectionWriter: BufferedWriter? = null
    private var errorWriter: BufferedWriter? = null
    private var decodedWriter: BufferedWriter? = null
    private var performanceWriter: BufferedWriter? = null
    private var sessionStartedAtMs: Long = 0L
    private var transactionCount: Long = 0L
    private var frameCount: Long = 0L
    private var eventCount: Long = 0L
    private var errorCount: Long = 0L
    @Volatile
    private var loggerDegraded = false
    private val pendingErrors = ArrayDeque<String>(100)

    @Volatile
    var state: SessionState = SessionState.IDLE
        private set

    private data class Stat(var count: Long = 0, var ok: Long = 0, var totalLatency: Long = 0, var maxLatency: Long = 0)
    private val requestStats = linkedMapOf<String, Stat>()

    var sessionId: String? = null
        private set

    fun isDegraded(): Boolean = loggerDegraded

    @Synchronized
    fun start(adapterName: String, adapterAddress: String): File {
        closeWriters()
        if (!root.exists() && !root.mkdirs()) error("Cannot create log root: ${root.absolutePath}")
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC).format(Instant.now())
        val id = "RX400h_$stamp"
        val dir = File(root, id)
        if (!dir.exists() && !dir.mkdirs()) error("Cannot create session directory: ${dir.absolutePath}")

        sessionId = id
        sessionDir = dir
        finalZip = null
        sessionStartedAtMs = System.currentTimeMillis()
        transactionCount = 0
        frameCount = 0
        eventCount = 0
        errorCount = 0
        loggerDegraded = false
        pendingErrors.clear()
        requestStats.clear()
        state = SessionState.ACTIVE

        rawWriter = writer(dir, "raw_io.jsonl")
        decodedWriter = writer(dir, "decoded.jsonl")
        connectionWriter = writer(dir, "connection.log")
        errorWriter = writer(dir, "errors.log")
        eventWriter = writer(dir, "events.csv").also { it.write("timestamp_ms,event_type,note\n") }
        frameWriter = writer(dir, "frames.csv").also {
            it.write(
                "timestamp_ms,rpm,speed_kph,coolant_c,adapter_12v_v," +
                    "soc_pct,hv_voltage_v,hv_current_a,hv_power_kw,battery_temp_min_c,battery_temp_max_c,battery_temp_avg_c," +
                    "battery_temp_1_c,battery_temp_2_c,battery_temp_3_c,battery_temp_4_c,battery_temp_5_c,battery_temp_6_c,battery_temp_7_c,battery_temp_8_c," +
                    "ice_torque_nm,ice_power_kw,warmup_active,idle_check_active\n"
            )
        }
        performanceWriter = writer(dir, "performance.csv").also {
            it.write(
                "timestamp_iso,elapsed_ms,pss_kb,java_heap_used_kb,java_heap_total_kb," +
                    "cpu_delta_ms,alloc_delta,freed_delta,cycle_ms,render_ms,logger_write_ms," +
                    "request_hz,publish_hz,deadline_misses,skipped_overdue," +
                    "latency_p50_ms,latency_p95_ms,latency_p99_ms,no_data,timeout,bus_error\n"
            )
        }

        File(dir, "device.json").writeText(
            JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("device", Build.DEVICE)
                .put("android_release", Build.VERSION.RELEASE)
                .put("api_level", Build.VERSION.SDK_INT)
                .put("adapter_name", adapterName)
                .put("adapter_id_sha256", sha256Text(adapterAddress.lowercase()))
                .put("screen_width_px", context.resources.displayMetrics.widthPixels)
                .put("screen_height_px", context.resources.displayMetrics.heightPixels)
                .put("screen_density", context.resources.displayMetrics.density)
                .put("orientation", if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait")
                .toString(2)
        )
        writeSessionJson("active", null)
        logConnection("SESSION_START id=$id adapter=$adapterName profile=$PROFILE_VERSION scheduler=$SCHEDULER_PROFILE")
        return dir
    }

    private fun writer(dir: File, name: String): BufferedWriter = BufferedWriter(FileWriter(File(dir, name), true))
    private fun isWritable(): Boolean = state == SessionState.ACTIVE

    @Synchronized
    fun logTransaction(header: String?, result: CommandResult, retryIndex: Int = 0) {
        if (!isWritable()) return
        transactionCount++
        val obj = JSONObject()
            .put("session_id", sessionId)
            .put("monotonic_ns", SystemClock.elapsedRealtimeNanos())
            .put("wall_time_iso", Instant.now().toString())
            .put("tx_header", header ?: JSONObject.NULL)
            .put("command_sent", result.command)
            .put("raw_response_lines", JSONArray(result.rawLines))
            .put("normalized_hex", result.normalizedHex)
            .put("latency_ms", result.latencyMs)
            .put("first_byte_latency_ms", result.firstByteLatencyMs ?: JSONObject.NULL)
            .put("prompt_latency_ms", result.promptLatencyMs ?: JSONObject.NULL)
            .put("quiet_window_ms", result.quietWindowMs)
            .put("pre_drain_ms", result.preDrainMs)
            .put("prompt_seen", result.promptSeen)
            .put("response_pending_seen", result.responsePendingSeen)
            .put("status", result.status.name)
            .put("retry_index", retryIndex)
        safeWrite("raw_io.jsonl") { rawWriter?.apply { write(obj.toString()); newLine() } }

        val key = "${header ?: "NONE"}:${result.command}"
        val stat = requestStats.getOrPut(key) { Stat() }
        stat.count++
        if (result.status == TransactionStatus.OK) stat.ok++
        stat.totalLatency += result.latencyMs
        if (result.latencyMs > stat.maxLatency) stat.maxLatency = result.latencyMs
        flushLightweight()
    }

    @Synchronized
    fun logDecoded(signal: String, value: Any?, unit: String?, sourceCommand: String, rawBytes: String, formulaVersion: String) {
        if (!isWritable()) return
        val obj = JSONObject()
            .put("wall_time_iso", Instant.now().toString())
            .put("signal", signal)
            .put("value", when (value) {
                null -> JSONObject.NULL
                is Iterable<*> -> JSONArray(value.toList())
                else -> value
            })
            .put("unit", unit ?: JSONObject.NULL)
            .put("source_command", sourceCommand)
            .put("raw_bytes", rawBytes)
            .put("formula_version", formulaVersion)
        safeWrite("decoded.jsonl") { decodedWriter?.apply { write(obj.toString()); newLine() } }
    }

    @Synchronized
    fun logConnection(message: String) {
        if (!isWritable()) return
        safeWrite("connection.log") { connectionWriter?.apply { write("${Instant.now()} $message\n"); flush() } }
    }

    @Synchronized
    fun logError(message: String, throwable: Throwable? = null) {
        if (!isWritable()) return
        errorCount++
        val line = buildString {
            append(Instant.now()).append(' ').append(message)
            throwable?.let { append(" | ").append(it::class.java.simpleName).append(": ").append(it.message) }
        }
        safeWrite("errors.log") { errorWriter?.apply { write(line); write("\n"); flush() } }
    }

    @Synchronized
    fun logEvent(type: String, note: String = "") {
        if (!isWritable()) return
        eventCount++
        safeWrite("events.csv") { eventWriter?.apply { write("${System.currentTimeMillis()},${csv(type)},${csv(note)}\n"); flush() } }
    }

    @Synchronized
    fun logFrame(data: BaselineData, hybrid: HybridData) {
        if (!isWritable()) return
        frameCount++
        val temps = hybrid.batteryTempsC.value ?: emptyList()
        val icePower = icePowerKw(data.rpm.value, hybrid.iceTorqueNm.value)
        val row = listOf(
            System.currentTimeMillis(),
            data.rpm.value, data.speedKph.value, data.coolantC.value, data.adapterVoltageV.value,
            hybrid.socPct.value, hybrid.hvVoltageV.value, hybrid.hvCurrentA.value, hybrid.hvPowerKw.value,
            hybrid.batteryTempMinC.value, hybrid.batteryTempMaxC.value, hybrid.batteryTempAvgC.value,
            temps.getOrNull(0), temps.getOrNull(1), temps.getOrNull(2), temps.getOrNull(3),
            temps.getOrNull(4), temps.getOrNull(5), temps.getOrNull(6), temps.getOrNull(7),
            hybrid.iceTorqueNm.value, icePower, hybrid.warmupActive.value, hybrid.idleCheckActive.value
        ).joinToString(",") { it?.toString() ?: "" }
        safeWrite("frames.csv") { frameWriter?.apply { write(row); newLine() } }
        flushLightweight()
    }

    @Synchronized
    fun logPerformance(sample: PerformanceTracker.Sample) {
        if (!isWritable()) return
        safeWrite("performance.csv") { performanceWriter?.apply {
            write(
                "${sample.wallTimeIso},${sample.elapsedMs},${sample.pssKb},${sample.javaHeapUsedKb},${sample.javaHeapTotalKb}," +
                    "${sample.cpuDeltaMs},${sample.allocDelta},${sample.freedDelta},${sample.cycleMs},${sample.renderMs},${sample.loggerWriteMs}," +
                    "${sample.requestHz},${sample.publishHz},${sample.deadlineMisses},${sample.skippedOverdue}," +
                    "${sample.latencyP50Ms},${sample.latencyP95Ms},${sample.latencyP99Ms},${sample.noData},${sample.timeout},${sample.busError}\n"
            )
            flush()
        } }
    }

    fun currentSessionDir(): File? = sessionDir

    @Synchronized
    fun finalizeAndZip(): File {
        finalZip?.takeIf { state == SessionState.FINALIZED && it.isFile }?.let { return it }
        if (state != SessionState.ACTIVE) error("Session cannot be finalized from state $state")
        val dir = sessionDir ?: error("No active session")
        safeWrite("connection.log") { connectionWriter?.apply { write("${Instant.now()} SESSION_FINALIZE_REQUEST\n"); flush() } }
        state = SessionState.FINALIZING
        closeWriters()

        val zip = File(root, "${dir.name}.zip")
        val tmp = File(root, "${dir.name}.zip.tmp")
        try {
            writePendingErrors(dir)
            writeRequestStats(dir)
            writeSessionJson("completed", Instant.now().toString())
            writeManifest(dir)
            if (tmp.exists()) tmp.delete()
            ZipOutputStream(FileOutputStream(tmp)).use { zos ->
                dir.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(dir).path }.forEach { file ->
                    val relative = file.relativeTo(dir).path.replace(File.separatorChar, '/')
                    zos.putNextEntry(ZipEntry(relative))
                    FileInputStream(file).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            if (tmp.length() <= 0L) error("Generated ZIP is empty")
            if (zip.exists() && !zip.delete()) error("Cannot replace old ZIP: ${zip.absolutePath}")
            if (!tmp.renameTo(zip)) {
                FileInputStream(tmp).use { input -> FileOutputStream(zip).use { output -> input.copyTo(output) } }
                if (!tmp.delete()) tmp.deleteOnExit()
            }
            finalZip = zip
            state = SessionState.FINALIZED
            return zip
        } catch (e: Exception) {
            state = SessionState.FINALIZE_FAILED
            val failure = "${Instant.now()} FINALIZE_FAILED ${e::class.java.simpleName}: ${e.message}\n"
            try { File(dir, "finalize_failure.txt").appendText(failure) } catch (_: Exception) {}
            try { File(context.filesDir, "rx400h_probe_fallback_error.log").appendText(failure) } catch (_: Exception) {}
            throw e
        }
    }

    @Synchronized
    fun stop() {
        closeWriters()
        if (state == SessionState.ACTIVE) state = SessionState.IDLE
    }

    private fun closeWriters() {
        listOf(rawWriter, eventWriter, frameWriter, connectionWriter, errorWriter, decodedWriter, performanceWriter).forEach {
            try { it?.flush(); it?.close() } catch (_: Exception) {}
        }
        rawWriter = null
        eventWriter = null
        frameWriter = null
        connectionWriter = null
        errorWriter = null
        decodedWriter = null
        performanceWriter = null
    }

    private fun safeWrite(target: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            markDegraded("write failed target=$target ${e::class.java.simpleName}: ${e.message}")
        }
    }

    private fun markDegraded(message: String) {
        loggerDegraded = true
        if (pendingErrors.size >= 100) pendingErrors.removeFirst()
        val line = "${Instant.now()} $message"
        pendingErrors.addLast(line)
        try { File(context.filesDir, "rx400h_probe_fallback_error.log").appendText("$line\n") } catch (_: Exception) {}
    }

    private fun writePendingErrors(dir: File) {
        if (pendingErrors.isEmpty()) return
        try { File(dir, "errors.log").appendText(pendingErrors.joinToString("\n", postfix = "\n")) } catch (_: Exception) {}
    }

    private fun flushLightweight() {
        if (transactionCount % 10L == 0L || frameCount % 10L == 0L) {
            try { rawWriter?.flush() } catch (e: Exception) { markDegraded("raw flush failed: ${e.message}") }
            try { frameWriter?.flush() } catch (e: Exception) { markDegraded("frame flush failed: ${e.message}") }
            try { decodedWriter?.flush() } catch (e: Exception) { markDegraded("decoded flush failed: ${e.message}") }
        }
    }

    private fun writeRequestStats(dir: File) {
        File(dir, "request_stats.csv").bufferedWriter().use { out ->
            out.write("key,count,ok,success_rate,avg_latency_ms,max_latency_ms\n")
            requestStats.toSortedMap().forEach { (key, s) ->
                val successRate = if (s.count == 0L) 0.0 else s.ok.toDouble() / s.count
                val avg = if (s.count == 0L) 0.0 else s.totalLatency.toDouble() / s.count
                out.write("${csv(key)},${s.count},${s.ok},$successRate,$avg,${s.maxLatency}\n")
            }
        }
    }

    private fun writeSessionJson(status: String, endedAt: String?) {
        val dir = sessionDir ?: return
        val obj = JSONObject()
            .put("session_id", sessionId)
            .put("created_at", Instant.ofEpochMilli(sessionStartedAtMs).toString())
            .put("ended_at", endedAt ?: JSONObject.NULL)
            .put("status", status)
            .put("app_version", APP_VERSION)
            .put("protocol_profile_version", PROFILE_VERSION)
            .put("decoder_version", ObdParsers.DECODER_VERSION)
            .put("scheduler_profile", SCHEDULER_PROFILE)
            .put("transaction_count", transactionCount)
            .put("frame_count", frameCount)
            .put("event_count", eventCount)
            .put("error_count", errorCount)
            .put("logger_degraded", loggerDegraded)
            .put("evidence_complete", !loggerDegraded)
        File(dir, "session.json").writeText(obj.toString(2))
    }

    private fun writeManifest(dir: File) {
        val files = JSONArray()
        dir.listFiles()?.filter { it.isFile && it.name != "manifest.json" }?.sortedBy { it.name }?.forEach { file ->
            files.put(JSONObject().put("name", file.name).put("size_bytes", file.length()).put("sha256", sha256(file)))
        }
        File(dir, "manifest.json").writeText(
            JSONObject()
                .put("generated_at", Instant.now().toString())
                .put("profile_version", PROFILE_VERSION)
                .put("decoder_version", ObdParsers.DECODER_VERSION)
                .put("scheduler_profile", SCHEDULER_PROFILE)
                .put("evidence_complete", !loggerDegraded)
                .put("files", files)
                .toString(2)
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun sha256Text(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun icePowerKw(rpm: Double?, torqueNm: Double?): Double? {
        if (rpm == null || torqueNm == null) return null
        return torqueNm * 2.0 * Math.PI * rpm / 60.0 / 1000.0
    }

    private fun csv(text: String): String = "\"${text.replace("\"", "\"\"")}\""
}
