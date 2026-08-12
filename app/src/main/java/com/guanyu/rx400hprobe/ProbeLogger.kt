package com.guanyu.rx400hprobe

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.CRC32

internal enum class SessionState { IDLE, ACTIVE, FINALIZING, FINALIZED, FINALIZE_FAILED }

internal data class PublicLogCommit(
    val result: PublicLogResult,
    val receiptWritten: Boolean
)

/**
 * Streaming evidence logger with bounded V0.3.1 durability checkpoints.
 *
 * Live files stay in the permission-free app working directory. Public export
 * happens only after an immutable ZIP has been built and validated.
 */
internal class ProbeLogger(private val context: Context) {
    companion object {
        /** Semantic app version; VERSION_NAME separately records the debug/release variant suffix. */
        val APP_VERSION: String = BuildConfig.APP_VERSION_NAME
        const val PROFILE_VERSION = "rx400h_ha_hci_20260805_002"
        const val SCHEDULER_PROFILE = "v030_capacity_002"
        private const val BULK_FLUSH_INTERVAL_MS = 2_000L
        private const val DURABLE_SYNC_INTERVAL_MS = 10_000L
        private const val DURABLE_RETRY_INTERVAL_MS = 1_000L
        private const val PUBLICATION_MARKER = "public_export.json"
        private val REQUIRED_EVIDENCE_FILES = setOf(
            "connection.log",
            "decoded.jsonl",
            "device.json",
            "errors.log",
            "events.csv",
            "frames.csv",
            "performance.csv",
            "raw_io.jsonl",
            "request_stats.csv",
            "session.json"
        )
        private val CAPACITY_SCHEDULER_EVIDENCE_FILES = setOf(
            "scheduler_events.jsonl",
            "scheduler_request_stats.csv"
        )
        private val RECORD_TIME_FILES = setOf(
            "connection.log",
            "decoded.jsonl",
            "errors.log",
            "events.csv",
            "frames.csv",
            "performance.csv",
            "raw_io.jsonl",
            "scheduler_events.jsonl",
            "scheduler_request_stats.csv"
        )

        /**
         * Activity recreation can briefly leave an old and a new ProbeLogger in the same process.
         * Hold one permit for the lifetime of an active session so a replacement Activity cannot
         * recover or package files until the previous writers have been durably closed.
         */
        private val PROCESS_SESSION_IO_GATE = Semaphore(1, true)
        private val PROCESS_PUBLICATION_GATE = Semaphore(1, true)
    }

    private val root: File = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("probe_sessions")
    private var sessionDir: File? = null
    private var finalZip: File? = null
    private var finalArchiveName: String? = null
    private var finalEndedAt: Instant? = null
    private var finalCompletionKind: LogCompletionKind? = null

