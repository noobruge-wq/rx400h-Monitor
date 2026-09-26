package com.guanyu.rx400hprobe

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Debug-only deterministic fixture for responsive visual acceptance screenshots. */
class DashboardPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dashboard = PixelDashboardView(
            context = this,
            onSelectDevice = {},
            onReconnect = {}
        )
        dashboard.useReferenceCompositionForDebug(
            intent.getBooleanExtra("reference_renderer", false))
        val fixture = DashboardSnapshot(
                speedKph = 42.0,
                speedFresh = true,
                speedVersion = 1L,
                socPct = 61.5,
                socFresh = true,
                socVersion = 1L,
                batteryTempMinC = 27.0,
                batteryTempMaxC = 33.0,
                batteryTempAvgC = 29.8,
                batteryTempFresh = true,
                batteryTempVersion = 1L,
                hvPowerKw = -8.4,
                hvPowerFresh = true,
                hvPowerVersion = 1L,
                rpm = 1340.0,
                rpmFresh = true,
                rpmVersion = 1L,
                coolantC = 86.0,
                coolantFresh = true,
                coolantVersion = 1L,
                adapterVoltageV = 13.8,
                adapterVoltageFresh = true,
                adapterVoltageVersion = 1L,
                icePowerKw = 18.7,
                icePowerFresh = true,
                icePowerVersion = 1L,
                idleCheckActive = intent.getBooleanExtra("idle_active", false),
                idleCheckVersion = 1L
            )
        dashboard.render(fixture)
        dashboard.renderStatus(
            DashboardStatus(
                deviceName = "OBDLink MX+ 99905",
                connection = "CONNECTED",
                mode = "LIVE",
                notice = null,
                error = null,
                warning = false
            )
        )
        setContentView(dashboard)
        if (intent.getBooleanExtra("verify_composition", false)) {
            val view = dashboard
            view.verifyCompositionForDebug()
            val handler = Handler(Looper.getMainLooper())
            var step = 0
            val advance = object : Runnable {
                override fun run() {
                    if (isFinishing || isDestroyed) return
                    val fresh = step % 7 != 0
                    view.render(fixture.copy(
                        socPct = (step * 11 % 101).toDouble(), socVersion = step.toLong(), socFresh = fresh,
                        hvPowerKw = (step * 13 % 101 - 50).toDouble(), hvPowerVersion = step.toLong(), hvPowerFresh = fresh,
                        speedKph = (step * 7 % 121).toDouble(), speedVersion = step.toLong(), speedFresh = fresh,
                        rpmFresh = fresh, icePowerFresh = fresh, batteryTempFresh = fresh,
                        coolantFresh = fresh, adapterVoltageFresh = fresh,
                        idleCheckActive = step % 2 == 0, idleCheckVersion = step.toLong()))
                    if (step % 10 == 0) view.armStartSelfTest()
                    step++
                    if (step < 40) handler.postDelayed(this, 150L)
                    else handler.postDelayed({ Log.i("RX400hPixelOracle", "COMPLETE samples=40") }, 1500L)
                }
            }
            handler.postDelayed(advance, 200L)
        }
    }
}
