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
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One serial vehicle owner. There are no recording, saving or automatic retry states. */
class MainActivity : Activity() {
    companion object {
        private const val REQUEST_DEVICE = 100
        private const val REQUEST_PERMISSION = 20
        private val PROCESS_VEHICLE_SESSION_LEASE = ExclusiveSessionLease()
        private val BUSY_PHASES = setOf(MonitorSessionPhase.WAITING_PERMISSION,
            MonitorSessionPhase.CONNECTING, MonitorSessionPhase.INITIALIZING, MonitorSessionPhase.STOPPING)
    }
    private class Run(val address: String) {
        // Each attempt owns its client: a delayed close can never close its successor.
        val elm = Elm327Client()
        val cancelled = AtomicBoolean(false)
    }
    private val worker = Executors.newSingleThreadExecutor()
    private val closer = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val destroying = AtomicBoolean(false)
    private val phase = AtomicReference(MonitorSessionPhase.IDLE)
    @Volatile private var activeRun: Run? = null
    private var permissionPending = false // UI-thread only
    private var devicePickerPending = false
    private lateinit var dashboard: PixelDashboardView
    private var deviceAddress: String? = null
    private var deviceName: String? = null
    @Volatile private var lastError = "NONE"
    @Volatile private var lastNotice: String? = null
    private val signalLock = Any()
    private val store = SignalStore()
    private val requestSignals = RequestSignalBindings(store)
    private val baseline get() = store.baseline
    private val hybrid get() = store.hybrid
    private val idleCheckState = IdleCheckState()
    private val dashboardPublishPending = AtomicBoolean(false)
    @Volatile private var dashboardVisible = false
    private var lastDashboardSnapshot: DashboardSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val preferences = getSharedPreferences("probe", MODE_PRIVATE)
        deviceAddress = preferences.getString("address", null)
        deviceName = preferences.getString("name", null)
        dashboard = PixelDashboardView(this, onSelectDevice = {
            if (!destroying.get() && !devicePickerPending) {
                devicePickerPending = true
                startActivityForResult(Intent(this, DevicePickerActivity::class.java), REQUEST_DEVICE)
            }
        }, onReconnect = { requestConnection() })
        setContentView(dashboard)
        // Cleanup neither owns the vehicle worker nor blocks a fresh connection.
        Thread({
            runCatching {
                LegacySessionCleanup.clean(getExternalFilesDir(null) ?: filesDir)
            }
        }, "old-unsaved-cleanup").start()
        ui.post { requestConnection() } // Once per Activity creation, never in onStart/onResume.
    }

    override fun onStart() {
        super.onStart()
        dashboardVisible = true
        // Refresh truth before resuming animations after a long/suspended background.
        dashboardRenderRunnable.run()
        dashboard.setPresentationVisible(true)
        ui.removeCallbacks(refreshUiRunnable)
        ui.postDelayed(refreshUiRunnable, 500)
    }

    override fun onStop() {
        dashboardVisible = false
        dashboard.setPresentationVisible(false)
        ui.removeCallbacks(refreshUiRunnable)
        super.onStop() // Acquisition deliberately continues while the process can run.
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dashboard.onConfigurationChanged()
        renderDashboard()
    }

    @Deprecated("Native Back exits without finalization")
    override fun onBackPressed() {
        stopAndExit()
        finish()
    }

    private fun stopAndExit() {
        if (!destroying.compareAndSet(false, true)) return
        permissionPending = false
        if (::dashboard.isInitialized) {
            dashboard.persistLocalSettings()
            dashboard.setPresentationVisible(false)
        }
        cancelRun(activeRun)
        activeRun = null
        phase.set(MonitorSessionPhase.STOPPING)
        ui.removeCallbacksAndMessages(null)
        worker.shutdown()
        closer.shutdown()
    }

    override fun onDestroy() {
        stopAndExit()
        super.onDestroy()
    }

    private fun cancelRun(run: Run?) {
        if (run == null) return
        run.cancelled.set(true)
        // Socket close unblocks connect/read; capture the OLD run, never a mutable client.
        closer.execute { runCatching { run.elm.close() } }
    }

    @Deprecated("Native activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_DEVICE) return
        devicePickerPending = false
        if (destroying.get() || resultCode != RESULT_OK) return
        val address = data?.getStringExtra("address") ?: return
        deviceAddress = address
        deviceName = data.getStringExtra("name")
        getSharedPreferences("probe", MODE_PRIVATE).edit()
            .putString("address", address).putString("name", deviceName).apply()
        // Explicit device selection replaces even an in-flight old connection.
        permissionPending = false
        cancelRun(activeRun)
        activeRun = null
        phase.set(MonitorSessionPhase.IDLE)
        requestConnection()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode != REQUEST_PERMISSION || !permissionPending || destroying.get()) return
        permissionPending = false
        phase.set(MonitorSessionPhase.IDLE)
        if (hasBluetoothPermission()) requestConnection()
        else showFailure("缺少蓝牙连接权限；请在系统设置允许")
    }

    private fun hasBluetoothPermission() = Build.VERSION.SDK_INT < 31 ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun requestConnection() {
        if (destroying.get() || phase.get() in BUSY_PHASES) return
        val address = deviceAddress
        if (address == null) {
            showFailure("请在设置中选择 OBD 设备")
            return
        }
        if (!hasBluetoothPermission()) {
            phase.set(MonitorSessionPhase.WAITING_PERMISSION)
            permissionPending = true
            renderDashboard()
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_PERMISSION)
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        if (adapter == null || !adapter.isEnabled) {
            cancelRun(activeRun)
            activeRun = null
            showFailure("蓝牙未开启；请在系统设置开启")
            return
        }
        cancelRun(activeRun)
        val run = Run(address)
        activeRun = run
        phase.set(MonitorSessionPhase.CONNECTING)
        lastError = "NONE"
        lastNotice = null
        // First invalidate the old snapshot. Self-test requires new acquired samples.
        dashboardRenderRunnable.run()
        dashboard.armStartSelfTest()
        renderDashboard()
        worker.execute {
            PROCESS_VEHICLE_SESSION_LEASE.withCancellableLease({ current(run) }) {
                try {
                    requireCurrent(run)
                    synchronized(signalLock) { store.clear(); idleCheckState.reset() }
                    run.elm.connect(adapter.getRemoteDevice(run.address)) { current(run) }
                    setRunPhase(run, MonitorSessionPhase.INITIALIZING)
                    val initialization = run.elm.initialize { current(run) }
                    requireCurrent(run)
                    check(initialization.size == 8) { "初始化未完成" }
                    for (result in initialization) requireOk(result)
                    // Identification queries formerly existed only to write logs.
                    for (command in listOf("ATSP6", "ATAT1", "ATH1", "ATL0", "ATS0", "ATCAF1", "ATAL")) {
                        requireCurrent(run)
                        requireOk(run.elm.command(command, 5000, 250))
                    }
                    setRunPhase(run, MonitorSessionPhase.LIVE)
                    runLive(run)
                } catch (_: Cancelled) {
                    // Explicit replacement/exit is not a failure of the new run.
                } catch (failure: Exception) {
                    ui.post { if (current(run)) lastError = "连接或采集中断；点车图重连" }
                } finally {
                    run.elm.close()
                    ui.post {
                        if (!destroying.get() && activeRun === run) {
                            activeRun = null
                            phase.set(MonitorSessionPhase.IDLE)
                            if (lastError == "NONE") lastNotice = "采集已停止；点车图重连"
                            renderDashboard()
                        }
                    }
                }
            }
        }
    }

    private fun current(run: Run) = !destroying.get() && activeRun === run && !run.cancelled.get()
    private fun requireCurrent(run: Run) { if (!current(run)) throw Cancelled() }
    private fun setRunPhase(run: Run, next: MonitorSessionPhase) {
        requireCurrent(run)
        // Lifecycle/ownership changes and phase publication share the main-thread ordering.
        ui.post {
            if (current(run)) {
                phase.set(next)
                renderDashboard()
            }
        }
    }
    private fun requireOk(result: CommandResult) {
        if (result.status != TransactionStatus.OK || !result.promptSeen)
            throw IOException("Adapter rejected ${result.command}")
    }
    private fun showFailure(message: String) {
        phase.set(MonitorSessionPhase.IDLE)
        lastError = message
        lastNotice = null
        renderDashboard()
    }

    private fun runLive(run: Run) {
        val epoch = SystemClock.elapsedRealtime()
        val scheduler = HaScheduler(epoch)
        val health = VehicleDataHealth(epoch)
        var currentHeader: String? = null
        var consecutiveBusErrors = 0
        while (current(run)) {
            if (!run.elm.isConnected()) throw IOException("Bluetooth disconnected")
            val now = SystemClock.elapsedRealtime()
            if (health.expired(now)) {
                ui.post { if (current(run)) lastError = "车辆数据不可用；点车图重连" }
                return
            }
            val request = scheduler.next(now)
            if (request == null) {
                Thread.sleep((scheduler.wakeAt(now) - now).coerceIn(1L, 100L))
                continue
            }
            if (request.header != null && request.header != currentHeader) {
                requireCurrent(run)
                requireOk(run.elm.command("ATSH${request.header}", 4000, 0, 0, 0))
                currentHeader = request.header
                // Re-evaluate releases after header latency; never drain an obsolete backlog.
                continue
            }
            requireCurrent(run)
            scheduler.dispatched(request, SystemClock.elapsedRealtime())
            val result = run.elm.command(request.command, request.timeoutMs, 0, 0, 0)
            requireCurrent(run)
            if (!result.promptSeen || result.status == TransactionStatus.TIMEOUT)
                throw IOException("ELM response boundary lost")
            synchronized(signalLock) {
                if (result.status == TransactionStatus.OK) {
                    if (decodeScheduled(request, result)) health.valid(request.header, SystemClock.elapsedRealtime())
                } else store.markDecodeFailure(requestSignals.forRequest(request.id), request.command, result)
                store.refreshStaleStates(SystemClock.elapsedRealtime())
                updateIdleCheckState(run)
            }
            if (result.status == TransactionStatus.BUS_ERROR) {
                if (++consecutiveBusErrors >= 3) throw IOException("Vehicle bus unavailable")
            } else consecutiveBusErrors = 0
            renderDashboard()
        }
    }

    /** True means at least one valid vehicle field, not just an ELM OK/ATRV reply. */
    private fun decodeScheduled(request: ScheduledRequest, result: CommandResult): Boolean {
        var valid = false
        fun <T> update(signal: SignalValue<T>, value: T?) {
            store.update(signal, value, request.command, result)
            valid = valid || value != null
        }
        when (request.id) {
            "std_core", "coolant" -> {
                val message = ObdParsers.isoTpMessage(result.rawLines, "7E8", parsedFrames = result.canFrames)
                val decoded = message?.let { ObdParsers.decodeStandardPayload(it.payload) }
                if (request.id == "std_core") {
                    update(baseline.rpm, decoded?.rpm)
                    update(baseline.speedKph, decoded?.speedKph)
                } else update(baseline.coolantC, decoded?.coolantC)
            }
            "cd_f3" -> update(hybrid.iceTorqueNm,
                ObdParsers.decode21CdF3(result.rawLines, result.canFrames)?.iceTorqueNm)
            "c3" -> {
                val decoded = ObdParsers.decode21C3(result.rawLines, result.canFrames)
                update(hybrid.socPct, decoded?.socPct)
                update(hybrid.hvPowerKw, decoded?.hvPowerKw)
            }
            "c4" -> update(hybrid.warmupActive,
                ObdParsers.decode21C4(result.rawLines, result.canFrames)?.warmupActive)
            "cf" -> {
                val decoded = ObdParsers.decode21CF(result.rawLines, result.canFrames)
                update(hybrid.batteryTempMinC, decoded?.batteryTempMinC)
                update(hybrid.batteryTempMaxC, decoded?.batteryTempMaxC)
                update(hybrid.batteryTempAvgC, decoded?.batteryTempAvgC)
            }
            "atrv" -> update(baseline.adapterVoltageV, ObdParsers.adapterVoltage(result.rawLines))
        }
        return valid
    }

    private fun renderDashboard() {
        if (destroying.get() || !dashboardVisible || !dashboardPublishPending.compareAndSet(false, true)) return
        ui.post(dashboardRenderRunnable)
    }

    private val dashboardRenderRunnable = Runnable {
        dashboardPublishPending.set(false)
        if (destroying.get() || !dashboardVisible || !::dashboard.isInitialized) return@Runnable
        val snapshot = synchronized(signalLock) {
            val nowMs = SystemClock.elapsedRealtime()
            val live = phase.get() == MonitorSessionPhase.LIVE && activeRun?.cancelled?.get() == false
            val connected = activeRun?.elm?.isConnected() == true
            fun fresh(signal: SignalValue<*>, maxAge: Long = DashboardFreshness.FAST_AGE_MS) =
                DashboardFreshness.fresh(live, connected, signal.status == SignalStatus.VALID,
                    signal.updatedAtElapsedMs, nowMs, maxAge)
            val rpmFresh = fresh(baseline.rpm)
            val batteryTempFresh = fresh(hybrid.batteryTempMinC, DashboardFreshness.TEMPERATURE_AGE_MS) &&
                fresh(hybrid.batteryTempMaxC, DashboardFreshness.TEMPERATURE_AGE_MS) &&
                fresh(hybrid.batteryTempAvgC, DashboardFreshness.TEMPERATURE_AGE_MS)
            val powerFresh = rpmFresh && fresh(hybrid.iceTorqueNm) &&
                DashboardFreshness.powerPair(baseline.rpm.updatedAtElapsedMs, hybrid.iceTorqueNm.updatedAtElapsedMs)
            val icePower = mechanicalPowerKw(baseline.rpm.value, hybrid.iceTorqueNm.value)
            DashboardSnapshot(
                speedKph = baseline.speedKph.value,
                speedFresh = fresh(baseline.speedKph),
                speedVersion = baseline.speedKph.version,
                socPct = hybrid.socPct.value,
                socFresh = fresh(hybrid.socPct),
                socVersion = hybrid.socPct.version,
                batteryTempMinC = hybrid.batteryTempMinC.value,
                batteryTempMaxC = hybrid.batteryTempMaxC.value,
                batteryTempAvgC = hybrid.batteryTempAvgC.value,
                batteryTempFresh = batteryTempFresh,
                batteryTempVersion = hybrid.batteryTempMinC.version +
                    hybrid.batteryTempMaxC.version + hybrid.batteryTempAvgC.version,
                hvPowerKw = hybrid.hvPowerKw.value,
                hvPowerFresh = fresh(hybrid.hvPowerKw),
                hvPowerVersion = hybrid.hvPowerKw.version,
                rpm = baseline.rpm.value,
                rpmFresh = rpmFresh,
                rpmVersion = baseline.rpm.version,
                coolantC = baseline.coolantC.value,
                coolantFresh = fresh(baseline.coolantC),
                coolantVersion = baseline.coolantC.version,
                adapterVoltageV = baseline.adapterVoltageV.value,
                adapterVoltageFresh = fresh(baseline.adapterVoltageV),
                adapterVoltageVersion = baseline.adapterVoltageV.version,
                icePowerKw = icePower,
                icePowerFresh = powerFresh,
                icePowerVersion = baseline.rpm.version + hybrid.iceTorqueNm.version,
                idleCheckActive = powerFresh && fresh(baseline.speedKph) && fresh(hybrid.warmupActive) &&
                    hybrid.warmupActive.value == true && fresh(hybrid.idleCheckActive) &&
                    hybrid.idleCheckActive.value == true,
                idleCheckVersion = hybrid.idleCheckActive.version
            )
        }
        if (snapshot != lastDashboardSnapshot) {
            dashboard.render(snapshot)
            lastDashboardSnapshot = snapshot
        }
        dashboard.setConnectionBusy(phase.get() in BUSY_PHASES)
        val connection = if (activeRun?.elm?.isConnected() == true) "CONNECTED" else "OFFLINE"
        dashboard.renderStatus(
            DashboardStatus(
                deviceName = deviceName ?: "OBD",
                connection = connection,
                mode = phase.get().name,
                notice = lastNotice,
                error = lastError.takeUnless { it == "NONE" },
                warning = lastError != "NONE"
            )
        )
    }


    private fun mechanicalPowerKw(rpm: Double?, torqueNm: Double?): Double? {
        if (rpm == null || torqueNm == null) return null
        return torqueNm * 2.0 * Math.PI * rpm / 60.0 / 1000.0
    }

    private fun updateIdleCheckState(run: Run) {
        val rpm = baseline.rpm
        val torque = hybrid.iceTorqueNm
        val warmup = hybrid.warmupActive
        val speed = baseline.speedKph
        val now = SystemClock.elapsedRealtime()
        fun fresh(signal: SignalValue<*>) = DashboardFreshness.fresh(
            current(run), run.elm.isConnected(), signal.status == SignalStatus.VALID,
            signal.updatedAtElapsedMs, now)
        val fresh = fresh(rpm) && fresh(torque) && fresh(warmup) && fresh(speed) &&
            DashboardFreshness.powerPair(rpm.updatedAtElapsedMs, torque.updatedAtElapsedMs)
        idleCheckState.update(warmup.value.takeIf { fresh }, rpm.value.takeIf { fresh },
            mechanicalPowerKw(rpm.value, torque.value).takeIf { fresh },
            speed.value.takeIf { fresh }, now, torque.updatedAtElapsedMs ?: now)
        store.setDerived(hybrid.idleCheckActive, idleCheckState.active.takeIf { fresh }, "IDLE_CHECK")
    }

    private val refreshUiRunnable = object : Runnable {
        override fun run() {
            if (destroying.get() || !dashboardVisible) return
            renderDashboard()
            ui.postDelayed(this, 500)
        }
    }
    private class Cancelled : Exception()
}
