package com.guanyu.rx400hprobe

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

/**
 * V0.3.1 size-independent three-domain dashboard (D-041/D-043).
 *
 * This renderer consumes [DashboardSnapshot] only. The View tree is created
 * once; native measurement reflows the header, controls and cards from the
 * actual inset-safe window on every resize. Height overflow scrolls the whole
 * page, while typography and touch targets stay inside explicit readable
 * bounds instead of following a whole-screen scale factor.
 */
internal class DashboardUi(
    private val activity: Activity,
    onSelectDevice: () -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit
) {
    val root: View

    private lateinit var contentLayout: LinearLayout
    private lateinit var cardGrid: ResponsiveCardGrid
    private lateinit var separatorView: View

    private lateinit var statusDeviceText: TextView
    private lateinit var statusBleText: TextView
    private lateinit var statusProtoText: TextView
    private lateinit var statusDataText: TextView
    private lateinit var deviceButton: Button
    private lateinit var startButton: Button
    private lateinit var endButton: Button

    private lateinit var speedValue: TextView
    private lateinit var socValue: TextView
    private lateinit var batteryAvgValue: TextView
    private lateinit var batteryDetailValue: TextView
    private lateinit var coolantValue: TextView
    private lateinit var voltageValue: TextView
    private lateinit var icePowerValue: TextView
    private lateinit var rpmValue: TextView
    private lateinit var idleCheckValue: TextView
    private lateinit var hvPowerValue: TextView

    private val cards = mutableListOf<LinearLayout>()
    private val cardTitles = mutableListOf<TextView>()
    private val metricBlocks = mutableListOf<LinearLayout>()
    private val autoSizeTargets = mutableListOf<AutoSizeTarget>()
    private var lastSnapshot: DashboardSnapshot? = null

    private val valueColor = Color.rgb(125, 255, 175)
    private val dimColor = Color.rgb(95, 205, 175)
    private val titleColor = Color.rgb(70, 215, 210)

    private var insetLeftPx = 0
    private var insetTopPx = 0
    private var insetRightPx = 0
    private var insetBottomPx = 0
    private var lastWindowToken: WindowToken? = null
    private var lastDensityDpi = activity.resources.configuration.densityDpi
    private var lastFontScale = activity.resources.configuration.fontScale

    init {
        val titleColumn = buildTitleColumn()
        val statusColumn = buildStatusColumn()

        deviceButton = controlButton("设备", onSelectDevice)
        startButton = controlButton("开始", onStart)
        endButton = controlButton("结束", onEnd)
        val header = ResponsiveHeaderLayout(
            context = activity,
            titleView = titleColumn,
            statusView = statusColumn,
            controls = listOf(deviceButton, startButton, endButton)
        )

        cardGrid = ResponsiveCardGrid(activity)
        buildDomainCards().forEach { cardGrid.addView(it) }

        separatorView = View(activity).apply {
            setBackgroundColor(Color.rgb(20, 125, 115))
        }
        contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(2, 8, 7))
            addView(
                header,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(separatorView)
            addView(
                cardGrid,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val scroll = ScrollView(activity).apply {
            setBackgroundColor(Color.rgb(2, 8, 7))
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                contentLayout,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        root = scroll

        applyPhysicalMetrics()
        applyWindowLayout(
            widthPx = activity.resources.displayMetrics.widthPixels,
            heightPx = activity.resources.displayMetrics.heightPixels
        )
        attachInsetsAndResizeHandling()
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
            val active = snapshot.idleCheckActive
            idleCheckValue.visibility = if (active) View.VISIBLE else View.INVISIBLE
            idleCheckValue.contentDescription = if (active) "Idle Check active" else null
        }
        lastSnapshot = snapshot
    }

    fun renderStatus(status: DashboardStatus) {
        statusDeviceText.setStatusText(status.deviceName)
        val reconnect = if (status.reconnectCount > 0) "·重连${status.reconnectCount}" else ""
        val notice = status.notice?.let { "·$it" } ?: ""
        val error = status.error?.let { "·$it" } ?: ""
        statusBleText.setStatusText("蓝牙${connectionText(status.connection)}")
        statusProtoText.setStatusText("协议${modeText(status.mode)}")
        statusDataText.setStatusText("数据${loggingText(status.logging)}$reconnect$notice$error")
        val stateColor = when {
            status.warning -> Color.rgb(255, 185, 80)
            status.connection == "CONNECTED" -> Color.rgb(105, 240, 195)
            else -> Color.rgb(130, 160, 150)
        }
        statusBleText.setTextColor(stateColor)
        statusProtoText.setTextColor(stateColor)
        statusDataText.setTextColor(stateColor)
    }

    fun setControlState(state: MonitorControlState) {
        deviceButton.isEnabled = state.deviceEnabled
        startButton.isEnabled = state.startEnabled
        endButton.isEnabled = state.endEnabled
    }

    /** Existing views survive rotation, split-screen and freeform changes. */
    fun onConfigurationChanged() {
        val configuration = activity.resources.configuration
        val metricsChanged =
            configuration.densityDpi != lastDensityDpi || configuration.fontScale != lastFontScale
        if (metricsChanged) {
            lastDensityDpi = configuration.densityDpi
            lastFontScale = configuration.fontScale
            applyAutoSizeTargets()
            applyPhysicalMetrics()
            lastWindowToken = null
            root.requestLayout()
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun buildTitleColumn(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        addView(styledText("RX400h", 1).apply {
            setTextColor(valueColor)
            autoSize(
                ResponsiveLayout.TypographyBounds().headerTitleMinSp,
                ResponsiveLayout.TypographyBounds().headerTitleMaxSp
            )
        })
        addView(styledText("MONITOR", 1).apply {
            setTextColor(Color.rgb(110, 235, 205))
            autoSize(
                ResponsiveLayout.TypographyBounds().headerSubtitleMinSp,
                ResponsiveLayout.TypographyBounds().headerSubtitleMaxSp
            )
        })
    }

    private fun buildStatusColumn(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        statusDeviceText = styledText("未选择设备", 2).apply {
            gravity = Gravity.END
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Color.rgb(220, 235, 225))
            autoSize(14, 18)
        }
        fun statusLine(): TextView = styledText("", 2).apply {
            gravity = Gravity.END
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Color.rgb(130, 160, 150))
            autoSize(
                ResponsiveLayout.TypographyBounds().statusMinSp,
                ResponsiveLayout.TypographyBounds().statusMaxSp
            )
        }
        statusBleText = statusLine()
        statusProtoText = statusLine()
        statusDataText = statusLine().apply { maxLines = 3 }
        addView(statusDeviceText)
        addView(statusBleText)
        addView(statusProtoText)
        addView(statusDataText)
    }

    private fun buildDomainCards(): List<LinearLayout> {
        val batteryCard = card("电池")
        socValue = addMetric(batteryCard, "电量", "— %")
        batteryAvgValue = addMetric(batteryCard, "高压电池平均温度", "— °C")
        batteryDetailValue = addMetric(batteryCard, "最高 / 最低", "—°  —°", detail = true)

        val vehicleCard = card("车辆状态")
        speedValue = addMetric(vehicleCard, "速度", "— km/h")
        coolantValue = addMetric(vehicleCard, "冷却液温度", "— °C")
        voltageValue = addMetric(vehicleCard, "12V OBD", "— V")

        val powerCard = card("动力")
        icePowerValue = addMetric(powerCard, "引擎机械功率", "— kW")
        rpmValue = addMetric(powerCard, "引擎转速", "— rpm")
        idleCheckValue = valueText("IDLE CHECK", detail = true).apply {
            setTextColor(valueColor)
            visibility = View.INVISIBLE
            maxLines = 2
        }
        powerCard.addView(
            idleCheckValue,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        hvPowerValue = addMetric(powerCard, "高压电池功率", "— kW")

        return listOf(batteryCard, vehicleCard, powerCard)
    }

    private fun card(title: String): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val titleView = styledText(title, 2).apply {
            gravity = Gravity.CENTER
            setTextColor(titleColor)
            autoSize(
                ResponsiveLayout.TypographyBounds().cardTitleMinSp,
                ResponsiveLayout.TypographyBounds().cardTitleMaxSp
            )
            ViewCompat.setAccessibilityHeading(this, true)
        }
        addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        cards.add(this)
        cardTitles.add(titleView)
    }

    private fun addMetric(
        card: LinearLayout,
        label: String,
        initialValue: String,
        detail: Boolean = false
    ): TextView {
        val block = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val labelView = styledText(label, Int.MAX_VALUE).apply {
            gravity = Gravity.CENTER
            setTextColor(if (detail) dimColor else valueColor)
            if (detail) {
                autoSize(
                    ResponsiveLayout.TypographyBounds().detailMinSp,
                    ResponsiveLayout.TypographyBounds().detailMaxSp
                )
            } else {
                autoSize(
                    ResponsiveLayout.TypographyBounds().labelMinSp,
                    ResponsiveLayout.TypographyBounds().labelMaxSp
                )
            }
        }
        val valueView = valueText(initialValue, detail)
        block.addView(
            labelView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        block.addView(
            valueView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(
            block,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        metricBlocks.add(block)
        return valueView
    }

    private fun valueText(initial: String, detail: Boolean): TextView = styledText(initial, Int.MAX_VALUE).apply {
        gravity = Gravity.CENTER
        setTextColor(if (detail) dimColor else valueColor)
        val bounds = ResponsiveLayout.TypographyBounds()
        if (detail) autoSize(bounds.detailMinSp, bounds.detailMaxSp)
        else autoSize(bounds.valueMinSp, bounds.valueMaxSp)
    }

    private fun styledText(initial: String, lines: Int): TextView = TextView(activity).apply {
        text = initial
        maxLines = lines
        typeface = Typeface.MONOSPACE
        includeFontPadding = true
        setHorizontallyScrolling(false)
    }

    private fun controlButton(label: String, action: () -> Unit): Button = Button(activity).apply {
        text = label
        maxLines = 3
        gravity = Gravity.CENTER
        isAllCaps = false
        setHorizontallyScrolling(false)
        autoSize(
            ResponsiveLayout.TypographyBounds().buttonMinSp,
            ResponsiveLayout.TypographyBounds().buttonMaxSp
        )
        setOnClickListener { action() }
    }

    private fun TextView.autoSize(minSp: Int, maxSp: Int) {
        val target = AutoSizeTarget(this, minSp, maxSp)
        autoSizeTargets.add(target)
        applyAutoSize(target)
    }

    private fun applyAutoSizeTargets() {
        autoSizeTargets.forEach(::applyAutoSize)
    }

    private fun applyAutoSize(target: AutoSizeTarget) {
        target.view.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
        target.view.setAutoSizeTextTypeUniformWithConfiguration(
            target.minSp,
            target.maxSp,
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
    }

    private fun attachInsetsAndResizeHandling() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            insetLeftPx = safe.left
            insetTopPx = safe.top
            insetRightPx = safe.right
            insetBottomPx = safe.bottom
            applyWindowLayout(root.width, root.height)
            insets
        }
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val width = right - left
            val height = bottom - top
            if (width != oldRight - oldLeft || height != oldBottom - oldTop) {
                applyWindowLayout(width, height)
            }
        }
        root.post { ViewCompat.requestApplyInsets(root) }
    }

    private fun applyWindowLayout(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        val density = activity.resources.displayMetrics.density.coerceAtLeast(0.1f)
        val spacing = ResponsiveLayout.verticalSpacingUnits(
            windowHeight = heightPx,
            insetTop = insetTopPx,
            insetBottom = insetBottomPx,
            density = density
        )
        val token = WindowToken(
            insetLeftPx = insetLeftPx,
            insetTopPx = insetTopPx,
            insetRightPx = insetRightPx,
            insetBottomPx = insetBottomPx,
            compactHeight = spacing.compactHeight
        )
        if (token == lastWindowToken) return
        lastWindowToken = token

        val horizontal = dp(ResponsiveLayout.OUTER_HORIZONTAL_DP)
        val vertical = dp(spacing.outerVerticalDp)
        root.setPadding(insetLeftPx, insetTopPx, insetRightPx, insetBottomPx)
        contentLayout.setPadding(horizontal, vertical, horizontal, vertical)
        val sectionGap = dp(spacing.sectionGapDp)
        separatorView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        ).apply {
            topMargin = sectionGap
            bottomMargin = sectionGap
        }
        cardGrid.setPadding(0, 0, 0, sectionGap)
        contentLayout.requestLayout()
    }

    private fun applyPhysicalMetrics() {
        val cardPadding = dp(ResponsiveLayout.CARD_PADDING_DP)
        cards.forEach { card ->
            card.minimumHeight = dp(ResponsiveLayout.CARD_MIN_HEIGHT_DP)
            card.setPadding(cardPadding, cardPadding, cardPadding, cardPadding)
            card.background = GradientDrawable().apply {
                setColor(Color.rgb(3, 14, 12))
                setStroke(dp(1), Color.rgb(30, 205, 175))
                cornerRadius = dp(8).toFloat()
            }
        }
        cardTitles.forEach { it.setPadding(0, 0, 0, dp(8)) }
        metricBlocks.forEach { it.setPadding(0, dp(3), 0, dp(3)) }
        listOf(deviceButton, startButton, endButton).forEach { button ->
            button.minHeight = dp(ResponsiveLayout.CONTROL_MIN_HEIGHT_DP)
            button.minimumHeight = dp(ResponsiveLayout.CONTROL_MIN_HEIGHT_DP)
            button.minWidth = dp(ResponsiveLayout.CONTROL_MIN_WIDTH_DP)
            button.minimumWidth = dp(ResponsiveLayout.CONTROL_MIN_WIDTH_DP)
            button.setPadding(dp(10), dp(8), dp(10), dp(8))
        }
    }

    private fun connectionText(connection: String): String =
        if (connection == "CONNECTED") "已连接" else "未连接"

    private fun modeText(mode: String): String = when (mode) {
        "LIVE" -> "实时"
        "PERMISSION" -> "等待授权"
        "CONNECTING" -> "连接中"
        "INITIALIZING" -> "初始化"
        "STOPPING" -> "停止中"
        "SAVING" -> "保存中"
        "SAVE_FAILED" -> "保存失败"
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

    private fun valueWithUnit(v: Double?, fresh: Boolean, unit: String, digits: Int): String =
        "${value(v, fresh, digits)} $unit"

    private fun value(v: Double?, fresh: Boolean, digits: Int): String {
        val text = v?.let { String.format(Locale.US, "%.${digits}f", it) } ?: "—"
        return if (fresh) text else "$text·"
    }

    private fun TextView.setTextIfDifferent(value: String) {
        if (text != value) text = value
    }

    private fun TextView.setStatusText(value: String) {
        setTextIfDifferent(value)
        if (contentDescription?.toString() != value) contentDescription = value
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density + 0.5f).toInt().coerceAtLeast(if (value > 0) 1 else 0)

    private data class AutoSizeTarget(val view: TextView, val minSp: Int, val maxSp: Int)

    private data class WindowToken(
        val insetLeftPx: Int,
        val insetTopPx: Int,
        val insetRightPx: Int,
        val insetBottomPx: Int,
        val compactHeight: Boolean
    )
}
