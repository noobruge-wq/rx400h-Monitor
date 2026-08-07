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

internal data class DashboardSnapshot(
    val speedKph: Double?,
    val speedFresh: Boolean,
    val socPct: Double?,
    val socFresh: Boolean,
    val batteryTempMinC: Double?,
    val batteryTempMaxC: Double?,
    val batteryTempAvgC: Double?,
    val batteryTempFresh: Boolean,
    val hvPowerKw: Double?,
    val hvPowerFresh: Boolean,
    val rpm: Double?,
    val rpmFresh: Boolean,
    val coolantC: Double?,
    val coolantFresh: Boolean,
    val adapterVoltageV: Double?,
    val adapterVoltageFresh: Boolean,
    val engineState: String,
    val warmupText: String,
    val icePowerKw: Double?,
    val mg1PowerKw: Double?,
    val mg2PowerKw: Double?,
    val rearMgPowerKw: Double?
)

internal data class DashboardStatus(
    val deviceName: String,
    val connection: String,
    val mode: String,
    val logging: String,
    val reconnectCount: Int,
    val error: String?,
    val warning: Boolean
)

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
    private val batteryTempValue: TextView
    private val batteryDetailValue: TextView
    private val hvPowerValue: TextView
    private val hvPowerMode: TextView
    private val rpmValue: TextView
    private val coolantValue: TextView
    private val voltageValue: TextView
    private val engineStateValue: TextView
    private val warmupValue: TextView
    private val powertrainValue: TextView

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
        controls.addView(deviceButton)
        connectButton = smallButton("连接", onConnectToggle)
        liveButton = smallButton("开始实时", onLiveToggle)
        exportButton = smallButton("结束并导出", onExport)
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
        val primary = LinearLayout(activity).apply { orientation = if (isWide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }

        val speedCard = card("VEHICLE SPEED")
        speedValue = bigValue("— km/h", if (isWide) 62f else 52f)
        speedCard.addView(speedValue)
        speedCard.addView(smallInfo("驾驶主读数"))

        val batteryCard = card("HV BATTERY")
        socValue = bigValue("— %", if (isWide) 52f else 46f)
        batteryTempValue = mediumValue("MAX — °C")
        batteryDetailValue = smallInfo("MIN —   AVG —")
        batteryCard.addView(socValue)
        batteryCard.addView(batteryTempValue)
        batteryCard.addView(batteryDetailValue)

        val powerCard = card("BATTERY POWER")
        hvPowerValue = bigValue("— kW", if (isWide) 52f else 46f)
        hvPowerMode = mediumValue("UNKNOWN")
        powerCard.addView(hvPowerValue)
        powerCard.addView(hvPowerMode)

        if (isWide) {
            primary.addView(speedCard, weightedCard(0.33f))
            primary.addView(batteryCard, weightedCard(0.33f))
            primary.addView(powerCard, weightedCard(0.34f))
        } else {
            primary.addView(speedCard, fullCard())
            primary.addView(batteryCard, fullCard())
            primary.addView(powerCard, fullCard())
        }
        body.addView(primary, if (isWide) weightedRow(0.48f) else wrapRow())

        val secondary = LinearLayout(activity).apply { orientation = if (isWide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        val engineCard = card("ENGINE")
        rpmValue = bigValue("— rpm", if (isWide) 38f else 34f)
        coolantValue = mediumValue("COOLANT — °C")
        engineStateValue = mediumValue("STATE UNKNOWN")
        engineCard.addView(rpmValue)
        engineCard.addView(coolantValue)
        engineCard.addView(engineStateValue)

        val systemCard = card("HYBRID SYSTEM")
        warmupValue = mediumValue("WARMUP —")
        voltageValue = mediumValue("12V OBD — V")
        powertrainValue = smallInfo("ICE — kW   MG1 — kW   MG2 — kW   MGR — kW")
        systemCard.addView(warmupValue)
        systemCard.addView(voltageValue)
        systemCard.addView(powertrainValue)

        if (isWide) {
            secondary.addView(engineCard, weightedCard(0.42f))
            secondary.addView(systemCard, weightedCard(0.58f))
        } else {
            secondary.addView(engineCard, fullCard())
            secondary.addView(systemCard, fullCard())
        }
        body.addView(secondary, if (isWide) weightedRow(0.52f) else wrapRow())
        rootLayout.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root = rootLayout
    }

    fun render(snapshot: DashboardSnapshot) {
        speedValue.text = valueWithUnit(snapshot.speedKph, snapshot.speedFresh, "km/h", 0)
        socValue.text = valueWithUnit(snapshot.socPct, snapshot.socFresh, "%", 1)
        batteryTempValue.text = "MAX ${value(snapshot.batteryTempMaxC, snapshot.batteryTempFresh, 1)} °C"
        batteryDetailValue.text = "MIN ${value(snapshot.batteryTempMinC, snapshot.batteryTempFresh, 1)}°   AVG ${value(snapshot.batteryTempAvgC, snapshot.batteryTempFresh, 1)}°"
        hvPowerValue.text = valueWithUnit(snapshot.hvPowerKw, snapshot.hvPowerFresh, "kW", 1)
        hvPowerMode.text = when (snapshot.hvPowerKw) {
            null -> "UNKNOWN"
            else -> when {
                snapshot.hvPowerKw > 0.5 -> "DISCHARGE / TRACTION"
                snapshot.hvPowerKw < -0.5 -> "CHARGE / REGEN"
                else -> "HV NEUTRAL"
            }
        }
        rpmValue.text = valueWithUnit(snapshot.rpm, snapshot.rpmFresh, "rpm", 0)
        coolantValue.text = "COOLANT ${value(snapshot.coolantC, snapshot.coolantFresh, 0)} °C"
        voltageValue.text = "12V OBD ${value(snapshot.adapterVoltageV, snapshot.adapterVoltageFresh, 1)} V"
        engineStateValue.text = snapshot.engineState
        warmupValue.text = snapshot.warmupText
        powertrainValue.text = "ICE ${kw(snapshot.icePowerKw)}   MG1 ${kw(snapshot.mg1PowerKw)}   MG2 ${kw(snapshot.mg2PowerKw)}   MGR ${kw(snapshot.rearMgPowerKw)}"
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

    private fun valueWithUnit(v: Double?, fresh: Boolean, unit: String, digits: Int): String = "${value(v, fresh, digits)} $unit"
    private fun value(v: Double?, fresh: Boolean, digits: Int): String {
        val text = v?.let { String.format(Locale.US, "%.${digits}f", it) } ?: "—"
        return if (fresh) text else "$text·"
    }
    private fun kw(v: Double?): String = v?.let { String.format(Locale.US, "%.1f kW", it) } ?: "— kW"

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
