package com.fluxa.motion.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Operasi blur untuk layer. Sebagian besar jenis dibangun dari banyak salinan bitmap
 * yang digeser, diskala, atau diputar, lalu dijumlahkan (ADD) dengan bobot rata-rata.
 */
object BlurOps {

    private class Copy(val dx: Float, val dy: Float, val scale: Float, val rotDeg: Float, val weight: Float)

    private val ADD = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val SRC_ATOP = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    private val CLEAR = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    /** Mengembalikan bitmap hasil blur ukuran sama dengan src. Bisa mengembalikan src bila jenis tidak dikenal. */
    fun apply(src: Bitmap, type: String, radius: Float): Bitmap {
        val r = max(0.5f, radius)
        return when (type) {
            "pixelate" -> pixelate(src, max(2, (r / 3f).toInt()))
            "mosaic" -> mosaic(src, max(4, (r / 2f).toInt()))
            "censor" -> censor(src, max(6, (r / 2f).toInt()))
            "chromatic" -> chromatic(src, r * 0.3f)
            "bloom" -> bloom(src, r)
            else -> {
                val copies = copiesFor(type, r) ?: return src
                accumulate(src, copies)
            }
        }
    }

    private fun copiesFor(type: String, r: Float): List<Copy>? = when (type) {
        "gauss" -> grid(5, r)
        "box" -> grid(3, r)
        "motion_h" -> line(r, 1f, 0f, 11, false)
        "motion_v" -> line(r, 0f, 1f, 11, false)
        "motion_d1" -> line(r, 0.7071f, -0.7071f, 11, false)
        "motion_d2" -> line(r, 0.7071f, 0.7071f, 11, false)
        "streak" -> line(r * 1.6f, 1f, 0f, 16, true)
        "ghost" -> line(r, 0.7071f, -0.7071f, 9, true)
        "zoom_in" -> zoom(r, 1f)
        "zoom_out" -> zoom(r, -1f)
        "spin" -> spin(r)
        "bokeh" -> ring(r, 24)
        "double" -> listOf(Copy(-r * 0.5f, 0f, 1f, 0f, 0.5f), Copy(r * 0.5f, 0f, 1f, 0f, 0.5f))
        "jitter" -> jitter(r, 20, 1f)
        "frost" -> jitter(r, 36, 0.35f)
        else -> null
    }

    private fun grid(n: Int, r: Float): List<Copy> {
        val out = ArrayList<Copy>(n * n)
        for (iy in 0 until n) for (ix in 0 until n) {
            val fx = ix / (n - 1f) * 2f - 1f
            val fy = iy / (n - 1f) * 2f - 1f
            out.add(Copy(fx * r, fy * r, 1f, 0f, 1f / (n * n)))
        }
        return out
    }

    private fun line(r: Float, ax: Float, ay: Float, n: Int, decay: Boolean): List<Copy> {
        val w = FloatArray(n) { i -> if (decay) max(0.05f, 1f - i / n.toFloat()) else 1f }
        val sum = w.sum()
        return (0 until n).map { i ->
            val f = if (n == 1) 0f else i / (n - 1f) * 2f - 1f
            Copy(f * r * ax, f * r * ay, 1f, 0f, w[i] / sum)
        }
    }

    private fun zoom(r: Float, dir: Float): List<Copy> {
        val n = 12
        val k = min(0.8f, r / 250f)
        return (0 until n).map { i ->
            val f = i / (n - 1f)
            Copy(0f, 0f, 1f + dir * f * k, 0f, 1f / n)
        }
    }

    private fun spin(r: Float): List<Copy> {
        val n = 14
        val deg = min(60f, r / 40f)
        return (0 until n).map { i ->
            val f = i / (n - 1f) * 2f - 1f
            Copy(0f, 0f, 1f, f * deg, 1f / n)
        }
    }

    private fun ring(r: Float, n: Int): List<Copy> = (0 until n).map { i ->
        val a = (2.0 * PI * i / n).toFloat()
        Copy(cos(a) * r, sin(a) * r, 1f, 0f, 1f / n)
    }

