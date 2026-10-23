package com.fluxa.motion.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import com.fluxa.motion.core.preview.PreviewSamples
import com.fluxa.motion.core.render.Renderer
import com.fluxa.motion.core.model.Layer
import com.fluxa.motion.ui.theme.dpf
import kotlin.math.min

/**
 * Kotak kecil yang memutar animasi (atau efek) contoh secara terus-menerus.
 * Memakai Renderer yang sama dengan editor, jadi preview sesuai hasil akhir.
 */
class LayerPreviewView(context: Context) : View(context) {

    private var sample: Layer? = null
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF11141C.toInt() }
    private val rect = RectF()
    private val origin = SystemClock.uptimeMillis()

    fun showPreset(preset: String) {
        sample = PreviewSamples.presetLayer(preset)
        invalidate()
    }

    fun showEffect(type: String) {
        sample = PreviewSamples.effectLayer(type)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = context.dpf(12f)
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, bg)

        val layer = sample ?: return
        layer.x = w / 2f
        layer.y = h / 2f
        layer.textSize = min(w, h) * 0.5f

        val elapsed = (SystemClock.uptimeMillis() - origin) / 1000f
        val t = elapsed % PreviewSamples.DURATION

        canvas.save()
        canvas.clipRect(0f, 0f, w, h)
        Renderer.drawLayer(canvas, layer, t)
        canvas.restore()

        // ~30 fps cukup untuk preview; berhenti otomatis saat view dilepas
        postInvalidateDelayed(33L)
    }
}
