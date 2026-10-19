package com.kilat.motion.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.kilat.motion.ui.theme.KColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Set ikon KILAT — digambar MANUAL pakai Canvas, tanpa emoji, tanpa glyph teks,
 * tanpa ikon bawaan Android. Tiap ikon punya gaya sendiri:
 * ada yang filled solid, outline rounded, duotone (dua lapis warna), dst.
 * Viewport desain 24x24, di-scale ke ukuran view.
 */
enum class KilatIcon {
    BOLT, HOME, FOLDER, LAYERS, USER, PLUS,
    TEXT, SHAPE, IMAGE, SLIDERS, EXPORT, PLAY, PAUSE, LOOP, RESET,
    MUSIC, SETTINGS, SAVE, TRASH, COPY, CLOSE, BACK, NEXT,
    CHEVRON_DOWN, CHEVRON_RIGHT, MORE, KEYFRAME, EYE, EYE_OFF,
    CHECK, SPARK, FILM, CLOCK, RATIO, BOOK, INFO, GRID, STAR, SHARE, WAND
}

object IconPainter {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fill2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rf = RectF()

    /** Gambar [icon] di kotak (x,y,size). [c2] = warna lapis kedua (duotone); null = turunkan dari c1. */
    fun draw(canvas: Canvas, icon: KilatIcon, x: Float, y: Float, size: Float, c1: Int, c2: Int? = null) {
        val s = size / 24f
        val second = c2 ?: withAlpha(c1, 0.38f)
        fill.color = c1
        fill2.color = second
        stroke.color = c1
        stroke.strokeWidth = 1.9f * s
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(s, s)
        when (icon) {
            KilatIcon.BOLT -> {
                // petir filled + ekor duotone geser: identitas KILAT
                path.reset()
                path.moveTo(13.2f, 2f); path.lineTo(5f, 13.2f); path.lineTo(10.2f, 13.2f)
                path.lineTo(8.8f, 22f); path.lineTo(19f, 9.8f); path.lineTo(13.6f, 9.8f)
                path.close()
                canvas.drawPath(path, fill)
                path.reset()
                path.moveTo(16.8f, 3f); path.lineTo(13.4f, 8f); path.lineTo(15.6f, 8f)
                path.lineTo(13.8f, 13.4f); path.lineTo(19.6f, 6.8f); path.lineTo(17.2f, 6.8f)
                path.close()
                canvas.drawPath(path, fill2)
            }
            KilatIcon.HOME -> {
                // rumah outline rounded + pintu filled
                path.reset()
                path.moveTo(4f, 11f); path.lineTo(12f, 3.6f); path.lineTo(20f, 11f)
                canvas.drawPath(path, stroke)
                path.reset()
                path.moveTo(6f, 9.4f); path.lineTo(6f, 20f); path.lineTo(18f, 20f); path.lineTo(18f, 9.4f)
                canvas.drawPath(path, stroke)
                rf.set(10f, 13.5f, 14f, 20f)
                canvas.drawRoundRect(rf, 1.2f, 1.2f, fill)
            }
            KilatIcon.FOLDER -> {
                // folder duotone: tab belakang pudar, badan outline
                rf.set(3f, 6.5f, 11f, 9f)
                canvas.drawRoundRect(rf, 1.6f, 1.6f, fill2)
                rf.set(3f, 8f, 21f, 19.5f)
                canvas.drawRoundRect(rf, 2.2f, 2.2f, stroke)
                canvas.drawLine(3.2f, 12.4f, 20.8f, 12.4f, stroke)
            }
            KilatIcon.LAYERS -> {
                // tiga wajik bertumpuk (template / layer)
                fun diamond(cy: Float, p: Paint) {
                    path.reset()
                    path.moveTo(12f, cy - 4.2f); path.lineTo(20.5f, cy); path.lineTo(12f, cy + 4.2f); path.lineTo(3.5f, cy)
                    path.close()
                    canvas.drawPath(path, p)
                }
                diamond(7.4f, fill)
                diamond(12.2f, stroke)
                diamond(17f, stroke)
            }
            KilatIcon.USER -> {
                // kepala outline + bahu filled melengkung
                canvas.drawCircle(12f, 8f, 3.6f, stroke)
                path.reset()
                path.moveTo(4.5f, 20.5f)
                path.cubicTo(5.5f, 15.5f, 8.5f, 14f, 12f, 14f)
                path.cubicTo(15.5f, 14f, 18.5f, 15.5f, 19.5f, 20.5f)
                path.close()
                canvas.drawPath(path, fill)
            }
            KilatIcon.PLUS -> {
                rf.set(10.6f, 4f, 13.4f, 20f); canvas.drawRoundRect(rf, 1.4f, 1.4f, fill)
                rf.set(4f, 10.6f, 20f, 13.4f); canvas.drawRoundRect(rf, 1.4f, 1.4f, fill)
            }
            KilatIcon.TEXT -> {
                // huruf T tegas di dalam bingkai pudar
                rf.set(3f, 3f, 21f, 21f)
                val keep = stroke.color
                stroke.color = second
                canvas.drawRoundRect(rf, 3f, 3f, stroke)
                stroke.color = keep
                canvas.drawLine(7f, 7.5f, 17f, 7.5f, stroke)
                canvas.drawLine(12f, 7.5f, 12f, 16.5f, stroke)
                canvas.drawLine(9.6f, 16.5f, 14.4f, 16.5f, stroke)
            }
            KilatIcon.SHAPE -> {
                // wajik outline + inti filled (bentuk)
                path.reset()
                path.moveTo(12f, 3f); path.lineTo(21f, 12f); path.lineTo(12f, 21f); path.lineTo(3f, 12f)
                path.close()
                canvas.drawPath(path, stroke)
                path.reset()
                path.moveTo(12f, 8.2f); path.lineTo(15.8f, 12f); path.lineTo(12f, 15.8f); path.lineTo(8.2f, 12f)
                path.close()
                canvas.drawPath(path, fill)
            }
            KilatIcon.IMAGE -> {
                // bingkai foto: matahari filled + gunung duotone
                rf.set(3f, 4.5f, 21f, 19.5f)
                canvas.drawRoundRect(rf, 2.4f, 2.4f, stroke)
                canvas.drawCircle(8.6f, 9.4f, 1.7f, fill)
                path.reset()
                path.moveTo(4.5f, 18f); path.lineTo(10f, 12.4f); path.lineTo(13.4f, 15.2f)
                path.lineTo(16.2f, 12.8f); path.lineTo(19.5f, 18f)
                path.close()
                canvas.drawPath(path, fill2)
            }
            KilatIcon.SLIDERS -> {
                // tiga slider horizontal, knob di posisi beda-beda
                canvas.drawLine(4f, 6.5f, 20f, 6.5f, stroke)
                canvas.drawLine(4f, 12f, 20f, 12f, stroke)
                canvas.drawLine(4f, 17.5f, 20f, 17.5f, stroke)
                canvas.drawCircle(9f, 6.5f, 2.1f, fill)
                canvas.drawCircle(15f, 12f, 2.1f, fill)
                canvas.drawCircle(7f, 17.5f, 2.1f, fill)
            }
            KilatIcon.EXPORT -> {
                // panah keluar ke atas dari nampan
                path.reset()
                path.moveTo(4f, 14f); path.lineTo(4f, 19f); path.cubicTo(4f, 20.2f, 5f, 21f, 6.2f, 21f)
                path.lineTo(17.8f, 21f); path.cubicTo(19f, 21f, 20f, 20.2f, 20f, 19f); path.lineTo(20f, 14f)
                canvas.drawPath(path, stroke)
                canvas.drawLine(12f, 16.5f, 12f, 4.5f, stroke)
                path.reset()
                path.moveTo(7.2f, 8.6f); path.lineTo(12f, 3.8f); path.lineTo(16.8f, 8.6f)
                canvas.drawPath(path, stroke)
            }
            KilatIcon.PLAY -> {
                path.reset()
                path.moveTo(8f, 5f); path.lineTo(19f, 12f); path.lineTo(8f, 19f)
                path.close()
                canvas.drawPath(path, fill)
            }
            KilatIcon.PAUSE -> {
                rf.set(6.8f, 5f, 10.4f, 19f); canvas.drawRoundRect(rf, 1.6f, 1.6f, fill)
                rf.set(13.6f, 5f, 17.2f, 19f); canvas.drawRoundRect(rf, 1.6f, 1.6f, fill)
            }
            KilatIcon.LOOP -> {
                rf.set(4.5f, 4.5f, 19.5f, 19.5f)
                canvas.drawArc(rf, -40f, 290f, false, stroke)
                path.reset()
                path.moveTo(19.8f, 2.6f); path.lineTo(20.2f, 7.4f); path.lineTo(15.6f, 6f)
                path.close()
                canvas.drawPath(path, fill)
            }
            KilatIcon.RESET -> {
                rf.set(4.5f, 4.5f, 19.5f, 19.5f)
                canvas.drawArc(rf, 140f, 290f, false, stroke)
                path.reset()
                path.moveTo(4.2f, 2.6f); path.lineTo(3.8f, 7.4f); path.lineTo(8.4f, 6f)
                path.close()
                canvas.drawPath(path, fill)
            }
            KilatIcon.MUSIC -> {
                // not balok: dua kepala filled + balok miring
                canvas.drawLine(9.5f, 17f, 9.5f, 6.5f, stroke)
                canvas.drawLine(18f, 15.5f, 18f, 5f, stroke)
                path.reset()
                path.moveTo(9.5f, 6.5f); path.lineTo(18f, 5f); path.lineTo(18f, 8.2f); path.lineTo(9.5f, 9.7f)
                path.close()
                canvas.drawPath(path, fill)
                rf.set(4.2f, 14.6f, 9.8f, 19f); canvas.drawOval(rf, fill)
                rf.set(12.7f, 13.1f, 18.3f, 17.5f); canvas.drawOval(rf, fill)
            }
            KilatIcon.SETTINGS -> {
                // gir digambar manual: 6 gigi + lubang tengah duotone
                for (i in 0 until 6) {
                    canvas.save()
                    canvas.rotate(i * 60f, 12f, 12f)
                    rf.set(10.7f, 3f, 13.3f, 6.4f)
                    canvas.drawRoundRect(rf, 1f, 1f, fill)
                    canvas.restore()
                }
                canvas.drawCircle(12f, 12f, 5.4f, fill)
                canvas.drawCircle(12f, 12f, 2.3f, fill2)
            }
            KilatIcon.SAVE -> {
                rf.set(4f, 4f, 20f, 20f)
                canvas.drawRoundRect(rf, 2.4f, 2.4f, stroke)
                rf.set(7f, 4f, 16f, 9.5f)
                canvas.drawRect(rf, fill2)
                rf.set(7.5f, 13f, 16.5f, 20f)
                canvas.drawRoundRect(rf, 1.4f, 1.4f, fill)
            }
            KilatIcon.TRASH -> {
                canvas.drawLine(4.5f, 6.5f, 19.5f, 6.5f, stroke)
                canvas.drawLine(9.5f, 6.5f, 10.2f, 4.2f, stroke)
                canvas.drawLine(14.5f, 6.5f, 13.8f, 4.2f, stroke)
                path.reset()
                path.moveTo(6f, 6.5f); path.lineTo(7.2f, 19.5f)
                path.cubicTo(7.3f, 20.4f, 8f, 21f, 9f, 21f); path.lineTo(15f, 21f)
                path.cubicTo(16f, 21f, 16.7f, 20.4f, 16.8f, 19.5f); path.lineTo(18f, 6.5f)
                canvas.drawPath(path, stroke)
                canvas.drawLine(10f, 10.5f, 10.4f, 17f, stroke)
                canvas.drawLine(14f, 10.5f, 13.6f, 17f, stroke)
            }
            KilatIcon.COPY -> {
                rf.set(8.5f, 8.5f, 20f, 20f)
                canvas.drawRoundRect(rf, 2.2f, 2.2f, fill)
                rf.set(4f, 4f, 15.5f, 15.5f)
                canvas.drawRoundRect(rf, 2.2f, 2.2f, stroke)
            }
            KilatIcon.CLOSE -> {
                canvas.drawLine(6f, 6f, 18f, 18f, stroke)
                canvas.drawLine(18f, 6f, 6f, 18f, stroke)
            }
            KilatIcon.BACK -> {
                path.reset()
                path.moveTo(15f, 4.5f); path.lineTo(8f, 12f); path.lineTo(15f, 19.5f)
                canvas.drawPath(path, stroke)
            }
            KilatIcon.NEXT -> {
                canvas.drawLine(4f, 12f, 19f, 12f, stroke)
                path.reset()
                path.moveTo(13.5f, 6f); path.lineTo(19.5f, 12f); path.lineTo(13.5f, 18f)
                canvas.drawPath(path, stroke)
            }
            KilatIcon.CHEVRON_DOWN -> {
                path.reset()
                path.moveTo(5.5f, 9f); path.lineTo(12f, 15.5f); path.lineTo(18.5f, 9f)
                canvas.drawPath(path, stroke)
            }
            KilatIcon.CHEVRON_RIGHT -> {
                path.reset()
                path.moveTo(9f, 5.5f); path.lineTo(15.5f, 12f); path.lineTo(9f, 18.5f)
                canvas.drawPath(path, stroke)
            }
            KilatIcon.MORE -> {
                canvas.drawCircle(12f, 5.5f, 1.9f, fill)
                canvas.drawCircle(12f, 12f, 1.9f, fill)
                canvas.drawCircle(12f, 18.5f, 1.9f, fill)
            }
            KilatIcon.KEYFRAME -> {
                path.reset()
                path.moveTo(12f, 4f); path.lineTo(20f, 12f); path.lineTo(12f, 20f); path.lineTo(4f, 12f)
                path.close()
                canvas.drawPath(path, fill)
            }
            KilatIcon.EYE -> {
                path.reset()
                path.moveTo(2.8f, 12f)
                path.cubicTo(6f, 6.8f, 9f, 5f, 12f, 5f)
                path.cubicTo(15f, 5f, 18f, 6.8f, 21.2f, 12f)
                path.cubicTo(18f, 17.2f, 15f, 19f, 12f, 19f)
                path.cubicTo(9f, 19f, 6f, 17.2f, 2.8f, 12f)
                canvas.drawPath(path, stroke)
                canvas.drawCircle(12f, 12f, 2.6f, fill)
            }
            KilatIcon.EYE_OFF -> {
                path.reset()
                path.moveTo(2.8f, 12f)
                path.cubicTo(6f, 6.8f, 9f, 5f, 12f, 5f)
                path.cubicTo(15f, 5f, 18f, 6.8f, 21.2f, 12f)
                path.cubicTo(18f, 17.2f, 15f, 19f, 12f, 19f)
                path.cubicTo(9f, 19f, 6f, 17.2f, 2.8f, 12f)
                canvas.drawPath(path, stroke)
                canvas.drawLine(4.5f, 4.5f, 19.5f, 19.5f, stroke)
            }
            KilatIcon.CHECK -> {
                path.reset()
                path.moveTo(4.5f, 12.8f); path.lineTo(9.8f, 17.8f); path.lineTo(19.5f, 6.5f)
                canvas.drawPath(path, stroke)
            }
            KilatIcon.SPARK -> {
                // bintang 4 ujung tajam khas "magic" + titik pendamping
                path.reset()
                path.moveTo(10f, 3f)
                path.cubicTo(11f, 8f, 13f, 10f, 18f, 11f)
                path.cubicTo(13f, 12f, 11f, 14f, 10f, 19f)
                path.cubicTo(9f, 14f, 7f, 12f, 2f, 11f)
                path.cubicTo(7f, 10f, 9f, 8f, 10f, 3f)
                path.close()
                canvas.drawPath(path, fill)
                canvas.drawCircle(18.5f, 5f, 1.4f, fill)
                canvas.drawCircle(18f, 17.5f, 1.1f, fill2)
            }
            KilatIcon.FILM -> {
                rf.set(3.5f, 4.5f, 20.5f, 19.5f)
                canvas.drawRoundRect(rf, 2.2f, 2.2f, stroke)
                canvas.drawLine(8f, 4.8f, 8f, 19.2f, stroke)
                canvas.drawLine(16f, 4.8f, 16f, 19.2f, stroke)
                canvas.drawLine(3.8f, 9.5f, 7.7f, 9.5f, stroke)
                canvas.drawLine(3.8f, 14.5f, 7.7f, 14.5f, stroke)
                canvas.drawLine(16.3f, 9.5f, 20.2f, 9.5f, stroke)
                canvas.drawLine(16.3f, 14.5f, 20.2f, 14.5f, stroke)
                path.reset()
                path.moveTo(10.6f, 9.8f); path.lineTo(14.4f, 12f); path.lineTo(10.6f, 14.2f)
                path.close()
                canvas.drawPath(path, fill)
            }
            KilatIcon.CLOCK -> {
                canvas.drawCircle(12f, 12f, 8.2f, stroke)
                canvas.drawLine(12f, 12f, 12f, 7.8f, stroke)
                canvas.drawLine(12f, 12f, 15.4f, 14f, stroke)
                canvas.drawCircle(12f, 12f, 1.2f, fill)
            }
            KilatIcon.RATIO -> {
                // empat siku bingkai rasio + kotak tengah duotone
                rf.set(8f, 8f, 16f, 16f)
                canvas.drawRoundRect(rf, 1.4f, 1.4f, fill2)
                canvas.drawLine(3.5f, 8f, 3.5f, 3.5f, stroke); canvas.drawLine(3.5f, 3.5f, 8f, 3.5f, stroke)
                canvas.drawLine(16f, 3.5f, 20.5f, 3.5f, stroke); canvas.drawLine(20.5f, 3.5f, 20.5f, 8f, stroke)
                canvas.drawLine(20.5f, 16f, 20.5f, 20.5f, stroke); canvas.drawLine(20.5f, 20.5f, 16f, 20.5f, stroke)
                canvas.drawLine(8f, 20.5f, 3.5f, 20.5f, stroke); canvas.drawLine(3.5f, 20.5f, 3.5f, 16f, stroke)
            }
            KilatIcon.BOOK -> {
                path.reset()
                path.moveTo(12f, 6.5f)
                path.cubicTo(9.5f, 4.8f, 6.5f, 4.2f, 4f, 4.8f); path.lineTo(4f, 19f)
                path.cubicTo(6.5f, 18.4f, 9.5f, 19f, 12f, 20.5f)
                path.cubicTo(14.5f, 19f, 17.5f, 18.4f, 20f, 19f); path.lineTo(20f, 4.8f)
                path.cubicTo(17.5f, 4.2f, 14.5f, 4.8f, 12f, 6.5f)
                path.close()
                canvas.drawPath(path, stroke)
                canvas.drawLine(12f, 6.5f, 12f, 20.5f, stroke)
            }
            KilatIcon.INFO -> {
                canvas.drawCircle(12f, 12f, 8.4f, stroke)
                canvas.drawCircle(12f, 8f, 1.5f, fill)
                canvas.drawLine(12f, 11.2f, 12f, 16.2f, stroke)
            }
            KilatIcon.GRID -> {
                rf.set(4f, 4f, 10.6f, 10.6f); canvas.drawRoundRect(rf, 1.8f, 1.8f, fill)
                rf.set(13.4f, 4f, 20f, 10.6f); canvas.drawRoundRect(rf, 1.8f, 1.8f, stroke)
                rf.set(4f, 13.4f, 10.6f, 20f); canvas.drawRoundRect(rf, 1.8f, 1.8f, stroke)
                rf.set(13.4f, 13.4f, 20f, 20f); canvas.drawRoundRect(rf, 1.8f, 1.8f, fill2)
            }
            KilatIcon.STAR -> {
                path.reset()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 8.6f else 3.8f
                    val a = Math.toRadians((i * 36 - 90).toDouble())
                    val px = 12f + r * cos(a).toFloat()
                    val py = 12f + r * sin(a).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, fill)
            }
            KilatIcon.SHARE -> {
                canvas.drawCircle(6f, 12f, 2.4f, fill)
                canvas.drawCircle(17.5f, 5.5f, 2.4f, fill)
                canvas.drawCircle(17.5f, 18.5f, 2.4f, fill)
                canvas.drawLine(8.1f, 10.8f, 15.4f, 6.6f, stroke)
                canvas.drawLine(8.1f, 13.2f, 15.4f, 17.4f, stroke)
            }
            KilatIcon.WAND -> {
                // tongkat preset: garis miring tebal + semburan bintang
                canvas.drawLine(4.5f, 19.5f, 15.5f, 8.5f, stroke)
                canvas.drawCircle(18f, 5.5f, 1.5f, fill)
                canvas.drawCircle(13.5f, 3.8f, 1.1f, fill)
                canvas.drawCircle(20f, 10.5f, 1.1f, fill2)
                canvas.drawCircle(8f, 6.5f, 1f, fill2)
            }
        }
        canvas.restore()
    }

    private fun withAlpha(color: Int, f: Float): Int {
        val a = (Color.alpha(color) * f).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}

/** View ikon custom. Ukuran mengikuti layout params; ikon digambar di tengah area. */
class IconView(
    context: Context,
    icon: KilatIcon = KilatIcon.BOLT,
    tint: Int = KColors.Text,
    tint2: Int? = null
) : View(context) {

    var icon: KilatIcon = icon
        set(v) { field = v; invalidate() }
    var tintColor: Int = tint
        set(v) { field = v; invalidate() }
    var tintColor2: Int? = tint2
        set(v) { field = v; invalidate() }
    /** Padding ikon dari tepi view (0..0.4 dari ukuran). */
    var iconInset: Float = 0.08f
        set(v) { field = v; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom
        if (w <= 0 || h <= 0) return
        val size = min(w, h) * (1f - iconInset * 2f)
        val x = paddingLeft + (w - size) / 2f
        val y = paddingTop + (h - size) / 2f
        IconPainter.draw(canvas, icon, x, y, size, tintColor, tintColor2)
    }
}
