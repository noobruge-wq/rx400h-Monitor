package com.guanyu.rx400hprobe

import android.app.Activity
import android.os.Bundle

/** Debug-only deterministic fixture for responsive visual acceptance screenshots. */
class DashboardPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dashboard = DashboardUi(
            activity = this,
            onSelectDevice = {},
            onStart = {},
            onEnd = {}
        )
        dashboard.render(
            DashboardSnapshot(
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
        )
        dashboard.renderStatus(
            DashboardStatus(
                deviceName = "OBDLink MX+ 99905",
                connection = "CONNECTED",
                mode = "LIVE",
                logging = "LOG",
                reconnectCount = 0,
                notice = null,
                error = null,
                warning = false
            )
        )
        dashboard.setControlState(
            MonitorControlState(
                deviceEnabled = false,
                startEnabled = false,
                endEnabled = true
            )
        )
        setContentView(dashboard.root)
    }
}
