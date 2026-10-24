package com.fluxa.motion.ui.components

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.fluxa.motion.core.animation.Anim
import com.fluxa.motion.core.render.BlendFx
import com.fluxa.motion.core.render.ShapeGeom
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Gambar pratinjau untuk pilihan visual di Inspector. Tidak memakai teks, kecuali
 * spesimen huruf "Aa" pada pilihan font. Semua fungsi menggambar di kotak [r].
 */
object PickerArt {
    private const val INK = 0xFFF4F6FA.toInt()
    private const val DIM = 0xFF5A6374.toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val faces = HashMap<String, Typeface>()

    private fun stroke(r: RectF) = (r.height() * 0.06f).coerceAtLeast(1.5f)

    /** Kurva easing: sumbu tipis sebagai acuan, kurva putih di atasnya. */
    fun easing(c: Canvas, r: RectF, name: String) {
        val x0 = r.left + r.width() * 0.08f
        val x1 = r.right - r.width() * 0.08f
        val y0 = r.bottom - r.height() * 0.12f
        val y1 = r.top + r.height() * 0.12f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke(r) * 0.5f
        paint.color = DIM
        c.drawLine(x0, y0, x1, y0, paint)
        val p = Path()
        val n = 36
        for (i in 0..n) {
            val t = i / n.toFloat()
            val v = Anim.ease(name, t)
            val px = x0 + (x1 - x0) * t
            val py = y0 + (y1 - y0) * v
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        paint.color = INK
        paint.strokeWidth = stroke(r)
        paint.strokeCap = Paint.Cap.ROUND
        c.drawPath(p, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    /** Blend: dua lingkaran berwarna di atas gradasi, mode blend diterapkan ke lingkaran kanan. */
    fun blend(c: Canvas, r: RectF, mode: String) {
        val layer = c.saveLayer(r, null)
        paint.style = Paint.Style.FILL
        paint.xfermode = null
        paint.shader = LinearGradient(
            r.left, 0f, r.right, 0f,
            intArrayOf(0xFF2B313D.toInt(), 0xFFA9B3C2.toInt()), null, Shader.TileMode.CLAMP
        )
        c.drawRect(r, paint)
        paint.shader = null
        val rad = min(r.width(), r.height()) * 0.28f
        paint.color = 0xFFFF5C7A.toInt()
        c.drawCircle(r.centerX() - rad * 0.55f, r.centerY(), rad, paint)
        paint.color = 0xFF59E3FF.toInt()
        paint.xfermode = BlendFx.xfer(mode)
        c.drawCircle(r.centerX() + rad * 0.55f, r.centerY(), rad, paint)
        paint.xfermode = null
        c.restoreToCount(layer)
    }

    /** Bentuk: geometri yang sama dengan layer bentuk di Renderer. */
    fun shape(c: Canvas, r: RectF, kind: String) {
        val s = min(r.width(), r.height()) * 0.82f
        ShapeGeom.build(kind, s, s, 8, s * 0.14f, path)
        fillPath(c, r)
    }

    /** Mask: "none" digambar sebagai kotak coret, lainnya sebagai bentuk terisi. */
    fun mask(c: Canvas, r: RectF, kind: String) {
        val s = min(r.width(), r.height()) * 0.8f
        if (kind == "none") {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke(r)
            paint.color = INK
            val box = RectF(r.centerX() - s / 2, r.centerY() - s / 2, r.centerX() + s / 2, r.centerY() + s / 2)
            c.drawRect(box, paint)
            c.drawLine(box.left, box.bottom, box.right, box.top, paint)
            return
        }
        ShapeGeom.build(kind, s, s, 8, s * 0.3f, path)
        fillPath(c, r)
    }

    /** Font: spesimen "Aa" dengan typeface yang dipakai layer teks. */
    fun font(c: Canvas, r: RectF, key: String) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.xfermode = null
        paint.color = INK
        paint.typeface = face(key)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = r.height() * 0.62f
        val fm = paint.fontMetrics
        val base = r.centerY() - (fm.ascent + fm.descent) / 2f
        c.drawText("Aa", r.centerX(), base, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.DEFAULT
    }

    /** Tile isi shape: warna (lingkaran warna layer), gambar, atau video (ikon). */
    fun fillKind(c: Canvas, r: RectF, kind: String, color: Int) {
        val s = min(r.width(), r.height()) * 0.7f
        val x = r.centerX() - s / 2f
        val y = r.centerY() - s / 2f
        when (kind) {
            "image" -> IconPainter.draw(c, FluxaIcon.IMAGE, x, y, s, INK)
            "video" -> IconPainter.draw(c, FluxaIcon.FILM, x, y, s, INK)
            else -> {
                paint.style = Paint.Style.FILL
                paint.shader = null
                paint.xfermode = null
                paint.color = color
                c.drawCircle(r.centerX(), r.centerY(), s / 2f, paint)
            }
        }
    }

    /**
     * Tile keyframe otomatis. "t:" = pola waktu (grafik gerak), "b:" = pulsa ketukan
     * (ikon gaya + tanda musik).
     */
    fun autoKey(c: Canvas, r: RectF, key: String) {
        val name = key.substringAfter(":")
        if (key.startsWith("b:")) {
            val icon = when (name) {
                "ZOOM" -> FluxaIcon.SPARK
                "SWING" -> FluxaIcon.LOOP
                "DIP" -> FluxaIcon.EYE_OFF
                "BOUNCE" -> FluxaIcon.BOLT
                else -> FluxaIcon.TRANSFORM
            }
            val s = min(r.width(), r.height()) * 0.62f
            IconPainter.draw(c, icon, r.centerX() - s / 2f, r.centerY() - s / 2f, s, INK)
            val m = min(r.width(), r.height()) * 0.32f
            IconPainter.draw(c, FluxaIcon.MUSIC, r.right - m, r.top, m, INK)
            return
        }
        val x0 = r.left + r.width() * 0.14f
        val x1 = r.right - r.width() * 0.14f
        val yc = r.centerY()
        val amp = r.height() * 0.3f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = stroke(r)
        paint.shader = null
        paint.xfermode = null
        paint.color = INK
        when (name) {
            "ZOOM_IN", "ZOOM_OUT" -> {
                val n = 5
                for (i in 0 until n) {
                    val f = i / (n - 1f)
                    val h = amp * (0.25f + 0.75f * (if (name == "ZOOM_IN") f else 1f - f))
                    val x = x0 + (x1 - x0) * f
                    c.drawLine(x, yc - h, x, yc + h, paint)
                }
            }
            "SHAKE" -> {
                path.reset()
                val n = 8
                for (i in 0..n) {
                    val f = i / n.toFloat()
                    val x = x0 + (x1 - x0) * f
                    val y = yc + (if (i % 2 == 0) -amp * 0.6f else amp * 0.6f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                c.drawPath(path, paint)
            }
            "FLOAT" -> {
                path.reset()
                val n = 40
                for (i in 0..n) {
                    val f = i / n.toFloat()
                    val x = x0 + (x1 - x0) * f
                    val y = yc - amp * sin(f * 2f * PI.toFloat() * 1.5f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                c.drawPath(path, paint)
            }
            "SPIN" -> {
                val s = min(r.width(), r.height()) * 0.62f
                IconPainter.draw(c, FluxaIcon.LOOP, r.centerX() - s / 2f, r.centerY() - s / 2f, s, INK)
            }
            else -> {
                // kedip berulang: batang terang dan redup bergantian
                val n = 5
                for (i in 0 until n) {
                    val f = i / (n - 1f)
                    val x = x0 + (x1 - x0) * f
                    paint.color = if (i % 2 == 0) INK else DIM
                    c.drawLine(x, yc - amp * 0.8f, x, yc + amp * 0.8f, paint)
                }
            }
        }
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun fillPath(c: Canvas, r: RectF) {
        c.save()
        c.translate(r.centerX(), r.centerY())
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.xfermode = null
        paint.color = INK
        c.drawPath(path, paint)
        c.restore()
    }

    private fun face(key: String): Typeface = com.fluxa.motion.core.render.FontBank.bundled(key) ?: faces.getOrPut(key) {
        try {
            when (key) {
                "sans" -> Typeface.create("sans-serif", Typeface.NORMAL)
                "sans_black" -> Typeface.create("sans-serif-black", Typeface.BOLD)
                "serif" -> Typeface.create("serif", Typeface.NORMAL)
                "serif_bold" -> Typeface.create("serif", Typeface.BOLD)
                "mono" -> Typeface.create("monospace", Typeface.NORMAL)
                "condensed" -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
                "casual" -> Typeface.create("casual", Typeface.NORMAL)
                "cursive" -> Typeface.create("cursive", Typeface.NORMAL)
                else -> Typeface.create("sans-serif", Typeface.NORMAL)
            }
        } catch (e: Throwable) {
            Typeface.DEFAULT
        }
    }
}