    private var rawWriter: DurableWriter? = null
    private var eventWriter: DurableWriter? = null
    private var frameWriter: DurableWriter? = null
    private var connectionWriter: DurableWriter? = null
    private var errorWriter: DurableWriter? = null
    private var decodedWriter: DurableWriter? = null
    private var performanceWriter: DurableWriter? = null
    private var schedulerEventWriter: DurableWriter? = null
    private var schedulerRequestStatsWriter: DurableWriter? = null
    private val checkpointExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "rx400h-log-checkpoint").apply { isDaemon = true }
    }
    private var checkpointTask: ScheduledFuture<*>? = null

    private var sessionStartedAtMs: Long = 0L
    private var sessionTimeZoneId: String = ZoneId.systemDefault().id
    private var lastRecordAtMs: Long = 0L
    private var lastDurableRecordAtMs: Long = 0L
    private var lastFlushElapsedMs: Long = 0L
    private var lastSyncElapsedMs: Long = 0L
    private var transactionCount: Long = 0L
    private var frameCount: Long = 0L
    private var eventCount: Long = 0L
    private var schedulerEventCount: Long = 0L
    private var schedulerRequestStatsCount: Long = 0L
    private var errorCount: Long = 0L
    private var loggerErrorCount: Long = 0L
    private var apkSha256: String = "unavailable"
    private var signingCertificateSha256: String = "unavailable"
    private var writeTimingNs: Long = 0L
    private var checkpointTimingNs: Long = 0L
    private var syncTimingNs: Long = 0L
    private var checkpointMaxNs: Long = 0L
    private var checkpointLockWaitNs: Long = 0L
    private var ownsProcessSessionGate = false
    private val shutdownRequested = AtomicBoolean(false)
    private var schedulerAdmissionState: String = "UNKNOWN"
    private var schedulerRunMode: String = "NOT_STARTED"
    private var schedulerCostModelId: String? = null
    private var schedulerCostSource: String? = null
    private var schedulerRequestUtilization: Double? = null
    private var schedulerProjectedUtilization: Double? = null
    private var schedulerProjectedMisses: Long? = null
    private var schedulerProjectedCapacityRejections: Long? = null

    @Volatile
    private var loggerDegraded = false
    private val pendingErrors = ArrayDeque<String>(100)

    @Volatile
    var state: SessionState = SessionState.IDLE
        private set

    private data class Stat(
        var count: Long = 0,
        var ok: Long = 0,
        var totalLatency: Long = 0,
        var maxLatency: Long = 0
    )

    private data class RecoveryEnd(val instant: Instant, val basis: String)
    private data class ManifestRecord(val size: Long, val sha256: String)
    private data class IntegrityCheck(val valid: Boolean, val reason: String?)
    private data class ZipFingerprint(val size: Long, val crc32: Long, val sha256: String)

    private val requestStats = linkedMapOf<String, Stat>()

    var sessionId: String? = null
        private set

    fun isDegraded(): Boolean = loggerDegraded

    @Synchronized
    fun start(adapterName: String, adapterAddress: String): File {
        check(!shutdownRequested.get()) { "Logger shutdown was already requested" }
        check(state != SessionState.ACTIVE && state != SessionState.FINALIZING) {
            "Cannot start a second logger session from $state"
        }
        acquireProcessSessionGate()
        return try {
            val dir = startWithProcessGate(adapterName, adapterAddress)
            if (shutdownRequested.get()) {
                stopInterruptedWithProcessGate("LOGGER_SHUTDOWN_DURING_START")
                error("Logger shutdown was requested while the session was starting")
            }
            dir
        } catch (failure: Throwable) {
            stopCheckpointTimer()
            runCatching { closeWriters(durable = true) }
            state = SessionState.IDLE
            releaseProcessSessionGate()
            throw failure
        }
    }

    private fun startWithProcessGate(adapterName: String, adapterAddress: String): File {
        check(state != SessionState.ACTIVE && state != SessionState.FINALIZING) {
            "Cannot start a second logger session from $state"
        }
        closeWriters(durable = true)
        ensureRoot()
        val startedAt = Instant.now()
        val baseId = LogArchiveNaming.sessionId(startedAt)
        var id = baseId
        var suffix = 2
        var dir = File(root, id)
        while (dir.exists()) {
            id = "${baseId}_$suffix"
            suffix++
            dir = File(root, id)
        }
        if (!dir.mkdirs()) error("Cannot create session directory: ${dir.absolutePath}")

        sessionId = id
        sessionDir = dir
        finalZip = null
        finalArchiveName = null
        finalEndedAt = null
        finalCompletionKind = null
        sessionStartedAtMs = startedAt.toEpochMilli()
        sessionTimeZoneId = ZoneId.systemDefault().id
        lastRecordAtMs = sessionStartedAtMs
        lastDurableRecordAtMs = sessionStartedAtMs
        lastFlushElapsedMs = SystemClock.elapsedRealtime()
        lastSyncElapsedMs = lastFlushElapsedMs
        transactionCount = 0
        frameCount = 0
        eventCount = 0
        schedulerEventCount = 0
        schedulerRequestStatsCount = 0
        errorCount = 0
        loggerErrorCount = 0
        writeTimingNs = 0L
        checkpointTimingNs = 0L
        syncTimingNs = 0L
        checkpointMaxNs = 0L
        checkpointLockWaitNs = 0L
        loggerDegraded = false
        pendingErrors.clear()
        requestStats.clear()
        schedulerAdmissionState = "UNKNOWN"
        schedulerRunMode = "NOT_STARTED"
        schedulerCostModelId = null
        schedulerCostSource = null
        schedulerRequestUtilization = null
        schedulerProjectedUtilization = null
        schedulerProjectedMisses = null
        schedulerProjectedCapacityRejections = null
        apkSha256 = runCatching { sha256(File(context.applicationInfo.sourceDir)) }.getOrDefault("unavailable")
        signingCertificateSha256 = signingCertificateSha256()
        state = SessionState.ACTIVE

        rawWriter = writer(dir, "raw_io.jsonl")
        decodedWriter = writer(dir, "decoded.jsonl")
        connectionWriter = writer(dir, "connection.log")
        errorWriter = writer(dir, "errors.log")
        eventWriter = writer(dir, "events.csv").also {
            it.write("timestamp_ms,event_type,note\n")
        }
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
                    "cpu_delta_ms,alloc_delta,freed_delta,cycle_ms,render_ms,frame_log_ms," +
                    "request_hz,signal_update_hz,deadline_misses,skipped_overdue," +
                    "expired_unexecuted,capacity_rejections,transport_unavailable,executed_late,pending," +
                    "header_switches,admission_state," +
                    "latency_p50_ms,latency_p95_ms,latency_p99_ms,no_data,timeout,bus_error," +
                    "logger_stream_write_total_ms,logger_checkpoint_total_ms,logger_sync_total_ms," +
                    "logger_checkpoint_max_ms,logger_checkpoint_lock_wait_ms\n"
            )
        }
        schedulerEventWriter = writer(dir, "scheduler_events.jsonl")
        schedulerRequestStatsWriter = writer(dir, "scheduler_request_stats.csv").also {
            it.write(
                "timestamp_iso,monotonic_ns,elapsed_ms,request_id,header,target_period_ms,released," +
                    "executed_on_time,executed_late,capacity_rejected,expired_unexecuted," +
                    "transport_unavailable,session_ended,pending,in_flight," +
                    "queue_wait_p50_ms,queue_wait_p95_ms,queue_wait_max_ms," +
                    "setup_p50_ms,setup_p95_ms,setup_max_ms,service_p50_ms,service_p95_ms,service_max_ms," +
                    "interval_p50_ms,interval_p95_ms,interval_max_ms," +
                    "lateness_p50_ms,lateness_p95_ms,lateness_max_ms,header_switches,effective_hz,admission_state\n"
            )
        }

        atomicWriteText(
            File(dir, "device.json"),
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
                .put(
                    "orientation",
                    if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        "landscape"
                    } else {
                        "portrait"
                    }
                )
                .toString(2)
        )
        writeSessionJson(
            status = "active",
            endedAt = null,
            endTimeBasis = null,
            reason = "SESSION_START",
            evidenceComplete = false,
            archiveName = null
        )
        logConnection("SESSION_START id=$id adapter=$adapterName profile=$PROFILE_VERSION scheduler=$SCHEDULER_PROFILE")
        forceCheckpoint("SESSION_START_READY")
        startCheckpointTimer()
        return dir
    }

    @Synchronized
    fun logTransaction(header: String?, result: CommandResult, retryIndex: Int = 0) {
        if (!isWritable()) return
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
            .put("minimum_gap_ms", result.minimumGapMs)
            .put("gap_wait_ms", result.gapWaitMs)
            .put("quiet_window_ms", result.quietWindowMs)
            .put("pre_drain_ms", result.preDrainMs)
            .put("prompt_seen", result.promptSeen)
            .put("response_pending_seen", result.responsePendingSeen)
            .put("status", result.status.name)
            .put("retry_index", retryIndex)
        val written = safeWrite("raw_io.jsonl") { rawWriter?.apply { write(obj.toString()); newLine() } }
        if (!written) return
        transactionCount++
        touchRecord()

        val key = "${header ?: "NONE"}:${result.command}"
        val stat = requestStats.getOrPut(key) { Stat() }
        stat.count++
        if (result.status == TransactionStatus.OK) stat.ok++
        stat.totalLatency += result.latencyMs
        if (result.latencyMs > stat.maxLatency) stat.maxLatency = result.latencyMs
    }

    @Synchronized
    fun logDecoded(
        signal: String,
        value: Any?,
        unit: String?,
        sourceCommand: String,
        rawBytes: String,
        formulaVersion: String
    ) {
        if (!isWritable()) return
        val obj = JSONObject()
            .put("wall_time_iso", Instant.now().toString())
            .put("signal", signal)
            .put(
                "value",
                when (value) {
                    null -> JSONObject.NULL
                    is Iterable<*> -> JSONArray(value.toList())
                    else -> value
                }
            )
            .put("unit", unit ?: JSONObject.NULL)
            .put("source_command", sourceCommand)
            .put("raw_bytes", rawBytes)
            .put("formula_version", formulaVersion)
        if (safeWrite("decoded.jsonl") { decodedWriter?.apply { write(obj.toString()); newLine() } }) {
            touchRecord()
        }
    }

    @Synchronized
    fun logConnection(message: String) {
        if (!isWritable()) return
        if (safeWrite("connection.log") {
            connectionWriter?.apply { write("${Instant.now()} $message\n") }
        }) {
            touchRecord()
        }
    }

    @Synchronized
    fun logError(message: String, throwable: Throwable? = null) {
        if (!isWritable()) return
        val line = buildString {
            append(Instant.now()).append(' ').append(message)
            throwable?.let { append(" | ").append(it::class.java.simpleName).append(": ").append(it.message) }
        }
        if (safeWrite("errors.log") { errorWriter?.apply { write(line); write("\n") } }) {
            errorCount++
            touchRecord()
        }
        checkpointIfDue(forceDurable = true, reason = "ERROR")
    }

    @Synchronized
    fun logEvent(type: String, note: String = "") {
        if (!isWritable()) return
        if (safeWrite("events.csv") {
            eventWriter?.apply { write("${System.currentTimeMillis()},${csv(type)},${csv(note)}\n") }
        }) {
            eventCount++
            touchRecord()
        }
        checkpointIfDue(forceDurable = true, reason = "EVENT_$type")
    }

    /** Posts lifecycle bookkeeping without making the Android main thread wait for fsync. */
    fun logEventAsync(type: String, note: String = "") {
        runCatching { checkpointExecutor.execute { logEvent(type, note) } }
    }

    @Synchronized
    fun logFrame(data: BaselineData, hybrid: HybridData) {
        if (!isWritable()) return
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
        if (safeWrite("frames.csv") { frameWriter?.apply { write(row); newLine() } }) {
            frameCount++
            touchRecord()
        }
    }

    @Synchronized
    fun logPerformance(sample: PerformanceTracker.Sample) {
        if (!isWritable()) return
        if (safeWrite("performance.csv") {
            performanceWriter?.apply {
                write(
                    "${sample.wallTimeIso},${sample.elapsedMs},${sample.pssKb},${sample.javaHeapUsedKb},${sample.javaHeapTotalKb}," +
                        "${sample.cpuDeltaMs},${sample.allocDelta},${sample.freedDelta},${sample.cycleMs},${sample.renderMs},${sample.frameLogMs}," +
                        "${sample.requestHz},${sample.signalUpdateHz},${sample.deadlineMisses},${sample.skippedOverdue}," +
                        "${sample.expiredUnexecuted},${sample.capacityRejections},${sample.transportUnavailable}," +
                        "${sample.executedLate},${sample.pending},${sample.headerSwitches},${csv(sample.admissionState)}," +
                        "${sample.latencyP50Ms},${sample.latencyP95Ms},${sample.latencyP99Ms},${sample.noData},${sample.timeout},${sample.busError}," +
                        "${sample.loggerWriteTotalMs},${sample.loggerCheckpointTotalMs},${sample.loggerSyncTotalMs}," +
                        "${sample.loggerCheckpointMaxMs},${sample.loggerCheckpointLockWaitMs}\n"
                )
            }
        }) {
            touchRecord()
        }
    }

    /**
     * Writes one scheduler transition/release/outcome record. Callers should use named
     * arguments: nullable timing/cost fields stay explicit JSON nulls instead of being guessed.
     */
    @Synchronized
    fun logSchedulerEvent(
        eventType: String,
        requestId: String? = null,
        requestHeader: String? = null,
        releaseSequence: Long? = null,
        releaseCount: Long? = null,
        outcome: String? = null,
        reason: String? = null,
        releaseAtMs: Long? = null,
        deadlineAtMs: Long? = null,
        dispatchAtMs: Long? = null,
        completedAtMs: Long? = null,
        queueWaitMs: Long? = null,
        latenessMs: Long? = null,
        predictedSetupMs: Long? = null,
        predictedServiceMs: Long? = null,
        actualSetupMs: Long? = null,
        actualServiceMs: Long? = null,
        fromHeader: String? = null,
        toHeader: String? = null,
        admissionState: String? = null
    ) {
        if (!isWritable()) return
        val eventSequence = schedulerEventCount + 1L
        val obj = JSONObject()
            .put("session_id", sessionId)
            .put("event_sequence", eventSequence)
            .put("monotonic_ns", SystemClock.elapsedRealtimeNanos())
            .put("wall_time_iso", Instant.now().toString())
            .put("event_type", eventType)
            .put("request_id", requestId ?: JSONObject.NULL)
            .put("request_header", requestHeader ?: JSONObject.NULL)
            .put("release_sequence", releaseSequence ?: JSONObject.NULL)
            .put("release_count", releaseCount ?: JSONObject.NULL)
            .put("outcome", outcome ?: JSONObject.NULL)
            .put("reason", reason ?: JSONObject.NULL)
            .put("release_at_ms", releaseAtMs ?: JSONObject.NULL)
            .put("deadline_at_ms", deadlineAtMs ?: JSONObject.NULL)
            .put("dispatch_at_ms", dispatchAtMs ?: JSONObject.NULL)
            .put("completed_at_ms", completedAtMs ?: JSONObject.NULL)
            .put("queue_wait_ms", queueWaitMs ?: JSONObject.NULL)
            .put("lateness_ms", latenessMs ?: JSONObject.NULL)
            .put("predicted_setup_ms", predictedSetupMs ?: JSONObject.NULL)
            .put("predicted_service_ms", predictedServiceMs ?: JSONObject.NULL)
            .put("actual_setup_ms", actualSetupMs ?: JSONObject.NULL)
            .put("actual_service_ms", actualServiceMs ?: JSONObject.NULL)
            .put("from_header", fromHeader ?: JSONObject.NULL)
            .put("to_header", toHeader ?: JSONObject.NULL)
            .put("admission_state", admissionState ?: schedulerAdmissionState)
        if (safeWrite("scheduler_events.jsonl") {
                schedulerEventWriter?.apply { write(obj.toString()); newLine() }
            }
        ) {
            schedulerEventCount = eventSequence
            touchRecord()
        }
    }

    /** Streams a bounded per-request scheduler snapshot; percentile fields may be unavailable. */
    @Suppress("LongParameterList")
    @Synchronized
    fun logSchedulerRequestStats(
        elapsedMs: Long,
        requestId: String,
        header: String?,
        targetPeriodMs: Long,
        released: Long,
        executedOnTime: Long,
        executedLate: Long,
        capacityRejected: Long,
        expiredUnexecuted: Long,
        transportUnavailable: Long,
        sessionEnded: Long,
        pending: Long,
        inFlight: Long,
        queueWaitP50Ms: Long? = null,
        queueWaitP95Ms: Long? = null,
        queueWaitMaxMs: Long? = null,
        setupP50Ms: Long? = null,
        setupP95Ms: Long? = null,
        setupMaxMs: Long? = null,
        serviceP50Ms: Long? = null,
        serviceP95Ms: Long? = null,
        serviceMaxMs: Long? = null,
        intervalP50Ms: Long? = null,
        intervalP95Ms: Long? = null,
        intervalMaxMs: Long? = null,
        latenessP50Ms: Long? = null,
        latenessP95Ms: Long? = null,
        latenessMaxMs: Long? = null,
        headerSwitches: Long,
        effectiveHz: Double,
        admissionState: String = schedulerAdmissionState
    ) {
        if (!isWritable()) return
        val row = listOf(
            csv(Instant.now().toString()),
            SystemClock.elapsedRealtimeNanos().toString(),
            elapsedMs.toString(),
            csv(requestId),
            header?.let(::csv).orEmpty(),
            targetPeriodMs.toString(),
            released.toString(),
            executedOnTime.toString(),
            executedLate.toString(),
            capacityRejected.toString(),
            expiredUnexecuted.toString(),
            transportUnavailable.toString(),
            sessionEnded.toString(),
            pending.toString(),
            inFlight.toString(),
            csvNumber(queueWaitP50Ms),
            csvNumber(queueWaitP95Ms),
            csvNumber(queueWaitMaxMs),
            csvNumber(setupP50Ms),
            csvNumber(setupP95Ms),
            csvNumber(setupMaxMs),
            csvNumber(serviceP50Ms),
            csvNumber(serviceP95Ms),
            csvNumber(serviceMaxMs),
            csvNumber(intervalP50Ms),
            csvNumber(intervalP95Ms),
            csvNumber(intervalMaxMs),
            csvNumber(latenessP50Ms),
            csvNumber(latenessP95Ms),
            csvNumber(latenessMaxMs),
            headerSwitches.toString(),
            effectiveHz.toString(),
            csv(admissionState)
        ).joinToString(",", postfix = "\n")
        if (safeWrite("scheduler_request_stats.csv") { schedulerRequestStatsWriter?.write(row) }) {
            schedulerRequestStatsCount++
            touchRecord()
        }
    }

    /** Records the admission decision and cost provenance in session metadata. */
    @Synchronized
    fun setSchedulerRunMetadata(
        admissionState: String,
        runMode: String,
        costModelId: String?,
        costSource: String?,
        requestUtilization: Double? = null,
        projectedUtilization: Double? = null,
        projectedMisses: Long? = null,
        projectedCapacityRejections: Long? = null
    ) {
        if (!isWritable()) return
        schedulerAdmissionState = admissionState
        schedulerRunMode = runMode
        schedulerCostModelId = costModelId
        schedulerCostSource = costSource
        schedulerRequestUtilization = requestUtilization
        schedulerProjectedUtilization = projectedUtilization
        schedulerProjectedMisses = projectedMisses
        schedulerProjectedCapacityRejections = projectedCapacityRejections
        touchRecord()
    }

    @Synchronized
    fun forceCheckpoint(reason: String) {
        if (!isWritable()) return
        checkpointIfDue(forceDurable = true, reason = reason)
    }

    @Synchronized
    fun takeTimingSample(): PerformanceTracker.LoggerMetrics {
        val sample = PerformanceTracker.LoggerMetrics(
            writeTotalMs = writeTimingNs / 1_000_000L,
            checkpointTotalMs = checkpointTimingNs / 1_000_000L,
            syncTotalMs = syncTimingNs / 1_000_000L,
            checkpointMaxMs = checkpointMaxNs / 1_000_000L,
            checkpointLockWaitMs = checkpointLockWaitNs / 1_000_000L
        )
        writeTimingNs = 0L
        checkpointTimingNs = 0L
        syncTimingNs = 0L
        checkpointMaxNs = 0L
        checkpointLockWaitNs = 0L
        return sample
    }

    @Synchronized
    fun finalizeAndZip(
        completionKind: LogCompletionKind = LogCompletionKind.COMPLETED,
        reason: String = "USER_END"
    ): PendingLogArchive {
        acquireProcessSessionGate()
        return try {
            finalizeAndZipWithProcessGate(completionKind, reason)
        } finally {
            releaseProcessSessionGate()
        }
    }

    private fun finalizeAndZipWithProcessGate(
        completionKind: LogCompletionKind,
        reason: String
    ): PendingLogArchive {
        stopCheckpointTimer()
        finalZip?.takeIf { state == SessionState.FINALIZED && it.isFile }?.let { zip ->
            if (runCatching { validateZip(zip, sessionDir?.name) }.isSuccess) {
                return PendingLogArchive(
                    sessionDir = sessionDir ?: error("No finalized session directory"),
                    zipFile = zip,
                    displayName = finalArchiveName ?: zip.name
                )
            }
            finalZip = null
            state = SessionState.FINALIZE_FAILED
        }
        if (state != SessionState.ACTIVE && state != SessionState.FINALIZE_FAILED) {
            error("Session cannot be finalized from state $state")
        }
        val dir = sessionDir ?: error("No active session")
        val endedAt = finalEndedAt ?: Instant.now().also { finalEndedAt = it }
        val kind = finalCompletionKind ?: completionKind.also { finalCompletionKind = it }
        val archiveName = finalArchiveName ?: LogArchiveNaming.uniqueFile(
            root,
            LogArchiveNaming.archiveName(
                endedAt = endedAt,
                zoneId = ZoneId.of(sessionTimeZoneId),
                kind = kind
            )
        ).name.also { finalArchiveName = it }

        if (state == SessionState.ACTIVE) {
            safeWrite("connection.log") {
                connectionWriter?.write("${Instant.now()} SESSION_FINALIZE_REQUEST reason=$reason\n")
            }
            forceCheckpoint("SESSION_FINALIZE_REQUEST")
        }
        state = SessionState.FINALIZING
        closeWriters(durable = true)

        val evidenceComplete = kind == LogCompletionKind.COMPLETED &&
            !loggerDegraded &&
            capacitySchedulerEvidencePresent(dir) &&
            currentSessionProvenanceComplete()
        val status = when (kind) {
            LogCompletionKind.COMPLETED -> "completed"
            LogCompletionKind.INTERRUPTED -> "interrupted"
            LogCompletionKind.START_FAILED -> "start_failed"
        }
        val target = File(root, archiveName)
        val temp = File(root, ".$archiveName.tmp")
        try {
            writePendingErrors(dir)
            writeRequestStats(dir)
            writeSessionJson(
                status = status,
                endedAt = endedAt,
                endTimeBasis = "finalize_time",
                reason = reason,
                evidenceComplete = evidenceComplete,
                archiveName = archiveName
            )
            writeManifest(dir, evidenceComplete)
            buildValidatedZip(dir, temp)
            promoteArchive(temp, target)
            validateZip(target, dir.name)
            finalZip = target
            state = SessionState.FINALIZED
            return PendingLogArchive(dir, target, archiveName)
        } catch (e: Exception) {
            state = SessionState.FINALIZE_FAILED
            val failure = "${Instant.now()} FINALIZE_FAILED ${e::class.java.simpleName}: ${e.message}\n"
            runCatching { File(dir, "finalize_failure.txt").appendText(failure) }
            runCatching { File(context.filesDir, "rx400h_probe_fallback_error.log").appendText(failure) }
            runCatching {
                writeSessionJson(
                    status = "finalize_failed",
                    endedAt = endedAt,
                    endTimeBasis = "finalize_attempt_time",
                    reason = reason,
                    evidenceComplete = false,
                    archiveName = archiveName
                )
            }
            throw e
        }
    }

    /** Flushes and leaves an incomplete session for deterministic next-launch recovery. */
    @Synchronized
    fun stopInterrupted(reason: String) {
        acquireProcessSessionGate()
        try {
            stopInterruptedWithProcessGate(reason)
        } finally {
            releaseProcessSessionGate()
        }
    }

    private fun stopInterruptedWithProcessGate(reason: String) {
        stopCheckpointTimer()
        if (state != SessionState.ACTIVE) {
            closeWriters(durable = true)
            return
        }
        runCatching {
            safeWrite("connection.log") {
                connectionWriter?.write("${Instant.now()} SESSION_INTERRUPTED reason=$reason\n")
            }
            forceCheckpoint("SESSION_INTERRUPTED")
        }
        closeWriters(durable = true)
        val lastDurable = Instant.ofEpochMilli(lastDurableRecordAtMs.coerceAtLeast(sessionStartedAtMs))
        runCatching {
            writeSessionJson(
                status = "interrupted",
                endedAt = lastDurable,
                endTimeBasis = "last_durable_record",
                reason = reason,
                evidenceComplete = false,
                archiveName = null
            )
        }
        state = SessionState.IDLE
    }

    @Synchronized
    fun stop() = stopInterrupted("LOGGER_STOP")

    @Synchronized
    fun shutdown() {
        shutdownRequested.set(true)
        stopInterrupted("LOGGER_SHUTDOWN")
        checkpointExecutor.shutdownNow()
    }

    /** Never makes the Activity main thread wait for a slow fsync or recovery hash. */
    fun shutdownAsync() {
        shutdownRequested.set(true)
        if (runCatching { checkpointExecutor.execute { shutdown() } }.isFailure) {
            Thread({ shutdown() }, "rx400h-log-shutdown").apply { isDaemon = true }.start()
        }
    }

    /** Packages incomplete old sessions and returns all V0.3.1 archives still awaiting public export. */
    @Synchronized
    fun recoverInterruptedSessions(): List<PendingLogArchive> {
        if (state == SessionState.ACTIVE || state == SessionState.FINALIZING) return emptyList()
        PROCESS_SESSION_IO_GATE.acquireUninterruptibly()
        return try {
            recoverInterruptedSessionsWithProcessGate()
        } finally {
            PROCESS_SESSION_IO_GATE.release()
        }
    }

    private fun recoverInterruptedSessionsWithProcessGate(): List<PendingLogArchive> {
        ensureRoot()
        val pending = mutableListOf<PendingLogArchive>()
        root.listFiles()
            ?.filter { isTrustedSessionDirectory(it) }
            ?.sortedBy { it.name }
            ?.forEach { dir ->
                val session = readJson(File(dir, "session.json"))
                val archiveName = trustedArchiveName(session)
                val existing = archiveName?.let { archiveFileInRoot(it) }
                val published = hasPublicationReceipt(dir, session, existing)
                if (published && existing?.isFile == true) return@forEach
                if (
                    existing != null &&
                    existing.isFile &&
                    runCatching { validateZip(existing, dir.name) }.isSuccess
                ) {
                    val metadata = session ?: return@forEach
                    val createdPending = metadata.optBoolean(
                        "public_export_pending_at_archive_creation",
                        metadata.optBoolean("public_export_pending", false)
                    )
                    if (!published && createdPending) {
                        pending += PendingLogArchive(
                            dir,
                            existing,
                            archiveName
                        )
                    }
                    return@forEach
                }

                val status = session?.optString("status").orEmpty()
                val containsEvidence = File(dir, "raw_io.jsonl").isFile || File(dir, "connection.log").isFile
                val incomplete = containsEvidence && status in setOf(
                    "",
                    "active",
                    "interrupted",
                    "finalizing",
                    "finalize_failed"
                )
                val completedButArchiveMissing = containsEvidence && status in setOf("completed", "start_failed")
                if (incomplete || completedButArchiveMissing) {
                    runCatching { recoverDirectory(dir, session, completedButArchiveMissing) }
                        .onSuccess { recovered -> if (!published) pending += recovered }
                        .onFailure { e ->
                            val line = "${Instant.now()} RECOVERY_FAILED dir=${dir.name} ${e::class.java.simpleName}: ${e.message}\n"
                            runCatching { File(dir, "recovery_failure.txt").appendText(line) }
                            runCatching { File(context.filesDir, "rx400h_probe_fallback_error.log").appendText(line) }
                        }
                }
            }
        return pending
    }

    /** Serializes public copy + receipt across replacement Activity instances. */
    @Synchronized
    fun publishAndMark(
        archive: PendingLogArchive,
        publisher: () -> PublicLogResult
    ): PublicLogCommit {
        PROCESS_PUBLICATION_GATE.acquireUninterruptibly()
        return try {
            val session = readJson(File(archive.sessionDir, "session.json"))
            if (hasPublicationReceipt(archive.sessionDir, session, archive.zipFile)) {
                val marker = readJson(File(archive.sessionDir, PUBLICATION_MARKER))
                return PublicLogCommit(
                    result = PublicLogResult(
                        success = true,
                        displayName = jsonStringOrNull(marker, "display_name") ?: archive.displayName,
                        location = jsonStringOrNull(marker, "location"),
                        uri = null
                    ),
                    receiptWritten = true
                )
            }
            val result = publisher()
            if (!result.success) return PublicLogCommit(result, receiptWritten = false)
            PublicLogCommit(result, receiptWritten = writePublicationReceipt(archive, result))
        } finally {
            PROCESS_PUBLICATION_GATE.release()
        }
    }

    private fun writePublicationReceipt(archive: PendingLogArchive, result: PublicLogResult): Boolean =
        runCatching {
            atomicWriteText(
                File(archive.sessionDir, PUBLICATION_MARKER),
                JSONObject()
                    .put("published_at", Instant.now().toString())
                    .put("session_id", archive.sessionDir.name)
                    .put("archive_name", archive.zipFile.name)
                    .put("archive_size_bytes", archive.zipFile.length())
                    .put("archive_sha256", sha256(archive.zipFile))
                    .put("display_name", result.displayName)
                    .put("location", result.location ?: JSONObject.NULL)
                    .put("uri", result.uri?.toString() ?: JSONObject.NULL)
                    .toString(2)
            )
        }.onFailure { markDegraded("publication receipt failed: ${it.message}") }.isSuccess

    private fun recoverDirectory(
        dir: File,
        previous: JSONObject?,
        completedButArchiveMissing: Boolean
    ): PendingLogArchive {
        val currentManifest = readJson(File(dir, "manifest.json"))
        val preservedPrevious = readJson(File(dir, "session.pre_recovery.json"))
        val preservedManifest = readJson(File(dir, "manifest.pre_recovery.json"))
        val recoverySource = preservedPrevious ?: previous
        val recoveryManifest = preservedManifest ?: currentManifest
        val recoveryEnd = recoveryEnd(dir, recoverySource, completedButArchiveMissing)
        val partialTail = hasPartialEvidenceTail(dir)
        val completedIntegrity = if (completedButArchiveMissing) {
            validateCompletedWorkingSet(
                dir = dir,
                previous = recoverySource,
                previousManifest = recoveryManifest,
                partialTail = partialTail,
                usePreservedSession = preservedPrevious != null
            )
        } else {
            IntegrityCheck(valid = false, reason = null)
        }
        preservePreRecoveryMetadata(dir, "session.json", "session.pre_recovery.json")
        preservePreRecoveryMetadata(dir, "manifest.json", "manifest.pre_recovery.json")
        val endedAt = recoveryEnd.instant
        val lastRecordAt = endedAt.toEpochMilli()
        val kind = when {
            recoverySource?.optString("status") == "start_failed" -> LogCompletionKind.START_FAILED
            completedButArchiveMissing -> LogCompletionKind.COMPLETED
            else -> LogCompletionKind.INTERRUPTED
        }
        val originalZone = jsonStringOrNull(recoverySource, "time_zone_id")
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()
        val preferredName = trustedArchiveName(recoverySource)
            ?: LogArchiveNaming.archiveName(endedAt, originalZone, kind)
        val target = LogArchiveNaming.uniqueFile(root, preferredName)
        require(target.canonicalFile.parentFile == root.canonicalFile) { "Archive target escaped log root" }
        val archiveName = target.name
        val temp = File(root, ".$archiveName.tmp")

        val updated = recoverySource?.let { JSONObject(it.toString()) } ?: JSONObject()
        inheritAcquisitionProvenance(updated, recoveryManifest)
        val provenanceComplete = hasCompleteAcquisitionProvenance(updated)
        val recoveredEvidenceComplete = kind == LogCompletionKind.COMPLETED &&
            completedIntegrity.valid &&
            provenanceComplete
        val integrityDowngradeReason = completedIntegrity.reason ?: if (
            kind == LogCompletionKind.COMPLETED && !provenanceComplete
        ) {
            "PROVENANCE_INCOMPLETE"
        } else {
            null
        }
        atomicWriteText(
            File(dir, "recovery.json"),
            JSONObject()
                .put("recovered_at", Instant.now().toString())
                .put("previous_status", recoverySource?.optString("status") ?: JSONObject.NULL)
                .put("last_record_at", Instant.ofEpochMilli(lastRecordAt).toString())
                .put("end_time_basis", recoveryEnd.basis)
                .put("evidence_complete", recoveredEvidenceComplete)
                .put(
                    "integrity_downgrade_reason",
                    integrityDowngradeReason ?: JSONObject.NULL
                )
                .toString(2)
        )

        updated
            .put("session_id", dir.name)
            .put(
                "status",
                when (kind) {
                    LogCompletionKind.COMPLETED -> "completed"
                    LogCompletionKind.INTERRUPTED -> "interrupted"
                    LogCompletionKind.START_FAILED -> "start_failed"
                }
            )
            .put("ended_at", endedAt.toString())
            .put("end_time_basis", recoveryEnd.basis)
            .put("recovered_at", Instant.now().toString())
            .put("transaction_count", countValidJsonLines(File(dir, "raw_io.jsonl")))
            .put("frame_count", countDataRows(File(dir, "frames.csv")))
            .put("event_count", countDataRows(File(dir, "events.csv")))
            .put("scheduler_event_count", countValidJsonLines(File(dir, "scheduler_events.jsonl")))
            .put(
                "scheduler_request_stats_count",
                countDataRows(File(dir, "scheduler_request_stats.csv"))
            )
            .put("partial_tail_detected", partialTail)
            .put(
                "runtime_error_count",
                recoverySource?.optLong("runtime_error_count", countLines(File(dir, "errors.log")))
                    ?: countLines(File(dir, "errors.log"))
            )
            .put("logger_error_count", recoverySource?.optLong("logger_error_count", 0L) ?: 0L)
            .put(
                "error_count",
                recoverySource?.optLong("error_count", countLines(File(dir, "errors.log")))
                    ?: countLines(File(dir, "errors.log"))
            )
            .put("evidence_complete", recoveredEvidenceComplete)
            .put(
                "integrity_downgrade_reason",
                integrityDowngradeReason ?: JSONObject.NULL
            )
            .put("provenance_incomplete", !hasCompleteAcquisitionProvenance(updated))
            .put("archive_name", archiveName)
            .put("public_export_pending_at_archive_creation", true)
        atomicWriteText(File(dir, "session.json"), updated.toString(2))
        writeManifest(dir, updated.optBoolean("evidence_complete", false))
        buildValidatedZip(dir, temp)
        promoteArchive(temp, target)
        validateZip(target, dir.name)
        return PendingLogArchive(dir, target, archiveName)
    }

    private fun checkpointIfDue(forceDurable: Boolean = false, reason: String? = null) {
        if (!isWritable()) return
        val now = SystemClock.elapsedRealtime()
        val flushDue = forceDurable || now - lastFlushElapsedMs >= BULK_FLUSH_INTERVAL_MS
        val syncDue = forceDurable || now - lastSyncElapsedMs >= DURABLE_SYNC_INTERVAL_MS
        if (!flushDue && !syncDue) return
        val checkpointStart = SystemClock.elapsedRealtimeNanos()
        try {
            if (flushDue && flushWriters()) lastFlushElapsedMs = now
            if (syncDue) {
                val previousDurable = lastDurableRecordAtMs
                if (syncWriters()) {
                    lastDurableRecordAtMs = lastRecordAtMs
                    try {
                        writeSessionJson(
                            status = "active",
                            endedAt = null,
                            endTimeBasis = null,
                            reason = reason ?: "PERIODIC_CHECKPOINT",
                            evidenceComplete = false,
                            archiveName = null
                        )
                        lastSyncElapsedMs = now
                    } catch (e: Exception) {
                        lastDurableRecordAtMs = previousDurable
                        markDegraded("checkpoint metadata failed: ${e.message}")
                        scheduleDurabilityRetry(now)
                    }
                } else {
                    scheduleDurabilityRetry(now)
                }
            }
        } finally {
            val elapsed = SystemClock.elapsedRealtimeNanos() - checkpointStart
            checkpointTimingNs += elapsed
            if (elapsed > checkpointMaxNs) checkpointMaxNs = elapsed
        }
    }

    private fun startCheckpointTimer() {
        stopCheckpointTimer()
        checkpointTask = checkpointExecutor.scheduleWithFixedDelay(
            {
                val lockRequestedAt = SystemClock.elapsedRealtimeNanos()
                synchronized(this) {
                    checkpointLockWaitNs += SystemClock.elapsedRealtimeNanos() - lockRequestedAt
                    runCatching { checkpointIfDue(reason = "TIMER_CHECKPOINT") }
                        .onFailure { markDegraded("checkpoint timer failed: ${it.message}") }
                }
            },
            BULK_FLUSH_INTERVAL_MS,
            BULK_FLUSH_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopCheckpointTimer() {
        checkpointTask?.cancel(false)
        checkpointTask = null
    }

    private fun touchRecord() {
        lastRecordAtMs = System.currentTimeMillis()
    }

    private fun writer(dir: File, name: String): DurableWriter = DurableWriter(File(dir, name))
    private fun isWritable(): Boolean = state == SessionState.ACTIVE

    private fun flushWriters(): Boolean {
        var succeeded = true
        allWriters().forEach { writer ->
            runCatching { writer.flush() }.onFailure {
                succeeded = false
                markDegraded("flush failed: ${it.message}")
            }
        }
        return succeeded
    }

    private fun syncWriters(): Boolean {
        val start = SystemClock.elapsedRealtimeNanos()
        var succeeded = true
        try {
            allWriters().forEach { writer ->
                runCatching { writer.sync() }.onFailure {
                    succeeded = false
                    markDegraded("sync failed: ${it.message}")
                }
            }
        } finally {
            syncTimingNs += SystemClock.elapsedRealtimeNanos() - start
        }
        return succeeded
    }

    private fun scheduleDurabilityRetry(now: Long) {
        lastSyncElapsedMs = now - (DURABLE_SYNC_INTERVAL_MS - DURABLE_RETRY_INTERVAL_MS)
    }

    private fun closeWriters(durable: Boolean) {
        allWriters().forEach { writer ->
            runCatching { writer.close(durable) }.onFailure { markDegraded("close failed: ${it.message}") }
        }
        rawWriter = null
        eventWriter = null
        frameWriter = null
        connectionWriter = null
        errorWriter = null
        decodedWriter = null
        performanceWriter = null
        schedulerEventWriter = null
        schedulerRequestStatsWriter = null
    }

    private fun allWriters(): List<DurableWriter> = listOfNotNull(
        rawWriter,
        eventWriter,
        frameWriter,
        connectionWriter,
        errorWriter,
        decodedWriter,
        performanceWriter,
        schedulerEventWriter,
        schedulerRequestStatsWriter
    )

    private fun safeWrite(target: String, block: () -> Unit): Boolean {
        val start = SystemClock.elapsedRealtimeNanos()
        return try {
            block()
            true
        } catch (e: Exception) {
            markDegraded("write failed target=$target ${e::class.java.simpleName}: ${e.message}")
            false
        } finally {
            writeTimingNs += SystemClock.elapsedRealtimeNanos() - start
        }
    }

    private fun markDegraded(message: String) {
        loggerDegraded = true
        loggerErrorCount++
        if (pendingErrors.size >= 100) pendingErrors.removeFirst()
        val line = "${Instant.now()} $message"
        pendingErrors.addLast(line)
        runCatching { File(context.filesDir, "rx400h_probe_fallback_error.log").appendText("$line\n") }
    }

    private fun writePendingErrors(dir: File) {
        if (pendingErrors.isEmpty()) return
        File(dir, "errors.log").appendText(pendingErrors.joinToString("\n", postfix = "\n"))
        pendingErrors.clear()
    }

    private fun writeRequestStats(dir: File) {
        File(dir, "request_stats.csv").bufferedWriter().use { out ->
            out.write("key,count,ok,success_rate,avg_latency_ms,max_latency_ms\n")
            requestStats.toSortedMap().forEach { (key, stat) ->
                val successRate = if (stat.count == 0L) 0.0 else stat.ok.toDouble() / stat.count
                val average = if (stat.count == 0L) 0.0 else stat.totalLatency.toDouble() / stat.count
                out.write("${csv(key)},${stat.count},${stat.ok},$successRate,$average,${stat.maxLatency}\n")
            }
        }
    }

    private fun writeSessionJson(
        status: String,
        endedAt: Instant?,
        endTimeBasis: String?,
        reason: String?,
        evidenceComplete: Boolean,
        archiveName: String?
    ) {
        val dir = sessionDir ?: return
        val zoneId = ZoneId.of(sessionTimeZoneId)
        val referenceInstant = endedAt ?: Instant.ofEpochMilli(lastRecordAtMs.coerceAtLeast(sessionStartedAtMs))
        val obj = JSONObject()
            .put("session_id", sessionId)
            .put("created_at", Instant.ofEpochMilli(sessionStartedAtMs).toString())
            .put("ended_at", endedAt?.toString() ?: JSONObject.NULL)
            .put("end_time_basis", endTimeBasis ?: JSONObject.NULL)
            .put("last_record_at", Instant.ofEpochMilli(lastRecordAtMs.coerceAtLeast(sessionStartedAtMs)).toString())
            .put(
                "last_durable_record_at",
                Instant.ofEpochMilli(lastDurableRecordAtMs.coerceAtLeast(sessionStartedAtMs)).toString()
            )
            .put("time_zone_id", sessionTimeZoneId)
            .put("utc_offset", zoneId.rules.getOffset(referenceInstant).id)
            .put("status", status)
            .put("status_reason", reason ?: JSONObject.NULL)
            .put("app_version", APP_VERSION)
            .put("version_name", BuildConfig.VERSION_NAME)
            .put("version_code", BuildConfig.VERSION_CODE)
            .put("application_id", BuildConfig.APPLICATION_ID)
            .put("build_type", BuildConfig.BUILD_TYPE)
            .put("git_commit", BuildConfig.GIT_COMMIT)
            .put("git_dirty", BuildConfig.GIT_DIRTY)
            .put("apk_sha256", apkSha256)
            .put("signing_certificate_sha256", signingCertificateSha256)
            .put("protocol_profile_version", PROFILE_VERSION)
            .put("decoder_version", ObdParsers.DECODER_VERSION)
            .put("scheduler_profile", SCHEDULER_PROFILE)
            .put("transaction_count", transactionCount)
            .put("frame_count", frameCount)
            .put("event_count", eventCount)
            .put("scheduler_event_count", schedulerEventCount)
            .put("scheduler_request_stats_count", schedulerRequestStatsCount)
            .put("scheduler_admission_state", schedulerAdmissionState)
            .put("scheduler_run_mode", schedulerRunMode)
            .put("scheduler_cost_model_id", schedulerCostModelId ?: JSONObject.NULL)
            .put("scheduler_cost_source", schedulerCostSource ?: JSONObject.NULL)
            .put(
                "scheduler_request_utilization",
                schedulerRequestUtilization ?: JSONObject.NULL
            )
            .put(
                "scheduler_projected_utilization",
                schedulerProjectedUtilization ?: JSONObject.NULL
            )
            .put("scheduler_projected_misses", schedulerProjectedMisses ?: JSONObject.NULL)
            .put(
                "scheduler_projected_capacity_rejections",
                schedulerProjectedCapacityRejections ?: JSONObject.NULL
            )
            .put("runtime_error_count", errorCount)
            .put("logger_error_count", loggerErrorCount)
            .put("error_count", errorCount + loggerErrorCount)
            .put("logger_degraded", loggerDegraded)
            .put("evidence_complete", evidenceComplete)
            .put("archive_name", archiveName ?: JSONObject.NULL)
            .put("public_export_pending_at_archive_creation", archiveName != null)
        atomicWriteText(File(dir, "session.json"), obj.toString(2))
    }

    private fun writeManifest(dir: File, evidenceComplete: Boolean) {
        val files = JSONArray()
        dir.listFiles()
            ?.filter {
                it.isFile && it.name !in setOf("manifest.json", PUBLICATION_MARKER)
            }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                files.put(
                    JSONObject()
                        .put("name", file.name)
                        .put("size_bytes", file.length())
                        .put("sha256", sha256(file))
                )
            }
        val sessionMetadata = readJson(File(dir, "session.json"))
        val provenanceIncomplete = !hasCompleteAcquisitionProvenance(sessionMetadata)
        atomicWriteText(
            File(dir, "manifest.json"),
            JSONObject()
                .put("generated_at", Instant.now().toString())
                .put("session_id", jsonStringOrNull(sessionMetadata, "session_id") ?: JSONObject.NULL)
                .put("app_version", jsonStringOrNull(sessionMetadata, "app_version") ?: JSONObject.NULL)
                .put("version_name", jsonStringOrNull(sessionMetadata, "version_name") ?: JSONObject.NULL)
                .put("version_code", jsonLongOrNull(sessionMetadata, "version_code") ?: JSONObject.NULL)
                .put("application_id", jsonStringOrNull(sessionMetadata, "application_id") ?: JSONObject.NULL)
                .put("build_type", jsonStringOrNull(sessionMetadata, "build_type") ?: JSONObject.NULL)
                .put("git_commit", jsonStringOrNull(sessionMetadata, "git_commit") ?: JSONObject.NULL)
                .put("git_dirty", jsonBooleanOrNull(sessionMetadata, "git_dirty") ?: JSONObject.NULL)
                .put("apk_sha256", jsonStringOrNull(sessionMetadata, "apk_sha256") ?: JSONObject.NULL)
                .put(
                    "signing_certificate_sha256",
                    jsonStringOrNull(sessionMetadata, "signing_certificate_sha256") ?: JSONObject.NULL
                )
                .put(
                    "profile_version",
                    jsonStringOrNull(sessionMetadata, "protocol_profile_version") ?: JSONObject.NULL
                )
                .put(
                    "decoder_version",
                    jsonStringOrNull(sessionMetadata, "decoder_version") ?: JSONObject.NULL
                )
                .put(
                    "scheduler_profile",
                    jsonStringOrNull(sessionMetadata, "scheduler_profile") ?: JSONObject.NULL
                )
                .put(
                    "scheduler_admission_state",
                    jsonStringOrNull(sessionMetadata, "scheduler_admission_state") ?: JSONObject.NULL
                )
                .put(
                    "scheduler_run_mode",
                    jsonStringOrNull(sessionMetadata, "scheduler_run_mode") ?: JSONObject.NULL
                )
                .put(
                    "scheduler_cost_model_id",
                    jsonStringOrNull(sessionMetadata, "scheduler_cost_model_id") ?: JSONObject.NULL
                )
                .put(
                    "scheduler_cost_source",
                    jsonStringOrNull(sessionMetadata, "scheduler_cost_source") ?: JSONObject.NULL
                )
                .put(
                    "scheduler_request_utilization",
                    jsonDoubleOrNull(sessionMetadata, "scheduler_request_utilization") ?: JSONObject.NULL
                )
                .put(
                    "scheduler_projected_utilization",
                    jsonDoubleOrNull(sessionMetadata, "scheduler_projected_utilization") ?: JSONObject.NULL
                )
                .put(
                    "scheduler_projected_misses",
                    jsonLongOrNull(sessionMetadata, "scheduler_projected_misses") ?: JSONObject.NULL
                )
                .put(
                    "scheduler_projected_capacity_rejections",
                    jsonLongOrNull(sessionMetadata, "scheduler_projected_capacity_rejections")
                        ?: JSONObject.NULL
                )
                .put("provenance_incomplete", provenanceIncomplete)
                .put("manifest_generator_app_version", APP_VERSION)
                .put("manifest_generator_version_name", BuildConfig.VERSION_NAME)
                .put("manifest_generator_version_code", BuildConfig.VERSION_CODE)
                .put("manifest_generator_git_commit", BuildConfig.GIT_COMMIT)
                .put("manifest_generator_git_dirty", BuildConfig.GIT_DIRTY)
                .put("evidence_complete", evidenceComplete)
                .put("public_export_receipt_external", true)
                .put("files", files)
                .toString(2)
        )
    }

    private fun buildValidatedZip(dir: File, temp: File) {
        if (temp.exists() && !temp.delete()) error("Cannot replace temporary ZIP: ${temp.absolutePath}")
        val canonicalDir = dir.canonicalFile
        ZipOutputStream(FileOutputStream(temp)).use { zip ->
            dir.listFiles()
                ?.asSequence()
                ?.filter { it.isFile && it.name != PUBLICATION_MARKER }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    require(file.parentFile?.canonicalFile == canonicalDir) {
                        "Evidence file escaped session directory"
                    }
                    val relative = file.name
                    zip.putNextEntry(ZipEntry(relative))
                    FileInputStream(file).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        if (temp.length() <= 0L) error("Generated ZIP is empty")
        FileOutputStream(temp, true).use { it.fd.sync() }
        validateZip(temp, dir.name)
    }

    private fun promoteArchive(temp: File, target: File) {
        if (target.exists()) {
            val sameArchive = runCatching {
                validateZip(target)
                target.length() == temp.length() && sha256(target) == sha256(temp)
            }.getOrDefault(false)
            if (sameArchive) {
                if (!temp.delete()) error("Cannot remove redundant temporary ZIP: ${temp.absolutePath}")
                return
            }
            error("Archive promotion conflict: ${target.absolutePath}")
        }
        if (temp.renameTo(target)) return
        val staging = File(target.parentFile, ".${target.name}.promoting")
        if (staging.exists() && !staging.delete()) {
            error("Cannot replace promotion staging ZIP: ${staging.absolutePath}")
        }
        FileInputStream(temp).use { input ->
            FileOutputStream(staging).use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        }
        validateZip(staging)
        if (!staging.renameTo(target)) {
            error("Cannot atomically promote validated ZIP: ${target.absolutePath}")
        }
        validateZip(target)
        if (!temp.delete()) error("Cannot remove promoted temporary ZIP: ${temp.absolutePath}")
    }

    private fun validateZip(file: File, expectedSessionId: String? = null) {
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            if (!entries.hasMoreElements()) error("ZIP contains no entries")
            val buffer = ByteArray(8192)
            val fingerprints = linkedMapOf<String, ZipFingerprint>()
            var manifestBytes: ByteArray? = null
            var sessionBytes: ByteArray? = null
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                require(!entry.isDirectory) { "ZIP contains an unexpected directory: ${entry.name}" }
                require(isSafeEvidenceEntryName(entry.name)) { "ZIP contains an unsafe entry: ${entry.name}" }
                require(entry.name !in fingerprints) { "ZIP contains a duplicate entry: ${entry.name}" }
                val digest = MessageDigest.getInstance("SHA-256")
                val crc = CRC32()
                var size = 0L
                val captured = if (entry.name in setOf("manifest.json", "session.json")) {
                    ByteArrayOutputStream()
                } else {
                    null
                }
                zip.getInputStream(entry).use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        size += read
                        crc.update(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        captured?.write(buffer, 0, read)
                    }
                }
                require(entry.size < 0L || entry.size == size) {
                    "ZIP entry size mismatch: ${entry.name}"
                }
                require(entry.crc < 0L || entry.crc == crc.value) {
                    "ZIP entry CRC mismatch: ${entry.name}"
                }
                val fingerprint = ZipFingerprint(
                    size = size,
                    crc32 = crc.value,
                    sha256 = digest.digest().toHex()
                )
                fingerprints[entry.name] = fingerprint
                if (captured != null) {
                    when (entry.name) {
                        "manifest.json" -> manifestBytes = captured.toByteArray()
                        "session.json" -> sessionBytes = captured.toByteArray()
                    }
                }
            }
            require("session.json" in fingerprints) { "ZIP is missing session.json" }
            val session = sessionBytes?.let { JSONObject(String(it, Charsets.UTF_8)) }
                ?: error("ZIP session metadata is invalid")
            val manifest = manifestBytes?.let { JSONObject(String(it, Charsets.UTF_8)) }
                ?: error("ZIP is missing manifest.json")
            val archivedSessionId = jsonStringOrNull(session, "session_id")
            val manifestSessionId = jsonStringOrNull(manifest, "session_id")
            val requiredSessionId = expectedSessionId ?: archivedSessionId
                ?: error("ZIP session_id is missing")
            require(
                EvidenceRecoveryPolicy.identityMatches(
                    expectedSessionId = requiredSessionId,
                    sessionId = archivedSessionId,
                    manifestSessionId = manifestSessionId
                )
            ) { "ZIP session identity mismatch" }
            val records = manifestRecords(manifest) ?: error("ZIP manifest file list is invalid")
            require(requiredEvidenceFiles(session).all { it in records }) {
                "ZIP manifest is missing required evidence files"
            }
            require(fingerprints.keys == records.keys + "manifest.json") {
                "ZIP entries do not match the manifest file set"
            }
            records.forEach { (name, expected) ->
                val actual = fingerprints[name] ?: error("ZIP entry missing: $name")
                require(actual.size == expected.size) { "Manifest size mismatch: $name" }
                require(actual.sha256 == expected.sha256) { "Manifest SHA-256 mismatch: $name" }
            }
        }
    }

    private fun validateCompletedWorkingSet(
        dir: File,
        previous: JSONObject?,
        previousManifest: JSONObject?,
        partialTail: Boolean,
        usePreservedSession: Boolean
    ): IntegrityCheck {
        return try {
            if (previous == null || !previous.optBoolean("evidence_complete", false)) {
                return IntegrityCheck(false, "PREVIOUS_SESSION_NOT_COMPLETE")
            }
            if (previousManifest == null || !previousManifest.optBoolean("evidence_complete", false)) {
                return IntegrityCheck(false, "PREVIOUS_MANIFEST_NOT_COMPLETE")
            }
            if (
                !EvidenceRecoveryPolicy.identityMatches(
                    expectedSessionId = dir.name,
                    sessionId = jsonStringOrNull(previous, "session_id"),
                    manifestSessionId = jsonStringOrNull(previousManifest, "session_id")
                )
            ) {
                return IntegrityCheck(false, "SESSION_ID_MISMATCH")
            }
            if (partialTail) return IntegrityCheck(false, "PARTIAL_EVIDENCE_TAIL")

            val records = manifestRecords(previousManifest)
                ?: return IntegrityCheck(false, "INVALID_PREVIOUS_MANIFEST")
            if (!requiredEvidenceFiles(previous).all { it in records }) {
                return IntegrityCheck(false, "REQUIRED_FILE_MISSING_FROM_MANIFEST")
            }
            val actualNames = EvidenceRecoveryPolicy.acquisitionFileNames(
                dir.listFiles()
                    ?.filter { it.isFile }
                    ?.map { it.name }
                    .orEmpty()
            )
            if (actualNames != records.keys) {
                return IntegrityCheck(false, "WORKING_FILE_SET_MISMATCH")
            }
            records.forEach { (name, expected) ->
                val working = File(
                    dir,
                    EvidenceRecoveryPolicy.sourceFileName(name, usePreservedSession)
                )
                if (!working.isFile || working.length() != expected.size || sha256(working) != expected.sha256) {
                    return IntegrityCheck(false, "WORKING_FILE_HASH_MISMATCH:$name")
                }
            }

            val countChecks = listOf(
                "transaction_count" to countValidJsonLines(File(dir, "raw_io.jsonl")),
                "frame_count" to countDataRows(File(dir, "frames.csv")),
                "event_count" to countDataRows(File(dir, "events.csv"))
            )
            countChecks.forEach { (key, actual) ->
                if (!previous.has(key) || previous.isNull(key) || previous.optLong(key, -1L) != actual) {
                    return IntegrityCheck(false, "RECORD_COUNT_MISMATCH:$key")
                }
            }
            IntegrityCheck(true, null)
        } catch (failure: Exception) {
            IntegrityCheck(false, "INTEGRITY_CHECK_FAILED:${failure::class.java.simpleName}")
        }
    }

    private fun manifestRecords(manifest: JSONObject): Map<String, ManifestRecord>? = runCatching {
        val files = manifest.optJSONArray("files") ?: error("manifest files missing")
        val records = linkedMapOf<String, ManifestRecord>()
        for (index in 0 until files.length()) {
            val item = files.getJSONObject(index)
            val name = jsonStringOrNull(item, "name") ?: error("manifest name missing")
            val size = jsonLongOrNull(item, "size_bytes") ?: error("manifest size missing")
            val hash = jsonStringOrNull(item, "sha256")?.lowercase()
                ?: error("manifest hash missing")
            require(isSafeEvidenceEntryName(name) && name != "manifest.json" && name != PUBLICATION_MARKER)
            require(size >= 0L && hash.matches(Regex("[0-9a-f]{64}")))
            require(records.put(name, ManifestRecord(size, hash)) == null) {
                "duplicate manifest entry"
            }
        }
        records
    }.getOrNull()

    private fun isSafeEvidenceEntryName(name: String): Boolean =
        name.length in 1..160 &&
            File(name).name == name &&
            !name.contains('/') &&
            !name.contains('\\') &&
            name.none { it.code < 0x20 }

    private fun inheritAcquisitionProvenance(target: JSONObject, oldManifest: JSONObject?) {
        if (oldManifest == null) return
        listOf(
            "app_version" to "app_version",
            "version_name" to "version_name",
            "version_code" to "version_code",
            "application_id" to "application_id",
            "build_type" to "build_type",
            "git_commit" to "git_commit",
            "git_dirty" to "git_dirty",
            "apk_sha256" to "apk_sha256",
            "signing_certificate_sha256" to "signing_certificate_sha256",
            "profile_version" to "protocol_profile_version",
            "decoder_version" to "decoder_version",
            "scheduler_profile" to "scheduler_profile",
            "scheduler_admission_state" to "scheduler_admission_state",
            "scheduler_run_mode" to "scheduler_run_mode",
            "scheduler_cost_model_id" to "scheduler_cost_model_id",
            "scheduler_cost_source" to "scheduler_cost_source",
            "scheduler_request_utilization" to "scheduler_request_utilization",
            "scheduler_projected_utilization" to "scheduler_projected_utilization",
            "scheduler_projected_misses" to "scheduler_projected_misses",
            "scheduler_projected_capacity_rejections" to
                "scheduler_projected_capacity_rejections"
        ).forEach { (sourceKey, targetKey) ->
            if ((!target.has(targetKey) || target.isNull(targetKey)) &&
                oldManifest.has(sourceKey) && !oldManifest.isNull(sourceKey)
            ) {
                target.put(targetKey, oldManifest.get(sourceKey))
            }
        }
    }

    private fun hasCompleteAcquisitionProvenance(metadata: JSONObject?): Boolean {
        if (metadata == null) return false
        val requiredText = listOf(
            "app_version",
            "version_name",
            "application_id",
            "build_type",
            "protocol_profile_version",
            "decoder_version",
            "scheduler_profile"
        )
        if (requiredText.any { jsonStringOrNull(metadata, it) == null }) return false
        if ((jsonLongOrNull(metadata, "version_code") ?: 0L) <= 0L) return false
        if (jsonBooleanOrNull(metadata, "git_dirty") == null) return false
        val git = jsonStringOrNull(metadata, "git_commit")?.lowercase()
        val apk = jsonStringOrNull(metadata, "apk_sha256")?.lowercase()
        val cert = jsonStringOrNull(metadata, "signing_certificate_sha256")?.lowercase()
        return git?.matches(Regex("[0-9a-f]{40}")) == true &&
            apk?.matches(Regex("[0-9a-f]{64}")) == true &&
            cert?.matches(Regex("[0-9a-f]{64}")) == true
    }

    private fun requiredEvidenceFiles(metadata: JSONObject?): Set<String> {
        val schedulerProfile = jsonStringOrNull(metadata, "scheduler_profile")
        return if (schedulerProfile == SCHEDULER_PROFILE) {
            REQUIRED_EVIDENCE_FILES + CAPACITY_SCHEDULER_EVIDENCE_FILES
        } else {
            REQUIRED_EVIDENCE_FILES
        }
    }

    private fun capacitySchedulerEvidencePresent(dir: File): Boolean =
        CAPACITY_SCHEDULER_EVIDENCE_FILES.all { File(dir, it).isFile } &&
            schedulerEventCount > 0L &&
            schedulerRequestStatsCount >= RequestTable.requests.size.toLong() &&
            schedulerRunMode != "NOT_STARTED"

    private fun currentSessionProvenanceComplete(): Boolean =
        BuildConfig.GIT_COMMIT.matches(Regex("[0-9a-fA-F]{40}")) &&
            apkSha256.matches(Regex("[0-9a-f]{64}")) &&
            signingCertificateSha256.matches(Regex("[0-9a-f]{64}"))

    private fun recoveryEnd(
        dir: File,
        previous: JSONObject?,
        completedButArchiveMissing: Boolean
    ): RecoveryEnd {
        if (completedButArchiveMissing) {
            jsonStringOrNull(previous, "ended_at")
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?.let { ended ->
                    return RecoveryEnd(
                        ended,
                        jsonStringOrNull(previous, "end_time_basis") ?: "recorded_finalize_time"
                    )
                }
        }
        jsonStringOrNull(previous, "last_durable_record_at")
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?.let { return RecoveryEnd(it, "last_durable_record") }
        val fileTime = dir.listFiles()
            ?.filter { it.isFile && it.name in RECORD_TIME_FILES }
            ?.maxOfOrNull { it.lastModified() }
        if (fileTime != null && fileTime > 0L) {
            return RecoveryEnd(Instant.ofEpochMilli(fileTime), "last_record_file_mtime")
        }
        jsonStringOrNull(previous, "created_at")
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?.let { return RecoveryEnd(it, "session_created_at_fallback") }
        return RecoveryEnd(Instant.ofEpochMilli(dir.lastModified()), "session_directory_mtime_fallback")
    }

    private fun countLines(file: File): Long {
        if (!file.isFile) return 0L
        var count = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                for (index in 0 until read) if (buffer[index] == '\n'.code.toByte()) count++
            }
        }
        return count
    }

    private fun countValidJsonLines(file: File): Long {
        if (!file.isFile) return 0L
        var valid = 0L
        var lastLineValid = false
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                lastLineValid = line.isNotBlank() && runCatching { JSONObject(line) }.isSuccess
                if (lastLineValid) valid++
            }
        }
        if (!endsWithLineBreak(file) && lastLineValid) valid--
        return valid
    }

    private fun hasPartialEvidenceTail(dir: File): Boolean = listOf(
        "raw_io.jsonl",
        "decoded.jsonl",
        "frames.csv",
        "events.csv",
        "performance.csv",
        "scheduler_events.jsonl",
        "scheduler_request_stats.csv",
        "connection.log",
        "errors.log"
    ).any { name -> File(dir, name).let { it.isFile && it.length() > 0L && !endsWithLineBreak(it) } }

    private fun endsWithLineBreak(file: File): Boolean = FileInputStream(file).use { input ->
        var last = -1
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            last = buffer[read - 1].toInt() and 0xFF
        }
        last == '\n'.code || last == '\r'.code
    }

    private fun countDataRows(file: File): Long = (countLines(file) - if (file.isFile) 1L else 0L).coerceAtLeast(0L)

    private fun readJson(file: File): JSONObject? = runCatching {
        AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    }.getOrNull()

    private fun hasPublicationReceipt(dir: File, session: JSONObject?, archive: File?): Boolean {
        val marker = readJson(File(dir, PUBLICATION_MARKER)) ?: return false
        if (archive == null || !archive.isFile) return false
        if (jsonStringOrNull(marker, "session_id") != dir.name) return false
        if (jsonStringOrNull(marker, "archive_name") != archive.name) return false
        if (marker.optLong("archive_size_bytes", -1L) != archive.length()) return false
        val expectedHash = jsonStringOrNull(marker, "archive_sha256") ?: return false
        if (runCatching { validateZip(archive, dir.name) }.isFailure) return false
        if (runCatching { sha256(archive) }.getOrNull() != expectedHash) return false
        val recordedSessionId = jsonStringOrNull(session, "session_id")
        return recordedSessionId == null || recordedSessionId == dir.name
    }

    private fun jsonStringOrNull(json: JSONObject?, key: String): String? {
        if (json == null || json.isNull(key)) return null
        return json.optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun jsonLongOrNull(json: JSONObject?, key: String): Long? {
        if (json == null || !json.has(key) || json.isNull(key)) return null
        return runCatching { json.getLong(key) }.getOrNull()
    }

    private fun jsonDoubleOrNull(json: JSONObject?, key: String): Double? {
        if (json == null || !json.has(key) || json.isNull(key)) return null
        return runCatching { json.getDouble(key) }.getOrNull()?.takeIf { it.isFinite() }
    }

    private fun jsonBooleanOrNull(json: JSONObject?, key: String): Boolean? {
        if (json == null || !json.has(key) || json.isNull(key)) return null
        return runCatching { json.getBoolean(key) }.getOrNull()
    }

    private fun trustedArchiveName(json: JSONObject?): String? =
        jsonStringOrNull(json, "archive_name")?.takeIf(LogArchiveNaming::isSafeArchiveName)

    private fun archiveFileInRoot(name: String): File {
        val file = File(root, name).canonicalFile
        require(file.parentFile == root.canonicalFile) { "Archive path escaped log root" }
        return file
    }

    private fun isTrustedSessionDirectory(dir: File): Boolean = runCatching {
        if (!dir.isDirectory || !dir.name.startsWith("RX400h_")) return@runCatching false
        val absolute = dir.absoluteFile
        val canonical = dir.canonicalFile
        canonical == absolute && canonical.parentFile == root.canonicalFile
    }.getOrDefault(false)

    private fun acquireProcessSessionGate() {
        if (ownsProcessSessionGate) return
        PROCESS_SESSION_IO_GATE.acquireUninterruptibly()
        ownsProcessSessionGate = true
    }

    private fun releaseProcessSessionGate() {
        if (!ownsProcessSessionGate) return
        ownsProcessSessionGate = false
        PROCESS_SESSION_IO_GATE.release()
    }

    private fun ensureRoot() {
        if (!root.exists() && !root.mkdirs()) error("Cannot create log root: ${root.absolutePath}")
    }

    private fun atomicWriteText(target: File, text: String) {
        atomicWriteBytes(target, text.toByteArray(Charsets.UTF_8))
    }

    private fun atomicWriteBytes(target: File, bytes: ByteArray) {
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            output.flush()
            atomic.finishWrite(output)
        } catch (e: Exception) {
            atomic.failWrite(output)
            throw e
        }
    }

    private fun preservePreRecoveryMetadata(dir: File, sourceName: String, targetName: String) {
        val target = File(dir, targetName)
        val source = File(dir, sourceName)
        val bytes = runCatching { AtomicFile(source).openRead().use { it.readBytes() } }
            .recoverCatching { if (source.isFile) source.readBytes() else throw it }
            .getOrNull()
            ?: return
        val existing = runCatching { AtomicFile(target).openRead().use { it.readBytes() } }.getOrNull()
        if (existing != null && existing.isNotEmpty()) {
            val validJson = runCatching { JSONObject(String(existing, Charsets.UTF_8)) }.isSuccess
            if (validJson) return
        }
        atomicWriteBytes(target, bytes)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun sha256Text(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateSha256(): String = runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            packageInfo.signatures?.firstOrNull()
        } ?: return@runCatching "unavailable"
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }.getOrDefault("unavailable")

    private fun ByteArray.toHex(): String = joinToString("") {
        "%02x".format(it.toInt() and 0xFF)
    }

    private fun icePowerKw(rpm: Double?, torqueNm: Double?): Double? {
        if (rpm == null || torqueNm == null) return null
        return torqueNm * 2.0 * Math.PI * rpm / 60.0 / 1000.0
    }

    private fun csv(text: String): String = "\"${text
        .replace("\"", "\"\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")}\""

    private fun csvNumber(value: Long?): String = value?.toString().orEmpty()

    private class DurableWriter(file: File) {
        private val stream = FileOutputStream(file, true)
        private val writer = BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8))

        fun write(value: String) = writer.write(value)
        fun newLine() = writer.newLine()
        fun flush() = writer.flush()

        fun sync() {
            writer.flush()
            stream.fd.sync()
        }

        fun close(durable: Boolean) {
            var firstFailure: Throwable? = null
            try {
                if (durable) sync() else writer.flush()
            } catch (failure: Throwable) {
                firstFailure = failure
            }
            try {
                writer.close()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
            firstFailure?.let { throw it }
        }
    }
}
