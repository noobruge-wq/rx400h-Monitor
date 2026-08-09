package com.guanyu.rx400hprobe

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.core.content.FileProvider
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_DEVICE = 100
        private const val REQUEST_BLUETOOTH = 20
        private const val HEADER_ENGINE = "7E0"
        private const val RESPONSE_ENGINE = "7E8"
        private const val HEADER_HYBRID = "7E2"
        private const val RESPONSE_HYBRID = "7EA"
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val elm = Elm327Client()
    private lateinit var logger: ProbeLogger
    private lateinit var dashboard: DashboardUi

    private val busy = AtomicBoolean(false)
    private val liveMode = AtomicBoolean(false)

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
    @Volatile
    private var lastRenderDurationMs = 0L
    private var lastPerfSampleMs = 0L
    private var lastLoggerWriteMs = 0L
    private var lastIdleCheckLogged: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        logger = ProbeLogger(this)
        setContentView(buildDashboard())
        loadSavedDevice()
        renderDashboard()
        ui.post(refreshUiRunnable)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setContentView(buildDashboard())
        renderDashboard()
    }

    override fun onDestroy() {
        liveMode.set(false)
        elm.close()
        logger.stop()
        ui.removeCallbacksAndMessages(null)
        worker.shutdownNow()
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
        onSelectDevice = { startActivityForResult(Intent(this, DevicePickerActivity::class.java), REQUEST_DEVICE) },
        onConnectToggle = { if (elm.isConnected()) disconnect() else connectSelected() },
        onLiveToggle = { toggleLiveMode() },
        onExport = { finishAndShareLogs() }
    ).also { dashboard = it }.root

    private fun loadSavedDevice() {
        val preferences = getSharedPreferences("probe", MODE_PRIVATE)
        deviceAddress = preferences.getString("address", null)
        deviceName = preferences.getString("name", null)
    }

    @Deprecated("Deprecated in Android framework; kept to avoid adding another activity dependency during protocol freeze")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEVICE && resultCode == RESULT_OK) {
            deviceName = data?.getStringExtra("name")
            deviceAddress = data?.getStringExtra("address")
            getSharedPreferences("probe", MODE_PRIVATE).edit()
                .putString("name", deviceName)
                .putString("address", deviceAddress)
                .apply()
            renderDashboard()
        }
    }

    private fun ensurePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), REQUEST_BLUETOOTH)
            return false
        }
        return true
    }

    private fun bluetoothManager(): BluetoothManager = getSystemService(BluetoothManager::class.java)

    private fun connectSelected() {
        if (!ensurePermission() || busy.get()) return
        val address = deviceAddress ?: run {
            lastError = "NO DEVICE SELECTED"
            renderDashboard()
            return
        }
        busy.set(true)
        setControlsEnabled(false)
        worker.execute {
            try {
                establishConnection(address, newSession = logger.state != SessionState.ACTIVE)
                lastError = "NONE"
            } catch (e: Exception) {
                safeLogError("CONNECT_ERROR", e)
                lastError = "CONNECT: ${e.message}"
                elm.close()
            } finally {
                busy.set(false)
                setControlsEnabled(true)
                renderDashboard()
            }
        }
    }

    private fun establishConnection(address: String, newSession: Boolean) {
        val adapter = bluetoothManager().adapter ?: error("Bluetooth unavailable")
        val device = adapter.getRemoteDevice(address)
        elm.connect(device)
        if (newSession || logger.currentSessionDir() == null || logger.state != SessionState.ACTIVE) {
            logger.start(deviceName ?: "Unknown", address)
        }
        logger.logConnection("BLUETOOTH_CONNECTED name=${deviceName ?: "Unknown"}")
        elm.initialize().forEach { record(null, it) }
        for (command in listOf("ATI", "STI", "AT@1", "ATDP", "ATDPN", "ATRV")) {
            val result = elm.command(command, 6000, 300)
            record(null, result)
            if (command == "ATRV") updateAdapterVoltage(result)
        }
        currentHeader = null
    }

    private fun disconnect() {
        liveMode.set(false)
        logger.logEvent("DISCONNECT_REQUEST")
        logger.logConnection("DISCONNECT_REQUEST")
        elm.close()
        currentHeader = null
        ui.post { dashboard.setLiveButton(false) }
        renderDashboard()
    }

    private fun toggleLiveMode() {
        if (liveMode.get()) {
            liveMode.set(false)
            logger.logEvent("LIVE_STOP_REQUEST")
            ui.post { dashboard.setLiveButton(false) }
            return
        }
        if (!elm.isConnected()) {
            lastError = "OBD NOT CONNECTED"
            renderDashboard()
            return
        }
        if (!busy.compareAndSet(false, true)) return
        liveMode.set(true)
        logger.logEvent("LIVE_START")
        ui.post { dashboard.setLiveButton(true) }
        worker.execute {
            try {
                configureRuntimeAdapter()
                runLiveScheduler()
            } catch (e: Exception) {
                safeLogError("LIVE_MODE_ERROR", e)
                lastError = "LIVE: ${e.message}"
            } finally {
                liveMode.set(false)
                busy.set(false)
                ui.post { dashboard.setLiveButton(false) }
                setControlsEnabled(true)
                renderDashboard()
            }
        }
    }

    private fun configureRuntimeAdapter() {
        for (command in listOf("ATSP6", "ATAT1", "ATH1", "ATL0", "ATS0", "ATCAF1", "ATAL")) {
            val result = elm.command(command, 5000, 250)
            record(null, result)
            if (result.status == TransactionStatus.COMMAND_ERROR || result.status == TransactionStatus.TIMEOUT) {
                error("Adapter rejected $command (${result.status})")
            }
        }
        currentHeader = null
        logger.logConnection("RUNTIME_PROFILE_CONFIGURED profile=${ProbeLogger.PROFILE_VERSION}")
    }

    private fun runLiveScheduler() {
        logger.logConnection("LIVE_MODE_START cycle_target_ms=${RequestTable.CORE_CYCLE_MS} scheduler=${ProbeLogger.SCHEDULER_PROFILE}")
        var nextCoolant = 0L
        var nextVoltage = 0L
        var nextCdF3 = 0L
        var nextC4 = 0L
        var nextCf = 0L
        var nextFrameLog = 0L

        while (liveMode.get()) {
            if (!elm.isConnected()) {
                if (!attemptReconnect()) break
                nextCoolant = 0L
                nextVoltage = 0L
                nextCdF3 = 0L
                nextC4 = 0L
                nextCf = 0L
            }
            val cycleStart = SystemClock.elapsedRealtime()
            try {
                store.refreshStaleStates(SystemClock.elapsedRealtime())
                ensureHeader(HEADER_ENGINE)
                pollStandardCore()
                var now = SystemClock.elapsedRealtime()
                if (now >= nextCoolant) {
                    pollCoolantBlock()
                    nextCoolant = now + RequestTable.period("coolant")
                }
                now = SystemClock.elapsedRealtime()
                if (now >= nextCdF3) {
                    pollCdF3()
                    nextCdF3 = now + RequestTable.period("cd_f3")
                }
                now = SystemClock.elapsedRealtime()
                if (now >= nextVoltage) {
                    pollAdapterVoltage()
                    nextVoltage = now + RequestTable.period("atrv")
                }

                ensureHeader(HEADER_HYBRID)
                pollC3()
                now = SystemClock.elapsedRealtime()
                if (now >= nextC4) {
                    pollC4()
                    nextC4 = now + RequestTable.period("c4")
                }
                now = SystemClock.elapsedRealtime()
                if (now >= nextCf) {
                    pollCf()
                    nextCf = now + RequestTable.period("cf")
                }
                now = SystemClock.elapsedRealtime()
                if (now >= nextFrameLog) {
                    val logStart = SystemClock.elapsedRealtime()
                    logger.logFrame(baseline, hybrid)
                    lastLoggerWriteMs = SystemClock.elapsedRealtime() - logStart
                    nextFrameLog = now + 1000L
                }
                updateIdleCheckState()
                consecutiveErrors = 0
            } catch (e: Exception) {
                consecutiveErrors++
                lastError = "POLL: ${e.message}"
                safeLogError("LIVE_POLL_ERROR count=$consecutiveErrors", e)
                if (consecutiveErrors >= 3) elm.close()
            }
            val cycleDuration = SystemClock.elapsedRealtime() - cycleStart
            val nowAfterCycle = SystemClock.elapsedRealtime()
            if (nowAfterCycle - lastPerfSampleMs >= 5000L) {
                val sample = performanceTracker.sample(cycleDuration, lastRenderDurationMs, lastLoggerWriteMs)
                logger.logPerformance(sample)
                lastPerfSampleMs = nowAfterCycle
            }
            val remaining = RequestTable.CORE_CYCLE_MS - cycleDuration
            if (remaining > 0L) Thread.sleep(remaining)
        }
        logger.logConnection("LIVE_MODE_STOP")
        logger.logEvent("LIVE_STOP")
    }

    private fun ensureHeader(header: String) {
        if (currentHeader == header) return
        val result = elm.command("ATSH$header", 4000, 120, 80)
        record(null, result)
        if (result.status != TransactionStatus.OK) error("Header $header failed: ${result.status}")
        currentHeader = header
    }

    private fun sendData(spec: ScheduledRequest): CommandResult {
        val result = elm.command(spec.command, spec.timeoutMs, spec.minimumGapMs, spec.quietWindowMs, spec.preDrainMs)
        record(spec.header, result)
        if (result.status == TransactionStatus.TIMEOUT || result.status == TransactionStatus.BUS_ERROR) {
            throw IOException("${spec.header} ${spec.command} ${result.status}")
        }
        return result
    }

    private fun pollStandardCore() {
        val spec = RequestTable.spec("std_core")
        val result = sendData(spec)
        val decoded = ObdParsers.decodeStandard(result.rawLines, RESPONSE_ENGINE)
        if (decoded == null) {
            markDecodeFailure(listOf(baseline.rpm, baseline.speedKph), spec.command, result)
            return
        }
        applyStandard(spec.command, result, decoded)
    }

    private fun pollCoolantBlock() {
        val spec = RequestTable.spec("coolant")
        val result = sendData(spec)
        val decoded = ObdParsers.decodeStandard(result.rawLines, RESPONSE_ENGINE)
        updateSignal(baseline.coolantC, decoded?.coolantC, spec.command, result)
        decoded?.coolantC?.let { logger.logDecoded("coolant_c", it, "C", spec.command, payloadHex(result, RESPONSE_ENGINE), ObdParsers.DECODER_VERSION) }
    }

    private fun pollAdapterVoltage() {
        val spec = RequestTable.spec("atrv")
        val result = elm.command(spec.command, spec.timeoutMs, spec.minimumGapMs, spec.quietWindowMs, spec.preDrainMs)
        record(null, result)
        updateAdapterVoltage(result)
    }

    private fun pollCdF3() {
        val spec = RequestTable.spec("cd_f3")
        val result = sendData(spec)
        val decoded = ObdParsers.decode21CdF3(result.rawLines)
        if (decoded == null) {
            markDecodeFailure(listOf(hybrid.iceTorqueNm), spec.command, result)
            return
        }
        updateSignal(hybrid.iceTorqueNm, decoded.iceTorqueNm, spec.command, result)
        logger.logDecoded("ice_torque_nm", decoded.iceTorqueNm, "Nm", spec.command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
    }

    private fun pollC3() {
        val spec = RequestTable.spec("c3")
        val result = sendData(spec)
        val decoded = ObdParsers.decode21C3(result.rawLines)
        if (decoded == null) {
            markDecodeFailure(listOf(hybrid.socPct, hybrid.hvVoltageV, hybrid.hvCurrentA, hybrid.hvPowerKw), spec.command, result)
            return
        }
        applyC3(spec.command, result, decoded)
    }

    private fun pollC4() {
        val spec = RequestTable.spec("c4")
        val result = sendData(spec)
        val decoded = ObdParsers.decode21C4(result.rawLines)
        if (decoded == null) {
            markDecodeFailure(listOf(hybrid.warmupActive), spec.command, result)
            return
        }
        updateSignal(hybrid.warmupActive, decoded.warmupActive, spec.command, result)
        logger.logDecoded("warmup_active", decoded.warmupActive, null, spec.command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
    }

    private fun pollCf() {
        val spec = RequestTable.spec("cf")
        val result = sendData(spec)
        val decoded = ObdParsers.decode21CF(result.rawLines)
        if (decoded == null) {
            markDecodeFailure(listOf(hybrid.batteryTempsC, hybrid.batteryTempMinC, hybrid.batteryTempMaxC, hybrid.batteryTempAvgC), spec.command, result)
            return
        }
        updateSignal(hybrid.batteryTempsC, decoded.batteryTempsC, spec.command, result)
        updateSignal(hybrid.batteryTempMinC, decoded.batteryTempMinC, spec.command, result)
        updateSignal(hybrid.batteryTempMaxC, decoded.batteryTempMaxC, spec.command, result)
        updateSignal(hybrid.batteryTempAvgC, decoded.batteryTempAvgC, spec.command, result)
        logger.logDecoded("battery_temps_c", decoded.batteryTempsC, "C", spec.command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
        logger.logDecoded("battery_temp_min_c", decoded.batteryTempMinC, "C", spec.command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
        logger.logDecoded("battery_temp_max_c", decoded.batteryTempMaxC, "C", spec.command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
        logger.logDecoded("battery_temp_avg_c", decoded.batteryTempAvgC, "C", spec.command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
    }

    private fun applyStandard(command: String, result: CommandResult, decoded: StandardDecoded) {
        val raw = payloadHex(result, RESPONSE_ENGINE)
        decoded.rpm?.let { updateSignal(baseline.rpm, it, command, result); logger.logDecoded("rpm", it, "rpm", command, raw, ObdParsers.DECODER_VERSION) }
        decoded.speedKph?.let { updateSignal(baseline.speedKph, it, command, result); logger.logDecoded("speed_kph", it, "km/h", command, raw, ObdParsers.DECODER_VERSION) }
        decoded.coolantC?.let { updateSignal(baseline.coolantC, it, command, result); logger.logDecoded("coolant_c", it, "C", command, raw, ObdParsers.DECODER_VERSION) }
    }

    private fun applyC3(command: String, result: CommandResult, decoded: ToyotaC3Decoded) {
        updateSignal(hybrid.socPct, decoded.socPct, command, result)
        updateSignal(hybrid.hvVoltageV, decoded.hvVoltageV, command, result)
        updateSignal(hybrid.hvCurrentA, decoded.hvCurrentA, command, result)
        updateSignal(hybrid.hvPowerKw, decoded.hvPowerKw, command, result)
        val values = listOf(
            Triple("soc_pct", decoded.socPct, "%"), Triple("hv_voltage_v", decoded.hvVoltageV, "V"),
            Triple("hv_current_a", decoded.hvCurrentA, "A"), Triple("hv_power_kw", decoded.hvPowerKw, "kW")
        )
        for ((name, value, unit) in values) logger.logDecoded(name, value, unit, command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
    }

    private fun updateAdapterVoltage(result: CommandResult) {
        val value = ObdParsers.adapterVoltage(result.rawLines)
        updateSignal(baseline.adapterVoltageV, value, "ATRV", result)
        value?.let { logger.logDecoded("adapter_12v_v", it, "V", "ATRV", result.rawLines.joinToString(" | "), ObdParsers.DECODER_VERSION) }
    }

    private fun <T> updateSignal(signal: SignalValue<T>, value: T?, command: String, result: CommandResult) {
        store.update(signal, value, command, result)
    }

    private fun markDecodeFailure(signals: List<SignalValue<*>>, command: String, result: CommandResult) {
        store.markDecodeFailure(signals, command, result)
    }

    private fun payloadHex(result: CommandResult, canId: String): String =
        ObdParsers.isoTpMessage(result.rawLines, canId)?.payloadHex ?: result.normalizedHex

    private fun attemptReconnect(): Boolean {
        val address = deviceAddress ?: return false
        val delays = longArrayOf(1000, 2000, 5000, 10_000, 30_000)
        var attempt = 0
        while (liveMode.get()) {
            reconnectCount++
            val wait = delays[attempt.coerceAtMost(delays.lastIndex)]
            logger.logConnection("RECONNECT_WAIT count=$reconnectCount delay_ms=$wait")
            Thread.sleep(wait)
            if (!liveMode.get()) return false
            try {
                establishConnection(address, newSession = false)
                configureRuntimeAdapter()
                consecutiveErrors = 0
                logger.logConnection("RECONNECT_SUCCESS total_attempts=$reconnectCount")
                logger.logEvent("RECONNECT_SUCCESS", reconnectCount.toString())
                return true
            } catch (e: Exception) {
                safeLogError("RECONNECT_FAILED attempt=${attempt + 1}", e)
                elm.close()
                attempt++
            }
        }
        return false
    }

    private fun finishAndShareLogs() {
        val session = logger.currentSessionDir() ?: run {
            lastError = "NO ACTIVE SESSION"
            renderDashboard()
            return
        }
        liveMode.set(false)
        setControlsEnabled(false)
        worker.execute {
            try {
                logger.logEvent("SESSION_END")
                logger.logConnection("END_AND_SHARE_CLICKED dir=${session.name}")
                val zip = logger.finalizeAndZip()
                elm.close()
                currentHeader = null
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", zip)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "RX400h Monitor research logs ${zip.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ui.post { setControlsEnabled(true); startActivity(Intent.createChooser(share, "发送全部测试日志")) }
            } catch (e: Exception) {
                lastError = "EXPORT: ${e.message}"
                ui.post { setControlsEnabled(true); renderDashboard() }
            }
        }
    }

    private fun record(header: String?, result: CommandResult) = logger.logTransaction(header, result)
    private fun safeLogError(message: String, throwable: Throwable) { try { logger.logError(message, throwable) } catch (_: Exception) {} }

    private fun renderDashboard() = ui.post {
        val renderStart = SystemClock.elapsedRealtime()
        val rpmFresh = baseline.rpm.status == SignalStatus.VALID
        val socFresh = hybrid.socPct.status == SignalStatus.VALID
        val batteryTempFresh = hybrid.batteryTempAvgC.status == SignalStatus.VALID
        val hvPowerFresh = hybrid.hvPowerKw.status == SignalStatus.VALID
        val coolantFresh = baseline.coolantC.status == SignalStatus.VALID
        val voltageFresh = baseline.adapterVoltageV.status == SignalStatus.VALID
        val icePower = mechanicalPowerKw(baseline.rpm.value, hybrid.iceTorqueNm.value)
        val icePowerFresh = rpmFresh && hybrid.iceTorqueNm.status == SignalStatus.VALID
        val icePowerVersion = maxOf(baseline.rpm.version, hybrid.iceTorqueNm.version)
        val batteryTempVersion = maxOf(
            hybrid.batteryTempMinC.version,
            hybrid.batteryTempMaxC.version,
            hybrid.batteryTempAvgC.version
        )
        dashboard.render(
            DashboardSnapshot(
                speedKph = baseline.speedKph.value,
                speedFresh = baseline.speedKph.status == SignalStatus.VALID,
                speedVersion = baseline.speedKph.version,
                socPct = hybrid.socPct.value,
                socFresh = socFresh,
                socVersion = hybrid.socPct.version,
                batteryTempMinC = hybrid.batteryTempMinC.value,
                batteryTempMaxC = hybrid.batteryTempMaxC.value,
                batteryTempAvgC = hybrid.batteryTempAvgC.value,
                batteryTempFresh = batteryTempFresh,
                batteryTempVersion = batteryTempVersion,
                hvPowerKw = hybrid.hvPowerKw.value,
                hvPowerFresh = hvPowerFresh,
                hvPowerVersion = hybrid.hvPowerKw.version,
                rpm = baseline.rpm.value,
                rpmFresh = rpmFresh,
                rpmVersion = baseline.rpm.version,
                coolantC = baseline.coolantC.value,
                coolantFresh = coolantFresh,
                coolantVersion = baseline.coolantC.version,
                adapterVoltageV = baseline.adapterVoltageV.value,
                adapterVoltageFresh = voltageFresh,
                adapterVoltageVersion = baseline.adapterVoltageV.version,
                icePowerKw = icePower,
                icePowerFresh = icePowerFresh,
                icePowerVersion = icePowerVersion,
                idleCheckActive = hybrid.idleCheckActive.value == true,
                idleCheckVersion = hybrid.idleCheckActive.version
            )
        )
        val connection = if (elm.isConnected()) "CONNECTED" else "OFFLINE"
        val mode = when { liveMode.get() -> "LIVE"; busy.get() -> "BUSY"; else -> "IDLE" }
        val logging = when (logger.state) {
            SessionState.ACTIVE -> if (logger.isDegraded()) "LOG!" else "LOG"
            SessionState.FINALIZING -> "PACKING"
            SessionState.FINALIZED -> "SAVED"
            SessionState.FINALIZE_FAILED -> "LOG ERROR"
            else -> "NO LOG"
        }
        dashboard.renderStatus(
            DashboardStatus(
                deviceName ?: "OBD", connection, mode, logging, reconnectCount,
                lastError.takeUnless { it == "NONE" }, logger.isDegraded() || lastError != "NONE"
            )
        )
        lastRenderDurationMs = SystemClock.elapsedRealtime() - renderStart
    }

    private fun mechanicalPowerKw(rpm: Double?, torqueNm: Double?): Double? {
        if (rpm == null || torqueNm == null) return null
        return torqueNm * 2.0 * Math.PI * rpm / 60.0 / 1000.0
    }

    private fun updateIdleCheckState() {
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
            return
        }
        val icePower = mechanicalPowerKw(rpm.value, torque.value)
        idleCheckState.update(warmup.value, rpm.value, icePower, speed.value, SystemClock.elapsedRealtime())
        val active = idleCheckState.active
        store.setDerived(hybrid.idleCheckActive, active, "IDLE_CHECK")
        if (lastIdleCheckLogged != active) {
            logger.logDecoded("idle_check_active", active, null, "IDLE_CHECK", "", ObdParsers.DECODER_VERSION)
            logger.logEvent(if (active) "IDLE_CHECK_ACTIVE" else "IDLE_CHECK_INACTIVE")
            lastIdleCheckLogged = active
        }
    }

    private fun setControlsEnabled(enabled: Boolean) = ui.post { dashboard.setControlsEnabled(enabled, liveMode.get()) }
}
