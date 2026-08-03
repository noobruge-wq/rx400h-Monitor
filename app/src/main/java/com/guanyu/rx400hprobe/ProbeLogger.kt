package com.guanyu.rx400hprobe

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.content.res.Configuration
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
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProbeLogger(private val context: Context) {
    private val root = File(context.getExternalFilesDir(null), "probe_sessions")
    private var sessionDir: File? = null
    private var rawWriter: BufferedWriter? = null
    private var eventWriter: BufferedWriter? = null
    private var frameWriter: BufferedWriter? = null
    private var connectionWriter: BufferedWriter? = null
    private var errorWriter: BufferedWriter? = null
    private var decodedWriter: BufferedWriter? = null
    private var protocolWriter: BufferedWriter? = null
    private var sessionStartedAtMs: Long = 0L
    private var transactionCount: Long = 0L
    private var frameCount: Long = 0L
    private var eventCount: Long = 0L
    private var errorCount: Long = 0L

    private data class Stat(var count: Long = 0, var ok: Long = 0, var totalLatency: Long = 0, var maxLatency: Long = 0)
    private val requestStats = ConcurrentHashMap<String, Stat>()

    var sessionId: String? = null
        private set

    @Synchronized
    fun start(adapterName: String, adapterAddress: String): File {
        stop()
        root.mkdirs()
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneOffset.UTC).format(Instant.now())
        val id = "RX400h_$stamp"
        val dir = File(root, id).apply { mkdirs() }
        sessionId = id
        sessionDir = dir
        sessionStartedAtMs = System.currentTimeMillis()
        transactionCount = 0
        frameCount = 0
        eventCount = 0
        errorCount = 0
        requestStats.clear()

        rawWriter = writer(dir, "raw_io.jsonl")
        decodedWriter = writer(dir, "decoded.jsonl")
        protocolWriter = writer(dir, "protocol_matrix.csv").also {
            it.write("requested_code,label,resolved_code,description,valid_0100,total_0100,valid_010c,total_010c,valid_0105,total_0105,valid_010d,total_010d,ecu_ids,valid_frames,no_data,bus_errors,avg_latency_ms,score\n")
        }
        connectionWriter = writer(dir, "connection.log")
        errorWriter = writer(dir, "errors.log")
        eventWriter = writer(dir, "events.csv").also {
            it.write("timestamp_ms,event_type,note\n")
        }
        frameWriter = writer(dir, "frames.csv").also {
            it.write("timestamp_ms,rpm_std,speed_kph_std,coolant_c_std,adapter_12v_v,temp1_c,temp2_c,temp3_c,temp4_c,temp5_c,temp6_c,room_candidate_c,temp_max_c,temp_min_c,temp_avg_c,temp_hot3_avg_c,temp_delta_c\n")
        }

        File(dir, "device.json").writeText(
            JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("device", Build.DEVICE)
                .put("android_release", Build.VERSION.RELEASE)
                .put("api_level", Build.VERSION.SDK_INT)
                .put("adapter_name", adapterName)
                .put("adapter_address", adapterAddress)
                .put("screen_width_px", context.resources.displayMetrics.widthPixels)
                .put("screen_height_px", context.resources.displayMetrics.heightPixels)
                .put("screen_density", context.resources.displayMetrics.density)
                .put("orientation", if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait")
                .toString(2)
        )
        writeSessionJson(status = "active", endedAt = null)
        logConnection("SESSION_START id=$id adapter=$adapterName address=$adapterAddress")
        return dir
    }

    private fun writer(dir: File, name: String): BufferedWriter = BufferedWriter(FileWriter(File(dir, name), true))

    @Synchronized
    fun logTransaction(header: String?, result: CommandResult, retryIndex: Int = 0) {
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
            .put("prompt_seen", result.promptSeen)
            .put("response_pending_seen", result.responsePendingSeen)
            .put("status", result.status.name)
            .put("retry_index", retryIndex)
        rawWriter?.apply { write(obj.toString()); newLine() }

        val key = "${header ?: "NONE"}:${result.command}"
        val stat = requestStats.getOrPut(key) { Stat() }
        stat.count++
        if (result.status == TransactionStatus.OK) stat.ok++
        stat.totalLatency += result.latencyMs
        if (result.latencyMs > stat.maxLatency) stat.maxLatency = result.latencyMs
        flushLightweight()
    }


    @Synchronized
    fun logProtocolAttempt(a: ProtocolAttempt) {
        protocolWriter?.apply {
            write(listOf(
                a.requestedCode, a.label, a.resolvedCode ?: "", a.description ?: "",
                a.valid0100, a.total0100, a.valid010C, a.total010C,
                a.valid0105, a.total0105, a.valid010D, a.total010D,
                a.ecuIds.joinToString(";"), a.validFrames, a.noData, a.busErrors,
                a.averageLatencyMs, a.score
            ).joinToString(",") { csv(it.toString()) })
            write("\n"); flush()
        }
    }

    @Synchronized
    fun logProtocolSummary(attempts: List<ProtocolAttempt>, best: ProtocolAttempt?) {
        val dir = sessionDir ?: return
        val arr = JSONArray()
        attempts.forEach { a ->
            arr.put(JSONObject()
                .put("requested_code", a.requestedCode)
                .put("label", a.label)
                .put("resolved_code", a.resolvedCode ?: JSONObject.NULL)
                .put("description", a.description ?: JSONObject.NULL)
                .put("valid_0100", a.valid0100)
                .put("total_0100", a.total0100)
                .put("valid_010c", a.valid010C)
                .put("valid_0105", a.valid0105)
                .put("valid_010d", a.valid010D)
                .put("ecu_ids", JSONArray(a.ecuIds.toList()))
                .put("valid_frames", a.validFrames)
                .put("no_data", a.noData)
                .put("bus_errors", a.busErrors)
                .put("avg_latency_ms", a.averageLatencyMs)
                .put("score", a.score))
        }
        File(dir, "protocol_summary.json").writeText(JSONObject()
            .put("best_requested_code", best?.requestedCode ?: JSONObject.NULL)
            .put("best_label", best?.label ?: JSONObject.NULL)
            .put("best_resolved_code", best?.resolvedCode ?: JSONObject.NULL)
            .put("attempts", arr).toString(2))
    }

    @Synchronized
    fun logDecoded(signal: String, value: Any?, unit: String?, sourceCommand: String, rawBytes: String, formulaVersion: String) {
        val obj = JSONObject()
            .put("wall_time_iso", Instant.now().toString())
            .put("signal", signal)
            .put("value", value ?: JSONObject.NULL)
            .put("unit", unit ?: JSONObject.NULL)
            .put("source_command", sourceCommand)
            .put("raw_bytes", rawBytes)
            .put("formula_version", formulaVersion)
        decodedWriter?.apply { write(obj.toString()); newLine() }
    }

    @Synchronized
    fun logConnection(message: String) {
        connectionWriter?.apply {
            write("${Instant.now()} $message\n")
            flush()
        }
    }

    @Synchronized
    fun logError(message: String, throwable: Throwable? = null) {
        errorCount++
        errorWriter?.apply {
            write("${Instant.now()} $message")
            throwable?.let { write(" | ${it::class.java.simpleName}: ${it.message}") }
            write("\n")
            flush()
        }
    }

    @Synchronized
    fun logEvent(type: String, note: String = "") {
        eventCount++
        eventWriter?.apply {
            write("${System.currentTimeMillis()},${csv(type)},${csv(note)}\n")
            flush()
        }
    }

    @Synchronized
    fun logFrame(data: BaselineData, temp: TempCandidate?) {
        frameCount++
        val t = temp?.values ?: emptyList()
        val row = listOf(
            System.currentTimeMillis(), data.rpm.value, data.speedKph.value, data.coolantC.value, data.adapterVoltageV.value,
            t.getOrNull(0), t.getOrNull(1), t.getOrNull(2), t.getOrNull(3), t.getOrNull(4), t.getOrNull(5), temp?.room,
            temp?.max, temp?.min, temp?.average, temp?.hottestThreeAverage, temp?.delta
        ).joinToString(",") { it?.toString() ?: "" }
        frameWriter?.apply { write(row); newLine() }
        flushLightweight()
    }

    fun currentSessionDir(): File? = sessionDir

    @Synchronized
    fun finalizeAndZip(): File {
        val dir = sessionDir ?: error("No active session")
        logConnection("SESSION_FINALIZE_REQUEST")
        closeWriters()
        writeRequestStats(dir)
        writeSessionJson(status = "completed", endedAt = Instant.now().toString())
        writeManifest(dir)

        val zip = File(root, "${dir.name}.zip")
        if (zip.exists()) zip.delete()
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            dir.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.relativeTo(dir).path }
                .forEach { file ->
                    val relative = file.relativeTo(dir).path.replace(File.separatorChar, '/')
                    zos.putNextEntry(ZipEntry(relative))
                    FileInputStream(file).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
        }
        return zip
    }

    @Synchronized
    fun stop() {
        closeWriters()
    }

    private fun closeWriters() {
        listOf(rawWriter, eventWriter, frameWriter, connectionWriter, errorWriter, decodedWriter, protocolWriter).forEach {
            try { it?.flush(); it?.close() } catch (_: Exception) {}
        }
        rawWriter = null
        eventWriter = null
        frameWriter = null
        connectionWriter = null
        errorWriter = null
        decodedWriter = null
        protocolWriter = null
    }

    private fun flushLightweight() {
        if (transactionCount % 10L == 0L || frameCount % 10L == 0L) {
            try { rawWriter?.flush() } catch (_: Exception) {}
            try { frameWriter?.flush() } catch (_: Exception) {}
            try { decodedWriter?.flush() } catch (_: Exception) {}
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
            .put("app_version", "0.1.6")
            .put("protocol_profile_version", "0.1.6")
            .put("decoder_version", "0.1.6")
            .put("transaction_count", transactionCount)
            .put("frame_count", frameCount)
            .put("event_count", eventCount)
            .put("error_count", errorCount)
        File(dir, "session.json").writeText(obj.toString(2))
    }

    private fun writeManifest(dir: File) {
        val files = JSONArray()
        dir.listFiles()?.filter { it.isFile && it.name != "manifest.json" }?.sortedBy { it.name }?.forEach { file ->
            files.put(
                JSONObject()
                    .put("name", file.name)
                    .put("size_bytes", file.length())
                    .put("sha256", sha256(file))
            )
        }
        File(dir, "manifest.json").writeText(
            JSONObject()
                .put("generated_at", Instant.now().toString())
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
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun csv(text: String): String = "\"${text.replace("\"", "\"\"")}\""
}
