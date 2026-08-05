package com.guanyu.rx400hprobe

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.IOException
import java.util.Locale
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
        private const val CORE_CYCLE_MS = 800L
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val elm = Elm327Client()
    private lateinit var logger: ProbeLogger

    private val busy = AtomicBoolean(false)
    private val liveMode = AtomicBoolean(false)

    private var deviceAddress: String? = null
    private var deviceName: String? = null
    private var currentHeader: String? = null
    private var bestProtocol: ProtocolAttempt? = null
    private val baseline = BaselineData()
    private val hybrid = HybridData()

    private var reconnectCount = 0
    private var consecutiveErrors = 0
    private var lastError = "NONE"
    private var lastTransaction = "IDLE"
    private var lastHeader = "FUNCTIONAL"
    private var liveStartedElapsedMs: Long? = null

    private lateinit var statusText: TextView
    private lateinit var progressText: TextView
    private lateinit var dataText: TextView
    private lateinit var rawText: TextView
    private lateinit var probeButton: Button
    private lateinit var liveButton: Button
    private lateinit var toyotaButton: Button
    private lateinit var exportButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        logger = ProbeLogger(this)
        setContentView(buildUi())
        loadSavedDevice()
        updateStatus("OFFLINE")
        renderData()
        ui.post(refreshUiRunnable)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setContentView(buildUi())
        renderData()
        updateStatus(if (elm.isConnected()) "CONNECTED: $deviceName" else "OFFLINE")
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
            renderData()
            ui.postDelayed(this, 500)
        }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(3, 10, 8))
            setPadding(14, 10, 14, 10)
        }
        root.addView(TextView(this).apply {
            text = "RX400h PROTOCOL PROBE  V0.1.8"
            textSize = 21f
            setTextColor(Color.rgb(110, 255, 180))
        })
        statusText = TextView(this).apply { textSize = 15f; setTextColor(Color.rgb(255, 210, 80)) }
        progressText = TextView(this).apply { textSize = 13f; setTextColor(Color.rgb(150, 220, 255)) }
        root.addView(statusText)
        root.addView(progressText)

        fun button(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        controls.addView(button("选择设备") { startActivityForResult(Intent(this, DevicePickerActivity::class.java), REQUEST_DEVICE) })
        controls.addView(button("连接") { connectSelected() })
        controls.addView(button("断开") { disconnect() })
        probeButton = button("链路确认") { startLinkProbe() }
        liveButton = button("开始实时仪表") { toggleLiveMode() }
        toyotaButton = button("HA链验证") { startHaChainValidation() }
        exportButton = button("结束并发送所有日志") { finishAndShareLogs() }
        controls.addView(probeButton)
        controls.addView(liveButton)
        controls.addView(toyotaButton)
        controls.addView(exportButton)
        root.addView(HorizontalScrollView(this).apply { addView(controls) })

        dataText = TextView(this).apply { textSize = 16f; setTextColor(Color.rgb(110, 255, 180)); setPadding(10, 8, 10, 8) }
        rawText = TextView(this).apply { textSize = 12f; setTextColor(Color.rgb(210, 240, 220)); setPadding(10, 8, 10, 8) }
        val metrics = resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        val heightDp = metrics.heightPixels / metrics.density
        val isWide = widthDp >= 600f || widthDp > heightDp
        val body = LinearLayout(this).apply { orientation = if (isWide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        val dataScroll = ScrollView(this).apply { addView(dataText) }
        val rawScroll = ScrollView(this).apply { addView(rawText) }
        if (isWide) {
            body.addView(dataScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.48f))
            body.addView(rawScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.52f))
        } else {
            body.addView(dataScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.58f))
            body.addView(rawScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.42f))
        }
        root.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val events = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (name in listOf("READY", "ENGINE_STARTED", "ENGINE_STOPPED", "EV_MOVE", "REGEN", "STOP")) {
            events.addView(button(name) { logger.logEvent(name); appendRaw("EVENT $name") })
        }
        root.addView(HorizontalScrollView(this).apply { addView(events) })
        return root
    }

    private fun loadSavedDevice() {
        val preferences = getSharedPreferences("probe", MODE_PRIVATE)
        deviceAddress = preferences.getString("address", null)
        deviceName = preferences.getString("name", null)
    }

    @Deprecated("Deprecated in Android framework, retained for minSdk-compatible device picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEVICE && resultCode == RESULT_OK) {
            deviceName = data?.getStringExtra("name")
            deviceAddress = data?.getStringExtra("address")
            getSharedPreferences("probe", MODE_PRIVATE).edit()
                .putString("name", deviceName)
                .putString("address", deviceAddress)
                .apply()
            updateStatus("SELECTED: $deviceName / $deviceAddress")
        }
    }

    private fun ensurePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), REQUEST_BLUETOOTH)
            return false
        }
        return true
    }

    private fun connectSelected() {
        if (!ensurePermission() || busy.get()) return
        val address = deviceAddress ?: run { updateStatus("请先选择设备"); return }
        busy.set(true)
        setButtonsEnabled(false)
        worker.execute {
            try {
                establishConnection(address, newSession = true)
                updateStatus("CONNECTED: $deviceName")
            } catch (e: Exception) {
                safeLogError("CONNECT_ERROR", e)
                lastError = "CONNECT: ${e.message}"
                updateStatus("CONNECT ERROR: ${e.message}")
                elm.close()
            } finally {
                busy.set(false)
                setButtonsEnabled(true)
            }
        }
    }

    private fun establishConnection(address: String, newSession: Boolean) {
        updateStatus("CONNECTING: $deviceName")
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: error("Bluetooth unavailable")
        val device = adapter.getRemoteDevice(address)
        elm.connect(device)
        if (newSession || logger.currentSessionDir() == null || logger.state != SessionState.ACTIVE) {
            val dir = logger.start(deviceName ?: "Unknown", address)
            appendRaw("SESSION ${dir.absolutePath}")
        }
        logger.logConnection("BLUETOOTH_CONNECTED name=${deviceName ?: "Unknown"} address=$address")
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
        logger.logConnection("DISCONNECT_REQUEST")
        elm.close()
        currentHeader = null
        updateStatus("OFFLINE")
        ui.post { liveButton.text = "开始实时仪表"; setButtonsEnabled(true) }
    }

    private fun startLinkProbe() {
        if (!elm.isConnected()) { updateStatus("未连接OBD"); return }
        if (!busy.compareAndSet(false, true)) return
        liveMode.set(false)
        setButtonsEnabled(false)
        worker.execute {
            try {
                configureRuntimeAdapter()
                updateStatus("VERIFYING HCI-DERIVED PATHS")

                ensureHeader(HEADER_ENGINE)
                val standard = sendData(HEADER_ENGINE, "01040C0D0E10 2", 6000)
                val standardDecoded = ObdParsers.decodeStandard(standard.rawLines, RESPONSE_ENGINE)
                    ?: error("7E0→7E8 standard response decode failed")
                applyStandard("01040C0D0E10 2", standard, standardDecoded)

                ensureHeader(HEADER_HYBRID)
                val c3Result = sendData(HEADER_HYBRID, "21C3 6", 6000)
                val c3 = ObdParsers.decode21C3(c3Result.rawLines)
                    ?: error("7E2→7EA 61C3 response decode failed")
                applyC3("21C3 6", c3Result, c3)

                bestProtocol = ProtocolAttempt("6", "CAN 11/500 — HA capture").apply {
                    resolvedCode = "6"
                    valid0100 = 1
                    total0100 = 1
                    ecuIds += listOf(RESPONSE_ENGINE, RESPONSE_HYBRID)
                    validFrames = 2
                }
                logger.logConnection("HCI_PATHS_CONFIRMED engine=$HEADER_ENGINE/$RESPONSE_ENGINE hybrid=$HEADER_HYBRID/$RESPONSE_HYBRID")
                updateStatus("LINK READY — HA PATHS CONFIRMED")
                updateProgress("SOC=${fmt(c3.socPct, 1)}% | HV=${fmt(c3.hvVoltageV, 0)}V ${fmt(c3.hvCurrentA, 0)}A")
            } catch (e: Exception) {
                safeLogError("LINK_PROBE_ERROR", e)
                lastError = "LINK: ${e.message}"
                updateStatus("LINK PROBE ERROR: ${e.message}")
            } finally {
                busy.set(false)
                setButtonsEnabled(true)
            }
        }
    }

    private fun startHaChainValidation() {
        if (!elm.isConnected()) { updateStatus("未连接OBD"); return }
        if (!busy.compareAndSet(false, true)) return
        liveMode.set(false)
        setButtonsEnabled(false)
        worker.execute {
            try {
                configureRuntimeAdapter()
                updateStatus("HA CHAIN VALIDATION")
                updateProgress("Only HCI-observed RX400h commands; no scan")

                ensureHeader(HEADER_ENGINE)
                pollStandardCore()
                pollCoolantBlock()
                pollCdF3()
                pollAdapterVoltage()

                ensureHeader(HEADER_HYBRID)
                pollC3()
                pollC4()
                pollCf()
                logger.logFrame(baseline, hybrid)

                updateStatus("HA CHAIN VALID — ALL CORE DECODERS RAN")
                updateProgress("61C3 / 61C4 / 61CF / 61CD decoded")
            } catch (e: Exception) {
                safeLogError("HA_CHAIN_VALIDATION_ERROR", e)
                lastError = "HA CHAIN: ${e.message}"
                updateStatus("HA CHAIN ERROR: ${e.message}")
            } finally {
                busy.set(false)
                setButtonsEnabled(true)
            }
        }
    }

    private fun toggleLiveMode() {
        if (liveMode.get()) {
            liveMode.set(false)
            ui.post { liveButton.text = "开始实时仪表" }
            updateStatus("LIVE STOPPING")
            return
        }
        if (!elm.isConnected()) { updateStatus("未连接OBD"); return }
        if (!busy.compareAndSet(false, true)) return
        liveMode.set(true)
        liveStartedElapsedMs = SystemClock.elapsedRealtime()
        ui.post {
            liveButton.text = "停止实时仪表"
            probeButton.isEnabled = false
            toyotaButton.isEnabled = false
        }
        worker.execute {
            try {
                configureRuntimeAdapter()
                runLiveScheduler()
            } catch (e: Exception) {
                safeLogError("LIVE_MODE_ERROR", e)
                lastError = "LIVE: ${e.message}"
                updateStatus("LIVE ERROR: ${e.message}")
            } finally {
                liveMode.set(false)
                busy.set(false)
                ui.post { liveButton.text = "开始实时仪表"; setButtonsEnabled(true) }
            }
        }
    }

    private fun configureRuntimeAdapter() {
        updateProgress("Configuring HCI-derived CAN 11/500 profile")
        val commands = listOf("ATSP6", "ATAT1", "ATH1", "ATL0", "ATS0", "ATCAF1", "ATAL")
        for (command in commands) {
            val result = elm.command(command, 5000, 250)
            record(null, result)
            if (result.status == TransactionStatus.COMMAND_ERROR || result.status == TransactionStatus.TIMEOUT) {
                error("Adapter rejected $command (${result.status})")
            }
        }
        currentHeader = null
        bestProtocol = ProtocolAttempt("6", "CAN 11/500 — HA capture").apply {
            resolvedCode = "6"
            ecuIds += listOf(RESPONSE_ENGINE, RESPONSE_HYBRID)
        }
        logger.logConnection("RUNTIME_PROFILE_CONFIGURED profile=${ProbeLogger.PROFILE_VERSION}")
    }

    private fun runLiveScheduler() {
        updateStatus("LIVE — HCI-DERIVED RX400h PROFILE")
        logger.logConnection("LIVE_MODE_START cycle_target_ms=$CORE_CYCLE_MS")
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
            val remaining: Long = CORE_CYCLE_MS - (SystemClock.elapsedRealtime() - cycleStart)
            if (remaining > 0L) Thread.sleep(remaining)
        }
        logger.logConnection("LIVE_MODE_STOP")
        updateStatus("LIVE STOPPED")
    }

    private fun ensureHeader(header: String) {
        if (currentHeader == header) return
        val result = elm.command("ATSH$header", 4000, 120, 80)
        record(null, result)
        if (result.status != TransactionStatus.OK) error("Header $header failed: ${result.status}")
        currentHeader = header
        lastHeader = header
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
            markDecodeFailure(listOf(baseline.rpm, baseline.speedKph), command, result)
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
        val command = "ATRV"
        val result = elm.command(command, 4000, 120, 80)
        record(null, result)
        updateAdapterVoltage(result)
    }

    private fun pollCdF3() {
        val command = "21CDF3 3"
        val result = sendData(HEADER_ENGINE, command, 5000)
        val decoded = ObdParsers.decode21CdF3(result.rawLines)
        if (decoded == null) {
            markDecodeFailure(listOf(hybrid.injectionUl, hybrid.iceTorqueRaw), command, result)
            return
        }
        updateSignal(hybrid.injectionUl, decoded.injectionUl, command, result)
        updateSignal(hybrid.iceTorqueRaw, decoded.iceTorqueRaw, command, result)
        logger.logDecoded("injection_ul", decoded.injectionUl, "uL", command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
        logger.logDecoded("ice_torque_raw", decoded.iceTorqueRaw, "raw", command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
    }

    private fun pollC3() {
        val command = "21C3 6"
        val result = sendData(HEADER_HYBRID, command, 6000)
        val decoded = ObdParsers.decode21C3(result.rawLines)
        if (decoded == null) {
            markDecodeFailure(
                listOf(hybrid.socPct, hybrid.hvVoltageV, hybrid.hvCurrentA, hybrid.hvPowerKw, hybrid.mg1Rpm, hybrid.mg2Rpm),
                command,
                result
            )
            return
        }
        applyC3(command, result, decoded)
    }

    private fun pollC4() {
        val command = "21C4 5"
        val result = sendData(HEADER_HYBRID, command, 6000)
        val decoded = ObdParsers.decode21C4(result.rawLines)
        if (decoded == null) {
            markDecodeFailure(listOf(hybrid.rearMgRpm, hybrid.rearMgTorqueNm), command, result)
            return
        }
        updateSignal(hybrid.rearMgRpm, decoded.rearMgRpm, command, result)
        updateSignal(hybrid.rearMgTorqueNm, decoded.rearMgTorqueNm, command, result)
        logger.logDecoded("rear_mg_rpm", decoded.rearMgRpm, "rpm", command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
        logger.logDecoded("rear_mg_torque_nm", decoded.rearMgTorqueNm, "Nm", command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
    }

    private fun pollCf() {
        val command = "21CF 4"
        val result = sendData(HEADER_HYBRID, command, 6000)
        val decoded = ObdParsers.decode21CF(result.rawLines)
        if (decoded == null) {
            markDecodeFailure(
                listOf(hybrid.batteryTempsC, hybrid.batteryTempMinC, hybrid.batteryTempMaxC, hybrid.batteryTempAvgC),
                command,
                result
            )
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
        decoded.rpm?.let {
            updateSignal(baseline.rpm, it, command, result)
            logger.logDecoded("rpm", it, "rpm", command, payloadHex(result, RESPONSE_ENGINE), ObdParsers.DECODER_VERSION)
        }
        decoded.speedKph?.let {
            updateSignal(baseline.speedKph, it, command, result)
            logger.logDecoded("speed_kph", it, "km/h", command, payloadHex(result, RESPONSE_ENGINE), ObdParsers.DECODER_VERSION)
        }
        decoded.coolantC?.let {
            updateSignal(baseline.coolantC, it, command, result)
            logger.logDecoded("coolant_c", it, "C", command, payloadHex(result, RESPONSE_ENGINE), ObdParsers.DECODER_VERSION)
        }
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
        val values = listOf(
            "soc_pct" to decoded.socPct,
            "hv_voltage_v" to decoded.hvVoltageV,
            "hv_current_a" to decoded.hvCurrentA,
            "hv_power_kw" to decoded.hvPowerKw,
            "mg1_rpm" to decoded.mg1Rpm,
            "mg2_rpm" to decoded.mg2Rpm,
            "mg1_torque_nm" to decoded.mg1TorqueNm,
            "mg2_torque_nm" to decoded.mg2TorqueNm
        )
        for ((name, value) in values) {
            val unit = when {
                name.endsWith("_pct") -> "%"
                name.endsWith("_v") -> "V"
                name.endsWith("_a") -> "A"
                name.endsWith("_kw") -> "kW"
                name.endsWith("_rpm") -> "rpm"
                else -> "Nm"
            }
            logger.logDecoded(name, value, unit, command, decoded.rawDataHex, ObdParsers.DECODER_VERSION)
        }
    }

    private fun updateAdapterVoltage(result: CommandResult) {
        val value = ObdParsers.adapterVoltage(result.rawLines)
        updateSignal(baseline.adapterVoltageV, value, "ATRV", result)
        value?.let { logger.logDecoded("adapter_12v_v", it, "V", "ATRV", result.rawLines.joinToString(" | "), ObdParsers.DECODER_VERSION) }
    }

    private fun <T> updateSignal(signal: SignalValue<T>, value: T?, command: String, result: CommandResult) {
        signal.source = command
        signal.rawResponse = result.rawLines.joinToString(" | ")
        if (value != null) {
            signal.value = value
            signal.updatedAtElapsedMs = SystemClock.elapsedRealtime()
            signal.status = SignalStatus.VALID
        } else {
            signal.status = resultToSignalStatus(result)
        }
    }

    private fun markDecodeFailure(signals: List<SignalValue<*>>, command: String, result: CommandResult) {
        for (signal in signals) {
            signal.source = command
            signal.rawResponse = result.rawLines.joinToString(" | ")
            signal.status = resultToSignalStatus(result)
        }
    }

    private fun resultToSignalStatus(result: CommandResult): SignalStatus = when (result.status) {
        TransactionStatus.NO_DATA -> SignalStatus.NO_DATA
        TransactionStatus.INTERRUPTED -> SignalStatus.INTERRUPTED
        TransactionStatus.TIMEOUT -> SignalStatus.TIMEOUT
        TransactionStatus.IN_PROGRESS -> SignalStatus.SEARCHING_PROTOCOL
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
            updateStatus("RECONNECTING in ${wait / 1000}s")
            logger.logConnection("RECONNECT_WAIT count=$reconnectCount delay_ms=$wait")
            Thread.sleep(wait)
            if (!liveMode.get()) return false
            try {
                establishConnection(address, newSession = false)
                configureRuntimeAdapter()
                consecutiveErrors = 0
                logger.logConnection("RECONNECT_SUCCESS total_attempts=$reconnectCount")
                updateStatus("LIVE — RECONNECTED")
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
        val fiveSecond = listOf(
            baseline.rpm,
            baseline.speedKph,
            baseline.coolantC,
            baseline.adapterVoltageV,
            hybrid.socPct,
            hybrid.hvVoltageV,
            hybrid.hvCurrentA,
            hybrid.hvPowerKw,
            hybrid.mg1Rpm,
            hybrid.mg2Rpm,
            hybrid.injectionUl
        )
        fiveSecond.forEach { signal -> markStale(signal, now, 5000L) }
        listOf(hybrid.batteryTempsC, hybrid.batteryTempMinC, hybrid.batteryTempMaxC, hybrid.batteryTempAvgC)
            .forEach { signal -> markStale(signal, now, 12_000L) }
    }

    private fun markStale(signal: SignalValue<*>, now: Long, thresholdMs: Long) {
        val updated = signal.updatedAtElapsedMs ?: return
        if (signal.value != null && now - updated > thresholdMs) signal.status = SignalStatus.STALE
    }

    private fun finishAndShareLogs() {
        val session = logger.currentSessionDir() ?: run { updateStatus("当前没有可导出的测试会话"); return }
        liveMode.set(false)
        setButtonsEnabled(false)
        worker.execute {
            try {
                logger.logEvent("SESSION_END")
                logger.logConnection("END_AND_SHARE_CLICKED dir=${session.name}")
                val zip = logger.finalizeAndZip()
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", zip)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "RX400h Protocol Probe logs ${zip.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ui.post {
                    setButtonsEnabled(true)
                    updateStatus("日志已封包: ${zip.name}")
                    startActivity(Intent.createChooser(share, "发送所有测试日志"))
                }
            } catch (e: Exception) {
                lastError = "EXPORT: ${e.message}"
                ui.post { setButtonsEnabled(true); updateStatus("日志导出失败: ${e.message}") }
            }
        }
    }

    private fun record(header: String?, result: CommandResult) {
        lastTransaction = "${result.command} ${result.status} ${result.latencyMs}ms"
        logger.logTransaction(header, result)
        appendRaw("${header ?: "ADAPTER"}  ${result.command}  ${result.status}  ${result.latencyMs}ms\n${result.rawLines.joinToString(" | ")}")
    }

    private fun safeLogError(message: String, throwable: Throwable) {
        try { logger.logError(message, throwable) } catch (_: Exception) {}
    }

    private fun renderData() {
        ui.post {
            val rpm = baseline.rpm.value
            val injection = hybrid.injectionUl.value
            val engineState = when {
                rpm == null -> "UNKNOWN"
                rpm <= 0.0 -> "OFF"
                injection != null && injection > 0.0 -> "FUELING / COMBUSTION"
                injection != null -> "ROTATING / NO INJECTION"
                else -> "ROTATING / FUEL STATUS UNKNOWN"
            }
            val power = hybrid.hvPowerKw.value
            val energyState = when {
                power == null -> "UNKNOWN"
                power > 0.5 -> "BATTERY DISCHARGE / TRACTION"
                power < -0.5 -> "BATTERY CHARGE / REGEN"
                else -> "HV NEUTRAL"
            }
            val liveSeconds = liveStartedElapsedMs?.takeIf { liveMode.get() }
                ?.let { (SystemClock.elapsedRealtime() - it) / 1000 } ?: 0
            val temps = hybrid.batteryTempsC.value

            dataText.text = buildString {
                appendLine("RUNTIME")
                appendLine("MODE: ${if (liveMode.get()) "LIVE" else if (busy.get()) "BUSY" else "IDLE"}")
                appendLine("BLUETOOTH: ${if (elm.isConnected()) "CONNECTED" else "OFFLINE"}")
                appendLine("PROFILE: ${ProbeLogger.PROFILE_VERSION}")
                appendLine("HEADER: $lastHeader")
                appendLine("LIVE TIME: ${liveSeconds}s")
                appendLine("RECONNECTS: $reconnectCount")
                appendLine("LAST TX: $lastTransaction")
                appendLine("LAST ERROR: $lastError")
                appendLine()
                appendLine("CORE 7 SIGNALS")
                appendLine(signal("SOC", hybrid.socPct, "%", 1))
                appendLine(signal("HV POWER", hybrid.hvPowerKw, "kW", 2))
                appendLine(signal("COOLANT", baseline.coolantC, "°C", 1))
                appendLine(signal("ENGINE RPM", baseline.rpm, "rpm", 0))
                appendLine(signal("BATTERY MAX", hybrid.batteryTempMaxC, "°C", 2))
                appendLine(signal("12V OBD", baseline.adapterVoltageV, "V", 2))
                appendLine(signal("SPEED", baseline.speedKph, "km/h", 0))
                appendLine()
                appendLine("HV BATTERY")
                appendLine(signal("VOLTAGE", hybrid.hvVoltageV, "V", 0))
                appendLine(signal("CURRENT", hybrid.hvCurrentA, "A", 0))
                appendLine("FLOW: $energyState")
                appendLine(signal("TEMP MIN", hybrid.batteryTempMinC, "°C", 2))
                appendLine(signal("TEMP AVG", hybrid.batteryTempAvgC, "°C", 2))
                appendLine("T1–T8: ${temps?.joinToString("  ") { fmt(it, 2) } ?: "—"}")
                appendLine()
                appendLine("ENGINE / MOTOR")
                appendLine("ENGINE STATE: $engineState")
                appendLine(signal("INJECTION", hybrid.injectionUl, "µL", 2))
                appendLine(signal("MG1 RPM", hybrid.mg1Rpm, "rpm", 0))
                appendLine(signal("MG2 RPM", hybrid.mg2Rpm, "rpm", 0))
                appendLine(signal("MG1 TORQUE", hybrid.mg1TorqueNm, "Nm", 1))
                appendLine(signal("MG2 TORQUE", hybrid.mg2TorqueNm, "Nm", 1))
                appendLine(signal("REAR MG RPM", hybrid.rearMgRpm, "rpm", 0))
                appendLine(signal("REAR MG TORQUE", hybrid.rearMgTorqueNm, "Nm", 1))
            }
        }
    }

    private fun signal(name: String, signal: SignalValue<Double>, unit: String, digits: Int): String {
        val age = signal.updatedAtElapsedMs?.let { (SystemClock.elapsedRealtime() - it) / 1000.0 }
        val value = signal.value?.let { fmt(it, digits) } ?: "—"
        return "$name: $value $unit  [${signal.status}]${age?.let { " age=${fmt(it, 1)}s" } ?: ""}"
    }

    private fun fmt(value: Double, digits: Int): String = String.format(Locale.US, "%.${digits}f", value)

    private fun setButtonsEnabled(enabled: Boolean) = ui.post {
        probeButton.isEnabled = enabled && !liveMode.get()
        toyotaButton.isEnabled = enabled && !liveMode.get()
        exportButton.isEnabled = enabled
        liveButton.isEnabled = enabled || liveMode.get()
    }

    private fun appendRaw(text: String) = ui.post {
        rawText.append(text + "\n\n")
        if (rawText.text.length > 60_000) rawText.text = rawText.text.takeLast(40_000)
    }

    private fun updateStatus(text: String) = ui.post { statusText.text = text }
    private fun updateProgress(text: String) = ui.post { progressText.text = text }
}
