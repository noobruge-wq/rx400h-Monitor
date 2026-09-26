package com.guanyu.rx400hprobe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import java.io.IOException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Exact fixed-pixel renderer for the user-authored V0.3.5 dashboard.
 *
 * Vehicle data enters only through [DashboardSnapshot].  The editable JSON is
 * parsed once and every mask/rotation frame is cached outside [onDraw].
 */
internal class PixelDashboardView(
    context: Context,
    private val onSelectDevice: () -> Unit,
    private val onReconnect: () -> Unit
) : View(context) {
    private val layout = PixelLayout.load(context)
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private var effects = CrtEffectSettings(
        scanlineOpacity = preferences.getInt(KEY_SCANLINE_OPACITY, layout.defaultEffects.scanlineOpacity),
        scanlineWidthPx = preferences.getInt(KEY_SCANLINE_WIDTH, layout.defaultEffects.scanlineWidthPx),
        scanlineGapPx = preferences.getInt(KEY_SCANLINE_GAP, layout.defaultEffects.scanlineGapPx),
        glowIntensity = preferences.getInt(KEY_GLOW_INTENSITY, layout.defaultEffects.glowIntensity),
        glowRadiusPx = preferences.getInt(KEY_GLOW_RADIUS, layout.defaultEffects.glowRadiusPx)
    ).bounded()

    private val sharpBitmap = Bitmap.createBitmap(
        PixelDashboardModel.OUTPUT_WIDTH,
        PixelDashboardModel.OUTPUT_HEIGHT,
        Bitmap.Config.ARGB_8888
    )
    private var sharpCanvas = Canvas(sharpBitmap)
    private val motionDamage = PixelMotionDamage()
    private val staticPrefixBitmap by lazy { buildStaticPrefixes() }
    private val rasterTiles by lazy { buildRasterTiles() }
    private val oracleSharpBitmap by lazy { Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888) }
    private var referenceComposition = false
    private val referenceBitmap by lazy {
        Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
    }
    private val referenceCanvas by lazy { Canvas(referenceBitmap) }
    private val compositionDamage = BitmapDamage().apply { all() }
    private var verifyComposition = false
    private val oracleBitmap by lazy { Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888) }
    private val oracleCanvas by lazy { Canvas(oracleBitmap) }
    private var compositionChecks = 0L
    private val bitmapPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val haloPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val scanlinePaint = Paint().apply { color = Color.BLACK }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayTypeface = Typeface.createFromAsset(context.assets,
        "rx400h_ui/fonts/noto_sans_sc_medium.ttf")
    private val settingsLogicalBitmap by lazy {
        Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
    }
    private val settingsLogicalCanvas by lazy { Canvas(settingsLogicalBitmap) }
    private val sourceRect = Rect()
    private val destinationRect = Rect()
    private val destinationRectF = RectF()
    private val slotTexts = HashMap<String, String>(12)
    private val settingsValueLabels = arrayOf("75%", "1px", "1px", "100%", "8px")

    private val statusLayer = layout.layers.first { it.id == "status-block" }
    private val wheelLayer = layout.layers.first { it.role == "wheel-spokes" }
    private val socFillLayer = layout.layers.first { it.role == "soc-fill" }
    private val idleLayer = layout.layers.first { it.role == "idle" }
    private val powerFillLayer = layout.layers.first { it.role == "power-fill" }
    private val powerCursorLayer = layout.layers.first { it.role == "power-cursor" }
    private val powerGeometry = powerMaskGeometry(powerFillLayer, powerCursorLayer)
    private val statusBitmap = Bitmap.createBitmap(
        statusLayer.width * PixelDashboardModel.LOGICAL_SCALE,
        statusLayer.height * PixelDashboardModel.LOGICAL_SCALE,
        Bitmap.Config.ARGB_8888
    )
    private val statusCanvas = Canvas(statusBitmap)
    private val statusPixels = IntArray(statusBitmap.width * statusBitmap.height)
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
        textAlign = Paint.Align.RIGHT
        typeface = overlayTypeface
    }

    private var snapshot: DashboardSnapshot? = null
    private var status: DashboardStatus? = null
    private var connectionBusy = false
    private var presentationVisible = true
    private var settingsOpen = false
    private var activeSlider = -1
    private var pressedButton: String? = null
    private var contentDirty = true
    private var scanlinePatternDirty = true
    private var scanlinePatternBitmap: Bitmap? = null

    private var startSelfTestArmed = false
    private var armedSocVersion: Long? = null
    private var armedPowerVersion: Long? = null
    private var selfTestStartedAtMs = NO_TIME
    private var selfTestSocGoal = 0f
    private var selfTestPowerGoal = 0f
    private var displayedSoc = Float.NaN
    private var displayedPower = Float.NaN
    private var wheelRps = 0f
    private var wheelAngle = 0f
    private var lastWheelStepMs = NO_TIME
    private var socTransition: Transition? = null
    private var powerTransition: Transition? = null
    private var wheelTransition: Transition? = null
    private val renderPower = PowerSegments.unknown()

    private var viewportScale = 1f
    private var viewportLeft = 0f
    private var viewportTop = 0f
    private var frameScheduled = false
    private val frameRunnable = Runnable {
        frameScheduled = false
        if (presentationVisible && isAttachedToWindow && windowVisibility == VISIBLE) invalidate()
    }

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "RX400h Monitor"
        updateSlotTexts(null, null)
        updateSettingsValueLabels()
        rebuildStatusBitmap(null)
    }

    fun render(value: DashboardSnapshot) {
        val previous = snapshot
        snapshot = value
        if (previous == value) return
        var baseContentChanged = updateSlotTexts(value, previous)

        val targetSoc = finiteFresh(value.socPct, value.socFresh)
        val targetPower = finiteFresh(value.hvPowerKw, value.hvPowerFresh)
        // End/expired samples also cancel an in-flight self-test. Do not allow
        // its cached goal to relight a fill after the value becomes unknown.
        if (selfTestStartedAtMs != NO_TIME && (targetSoc == null || targetPower == null)) {
            selfTestStartedAtMs = NO_TIME
            baseContentChanged = updateSocTarget(targetSoc) || baseContentChanged
            baseContentChanged = updatePowerTarget(targetPower) || baseContentChanged
        }
        val hasNewSelfTestSoc = PixelDashboardModel.newSelfTestSource(
            armedSocVersion, value.socVersion, previous?.socFresh, value.socFresh)
        val hasNewSelfTestPower = PixelDashboardModel.newSelfTestSource(
            armedPowerVersion, value.hvPowerVersion, previous?.hvPowerFresh, value.hvPowerFresh)
        if (
            startSelfTestArmed && targetSoc != null && targetPower != null &&
            hasNewSelfTestSoc && hasNewSelfTestPower
        ) {
            startSelfTestArmed = false
            armedSocVersion = null
            armedPowerVersion = null
            selfTestStartedAtMs = SystemClock.uptimeMillis()
            selfTestSocGoal = targetSoc.coerceIn(0f, 100f)
            selfTestPowerGoal = targetPower.coerceIn(
                -PixelDashboardModel.POWER_LIMIT_KW,
                PixelDashboardModel.POWER_LIMIT_KW
            )
            displayedSoc = 0f
            displayedPower = 0f
            socTransition = null
            powerTransition = null
            baseContentChanged = true
        } else if (selfTestStartedAtMs != NO_TIME) {
            targetSoc?.let { selfTestSocGoal = it.coerceIn(0f, 100f) }
            targetPower?.let {
                selfTestPowerGoal = it.coerceIn(
                    -PixelDashboardModel.POWER_LIMIT_KW,
                    PixelDashboardModel.POWER_LIMIT_KW
                )
            }
        } else if (selfTestStartedAtMs == NO_TIME) {
            if (previous == null || value.socVersion != previous.socVersion || value.socFresh != previous.socFresh) {
                baseContentChanged = updateSocTarget(targetSoc) || baseContentChanged
            }
            if (previous == null || value.hvPowerVersion != previous.hvPowerVersion || value.hvPowerFresh != previous.hvPowerFresh) {
                baseContentChanged = updatePowerTarget(targetPower) || baseContentChanged
            }
        }

        var wheelChanged = false
        if (previous == null || value.speedVersion != previous.speedVersion || value.speedFresh != previous.speedFresh) {
            wheelChanged = updateWheelTarget(finiteFresh(value.speedKph, value.speedFresh))
        }
        if (previous == null || value.idleCheckActive != previous.idleCheckActive) {
            markLayerDamage(idleLayer)
            baseContentChanged = true
        }
        // Numeric text damage is independent from animation geometry.
        baseContentChanged = updateMotionDamage(SystemClock.uptimeMillis()) || baseContentChanged
        if (baseContentChanged) {
            contentDirty = true
        }
        if (baseContentChanged || wheelChanged) invalidate()
        scheduleFrameIfNeeded()
    }

    fun renderStatus(value: DashboardStatus) {
        if (status == value) return
        status = value
        rebuildStatusBitmap(value)
        markLayerDamage(statusLayer)
        contentDirty = true
        invalidate()
    }

    fun setConnectionBusy(value: Boolean) { connectionBusy = value }

    fun persistLocalSettings() { persistEffects() }

    fun setPresentationVisible(visible: Boolean) {
        presentationVisible = visible
        lastWheelStepMs = NO_TIME
        if (visible) { scheduleFrameIfNeeded(); invalidate() }
        else { removeCallbacks(frameRunnable); frameScheduled = false }
    }

    fun onConfigurationChanged() {
        requestLayout()
        invalidate()
    }

    fun armStartSelfTest() {
        startSelfTestArmed = true
        armedSocVersion = snapshot?.socVersion
        armedPowerVersion = snapshot?.hvPowerVersion
        selfTestStartedAtMs = NO_TIME
        socTransition = null
        powerTransition = null
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        viewportScale = min(
            width / PixelDashboardModel.OUTPUT_WIDTH.toFloat(),
            height / PixelDashboardModel.OUTPUT_HEIGHT.toFloat()
        ).coerceAtLeast(0f)
        viewportLeft = (width - PixelDashboardModel.OUTPUT_WIDTH * viewportScale) / 2f
        viewportTop = (height - PixelDashboardModel.OUTPUT_HEIGHT * viewportScale) / 2f
    }

    /** Debug-only D-057 software composition algorithm with the same current
     * inputs/content. Not an exact historical APK or a user-selectable mode. */
    fun useReferenceCompositionForDebug(enabled: Boolean) {
        check(VALIDATION_BUILD)
        referenceComposition = enabled
        markWholeFrameDirty()
        invalidate()
    }

    /** Expensive exact-pixel oracle, opt-in for debug correctness tests only. */
    fun verifyCompositionForDebug() { check(VALIDATION_BUILD); verifyComposition = true }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        if (advanceAnimations(now)) {
            contentDirty = true
        }
        if (contentDirty) rebuildSharpFrame(now)
        if (VALIDATION_BUILD && verifyComposition) verifyFullRaster(now)

        canvas.drawColor(Color.BLACK)
        if (viewportScale <= 0f) return
        canvas.save()
        canvas.translate(viewportLeft, viewportTop)
        canvas.scale(viewportScale, viewportScale)
        bitmapPaint.alpha = 255
        canvas.drawBitmap(referenceBitmap, 0f, 0f, bitmapPaint)
        if (!settingsOpen) drawWheelOnCanvas(canvas)
        drawScanlines(canvas)
        canvas.restore()
        scheduleFrameIfNeeded()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (viewportScale <= 0f) return false
        val outputX = (event.x - viewportLeft) / viewportScale
        val outputY = (event.y - viewportTop) / viewportScale
        if (outputX !in 0f..PixelDashboardModel.OUTPUT_WIDTH.toFloat() ||
            outputY !in 0f..PixelDashboardModel.OUTPUT_HEIGHT.toFloat()
        ) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (activeSlider >= 0) persistEffects()
                pressedButton = null
                activeSlider = -1
                contentDirty = true
                invalidate()
            }
            return true
        }

        if (settingsOpen) return handleSettingsTouch(event, outputX, outputY)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedButton = buttonAt(outputX, outputY)?.takeIf(::buttonEnabled)
                contentDirty = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val released = buttonAt(outputX, outputY)
                val action = pressedButton
                pressedButton = null
                contentDirty = true
                invalidate()
                if (action != null && action == released && buttonEnabled(action)) {
                    performClick()
                    when (action) {
                        "side-car-hit" -> onReconnect()
                        "settings-button" -> openSettings()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedButton = null
                contentDirty = true
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleFrameIfNeeded()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frameRunnable)
        frameScheduled = false
        lastWheelStepMs = NO_TIME
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            lastWheelStepMs = NO_TIME
            scheduleFrameIfNeeded()
        } else {
            removeCallbacks(frameRunnable)
            frameScheduled = false
            lastWheelStepMs = NO_TIME
        }
    }

    private fun rebuildSharpFrame(nowMs: Long) {
        preparePowerSegments(nowMs)
        if (settingsOpen || referenceComposition || !compositionDamage.any) compositionDamage.all()
        if (settingsOpen || referenceComposition) {
            drawFullSharp()
        } else {
            val prefix = staticPrefixBitmap
            for (i in rasterTiles.indices) {
                if (!compositionDamage.contains(i % BitmapDamage.COLUMNS, i / BitmapDamage.COLUMNS)) continue
                val tile = rasterTiles[i]
                sharpCanvas.save()
                sharpCanvas.clipRect(tile.bounds)
                sharpCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                bitmapPaint.alpha = 255
                sharpCanvas.drawBitmap(prefix, 0f, 0f, bitmapPaint)
                for (j in tile.prefixCount until tile.layers.size) drawLayer(layout.layers[tile.layers[j]])
                sharpCanvas.restore()
            }
        }
        rebuildComposition()
        contentDirty = false
    }


    private fun drawFullSharp() {
        sharpCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        for (layer in layout.layers) if (layer.role != "wheel-spokes") drawLayer(layer)
        if (settingsOpen) drawSettingsOverlay()
    }

    private data class RasterTile(val bounds: Rect, val layers: IntArray, val prefixCount: Int)

    /** Conservative footprints are compiled once. Text may exceed its authored
     * slot: include its entire horizontal band rather than clip/reposition it. */
    private fun buildRasterTiles(): Array<RasterTile> = Array(BitmapDamage.COLUMNS * BitmapDamage.ROWS) { tile ->
        val left = tile % BitmapDamage.COLUMNS * BitmapDamage.WIDTH
        val top = tile / BitmapDamage.COLUMNS * BitmapDamage.HEIGHT
        val bounds = Rect(left, top, left + BitmapDamage.WIDTH, top + BitmapDamage.HEIGHT)
        val indices = layout.layers.indices.filter { index ->
            val layer = layout.layers[index]
            if (!layer.visible || layer.role == "wheel-spokes") false
            else {
                val glyph = layout.glyphSets[layer.glyphSet]
                val extra = if (layer.role == "slot") (glyph?.lineHeight ?: layer.height) * 2 else 0
                val fullBand = layer.role == "slot" || layer.role == "power-cursor"
                val l = if (fullBand) 0 else layer.x * 2
                val r = if (fullBand) 1280 else (layer.x + layer.width) * 2
                val t = layer.y * 2 - extra
                val b = (layer.y + layer.height) * 2 + extra
                l < bounds.right && r > bounds.left && t < bounds.bottom && b > bounds.top
            }
        }.toIntArray()
        val firstDynamic = indices.indexOfFirst { index ->
            val layer = layout.layers[index]
            layer.role in DYNAMIC_ROLES || layer.id == "status-block" || layer.id.startsWith("button-")
        }.let { if (it < 0) indices.size else it }
        RasterTile(bounds, indices, firstDynamic)
    }

    /** Cache only the static prefix within each tile; later static layers stay
     * interleaved with dynamic layers in original z-order. No alpha regrouping. */
    private fun buildStaticPrefixes(): Bitmap {
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val previousCanvas = sharpCanvas
        sharpCanvas = Canvas(bitmap)
        try {
            for (tile in rasterTiles) {
                sharpCanvas.save()
                sharpCanvas.clipRect(tile.bounds)
                for (i in 0 until tile.prefixCount) drawLayer(layout.layers[tile.layers[i]])
                sharpCanvas.restore()
            }
        } finally { sharpCanvas = previousCanvas }
        return bitmap
    }

    private fun verifyFullRaster(nowMs: Long) {
        val previousCanvas = sharpCanvas
        sharpCanvas = Canvas(oracleSharpBitmap)
        try { preparePowerSegments(nowMs); drawFullSharp() }
        finally { sharpCanvas = previousCanvas }
        check(sharpBitmap.sameAs(oracleSharpBitmap)) { "D061 sharp damage differs at frame $compositionChecks" }
        oracleCanvas.drawColor(Color.BLACK)
        drawHalo(oracleCanvas, oracleSharpBitmap)
        bitmapPaint.alpha = 255
        oracleCanvas.drawBitmap(oracleSharpBitmap, 0f, 0f, bitmapPaint)
        check(referenceBitmap.sameAs(oracleBitmap)) { "D061 composition differs at frame $compositionChecks" }
        compositionChecks++
        android.util.Log.i("RX400hPixelOracle", "PASS frames=$compositionChecks")
    }

    private fun updateMotionDamage(nowMs: Long): Boolean {
        preparePowerSegments(nowMs)
        val unknown = Int.MIN_VALUE
        val soc = if (!displayedSoc.isFinite()) unknown else
            (socFillLayer.height * PixelDashboardModel.socVisualFraction(displayedSoc)).roundToInt().coerceIn(0, socFillLayer.height)
        val left = if (renderPower.known) powerGeometry.fillX(renderPower.leftKw) else unknown
        val right = if (renderPower.known) powerGeometry.fillX(renderPower.rightKw) else unknown
        var leftCursor = unknown
        var rightCursor = unknown
        if (renderPower.known) {
            if (renderPower.leftKw < -0.01f) leftCursor = powerGeometry.cursorX(renderPower.leftKw).roundToInt()
            if (renderPower.rightKw > 0.01f) rightCursor = powerGeometry.cursorX(renderPower.rightKw).roundToInt()
            if (leftCursor == unknown && rightCursor == unknown) leftCursor = powerGeometry.cursorX(0f).roundToInt()
        }
        val changes = motionDamage.update(soc, left, right, leftCursor, rightCursor)
        if (changes and 1 != 0) markLayerDamage(socFillLayer)
        if (changes and 2 != 0) {
            markLayerDamage(powerFillLayer)
            val l = powerGeometry.cursorX(-50f).roundToInt() - powerCursorLayer.width
            val r = powerGeometry.cursorX(50f).roundToInt() + powerCursorLayer.width
            compositionDamage.mark(l * 2 - 10, powerCursorLayer.y * 2 - 10,
                r * 2 + 10, (powerCursorLayer.y + powerCursorLayer.height) * 2 + 10)
        }
        return changes != 0
    }

    private fun drawLayer(layer: PixelLayer) {
        if (!layer.visible) return
        val stateAlpha = 1f
        when (layer.role) {
            "slot" -> drawGlyphText(layer, slotTexts[layer.binding].orEmpty(), stateAlpha)
            "soc-fill" -> drawSocFill(layer)
            "wheel-spokes" -> drawWheelSpokes(layer)
            "power-fill" -> drawPowerFill(layer)
            "power-cursor" -> drawPowerCursor(layer)
            "idle" -> drawBitmapLayer(
                layer,
                if (snapshot?.idleCheckActive == true) 1f else IDLE_INACTIVE_ALPHA
            )
            else -> drawBitmapLayer(layer, layer.alpha * stateAlpha)
        }
        if (layer.id == "status-block") {
            bitmapPaint.alpha = alphaByte(layer.alpha)
            sharpCanvas.drawBitmap(
                statusBitmap,
                (layer.x * PixelDashboardModel.LOGICAL_SCALE).toFloat(),
                (layer.y * PixelDashboardModel.LOGICAL_SCALE).toFloat(),
                bitmapPaint
            )
        } else if (layer.physicalTextBitmap != null) {
            bitmapPaint.alpha = alphaByte(layer.alpha * stateAlpha)
            sharpCanvas.drawBitmap(
                layer.physicalTextBitmap,
                (layer.x * PixelDashboardModel.LOGICAL_SCALE).toFloat(),
                (layer.y * PixelDashboardModel.LOGICAL_SCALE).toFloat(),
                bitmapPaint
            )
        }
    }

    private fun drawBitmapLayer(layer: PixelLayer, alpha: Float) {
        val bitmap = layer.bitmap ?: return
        sourceRect.set(0, 0, bitmap.width, bitmap.height)
        destinationRect.set(
            layer.x * PixelDashboardModel.LOGICAL_SCALE,
            layer.y * PixelDashboardModel.LOGICAL_SCALE,
            (layer.x + layer.width) * PixelDashboardModel.LOGICAL_SCALE,
            (layer.y + layer.height) * PixelDashboardModel.LOGICAL_SCALE
        )
        bitmapPaint.alpha = alphaByte(alpha)
        sharpCanvas.drawBitmap(bitmap, sourceRect, destinationRect, bitmapPaint)
    }

    private fun drawMaskRegion(
        layer: PixelLayer,
        sourceLeft: Int,
        sourceTop: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        destinationLeft: Int,
        destinationTop: Int,
        alpha: Float
    ) {
        val bitmap = layer.bitmap ?: return
        if (sourceWidth <= 0 || sourceHeight <= 0) return
        sourceRect.set(sourceLeft, sourceTop, sourceLeft + sourceWidth, sourceTop + sourceHeight)
        destinationRect.set(
            destinationLeft * PixelDashboardModel.LOGICAL_SCALE,
            destinationTop * PixelDashboardModel.LOGICAL_SCALE,
            (destinationLeft + sourceWidth) * PixelDashboardModel.LOGICAL_SCALE,
            (destinationTop + sourceHeight) * PixelDashboardModel.LOGICAL_SCALE
        )
        bitmapPaint.alpha = alphaByte(alpha)
        sharpCanvas.drawBitmap(bitmap, sourceRect, destinationRect, bitmapPaint)
    }

    private fun drawGlyphText(layer: PixelLayer, text: String, stateAlpha: Float) {
        val glyphs = layout.glyphSets[layer.glyphSet] ?: return
        val totalWidth = (text.length * glyphs.advance - glyphs.scale).coerceAtLeast(0)
        var logicalX = when (layer.align) {
            "center" -> layer.x + floor((layer.width - totalWidth) / 2f).toInt()
            "left" -> layer.x
            else -> layer.x + layer.width - totalWidth
        }
        val logicalY = layer.y + floor(
            (layer.height - glyphs.lineHeight + glyphs.scale) / 2f
        ).toInt()
        bitmapPaint.alpha = alphaByte(layer.alpha * stateAlpha)
        text.forEach { character ->
            val glyph = glyphs.characters[character] ?: glyphs.characters[' '] ?: return@forEach
            sourceRect.set(0, 0, glyph.width, glyph.height)
            destinationRect.set(
                logicalX * PixelDashboardModel.LOGICAL_SCALE,
                logicalY * PixelDashboardModel.LOGICAL_SCALE,
                (logicalX + glyph.width) * PixelDashboardModel.LOGICAL_SCALE,
                (logicalY + glyph.height) * PixelDashboardModel.LOGICAL_SCALE
            )
            sharpCanvas.drawBitmap(glyph, sourceRect, destinationRect, bitmapPaint)
            logicalX += glyphs.advance
        }
    }

    private fun drawSocFill(layer: PixelLayer) {
        if (!displayedSoc.isFinite()) return
        val fraction = PixelDashboardModel.socVisualFraction(displayedSoc)
        val visibleHeight = (layer.height * fraction).roundToInt().coerceIn(0, layer.height)
        if (visibleHeight <= 0) return
        drawMaskRegion(
            layer = layer,
            sourceLeft = 0,
            sourceTop = layer.height - visibleHeight,
            sourceWidth = layer.width,
            sourceHeight = visibleHeight,
            destinationLeft = layer.x,
            destinationTop = layer.y + layer.height - visibleHeight,
            alpha = layer.alpha
        )
    }

    private fun drawWheelSpokes(layer: PixelLayer) {
        val frames = layer.rotationFrames
        if (frames.isNullOrEmpty()) {
            drawBitmapLayer(layer, layer.alpha)
            return
        }
        val normalized = ((wheelAngle % TWO_PI) + TWO_PI) % TWO_PI
        val index = (normalized / TWO_PI * frames.size).roundToInt() % frames.size
        val bitmap = frames[index]
        sourceRect.set(0, 0, bitmap.width, bitmap.height)
        destinationRect.set(
            layer.x * PixelDashboardModel.LOGICAL_SCALE,
            layer.y * PixelDashboardModel.LOGICAL_SCALE,
            (layer.x + layer.width) * PixelDashboardModel.LOGICAL_SCALE,
            (layer.y + layer.height) * PixelDashboardModel.LOGICAL_SCALE
        )
        bitmapPaint.alpha = alphaByte(layer.alpha)
        sharpCanvas.drawBitmap(bitmap, sourceRect, destinationRect, bitmapPaint)
    }

    /**
     * The wheels are the only steady-state animation. Draw them directly over
     * the unchanged sharp page so a 10 fps wheel does not rebuild and re-upload
     * the complete 1280 x 720 dashboard on every step.
     */
    private fun drawWheelOnCanvas(canvas: Canvas) {
        if (!wheelLayer.visible) return
        val frames = wheelLayer.rotationFrames
        val bitmap = if (frames.isNullOrEmpty()) {
            wheelLayer.bitmap ?: return
        } else {
            val normalized = ((wheelAngle % TWO_PI) + TWO_PI) % TWO_PI
            val index = (normalized / TWO_PI * frames.size).roundToInt() % frames.size
            frames[index]
        }

        if (effects.glowIntensity > 0 && effects.glowRadiusPx > 0) {
            val radius = effects.glowRadiusPx.toFloat()
            val diagonal = radius * 0.58f
            haloPaint.alpha = (haloAlpha() * currentWheelAlpha()).roundToInt()
            drawWheelBitmap(canvas, bitmap, -radius, 0f, haloPaint)
            drawWheelBitmap(canvas, bitmap, radius, 0f, haloPaint)
            drawWheelBitmap(canvas, bitmap, 0f, -radius, haloPaint)
            drawWheelBitmap(canvas, bitmap, 0f, radius, haloPaint)
            drawWheelBitmap(canvas, bitmap, -diagonal, -diagonal, haloPaint)
            drawWheelBitmap(canvas, bitmap, diagonal, -diagonal, haloPaint)
            drawWheelBitmap(canvas, bitmap, -diagonal, diagonal, haloPaint)
            drawWheelBitmap(canvas, bitmap, diagonal, diagonal, haloPaint)
        }
        bitmapPaint.alpha = alphaByte(currentWheelAlpha())
        drawWheelBitmap(canvas, bitmap, 0f, 0f, bitmapPaint)
    }

    private fun drawWheelBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        offsetX: Float,
        offsetY: Float,
        paint: Paint
    ) {
        sourceRect.set(0, 0, bitmap.width, bitmap.height)
        val scale = PixelDashboardModel.LOGICAL_SCALE.toFloat()
        val left = wheelLayer.x * scale + offsetX
        val top = wheelLayer.y * scale + offsetY
        destinationRectF.set(
            left,
            top,
            left + wheelLayer.width * scale,
            top + wheelLayer.height * scale
        )
        canvas.drawBitmap(bitmap, sourceRect, destinationRectF, paint)
    }

    private fun currentWheelAlpha(): Float = PixelDashboardModel.wheelAlpha(
        snapshot?.speedKph, snapshot?.speedFresh == true, wheelLayer.alpha)

    private fun preparePowerSegments(nowMs: Long) {
        if (selfTestStartedAtMs != NO_TIME) {
            PixelDashboardModel.powerSelfTestSegments(nowMs - selfTestStartedAtMs, selfTestPowerGoal, renderPower)
        } else {
            PixelDashboardModel.powerSegments(displayedPower, renderPower)
        }
    }

    private fun drawPowerFill(layer: PixelLayer) {
        if (!renderPower.known) return
        val zero = powerGeometry.zeroLocal
        val leftEnd = powerGeometry.fillX(renderPower.leftKw)
        val rightEnd = powerGeometry.fillX(renderPower.rightKw)
        if (leftEnd < zero) {
            drawMaskRegion(
                layer,
                leftEnd,
                0,
                zero - leftEnd + 1,
                layer.height,
                layer.x + leftEnd,
                layer.y,
                layer.alpha
            )
        }
        if (rightEnd > zero) {
            drawMaskRegion(
                layer,
                zero,
                0,
                rightEnd - zero + 1,
                layer.height,
                layer.x + zero,
                layer.y,
                layer.alpha
            )
        }
    }

    private fun drawPowerCursor(layer: PixelLayer) {
        if (!renderPower.known) return
        var drew = false
        if (renderPower.leftKw < -0.01f) {
            drawCursorAt(layer, powerGeometry.cursorX(renderPower.leftKw))
            drew = true
        }
        if (renderPower.rightKw > 0.01f) {
            drawCursorAt(layer, powerGeometry.cursorX(renderPower.rightKw))
            drew = true
        }
        if (!drew) drawCursorAt(layer, powerGeometry.cursorX(0f))
    }

    private fun drawCursorAt(layer: PixelLayer, centerX: Float) {
        val left = centerX.roundToInt() - layer.width / 2
        drawMaskRegion(layer, 0, 0, layer.width, layer.height, left, layer.y, layer.alpha)
    }

    private fun drawHalo(canvas: Canvas, bitmap: Bitmap) {
        if (effects.glowIntensity <= 0 || effects.glowRadiusPx <= 0) return
        val radius = effects.glowRadiusPx.toFloat()
        // Both reference and cached tiles use the original software rasterizer
        // at 720p; viewport scaling happens once, after composition.
        val diagonal = radius * 0.58f
        haloPaint.alpha = haloAlpha()
        canvas.drawBitmap(bitmap, -radius, 0f, haloPaint)
        canvas.drawBitmap(bitmap, radius, 0f, haloPaint)
        canvas.drawBitmap(bitmap, 0f, -radius, haloPaint)
        canvas.drawBitmap(bitmap, 0f, radius, haloPaint)
        canvas.drawBitmap(bitmap, -diagonal, -diagonal, haloPaint)
        canvas.drawBitmap(bitmap, diagonal, -diagonal, haloPaint)
        canvas.drawBitmap(bitmap, -diagonal, diagonal, haloPaint)
        canvas.drawBitmap(bitmap, diagonal, diagonal, haloPaint)
    }

    private fun drawScanlines(canvas: Canvas) {
        if (effects.scanlineOpacity <= 0) return
        ensureScanlinePattern()
        scanlinePaint.alpha = (effects.scanlineOpacity * 255 / 100).coerceIn(0, 255)
        canvas.drawRect(
            0f,
            0f,
            PixelDashboardModel.OUTPUT_WIDTH.toFloat(),
            PixelDashboardModel.OUTPUT_HEIGHT.toFloat(),
            scanlinePaint
        )
    }

    private fun ensureScanlinePattern() {
        if (!scanlinePatternDirty && scanlinePatternBitmap != null) return
        val gap = effects.scanlineGapPx
        val pitch = effects.scanlineWidthPx + gap
        val pixels = IntArray(pitch) { row -> if (row >= gap) Color.BLACK else Color.TRANSPARENT }
        val next = Bitmap.createBitmap(pixels, 1, pitch, Bitmap.Config.ARGB_8888)
        val previous = scanlinePatternBitmap
        scanlinePatternBitmap = next
        scanlinePaint.shader = BitmapShader(next, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        scanlinePatternDirty = false
        previous?.recycle()
    }

    private fun updateSlotTexts(value: DashboardSnapshot?, previous: DashboardSnapshot?): Boolean {
        var changed = false
        if (previous == null || value == null || value.socPct != previous.socPct || value.socFresh != previous.socFresh) {
            changed = updateSlotText(
                "soc",
                PixelDashboardModel.formatSoc(value?.socPct, value?.socFresh == true)
            ) || changed
        }
        if (previous == null || value == null || value.batteryTempMinC != previous.batteryTempMinC || value.batteryTempMaxC != previous.batteryTempMaxC || value.batteryTempAvgC != previous.batteryTempAvgC || value.batteryTempFresh != previous.batteryTempFresh) {
            changed = updateSlotText(
                "temp",
                PixelDashboardModel.formatTemperature(
                    value?.batteryTempAvgC,
                    value?.batteryTempFresh == true
                )
            ) || changed
            changed = updateSlotText(
                "tempRange",
                PixelDashboardModel.formatTemperatureRange(
                    value?.batteryTempMaxC,
                    value?.batteryTempMinC,
                    value?.batteryTempFresh == true
                )
            ) || changed
        }
        if (previous == null || value == null || value.speedKph != previous.speedKph || value.speedFresh != previous.speedFresh) {
            changed = updateSlotText(
                "speed",
                PixelDashboardModel.formatSpeed(value?.speedKph, value?.speedFresh == true)
            ) || changed
        }
        if (previous == null || value == null || value.coolantC != previous.coolantC || value.coolantFresh != previous.coolantFresh) {
            changed = updateSlotText(
                "coolant",
                PixelDashboardModel.formatCoolant(
                    value?.coolantC,
                    value?.coolantFresh == true
                )
            ) || changed
        }
        if (previous == null || value == null || value.adapterVoltageV != previous.adapterVoltageV || value.adapterVoltageFresh != previous.adapterVoltageFresh) {
            changed = updateSlotText(
                "voltage",
                PixelDashboardModel.formatVoltage(
                    value?.adapterVoltageV,
                    value?.adapterVoltageFresh == true
                )
            ) || changed
        }
        if (previous == null || value == null || value.icePowerKw != previous.icePowerKw || value.icePowerFresh != previous.icePowerFresh) {
            changed = updateSlotText(
                "icePower",
                PixelDashboardModel.formatPower(
                    value?.icePowerKw,
                    value?.icePowerFresh == true
                )
            ) || changed
        }
        if (previous == null || value == null || value.rpm != previous.rpm || value.rpmFresh != previous.rpmFresh) {
            changed = updateSlotText(
                "rpm",
                PixelDashboardModel.formatRpm(value?.rpm, value?.rpmFresh == true)
            ) || changed
        }
        if (previous == null || value == null || value.hvPowerKw != previous.hvPowerKw || value.hvPowerFresh != previous.hvPowerFresh) {
            changed = updateSlotText(
                "hvPower",
                PixelDashboardModel.formatPower(
                    value?.hvPowerKw,
                    value?.hvPowerFresh == true
                )
            ) || changed
        }
        return changed
    }

    private fun updateSlotText(key: String, value: String): Boolean {
        val old = slotTexts.put(key, value)
        if (old == value) return false
        for (layer in layout.layers) {
            if (layer.role != "slot" || layer.binding != key) continue
            val glyphs = layout.glyphSets[layer.glyphSet] ?: continue
            val textWidth = (maxOf(old?.length ?: 0, value.length) * glyphs.advance - glyphs.scale).coerceAtLeast(0)
            // Include old and new glyph extents even when a long placeholder
            // is wider than the authored slot. Never truncate/move its text.
            val extra = (textWidth - layer.width).coerceAtLeast(0)
            val verticalExtra = (glyphs.lineHeight - layer.height).coerceAtLeast(0)
            markLayerDamage(layer, extra, verticalExtra)
        }
        return true
    }

    private fun markLayerDamage(layer: PixelLayer, extraX: Int = 0, extraY: Int = 0) {
        val pad = 10 // Maximum 8 physical-pixel halo plus raster edge coverage.
        compositionDamage.mark((layer.x - extraX) * 2 - pad, (layer.y - extraY) * 2 - pad,
            (layer.x + layer.width + extraX) * 2 + pad, (layer.y + layer.height + extraY) * 2 + pad)
    }

    private fun markWholeFrameDirty() {
        compositionDamage.all()
        contentDirty = true
    }

    private fun rebuildComposition() {
        // Settings may overlap any damaged signal. Reference mode deliberately
        // recomposes everything for the controlled A/B and pixel oracle.
        if (settingsOpen || referenceComposition || !compositionDamage.any) compositionDamage.all()
        val canvas = referenceCanvas
        bitmapPaint.alpha = 255
        if (referenceComposition) {
            canvas.drawColor(Color.BLACK)
            drawHalo(canvas, sharpBitmap)
            canvas.drawBitmap(sharpBitmap, 0f, 0f, bitmapPaint)
            compositionDamage.clear()
            return
        }
        for (row in 0 until BitmapDamage.ROWS) {
            var column = 0
            while (column < BitmapDamage.COLUMNS) {
                if (!compositionDamage.contains(column, row)) { column++; continue }
                val start = column
                do { column++ } while (column < BitmapDamage.COLUMNS && compositionDamage.contains(column, row))
                canvas.save()
                canvas.clipRect(start * BitmapDamage.WIDTH, row * BitmapDamage.HEIGHT,
                    column * BitmapDamage.WIDTH, (row + 1) * BitmapDamage.HEIGHT)
                canvas.drawColor(Color.BLACK)
                drawHalo(canvas, sharpBitmap)
                canvas.drawBitmap(sharpBitmap, 0f, 0f, bitmapPaint)
                canvas.restore()
            }
        }
        compositionDamage.clear()
    }

    private fun updateSocTarget(target: Float?): Boolean {
        if (target == null) {
            val changed = displayedSoc.isFinite() || socTransition != null
            displayedSoc = Float.NaN
            socTransition = null
            return changed
        }
        val bounded = target.coerceIn(0f, 100f)
        if (!displayedSoc.isFinite()) {
            displayedSoc = bounded
            return true
        }
        if (socTransition?.to?.approximatelyEquals(bounded) == true) return false
        if (socTransition == null && displayedSoc.approximatelyEquals(bounded)) return false
        socTransition = Transition(displayedSoc, bounded, SystemClock.uptimeMillis(), 300L)
        return true
    }

    private fun updatePowerTarget(target: Float?): Boolean {
        if (target == null) {
            val changed = displayedPower.isFinite() || powerTransition != null
            displayedPower = Float.NaN
            powerTransition = null
            return changed
        }
        val bounded = target.coerceIn(-PixelDashboardModel.POWER_LIMIT_KW, PixelDashboardModel.POWER_LIMIT_KW)
        if (!displayedPower.isFinite()) {
            displayedPower = bounded
            return true
        }
        if (powerTransition?.to?.approximatelyEquals(bounded) == true) return false
        if (powerTransition == null && displayedPower.approximatelyEquals(bounded)) return false
        powerTransition = Transition(
            displayedPower,
            bounded,
            SystemClock.uptimeMillis(),
            300L
        )
        return true
    }

    private fun updateWheelTarget(speed: Float?): Boolean {
        val target = PixelDashboardModel.wheelRevolutionsPerSecond(speed ?: 0f)
        if (target <= 0f) {
            val changed = wheelRps > 0f || wheelTransition != null
            wheelRps = 0f
            wheelTransition = null
            lastWheelStepMs = NO_TIME
            return changed
        }
        if (wheelTransition?.to?.approximatelyEquals(target) == true) return false
        if (wheelTransition == null && wheelRps.approximatelyEquals(target)) return false
        val now = SystemClock.uptimeMillis()
        wheelTransition?.let { wheelRps = it.valueAt(now) }
        wheelTransition = Transition(wheelRps, target, now, 300L)
        return true
    }

    private fun advanceAnimations(nowMs: Long): Boolean {
        if (selfTestStartedAtMs != NO_TIME) {
            val elapsed = nowMs - selfTestStartedAtMs
            displayedSoc = PixelDashboardModel.startupSoc(elapsed, selfTestSocGoal)
            if (elapsed >= SELF_TEST_DURATION_MS) {
                displayedSoc = selfTestSocGoal
                displayedPower = selfTestPowerGoal
                selfTestStartedAtMs = NO_TIME
            }
        }
        socTransition?.let { transition ->
            displayedSoc = transition.valueAt(nowMs)
            if (transition.finished(nowMs)) socTransition = null
        }
        powerTransition?.let { transition ->
            displayedPower = transition.valueAt(nowMs)
            if (transition.finished(nowMs)) powerTransition = null
        }
        wheelTransition?.let { transition ->
            wheelRps = transition.valueAt(nowMs)
            if (transition.finished(nowMs)) wheelTransition = null
        }
        if (settingsOpen) {
            lastWheelStepMs = NO_TIME
        } else if (wheelRps > 0.001f) {
            val previous = lastWheelStepMs
            if (previous == NO_TIME) {
                lastWheelStepMs = nowMs
            } else if (nowMs - previous >= WHEEL_FRAME_MS) {
                val deltaSeconds = (nowMs - previous).coerceAtMost(250L) / 1000f
                wheelAngle = (wheelAngle - wheelRps * TWO_PI * deltaSeconds) % TWO_PI
                lastWheelStepMs = nowMs
            }
        }
        return updateMotionDamage(nowMs)
    }

    private fun contentAnimationNeeded(): Boolean =
        selfTestStartedAtMs != NO_TIME || socTransition != null || powerTransition != null

    private fun animationNeeded(): Boolean = PixelDashboardModel.animationFrameNeeded(
        settingsOpen = settingsOpen,
        contentAnimationActive = contentAnimationNeeded(),
        wheelTransitionActive = wheelTransition != null,
        wheelRps = wheelRps
    )

    private fun scheduleFrameIfNeeded() {
        if (!animationNeeded()) return
        if (!presentationVisible || frameScheduled || !isAttachedToWindow || windowVisibility != VISIBLE) return
        frameScheduled = true
        val delay = if (
            contentAnimationNeeded() || (!settingsOpen && wheelTransition != null)
        ) TRANSITION_FRAME_MS else WHEEL_FRAME_MS
        postDelayed(frameRunnable, delay)
    }

    private fun rebuildStatusBitmap(value: DashboardStatus?) {
        statusCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val lines = statusLines(value)
        val lineHeight = 20f
        val firstCenter = (statusBitmap.height - lines.size * lineHeight) / 2f + lineHeight / 2f
        lines.forEachIndexed { index, line ->
            val centerY = firstCenter + index * lineHeight
            val baseline = centerY - (statusPaint.ascent() + statusPaint.descent()) / 2f
            statusCanvas.drawText(
                ellipsizeStatus(line, statusBitmap.width.toFloat()),
                statusBitmap.width.toFloat(),
                baseline,
                statusPaint
            )
        }
        statusBitmap.getPixels(
            statusPixels,
            0,
            statusBitmap.width,
            0,
            0,
            statusBitmap.width,
            statusBitmap.height
        )
        statusPixels.indices.forEach { index ->
            statusPixels[index] = if (Color.alpha(statusPixels[index]) >= STATUS_THRESHOLD) PHOSPHOR else 0
        }
        statusBitmap.setPixels(
            statusPixels,
            0,
            statusBitmap.width,
            0,
            0,
            statusBitmap.width,
            statusBitmap.height
        )
    }

    private fun statusLines(value: DashboardStatus?): Array<String> {
        if (value == null) return arrayOf("OBD", "蓝牙未连接 / BT OFF", "协议空闲 / IDLE", "点车图重连")
        val bluetooth = if (value.connection == "CONNECTED") "蓝牙已连接 / BT LINK" else "蓝牙未连接 / BT OFF"
        val protocol = when (value.mode) {
            "LIVE" -> "协议就绪 / CAN 500K"
            "WAITING_PERMISSION" -> "等待蓝牙授权"
            "CONNECTING" -> "协议连接中 / LINK"
            "INITIALIZING" -> "协议初始化 / INIT"
            "STOPPING" -> "协议停止中 / STOP"
            else -> "协议空闲 / IDLE"
        }
        val data = value.error ?: value.notice ?: if (value.mode == "LIVE") "实时仪表 / LIVE" else "点车图重连"
        return arrayOf(value.deviceName, bluetooth, protocol, data)
    }

    private fun ellipsizeStatus(value: String, width: Float): String {
        if (statusPaint.measureText(value) <= width) return value
        val ellipsis = "…"
        var end = value.length
        while (end > 0 && statusPaint.measureText(value.substring(0, end) + ellipsis) > width) end--
        return value.substring(0, end) + ellipsis
    }

    private fun openSettings() {
        settingsOpen = true
        activeSlider = -1
        removeCallbacks(frameRunnable)
        frameScheduled = false
        lastWheelStepMs = NO_TIME
        markWholeFrameDirty()
        invalidate()
    }

    private fun closeSettings() {
        settingsOpen = false
        activeSlider = -1
        persistEffects()
        markWholeFrameDirty()
        invalidate()
    }

    private fun drawSettingsOverlay() {
        val logical = settingsLogicalCanvas
        logical.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        overlayPaint.isAntiAlias = false
        overlayPaint.alpha = 255
        overlayPaint.style = Paint.Style.FILL
        overlayPaint.color = Color.BLACK
        fun pixel(value: Float) = (value / 2f).roundToInt().toFloat()
        fun rect(left: Float, top: Float, right: Float, bottom: Float) =
            logical.drawRect(pixel(left), pixel(top), pixel(right), pixel(bottom), overlayPaint)
        fun line(left: Float, y: Float, right: Float) =
            logical.drawLine(pixel(left), pixel(y), pixel(right), pixel(y), overlayPaint)
        rect(SETTINGS_LEFT, SETTINGS_TOP, SETTINGS_RIGHT, SETTINGS_BOTTOM)
        overlayPaint.style = Paint.Style.STROKE
        overlayPaint.strokeWidth = 1f
        overlayPaint.color = PHOSPHOR
        rect(SETTINGS_LEFT, SETTINGS_TOP, SETTINGS_RIGHT, SETTINGS_BOTTOM)
        rect(CLOSE_LEFT, CLOSE_TOP, CLOSE_RIGHT, CLOSE_BOTTOM)
        overlayPaint.style = Paint.Style.FILL
        overlayPaint.typeface = Typeface.MONOSPACE
        overlayPaint.textAlign = Paint.Align.CENTER
        overlayPaint.textSize = 16f
        logical.drawText("×", pixel((CLOSE_LEFT + CLOSE_RIGHT) / 2f),
            pixel(CLOSE_BOTTOM - 11f), overlayPaint)
        overlayPaint.style = Paint.Style.STROKE
        rect(SLIDER_LEFT, 140f, SLIDER_RIGHT, 184f)
        var index = 0
        while (index < SLIDER_COUNT) {
            val centerY = SLIDER_FIRST_Y + index * SLIDER_ROW_GAP
            overlayPaint.style = Paint.Style.FILL
            overlayPaint.textSize = 12f
            overlayPaint.textAlign = Paint.Align.RIGHT
            overlayPaint.alpha = 255
            logical.drawText(settingsValueLabels[index], pixel(SETTINGS_VALUE_X), pixel(centerY + 8f), overlayPaint)
            overlayPaint.alpha = 100
            line(SLIDER_LEFT, centerY, SLIDER_RIGHT)
            val knobX = SLIDER_LEFT + (SLIDER_RIGHT - SLIDER_LEFT) * sliderFraction(index)
            overlayPaint.alpha = 255
            line(SLIDER_LEFT, centerY, knobX)
            overlayPaint.style = Paint.Style.STROKE
            rect(knobX - 8f, centerY - 13f, knobX + 8f, centerY + 13f)
            index++
        }
        bitmapPaint.alpha = 255
        destinationRect.set(0, 0, PixelDashboardModel.OUTPUT_WIDTH, PixelDashboardModel.OUTPUT_HEIGHT)
        sharpCanvas.drawBitmap(settingsLogicalBitmap, null, destinationRect, bitmapPaint)

        // Chinese is the explicit physical-pixel exception; all geometry and
        // Latin/numerical controls above are rasterized at 640×360 then doubled.
        overlayPaint.isAntiAlias = true
        overlayPaint.typeface = overlayTypeface
        overlayPaint.style = Paint.Style.FILL
        overlayPaint.alpha = 255
        overlayPaint.textAlign = Paint.Align.LEFT
        overlayPaint.textSize = 32f
        sharpCanvas.drawText("本机设置", SETTINGS_LEFT + 30f, SETTINGS_TOP + 48f, overlayPaint)
        overlayPaint.textSize = 24f
        sharpCanvas.drawText("蓝牙设备", SETTINGS_LABEL_X, 172f, overlayPaint)
        sharpCanvas.drawText("选择设备", SLIDER_LEFT + 12f, 172f, overlayPaint)
        index = 0
        while (index < SLIDER_COUNT) {
            sharpCanvas.drawText(SETTINGS_LABELS[index], SETTINGS_LABEL_X,
                SLIDER_FIRST_Y + index * SLIDER_ROW_GAP + 8f, overlayPaint)
            index++
        }
    }

    private fun handleSettingsTouch(event: MotionEvent, x: Float, y: Float): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeSlider = when {
                    x in CLOSE_LEFT..CLOSE_RIGHT && y in CLOSE_TOP..CLOSE_BOTTOM -> CLOSE_SLIDER
                    x in SLIDER_LEFT..SLIDER_RIGHT && y in 140f..184f -> DEVICE_ACTION
                    else -> sliderAt(y)
                }
                if (activeSlider >= 0) updateSlider(activeSlider, x)
            }
            MotionEvent.ACTION_MOVE -> if (activeSlider >= 0) updateSlider(activeSlider, x)
            MotionEvent.ACTION_UP -> {
                val action = activeSlider
                activeSlider = -1
                when {
                    action == CLOSE_SLIDER && x in CLOSE_LEFT..CLOSE_RIGHT && y in CLOSE_TOP..CLOSE_BOTTOM -> closeSettings()
                    action == DEVICE_ACTION && x in SLIDER_LEFT..SLIDER_RIGHT && y in 140f..184f -> {
                        persistEffects()
                        onSelectDevice()
                    }
                    action >= 0 -> { updateSlider(action, x); persistEffects() }
                }
            }
            MotionEvent.ACTION_CANCEL -> { activeSlider = -1; persistEffects() }
        }
        return true // The modal always consumes touches; never reconnect through it.
    }

    private fun sliderAt(y: Float): Int {
        var index = 0
        while (index < SLIDER_COUNT) {
            val center = SLIDER_FIRST_Y + index * SLIDER_ROW_GAP
            if (abs(y - center) <= 32f) return index
            index++
        }
        return -1
    }

    private fun updateSlider(index: Int, x: Float) {
        val fraction = ((x - SLIDER_LEFT) / (SLIDER_RIGHT - SLIDER_LEFT)).coerceIn(0f, 1f)
        val previous = effects
        val updated = when (index) {
            0 -> effects.copy(scanlineOpacity = (fraction * 100f).roundToInt())
            1 -> effects.copy(scanlineWidthPx = 1 + (fraction * 3f).roundToInt())
            2 -> effects.copy(scanlineGapPx = 1 + (fraction * 11f).roundToInt())
            3 -> effects.copy(glowIntensity = (fraction * 100f).roundToInt())
            4 -> effects.copy(glowRadiusPx = (fraction * 8f).roundToInt())
            else -> effects
        }.bounded()
        if (updated == previous) return
        effects = updated
        if (
            updated.scanlineWidthPx != previous.scanlineWidthPx ||
            updated.scanlineGapPx != previous.scanlineGapPx
        ) {
            scanlinePatternDirty = true
        }
        updateSettingsValueLabels()
        contentDirty = true
        invalidate()
    }

    private fun updateSettingsValueLabels() {
        settingsValueLabels[0] = "${effects.scanlineOpacity}%"
        settingsValueLabels[1] = "${effects.scanlineWidthPx}px"
        settingsValueLabels[2] = "${effects.scanlineGapPx}px"
        settingsValueLabels[3] = "${effects.glowIntensity}%"
        settingsValueLabels[4] = "${effects.glowRadiusPx}px"
    }

    private fun sliderFraction(index: Int): Float = when (index) {
        0 -> effects.scanlineOpacity / 100f
        1 -> (effects.scanlineWidthPx - 1) / 3f
        2 -> (effects.scanlineGapPx - 1) / 11f
        3 -> effects.glowIntensity / 100f
        4 -> effects.glowRadiusPx / 8f
        else -> 0f
    }

    private fun persistEffects() {
        preferences.edit()
            .putInt(KEY_SCANLINE_OPACITY, effects.scanlineOpacity)
            .putInt(KEY_SCANLINE_WIDTH, effects.scanlineWidthPx)
            .putInt(KEY_SCANLINE_GAP, effects.scanlineGapPx)
            .putInt(KEY_GLOW_INTENSITY, effects.glowIntensity)
            .putInt(KEY_GLOW_RADIUS, effects.glowRadiusPx)
            .apply()
    }

    private fun buttonAt(x: Float, y: Float): String? {
        val logicalX = x / PixelDashboardModel.LOGICAL_SCALE
        val logicalY = y / PixelDashboardModel.LOGICAL_SCALE
        val settings = layout.layersById["settings-button"]
        if (settings != null && logicalX >= settings.x && logicalX <= settings.x + settings.width &&
            logicalY >= settings.y && logicalY <= settings.y + settings.height) return "settings-button"
        val car = layout.layers.first { it.role == "side-car" }
        return if (logicalX >= car.x && logicalX <= car.x + car.width &&
            logicalY >= car.y && logicalY <= car.y + car.height) "side-car-hit" else null
    }

    private fun buttonEnabled(id: String): Boolean = id == "settings-button" ||
        (id == "side-car-hit" && !connectionBusy)

    private fun finiteFresh(value: Double?, fresh: Boolean): Float? =
        value?.takeIf { fresh && it.isFinite() }?.toFloat()

    private fun Float.approximatelyEquals(other: Float): Boolean = abs(this - other) <= TARGET_EPSILON

    private fun haloAlpha(): Int =
        ((effects.glowIntensity / 100f) * 0.7f * 255f / 8f).roundToInt().coerceIn(0, 255)

    private fun alphaByte(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).roundToInt()

    private data class Transition(
        val from: Float,
        val to: Float,
        val startedAtMs: Long,
        val durationMs: Long
    ) {
        fun valueAt(nowMs: Long): Float = PixelDashboardModel.smoothStep(
            from,
            to,
            nowMs - startedAtMs,
            durationMs
        )

        fun finished(nowMs: Long): Boolean = nowMs - startedAtMs >= durationMs
    }

    private companion object {
        private val VALIDATION_BUILD = BuildConfig.DEBUG
        private val DYNAMIC_ROLES = setOf("slot", "soc-fill", "power-fill", "power-cursor", "idle")
        const val PREFERENCES = "crt_display_v035"
        const val KEY_SCANLINE_OPACITY = "scanline_opacity"
        const val KEY_SCANLINE_WIDTH = "scanline_width"
        const val KEY_SCANLINE_GAP = "scanline_gap"
        const val KEY_GLOW_INTENSITY = "glow_intensity"
        const val KEY_GLOW_RADIUS = "glow_radius"
        const val NO_TIME = -1L
        const val SELF_TEST_DURATION_MS = 1_000L
        const val TRANSITION_FRAME_MS = 50L
        const val WHEEL_FRAME_MS = 100L
        const val TWO_PI = (PI * 2.0).toFloat()
        const val STATUS_THRESHOLD = 96
        const val PHOSPHOR = 0xFF76FF96.toInt()
        const val IDLE_INACTIVE_ALPHA = 0.10f
        const val TARGET_EPSILON = 0.0001f


        const val SETTINGS_LEFT = 260f
        const val SETTINGS_TOP = 70f
        const val SETTINGS_RIGHT = 1020f
        const val SETTINGS_BOTTOM = 650f
        const val CLOSE_LEFT = 940f
        const val CLOSE_TOP = 84f
        const val CLOSE_RIGHT = 990f
        const val CLOSE_BOTTOM = 134f
        const val SETTINGS_LABEL_X = 310f
        const val SETTINGS_VALUE_X = 540f
        const val SLIDER_LEFT = 570f
        const val SLIDER_RIGHT = 950f
        const val SLIDER_FIRST_Y = 210f
        const val SLIDER_ROW_GAP = 88f
        const val SLIDER_COUNT = 5
        const val CLOSE_SLIDER = -2
        const val DEVICE_ACTION = -3
        val SETTINGS_LABELS = arrayOf("扫描线不透明度", "扫描线宽度", "扫描线间隔", "辉光强度", "辉光半径")
    }
}

