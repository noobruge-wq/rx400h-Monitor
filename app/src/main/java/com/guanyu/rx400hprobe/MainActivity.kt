package com.guanyu.rx400hprobe

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_DEVICE = 100
        private const val REQUEST_SAVE_LOG = 101
        private const val REQUEST_RUNTIME_PERMISSIONS = 20
        private const val RESPONSE_ENGINE = "7E8"
        private const val RESPONSE_HYBRID = "7EA"
        private const val STORAGE_PERMISSION_ASKED = "storage_permission_asked"
        private val PROCESS_VEHICLE_SESSION_LEASE = ExclusiveSessionLease()
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val connectionCancelWorker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val elm = Elm327Client()
    private lateinit var logger: ProbeLogger
    private lateinit var publicLogExporter: PublicLogExporter
    private lateinit var dashboard: DashboardUi

    private val phase = AtomicReference(MonitorSessionPhase.IDLE)
    private val stopRequested = AtomicBoolean(false)
    private val liveMode = AtomicBoolean(false)
    private val endRequestedFromLive = AtomicBoolean(false)
    private val endRequestedAtWallMs = AtomicLong(0L)
    private val pendingPermissionStart = AtomicBoolean(false)
    private val destroying = AtomicBoolean(false)
    private val scheduler = DeadlineScheduler(
        RequestTable.requests.map { ScheduledSpec(it.id, it.header, it.targetPeriodMs, it.priority) }
    )
    private val latencyWindow = LatencyWindow(64)
    private val signalUpdateCounter = AtomicLong()
    private val noDataCount = AtomicLong()
    private val timeoutCount = AtomicLong()
    private val busErrorCount = AtomicLong()
    private val signalLock = Any()

    private var deviceAddress: String? = null
    private var deviceName: String? = null
    private var currentHeader: String? = null
    private val store = SignalStore()
    private val baseline get() = store.baseline
    private val hybrid get() = store.hybrid
    private val idleCheckState = IdleCheckState()
    private val performanceTracker = PerformanceTracker()

    private var reconnectCount = 0
    private var consecutiveErrors = 0
    private var lastError = "NONE"
    private var lastNotice: String? = null
    private var pendingArchive: PendingLogArchive? = null
    private var pendingPublicationReceipt: PublicLogResult? = null
    private var pendingManualArchive: PendingLogArchive? = null
    private var retryCompletionKind = LogCompletionKind.COMPLETED
    private var retryReason = "USER_END"
    @Volatile
    private var lastRenderDurationMs = 0L
    private var lastFrameLogMs = 0L
    private var lastIdleCheckLogged: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        logger = ProbeLogger(this)
        publicLogExporter = PublicLogExporter(this)
        setContentView(buildDashboard())
        loadSavedDevice()
        renderDashboard()
        ui.post(refreshUiRunnable)
        recoverPendingLogs()
    }

    override fun onStart() {
        super.onStart()
        if (::logger.isInitialized && logger.state == SessionState.ACTIVE) {
            logger.logEventAsync("ACTIVITY_START")
        }
    }

    override fun onStop() {
        if (::logger.isInitialized && logger.state == SessionState.ACTIVE) {
            logger.logEventAsync("ACTIVITY_STOP")
        }
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dashboard.onConfigurationChanged()
        refreshControls()
        renderDashboard()
    }

    @Deprecated("Back is guarded while a vehicle/log session owns the Activity")
    override fun onBackPressed() {
        if (phase.get() !in setOf(MonitorSessionPhase.IDLE, MonitorSessionPhase.SAVE_FAILED)) {
            lastError = "请先按结束并等待保存完成"
            renderDashboard()
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        destroying.set(true)
        pendingPermissionStart.set(false)
        stopRequested.set(true)
        liveMode.set(false)
        closeElmAsync()
        if (::logger.isInitialized) logger.shutdownAsync()
        ui.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        connectionCancelWorker.shutdown()
        super.onDestroy()
    }

    private val refreshUiRunnable = object : Runnable {
        override fun run() {
            renderDashboard()
            ui.postDelayed(this, 500)
        }
    }

    private fun buildDashboard() = DashboardUi(
        activity = this,
        onSelectDevice = {
            if (phase.get() == MonitorSessionPhase.IDLE) {
                startActivityForResult(Intent(this, DevicePickerActivity::class.java), REQUEST_DEVICE)
            }
        },
        onStart = { requestStart() },
        onEnd = { requestEnd() }
    ).also { dashboard = it }.root

    private fun refreshControls() {
        dashboard.setControlState(MonitorSessionPolicy.controls(phase.get()))
    }

    private fun setPhase(value: MonitorSessionPhase) {
        phase.set(value)
        notifyPhaseChanged()
    }

    private fun notifyPhaseChanged() {
        if (destroying.get()) return
        ui.post { if (::dashboard.isInitialized) refreshControls() }
        renderDashboard()
    }

    private fun advancePhase(expected: MonitorSessionPhase, next: MonitorSessionPhase) {
        if (stopRequested.get() || !phase.compareAndSet(expected, next)) {
            throw SessionCancelledException()
        }
        if (destroying.get()) throw SessionCancelledException()
        notifyPhaseChanged()
    }

    private fun loadSavedDevice() {
        val preferences = getSharedPreferences("probe", MODE_PRIVATE)
        deviceAddress = preferences.getString("address", null)
        deviceName = preferences.getString("name", null)
    }

    @Deprecated("Deprecated in Android framework; retained to avoid another runtime dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_DEVICE -> if (resultCode == RESULT_OK && phase.get() == MonitorSessionPhase.IDLE) {
                deviceName = data?.getStringExtra("name")
                deviceAddress = data?.getStringExtra("address")
                getSharedPreferences("probe", MODE_PRIVATE).edit()
                    .putString("name", deviceName)
                    .putString("address", deviceAddress)
                    .apply()
                lastError = "NONE"
                lastNotice = "已选择 ${deviceName ?: "OBD"}"
                renderDashboard()
            }
            REQUEST_SAVE_LOG -> {
                val archive = pendingManualArchive
                pendingManualArchive = null
                val destination = data?.data
                if (resultCode == RESULT_OK && archive != null && destination != null) {
                    saveToChosenDestination(archive, destination)
                } else if (archive != null) {
                    pendingArchive = archive
                    lastError = "公共保存已取消；内部 ZIP 仍保留"
                    setPhase(MonitorSessionPhase.SAVE_FAILED)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_RUNTIME_PERMISSIONS || !pendingPermissionStart.compareAndSet(true, false)) return
        if (phase.get() != MonitorSessionPhase.WAITING_PERMISSION || stopRequested.get()) {
            setPhase(MonitorSessionPhase.IDLE)
            return
        }
        if (!hasBluetoothPermission()) {
            lastError = "缺少蓝牙连接权限"
            setPhase(MonitorSessionPhase.IDLE)
            return
        }
        beginSession(MonitorSessionPhase.WAITING_PERMISSION)
    }

    private fun requestStart() {
        if (phase.get() != MonitorSessionPhase.IDLE) return
        if (deviceAddress == null) {
            lastError = "请先选择 OBD 设备"
            lastNotice = null
            renderDashboard()
            return
        }
        stopRequested.set(false)
        val missing = missingStartPermissions()
        if (missing.isNotEmpty()) {
            if (!phase.compareAndSet(MonitorSessionPhase.IDLE, MonitorSessionPhase.WAITING_PERMISSION)) return
            pendingPermissionStart.set(true)
            if (missing.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                getSharedPreferences("probe", MODE_PRIVATE).edit()
                    .putBoolean(STORAGE_PERMISSION_ASKED, true)
                    .apply()
            }
            refreshControls()
            renderDashboard()
            requestPermissions(missing.toTypedArray(), REQUEST_RUNTIME_PERMISSIONS)
            return
        }
        beginSession(MonitorSessionPhase.IDLE)
    }

    private fun beginSession(expected: MonitorSessionPhase) {
        endRequestedFromLive.set(false)
        endRequestedAtWallMs.set(0L)
        if (!phase.compareAndSet(expected, MonitorSessionPhase.CONNECTING)) return
        val address = deviceAddress ?: run {
            setPhase(MonitorSessionPhase.IDLE)
            return
        }
        val name = deviceName ?: "Unknown"
        stopRequested.set(false)
        lastError = "NONE"
        lastNotice = null
        refreshControls()
        renderDashboard()
        worker.execute { runOwnedSession(name, address) }
    }

    private fun requestEnd() {
        while (true) {
            val current = phase.get()
            when (current) {
                MonitorSessionPhase.WAITING_PERMISSION -> {
                    if (!phase.compareAndSet(current, MonitorSessionPhase.IDLE)) continue
                    pendingPermissionStart.set(false)
                    stopRequested.set(true)
                    lastNotice = "已取消开始"
                    notifyPhaseChanged()
                    return
                }
                MonitorSessionPhase.CONNECTING,
                MonitorSessionPhase.INITIALIZING,
                MonitorSessionPhase.LIVE -> {
                    if (!phase.compareAndSet(current, MonitorSessionPhase.STOPPING)) continue
                    if (current == MonitorSessionPhase.LIVE) endRequestedFromLive.set(true)
                    endRequestedAtWallMs.compareAndSet(0L, System.currentTimeMillis())
                    stopRequested.set(true)
                    liveMode.set(false)
                    notifyPhaseChanged()
                    closeElmAsync()
                    return
                }
                MonitorSessionPhase.SAVE_FAILED -> {
                    retrySave()
                    return
                }
                else -> return
            }
        }
    }

    private fun closeElmAsync() {
        runCatching {
            connectionCancelWorker.execute { runCatching { elm.close() } }
        }
    }

    private fun runOwnedSession(sessionDeviceName: String, sessionDeviceAddress: String) {
        val ran = PROCESS_VEHICLE_SESSION_LEASE.withCancellableLease(
            shouldContinue = { !stopRequested.get() && !destroying.get() }
        ) {
            try {
                runOwnedSessionWithLease(sessionDeviceName, sessionDeviceAddress)
            } finally {
                runCatching { elm.close() }
            }
        }
        if (!ran && !destroying.get()) {
            lastNotice = "已取消开始"
            setPhase(MonitorSessionPhase.IDLE)
        }
    }

    private fun runOwnedSessionWithLease(sessionDeviceName: String, sessionDeviceAddress: String) {
        var enteredLive = false
        var completionKind = LogCompletionKind.COMPLETED
        var completionReason = "USER_END"
        try {
            resetSessionRuntime()
            logger.start(sessionDeviceName, sessionDeviceAddress)
            logger.logEvent("START_REQUEST")
            requireContinue()

            establishConnection(sessionDeviceAddress, sessionDeviceName)
            requireContinue()

            advancePhase(MonitorSessionPhase.CONNECTING, MonitorSessionPhase.INITIALIZING)
            configureRuntimeAdapter()
            requireContinue()

            advancePhase(MonitorSessionPhase.INITIALIZING, MonitorSessionPhase.LIVE)
            enteredLive = true
            liveMode.set(true)
            logger.logEvent("LIVE_START")
            requireContinue()
            runLiveScheduler(sessionDeviceAddress, sessionDeviceName)
            if (stopRequested.get()) {
                completionReason = "USER_END"
            } else {
                completionKind = LogCompletionKind.INTERRUPTED
                completionReason = "LIVE_ENDED_UNEXPECTEDLY"
                lastError = "实时监控意外结束；日志将作为中断记录保存"
            }
        } catch (_: SessionCancelledException) {
            val reachedLive = enteredLive || endRequestedFromLive.get()
            if (!reachedLive) completionKind = LogCompletionKind.INTERRUPTED
            completionReason = if (reachedLive) "USER_END" else "USER_END_BEFORE_LIVE"
        } catch (e: Exception) {
            val reachedLive = enteredLive || endRequestedFromLive.get()
            if (stopRequested.get()) {
                if (!reachedLive) completionKind = LogCompletionKind.INTERRUPTED
                completionReason = if (reachedLive) "USER_END" else "USER_END_BEFORE_LIVE"
            } else {
                safeLogError(if (reachedLive) "LIVE_MODE_ERROR" else "START_ERROR", e)
                lastError = if (reachedLive) "实时监控中断: ${e.message}" else "开始失败: ${e.message}"
                completionKind = if (reachedLive) LogCompletionKind.INTERRUPTED else LogCompletionKind.START_FAILED
                completionReason = if (reachedLive) "LIVE_ERROR" else "START_FAILED"
            }
        } finally {
            liveMode.set(false)
            elm.close()
            currentHeader = null
            if (destroying.get()) return
            val reachedLive = enteredLive || endRequestedFromLive.get()
            if (logger.state == SessionState.ACTIVE || logger.state == SessionState.FINALIZE_FAILED) {
                setPhase(MonitorSessionPhase.SAVING)
                retryCompletionKind = completionKind
                retryReason = completionReason
                try {
                    if (logger.state == SessionState.ACTIVE) {
                        endRequestedAtWallMs.get().takeIf { it > 0L }?.let {
                            logger.logEvent("END_REQUEST", "wall_time_ms=$it")
                        }
                        if (reachedLive) logger.logEvent("LIVE_STOP", completionReason)
                        logger.logEvent("SESSION_END", completionReason)
                    }
                    val archive = logger.finalizeAndZip(completionKind, completionReason)
                    publishArchive(archive, offerUserDestinationOnFailure = true)
                } catch (e: Exception) {
                    lastError = "保存失败: ${e.message}"
                    pendingArchive = null
                    setPhase(MonitorSessionPhase.SAVE_FAILED)
                }
            } else {
                setPhase(MonitorSessionPhase.IDLE)
            }
        }
    }

    private fun resetSessionRuntime() {
        synchronized(signalLock) {
            store.clear()
            idleCheckState.reset()
            lastIdleCheckLogged = null
        }
        scheduler.startRun(SystemClock.elapsedRealtime())
        latencyWindow.clear()
        signalUpdateCounter.set(0L)
        noDataCount.set(0L)
        timeoutCount.set(0L)
        busErrorCount.set(0L)
        performanceTracker.reset()
        reconnectCount = 0
        consecutiveErrors = 0
        currentHeader = null
        renderDashboard()
    }

    private fun establishConnection(address: String, name: String) {
        val adapter = bluetoothManager().adapter ?: error("Bluetooth unavailable")
        val device = adapter.getRemoteDevice(address)
        elm.connect(device) { !stopRequested.get() && !destroying.get() }
        requireContinue()
        logger.logConnection("BLUETOOTH_CONNECTED name=$name")

        val initialization = elm.initialize { !stopRequested.get() }
        for (result in initialization) {
            record(null, result)
            requireOk(result, "adapter initialization")
            requireContinue()
        }
        if (initialization.size != 8) requireContinue()

        for (command in listOf("ATI", "STI", "AT@1", "ATDP", "ATDPN", "ATRV")) {
            requireContinue()
            val result = elm.command(command, 6000, 300)
            record(null, result)
            if (command == "ATRV") updateAdapterVoltage(result)
            if (result.status != TransactionStatus.OK) {
                logger.logConnection("IDENTITY_QUERY_WARNING command=$command status=${result.status}")
            }
        }
        currentHeader = null
    }

    private fun configureRuntimeAdapter() {
        for (command in listOf("ATSP6", "ATAT1", "ATH1", "ATL0", "ATS0", "ATCAF1", "ATAL")) {
            requireContinue()
            val result = elm.command(command, 5000, 250)
            record(null, result)
            requireOk(result, "runtime profile")
        }
        currentHeader = null
        logger.logConnection("RUNTIME_PROFILE_CONFIGURED profile=${ProbeLogger.PROFILE_VERSION}")
        logger.forceCheckpoint("RUNTIME_PROFILE_READY")
    }

    private fun requireOk(result: CommandResult, stage: String) {
        if (result.status != TransactionStatus.OK) {
            error("$stage rejected ${result.command} (${result.status})")
        }
    }

    private fun requireContinue() {
        if (stopRequested.get() || destroying.get()) throw SessionCancelledException()
    }

    private fun runLiveScheduler(sessionAddress: String, sessionName: String) {
        logger.logConnection("LIVE_MODE_START scheduler=${ProbeLogger.SCHEDULER_PROFILE}")
        scheduler.startRun(SystemClock.elapsedRealtime())
        latencyWindow.clear()
        performanceTracker.reset()
        var nextFrameLog = 0L
        var lastMetricSampleMs = SystemClock.elapsedRealtime()
        var lastExecutions = 0L
        var lastSignalUpdates = 0L
        val due = IntArray(RequestTable.requests.size)

        while (liveMode.get() && !stopRequested.get()) {
            if (!elm.isConnected()) {
                if (!attemptReconnect(sessionAddress, sessionName)) break
                scheduler.reset(SystemClock.elapsedRealtime())
                nextFrameLog = 0L
            }
            val cycleStart = SystemClock.elapsedRealtime()
            try {
                synchronized(signalLock) { store.refreshStaleStates(SystemClock.elapsedRealtime()) }
                val now = SystemClock.elapsedRealtime()
                val dueCount = scheduler.dueRequests(now, due)
                for (i in 0 until dueCount) {
                    requireContinue()
                    val request = RequestTable.requests[due[i]]
                    val requestStart = SystemClock.elapsedRealtime()
                    val result = executeScheduled(request)
                    latencyWindow.add(SystemClock.elapsedRealtime() - requestStart)
                    when (result.status) {
                        TransactionStatus.NO_DATA -> noDataCount.incrementAndGet()
                        TransactionStatus.TIMEOUT -> timeoutCount.incrementAndGet()
                        TransactionStatus.BUS_ERROR -> busErrorCount.incrementAndGet()
                        else -> Unit
                    }
                    scheduler.markExecuted(due[i], SystemClock.elapsedRealtime())
                    if (result.status == TransactionStatus.TIMEOUT || result.status == TransactionStatus.BUS_ERROR) {
                        throw IOException("${request.header} ${request.command} ${result.status}")
                    }
                }
                updateIdleCheckState()
                val nowAfter = SystemClock.elapsedRealtime()
                if (nowAfter >= nextFrameLog) {
                    val logStart = SystemClock.elapsedRealtime()
                    synchronized(signalLock) { logger.logFrame(baseline, hybrid) }
                    lastFrameLogMs = SystemClock.elapsedRealtime() - logStart
                    nextFrameLog = nowAfter + 1000L
                }
                consecutiveErrors = 0
            } catch (_: SessionCancelledException) {
                break
            } catch (e: Exception) {
                if (stopRequested.get() || !liveMode.get()) break
                consecutiveErrors++
                lastError = "POLL: ${e.message}"
                safeLogError("LIVE_POLL_ERROR count=$consecutiveErrors", e)
                if (consecutiveErrors >= 3) elm.close()
            }
            if (!liveMode.get() || stopRequested.get()) break
            val cycleDuration = SystemClock.elapsedRealtime() - cycleStart
            val nowAfterCycle = SystemClock.elapsedRealtime()
            if (nowAfterCycle - lastMetricSampleMs >= 5000L) {
                val deltaMs = (nowAfterCycle - lastMetricSampleMs).coerceAtLeast(1L)
                val executions = scheduler.executions
                val signalUpdates = signalUpdateCounter.get()
                val metrics = PerformanceTracker.SchedulerMetrics(
                    requestHz = (executions - lastExecutions) * 1000.0 / deltaMs,
                    signalUpdateHz = (signalUpdates - lastSignalUpdates) * 1000.0 / deltaMs,
                    deadlineMisses = scheduler.deadlineMisses,
                    skippedOverdue = scheduler.skippedOverdue,
                    latencyP50Ms = latencyWindow.percentile(0.50),
                    latencyP95Ms = latencyWindow.percentile(0.95),
                    latencyP99Ms = latencyWindow.percentile(0.99),
                    noData = noDataCount.get(),
                    timeout = timeoutCount.get(),
                    busError = busErrorCount.get()
                )
                logger.logPerformance(
                    performanceTracker.sample(
                        cycleDuration,
                        lastRenderDurationMs,
                        lastFrameLogMs,
                        metrics,
                        logger.takeTimingSample()
                    )
                )
                lastMetricSampleMs = nowAfterCycle
                lastExecutions = executions
                lastSignalUpdates = signalUpdates
            }
            val wakeMs = scheduler.nextWakeMs(SystemClock.elapsedRealtime())
            sleepWhileRunning((wakeMs - SystemClock.elapsedRealtime()).coerceIn(5L, 250L))
        }
        logger.logConnection("LIVE_MODE_STOP")
    }

    private fun ensureHeader(header: String) {
        if (currentHeader == header) return
        val result = elm.command("ATSH$header", 4000, 120, 80)
        record(null, result)
        requireOk(result, "header $header")
        currentHeader = header
    }

    private fun executeScheduled(request: ScheduledRequest): CommandResult {
        request.header?.let { ensureHeader(it) }
        val result = elm.command(
            request.command,
            request.timeoutMs,
            request.minimumGapMs,
            request.quietWindowMs,
            request.preDrainMs
        )
        record(request.header, result)
        if (result.status != TransactionStatus.TIMEOUT && result.status != TransactionStatus.BUS_ERROR) {
            decodeScheduled(request, result)
        }
        return result
    }

    private fun decodeScheduled(request: ScheduledRequest, result: CommandResult) {
        when (request.id) {
            "std_core" -> {
                val decoded = ObdParsers.decodeStandard(result.rawLines, RESPONSE_ENGINE)
                if (decoded == null) {
                    markDecodeFailure(listOf(baseline.rpm, baseline.speedKph), request.command, result)
                    return
                }
                applyStandard(request.command, result, decoded)
            }
            "coolant" -> {
                val decoded = ObdParsers.decodeStandard(result.rawLines, RESPONSE_ENGINE)
                updateSignal(baseline.coolantC, decoded?.coolantC, request.command, result)
                decoded?.coolantC?.let {
                    logger.logDecoded(
                        "coolant_c",
                        it,
                        "C",
                        request.command,
                        payloadHex(result, RESPONSE_ENGINE),
                        ObdParsers.DECODER_VERSION
                    )
                }
            }
            "cd_f3" -> {
                val decoded = ObdParsers.decode21CdF3(result.rawLines)
                if (decoded == null) {
                    markDecodeFailure(listOf(hybrid.iceTorqueNm), request.command, result)
                    return
                }
                updateSignal(hybrid.iceTorqueNm, decoded.iceTorqueNm, request.command, result)
                logger.logDecoded(
                    "ice_torque_nm",
                    decoded.iceTorqueNm,
                    "Nm",
                    request.command,
                    decoded.rawDataHex,
                    ObdParsers.DECODER_VERSION
                )
            }
            "c3" -> {
                val decoded = ObdParsers.decode21C3(result.rawLines)
                if (decoded == null) {
                    markDecodeFailure(
                        listOf(hybrid.socPct, hybrid.hvVoltageV, hybrid.hvCurrentA, hybrid.hvPowerKw),
                        request.command,
                        result
                    )
                    return
                }
                applyC3(request.command, result, decoded)
            }
            "c4" -> {
                val decoded = ObdParsers.decode21C4(result.rawLines)
                if (decoded == null) {
                    markDecodeFailure(listOf(hybrid.warmupActive), request.command, result)
                    return
                }
                updateSignal(hybrid.warmupActive, decoded.warmupActive, request.command, result)
                logger.logDecoded(
                    "warmup_active",
                    decoded.warmupActive,
                    null,
                    request.command,
                    decoded.rawDataHex,
                    ObdParsers.DECODER_VERSION
                )
            }
            "cf" -> {
                val decoded = ObdParsers.decode21CF(result.rawLines)
                if (decoded == null) {
                    markDecodeFailure(
                        listOf(
                            hybrid.batteryTempsC,
                            hybrid.batteryTempMinC,
                            hybrid.batteryTempMaxC,
                            hybrid.batteryTempAvgC
                        ),
                        request.command,
                        result
                    )
                    return
                }
                synchronized(signalLock) {
                    store.update(hybrid.batteryTempsC, decoded.batteryTempsC, request.command, result)
                    store.update(hybrid.batteryTempMinC, decoded.batteryTempMinC, request.command, result)
                    store.update(hybrid.batteryTempMaxC, decoded.batteryTempMaxC, request.command, result)
                    store.update(hybrid.batteryTempAvgC, decoded.batteryTempAvgC, request.command, result)
                    signalUpdateCounter.addAndGet(4L)
                }
                logger.logDecoded(
                    "battery_temps_c",
                    decoded.batteryTempsC,
                    "C",
                    request.command,
                    decoded.rawDataHex,
                    ObdParsers.DECODER_VERSION
                )
                logger.logDecoded(
                    "battery_temp_min_c",
                    decoded.batteryTempMinC,
                    "C",
                    request.command,
                    decoded.rawDataHex,
                    ObdParsers.DECODER_VERSION
                )
                logger.logDecoded(
                    "battery_temp_max_c",
                    decoded.batteryTempMaxC,
                    "C",
                    request.command,
                    decoded.rawDataHex,
                    ObdParsers.DECODER_VERSION
                )
                logger.logDecoded(
                    "battery_temp_avg_c",
                    decoded.batteryTempAvgC,
                    "C",
                    request.command,
                    decoded.rawDataHex,
                    ObdParsers.DECODER_VERSION
                )
            }
            "atrv" -> updateAdapterVoltage(result)
        }
    }

    private fun applyStandard(command: String, result: CommandResult, decoded: StandardDecoded) {
        val raw = payloadHex(result, RESPONSE_ENGINE)
        var updates = 0L
        synchronized(signalLock) {
            decoded.rpm?.let { store.update(baseline.rpm, it, command, result); updates++ }
            decoded.speedKph?.let { store.update(baseline.speedKph, it, command, result); updates++ }
            decoded.coolantC?.let { store.update(baseline.coolantC, it, command, result); updates++ }
            signalUpdateCounter.addAndGet(updates)
        }
        decoded.rpm?.let {
            logger.logDecoded("rpm", it, "rpm", command, raw, ObdParsers.DECODER_VERSION)
        }
        decoded.speedKph?.let {
            logger.logDecoded("speed_kph", it, "km/h", command, raw, ObdParsers.DECODER_VERSION)
        }
        decoded.coolantC?.let {
            logger.logDecoded("coolant_c", it, "C", command, raw, ObdParsers.DECODER_VERSION)
        }
    }

    private fun applyC3(command: String, result: CommandResult, decoded: ToyotaC3Decoded) {
        synchronized(signalLock) {
            store.update(hybrid.socPct, decoded.socPct, command, result)
            store.update(hybrid.hvVoltageV, decoded.hvVoltageV, command, result)
            store.update(hybrid.hvCurrentA, decoded.hvCurrentA, command, result)
            store.update(hybrid.hvPowerKw, decoded.hvPowerKw, command, result)
            signalUpdateCounter.addAndGet(4L)
        }
        logger.logDecoded("soc_pct", decoded.socPct, "%", command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
        logger.logDecoded(
            "hv_voltage_v",
            decoded.hvVoltageV,
            "V",
            command,
            decoded.rawDataHex,
            ObdParsers.DECODER_VERSION
        )
        logger.logDecoded(
            "hv_current_a",
            decoded.hvCurrentA,
            "A",
            command,
            decoded.rawDataHex,
            ObdParsers.DECODER_VERSION
        )
        logger.logDecoded(
            "hv_power_kw",
            decoded.hvPowerKw,
            "kW",
            command,
            decoded.rawDataHex,
            ObdParsers.DECODER_VERSION
        )
    }

    private fun updateAdapterVoltage(result: CommandResult) {
        val value = ObdParsers.adapterVoltage(result.rawLines)
        updateSignal(baseline.adapterVoltageV, value, "ATRV", result)
        value?.let {
            logger.logDecoded(
                "adapter_12v_v",
                it,
                "V",
                "ATRV",
                result.rawLines.joinToString(" | "),
                ObdParsers.DECODER_VERSION
            )
        }
    }

    private fun <T> updateSignal(signal: SignalValue<T>, value: T?, command: String, result: CommandResult) {
        synchronized(signalLock) { store.update(signal, value, command, result) }
        signalUpdateCounter.incrementAndGet()
    }

    private fun markDecodeFailure(signals: List<SignalValue<*>>, command: String, result: CommandResult) {
        synchronized(signalLock) { store.markDecodeFailure(signals, command, result) }
    }

    private fun payloadHex(result: CommandResult, canId: String): String =
        ObdParsers.isoTpMessage(result.rawLines, canId)?.payloadHex ?: result.normalizedHex

    private fun attemptReconnect(sessionAddress: String, sessionName: String): Boolean {
        val delays = longArrayOf(1000, 2000, 5000, 10_000, 30_000)
        var attempt = 0
        while (liveMode.get() && !stopRequested.get()) {
            reconnectCount++
            val wait = delays[attempt.coerceAtMost(delays.lastIndex)]
            logger.logConnection("RECONNECT_WAIT count=$reconnectCount delay_ms=$wait")
            if (!sleepWhileRunning(wait)) return false
            try {
                establishConnection(sessionAddress, sessionName)
                configureRuntimeAdapter()
                consecutiveErrors = 0
                logger.logConnection("RECONNECT_SUCCESS total_attempts=$reconnectCount")
                logger.logEvent("RECONNECT_SUCCESS", reconnectCount.toString())
                return true
            } catch (_: SessionCancelledException) {
                return false
            } catch (e: Exception) {
                if (stopRequested.get() || !liveMode.get()) return false
                safeLogError("RECONNECT_FAILED attempt=${attempt + 1}", e)
                elm.close()
                attempt++
            }
        }
        return false
    }

    private fun sleepWhileRunning(durationMs: Long): Boolean {
        val end = SystemClock.elapsedRealtime() + durationMs.coerceAtLeast(0L)
        while (liveMode.get() && !stopRequested.get()) {
            val remaining = end - SystemClock.elapsedRealtime()
            if (remaining <= 0L) return true
            try {
                Thread.sleep(remaining.coerceAtMost(100L))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }

    private fun publishArchive(archive: PendingLogArchive, offerUserDestinationOnFailure: Boolean) {
        val commit = logger.publishAndMark(archive) { publicLogExporter.publish(archive) }
        val result = commit.result
        if (result.success && commit.receiptWritten) {
            completePublishedArchive(result)
            return
        }
        if (result.success) {
            pendingArchive = archive
            pendingPublicationReceipt = result
            lastError = "公共日志已保存，但内部保存回执失败；请按结束重试"
            lastNotice = "已保存 ${result.displayName}"
            setPhase(MonitorSessionPhase.SAVE_FAILED)
            return
        }
        pendingArchive = archive
        pendingPublicationReceipt = null
        lastError = "公共保存失败: ${result.error}; 内部 ZIP 已保留"
        setPhase(MonitorSessionPhase.SAVE_FAILED)
        if (offerUserDestinationOnFailure && result.needsUserDestination) {
            ui.post { launchSaveDocument(archive) }
        }
    }

    private fun retrySave() {
        val archive = pendingArchive
        val receipt = pendingPublicationReceipt
        if (archive != null && receipt != null) {
            setPhase(MonitorSessionPhase.SAVING)
            worker.execute {
                val commit = logger.publishAndMark(archive) { receipt }
                if (commit.result.success && commit.receiptWritten) {
                    completePublishedArchive(commit.result)
                } else {
                    pendingArchive = archive
                    pendingPublicationReceipt = receipt
                    lastError = "公共日志已保存，但内部保存回执失败；请按结束重试"
                    setPhase(MonitorSessionPhase.SAVE_FAILED)
                }
            }
            return
        }
        if (archive != null) {
            setPhase(MonitorSessionPhase.SAVING)
            worker.execute { publishArchive(archive, offerUserDestinationOnFailure = true) }
            return
        }
        if (logger.state == SessionState.FINALIZE_FAILED) {
            setPhase(MonitorSessionPhase.SAVING)
            worker.execute {
                try {
                    val rebuilt = logger.finalizeAndZip(retryCompletionKind, retryReason)
                    publishArchive(rebuilt, offerUserDestinationOnFailure = true)
                } catch (e: Exception) {
                    lastError = "保存重试失败: ${e.message}"
                    setPhase(MonitorSessionPhase.SAVE_FAILED)
                }
            }
        }
    }

    private fun launchSaveDocument(archive: PendingLogArchive) {
        if (destroying.get()) return
        pendingManualArchive = archive
        try {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                    putExtra(Intent.EXTRA_TITLE, archive.displayName)
                },
                REQUEST_SAVE_LOG
            )
        } catch (e: Exception) {
            pendingManualArchive = null
            pendingArchive = archive
            lastError = "无法打开系统保存位置: ${e.message}"
            setPhase(MonitorSessionPhase.SAVE_FAILED)
        }
    }

    private fun saveToChosenDestination(archive: PendingLogArchive, destination: Uri) {
        setPhase(MonitorSessionPhase.SAVING)
        worker.execute {
            val commit = logger.publishAndMark(archive) {
                publicLogExporter.publishToUri(archive, destination)
            }
            val result = commit.result
            if (result.success && commit.receiptWritten) {
                completePublishedArchive(result)
            } else if (result.success) {
                pendingArchive = archive
                pendingPublicationReceipt = result
                lastError = "日志已保存，但内部保存回执失败"
                setPhase(MonitorSessionPhase.SAVE_FAILED)
            } else {
                pendingArchive = archive
                pendingPublicationReceipt = null
                lastError = "所选位置保存失败: ${result.error}"
                setPhase(MonitorSessionPhase.SAVE_FAILED)
            }
        }
    }

    private fun recoverPendingLogs() {
        setPhase(MonitorSessionPhase.SAVING)
        worker.execute {
            try {
                val archives = logger.recoverInterruptedSessions()
                if (archives.isEmpty()) {
                    setPhase(MonitorSessionPhase.IDLE)
                    return@execute
                }
                var failed: PendingLogArchive? = null
                var recovered = 0
                for (archive in archives) {
                    val commit = logger.publishAndMark(archive) { publicLogExporter.publish(archive) }
                    val result = commit.result
                    if (result.success && commit.receiptWritten) {
                        recovered++
                    } else if (result.success && failed == null) {
                        failed = archive
                        pendingPublicationReceipt = result
                        lastError = "日志已保存，但内部保存回执失败"
                    } else if (failed == null) {
                        failed = archive
                        pendingPublicationReceipt = null
                        lastError = "恢复日志公共保存失败: ${result.error}"
                    }
                }
                if (failed != null) {
                    pendingArchive = failed
                    setPhase(MonitorSessionPhase.SAVE_FAILED)
                } else {
                    lastNotice = "已恢复并保存 $recovered 份日志"
                    lastError = "NONE"
                    setPhase(MonitorSessionPhase.IDLE)
                }
            } catch (e: Exception) {
                lastError = "日志恢复失败: ${e.message}"
                setPhase(MonitorSessionPhase.IDLE)
            }
        }
    }

    private fun completePublishedArchive(result: PublicLogResult) {
        pendingArchive = null
        pendingPublicationReceipt = null
        lastError = "NONE"
        lastNotice = "已保存 ${result.displayName}"
        setPhase(MonitorSessionPhase.IDLE)
    }

    private fun record(header: String?, result: CommandResult) = logger.logTransaction(header, result)

    private fun safeLogError(message: String, throwable: Throwable) {
        runCatching { logger.logError(message, throwable) }
    }

    private fun renderDashboard() = ui.post {
        if (destroying.get()) return@post
        if (!::dashboard.isInitialized) return@post
        val renderStart = SystemClock.elapsedRealtime()
        val snapshot = synchronized(signalLock) {
            val rpmFresh = baseline.rpm.status == SignalStatus.VALID
            val batteryTempFresh = hybrid.batteryTempAvgC.status == SignalStatus.VALID
            val icePower = mechanicalPowerKw(baseline.rpm.value, hybrid.iceTorqueNm.value)
            DashboardSnapshot(
                speedKph = baseline.speedKph.value,
                speedFresh = baseline.speedKph.status == SignalStatus.VALID,
                speedVersion = baseline.speedKph.version,
                socPct = hybrid.socPct.value,
                socFresh = hybrid.socPct.status == SignalStatus.VALID,
                socVersion = hybrid.socPct.version,
                batteryTempMinC = hybrid.batteryTempMinC.value,
                batteryTempMaxC = hybrid.batteryTempMaxC.value,
                batteryTempAvgC = hybrid.batteryTempAvgC.value,
                batteryTempFresh = batteryTempFresh,
                batteryTempVersion = hybrid.batteryTempMinC.version +
                    hybrid.batteryTempMaxC.version + hybrid.batteryTempAvgC.version,
                hvPowerKw = hybrid.hvPowerKw.value,
                hvPowerFresh = hybrid.hvPowerKw.status == SignalStatus.VALID,
                hvPowerVersion = hybrid.hvPowerKw.version,
                rpm = baseline.rpm.value,
                rpmFresh = rpmFresh,
                rpmVersion = baseline.rpm.version,
                coolantC = baseline.coolantC.value,
                coolantFresh = baseline.coolantC.status == SignalStatus.VALID,
                coolantVersion = baseline.coolantC.version,
                adapterVoltageV = baseline.adapterVoltageV.value,
                adapterVoltageFresh = baseline.adapterVoltageV.status == SignalStatus.VALID,
                adapterVoltageVersion = baseline.adapterVoltageV.version,
                icePowerKw = icePower,
                icePowerFresh = rpmFresh && hybrid.iceTorqueNm.status == SignalStatus.VALID,
                icePowerVersion = baseline.rpm.version + hybrid.iceTorqueNm.version,
                idleCheckActive = hybrid.idleCheckActive.value == true,
                idleCheckVersion = hybrid.idleCheckActive.version
            )
        }
        dashboard.render(snapshot)
        dashboard.setControlState(MonitorSessionPolicy.controls(phase.get()))
        val connection = if (elm.isConnected()) "CONNECTED" else "OFFLINE"
        val logging = when (logger.state) {
            SessionState.ACTIVE -> if (logger.isDegraded()) "LOG!" else "LOG"
            SessionState.FINALIZING -> "PACKING"
            SessionState.FINALIZED -> "SAVED"
            SessionState.FINALIZE_FAILED -> "LOG ERROR"
            else -> "NO LOG"
        }
        dashboard.renderStatus(
            DashboardStatus(
                deviceName = deviceName ?: "OBD",
                connection = connection,
                mode = MonitorSessionPolicy.modeCode(phase.get()),
                logging = logging,
                reconnectCount = reconnectCount,
                notice = lastNotice,
                error = lastError.takeUnless { it == "NONE" },
                warning = logger.isDegraded() || lastError != "NONE"
            )
        )
        lastRenderDurationMs = SystemClock.elapsedRealtime() - renderStart
    }

    private fun mechanicalPowerKw(rpm: Double?, torqueNm: Double?): Double? {
        if (rpm == null || torqueNm == null) return null
        return torqueNm * 2.0 * Math.PI * rpm / 60.0 / 1000.0
    }

    private fun updateIdleCheckState() {
        var transition: Boolean? = null
        synchronized(signalLock) {
            val rpm = baseline.rpm
            val torque = hybrid.iceTorqueNm
            val warmup = hybrid.warmupActive
            val speed = baseline.speedKph
            val fresh = rpm.status == SignalStatus.VALID &&
                torque.status == SignalStatus.VALID &&
                warmup.status == SignalStatus.VALID &&
                speed.status == SignalStatus.VALID
            if (!fresh) {
                idleCheckState.reset()
                store.setDerived(hybrid.idleCheckActive, null, "IDLE_CHECK")
                return@synchronized
            }
            val icePower = mechanicalPowerKw(rpm.value, torque.value)
            idleCheckState.update(
                warmup.value,
                rpm.value,
                icePower,
                speed.value,
                SystemClock.elapsedRealtime()
            )
            val active = idleCheckState.active
            store.setDerived(hybrid.idleCheckActive, active, "IDLE_CHECK")
            if (lastIdleCheckLogged != active) {
                lastIdleCheckLogged = active
                transition = active
            }
        }
        transition?.let { active ->
            logger.logDecoded(
                "idle_check_active",
                active,
                null,
                "IDLE_CHECK",
                "",
                ObdParsers.DECODER_VERSION
            )
            logger.logEvent(if (active) "IDLE_CHECK_ACTIVE" else "IDLE_CHECK_INACTIVE")
        }
    }

    private fun missingStartPermissions(): List<String> {
        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission()) {
            missing += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
            !getSharedPreferences("probe", MODE_PRIVATE).getBoolean(STORAGE_PERMISSION_ASKED, false)
        ) {
            missing += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        return missing
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun bluetoothManager(): BluetoothManager = getSystemService(BluetoothManager::class.java)

    private class SessionCancelledException : Exception()
}
