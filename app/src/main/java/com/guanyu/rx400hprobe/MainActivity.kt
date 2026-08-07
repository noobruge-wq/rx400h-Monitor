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

        // Deliberately unchanged from the verified V0.1.8 runtime.
        private const val CORE_CYCLE_MS = 800L
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
    private val baseline = BaselineData()
    private val hybrid = HybridData()

    private var reconnectCount = 0
    private var consecutiveErrors = 0
    private var lastError = "NONE"

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
            refreshStaleStates()
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
        logger.logConnection("LIVE_MODE_START cycle_target_ms=$CORE_CYCLE_MS scheduler=${ProbeLogger.SCHEDULER_PROFILE}")
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
                ensureHeader(HEADER_ENGINE)
                pollStandardCore()
                var now = SystemClock.elapsedRealtime()
                if (now >= nextCoolant) {
                    pollCoolantBlock()
                    nextCoolant = now + 3000L
                }
                now = SystemClock.elapsedRealtime()
                if (now >= nextCdF3) {
                    pollCdF3()
                    nextCdF3 = now + 1000L
                }
                now = SystemClock.elapsedRealtime()
                if (now >= nextVoltage) {
                    pollAdapterVoltage()
                    nextVoltage = now + 3000L
                }

                ensureHeader(HEADER_HYBRID)
                pollC3()
                now = SystemClock.elapsedRealtime()
                if (now >= nextC4) {
                    pollC4()
                    nextC4 = now + 1500L
                }
                now = SystemClock.elapsedRealtime()
                if (now >= nextCf) {
                    pollCf()
                    nextCf = now + 5000L
                }
                now = SystemClock.elapsedRealtime()
                if (now >= nextFrameLog) {
                    logger.logFrame(baseline, hybrid)
                    nextFrameLog = now + 1000L
                }
                consecutiveErrors = 0
            } catch (e: Exception) {
                consecutiveErrors++
                lastError = "POLL: ${e.message}"
                safeLogError("LIVE_POLL_ERROR count=$consecutiveErrors", e)
                if (consecutiveErrors >= 3) elm.close()
            }
            val remaining = CORE_CYCLE_MS - (SystemClock.elapsedRealtime() - cycleStart)
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

    private fun sendData(header: String, command: String, timeoutMs: Long): CommandResult {
        val result = elm.command(command, timeoutMs, 120, 80)
        record(header, result)
        if (result.status == TransactionStatus.TIMEOUT || result.status == TransactionStatus.BUS_ERROR) {
            throw IOException("$header $command ${result.status}")
        }
        return result
    }

    private fun pollStandardCore() {
        val command = "01040C0D0E10 2"
        val result = sendData(HEADER_ENGINE, command, 5000)
        val decoded = ObdParsers.decodeStandard(result.rawLines, RESPONSE_ENGINE)
        if (decoded == null) {
            markDecodeFailure(listOf(baseline.rpm, baseline.speedKph, baseline.engineLoadPct, baseline.ignitionTimingDeg, baseline.mafGps), command, result)
            return
        }
        applyStandard(command, result, decoded)
    }

    private fun pollCoolantBlock() {
        val command = "01050607 1"
        val result = sendData(HEADER_ENGINE, command, 5000)
        val decoded = ObdParsers.decodeStandard(result.rawLines, RESPONSE_ENGINE)
        updateSignal(baseline.coolantC, decoded?.coolantC, command, result)
        decoded?.coolantC?.let { logger.logDecoded("coolant_c", it, "C", command, payloadHex(result, RESPONSE_ENGINE), ObdParsers.DECODER_VERSION) }
    }

    private fun pollAdapterVoltage() {
        val result = elm.command("ATRV", 4000, 120, 80)
        record(null, result)
        updateAdapterVoltage(result)
    }

    private fun pollCdF3() {
        val command = "21CDF3 3"
        val result = sendData(HEADER_ENGINE, command, 5000)
        val decoded = ObdParsers.decode21CdF3(result.rawLines)
        if (decoded == null) {
            markDecodeFailure(listOf(hybrid.iceTorqueNm, hybrid.injectionUl), command, result)
            return
        }
        updateSignal(hybrid.iceTorqueNm, decoded.iceTorqueNm, command, result)
        updateSignal(hybrid.injectionUl, decoded.injectionUl, command, result)
        logger.logDecoded("ice_torque_nm", decoded.iceTorqueNm, "Nm", command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
        logger.logDecoded("injection_ul", decoded.injectionUl, "uL", command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
    }

    private fun pollC3() {
        val command = "21C3 6"
        val result = sendData(HEADER_HYBRID, command, 6000)
        val decoded = ObdParsers.decode21C3(result.rawLines)
        if (decoded == null) {
            markDecodeFailure(listOf(hybrid.socPct, hybrid.hvVoltageV, hybrid.hvCurrentA, hybrid.hvPowerKw, hybrid.mg1Rpm, hybrid.mg2Rpm), command, result)
            return
        }
        applyC3(command, result, decoded)
    }

    private fun pollC4() {
        val command = "21C4 5"
        val result = sendData(HEADER_HYBRID, command, 6000)
        val decoded = ObdParsers.decode21C4(result.rawLines)
        if (decoded == null) {
            markDecodeFailure(listOf(hybrid.rearMgRpm, hybrid.rearMgTorqueNm, hybrid.warmupActive), command, result)
            return
        }
        updateSignal(hybrid.rearMgRpm, decoded.rearMgRpm, command, result)
        updateSignal(hybrid.rearMgTorqueNm, decoded.rearMgTorqueNm, command, result)
        updateSignal(hybrid.warmupActive, decoded.warmupActive, command, result)
        logger.logDecoded("rear_mg_rpm", decoded.rearMgRpm, "rpm", command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
        logger.logDecoded("rear_mg_torque_nm", decoded.rearMgTorqueNm, "Nm", command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
        logger.logDecoded("warmup_active", decoded.warmupActive, null, command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
    }

    private fun pollCf() {
        val command = "21CF 4"
        val result = sendData(HEADER_HYBRID, command, 6000)
        val decoded = ObdParsers.decode21CF(result.rawLines)
        if (decoded == null) {
            markDecodeFailure(listOf(hybrid.batteryTempsC, hybrid.batteryTempMinC, hybrid.batteryTempMaxC, hybrid.batteryTempAvgC), command, result)
            return
        }
        updateSignal(hybrid.batteryTempsC, decoded.batteryTempsC, command, result)
        updateSignal(hybrid.batteryTempMinC, decoded.batteryTempMinC, command, result)
        updateSignal(hybrid.batteryTempMaxC, decoded.batteryTempMaxC, command, result)
        updateSignal(hybrid.batteryTempAvgC, decoded.batteryTempAvgC, command, result)
        logger.logDecoded("battery_temps_c", decoded.batteryTempsC, "C", command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
        logger.logDecoded("battery_temp_min_c", decoded.batteryTempMinC, "C", command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
        logger.logDecoded("battery_temp_max_c", decoded.batteryTempMaxC, "C", command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
        logger.logDecoded("battery_temp_avg_c", decoded.batteryTempAvgC, "C", command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
    }

    private fun applyStandard(command: String, result: CommandResult, decoded: StandardDecoded) {
        val raw = payloadHex(result, RESPONSE_ENGINE)
        decoded.rpm?.let { updateSignal(baseline.rpm, it, command, result); logger.logDecoded("rpm", it, "rpm", command, raw, ObdParsers.DECODER_VERSION) }
        decoded.speedKph?.let { updateSignal(baseline.speedKph, it, command, result); logger.logDecoded("speed_kph", it, "km/h", command, raw, ObdParsers.DECODER_VERSION) }
        decoded.coolantC?.let { updateSignal(baseline.coolantC, it, command, result); logger.logDecoded("coolant_c", it, "C", command, raw, ObdParsers.DECODER_VERSION) }
        decoded.engineLoadPct?.let { updateSignal(baseline.engineLoadPct, it, command, result); logger.logDecoded("engine_load_pct", it, "%", command, raw, ObdParsers.DECODER_VERSION) }
        decoded.timingDeg?.let { updateSignal(baseline.ignitionTimingDeg, it, command, result); logger.logDecoded("ignition_timing_deg", it, "deg", command, raw, ObdParsers.DECODER_VERSION) }
        decoded.mafGps?.let { updateSignal(baseline.mafGps, it, command, result); logger.logDecoded("maf_gps", it, "g/s", command, raw, ObdParsers.DECODER_VERSION) }
    }

    private fun applyC3(command: String, result: CommandResult, decoded: ToyotaC3Decoded) {
        updateSignal(hybrid.socPct, decoded.socPct, command, result)
        updateSignal(hybrid.hvVoltageV, decoded.hvVoltageV, command, result)
        updateSignal(hybrid.hvCurrentA, decoded.hvCurrentA, command, result)
        updateSignal(hybrid.hvPowerKw, decoded.hvPowerKw, command, result)
        updateSignal(hybrid.mg1Rpm, decoded.mg1Rpm, command, result)
        updateSignal(hybrid.mg2Rpm, decoded.mg2Rpm, command, result)
        updateSignal(hybrid.mg1TorqueNm, decoded.mg1TorqueNm, command, result)
        updateSignal(hybrid.mg2TorqueNm, decoded.mg2TorqueNm, command, result)
        updateSignal(hybrid.brakeRegenTorqueCandidate, decoded.brakeRegenTorqueCandidate, command, result)
        updateSignal(hybrid.brakeMasterTorqueCandidate, decoded.brakeMasterTorqueCandidate, command, result)
        val values = listOf(
            Triple("soc_pct", decoded.socPct, "%"), Triple("hv_voltage_v", decoded.hvVoltageV, "V"),
            Triple("hv_current_a", decoded.hvCurrentA, "A"), Triple("hv_power_kw", decoded.hvPowerKw, "kW"),
            Triple("mg1_rpm", decoded.mg1Rpm, "rpm"), Triple("mg2_rpm", decoded.mg2Rpm, "rpm"),
            Triple("mg1_torque_nm", decoded.mg1TorqueNm, "Nm"), Triple("mg2_torque_nm", decoded.mg2TorqueNm, "Nm"),
            Triple("brake_regen_torque_candidate", decoded.brakeRegenTorqueCandidate, "raw"),
            Triple("brake_master_torque_candidate", decoded.brakeMasterTorqueCandidate, "raw")
        )
        for ((name, value, unit) in values) logger.logDecoded(name, value, unit, command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
    }

    private fun updateAdapterVoltage(result: CommandResult) {
        val value = ObdParsers.adapterVoltage(result.rawLines)
        updateSignal(baseline.adapterVoltageV, value, "ATRV", result)
        value?.let { logger.logDecoded("adapter_12v_v", it, "V", "ATRV", result.rawLines.joinToString(" | "), ObdParsers.DECODER_VERSION) }
    }

    private fun <T> updateSignal(signal: SignalValue<T>, value: T?, command: String, result: CommandResult) {
        signal.source = command
        if (value != null) {
            signal.value = value
            signal.updatedAtElapsedMs = SystemClock.elapsedRealtime()
            signal.status = SignalStatus.VALID
        } else {
            signal.status = resultToSignalStatus(result)
        }
    }

    private fun markDecodeFailure(signals: List<SignalValue<*>>, command: String, result: CommandResult) {
        signals.forEach { signal -> signal.source = command; signal.status = resultToSignalStatus(result) }
    }

    private fun resultToSignalStatus(result: CommandResult): SignalStatus = when (result.status) {
        TransactionStatus.NO_DATA -> SignalStatus.NO_DATA
        TransactionStatus.INTERRUPTED -> SignalStatus.INTERRUPTED
        TransactionStatus.TIMEOUT -> SignalStatus.TIMEOUT
        else -> SignalStatus.DECODE_ERROR
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

    private fun refreshStaleStates() {
        val now = SystemClock.elapsedRealtime()
        listOf(
            baseline.rpm, baseline.speedKph, baseline.coolantC, baseline.adapterVoltageV,
            baseline.engineLoadPct, baseline.ignitionTimingDeg, baseline.mafGps,
            hybrid.socPct, hybrid.hvVoltageV, hybrid.hvCurrentA, hybrid.hvPowerKw,
            hybrid.mg1Rpm, hybrid.mg2Rpm, hybrid.iceTorqueNm, hybrid.warmupActive
        ).forEach { markStale(it, now, 5000L) }
        listOf(hybrid.batteryTempsC, hybrid.batteryTempMinC, hybrid.batteryTempMaxC, hybrid.batteryTempAvgC)
            .forEach { markStale(it, now, 12_000L) }
    }

    private fun markStale(signal: SignalValue<*>, now: Long, thresholdMs: Long) {
        val updated = signal.updatedAtElapsedMs ?: return
        if (signal.value != null && now - updated > thresholdMs) signal.status = SignalStatus.STALE
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
        val engineState = engineStateLabel()
        val warmupText = when (hybrid.warmupActive.value) { true -> "WARMUP ACTIVE"; false -> "WARMUP OFF"; null -> "WARMUP —" }
        dashboard.render(
            DashboardSnapshot(
                baseline.speedKph.value, baseline.speedKph.status == SignalStatus.VALID,
                hybrid.socPct.value, hybrid.socPct.status == SignalStatus.VALID,
                hybrid.batteryTempMinC.value, hybrid.batteryTempMaxC.value, hybrid.batteryTempAvgC.value,
                hybrid.batteryTempMaxC.status == SignalStatus.VALID,
                hybrid.hvPowerKw.value, hybrid.hvPowerKw.status == SignalStatus.VALID,
                baseline.rpm.value, baseline.rpm.status == SignalStatus.VALID,
                baseline.coolantC.value, baseline.coolantC.status == SignalStatus.VALID,
                baseline.adapterVoltageV.value, baseline.adapterVoltageV.status == SignalStatus.VALID,
                engineState, warmupText,
                mechanicalPowerKw(baseline.rpm.value, hybrid.iceTorqueNm.value),
                mechanicalPowerKw(hybrid.mg1Rpm.value, hybrid.mg1TorqueNm.value),
                mechanicalPowerKw(hybrid.mg2Rpm.value, hybrid.mg2TorqueNm.value),
                mechanicalPowerKw(hybrid.rearMgRpm.value, hybrid.rearMgTorqueNm.value)
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
    }

    private fun engineStateLabel(): String {
        val rpm = baseline.rpm.value ?: return "STATE UNKNOWN"
        val torque = hybrid.iceTorqueNm.value
        return when {
            torque != null && torque < 0.0 && rpm > 0.0 -> "ICE SPINNING"
            rpm >= 800.0 -> "ICE RUNNING"
            else -> "EV / ICE OFF"
        }
    }

    private fun mechanicalPowerKw(rpm: Double?, torqueNm: Double?): Double? {
        if (rpm == null || torqueNm == null) return null
        return torqueNm * 2.0 * Math.PI * rpm / 60.0 / 1000.0
    }

    private fun setControlsEnabled(enabled: Boolean) = ui.post { dashboard.setControlsEnabled(enabled, liveMode.get()) }
}
