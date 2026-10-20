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
 * Set ikon KILAT v2 — digambar MANUAL pakai Canvas, tanpa emoji, tanpa glyph
 * teks, tanpa ikon bawaan Android.
 *
 * Disiplin grid (viewport 24x24):
 * - area aman gambar 2.5..21.5 supaya semua ikon punya napas yang sama,
 * - stroke utama konsisten 2.0 unit, stroke detail 1.6 unit, ujung selalu
 *   round cap/join,
 * - koordinat di-snap ke kelipatan 0.5 unit supaya presisi dan tidak miring,
 * - tiap ikon punya karakter sendiri (filled solid, outline, duotone),
 *   tapi keluarga visualnya satu: geometris, tegas, bersih.
 */
enum class KilatIcon {
    BOLT, HOME, FOLDER, LAYERS, USER, PLUS,
    TEXT, SHAPE, IMAGE, SLIDERS, EXPORT, PLAY, PAUSE, LOOP, RESET,
    MUSIC, SETTINGS, SAVE, TRASH, COPY, CLOSE, BACK, NEXT,
    CHEVRON_DOWN, CHEVRON_RIGHT, MORE, KEYFRAME, EYE, EYE_OFF,
    CHECK, SPARK, FILM, CLOCK, RATIO, BOOK, INFO, GRID, STAR, SHARE, WAND,
    UNDO, REDO, SCISSORS, LAYER_UP, LAYER_DOWN, MAGNET, SKIP_START, CROSSHAIR, MINUS, PENCIL, VECTOR, CUBE, CURVE, LOCK, UNLOCK
}

object IconPainter {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fill2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val stroke2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rf = RectF()

    private const val SW = 2.0f   // ketebalan stroke utama (unit grid)
    private const val SW_FINE = 1.6f

