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
import android.widget.*
import androidx.core.content.FileProvider
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val elm = Elm327Client()
    private lateinit var logger: ProbeLogger

    private val busy = AtomicBoolean(false)
    private val liveMode = AtomicBoolean(false)

    private var deviceAddress: String? = null
    private var deviceName: String? = null
    private var currentHeader: String? = null
    private val baseline = BaselineData()
    private var bestProtocol: ProtocolAttempt? = null
    private val protocolAttempts = mutableListOf<ProtocolAttempt>()
    private var validVehicleResponses = 0
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
            text = "RX400h PROTOCOL PROBE  V0.1.7"
            textSize = 21f
            setTextColor(Color.rgb(110, 255, 180))
        })
        statusText = TextView(this).apply { textSize = 15f; setTextColor(Color.rgb(255, 210, 80)) }
        progressText = TextView(this).apply { textSize = 13f; setTextColor(Color.rgb(150, 220, 255)) }
        root.addView(statusText)
        root.addView(progressText)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun button(label: String, action: () -> Unit): Button = Button(this).apply { text = label; setOnClickListener { action() } }
        controls.addView(button("选择设备") { startActivityForResult(Intent(this, DevicePickerActivity::class.java), 100) })
        controls.addView(button("连接") { connectSelected() })
        controls.addView(button("断开") { disconnect() })
        probeButton = button("链路确认") { startLinkProbe() }
        liveButton = button("开始实时仪表") { toggleLiveMode() }
        toyotaButton = button("Toyota探测") { startToyotaProbe() }
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
            body.addView(dataScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.44f))
            body.addView(rawScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.56f))
        } else {
            body.addView(dataScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.50f))
            body.addView(rawScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.50f))
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
        val p = getSharedPreferences("probe", MODE_PRIVATE)
        deviceAddress = p.getString("address", null)
        deviceName = p.getString("name", null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            deviceName = data?.getStringExtra("name")
            deviceAddress = data?.getStringExtra("address")
            getSharedPreferences("probe", MODE_PRIVATE).edit().putString("name", deviceName).putString("address", deviceAddress).apply()
            updateStatus("SELECTED: $deviceName / $deviceAddress")
        }
    }

    private fun ensurePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 20)
            return false
        }
        return true
    }

    private fun connectSelected() {
        if (!ensurePermission()) return
        if (busy.get()) return
        val address = deviceAddress ?: run { updateStatus("请先选择设备"); return }
        busy.set(true)
        setButtonsEnabled(false)
        worker.execute {
            try {
                establishConnection(address, newSession = true)
                updateStatus("CONNECTED: $deviceName")
            } catch (e: Exception) {
                logger.logError("CONNECT_ERROR", e)
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
        if (newSession || logger.currentSessionDir() == null) {
            val dir = logger.start(deviceName ?: "Unknown", address)
            appendRaw("SESSION ${dir.absolutePath}")
        }
        logger.logConnection("BLUETOOTH_CONNECTED name=${deviceName ?: "Unknown"} address=$address")
        elm.initialize().forEach { record(null, it) }
        for (cmd in listOf("ATI", "ATDP", "ATDPN", "ATRV")) record(null, elm.command(cmd, 6000, 500))
    }

    private fun disconnect() {
        liveMode.set(false)
        busy.set(false)
        logger.logConnection("DISCONNECT_REQUEST")
        elm.close()
        logger.stop()
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
                protocolAttempts.clear()
                validVehicleResponses = 0
                updateProgress("LINK PROBE: SP6 then AUTO")
                val candidates = listOf(
                    ProtocolAttempt("6", "CAN 11/500"),
                    ProtocolAttempt("0", "AUTO")
                )
                for (a in candidates) evaluateProtocol(a, full = true)
                bestProtocol = candidates.maxWithOrNull(compareBy<ProtocolAttempt> { it.score }.thenBy { if (it.requestedCode == "6") 1 else 0 })
                val best = bestProtocol ?: error("未找到车辆协议")
                logger.logProtocolSummary(candidates, best)
                restoreAndLockBest(best)
                updateStatus("LINK READY — ${best.label}")
                updateProgress("ECU ${best.ecuIds.joinToString()} | score=${best.score}")
            } catch (e: Exception) {
                logger.logError("LINK_PROBE_ERROR", e)
                lastError = "LINK: ${e.message}"
                updateStatus("LINK PROBE ERROR: ${e.message}")
            } finally {
                busy.set(false)
                setButtonsEnabled(true)
            }
        }
    }

    private fun evaluateProtocol(attempt: ProtocolAttempt, full: Boolean) {
        protocolAttempts += attempt
        record(null, elm.command("ATPC", 5000, 900))
        Thread.sleep(1500)
        record(null, elm.command("ATSP${attempt.requestedCode}", 6000, 1800))
        Thread.sleep(2200)
        val commands = if (full) listOf("0100", "0100", "010C", "010C", "0105", "0105", "010D", "010D") else listOf("0100")
        for (cmd in commands) {
            val result = elm.command(cmd, if (cmd == "0100") 20_000 else 8_000, 900, 200)
            record(null, result)
            accumulateAttempt(attempt, cmd, result)
            if (result.status == TransactionStatus.BUS_ERROR) break
        }
        val dp = elm.command("ATDP", 6000, 700)
        val dpn = elm.command("ATDPN", 6000, 700)
        record(null, dp)
        record(null, dpn)
        attempt.description = dp.rawLines.joinToString(" | ")
        attempt.resolvedCode = parseProtocolNumber(dpn)
        logger.logProtocolAttempt(attempt)
    }

    private fun accumulateAttempt(a: ProtocolAttempt, command: String, result: CommandResult) {
        a.transactions++
        a.totalLatencyMs += result.latencyMs
        if (result.status == TransactionStatus.NO_DATA) a.noData++
        if (result.status == TransactionStatus.BUS_ERROR) a.busErrors++
        val valid = isPositiveObdResponse(command, result)
        when (command) {
            "0100" -> { a.total0100++; if (valid) a.valid0100++ }
            "010C" -> { a.total010C++; if (valid) a.valid010C++ }
            "0105" -> { a.total0105++; if (valid) a.valid0105++ }
            "010D" -> { a.total010D++; if (valid) a.valid010D++ }
        }
        if (valid) {
            validVehicleResponses++
            a.ecuIds += extractResponseIds(result)
            a.validFrames += countExpectedFrames(command, result)
        }
    }

    private fun restoreAndLockBest(best: ProtocolAttempt) {
        updateProgress("LOCKING ${best.label}")
        record(null, elm.command("ATPC", 5000, 900))
        Thread.sleep(1500)
        val code = if (best.requestedCode == "0") (best.resolvedCode?.removePrefix("A") ?: "6") else best.requestedCode
        record(null, elm.command("ATSP$code", 6000, 1800))
        Thread.sleep(2200)
        val verify1 = elm.command("0100", 20_000, 900, 200)
        val verify2 = elm.command("0100", 12_000, 900, 200)
        record(null, verify1)
        record(null, verify2)
        if (!isPositiveObdResponse("0100", verify1) || !isPositiveObdResponse("0100", verify2)) error("协议复核失败")
        currentHeader = null
        logger.logConnection("BEST_PROTOCOL_LOCKED requested=${best.requestedCode} resolved=$code")
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
        ui.post { liveButton.text = "停止实时仪表"; probeButton.isEnabled = false; toyotaButton.isEnabled = false }
        worker.execute {
            try {
                if (bestProtocol == null) {
                    val best = ProtocolAttempt("6", "CAN 11/500")
                    evaluateProtocol(best, full = false)
                    if (best.valid0100 == 0) error("SP6未确认")
                    bestProtocol = best
                    restoreAndLockBest(best)
                }
                runLiveScheduler()
            } catch (e: Exception) {
                logger.logError("LIVE_MODE_ERROR", e)
                lastError = "LIVE: ${e.message}"
                updateStatus("LIVE ERROR: ${e.message}")
            } finally {
                liveMode.set(false)
                busy.set(false)
                ui.post { liveButton.text = "开始实时仪表"; setButtonsEnabled(true) }
            }
        }
    }

    private fun runLiveScheduler() {
        updateStatus("LIVE STREAMING")
        logger.logConnection("LIVE_MODE_START")
        var nextRpm = 0L
        var nextSpeed = 0L
        var nextCoolant = 0L
        var nextVoltage = 0L
        var nextFrameLog = 0L
        while (liveMode.get()) {
            if (!elm.isConnected()) {
                if (!attemptReconnect()) break
                nextRpm = 0; nextSpeed = 0; nextCoolant = 0; nextVoltage = 0
            }
            val now = SystemClock.elapsedRealtime()
            var didWork = false
            try {
                if (now >= nextRpm) {
                    pollBaseline("010C", 5000)
                    nextRpm = now + 250
                    didWork = true
                }
                if (now >= nextSpeed) {
                    pollBaseline("010D", 5000)
                    nextSpeed = now + 350
                    didWork = true
                }
                if (now >= nextCoolant) {
                    pollBaseline("0105", 5000)
                    nextCoolant = now + 1500
                    didWork = true
                }
                if (now >= nextVoltage) {
                    pollBaseline("ATRV", 5000)
                    nextVoltage = now + 3000
                    didWork = true
                }
                if (now >= nextFrameLog) {
                    logger.logFrame(baseline, null)
                    nextFrameLog = now + 1000
                }
                consecutiveErrors = 0
            } catch (e: Exception) {
                consecutiveErrors++
                lastError = "POLL: ${e.message}"
                logger.logError("LIVE_POLL_ERROR count=$consecutiveErrors", e)
                if (consecutiveErrors >= 3) elm.close()
            }
            if (!didWork) Thread.sleep(30)
        }
        logger.logConnection("LIVE_MODE_STOP")
        updateStatus("LIVE STOPPED")
    }

    private fun pollBaseline(command: String, timeout: Long) {
        val result = elm.command(command, timeout, 220, 120)
        record(null, result)
        updateBaseline(command, result)
    }

    private fun attemptReconnect(): Boolean {
        val address = deviceAddress ?: return false
        val delays = longArrayOf(1000, 2000, 5000, 10_000, 30_000)
        var attempt = 0
        while (liveMode.get()) {
            reconnectCount++
            val wait = delays[min(attempt, delays.lastIndex)]
            updateStatus("RECONNECTING in ${wait / 1000}s")
            logger.logConnection("RECONNECT_WAIT count=$reconnectCount delay_ms=$wait")
            Thread.sleep(wait)
            if (!liveMode.get()) return false
            try {
                establishConnection(address, newSession = false)
                val best = bestProtocol ?: ProtocolAttempt("6", "CAN 11/500")
                restoreAndLockBest(best)
                consecutiveErrors = 0
                logger.logConnection("RECONNECT_SUCCESS total_attempts=$reconnectCount")
                updateStatus("LIVE STREAMING — RECONNECTED")
                return true
            } catch (e: Exception) {
                logger.logError("RECONNECT_FAILED attempt=${attempt + 1}", e)
                elm.close()
                attempt++
            }
        }
        return false
    }

    private fun startToyotaProbe() {
        if (!elm.isConnected()) { updateStatus("未连接OBD"); return }
        if (!busy.compareAndSet(false, true)) return
        val resumeLive = liveMode.getAndSet(false)
        setButtonsEnabled(false)
        worker.execute {
            try {
                val best = bestProtocol ?: ProtocolAttempt("6", "CAN 11/500")
                restoreAndLockBest(best)
                updateStatus("TOYOTA ECU TOPOLOGY PROBE")
                updateProgress("HA-informed headers: 7E3 → 7E1 → 7E4 → 7E2 → 7E0")

                // Hybrid Assistant APK contains strong header clues for 7E3, plus
                // response-ID clues compatible with request headers 7E1 and 7E4.
                // Known standard OBD responders 7E0 and 7E2 are kept as controls.
                val headers = listOf("7E3", "7E1", "7E4", "7E2", "7E0")
                val baseRequests = listOf("221001", "221002", "221814")
                val extendedKnownRequests = listOf("220103", "221F07")

                var reachedEcuCount = 0
                var positiveCount = 0
                var negativeCount = 0

                for (header in headers) {
                    currentHeader = header
                    lastHeader = header
                    record(header, elm.command("ATSH$header", 6000, 900))
                    Thread.sleep(1000)

                    var headerReached = false
                    var service22Supported = false
                    val requests = ArrayList(baseRequests)

                    for (req in requests) {
                        val result = elm.command(req, 15_000, 1200, 350)
                        record(header, result)
                        val assessment = assessToyotaResponse(req, result)
                        logger.logConnection(
                            "TOYOTA_ASSESS header=$header request=$req kind=${assessment.kind} " +
                                "nrc=${assessment.nrc ?: "-"} response_ids=${assessment.responseIds.joinToString(";")}"
                        )
                        when (assessment.kind) {
                            ToyotaResponseKind.POSITIVE -> {
                                positiveCount++
                                headerReached = true
                                service22Supported = true
                                updateProgress("POSITIVE $header / $req / IDs ${assessment.responseIds.joinToString()}")
                            }
                            ToyotaResponseKind.NEGATIVE -> {
                                negativeCount++
                                headerReached = true
                                if (assessment.nrc != "11") service22Supported = true
                                updateProgress("ECU REACHED $header / $req / NRC ${assessment.nrc} ${assessment.nrcText}")
                            }
                            ToyotaResponseKind.NONE -> Unit
                        }
                        if (result.status == TransactionStatus.BUS_ERROR) break
                        Thread.sleep(900)
                    }

                    if (headerReached) {
                        reachedEcuCount++
                        // Only expand the whitelist on a header that actually answered.
                        for (req in extendedKnownRequests) {
                            val result = elm.command(req, 15_000, 1200, 350)
                            record(header, result)
                            val assessment = assessToyotaResponse(req, result)
                            logger.logConnection(
                                "TOYOTA_ASSESS header=$header request=$req kind=${assessment.kind} " +
                                    "nrc=${assessment.nrc ?: "-"} response_ids=${assessment.responseIds.joinToString(";")}"
                            )
                            when (assessment.kind) {
                                ToyotaResponseKind.POSITIVE -> positiveCount++
                                ToyotaResponseKind.NEGATIVE -> negativeCount++
                                ToyotaResponseKind.NONE -> Unit
                            }
                            if (result.status == TransactionStatus.BUS_ERROR) break
                            Thread.sleep(900)
                        }
                    }

                    logger.logConnection(
                        "TOYOTA_HEADER_SUMMARY header=$header reached=$headerReached service22_supported=$service22Supported"
                    )
                    Thread.sleep(1200)
                }

                currentHeader = null
                record(null, elm.command("ATSH7DF", 6000, 900))
                restoreAndLockBest(best)
                updateStatus("TOYOTA PROBE COMPLETE — LIVE LINK RESTORED")
                updateProgress("ECUs reached=$reachedEcuCount | positive=$positiveCount | negative=$negativeCount")
            } catch (e: Exception) {
                logger.logError("TOYOTA_PROBE_ERROR", e)
                lastError = "TOYOTA: ${e.message}"
                updateStatus("TOYOTA PROBE ERROR: ${e.message}")
                try { bestProtocol?.let { restoreAndLockBest(it) } } catch (_: Exception) {}
            } finally {
                busy.set(false)
                setButtonsEnabled(true)
                if (resumeLive) ui.postDelayed({ toggleLiveMode() }, 500)
            }
        }
    }

    private enum class ToyotaResponseKind { POSITIVE, NEGATIVE, NONE }

    private data class ToyotaAssessment(
        val kind: ToyotaResponseKind,
        val nrc: String? = null,
        val nrcText: String = "",
        val responseIds: Set<String> = emptySet()
    )

    private fun assessToyotaResponse(request: String, result: CommandResult): ToyotaAssessment {
        val did = request.removePrefix("22")
        val compactLines = normalizedLines(result)
        val ids = extractResponseIds(result)
        if (compactLines.any { it.contains("62$did") }) {
            return ToyotaAssessment(ToyotaResponseKind.POSITIVE, responseIds = ids)
        }
        val nrcMatch = compactLines.asSequence()
            .mapNotNull { Regex("7F22([0-9A-F]{2})").find(it) }
            .firstOrNull()
        if (nrcMatch != null) {
            val nrc = nrcMatch.groupValues[1]
            return ToyotaAssessment(
                ToyotaResponseKind.NEGATIVE,
                nrc = nrc,
                nrcText = decodeNrc(nrc),
                responseIds = ids
            )
        }
        return ToyotaAssessment(ToyotaResponseKind.NONE, responseIds = ids)
    }

    private fun decodeNrc(nrc: String): String = when (nrc) {
        "10" -> "GENERAL REJECT"
        "11" -> "SERVICE NOT SUPPORTED"
        "12" -> "SUBFUNCTION NOT SUPPORTED"
        "13" -> "INCORRECT LENGTH / FORMAT"
        "21" -> "BUSY — REPEAT REQUEST"
        "22" -> "CONDITIONS NOT CORRECT"
        "24" -> "REQUEST SEQUENCE ERROR"
        "31" -> "REQUEST OUT OF RANGE"
        "33" -> "SECURITY ACCESS DENIED"
        "78" -> "RESPONSE PENDING"
        else -> "UNKNOWN NRC"
    }

    private fun updateBaseline(command: String, result: CommandResult) {
        val raw = result.rawLines.joinToString(" | ")
        fun <T> apply(signal: SignalValue<T>, value: T?) {
            signal.source = command
            signal.rawResponse = raw
            if (value != null) {
                signal.value = value
                signal.updatedAtElapsedMs = SystemClock.elapsedRealtime()
                signal.status = SignalStatus.VALID
            } else {
                signal.status = when (result.status) {
                    TransactionStatus.NO_DATA -> SignalStatus.NO_DATA
                    TransactionStatus.INTERRUPTED -> SignalStatus.INTERRUPTED
                    TransactionStatus.TIMEOUT -> SignalStatus.TIMEOUT
                    TransactionStatus.IN_PROGRESS -> SignalStatus.SEARCHING_PROTOCOL
                    else -> SignalStatus.DECODE_ERROR
                }
            }
        }
        when (command) {
            "ATRV" -> apply(baseline.adapterVoltageV, ObdParsers.adapterVoltage(result.rawLines))
            "0105" -> apply(baseline.coolantC, ObdParsers.coolant(result.normalizedHex))
            "010C" -> apply(baseline.rpm, ObdParsers.rpm(result.normalizedHex))
            "010D" -> apply(baseline.speedKph, ObdParsers.speed(result.normalizedHex))
        }
    }

    private fun refreshStaleStates() {
        val now = SystemClock.elapsedRealtime()
        listOf(baseline.rpm, baseline.speedKph, baseline.coolantC, baseline.adapterVoltageV).forEach { s ->
            val age = s.updatedAtElapsedMs?.let { now - it } ?: return@forEach
            if (age > 5000 && s.value != null) s.status = SignalStatus.STALE
        }
    }

    private fun parseProtocolNumber(result: CommandResult): String? {
        val text = result.rawLines.joinToString(" ").uppercase().replace(" ", "")
        return Regex("A?([0-9A])").find(text)?.groupValues?.getOrNull(1)
    }

    private fun normalizedLines(result: CommandResult): List<String> = result.rawLines.map { it.replace(" ", "").uppercase() }

    private fun isPositiveObdResponse(command: String, result: CommandResult): Boolean {
        val expected = when (command.take(4)) {
            "0100" -> "4100"
            "0105" -> "4105"
            "010C" -> "410C"
            "010D" -> "410D"
            else -> return false
        }
        return normalizedLines(result).any { it.contains(expected) }
    }

    private fun extractResponseIds(result: CommandResult): Set<String> {
        val ids = linkedSetOf<String>()
        for (line in result.rawLines) {
            val compact = line.replace(" ", "").uppercase()
            Regex("^([0-9A-F]{3})(?:[0-9A-F]{2})?(?:41|62|7F)").find(compact)?.groupValues?.getOrNull(1)?.let { ids += it }
        }
        return ids
    }

    private fun countExpectedFrames(command: String, result: CommandResult): Int {
        val expected = when (command.take(4)) {
            "0100" -> "4100"
            "0105" -> "4105"
            "010C" -> "410C"
            "010D" -> "410D"
            else -> ""
        }
        return normalizedLines(result).count { it.contains(expected) }
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
                logger.logError("EXPORT_SHARE_ERROR", e)
                ui.post { setButtonsEnabled(true); updateStatus("日志导出失败: ${e.message}") }
            }
        }
    }

    private fun record(header: String?, result: CommandResult) {
        lastTransaction = "${result.command} ${result.status} ${result.latencyMs}ms"
        logger.logTransaction(header, result)
        appendRaw("${header ?: "FUNC"}  ${result.command}  ${result.status}  ${result.latencyMs}ms\n${result.rawLines.joinToString(" | ")}")
    }

    private fun renderData() {
        ui.post {
            fun signal(name: String, s: SignalValue<*>, unit: String): String {
                val age = s.updatedAtElapsedMs?.let { (SystemClock.elapsedRealtime() - it) / 1000.0 }
                val v = s.value?.toString() ?: "—"
                return "$name: $v $unit  [${s.status}]${age?.let { " age=${"%.1f".format(it)}s" } ?: ""}"
            }
            val rpm = baseline.rpm.value
            val engineState = when {
                rpm == null -> "UNKNOWN"
                rpm <= 0.0 -> "OFF"
                else -> "ROTATING — FUEL STATUS UNKNOWN"
            }
            val liveSeconds = liveStartedElapsedMs?.takeIf { liveMode.get() }?.let { (SystemClock.elapsedRealtime() - it) / 1000 } ?: 0
            val best = bestProtocol
            dataText.text = buildString {
                appendLine("RUNTIME")
                appendLine("MODE: ${if (liveMode.get()) "LIVE" else if (busy.get()) "BUSY" else "IDLE"}")
                appendLine("BLUETOOTH: ${if (elm.isConnected()) "CONNECTED" else "OFFLINE"}")
                appendLine("PROTOCOL: ${best?.label ?: "NOT LOCKED"}")
                appendLine("ECU: ${best?.ecuIds?.joinToString() ?: "—"}")
                appendLine("HEADER: $lastHeader")
                appendLine("LIVE TIME: ${liveSeconds}s")
                appendLine("RECONNECTS: $reconnectCount")
                appendLine("LAST TX: $lastTransaction")
                appendLine("LAST ERROR: $lastError")
                appendLine()
                appendLine("ENGINE")
                appendLine(signal("RPM", baseline.rpm, "rpm"))
                appendLine("STATE: $engineState")
                appendLine(signal("COOLANT", baseline.coolantC, "°C"))
                appendLine(signal("SPEED", baseline.speedKph, "km/h"))
                appendLine(signal("12V OBD", baseline.adapterVoltageV, "V"))
                appendLine()
                appendLine("TOYOTA SIGNALS")
                appendLine("SOC: NOT MAPPED")
                appendLine("HV POWER: NOT MAPPED")
                appendLine("HV TEMP: NOT MAPPED")
            }
        }
    }

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
