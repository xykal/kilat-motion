package com.fluxa.motion.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.fluxa.motion.ui.theme.FluxaColors
import kotlin.math.floor

/**
 * Penggaris geser: nilai bisa digeser tanpa batas min/max.
 * Satu tick = [step] satuan, tick utama tiap 5 tick. Garis tengah = nilai aktif.
 * Geser jari ke kanan = nilai turun, ke kiri = nilai naik (seperti menggeser isi penggaris).
 */
class RulerSlider(context: Context) : View(context) {

    var step: Float = 1f
    var value: Float = 0f
        set(v) {
            field = v
            invalidate()
        }
    var onChange: ((Float, Boolean) -> Unit)? = null
    var dragging = false
        private set

    private val density = resources.displayMetrics.density
    private val tickPx = 8f * density
    private var lastX = 0f
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FluxaColors.Accent
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF11151F.toInt() }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        rect.set(0f, 0f, w, h)
        val r = 10f * density
        canvas.drawRoundRect(rect, r, r, bgPaint)
        val cx = w / 2f
        val baseTick = floor(value / step).toInt()
        val span = (cx / tickPx).toInt() + 2
        for (k in -span..span) {
            val idx = baseTick + k
            val x = cx + (idx - value / step) * tickPx
            if (x < 0f || x > w) continue
            val major = idx % 5 == 0
            val len = if (major) h * 0.55f else h * 0.3f
            tickPaint.color = if (major) 0xFFB8C2D6.toInt() else 0x66B8C2D6
            canvas.drawLine(x, (h - len) / 2f, x, (h + len) / 2f, tickPaint)
        }
        canvas.drawLine(cx, 4f * density, cx, h - 4f * density, accentPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = e.x
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastX
                lastX = e.x
                value -= dx / tickPx * step
                onChange?.invoke(value, false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                onChange?.invoke(value, true)
            }
        }
        return true
    }
}
