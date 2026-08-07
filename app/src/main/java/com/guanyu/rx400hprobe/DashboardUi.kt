package com.guanyu.rx400hprobe

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * V0.2.0 three-domain dashboard renderer.
 *
 * Consumes DashboardSnapshot only. Per-field versions make updates
 * change-driven: an unchanged signal does not reformat or setText.
 */
internal class DashboardUi(
    private val activity: Activity,
    onSelectDevice: () -> Unit,
    onConnectToggle: () -> Unit,
    onLiveToggle: () -> Unit,
    onExport: () -> Unit
) {
    val root: View
    private val statusText: TextView
    private val deviceButton: Button
    private val connectButton: Button
    private val liveButton: Button
    private val exportButton: Button

    private val speedValue: TextView
    private val socValue: TextView
    private val batteryAvgValue: TextView
    private val batteryDetailValue: TextView
    private val coolantValue: TextView
    private val voltageValue: TextView
    private val icePowerValue: TextView
    private val rpmValue: TextView
    private val idleCheckValue: TextView
    private val hvPowerValue: TextView

    private var lastSnapshot: DashboardSnapshot? = null

    init {
        val metrics = activity.resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        val heightDp = metrics.heightPixels / metrics.density
        val isWide = widthDp >= 600f || widthDp > heightDp

        val rootLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(2, 8, 7))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(activity).apply {
            text = "RX400h MONITOR"
            textSize = if (isWide) 27f else 22f
            setTextColor(Color.rgb(125, 255, 175))
            typeface = android.graphics.Typeface.MONOSPACE
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        statusText = TextView(activity).apply {
            textSize = 13f
            setTextColor(Color.rgb(110, 235, 205))
            gravity = Gravity.END
            setPadding(dp(8), 0, dp(8), 0)
        }
        top.addView(statusText)
        rootLayout.addView(top)
        rootLayout.addView(separator())

        val controls = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        deviceButton = smallButton("设备", onSelectDevice)
        connectButton = smallButton("连接", onConnectToggle)
        liveButton = smallButton("开始实时", onLiveToggle)
        exportButton = smallButton("结束并导出", onExport)
        controls.addView(deviceButton)
        controls.addView(connectButton)
        controls.addView(liveButton)
        controls.addView(exportButton)
        rootLayout.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(controls)
        })

        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val primary = LinearLayout(activity).apply {
            orientation = if (isWide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }

        val batteryCard = card("BATTERY")
        socValue = bigValue("— %", if (isWide) 44f else 40f)
        batteryAvgValue = mediumValue("AVG — °C")
        batteryDetailValue = smallInfo("MAX —°   MIN —°")
        batteryCard.addView(socValue)
        batteryCard.addView(batteryAvgValue)
        batteryCard.addView(batteryDetailValue)

        val vehicleCard = card("VEHICLE STATUS")
        speedValue = bigValue("— km/h", if (isWide) 44f else 40f)
        coolantValue = mediumValue("COOLANT — °C")
        voltageValue = mediumValue("12V OBD — V")
        vehicleCard.addView(speedValue)
        vehicleCard.addView(coolantValue)
        vehicleCard.addView(voltageValue)

        val powerCard = card("POWER")
        icePowerValue = bigValue("— kW", if (isWide) 44f else 40f)
        rpmValue = mediumValue("— rpm")
        idleCheckValue = mediumValue("")
        hvPowerValue = mediumValue("HV — kW")
        powerCard.addView(icePowerValue)
        powerCard.addView(rpmValue)
        powerCard.addView(idleCheckValue)
        powerCard.addView(hvPowerValue)

        if (isWide) {
            primary.addView(batteryCard, weightedCard(0.34f))
            primary.addView(vehicleCard, weightedCard(0.33f))
            primary.addView(powerCard, weightedCard(0.33f))
        } else {
            primary.addView(batteryCard, fullCard())
            primary.addView(vehicleCard, fullCard())
            primary.addView(powerCard, fullCard())
        }
        body.addView(primary, if (isWide) weightedRow(0.62f) else wrapRow())
        rootLayout.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root = rootLayout
    }

    fun render(snapshot: DashboardSnapshot) {
        val last = lastSnapshot
        if (last == null || snapshot.speedVersion != last.speedVersion) {
            speedValue.text = valueWithUnit(snapshot.speedKph, snapshot.speedFresh, "km/h", 0)
        }
        if (last == null || snapshot.socVersion != last.socVersion) {
            socValue.text = valueWithUnit(snapshot.socPct, snapshot.socFresh, "%", 1)
        }
        if (last == null || snapshot.batteryTempVersion != last.batteryTempVersion) {
            batteryAvgValue.text = "AVG ${value(snapshot.batteryTempAvgC, snapshot.batteryTempFresh, 1)} °C"
            batteryDetailValue.text =
                "MAX ${value(snapshot.batteryTempMaxC, snapshot.batteryTempFresh, 1)}°   MIN ${value(snapshot.batteryTempMinC, snapshot.batteryTempFresh, 1)}°"
        }
        if (last == null || snapshot.hvPowerVersion != last.hvPowerVersion) {
            hvPowerValue.text = "HV ${valueWithUnit(snapshot.hvPowerKw, snapshot.hvPowerFresh, "kW", 1)}"
        }
        if (last == null || snapshot.rpmVersion != last.rpmVersion) {
            rpmValue.text = valueWithUnit(snapshot.rpm, snapshot.rpmFresh, "rpm", 0)
        }
        if (last == null || snapshot.coolantVersion != last.coolantVersion) {
            coolantValue.text = "COOLANT ${value(snapshot.coolantC, snapshot.coolantFresh, 0)} °C"
        }
        if (last == null || snapshot.adapterVoltageVersion != last.adapterVoltageVersion) {
            voltageValue.text = "12V OBD ${value(snapshot.adapterVoltageV, snapshot.adapterVoltageFresh, 1)} V"
        }
        if (last == null || snapshot.icePowerVersion != last.icePowerVersion) {
            icePowerValue.text = valueWithUnit(snapshot.icePowerKw, snapshot.icePowerFresh, "kW", 1)
        }
        if (last == null || snapshot.idleCheckVersion != last.idleCheckVersion) {
            idleCheckValue.text = if (snapshot.idleCheckActive) "IDLE CHECK" else ""
        }
        lastSnapshot = snapshot
    }

    fun renderStatus(status: DashboardStatus) {
        val reconnect = if (status.reconnectCount > 0) " · R${status.reconnectCount}" else ""
        val error = status.error?.let { " · $it" } ?: ""
        statusText.text = "${status.deviceName} · ${status.connection} · ${status.mode} · ${status.logging}$reconnect$error"
        statusText.setTextColor(if (status.warning) Color.rgb(255, 185, 80) else if (status.connection == "CONNECTED") Color.rgb(105, 240, 195) else Color.rgb(130, 160, 150))
        connectButton.text = if (status.connection == "CONNECTED") "断开" else "连接"
    }

    fun setControlsEnabled(enabled: Boolean, live: Boolean) {
        deviceButton.isEnabled = enabled && !live
        connectButton.isEnabled = enabled && !live
        exportButton.isEnabled = enabled
        liveButton.isEnabled = enabled || live
    }

    fun setLiveButton(live: Boolean) {
        liveButton.text = if (live) "停止实时" else "开始实时"
    }

    private fun valueWithUnit(v: Double?, fresh: Boolean, unit: String, digits: Int): String =
        "${value(v, fresh, digits)} $unit"

    private fun value(v: Double?, fresh: Boolean, digits: Int): String {
        val text = v?.let { String.format(Locale.US, "%.${digits}f", it) } ?: "—"
        return if (fresh) text else "$text·"
    }

    private fun card(title: String): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.rgb(3, 14, 12))
            setStroke(dp(1), Color.rgb(30, 205, 175))
            cornerRadius = dp(8).toFloat()
        }
        addView(TextView(activity).apply {
            text = title
            textSize = 14f
            setTextColor(Color.rgb(70, 215, 210))
            typeface = android.graphics.Typeface.MONOSPACE
        })
    }

    private fun bigValue(initial: String, sizeSp: Float): TextView = TextView(activity).apply {
        text = initial
        textSize = sizeSp
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(125, 255, 145))
        typeface = android.graphics.Typeface.MONOSPACE
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun mediumValue(initial: String): TextView = TextView(activity).apply {
        text = initial
        textSize = 20f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(125, 255, 175))
        typeface = android.graphics.Typeface.MONOSPACE
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun smallInfo(initial: String): TextView = TextView(activity).apply {
        text = initial
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(95, 205, 175))
        typeface = android.graphics.Typeface.MONOSPACE
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun smallButton(label: String, action: () -> Unit): Button = Button(activity).apply {
        text = label
        textSize = 12f
        setOnClickListener { action() }
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(12), dp(4), dp(12), dp(4))
    }

    private fun separator(): View = View(activity).apply {
        setBackgroundColor(Color.rgb(20, 125, 115))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(6)
            bottomMargin = dp(6)
        }
    }

    private fun fullCard() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).margin(dp(4))
    private fun weightedCard(weight: Float) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).margin(dp(4))
    private fun weightedRow(weight: Float) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, weight)
    private fun wrapRow() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun LinearLayout.LayoutParams.margin(v: Int) = apply { setMargins(v, v, v, v) }
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
