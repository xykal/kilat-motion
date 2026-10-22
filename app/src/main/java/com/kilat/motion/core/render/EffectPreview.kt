package com.kilat.motion.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import com.kilat.motion.core.model.LayerEffect
import com.kilat.motion.core.preview.PreviewSamples
import java.util.Random

/**
 * Thumbnail blur dan efek untuk UI editor. Setiap thumbnail dirender sungguhan dari satu gambar contoh
 * lewat kode yang sama dengan renderer (BlurOps dan EffectDraw), jadi preview = hasil.
 *
 * Hanya dipanggil dari main thread. BlurOps memakai objek bersama, dan hasil di-cache.
 */
object EffectPreview {
    const val W = 150
    const val H = 100

    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private var sampleBitmap: Bitmap? = null
    private var grainTile: Bitmap? = null

    /** Gambar contoh deterministik: gradient, lingkaran, kotak, dan garis. */
    private fun sample(): Bitmap {
        sampleBitmap?.let { return it }
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(
            0f, 0f, 0f, H.toFloat(),
            intArrayOf(0xFF2EE6C0.toInt(), 0xFF5B3CC4.toInt(), 0xFFFF4D8D.toInt()),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null
        p.color = 0xFFFFD166.toInt()
        c.drawCircle(W * 0.28f, H * 0.42f, H * 0.24f, p)
        p.color = 0xFF0A0B0F.toInt()
        c.drawRoundRect(RectF(W * 0.55f, H * 0.2f, W * 0.9f, H * 0.66f), H * 0.08f, H * 0.08f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = H * 0.05f
        c.drawLine(W * 0.1f, H * 0.86f, W * 0.9f, H * 0.86f, p)
        sampleBitmap = b
        return b
    }

    private fun grain(): Bitmap {
        grainTile?.let { return it }
        val n = 96
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val rnd = Random(7)
        for (y in 0 until n) for (x in 0 until n) {
            val v = rnd.nextInt(256)
            b.setPixel(x, y, (0xFF shl 24) or (v shl 16) or (v shl 8) or v)
        }
        grainTile = b
        return b
    }

    /** Thumbnail blur. type null = tanpa blur (gambar contoh apa adanya). */
    fun blur(type: String?): Bitmap {
        val key = "blur:" + (type ?: "none")
        cache.get(key)?.let { return it }
        val src = sample()
        val out = if (type == null) src.copy(Bitmap.Config.ARGB_8888, false)
        else BlurOps.apply(src, type, 12f)
        cache.put(key, out)
        return out
    }

    /** Thumbnail satu efek dengan intensitas sedang. */
    fun effect(type: String): Bitmap {
        val key = "fx:$type"
        cache.get(key)?.let { return it }
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val e = LayerEffect(
            type = type, amount = 0.7f, size = 0.5f,
            color = PreviewSamples.defaultEffectColor(type)
        )
        val src = sample()
        val content = RectF(-W / 2f, -H / 2f, W / 2f, H / 2f)
        c.translate(W / 2f, H / 2f)

        val cp = Paint(Paint.ANTI_ALIAS_FLAG)
        EffectDraw.colorValues(e, 0.3f)?.let { cp.colorFilter = ColorMatrixColorFilter(ColorMatrix(it)) }

        when (type) {
            "glitch" -> {
                val bands = 5
                val h = H / bands.toFloat()
                for (i in 0 until bands) {
                    c.save()
                    c.clipRect(content.left, content.top + i * h, content.right, content.top + (i + 1) * h + 0.5f)
                    c.translate(if (i % 2 == 0) 10f else -8f, 0f)
                    c.drawBitmap(src, null, content, cp)
                    c.restore()
                }
            }
            "rgbsplit" -> {
                val d = 6f
                val channels = listOf(
                    floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f) to -d,
                    floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f) to 0f,
                    floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f) to d
                )
                for ((m, dx) in channels) {
                    val pc = Paint(Paint.ANTI_ALIAS_FLAG)
                    pc.colorFilter = ColorMatrixColorFilter(ColorMatrix(m))
                    pc.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                    c.save()
                    c.translate(dx, 0f)
                    c.drawBitmap(src, null, content, pc)
                    c.restore()
                }
            }
            "tint" -> {
                c.drawBitmap(src, null, content, cp)
                val tp = Paint()
                tp.color = EffectDraw.withA(e.color, 128)
                tp.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                c.drawRect(content, tp)
            }
            "shake" -> {
                c.translate(6f, -4f)
                c.drawBitmap(src, null, content, cp)
            }
            else -> c.drawBitmap(src, null, content, cp)
        }

        if (type in EffectDraw.OVERLAY_TYPES) {
            EffectDraw.drawOverlay(c, Paint(), e, content, W.toFloat(), H.toFloat(), 0.3f) { grain() }
        }
        cache.put(key, out)
        return out
    }
}
