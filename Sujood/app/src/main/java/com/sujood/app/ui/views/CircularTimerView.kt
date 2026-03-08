package com.sujood.app.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Custom view that draws the circular progress arc matching the design:
 * - Dark semi-transparent background circle
 * - Thin grey track ring
 * - Bright blue progress arc (sweeps from top, clockwise)
 * - Subtle blue glow on the arc
 */
class CircularTimerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // 0f = empty, 1f = full
    var progress: Float = 1f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.5f)
        color = Color.argb(30, 255, 255, 255)  // very faint white track
        strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(12f)
        color = Color.argb(40, 17, 50, 212)  // soft blue glow
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(dpToPx(8f), BlurMaskFilter.Blur.NORMAL)
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(4f)
        color = Color.rgb(17, 50, 212)  // #1132D4
        strokeCap = Paint.Cap.ROUND
    }

    private val bgCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(60, 10, 18, 45)  // dark navy fill
    }

    private val arcRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val strokeWidth = dpToPx(4f)
        val radius = (minOf(width, height) / 2f) - strokeWidth - dpToPx(8f)

        // Background circle fill
        canvas.drawCircle(cx, cy, radius, bgCirclePaint)

        // Track ring
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(arcRect, -90f, 360f, false, trackPaint)

        if (progress > 0f) {
            val sweep = 360f * progress
            // Glow pass
            canvas.drawArc(arcRect, -90f, sweep, false, glowPaint)
            // Arc pass
            canvas.drawArc(arcRect, -90f, sweep, false, arcPaint)
        }
    }

    private fun dpToPx(dp: Float): Float = dp * context.resources.displayMetrics.density
}
