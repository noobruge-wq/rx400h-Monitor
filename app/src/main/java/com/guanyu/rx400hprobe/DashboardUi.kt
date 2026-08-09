package com.guanyu.rx400hprobe

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/**
 * V0.3.0 responsive three-domain dashboard renderer.
 *
 * Consumes DashboardSnapshot only. Per-field versions make updates
 * change-driven: an unchanged signal does not reformat or setText. Layout is
 * derived from the current window size (D-039): cards reflow into a dynamic
 * column grid and the data area scrolls instead of shrinking text.
 */
internal class DashboardUi(
    private val activity: Activity,
    onSelectDevice: () -> Unit,
    onConnectToggle: () -> Unit,
    onLiveToggle: () -> Unit,
    onExport: () -> Unit,
    onLayoutChange: () -> Unit
) {
    val root: View
    private val statusDeviceText: TextView
    private val statusBleText: TextView
    private val statusProtoText: TextView
    private val statusDataText: TextView
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
    private val valueColor = Color.rgb(125, 255, 175)
    private val dimColor = Color.rgb(95, 205, 175)
    private val titleColor = Color.rgb(70, 215, 210)
    private val fontScale: Float
    private var lastLayoutKey = -1

    init {
        // V0.3.0 responsive UI (D-039): derive everything from the actual app
        // window (configuration screenWidthDp/HeightDp), so rotation,
        // split-screen and freeform resizes reflow instead of assuming a fixed
        // resolution or aspect ratio.
        val config = activity.resources.configuration
        var widthDp = config.screenWidthDp.toFloat()
        var heightDp = config.screenHeightDp.toFloat()
        if (widthDp <= 0f || heightDp <= 0f) {
            val metrics = activity.resources.displayMetrics
            widthDp = metrics.widthPixels / metrics.density
            heightDp = metrics.heightPixels / metrics.density
        }
        fontScale = (minOf(widthDp, heightDp) / 720f).coerceAtLeast(0.5f)
        val valueSp = 40f * fontScale
        val smallSp = 26f * fontScale
        val columns = ResponsiveLayout.columnCount(widthDp.toInt())
        val buttonsInHeader = widthDp >= 720f
        val rowRanges = ResponsiveLayout.rowPlan(3, columns)

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
            textSize = (if (buttonsInHeader) 30f else 26f) * fontScale
            setTextColor(Color.rgb(125, 255, 175))
            typeface = android.graphics.Typeface.MONOSPACE
            maxLines = 1
        })
        titleColumn.addView(TextView(activity).apply {
            text = "MONITOR"
            textSize = (if (buttonsInHeader) 17f else 15f) * fontScale
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

        // V0.3.0 header v5 (D-036): buttons live inside the header between the
        // title and status columns on wide screens so no separate button row
        // wastes vertical space; narrow screens keep the scrollable row below.
        deviceButton = smallButton("设备", onSelectDevice, buttonsInHeader)
        connectButton = smallButton("连接", onConnectToggle, buttonsInHeader)
        liveButton = smallButton("开始实时", onLiveToggle, buttonsInHeader)
        exportButton = smallButton("结束并导出", onExport, buttonsInHeader)

        val controls = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(if (buttonsInHeader) dp(6) else 0, dp(2), if (buttonsInHeader) dp(6) else 0, dp(2))
        }
        controls.addView(deviceButton)
        controls.addView(connectButton)
        controls.addView(liveButton)
        controls.addView(exportButton)
        if (buttonsInHeader) {
            top.addView(controls)
        }

        val statusColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        statusDeviceText = TextView(activity).apply {
            textSize = (if (buttonsInHeader) 17f else 15f) * fontScale
            setTextColor(Color.rgb(220, 235, 225))
            gravity = Gravity.END
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        fun statusLine(): TextView = TextView(activity).apply {
            textSize = (if (buttonsInHeader) 14f else 12f) * fontScale
            setTextColor(Color.rgb(130, 160, 150))
            gravity = Gravity.END
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        statusBleText = statusLine()
        statusProtoText = statusLine()
        statusDataText = statusLine()
        statusColumn.addView(statusDeviceText)
        statusColumn.addView(statusBleText)
        statusColumn.addView(statusProtoText)
        statusColumn.addView(statusDataText)
        top.addView(statusColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f))
        rootLayout.addView(top)
        rootLayout.addView(separator())

        if (!buttonsInHeader) {
            rootLayout.addView(HorizontalScrollView(activity).apply {
                isHorizontalScrollBarEnabled = false
                addView(controls)
            })
        }

        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        val batteryCard = card("能量域")
        batteryCard.addView(domainText("电量", valueSp, valueColor))
        socValue = domainText("— %", valueSp, valueColor)
        batteryCard.addView(socValue)
        batteryCard.addView(domainText("温度", valueSp, valueColor))
        batteryAvgValue = domainText("— °C", valueSp, valueColor)
        batteryCard.addView(batteryAvgValue)
        batteryCard.addView(domainText("最高 最低", smallSp, dimColor))
        batteryDetailValue = domainText("—°  —°", smallSp, dimColor)
        batteryCard.addView(batteryDetailValue)

        val vehicleCard = card("车辆域")
        vehicleCard.addView(domainText("速度", valueSp, valueColor))
        speedValue = domainText("— km/h", valueSp, valueColor)
        vehicleCard.addView(speedValue)
        vehicleCard.addView(domainText("冷却液", valueSp, valueColor))
        coolantValue = domainText("— °C", valueSp, valueColor)
        vehicleCard.addView(coolantValue)
        vehicleCard.addView(domainText("12V 供电", valueSp, valueColor))
        voltageValue = domainText("— V", valueSp, valueColor)
        vehicleCard.addView(voltageValue)

        val powerCard = card("动力域")
        powerCard.addView(domainText("混动功率", valueSp, valueColor))
        hvPowerValue = domainText("— kW", valueSp, valueColor)
        powerCard.addView(hvPowerValue)
        powerCard.addView(domainText("引擎功率", valueSp, valueColor))
        icePowerValue = domainText("— kW", valueSp, valueColor)
        powerCard.addView(icePowerValue)
        powerCard.addView(domainText("转速", valueSp, valueColor))
        rpmValue = domainText("— rpm", valueSp, valueColor)
        powerCard.addView(rpmValue)
        idleCheckValue = domainText("怠速检查", valueSp, idleIdleColor).apply {
            setTextColor(idleIdleColor)
        }
        powerCard.addView(idleCheckValue)

        // Responsive reflow (D-039): rows come from the computed column count;
        // every card in a row shares the width equally. The whole data area is
        // inside a vertical ScrollView, so a short window scrolls instead of
        // forcing text to shrink.
        for (range in rowRanges) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            for (index in range) {
                val card = when (index) {
                    0 -> batteryCard
                    1 -> vehicleCard
                    else -> powerCard
                }
                row.addView(card, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).margin(dp(4)))
            }
            body.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = true
            addView(body)
        }
        rootLayout.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root = rootLayout
        lastLayoutKey = layoutKey(widthDp.toInt(), heightDp.toInt(), columns, buttonsInHeader)
        attachLayoutObserver(onLayoutChange)
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
            batteryAvgValue.text = "${value(snapshot.batteryTempAvgC, snapshot.batteryTempFresh, 1)} °C"
            batteryDetailValue.text =
                "${value(snapshot.batteryTempMaxC, snapshot.batteryTempFresh, 1)}°  ${value(snapshot.batteryTempMinC, snapshot.batteryTempFresh, 1)}°"
        }
        if (last == null || snapshot.hvPowerVersion != last.hvPowerVersion) {
            hvPowerValue.text = valueWithUnit(snapshot.hvPowerKw, snapshot.hvPowerFresh, "kW", 1)
        }
        if (last == null || snapshot.rpmVersion != last.rpmVersion) {
            rpmValue.text = valueWithUnit(snapshot.rpm, snapshot.rpmFresh, "rpm", 0)
        }
        if (last == null || snapshot.coolantVersion != last.coolantVersion) {
            coolantValue.text = "${value(snapshot.coolantC, snapshot.coolantFresh, 0)} °C"
        }
        if (last == null || snapshot.adapterVoltageVersion != last.adapterVoltageVersion) {
            voltageValue.text = "${value(snapshot.adapterVoltageV, snapshot.adapterVoltageFresh, 1)} V"
        }
        if (last == null || snapshot.icePowerVersion != last.icePowerVersion) {
            icePowerValue.text = valueWithUnit(snapshot.icePowerKw, snapshot.icePowerFresh, "kW", 1)
        }
        if (last == null || snapshot.idleCheckVersion != last.idleCheckVersion) {
            idleCheckValue.text = "怠速检查"
            idleCheckValue.setTextColor(
                if (snapshot.idleCheckActive) valueColor else idleIdleColor
            )
        }
        lastSnapshot = snapshot
    }

    fun renderStatus(status: DashboardStatus) {
        statusDeviceText.text = status.deviceName
        val reconnect = if (status.reconnectCount > 0) "·重连${status.reconnectCount}" else ""
        val error = status.error?.let { "·$it" } ?: ""
        statusBleText.text = "蓝牙${connectionText(status.connection)}"
        statusProtoText.text = "协议${modeText(status.mode)}"
        statusDataText.text = "数据${loggingText(status.logging)}$reconnect$error"
        val stateColor = if (status.warning) Color.rgb(255, 185, 80) else if (status.connection == "CONNECTED") Color.rgb(105, 240, 195) else Color.rgb(130, 160, 150)
        statusBleText.setTextColor(stateColor)
        statusProtoText.setTextColor(stateColor)
        statusDataText.setTextColor(stateColor)
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
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.rgb(3, 14, 12))
            setStroke(dp(1), Color.rgb(30, 205, 175))
            cornerRadius = dp(8).toFloat()
        }
        addView(TextView(activity).apply {
            text = title
            textSize = 28f * fontScale
            setTextColor(titleColor)
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })
    }

    private fun domainText(initial: String, sizeSp: Float, color: Int): TextView = TextView(activity).apply {
        text = initial
        textSize = sizeSp
        gravity = Gravity.CENTER
        setTextColor(color)
        typeface = android.graphics.Typeface.MONOSPACE
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun smallButton(label: String, action: () -> Unit, compact: Boolean): Button = Button(activity).apply {
        text = label
        textSize = (if (compact) 14f else 16f) * fontScale
        val buttonHeight = dp(64)
        minHeight = buttonHeight
        minimumHeight = buttonHeight
        val buttonWidth = dp(if (compact) 56 else 72)
        minWidth = buttonWidth
        minimumWidth = buttonWidth
        setOnClickListener { action() }
        val padX = dp(if (compact) 12 else 16)
        val padY = dp(if (compact) 8 else 10)
        setPadding(padX, padY, padX, padY)
    }

    private fun separator(): View = View(activity).apply {
        setBackgroundColor(Color.rgb(20, 125, 115))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(6)
            bottomMargin = dp(6)
        }
    }

    private fun LinearLayout.LayoutParams.margin(v: Int) = apply { setMargins(v, v, v, v) }
    // V0.3.0 v7 (D-038): every layout metric scales with the same screen
    // proportion as typography; the 1px floor keeps strokes/padding visible.
    private fun dp(value: Int): Int =
        maxOf(1, (value * fontScale * activity.resources.displayMetrics.density).toInt())

    /**
     * Coarse layout bucket so continuous window resizes rebuild only when the
     * structure actually changes (columns, header mode or font scale bucket),
     * which prevents flicker and rebuild storms.
     */
    private fun layoutKey(widthDp: Int, heightDp: Int, columns: Int, buttonsInHeader: Boolean): Int {
        val fontBucket = ((minOf(widthDp.toFloat(), heightDp.toFloat()) / 720f).coerceAtLeast(0.5f) * 20).toInt()
        return columns * 1000 + (if (buttonsInHeader) 500 else 0) + fontBucket
    }

    /**
     * Watches the real laid-out window size and triggers [onLayoutChange] when
     * the layout bucket changes (split-screen / freeform / rotation).
     */
    private fun attachLayoutObserver(onLayoutChange: () -> Unit) {
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (root.width == 0 || root.height == 0) return
                val density = activity.resources.displayMetrics.density
                val widthDp = (root.width / density).toInt()
                val heightDp = (root.height / density).toInt()
                val columns = ResponsiveLayout.columnCount(widthDp)
                val buttonsInHeader = widthDp >= 720
                val key = layoutKey(widthDp, heightDp, columns, buttonsInHeader)
                if (key != lastLayoutKey) {
                    lastLayoutKey = key
                    root.post { onLayoutChange() }
                }
            }
        })
    }
}
