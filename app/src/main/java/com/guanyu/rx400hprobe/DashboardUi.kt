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
    private val statusDeviceText: TextView
    private val statusLineText: TextView
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

    private val idleIdleColor = Color.rgb(90, 105, 95)

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
        val titleColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleColumn.addView(TextView(activity).apply {
            text = "RX400h"
            textSize = if (isWide) 30f else 26f
            setTextColor(Color.rgb(125, 255, 175))
            typeface = android.graphics.Typeface.MONOSPACE
            maxLines = 1
        })
        titleColumn.addView(TextView(activity).apply {
            text = "MONITOR"
            textSize = if (isWide) 17f else 15f
            setTextColor(Color.rgb(110, 235, 205))
            typeface = android.graphics.Typeface.MONOSPACE
            maxLines = 1
        })

        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(72)
            setPadding(0, dp(6), 0, dp(6))
        }
        top.addView(titleColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        deviceButton = smallButton("设备", onSelectDevice, isWide)
        connectButton = smallButton("连接", onConnectToggle, isWide)
        liveButton = smallButton("开始实时", onLiveToggle, isWide)
        exportButton = smallButton("结束并导出", onExport, isWide)

        val controls = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(if (isWide) dp(8) else 0, dp(4), if (isWide) dp(8) else 0, dp(4))
        }
        controls.addView(deviceButton)
        controls.addView(connectButton)
        controls.addView(liveButton)
        controls.addView(exportButton)

        // V0.3.0 header (D-033/D-034): wide keeps title / buttons / status on one
        // row with two-line title and status text; narrow keeps buttons on their
        // own row so the two-line text cannot be squeezed.
        if (isWide) {
            top.addView(controls)
        }

        val statusColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        statusDeviceText = TextView(activity).apply {
            textSize = if (isWide) 16f else 14f
            setTextColor(Color.rgb(220, 235, 225))
            gravity = Gravity.END
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        statusLineText = TextView(activity).apply {
            textSize = if (isWide) 14f else 13f
            setTextColor(Color.rgb(130, 160, 150))
            gravity = Gravity.END
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        statusColumn.addView(statusDeviceText)
        statusColumn.addView(statusLineText)
        top.addView(statusColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        rootLayout.addView(top)
        rootLayout.addView(separator())

        if (!isWide) {
            rootLayout.addView(HorizontalScrollView(activity).apply {
                isHorizontalScrollBarEnabled = false
                addView(controls)
            })
        }

        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val primary = LinearLayout(activity).apply {
            orientation = if (isWide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }

        val batteryCard = card("BATTERY")
        socValue = valueLine("电量 — %")
        batteryAvgValue = valueLine("平均温度 — °C")
        batteryDetailValue = smallInfo("最高 —°  最低 —°")
        batteryCard.addView(socValue)
        batteryCard.addView(batteryAvgValue)
        batteryCard.addView(batteryDetailValue)

        val vehicleCard = card("VEHICLE STATUS")
        speedValue = valueLine("速度 — km/h")
        coolantValue = valueLine("冷却液 — °C")
        voltageValue = valueLine("12V 供电 — V")
        vehicleCard.addView(speedValue)
        vehicleCard.addView(coolantValue)
        vehicleCard.addView(voltageValue)

        val powerCard = card("POWER")
        hvPowerValue = valueLine("混动功率 — kW")
        icePowerValue = valueLine("引擎功率 — kW")
        rpmValue = valueLine("转速 — rpm")
        idleCheckValue = valueLine("怠速检查").apply {
            setTextColor(idleIdleColor)
        }
        powerCard.addView(hvPowerValue)
        powerCard.addView(icePowerValue)
        powerCard.addView(rpmValue)
        powerCard.addView(idleCheckValue)

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
            speedValue.text = "速度 ${valueWithUnit(snapshot.speedKph, snapshot.speedFresh, "km/h", 0)}"
        }
        if (last == null || snapshot.socVersion != last.socVersion) {
            socValue.text = "电量 ${valueWithUnit(snapshot.socPct, snapshot.socFresh, "%", 1)}"
        }
        if (last == null || snapshot.batteryTempVersion != last.batteryTempVersion) {
            batteryAvgValue.text = "平均温度 ${value(snapshot.batteryTempAvgC, snapshot.batteryTempFresh, 1)} °C"
            batteryDetailValue.text =
                "最高 ${value(snapshot.batteryTempMaxC, snapshot.batteryTempFresh, 1)}°  最低 ${value(snapshot.batteryTempMinC, snapshot.batteryTempFresh, 1)}°"
        }
        if (last == null || snapshot.hvPowerVersion != last.hvPowerVersion) {
            hvPowerValue.text = "混动功率 ${valueWithUnit(snapshot.hvPowerKw, snapshot.hvPowerFresh, "kW", 1)}"
        }
        if (last == null || snapshot.rpmVersion != last.rpmVersion) {
            rpmValue.text = "转速 ${valueWithUnit(snapshot.rpm, snapshot.rpmFresh, "rpm", 0)}"
        }
        if (last == null || snapshot.coolantVersion != last.coolantVersion) {
            coolantValue.text = "冷却液 ${value(snapshot.coolantC, snapshot.coolantFresh, 0)} °C"
        }
        if (last == null || snapshot.adapterVoltageVersion != last.adapterVoltageVersion) {
            voltageValue.text = "12V 供电 ${value(snapshot.adapterVoltageV, snapshot.adapterVoltageFresh, 1)} V"
        }
        if (last == null || snapshot.icePowerVersion != last.icePowerVersion) {
            icePowerValue.text = "引擎功率 ${valueWithUnit(snapshot.icePowerKw, snapshot.icePowerFresh, "kW", 1)}"
        }
        if (last == null || snapshot.idleCheckVersion != last.idleCheckVersion) {
            idleCheckValue.text = "怠速检查"
            idleCheckValue.setTextColor(
                if (snapshot.idleCheckActive) Color.rgb(125, 255, 175) else idleIdleColor
            )
        }
        lastSnapshot = snapshot
    }

    fun renderStatus(status: DashboardStatus) {
        statusDeviceText.text = status.deviceName
        val reconnect = if (status.reconnectCount > 0) " · 重连${status.reconnectCount}" else ""
        val error = status.error?.let { " · $it" } ?: ""
        statusLineText.text = "蓝牙${connectionText(status.connection)} · 协议${modeText(status.mode)} · 数据${loggingText(status.logging)}$reconnect$error"
        statusLineText.setTextColor(if (status.warning) Color.rgb(255, 185, 80) else if (status.connection == "CONNECTED") Color.rgb(105, 240, 195) else Color.rgb(130, 160, 150))
        connectButton.text = if (status.connection == "CONNECTED") "断开" else "连接"
    }

    private fun connectionText(connection: String): String =
        if (connection == "CONNECTED") "已连接" else "未连接"

    private fun modeText(mode: String): String = when (mode) {
        "LIVE" -> "实时"
        "BUSY" -> "忙"
        else -> "空闲"
    }

    private fun loggingText(logging: String): String = when (logging) {
        "LOG" -> "记录中"
        "LOG!" -> "记录降级"
        "PACKING" -> "打包中"
        "SAVED" -> "已保存"
        "LOG ERROR" -> "记录错误"
        else -> "未记录"
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

    private fun valueLine(initial: String): TextView = TextView(activity).apply {
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

    private fun smallButton(label: String, action: () -> Unit, compact: Boolean): Button = Button(activity).apply {
        text = label
        textSize = if (compact) 15f else 17f
        minHeight = dp(52)
        minimumHeight = dp(52)
        minWidth = if (compact) dp(88) else dp(96)
        minimumWidth = if (compact) dp(88) else dp(96)
        setOnClickListener { action() }
        setPadding(
            if (compact) dp(14) else dp(20),
            if (compact) dp(10) else dp(12),
            if (compact) dp(14) else dp(20),
            if (compact) dp(10) else dp(12)
        )
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
