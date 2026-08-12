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
        RequestTable.schedulerSpecs,
        RequestTable.diagnosticCostModel
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
        val admission = CapacityAdmission.assess(
            RequestTable.schedulerSpecs,
            RequestTable.diagnosticCostModel
        )
        val runMode = if (admission.state == AdmissionState.ADMITTED) {
            SchedulerRunMode.NORMAL
        } else {
            SchedulerRunMode.DIAGNOSTIC_BEST_EFFORT
        }
        val liveEpochMs = SystemClock.elapsedRealtime()
        scheduler.startRun(liveEpochMs, admission, runMode)
        logger.setSchedulerRunMetadata(
            admissionState = admission.state.name,
            runMode = runMode.name,
            costModelId = admission.modelId,
            costSource = admission.sourceEvidenceId,
            requestUtilization = admission.requestUtilization,
            projectedUtilization = admission.projectedUtilization,
            projectedMisses = admission.projectedDeadlineMisses,
            projectedCapacityRejections = admission.projectedCapacityRejections
        )
        logger.logSchedulerEvent(
            eventType = "ADMISSION",
            outcome = admission.state.name,
            reason = admission.reason,
            admissionState = admission.state.name
        )
        logger.forceCheckpoint("SCHEDULER_ADMISSION")
        logger.logConnection(
            "LIVE_MODE_START scheduler=${ProbeLogger.SCHEDULER_PROFILE} " +
                "admission=${admission.state} mode=$runMode model=${admission.modelId}"
        )
        latencyWindow.clear()
        performanceTracker.reset()
        var nextFrameLog = 0L
        var lastMetricSampleMs = liveEpochMs
        var lastExecutions = 0L
        var lastSignalUpdates = 0L

        while (liveMode.get() && !stopRequested.get()) {
            if (!elm.isConnected()) {
                val downAtMs = SystemClock.elapsedRealtime()
                scheduler.transportDown(downAtMs)
                logger.logSchedulerEvent(
                    eventType = "TRANSPORT_DOWN",
                    completedAtMs = downAtMs,
                    outcome = SchedulerOutcome.TRANSPORT_UNAVAILABLE.name,
                    reason = SchedulerTerminalReason.TRANSPORT_DOWN.name
                )
                drainSchedulerTerminalEvents()
                if (!attemptReconnect(sessionAddress, sessionName)) break
                val upAtMs = SystemClock.elapsedRealtime()
                currentHeader = null
                scheduler.transportUp(upAtMs)
                logger.logSchedulerEvent(eventType = "TRANSPORT_UP", completedAtMs = upAtMs)
                drainSchedulerTerminalEvents()
                nextFrameLog = 0L
            }
            val cycleStart = SystemClock.elapsedRealtime()
            var sleepUntilMs: Long? = null
            var successfulPoll = false
            try {
                synchronized(signalLock) { store.refreshStaleStates(SystemClock.elapsedRealtime()) }
                val now = SystemClock.elapsedRealtime()
                when (val decision = scheduler.next(now, currentHeader)) {
                    is SchedulerDecision.TerminalBatch -> logSchedulerTerminal(decision)
                    is SchedulerDecision.SleepUntil -> sleepUntilMs = decision.timeMs
                    is SchedulerDecision.ChangeHeader -> {
                        requireContinue()
                        val request = RequestTable.requests[decision.specIndex]
                        logger.logSchedulerEvent(
                            eventType = "HEADER_DISPATCH",
                            requestId = request.id,
                            requestHeader = request.header,
                            releaseSequence = decision.job.token.sequence,
                            releaseCount = 1L,
                            releaseAtMs = decision.job.releaseAtMs,
                            deadlineAtMs = decision.job.deadlineAtMs,
                            dispatchAtMs = decision.dispatchAtMs,
                            predictedSetupMs = decision.predictedSetupMs,
                            fromHeader = decision.fromHeader,
                            toHeader = decision.toHeader
                        )
                        val setupStart = SystemClock.elapsedRealtime()
                        try {
                            ensureHeader(decision.toHeader)
                            val completedAtMs = SystemClock.elapsedRealtime()
                            val actualSetupMs = completedAtMs - setupStart
                            scheduler.completeHeader(decision.token, completedAtMs, actualSetupMs)
                            logger.logSchedulerEvent(
                                eventType = "HEADER_COMPLETION",
                                requestId = request.id,
                                requestHeader = request.header,
                                releaseSequence = decision.job.token.sequence,
                                releaseCount = 1L,
                                outcome = "COMPLETED",
                                releaseAtMs = decision.job.releaseAtMs,
                                deadlineAtMs = decision.job.deadlineAtMs,
                                dispatchAtMs = decision.dispatchAtMs,
                                completedAtMs = completedAtMs,
                                predictedSetupMs = decision.predictedSetupMs,
                                actualSetupMs = actualSetupMs,
                                fromHeader = decision.fromHeader,
                                toHeader = decision.toHeader
                            )
                        } catch (failure: Exception) {
                            val failedAtMs = SystemClock.elapsedRealtime()
                            val actualSetupMs = failedAtMs - setupStart
                            scheduler.failHeader(decision.token, failedAtMs, actualSetupMs)
                            currentHeader = null
                            logger.logSchedulerEvent(
                                eventType = "HEADER_FAILED",
                                requestId = request.id,
                                requestHeader = request.header,
                                releaseSequence = decision.job.token.sequence,
                                releaseCount = 1L,
                                outcome = "FAILED_NON_TERMINAL",
                                reason = failure::class.java.simpleName,
                                releaseAtMs = decision.job.releaseAtMs,
                                deadlineAtMs = decision.job.deadlineAtMs,
                                dispatchAtMs = decision.dispatchAtMs,
                                completedAtMs = failedAtMs,
                                predictedSetupMs = decision.predictedSetupMs,
                                actualSetupMs = actualSetupMs,
                                fromHeader = decision.fromHeader,
                                toHeader = decision.toHeader
                            )
                            throw failure
                        }
                    }
                    is SchedulerDecision.Dispatch -> {
                        requireContinue()
                        val request = RequestTable.requests[decision.specIndex]
                        logger.logSchedulerEvent(
                            eventType = "DISPATCH",
                            requestId = request.id,
                            requestHeader = request.header,
                            releaseSequence = decision.job.token.sequence,
                            releaseCount = 1L,
                            releaseAtMs = decision.job.releaseAtMs,
                            deadlineAtMs = decision.job.deadlineAtMs,
                            dispatchAtMs = decision.dispatchAtMs,
                            queueWaitMs = (decision.dispatchAtMs - decision.job.releaseAtMs).coerceAtLeast(0L),
                            predictedSetupMs = decision.predictedSetupMs,
                            predictedServiceMs = decision.predictedServiceMs,
                            fromHeader = decision.fromHeader,
                            toHeader = decision.toHeader
                        )
                        val actualSetupMs = 0L
                        var actualServiceMs = 0L
                        var schedulerCompleted = false
                        try {
                            val serviceStart = SystemClock.elapsedRealtime()
                            val result = try {
                                executeScheduledTransaction(request)
                            } finally {
                                actualServiceMs = SystemClock.elapsedRealtime() - serviceStart
                            }
                            var decodeFailure: Exception? = null
                            if (
                                result.status != TransactionStatus.TIMEOUT &&
                                result.status != TransactionStatus.BUS_ERROR
                            ) {
                                try {
                                    decodeScheduled(request, result)
                                } catch (failure: Exception) {
                                    decodeFailure = failure
                                }
                            }
                            actualServiceMs = SystemClock.elapsedRealtime() - serviceStart
                            if (result.status == TransactionStatus.TIMEOUT) {
                                timeoutCount.incrementAndGet()
                                val failedAtMs = SystemClock.elapsedRealtime()
                                val terminal = scheduler.fail(
                                    decision.job.token,
                                    failedAtMs,
                                    SchedulerOutcome.TRANSPORT_UNAVAILABLE,
                                    SchedulerTerminalReason.TRANSPORT_DOWN
                                )
                                schedulerCompleted = true
                                logSchedulerDispatchFailure(request, decision, terminal, actualServiceMs)
                                throw IOException("${request.header} ${request.command} ${result.status}")
                            }
                            val completedAtMs = SystemClock.elapsedRealtime()
                            val completion = scheduler.complete(
                                decision.job.token,
                                completedAtMs,
                                actualSetupMs,
                                actualServiceMs
                            )
                            schedulerCompleted = true
                            latencyWindow.add(actualSetupMs + actualServiceMs)
                            logSchedulerCompletion(request, completion)
                            decodeFailure?.let { failure ->
                                lastError = "DECODE ${request.id}: ${failure.message}"
                                safeLogError("DECODE_PROCESSING_ERROR request=${request.id}", failure)
                                logger.logSchedulerEvent(
                                    eventType = "DECODE_FAILED",
                                    requestId = request.id,
                                    requestHeader = request.header,
                                    releaseSequence = decision.job.token.sequence,
                                    releaseCount = 1L,
                                    outcome = "FAILED_NON_TERMINAL",
                                    reason = failure::class.java.simpleName,
                                    releaseAtMs = decision.job.releaseAtMs,
                                    deadlineAtMs = decision.job.deadlineAtMs,
                                    dispatchAtMs = decision.dispatchAtMs,
                                    completedAtMs = completedAtMs
                                )
                            }
                            when (result.status) {
                                TransactionStatus.NO_DATA -> noDataCount.incrementAndGet()
                                TransactionStatus.BUS_ERROR -> busErrorCount.incrementAndGet()
                                else -> Unit
                            }
                            successfulPoll = result.status != TransactionStatus.TIMEOUT &&
                                result.status != TransactionStatus.BUS_ERROR
                            if (
                                result.status == TransactionStatus.BUS_ERROR
                            ) {
                                throw IOException("${request.header} ${request.command} ${result.status}")
                            }
                        } catch (cancelled: SessionCancelledException) {
                            if (!schedulerCompleted) {
                                val failedAtMs = SystemClock.elapsedRealtime()
                                val terminal = scheduler.fail(
                                    decision.job.token,
                                    failedAtMs,
                                    SchedulerOutcome.SESSION_ENDED,
                                    SchedulerTerminalReason.USER_OR_SESSION_END
                                )
                                logSchedulerDispatchFailure(request, decision, terminal, actualServiceMs)
                            }
                            throw cancelled
                        } catch (failure: Exception) {
                            if (!schedulerCompleted) {
                                val failedAtMs = SystemClock.elapsedRealtime()
                                val ending = stopRequested.get() || destroying.get() || !liveMode.get()
                                val terminal = scheduler.fail(
                                    decision.job.token,
                                    failedAtMs,
                                    if (ending) SchedulerOutcome.SESSION_ENDED else SchedulerOutcome.TRANSPORT_UNAVAILABLE,
                                    if (ending) SchedulerTerminalReason.USER_OR_SESSION_END else SchedulerTerminalReason.TRANSPORT_DOWN
                                )
                                logSchedulerDispatchFailure(request, decision, terminal, actualServiceMs)
                            }
                            throw failure
                        }
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
                if (successfulPoll) consecutiveErrors = 0
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
                val schedulerSnapshot = scheduler.snapshot(nowAfterCycle)
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
                    busError = busErrorCount.get(),
                    expiredUnexecuted = scheduler.expiredUnexecuted,
                    capacityRejections = scheduler.capacityRejections,
                    transportUnavailable = scheduler.transportUnavailableCount,
                    executedLate = scheduler.executedLate,
                    pending = scheduler.pendingCount,
                    headerSwitches = scheduler.headerSwitches,
                    admissionState = admission.state.name
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
                logSchedulerRequestStats(schedulerSnapshot)
                lastMetricSampleMs = nowAfterCycle
                lastExecutions = executions
                lastSignalUpdates = signalUpdates
            }
            sleepUntilMs?.let { wakeMs ->
                val nowBeforeSleep = SystemClock.elapsedRealtime()
                val sleepMs = if (wakeMs == Long.MAX_VALUE) {
                    250L
                } else {
                    (wakeMs - nowBeforeSleep).coerceIn(1L, 250L)
                }
                sleepWhileRunning(sleepMs)
            }
        }
        scheduler.finishRun(SystemClock.elapsedRealtime())
        drainSchedulerTerminalEvents()
        logSchedulerRequestStats(scheduler.snapshot(SystemClock.elapsedRealtime()))
        logger.logConnection("LIVE_MODE_STOP")
    }

    private fun ensureHeader(header: String) {
        if (currentHeader == header) return
        val result = elm.command("ATSH$header", 4000, 0, 0, 0)
        record(null, result)
        requireOk(result, "header $header")
        currentHeader = header
    }

    private fun executeScheduledTransaction(request: ScheduledRequest): CommandResult {
        val result = elm.command(
            request.command,
            request.timeoutMs,
            request.minimumGapMs,
            request.quietWindowMs,
            request.preDrainMs
        )
        record(request.header, result)
        return result
    }

    private fun logSchedulerCompletion(
        request: ScheduledRequest,
        completion: SchedulerCompletion
    ) {
        val dispatch = completion.dispatch
        logger.logSchedulerEvent(
            eventType = "COMPLETION",
            requestId = request.id,
            requestHeader = request.header,
            releaseSequence = dispatch.job.token.sequence,
            releaseCount = 1L,
            outcome = completion.outcome.name,
            reason = completion.reason.name,
            releaseAtMs = dispatch.job.releaseAtMs,
            deadlineAtMs = dispatch.job.deadlineAtMs,
            dispatchAtMs = dispatch.dispatchAtMs,
            completedAtMs = completion.completedAtMs,
            queueWaitMs = completion.queueWaitMs,
            latenessMs = completion.latenessMs,
            predictedSetupMs = dispatch.predictedSetupMs,
            predictedServiceMs = dispatch.predictedServiceMs,
            actualSetupMs = completion.actualSetupMs,
            actualServiceMs = completion.actualServiceMs,
            fromHeader = dispatch.fromHeader,
            toHeader = dispatch.toHeader
        )
    }

    private fun logSchedulerDispatchFailure(
        request: ScheduledRequest,
        decision: SchedulerDecision.Dispatch,
        terminal: SchedulerCompletion,
        actualServiceMs: Long
    ) {
        logger.logSchedulerEvent(
            eventType = "DISPATCH_FAILED",
            requestId = request.id,
            requestHeader = request.header,
            releaseSequence = decision.job.token.sequence,
            releaseCount = 1L,
            outcome = terminal.outcome.name,
            reason = terminal.reason.name,
            releaseAtMs = decision.job.releaseAtMs,
            deadlineAtMs = decision.job.deadlineAtMs,
            dispatchAtMs = decision.dispatchAtMs,
            completedAtMs = terminal.completedAtMs,
            queueWaitMs = terminal.queueWaitMs,
            latenessMs = terminal.latenessMs,
            predictedSetupMs = decision.predictedSetupMs,
            predictedServiceMs = decision.predictedServiceMs,
            actualSetupMs = 0L,
            actualServiceMs = actualServiceMs,
            fromHeader = decision.fromHeader,
            toHeader = currentHeader
        )
    }

    private fun logSchedulerTerminal(terminal: SchedulerDecision.TerminalBatch) {
        val request = RequestTable.requests[terminal.specIndex]
        logger.logSchedulerEvent(
            eventType = "TERMINAL_BATCH",
            requestId = request.id,
            requestHeader = request.header,
            releaseSequence = terminal.firstSequence,
            releaseCount = terminal.count,
            outcome = terminal.outcome.name,
            reason = terminal.reason.name,
            releaseAtMs = terminal.firstReleaseAtMs,
            deadlineAtMs = terminal.lastReleaseAtMs + request.deadlineMs,
            completedAtMs = terminal.recordedAtMs,
            latenessMs = (terminal.recordedAtMs - (terminal.lastReleaseAtMs + request.deadlineMs))
                .coerceAtLeast(0L)
        )
    }

    private fun drainSchedulerTerminalEvents() {
        while (true) {
            val terminal = scheduler.pollTerminal() ?: return
            logSchedulerTerminal(terminal)
        }
    }

    private fun logSchedulerRequestStats(snapshot: SchedulerSnapshot) {
        snapshot.requests.forEach { request ->
            logger.logSchedulerRequestStats(
                elapsedMs = request.elapsedMs,
                requestId = request.id,
                header = request.header,
                targetPeriodMs = request.periodMs,
                released = request.released,
                executedOnTime = request.executedOnTime,
                executedLate = request.executedLate,
                capacityRejected = request.capacityRejected,
                expiredUnexecuted = request.expiredUnexecuted,
                transportUnavailable = request.transportUnavailable,
                sessionEnded = request.sessionEnded,
                pending = request.pending,
                inFlight = request.inFlight,
                queueWaitP50Ms = request.queueWaitP50Ms,
                queueWaitP95Ms = request.queueWaitP95Ms,
                queueWaitMaxMs = request.queueWaitMaxMs,
                setupP50Ms = request.setupP50Ms,
                setupP95Ms = request.setupP95Ms,
                setupMaxMs = request.setupMaxMs,
                serviceP50Ms = request.serviceP50Ms,
                serviceP95Ms = request.serviceP95Ms,
                serviceMaxMs = request.serviceMaxMs,
                intervalP50Ms = request.intervalP50Ms,
                intervalP95Ms = request.intervalP95Ms,
                intervalMaxMs = request.intervalMaxMs,
                latenessP50Ms = request.latenessP50Ms,
                latenessP95Ms = request.latenessP95Ms,
                latenessMaxMs = request.latenessMaxMs,
                headerSwitches = request.headerSwitches,
                effectiveHz = request.effectiveHz,
                admissionState = snapshot.admission.state.name
            )
        }
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