private fun powerMaskGeometry(fill: PixelLayer, cursor: PixelLayer): PowerBarGeometry {
    val mask = requireNotNull(fill.bitmap) { "Missing authored power-fill mask" }
    var left = mask.width
    var right = -1
    for (y in 0 until mask.height) for (x in 0 until mask.width) {
        if (Color.alpha(mask.getPixel(x, y)) != 0) {
            left = minOf(left, x)
            right = maxOf(right, x)
        }
    }
    require(right >= left) { "Empty authored power-fill mask" }
    return PowerBarGeometry(left, right, cursor.x + cursor.width / 2)
}

private data class PixelLayout(
    val layers: List<PixelLayer>,
    val layersById: Map<String, PixelLayer>,
    val glyphSets: Map<String, PixelGlyphSet>,
    val defaultEffects: CrtEffectSettings
) {
    companion object {
        fun load(context: Context): PixelLayout {
            val root = context.assets.open(LAYOUT_ASSET).bufferedReader().use { reader ->
                JSONObject(reader.readText())
            }
            val palette = Color.parseColor(root.getJSONObject("palette").getString("phosphor"))
            val glyphSets = parseGlyphSets(root.getJSONObject("glyphs"), palette)
            val layerArray = root.getJSONArray("layers")
            val layers = ArrayList<PixelLayer>(layerArray.length())
            var index = 0
            while (index < layerArray.length()) {
                val value = layerArray.getJSONObject(index)
                val id = value.getString("id")
                // Do not allocate bitmaps/caches or retain hitboxes for the retired controls.
                if (id == "button-device" || id == "button-start" || id == "button-end") {
                    index++
                    continue
                }
                val width = value.getInt("w")
                val height = value.getInt("h")
                val visible = value.optBoolean("visible", true)
                val bitmap = value.optString("data").takeIf { it.isNotEmpty() }?.let { encoded ->
                    decodeMask(encoded, width, height, palette)
                }
                val physicalText = if (value.has("physicalText") && id != "status-block") {
                    decodeAssetOrNull(context, "rx400h_ui/physical_text/physical_${assetId(id)}.png")
                } else {
                    null
                }
                val role = value.optString("role", "static")
                val rotationFrames = if (role == "wheel-spokes" && bitmap != null) {
                    buildWheelFrames(bitmap, palette)
                } else {
                    null
                }
                layers += PixelLayer(
                    id = id,
                    role = role,
                    x = value.getInt("x"),
                    y = value.getInt("y"),
                    width = width,
                    height = height,
                    z = value.optDouble("z", 0.0),
                    alpha = value.optDouble("alpha", 1.0).toFloat(),
                    visible = visible,
                    glyphSet = value.optString("glyphSet", ""),
                    binding = value.optString("binding", ""),
                    align = value.optString("align", "right"),
                    bitmap = bitmap,
                    physicalTextBitmap = physicalText,
                    rotationFrames = rotationFrames
                )
                index++
            }
            layers.sortBy { it.z }
            val effects = root.getJSONObject("effects")
            val defaults = CrtEffectSettings(
                scanlineOpacity = effects.getInt("scanlineOpacity"),
                scanlineWidthPx = effects.getInt("scanlineWidthPx"),
                scanlineGapPx = effects.getInt("scanlineGapPx"),
                glowIntensity = effects.getInt("glowIntensity"),
                glowRadiusPx = effects.getInt("glowRadiusPx")
            ).bounded()
            return PixelLayout(layers, layers.associateBy { it.id }, glyphSets, defaults)
        }

        private fun parseGlyphSets(root: JSONObject, color: Int): Map<String, PixelGlyphSet> {
            val result = HashMap<String, PixelGlyphSet>()
            val names = root.keys()
            while (names.hasNext()) {
                val name = names.next()
                val value = root.getJSONObject(name)
                val characters = HashMap<Char, Bitmap>()
                val charObject = value.getJSONObject("chars")
                val charKeys = charObject.keys()
                while (charKeys.hasNext()) {
                    val key = charKeys.next()
                    val glyph = charObject.getJSONObject(key)
                    characters[key[0]] = decodeMask(
                        glyph.getString("data"),
                        glyph.getInt("w"),
                        glyph.getInt("h"),
                        color
                    )
                }
                result[name] = PixelGlyphSet(
                    scale = value.getInt("scale"),
                    advance = value.getInt("advance"),
                    lineHeight = value.getInt("lineHeight"),
                    characters = characters
                )
            }
            return result
        }

        private fun decodeMask(encoded: String, width: Int, height: Int, color: Int): Bitmap {
            val raw = Base64.decode(encoded, Base64.DEFAULT)
            require(raw.size == width * height) {
                "Pixel mask size mismatch: ${raw.size} != ${width * height}"
            }
            val pixels = IntArray(raw.size)
            var index = 0
            while (index < raw.size) {
                if (raw[index].toInt() != 0) pixels[index] = color
                index++
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }

        private fun decodeAssetOrNull(context: Context, path: String): Bitmap? = try {
            context.assets.open(path).use(BitmapFactory::decodeStream)
        } catch (_: IOException) {
            null
        }

        private fun buildWheelFrames(source: Bitmap, color: Int): Array<Bitmap> {
            val width = source.width
            val height = source.height
            val sourcePixels = IntArray(width * height)
            source.getPixels(sourcePixels, 0, width, 0, 0, width, height)
            val centers = intArrayOf(39, 77, 145, 77)
            return Array(WHEEL_ROTATION_FRAMES) { frame ->
                val angle = frame.toDouble() / WHEEL_ROTATION_FRAMES * PI * 2.0
                val cosine = kotlin.math.cos(angle)
                val sine = kotlin.math.sin(angle)
                val output = IntArray(width * height)
                var y = 0
                while (y < height) {
                    var x = 0
                    while (x < width) {
                        if (sourcePixels[y * width + x] != 0) {
                            val centerOffset = if (abs(x - centers[0]) <= abs(x - centers[2])) 0 else 2
                            val centerX = centers[centerOffset]
                            val centerY = centers[centerOffset + 1]
                            val dx = x - centerX
                            val dy = y - centerY
                            val rotatedX = (centerX + dx * cosine - dy * sine).roundToInt()
                            val rotatedY = (centerY + dx * sine + dy * cosine).roundToInt()
                            if (rotatedX in 0 until width && rotatedY in 0 until height) {
                                output[rotatedY * width + rotatedX] = color
                            }
                        }
                        x++
                    }
                    y++
                }
                Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
            }
        }

        private fun assetId(value: String): String = value.lowercase().replace('-', '_')

        private const val LAYOUT_ASSET = "rx400h_ui/layout_v035_pixel_v2.json"
        private const val WHEEL_ROTATION_FRAMES = 36
    }
}

private data class PixelLayer(
    val id: String,
    val role: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val z: Double,
    val alpha: Float,
    val visible: Boolean,
    val glyphSet: String,
    val binding: String,
    val align: String,
    val bitmap: Bitmap?,
    val physicalTextBitmap: Bitmap?,
    val rotationFrames: Array<Bitmap>?
)

private data class PixelGlyphSet(
    val scale: Int,
    val advance: Int,
    val lineHeight: Int,
    val characters: Map<Char, Bitmap>
)
