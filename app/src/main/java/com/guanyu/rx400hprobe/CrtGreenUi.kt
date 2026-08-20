package com.guanyu.rx400hprobe

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

/** Lightweight, static CRT treatment. No animation clock or draw-time allocation. */
internal class CrtScreenLayout(context: Context) : FrameLayout(context) {
    private var density = resources.displayMetrics.density.coerceAtLeast(0.1f)
    private var scanlineSpacingPx = max(3, dp(4f).toInt())
    private val scanlinePaint = Paint().apply {
        color = context.getColor(R.color.crt_scanline)
        strokeWidth = 1f
    }
    private val edgePaint = Paint().apply {
        color = context.getColor(R.color.crt_edge_shade)
        style = Paint.Style.STROKE
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.crt_green_frame)
        style = Paint.Style.STROKE
        strokeWidth = max(1f, dp(1f))
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.crt_green_bright)
        style = Paint.Style.STROKE
        strokeWidth = max(1f, dp(1.35f))
    }

    init {
        setWillNotDraw(false)
        clipToPadding = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        drawScanlines(canvas)
        drawEdgeShade(canvas)
        drawOuterFrame(canvas)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        scanlineSpacingPx = max(3, dp(4f).toInt())
        framePaint.strokeWidth = max(1f, dp(1f))
        cornerPaint.strokeWidth = max(1f, dp(1.35f))
        invalidate()
    }

    private fun drawScanlines(canvas: Canvas) {
        var y = paddingTop + scanlineSpacingPx
        val bottom = height - paddingBottom
        while (y < bottom) {
            canvas.drawLine(paddingLeft.toFloat(), y.toFloat(), (width - paddingRight).toFloat(), y.toFloat(), scanlinePaint)
            y += scanlineSpacingPx
        }
    }

    private fun drawEdgeShade(canvas: Canvas) {
        val baseInset = dp(2f)
        val step = dp(3f)
        edgePaint.strokeWidth = dp(5f)
        for (index in 0 until 4) {
            val inset = baseInset + index * step
            canvas.drawRect(
                paddingLeft + inset,
                paddingTop + inset,
                width - paddingRight - inset,
                height - paddingBottom - inset,
                edgePaint
            )
        }
    }

    private fun drawOuterFrame(canvas: Canvas) {
        val inset = dp(5f)
        val left = paddingLeft + inset
        val top = paddingTop + inset
        val right = width - paddingRight - inset
        val bottom = height - paddingBottom - inset
        if (right <= left || bottom <= top) return
        canvas.drawRect(left, top, right, bottom, framePaint)
        drawCornerAccents(canvas, left, top, right, bottom, dp(12f), cornerPaint)
    }

    private fun dp(value: Float): Float = value * density
}

/** Static terminal frame used by cards and button states. */
internal class CrtFrameDrawable(
    fillColor: Int,
    strokeColor: Int,
    cornerColor: Int,
    private val strokeWidthPx: Float,
    private val cornerLengthPx: Float,
    private val cornerInsetPx: Float = 0f
) : Drawable() {
    private val fillPaint = Paint().apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cornerColor
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx * 1.35f
    }

    override fun draw(canvas: Canvas) {
        val halfStroke = strokeWidthPx / 2f
        val left = bounds.left + halfStroke
        val top = bounds.top + halfStroke
        val right = bounds.right - halfStroke
        val bottom = bounds.bottom - halfStroke
        if (right <= left || bottom <= top) return
        canvas.drawRect(left, top, right, bottom, fillPaint)
        canvas.drawRect(left, top, right, bottom, strokePaint)
        drawCornerAccents(
            canvas,
            left + cornerInsetPx,
            top + cornerInsetPx,
            right - cornerInsetPx,
            bottom - cornerInsetPx,
            cornerLengthPx,
            cornerPaint
        )
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        cornerPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        cornerPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

internal fun crtButtonBackground(context: Context): Drawable {
    val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
    fun frame(fill: Int, stroke: Int, corner: Int) = CrtFrameDrawable(
        fillColor = context.getColor(fill),
        strokeColor = context.getColor(stroke),
        cornerColor = context.getColor(corner),
        strokeWidthPx = max(1f, density),
        cornerLengthPx = 9f * density,
        cornerInsetPx = 2f * density
    )
    return StateListDrawable().apply {
        addState(
            intArrayOf(-android.R.attr.state_enabled),
            frame(R.color.crt_panel_disabled, R.color.crt_green_disabled, R.color.crt_green_disabled)
        )
        addState(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
            frame(R.color.crt_panel_pressed, R.color.crt_green_bright, R.color.crt_green_bright)
        )
        addState(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused),
            frame(R.color.crt_panel_pressed, R.color.crt_green_bright, R.color.crt_green_bright)
        )
        addState(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_hovered),
            frame(R.color.crt_surface, R.color.crt_green_bright, R.color.crt_green_bright)
        )
        addState(
            intArrayOf(),
            frame(R.color.crt_surface, R.color.crt_green_frame, R.color.crt_green_bright)
        )
    }
}

internal fun crtButtonTextColors(context: Context): ColorStateList = ColorStateList(
    arrayOf(
        intArrayOf(-android.R.attr.state_enabled),
        intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
        intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused),
        intArrayOf(android.R.attr.state_enabled, android.R.attr.state_hovered),
        intArrayOf()
    ),
    intArrayOf(
        context.getColor(R.color.crt_green_disabled),
        context.getColor(R.color.crt_green_bright),
        context.getColor(R.color.crt_green_bright),
        context.getColor(R.color.crt_green_bright),
        context.getColor(R.color.crt_green_primary)
    )
)

private fun drawCornerAccents(
    canvas: Canvas,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    requestedLength: Float,
    paint: Paint
) {
    val length = min(requestedLength, min((right - left) / 4f, (bottom - top) / 4f)).coerceAtLeast(0f)
    if (length <= 0f) return

    canvas.drawLine(left, top, left + length, top, paint)
    canvas.drawLine(left, top, left, top + length, paint)
    canvas.drawLine(right - length, top, right, top, paint)
    canvas.drawLine(right, top, right, top + length, paint)
    canvas.drawLine(left, bottom - length, left, bottom, paint)
    canvas.drawLine(left, bottom, left + length, bottom, paint)
    canvas.drawLine(right, bottom - length, right, bottom, paint)
    canvas.drawLine(right - length, bottom, right, bottom, paint)
}
