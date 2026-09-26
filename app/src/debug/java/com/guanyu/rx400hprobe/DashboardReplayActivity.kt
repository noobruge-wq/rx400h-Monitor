package com.guanyu.rx400hprobe

import android.app.Activity
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import android.view.WindowManager

/**
 * Debug-only dynamic fixture. No Bluetooth, logger, session or scheduler access.
 * Intent: replay_mode=recorded|stress5|wheel|parked, autostart=true (optional).
 * Uses the actual production PixelDashboardView, not a substitute renderer.
 */
class DashboardReplayActivity : Activity() {
    private lateinit var dashboard: PixelDashboardView
    private lateinit var replay: DashboardReplay
    private val handler = Handler(Looper.getMainLooper())
    private var mode = "recorded"
    private var runId = ""
    private var running = false
    private var startedMs = 0L
    private var metricMs = 0L
    private var cpuMs = 0L
    private var lastIndex = -1
    private var published = 0L
    private var skipped = 0L
    private var lateMaxMs = 0L
    private var frames = 0L
    private var frameNs = 0L
    private var frameMaxNs = 0L
    private var over50Ms = 0L
    private var frameMetricDrops = 0L
    private var listenerAttached = false
    private var autostartPending = false
    private val frameListener = Window.OnFrameMetricsAvailableListener { _, metrics, drops ->
        if (running) {
            val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (duration >= 0) {
                frames++
                frameNs += duration
                frameMaxNs = maxOf(frameMaxNs, duration)
                if (duration > 50_000_000L) over50Ms++
            }
            frameMetricDrops += drops
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val index = replay.indexAt(now - startedMs)
            if (index > lastIndex) {
                skipped += index - lastIndex - 1L
                lateMaxMs = maxOf(lateMaxMs, now - startedMs - replay.samples[index].atMs)
                dashboard.render(replay.samples[index].snapshot)
                published++
                lastIndex = index
            }
            if (now - startedMs >= replay.samples.last().atMs + 2_000L) {
                stopReplay("complete")
                return
            }
            val nextMs = if (index < replay.samples.lastIndex) replay.samples[index + 1].atMs
                         else replay.samples.last().atMs + 2_000L
            handler.postAtTime(this, startedMs + nextMs)
        }
    }
    private val metricTick = object : Runnable {
        override fun run() {
            if (!running) return
            report("window")
            handler.postDelayed(this, 5_000L)
        }
    }
    private val autoStart = Runnable { startReplay() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("replay_mode") ?: "recorded"
        runId = intent.getStringExtra("replay_run_id")?.takeIf { it.matches(Regex("[a-zA-Z0-9-]{1,64}")) }
            ?: SystemClock.uptimeMillis().toString()
        replay = if (mode == "recorded") {
            assets.open("renderer_replay/recorded.csv").bufferedReader().use { reader ->
                DashboardReplay.recorded(reader.lineSequence())
            }
        } else DashboardReplay.synthetic(mode)
        dashboard = PixelDashboardView(this, {}, { startReplay() })
        dashboard.useReferenceCompositionForDebug(intent.getBooleanExtra("reference_renderer", false))
        dashboard.renderStatus(DashboardStatus(
            "UI TEST / $mode", "DISCONNECTED", "IDLE", "NO OBD / 本地回放", null, false
        ))
        setContentView(dashboard)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        autostartPending = savedInstanceState == null && intent.getBooleanExtra("autostart", false)
        Log.i(TAG, "loaded mode=$mode samples=${replay.samples.size} durationMs=${replay.samples.last().atMs} " +
            "contract=UI_ONLY valuesFreshAssumed=true runtimeRenderer=D060 referenceRenderer=${intent.getBooleanExtra("reference_renderer", false)}")
    }

    override fun onResume() {
        super.onResume()
        window.addOnFrameMetricsAvailableListener(frameListener, handler)
        listenerAttached = true
        if (autostartPending) {
            autostartPending = false
            handler.postDelayed(autoStart, 1_000L)
        }
    }

    private fun startReplay() {
        if (running) return
        // Establish a different initial version so a restart also receives new data.
        dashboard.render(replay.samples.first().snapshot.copy(socVersion = -1, hvPowerVersion = -1))
        dashboard.armStartSelfTest()
        lastIndex = -1
        published = 0
        skipped = 0
        running = true
        startedMs = SystemClock.uptimeMillis()
        metricMs = SystemClock.elapsedRealtime()
        cpuMs = Process.getElapsedCpuTime()
        resetFrameMetrics()
        Log.i(TAG, "start mode=$mode runId=$runId plannedSnapshots=${replay.samples.size} uptimeMs=$startedMs " +
            "warmupMs=10000 prefs=existing_local_no_override")
        handler.post(tick)
        handler.postDelayed(metricTick, 5_000L)
    }

    private fun stopReplay(reason: String) {
        handler.removeCallbacks(autoStart)
        handler.removeCallbacks(tick)
        handler.removeCallbacks(metricTick)
        if (!running) return
        report(reason)
        running = false
        if (lastIndex >= 0) {
            val last = replay.samples[lastIndex].snapshot
            dashboard.render(last.copy(speedFresh = false, speedVersion = last.speedVersion + 1))
        }
    }

    private fun resetFrameMetrics() {
        frames = 0
        frameNs = 0
        frameMaxNs = 0
        over50Ms = 0
        frameMetricDrops = 0
        lateMaxMs = 0
    }

    private fun report(reason: String) {
        val now = SystemClock.elapsedRealtime()
        val interval = (now - metricMs).coerceAtLeast(1)
        val cpu = Process.getElapsedCpuTime()
        // PSS is sampled after the timed boundary. Its overhead remains part of
        // the next process window; never described as isolated renderer CPU.
        Log.i(TAG, "reason=$reason mode=$mode runId=$runId elapsedMs=${SystemClock.uptimeMillis() - startedMs} " +
            "windowMs=$interval processCpuPct=${(cpu - cpuMs) * 100.0 / interval} pssKb=${Debug.getPss()} " +
            "published=$published sourceIndex=$lastIndex skipped=$skipped lateMaxMs=$lateMaxMs windowFrames=$frames " +
            "windowFrameHz=${frames * 1000.0 / interval} totalDurationAvgUs=${frameNs / frames.coerceAtLeast(1) / 1000} " +
            "totalDurationMaxUs=${frameMaxNs / 1000} over50Ms=$over50Ms frameMetricDrops=$frameMetricDrops")
        metricMs = now
        cpuMs = cpu
        resetFrameMetrics()
    }

    override fun onPause() {
        stopReplay("background_abort")
        if (listenerAttached) {
            window.removeOnFrameMetricsAvailableListener(frameListener)
            listenerAttached = false
        }
        super.onPause()
    }

    companion object { private const val TAG = "RX400hReplay" }
}