    private fun jitter(r: Float, n: Int, amp: Float): List<Copy> {
        val rnd = Random(7)
        return (0 until n).map {
            Copy((rnd.nextFloat() * 2f - 1f) * r * amp, (rnd.nextFloat() * 2f - 1f) * r * amp, 1f, 0f, 1f / n)
        }
    }

    /** Menjumlahkan salinan bitmap dengan bobot. Alpha salinan diskala oleh bobot, warna dibiarkan. */
    private fun accumulate(src: Bitmap, copies: List<Copy>): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.eraseColor(0)
        val c = Canvas(out)
        val mat = Matrix()
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        for (cp in copies) {
            mat.reset()
            mat.setScale(cp.scale, cp.scale, w / 2f, h / 2f)
            if (cp.rotDeg != 0f) mat.postRotate(cp.rotDeg, w / 2f, h / 2f)
            mat.postTranslate(cp.dx, cp.dy)
            p.reset()
            p.isFilterBitmap = true
            p.xfermode = ADD
            p.colorFilter = weightFilter(cp.weight)
            c.drawBitmap(src, mat, p)
        }
        return out
    }

    private fun weightFilter(w: Float): ColorMatrixColorFilter = ColorMatrixColorFilter(
        ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, w, 0f
        ))
    )

    private fun pixelate(src: Bitmap, block: Int): Bitmap {
        val sw = max(1, src.width / block)
        val sh = max(1, src.height / block)
        val small = Bitmap.createScaledBitmap(src, sw, sh, false)
        val out = Bitmap.createScaledBitmap(small, src.width, src.height, false)
        if (small !== out && small !== src) small.recycle()
        return out
    }

    /** Pixelate dengan celah transparan di antara sel. */
    private fun mosaic(src: Bitmap, block: Int): Bitmap {
        val out = pixelate(src, block)
        val c = Canvas(out)
        val p = Paint().apply {
            xfermode = CLEAR
            strokeWidth = max(1f, block / 8f)
        }
        var x = 0f
        while (x <= out.width) { c.drawLine(x, 0f, x, out.height.toFloat(), p); x += block }
        var y = 0f
        while (y <= out.height) { c.drawLine(0f, y, out.width.toFloat(), y, p); y += block }
        return out
    }

    /** Pixelate lalu pita hitam di tengah, hanya di area yang berisi konten. */
    private fun censor(src: Bitmap, block: Int): Bitmap {
        val out = pixelate(src, block)
        val c = Canvas(out)
        val p = Paint().apply {
            xfermode = SRC_ATOP
            color = 0xFF000000.toInt()
        }
        val bandH = out.height * 0.22f
        val cy = out.height / 2f
        c.drawRect(0f, cy - bandH / 2f, out.width.toFloat(), cy + bandH / 2f, p)
        return out
    }

    /** Pisah kanal R, G, B dengan geseran horizontal. */
    private fun chromatic(src: Bitmap, d: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        out.eraseColor(0)
        val c = Canvas(out)
        val shifts = floatArrayOf(-d, 0f, d)
        for (ch in 0 until 3) {
            val p = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                xfermode = ADD
                colorFilter = ColorMatrixColorFilter(channelMatrix(ch))
            }
            c.drawBitmap(src, shifts[ch], 0f, p)
        }
        return out
    }

    private fun channelMatrix(ch: Int): ColorMatrix {
        val m = FloatArray(20)
        m[ch * 5 + ch] = 1f
        m[18] = 1f
        return ColorMatrix(m)
    }

    /** Konten asli ditambah glow hasil blur lembut. */
    private fun bloom(src: Bitmap, r: Float): Bitmap {
        val glow = accumulate(src, grid(5, r * 1.2f))
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        out.eraseColor(0)
        val c = Canvas(out)
        c.drawBitmap(src, 0f, 0f, null)
        c.drawBitmap(glow, 0f, 0f, Paint().apply { xfermode = ADD })
        glow.recycle()
        return out
    }
}
