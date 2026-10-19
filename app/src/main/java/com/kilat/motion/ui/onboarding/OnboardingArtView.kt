package com.kilat.motion.ui.onboarding

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import com.kilat.motion.ui.components.IconPainter
import com.kilat.motion.ui.components.KilatIcon
import com.kilat.motion.ui.theme.KColors
import com.kilat.motion.ui.theme.dpf
import kotlin.math.min
import kotlin.math.sin

/**
 * Ilustrasi onboarding digambar manual per halaman (tanpa aset gambar/emoji):
 * 0 = logo + bentuk mengorbit, 1 = timeline + keyframe, 2 = tumpukan layer,
 * 3 = export video. Ada gerak melayang halus selama view terpasang.
 */
class OnboardingArtView(context: Context, page: Int, accent: Int) : View(context) {

    var page: Int = page
        set(v) { field = v; invalidate() }
    var accent: Int = accent
        set(v) { field = v; invalidate() }

    private var running = false
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            invalidate()
            postDelayed(this, 33)
        }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rf = RectF()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        post(ticker)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    private fun phase(speed: Float = 1f): Float =
        sin(SystemClock.uptimeMillis() / 900.0 * speed).toFloat()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val unit = min(w, h)

        // cahaya latar lembut dua warna
        glow.color = withAlpha(accent, 26)
        canvas.drawCircle(cx - unit * 0.22f, cy - unit * 0.18f, unit * 0.34f, glow)
        glow.color = withAlpha(KColors.Violet, 20)
        canvas.drawCircle(cx + unit * 0.26f, cy + unit * 0.2f, unit * 0.3f, glow)
        glow.color = withAlpha(accent, 14)
        canvas.drawCircle(cx, cy, unit * 0.46f, glow)

        when (page) {
            0 -> drawWelcome(canvas, cx, cy, unit)
            1 -> drawTimelineArt(canvas, cx, cy, unit)
            2 -> drawLayersArt(canvas, cx, cy, unit)
            else -> drawExportArt(canvas, cx, cy, unit)
        }
    }

    private fun drawWelcome(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        val bob = phase() * dpf(6f)
        val size = u * 0.42f
        canvas.save()
        canvas.translate(cx, cy + bob)
        canvas.rotate(-8f)
        fill.color = KColors.CardHi
        rf.set(-size / 2, -size / 2, size / 2, size / 2)
        canvas.drawRoundRect(rf, size * 0.26f, size * 0.26f, fill)
        stroke.color = withAlpha(accent, 120)
        stroke.strokeWidth = dpf(1.5f)
        canvas.drawRoundRect(rf, size * 0.26f, size * 0.26f, stroke)
        val iconSize = size * 0.62f
        IconPainter.draw(canvas, KilatIcon.BOLT, -iconSize / 2, -iconSize / 2, iconSize, accent, KColors.Sky)
        canvas.restore()

        // ornamen mengorbit
        val o1 = phase(0.8f) * dpf(8f)
        IconPainter.draw(canvas, KilatIcon.STAR, cx - u * 0.4f, cy - u * 0.34f + o1, u * 0.11f, KColors.Amber)
        IconPainter.draw(canvas, KilatIcon.SPARK, cx + u * 0.32f, cy - u * 0.3f - o1, u * 0.12f, KColors.Sky)
        IconPainter.draw(canvas, KilatIcon.SHAPE, cx + u * 0.34f, cy + u * 0.26f + o1, u * 0.1f, KColors.Violet)
        IconPainter.draw(canvas, KilatIcon.KEYFRAME, cx - u * 0.38f, cy + u * 0.3f - o1, u * 0.075f, accent)
    }

    private fun drawTimelineArt(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        val cardW = u * 0.86f
        val cardH = u * 0.52f
        val x0 = cx - cardW / 2
        val y0 = cy - cardH / 2 + phase(0.9f) * dpf(5f)
        fill.color = KColors.Panel
        rf.set(x0, y0, x0 + cardW, y0 + cardH)
        canvas.drawRoundRect(rf, dpf(18f), dpf(18f), fill)
        stroke.color = KColors.Line
        stroke.strokeWidth = dpf(1.2f)
        canvas.drawRoundRect(rf, dpf(18f), dpf(18f), stroke)

        // ruler
        stroke.color = KColors.Faint
        val rulerY = y0 + cardH * 0.16f
        for (i in 0..10) {
            val x = x0 + cardW * 0.08f + i * cardW * 0.084f
            canvas.drawLine(x, rulerY, x, rulerY + dpf(if (i % 2 == 0) 8f else 5f), stroke)
        }
        // tiga bar layer
        val bars = listOf(
            Triple(0.08f, 0.62f, accent),
            Triple(0.2f, 0.78f, KColors.Violet),
            Triple(0.05f, 0.45f, KColors.Amber)
        )
        for ((i, b) in bars.withIndex()) {
            val by = y0 + cardH * (0.34f + i * 0.2f)
            val bx0 = x0 + cardW * b.first
            val bx1 = bx0 + cardW * b.second * 0.8f
            fill.color = withAlpha(b.third, 64)
            rf.set(bx0, by, bx1, by + cardH * 0.13f)
            canvas.drawRoundRect(rf, dpf(7f), dpf(7f), fill)
            stroke.color = b.third
            stroke.strokeWidth = dpf(1.3f)
            canvas.drawRoundRect(rf, dpf(7f), dpf(7f), stroke)
            // keyframe wajik di bar
            val kSize = u * 0.045f
            IconPainter.draw(canvas, KilatIcon.KEYFRAME, bx0 + (bx1 - bx0) * 0.25f - kSize / 2, by + cardH * 0.065f - kSize / 2, kSize, b.third)
            IconPainter.draw(canvas, KilatIcon.KEYFRAME, bx0 + (bx1 - bx0) * 0.75f - kSize / 2, by + cardH * 0.065f - kSize / 2, kSize, b.third)
        }
        // playhead bergerak perlahan
        val prog = 0.5f + 0.34f * phase(0.45f)
        val px = x0 + cardW * prog
        stroke.color = KColors.Text
        stroke.strokeWidth = dpf(2f)
        canvas.drawLine(px, y0 + dpf(8f), px, y0 + cardH - dpf(8f), stroke)
        fill.color = KColors.Text
        canvas.drawCircle(px, y0 + dpf(10f), dpf(4.5f), fill)
    }

    private fun drawLayersArt(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        val cardW = u * 0.56f
        val cardH = u * 0.34f
        val cards = listOf(
            Triple(-0.16f, -0.2f, KilatIcon.IMAGE to KColors.Sky),
            Triple(0f, -0.04f, KilatIcon.SHAPE to KColors.Violet),
            Triple(0.16f, 0.12f, KilatIcon.TEXT to accent)
        )
        for ((i, c) in cards.withIndex()) {
            val bob = phase(0.7f + i * 0.22f) * dpf(5f)
            canvas.save()
            canvas.translate(cx + u * c.first, cy + u * c.second + bob)
            canvas.rotate(-7f + i * 6f)
            fill.color = if (i == 2) KColors.CardHi else KColors.Panel
            rf.set(-cardW / 2, -cardH / 2, cardW / 2, cardH / 2)
            canvas.drawRoundRect(rf, dpf(16f), dpf(16f), fill)
            stroke.color = withAlpha(c.third.second, 150)
            stroke.strokeWidth = dpf(1.4f)
            canvas.drawRoundRect(rf, dpf(16f), dpf(16f), stroke)
            val isz = u * 0.17f
            IconPainter.draw(canvas, c.third.first, -cardW / 2 + dpf(16f), -isz / 2, isz, c.third.second)
            // garis-garis proksi slider
            stroke.color = KColors.Faint
            stroke.strokeWidth = dpf(3f)
            val lx = -cardW / 2 + dpf(16f) + isz + dpf(14f)
            canvas.drawLine(lx, -cardH * 0.16f, cardW / 2 - dpf(18f), -cardH * 0.16f, stroke)
            canvas.drawLine(lx, cardH * 0.14f, cardW / 2 - dpf(34f), cardH * 0.14f, stroke)
            canvas.restore()
        }
    }

    private fun drawExportArt(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        val bob = phase() * dpf(6f)
        val phoneW = u * 0.4f
        val phoneH = u * 0.68f
        val x0 = cx - phoneW / 2
        val y0 = cy - phoneH / 2 + bob
        fill.color = KColors.Panel
        rf.set(x0, y0, x0 + phoneW, y0 + phoneH)
        canvas.drawRoundRect(rf, dpf(22f), dpf(22f), fill)
        stroke.color = withAlpha(accent, 140)
        stroke.strokeWidth = dpf(1.5f)
        canvas.drawRoundRect(rf, dpf(22f), dpf(22f), stroke)

        // tombol play di tengah layar
        fill.color = accent
        canvas.drawCircle(cx, cy + bob - phoneH * 0.08f, u * 0.1f, fill)
        IconPainter.draw(
            canvas, KilatIcon.PLAY,
            cx - u * 0.055f, cy + bob - phoneH * 0.08f - u * 0.055f, u * 0.11f,
            KColors.AccentInk
        )
        // bar progres export
        val barY = y0 + phoneH * 0.8f
        fill.color = KColors.Line
        rf.set(x0 + dpf(18f), barY, x0 + phoneW - dpf(18f), barY + dpf(8f))
        canvas.drawRoundRect(rf, dpf(4f), dpf(4f), fill)
        fill.color = accent
        val prog = 0.62f + 0.3f * phase(0.5f)
        rf.set(x0 + dpf(18f), barY, x0 + dpf(18f) + (phoneW - dpf(36f)) * prog, barY + dpf(8f))
        canvas.drawRoundRect(rf, dpf(4f), dpf(4f), fill)

        // badge export melayang
        fill.color = KColors.Violet
        canvas.drawCircle(cx + phoneW * 0.52f, y0 + phoneH * 0.2f, u * 0.085f, fill)
        IconPainter.draw(
            canvas, KilatIcon.EXPORT,
            cx + phoneW * 0.52f - u * 0.05f, y0 + phoneH * 0.2f - u * 0.05f, u * 0.1f,
            0xFFFFFFFF.toInt()
        )
        IconPainter.draw(canvas, KilatIcon.FILM, cx - phoneW * 0.78f, y0 + phoneH * 0.62f, u * 0.11f, KColors.Sky)
        IconPainter.draw(canvas, KilatIcon.STAR, cx + phoneW * 0.62f, y0 + phoneH * 0.78f, u * 0.09f, KColors.Amber)
    }

    private fun withAlpha(color: Int, a: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)
}