    fun draw(canvas: Canvas, icon: KilatIcon, x: Float, y: Float, size: Float, c1: Int, c2: Int? = null) {
        val s = size / 24f
        val second = c2 ?: duotoneOf(c1)
        fill.color = c1
        fill2.color = second
        stroke.color = c1
        stroke.strokeWidth = SW * s
        stroke2.color = second
        stroke2.strokeWidth = SW * s
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(s, s)
        when (icon) {
            KilatIcon.BOLT -> {
                path.reset()
                path.moveTo(13f, 2f); path.lineTo(5f, 13.5f); path.lineTo(10f, 13.5f)
                path.lineTo(9f, 22f); path.lineTo(19f, 10f); path.lineTo(13.5f, 10f)
                path.close()
                canvas.save()
                canvas.translate(1.7f, 0.9f)
                canvas.drawPath(path, fill2)
                canvas.restore()
                canvas.drawPath(path, fill)
            }
            KilatIcon.HOME -> {
                canvas.drawLine(16.6f, 7.4f, 16.6f, 4.6f, stroke)
                path.reset()
                path.moveTo(4f, 11f); path.lineTo(12f, 4f); path.lineTo(20f, 11f)
                canvas.drawPath(path, stroke)
                path.reset()
                path.moveTo(6.5f, 9.5f); path.lineTo(6.5f, 20f); path.lineTo(17.5f, 20f); path.lineTo(17.5f, 9.5f)
                canvas.drawPath(path, stroke)
                rf.set(10.25f, 14f, 13.75f, 20f)
                canvas.drawRoundRect(rf, 1f, 1f, fill)
            }
            KilatIcon.FOLDER -> {
                rf.set(3f, 5f, 11f, 9.5f)
                canvas.drawRoundRect(rf, 2f, 2f, fill)
                rf.set(3f, 8f, 21f, 19.5f)
                canvas.drawRoundRect(rf, 2.5f, 2.5f, fill2)
                canvas.drawRoundRect(rf, 2.5f, 2.5f, stroke)
            }
            KilatIcon.LAYERS -> {
                path.reset()
                path.moveTo(12f, 4.2f); path.lineTo(20f, 7.9f); path.lineTo(12f, 11.6f); path.lineTo(4f, 7.9f)
                path.close()
                canvas.drawPath(path, fill)
                path.reset()
                path.moveTo(4.6f, 12.4f); path.lineTo(12f, 15.8f); path.lineTo(19.4f, 12.4f)
                canvas.drawPath(path, stroke)
                path.reset()
                path.moveTo(4.6f, 16.8f); path.lineTo(12f, 20.2f); path.lineTo(19.4f, 16.8f)
                canvas.drawPath(path, stroke2)
            }
            KilatIcon.USER -> {
                canvas.drawCircle(12f, 8f, 3.5f, stroke)
                path.reset()
                path.moveTo(5f, 20.5f)
                path.cubicTo(5.8f, 15.8f, 8.6f, 14.2f, 12f, 14.2f)
                path.cubicTo(15.4f, 14.2f, 18.2f, 15.8f, 19f, 20.5f)
                path.close()
                canvas.drawPath(path, fill)
            }
            KilatIcon.PLUS -> {
                rf.set(10.5f, 4f, 13.5f, 20f); canvas.drawRoundRect(rf, 1.5f, 1.5f, fill)
                rf.set(4f, 10.5f, 20f, 13.5f); canvas.drawRoundRect(rf, 1.5f, 1.5f, fill)
            }
            KilatIcon.TEXT -> {
                stroke.strokeWidth = 2.5f * s
                canvas.drawLine(5f, 6f, 19f, 6f, stroke)
                canvas.drawLine(12f, 6f, 12f, 17f, stroke)
                stroke.strokeWidth = SW * s
                canvas.drawLine(5f, 4.8f, 5f, 7.2f, stroke)
                canvas.drawLine(19f, 4.8f, 19f, 7.2f, stroke)
                canvas.drawLine(9.6f, 17f, 14.4f, 17f, stroke)
                rf.set(6.5f, 19.2f, 17.5f, 21f)
                canvas.drawRoundRect(rf, 0.9f, 0.9f, fill2)
            }
            KilatIcon.SHAPE -> {
                rf.set(3.5f, 3.5f, 13f, 13f)
                canvas.drawRoundRect(rf, 1.8f, 1.8f, stroke)
                canvas.drawCircle(15.2f, 15.2f, 5f, fill2)
                canvas.drawCircle(15.2f, 15.2f, 5f, stroke)
            }
            KilatIcon.IMAGE -> {
                rf.set(3f, 4.5f, 21f, 19.5f)
                canvas.drawRoundRect(rf, 2.5f, 2.5f, stroke)
                canvas.drawCircle(8.5f, 9.5f, 1.8f, fill)
                path.reset()
                path.moveTo(5f, 18f); path.lineTo(10f, 12.5f); path.lineTo(13.2f, 15.4f)
                path.lineTo(16f, 13f); path.lineTo(19f, 18f)
                path.close()
                canvas.drawPath(path, fill2)
            }
            KilatIcon.SLIDERS -> {
                canvas.drawLine(4f, 6.5f, 20f, 6.5f, stroke)
                canvas.drawLine(4f, 12f, 20f, 12f, stroke)
                canvas.drawLine(4f, 17.5f, 20f, 17.5f, stroke)
                canvas.drawCircle(9f, 6.5f, 2.2f, fill)
                canvas.drawCircle(15f, 12f, 2.2f, fill)
                canvas.drawCircle(7f, 17.5f, 2.2f, fill)
                canvas.drawCircle(9f, 6.5f, 0.9f, fill2)
                canvas.drawCircle(15f, 12f, 0.9f, fill2)
                canvas.drawCircle(7f, 17.5f, 0.9f, fill2)
            }
            KilatIcon.EXPORT -> {
                path.reset()
                path.moveTo(5f, 13.5f); path.lineTo(5f, 18.5f)
                path.cubicTo(5f, 19.8f, 5.8f, 20.5f, 7f, 20.5f); path.lineTo(17f, 20.5f)
                path.cubicTo(18.2f, 20.5f, 19f, 19.8f, 19f, 18.5f); path.lineTo(19f, 13.5f)
                canvas.drawPath(path, stroke)
                canvas.drawLine(12f, 16f, 12f, 5f, stroke)
                path.reset()
                path.moveTo(7.5f, 9f); path.lineTo(12f, 4.5f); path.lineTo(16.5f, 9f)
                canvas.drawPath(path, stroke)
            }
            KilatIcon.PLAY -> {
                path.reset()
                path.moveTo(8.5f, 5f); path.lineTo(19f, 12f); path.lineTo(8.5f, 19f)
                path.close()
                canvas.drawPath(path, fill)
            }
            KilatIcon.PAUSE -> {
                rf.set(7f, 5f, 10.5f, 19f); canvas.drawRoundRect(rf, 1.5f, 1.5f, fill)
                rf.set(13.5f, 5f, 17f, 19f); canvas.drawRoundRect(rf, 1.5f, 1.5f, fill)
            }
            KilatIcon.LOOP -> loopShape(canvas)
            KilatIcon.RESET -> {
                canvas.save()
                canvas.scale(-1f, 1f)
                canvas.translate(-24f, 0f)
                loopShape(canvas)
                canvas.restore()
            }
            KilatIcon.UNDO -> undoShape(canvas)
            KilatIcon.REDO -> {
                canvas.save()
                canvas.scale(-1f, 1f)
                canvas.translate(-24f, 0f)
                undoShape(canvas)
                canvas.restore()
            }
            KilatIcon.MUSIC -> {
                canvas.drawLine(9.5f, 16.5f, 9.5f, 6.5f, stroke)
                canvas.drawLine(18f, 15f, 18f, 5f, stroke)
                path.reset()
                path.moveTo(9.5f, 6.5f); path.lineTo(18f, 5f); path.lineTo(18f, 8.3f); path.lineTo(9.5f, 9.8f)
                path.close()
                canvas.drawPath(path, fill)
                rf.set(4.1f, 15f, 9.8f, 19f); canvas.drawOval(rf, fill)
                rf.set(12.6f, 13.5f, 18.3f, 17.5f); canvas.drawOval(rf, fill)
            }
            KilatIcon.SETTINGS -> {
                // gir 6 gigi gemuk (siluet filled) - terbaca gir, bukan matahari
                path.reset()
                val teeth = 6
                for (i in 0 until teeth) {
                    val a0 = Math.toRadians((i * 360.0 / teeth) - 90.0)
                    val step = Math.toRadians(360.0 / teeth)
                    val pts = floatArrayOf(0f, 0.16f, 0.30f, 0.54f, 0.68f, 0.86f)
                    val rad = floatArrayOf(7f, 7f, 9.4f, 9.4f, 7f, 7f)
                    for (k in pts.indices) {
                        val ang = (a0 + step * pts[k]).toFloat()
                        val px = 12f + rad[k] * cos(ang)
                        val py = 12f + rad[k] * sin(ang)
                        if (i == 0 && k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                }
                path.close()
                canvas.drawPath(path, fill)
                canvas.drawCircle(12f, 12f, 3.2f, fill2)
                canvas.drawCircle(12f, 12f, 1.4f, fill)
            }
            KilatIcon.SAVE -> {
                rf.set(4f, 4f, 20f, 20f)
                canvas.drawRoundRect(rf, 2.5f, 2.5f, stroke)
                rf.set(7.5f, 6f, 16.5f, 10f)
                canvas.drawRoundRect(rf, 1f, 1f, fill2)
                rf.set(13.6f, 7f, 15.5f, 9.4f)
                canvas.drawRect(rf, fill)
                rf.set(7.5f, 13.5f, 16.5f, 19f)
                canvas.drawRoundRect(rf, 1.2f, 1.2f, fill)
                stroke2.strokeWidth = SW_FINE * s
                canvas.drawLine(9.2f, 15.3f, 14.8f, 15.3f, stroke2)
                canvas.drawLine(9.2f, 17.3f, 13.2f, 17.3f, stroke2)
            }
            KilatIcon.TRASH -> {
                canvas.drawLine(4.5f, 6.5f, 19.5f, 6.5f, stroke)
                path.reset()
                path.moveTo(9.8f, 6.5f); path.lineTo(10.4f, 4.4f); path.lineTo(13.6f, 4.4f); path.lineTo(14.2f, 6.5f)
                canvas.drawPath(path, stroke)
                path.reset()
                path.moveTo(6f, 6.5f); path.lineTo(7.1f, 19.3f)
                path.cubicTo(7.25f, 20.3f, 7.9f, 20.7f, 8.9f, 20.7f); path.lineTo(15.1f, 20.7f)
                path.cubicTo(16.1f, 20.7f, 16.75f, 20.3f, 16.9f, 19.3f); path.lineTo(18f, 6.5f)
                canvas.drawPath(path, stroke)
                stroke.strokeWidth = SW_FINE * s
                stroke.strokeWidth = SW_FINE * s
                canvas.drawLine(9.8f, 11f, 10.1f, 16.4f, stroke)
                canvas.drawLine(14.2f, 11f, 13.9f, 16.4f, stroke)
            }
            KilatIcon.COPY -> {
                rf.set(8.5f, 4f, 20f, 15.5f)
                canvas.drawRoundRect(rf, 2.2f, 2.2f, stroke2)
                rf.set(4f, 8.5f, 15.5f, 20f)
                canvas.drawRoundRect(rf, 2.2f, 2.2f, fill2)
                canvas.drawRoundRect(rf, 2.2f, 2.2f, stroke)
                stroke.strokeWidth = SW_FINE * s
                canvas.drawLine(7f, 12.4f, 12.5f, 12.4f, stroke)
                canvas.drawLine(7f, 15.8f, 11f, 15.8f, stroke)
            }
            KilatIcon.CLOSE -> {
                stroke.strokeWidth = 2.2f * s
                canvas.drawLine(6f, 6f, 18f, 18f, stroke)
                canvas.drawLine(18f, 6f, 6f, 18f, stroke)
            }
            KilatIcon.BACK -> {
                stroke.strokeWidth = 2.2f * s
                path.reset()
                path.moveTo(14.5f, 4.5f); path.lineTo(8f, 12f); path.lineTo(14.5f, 19.5f)
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
                path.reset()
                path.moveTo(12f, 10.2f); path.lineTo(13.8f, 12f); path.lineTo(12f, 13.8f); path.lineTo(10.2f, 12f)
                path.close()
                canvas.drawPath(path, fill2)
            }
            KilatIcon.EYE, KilatIcon.EYE_OFF -> {
                path.reset()
                path.moveTo(2.8f, 12f)
                path.cubicTo(6f, 6.8f, 9f, 5f, 12f, 5f)
                path.cubicTo(15f, 5f, 18f, 6.8f, 21.2f, 12f)
                path.cubicTo(18f, 17.2f, 15f, 19f, 12f, 19f)
                path.cubicTo(9f, 19f, 6f, 17.2f, 2.8f, 12f)
                canvas.drawPath(path, stroke)
                canvas.drawCircle(12f, 12f, 2.6f, fill)
                if (icon == KilatIcon.EYE_OFF) canvas.drawLine(4.5f, 4.5f, 19.5f, 19.5f, stroke)
            }
            KilatIcon.CHECK -> {
                stroke.strokeWidth = 2.2f * s
                path.reset()
                path.moveTo(4.5f, 12.8f); path.lineTo(9.8f, 17.8f); path.lineTo(19.5f, 6.5f)
                canvas.drawPath(path, stroke)
            }
            KilatIcon.SPARK -> {
                path.reset()
                path.moveTo(10.5f, 3f)
                path.quadTo(11.8f, 9.7f, 18.5f, 11f)
                path.quadTo(11.8f, 12.3f, 10.5f, 19f)
                path.quadTo(9.2f, 12.3f, 2.5f, 11f)
                path.quadTo(9.2f, 9.7f, 10.5f, 3f)
                path.close()
                canvas.drawPath(path, fill)
            }
            KilatIcon.FILM -> {
                rf.set(4f, 4.5f, 20f, 19.5f)
                canvas.drawRoundRect(rf, 2.2f, 2.2f, stroke)
                for (py in floatArrayOf(7.4f, 11.05f, 14.7f)) {
                    rf.set(5.8f, py, 7.2f, py + 1.8f); canvas.drawRoundRect(rf, 0.6f, 0.6f, fill)
                    rf.set(16.8f, py, 18.2f, py + 1.8f); canvas.drawRoundRect(rf, 0.6f, 0.6f, fill)
                }
                path.reset()
                path.moveTo(10.6f, 9.8f); path.lineTo(14.6f, 12f); path.lineTo(10.6f, 14.2f)
                path.close()
                canvas.drawPath(path, fill)
            }
            KilatIcon.CLOCK -> {
                canvas.drawCircle(12f, 12f, 8.5f, stroke)
                stroke2.strokeWidth = SW_FINE * s
                canvas.drawLine(12f, 5.6f, 12f, 7.2f, stroke2)
                canvas.drawLine(18.4f, 12f, 16.8f, 12f, stroke2)
                canvas.drawLine(12f, 18.4f, 12f, 16.8f, stroke2)
                canvas.drawLine(5.6f, 12f, 7.2f, 12f, stroke2)
                canvas.drawLine(12f, 12f, 12f, 7.6f, stroke)
                canvas.drawLine(12f, 12f, 15.2f, 14.2f, stroke)
                canvas.drawCircle(12f, 12f, 1.3f, fill)
            }
            KilatIcon.RATIO -> {
                rf.set(9.8f, 9.8f, 14.2f, 14.2f)
                canvas.drawRoundRect(rf, 1.1f, 1.1f, fill)
                canvas.drawLine(3.5f, 8f, 3.5f, 3.5f, stroke); canvas.drawLine(3.5f, 3.5f, 8f, 3.5f, stroke)
                canvas.drawLine(16f, 3.5f, 20.5f, 3.5f, stroke); canvas.drawLine(20.5f, 3.5f, 20.5f, 8f, stroke)
                canvas.drawLine(20.5f, 16f, 20.5f, 20.5f, stroke); canvas.drawLine(20.5f, 20.5f, 16f, 20.5f, stroke)
                canvas.drawLine(8f, 20.5f, 3.5f, 20.5f, stroke); canvas.drawLine(3.5f, 20.5f, 3.5f, 16f, stroke)
            }
            KilatIcon.BOOK -> {
                path.reset()
                path.moveTo(12f, 6.5f)
                path.cubicTo(9.5f, 4.8f, 6.5f, 4.2f, 4f, 4.8f); path.lineTo(4f, 19.2f)
                path.cubicTo(6.5f, 18.6f, 9.5f, 19.2f, 12f, 20.5f)
                path.cubicTo(14.5f, 19.2f, 17.5f, 18.6f, 20f, 19.2f); path.lineTo(20f, 4.8f)
                path.cubicTo(17.5f, 4.2f, 14.5f, 4.8f, 12f, 6.5f)
                path.close()
                canvas.drawPath(path, stroke)
                canvas.drawLine(12f, 6.5f, 12f, 20.5f, stroke)
            }
            KilatIcon.INFO -> {
                canvas.drawCircle(12f, 12f, 8.4f, stroke)
                canvas.drawCircle(12f, 8f, 1.5f, fill)
                canvas.drawLine(12f, 11.3f, 12f, 16.2f, stroke)
            }
            KilatIcon.GRID -> {
                rf.set(4f, 4f, 10.6f, 10.6f); canvas.drawRoundRect(rf, 1.8f, 1.8f, fill)
                rf.set(13.4f, 4f, 20f, 10.6f); canvas.drawRoundRect(rf, 1.8f, 1.8f, stroke)
                rf.set(4f, 13.4f, 10.6f, 20f); canvas.drawRoundRect(rf, 1.8f, 1.8f, stroke)
                rf.set(13.4f, 13.4f, 20f, 20f); canvas.drawRoundRect(rf, 1.8f, 1.8f, stroke)
            }
            KilatIcon.STAR -> {
                path.reset()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 8.8f else 3.9f
                    val a = Math.toRadians((i * 36 - 90).toDouble())
                    val px = 12f + r * cos(a).toFloat()
                    val py = 12.2f + r * sin(a).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, fill)
            }
            KilatIcon.SHARE -> {
                stroke.strokeWidth = SW_FINE * s
                canvas.drawLine(8.1f, 10.9f, 15.4f, 6.7f, stroke)
                canvas.drawLine(8.1f, 13.1f, 15.4f, 17.3f, stroke)
                canvas.drawCircle(6f, 12f, 2.5f, fill)
                canvas.drawCircle(17.5f, 5.5f, 2.5f, fill)
                canvas.drawCircle(17.5f, 18.5f, 2.5f, fill)
                canvas.drawCircle(6f, 12f, 1f, fill2)
                canvas.drawCircle(17.5f, 5.5f, 1f, fill2)
                canvas.drawCircle(17.5f, 18.5f, 1f, fill2)
            }
            KilatIcon.WAND -> {
                stroke.strokeWidth = 2.3f * s
                canvas.drawLine(5f, 19f, 15.5f, 8.5f, stroke)
                path.reset()
                path.moveTo(17.5f, 3.6f); path.lineTo(19.9f, 6f); path.lineTo(17.5f, 8.4f); path.lineTo(15.1f, 6f)
                path.close()
                canvas.drawPath(path, fill)
                canvas.drawCircle(13.5f, 3.8f, 1.1f, fill)
                canvas.drawCircle(20f, 10.5f, 1.1f, fill2)
            }
            KilatIcon.SCISSORS -> {
                path.reset()
                path.moveTo(8.6f, 8f); path.lineTo(20.3f, 18.4f); path.lineTo(18.9f, 19.3f); path.lineTo(7.9f, 9.7f)
                path.close()
                canvas.drawPath(path, fill)
                path.reset()
                path.moveTo(8.6f, 16f); path.lineTo(20.3f, 5.6f); path.lineTo(18.9f, 4.7f); path.lineTo(7.9f, 14.3f)
                path.close()
                canvas.drawPath(path, fill2)
                canvas.drawCircle(6.4f, 6.4f, 2.5f, stroke)
                canvas.drawCircle(6.4f, 17.6f, 2.5f, stroke)
                canvas.drawCircle(12.6f, 12f, 1.2f, fill)
            }
            KilatIcon.LAYER_UP -> {
                stroke.strokeWidth = 2.3f * s
                path.reset()
                path.moveTo(6.5f, 9.5f); path.lineTo(12f, 4f); path.lineTo(17.5f, 9.5f)
                canvas.drawPath(path, stroke)
                stroke2.strokeWidth = 2.3f * s
                path.reset()
                path.moveTo(6.5f, 15f); path.lineTo(12f, 9.5f); path.lineTo(17.5f, 15f)
                canvas.drawPath(path, stroke2)
                canvas.drawLine(5f, 20f, 19f, 20f, stroke)
            }
            KilatIcon.LAYER_DOWN -> {
                canvas.drawLine(5f, 4f, 19f, 4f, stroke)
                stroke.strokeWidth = 2.3f * s
                path.reset()
                path.moveTo(6.5f, 14.5f); path.lineTo(12f, 20f); path.lineTo(17.5f, 14.5f)
                canvas.drawPath(path, stroke)
                stroke2.strokeWidth = 2.3f * s
                path.reset()
                path.moveTo(6.5f, 9f); path.lineTo(12f, 14.5f); path.lineTo(17.5f, 9f)
                canvas.drawPath(path, stroke2)
                canvas.drawLine(5f, 4f, 19f, 4f, stroke)
            }
            KilatIcon.MAGNET -> {
                stroke.strokeWidth = 3.4f * s
                path.reset()
                path.moveTo(8f, 4f); path.lineTo(8f, 13f)
                path.cubicTo(8f, 16.2f, 9.8f, 17.8f, 12f, 17.8f)
                path.cubicTo(14.2f, 17.8f, 16f, 16.2f, 16f, 13f); path.lineTo(16f, 4f)
                canvas.drawPath(path, stroke)
                rf.set(5.8f, 3.2f, 10.2f, 6.4f); canvas.drawRoundRect(rf, 1f, 1f, fill2)
                rf.set(13.8f, 3.2f, 18.2f, 6.4f); canvas.drawRoundRect(rf, 1f, 1f, fill2)
            }
            KilatIcon.SKIP_START -> {
                rf.set(5.5f, 5.5f, 7.7f, 18.5f)
                canvas.drawRoundRect(rf, 1f, 1f, fill)
                path.reset()
                path.moveTo(18.5f, 5.5f); path.lineTo(18.5f, 18.5f); path.lineTo(9.2f, 12f)
                path.close()
                canvas.drawPath(path, fill)
            }
            KilatIcon.CROSSHAIR -> {
                canvas.drawCircle(12f, 12f, 6.3f, stroke)
                canvas.drawLine(12f, 3f, 12f, 6.4f, stroke)
                canvas.drawLine(12f, 17.6f, 12f, 21f, stroke)
                canvas.drawLine(3f, 12f, 6.4f, 12f, stroke)
                canvas.drawLine(17.6f, 12f, 21f, 12f, stroke)
                canvas.drawCircle(12f, 12f, 1.6f, fill)
            }
            KilatIcon.MINUS -> {
                rf.set(4f, 10.5f, 20f, 13.5f)
                canvas.drawRoundRect(rf, 1.5f, 1.5f, fill)
            }
            KilatIcon.PENCIL -> {
                path.reset()
                path.moveTo(15.8f, 3.6f); path.lineTo(20.4f, 8.2f); path.lineTo(9.2f, 19.4f); path.lineTo(4.5f, 20.5f); path.lineTo(3.6f, 15.8f)
                path.close()
                canvas.drawPath(path, stroke)
                canvas.drawLine(13.8f, 5.6f, 18.4f, 10.2f, stroke2)
                canvas.drawLine(7.4f, 15.2f, 9.2f, 19.4f, stroke2)
            }
            KilatIcon.VECTOR -> {
                path.reset()
                path.moveTo(5f, 19f); path.cubicTo(5f, 8f, 19f, 16f, 19f, 5f)
                canvas.drawPath(path, stroke)
                rf.set(3f, 17f, 7f, 21f); canvas.drawRoundRect(rf, 1f, 1f, fill)
                rf.set(17f, 3f, 21f, 7f); canvas.drawRoundRect(rf, 1f, 1f, fill)
                canvas.drawCircle(12f, 12f, 2f, fill2)
            }
            KilatIcon.CUBE -> {
                path.reset()
                path.moveTo(12f, 3f); path.lineTo(20f, 7.5f); path.lineTo(20f, 16.5f); path.lineTo(12f, 21f); path.lineTo(4f, 16.5f); path.lineTo(4f, 7.5f)
                path.close()
                canvas.drawPath(path, stroke)
                canvas.drawLine(4.2f, 7.7f, 12f, 12f, stroke2)
                canvas.drawLine(19.8f, 7.7f, 12f, 12f, stroke2)
                canvas.drawLine(12f, 12f, 12f, 20.8f, stroke2)
            }
            KilatIcon.CURVE -> {
                canvas.drawLine(4f, 4f, 4f, 20f, stroke2)
                canvas.drawLine(4f, 20f, 20f, 20f, stroke2)
                path.reset()
                path.moveTo(4f, 20f); path.cubicTo(9f, 20f, 9f, 6f, 20f, 6f)
                canvas.drawPath(path, stroke)
                canvas.drawCircle(9f, 13f, 1.9f, fill)
            }
            KilatIcon.LOCK -> {
                rf.set(8f, 3.5f, 16f, 11.5f)
                canvas.drawArc(rf, 180f, 180f, false, stroke)
                canvas.drawLine(8f, 7.5f, 8f, 11f, stroke)
                canvas.drawLine(16f, 7.5f, 16f, 11f, stroke)
                rf.set(5.5f, 10.5f, 18.5f, 20.5f)
                canvas.drawRoundRect(rf, 2.2f, 2.2f, fill)
                canvas.drawCircle(12f, 14.6f, 1.5f, fill2)
                rf.set(11.3f, 14.6f, 12.7f, 17.4f)
                canvas.drawRoundRect(rf, 0.7f, 0.7f, fill2)
            }
            KilatIcon.UNLOCK -> {
                rf.set(8f, 3.5f, 16f, 11.5f)
                canvas.drawArc(rf, 180f, 150f, false, stroke)
                canvas.drawLine(8f, 7.5f, 8f, 11f, stroke)
                rf.set(5.5f, 10.5f, 18.5f, 20.5f)
                canvas.drawRoundRect(rf, 2.2f, 2.2f, fill2)
                canvas.drawRoundRect(rf, 2.2f, 2.2f, stroke)
                canvas.drawCircle(12f, 15.5f, 1.5f, fill)
            }
        }
        canvas.restore()
    }

    private fun loopShape(canvas: Canvas) {
        rf.set(4.5f, 4.5f, 19.5f, 19.5f)
        canvas.drawArc(rf, -38f, 288f, false, stroke)
        path.reset()
        path.moveTo(19.6f, 3.2f); path.lineTo(20.1f, 8f); path.lineTo(15.7f, 6.7f)
        path.close()
        canvas.drawPath(path, fill)
    }

    private fun undoShape(canvas: Canvas) {
        path.reset()
        path.moveTo(9f, 5.8f); path.lineTo(5f, 9.8f); path.lineTo(9f, 13.8f)
        canvas.drawPath(path, stroke)
        path.reset()
        path.moveTo(5.2f, 9.8f); path.lineTo(13.5f, 9.8f)
        path.cubicTo(16.6f, 9.8f, 18.7f, 11.7f, 18.7f, 14.4f)
        path.cubicTo(18.7f, 17.1f, 16.6f, 19f, 13.5f, 19f); path.lineTo(8f, 19f)
        canvas.drawPath(path, stroke)
    }

    /** Warna pendamping duotone yang SOLID (opaque): warna utama dicampur nada permukaan gelap. */
    private fun duotoneOf(color: Int): Int {
        val tone = 0xFF2B303E.toInt()
        fun mix(a: Int, b: Int) = (a * 0.45f + b * 0.55f).toInt().coerceIn(0, 255)
        return Color.rgb(
            mix(Color.red(color), Color.red(tone)),
            mix(Color.green(color), Color.green(tone)),
            mix(Color.blue(color), Color.blue(tone))
        )
    }
}

/** View ikon custom. Ikon digambar presisi di tengah area view. */
class IconView(
    context: Context,
    icon: KilatIcon = KilatIcon.BOLT,
    tint: Int = KColors.Text,
    tint2: Int? = null
) : View(context) {

    var icon: KilatIcon = icon
        set(v) { field = v; contentDescription = v.name; invalidate() }
    var tintColor: Int = tint
        set(v) { field = v; invalidate() }
    var tintColor2: Int? = tint2
        set(v) { field = v; invalidate() }
    /** Napas ikon dari tepi view (fraksi ukuran, 0..0.3). Konsisten di semua ikon. */
    var iconInset: Float = 0.08f
        set(v) { field = v; invalidate() }

    init {
        contentDescription = icon.name
    }

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
