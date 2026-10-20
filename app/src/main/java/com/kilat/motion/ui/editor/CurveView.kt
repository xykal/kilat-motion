package com.kilat.motion.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import com.kilat.motion.core.animation.Anim
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Editor kurva easing cubic-bezier ala graph editor Alight Motion:
 * dua titik kontrol bisa diseret; sumbu X = waktu, sumbu Y = nilai (boleh overshoot).
 */
@SuppressLint("ClickableViewAccessibility")
class CurveView(context: Context) : View(context) {

    var x1: Float = 0.3f
    var y1: Float = 1.4f
    var x2: Float = 0.6f
    var y2: Float = 1f
    var onChange: ((commit: Boolean) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1f); color = 0xFF232A3A.toInt()
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1.2f); color = 0xFF39415A.toInt()
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1.2f); color = 0xFF5B6B8C.toInt()
    }
    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2.4f); color = 0xFF2EE6C0.toInt()
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD166.toInt() }
    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE9EEF7.toInt() }
    private val curvePath = Path()

    private val yMin = -0.7f
    private val yMax = 1.7f

    private fun area(): FloatArray {
        val pad = dp(16f)
        val side = min(width - pad * 2, height - pad * 2)
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        return floatArrayOf(left, top, side)
    }

    private fun pxX(v: Float): Float {
        val a = area()
        return a[0] + v * a[2]
    }

    private fun pxY(v: Float): Float {
        val a = area()
        return a[1] + (yMax - v) / (yMax - yMin) * a[2]
    }

    private fun fromX(px: Float): Float {
        val a = area()
        return ((px - a[0]) / a[2]).coerceIn(0f, 1f)
    }

    private fun fromY(px: Float): Float {
        val a = area()
        return (yMax - (px - a[1]) / a[2] * (yMax - yMin)).coerceIn(yMin, yMax)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val a = area()
        val left = a[0]; val top = a[1]; val side = a[2]
        // grid perempat
        for (i in 0..4) {
            val gx = left + side * i / 4f
            canvas.drawLine(gx, top, gx, top + side, gridPaint)
            val gy = top + side * i / 4f
            canvas.drawLine(left, gy, left + side, gy, gridPaint)
        }
        // sumbu nilai 0 dan 1
        canvas.drawLine(left, pxY(0f), left + side, pxY(0f), axisPaint)
        canvas.drawLine(left, pxY(1f), left + side, pxY(1f), axisPaint)
        // garis bantu titik kontrol
        canvas.drawLine(pxX(0f), pxY(0f), pxX(x1), pxY(y1), guidePaint)
        canvas.drawLine(pxX(1f), pxY(1f), pxX(x2), pxY(y2), guidePaint)
        // kurva
        curvePath.reset()
        val steps = 42
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val v = Anim.cubicBezier(t, x1, y1, x2, y2)
            val px = pxX(t)
            val py = pxY(v)
            if (i == 0) curvePath.moveTo(px, py) else curvePath.lineTo(px, py)
        }
        canvas.drawPath(curvePath, curvePaint)
        // titik ujung + pegangan
        canvas.drawCircle(pxX(0f), pxY(0f), dp(4f), endPaint)
        canvas.drawCircle(pxX(1f), pxY(1f), dp(4f), endPaint)
        canvas.drawCircle(pxX(x1), pxY(y1), dp(7f), handlePaint)
        canvas.drawCircle(pxX(x2), pxY(y2), dp(7f), handlePaint)
    }

    private var dragging = 0 // 0 tidak ada, 1 = P1, 2 = P2

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val d1 = hypot(event.x - pxX(x1), event.y - pxY(y1))
                val d2 = hypot(event.x - pxX(x2), event.y - pxY(y2))
                dragging = when {
                    d1 <= d2 && d1 < dp(28f) -> 1
                    d2 < dp(28f) -> 2
                    else -> if (d1 <= d2) 1 else 2
                }
                applyDrag(event)
            }
            MotionEvent.ACTION_MOVE -> applyDrag(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                dragging = 0
                onChange?.invoke(true)
            }
        }
        return true
    }

    private fun applyDrag(event: MotionEvent) {
        if (dragging == 0) return
        val nx = fromX(event.x)
        val ny = fromY(event.y)
        if (dragging == 1) { x1 = nx; y1 = ny } else { x2 = nx; y2 = ny }
        if (abs(y1) < 0.001f) y1 = 0f
        invalidate()
        onChange?.invoke(false)
    }

    fun setCurve(nx1: Float, ny1: Float, nx2: Float, ny2: Float) {
        x1 = nx1; y1 = ny1; x2 = nx2; y2 = ny2
        invalidate()
    }
}
