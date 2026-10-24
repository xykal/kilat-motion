package com.fluxa.motion.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.fluxa.motion.core.model.LayerEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Efek per-layer yang tidak menyimpan state: matriks warna dan overlay.
 * Dipakai bersama oleh [Renderer] (ekspor dan preview editor) dan [EffectPreview] (thumbnail).
 * Semua fungsi menerima Paint dari pemanggil, jadi tidak ada Paint bersama yang diperebutkan thread.
 */
object EffectDraw {

    /** Efek yang bekerja lewat matriks warna pada seluruh layer. */
    val COLOR_TYPES = setOf(
        "grayscale", "sepia", "invert", "contrast", "saturate", "warm", "cool", "flicker",
        "brightness", "exposure", "hue", "fade", "noir", "bleach", "teal_orange", "vintage", "polaroid", "night"
    )

    /** Efek yang digambar sebagai overlay di atas konten (hanya menimpa piksel yang sudah ada). */
    val OVERLAY_TYPES = setOf(
        "vignette", "lightleak", "scanlines", "grain", "letterbox", "dust", "border", "flash",
        "spotlight", "haze", "sparkle", "rain", "snow"
    )

    /** Efek yang butuh offscreen layer. */
    val LAYER_TYPES = setOf("tint", "rgbsplit", "chromakey")

    /** Efek yang menggeser atau memotong konten. */
    val MOTION_TYPES = setOf("glitch", "shake", "zoompulse", "wobble")

    val SUPPORTED: Set<String> = COLOR_TYPES + OVERLAY_TYPES + LAYER_TYPES + MOTION_TYPES

    private val IDENTITY = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    private val GRAY = floatArrayOf(
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    private val SEPIA = floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    private val INVERT = floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )

    /**
     * Matriks 4x5 (20 angka, format ColorMatrix) untuk efek warna. amount 0 selalu identitas.
     * Mengembalikan null untuk efek yang bukan efek warna.
     */
    fun colorValues(e: LayerEffect, t: Float): FloatArray? {
        if (e.type !in COLOR_TYPES) return null
        val a = e.amount.coerceIn(0f, 1f)
        return when (e.type) {
            "grayscale" -> lerp(IDENTITY, GRAY, a)
            "sepia" -> lerp(IDENTITY, SEPIA, a)
            "invert" -> lerp(IDENTITY, INVERT, a)
            "contrast" -> {
                val c = 1f + a
                val o = 128f * (1f - c)
                floatArrayOf(
                    c, 0f, 0f, 0f, o,
                    0f, c, 0f, 0f, o,
                    0f, 0f, c, 0f, o,
                    0f, 0f, 0f, 1f, 0f
                )
            }
            "saturate" -> saturation(1f + a)   // 0 = asli, 1 = saturasi 2x
            "warm" -> floatArrayOf(
                1f + 0.3f * a, 0f, 0f, 0f, 20f * a,
                0f, 1f + 0.1f * a, 0f, 0f, 8f * a,
                0f, 0f, 1f - 0.25f * a, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            "cool" -> floatArrayOf(
                1f - 0.25f * a, 0f, 0f, 0f, 0f,
                0f, 1f + 0.05f * a, 0f, 0f, 0f,
                0f, 0f, 1f + 0.3f * a, 0f, 20f * a,
                0f, 0f, 0f, 1f, 0f
            )
            "flicker" -> {
                val k = 1f + a * 0.5f * (hash01(floor(t * 18f).toInt()) * 2f - 1f)
                floatArrayOf(
                    k, 0f, 0f, 0f, 0f,
                    0f, k, 0f, 0f, 0f,
                    0f, 0f, k, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            }
            "brightness" -> scaleOff(1f, 1f, 1f, 90f * a, 90f * a, 90f * a)
            "exposure" -> {
                val m = 1f + 1.2f * a
                scaleOff(m, m, m, 0f, 0f, 0f)
            }
            "hue" -> hueRotate(150f * a)
            "fade" -> fadeM(a)
            "noir" -> mul(contrastM(1f + 1.2f * a), lerp(IDENTITY, GRAY, a))
            "bleach" -> mul(contrastM(1f + 0.6f * a), saturation(1f - 0.7f * a))
            "teal_orange" -> scaleOff(1f + 0.18f * a, 1f + 0.02f * a, 1f - 0.12f * a, 0f, 8f * a, 10f * a)
            "vintage" -> mul(fadeM(0.6f * a), lerp(IDENTITY, SEPIA, 0.8f * a))
            "polaroid" -> mul(contrastM(1f - 0.12f * a), scaleOff(1f + 0.08f * a, 1f + 0.03f * a, 1f - 0.05f * a, 10f * a, 6f * a, 0f))
            "night" -> scaleOff(1f - 0.45f * a, 1f - 0.3f * a, 1f + 0.1f * a, 0f, 0f, 0f)
            else -> null
        }
    }

    /** Gabungkan beberapa efek warna berurutan (urutan = urutan di stack). null bila tidak ada. */
    fun composeColor(effects: List<LayerEffect>, t: Float): FloatArray? {
        var acc: FloatArray? = null
        for (e in effects) {
            val m = colorValues(e, t) ?: continue
            acc = if (acc == null) m else mul(m, acc)
        }
        return acc
    }

    /** Skala denyut untuk efek zoompulse. Berdenyut sekitar 1,2 kali per detik. amount 0 = 1. */
    fun zoomPulseScale(e: LayerEffect, t: Float): Float {
        val a = e.amount.coerceIn(0f, 1f)
        val beat = abs(sin(t * PI.toFloat() * 1.2f))
        return 1f + a * (0.04f + 0.12f * e.size) * beat
    }

    /** Sudut goyang (derajat) untuk efek wobble. amount 0 = 0. */
    fun wobbleDeg(e: LayerEffect, t: Float): Float {
        val a = e.amount.coerceIn(0f, 1f)
        return a * (2f + 6f * e.size) * sin(t * 2f * PI.toFloat() * 0.9f)
    }

    /** Pergeseran kamera untuk efek shake, dalam piksel layer. */
    fun shakeOffset(e: LayerEffect, cw: Float, t: Float): Pair<Float, Float> {
        val amp = e.amount.coerceIn(0f, 1f) * cw * 0.04f * (0.5f + e.size)
        return (sin(t * 29f) * amp) to (cos(t * 23f) * amp)
    }

    /**
     * Gambar overlay ke [canvas]. Koordinat lokal: konten berpusat di (0,0) dengan ukuran cw x ch.
     * [bounds] adalah area yang ditutupi overlay. [grain] mengembalikan tile noise.
     */
    fun drawOverlay(
        canvas: Canvas,
        p: Paint,
        e: LayerEffect,
        bounds: RectF,
        cw: Float,
        ch: Float,
        t: Float,
        grain: () -> Bitmap
    ) {
        p.reset()
        p.isAntiAlias = true
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        val a = (e.amount * 255f).toInt().coerceIn(0, 255)
        when (e.type) {
            "vignette" -> {
                val r = maxOf(1f, kotlin.math.hypot(cw, ch) / 2f * (0.35f + 0.65f * (1f - e.size)))
                p.shader = RadialGradient(
                    0f, 0f, r,
                    intArrayOf(withA(e.color, 0), withA(e.color, a)),
                    floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP
                )
                canvas.drawRect(bounds, p)
            }
            "lightleak" -> {
                val cx = -cw / 2f + e.size * cw
                val cy = -ch / 2f
                p.shader = RadialGradient(
                    cx, cy, maxOf(cw, ch) * 0.9f,
                    intArrayOf(withA(e.color, a), withA(e.color, 0)),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                )
                canvas.drawRect(bounds, p)
            }
            "scanlines" -> {
                val step = 4f + e.size * 16f
                val thick = step * 0.45f
                p.color = withA(0xFF000000.toInt(), a)
                var y = bounds.top
                while (y < bounds.bottom) {
                    canvas.drawRect(bounds.left, y, bounds.right, minOf(bounds.bottom, y + thick), p)
                    y += step
                }
            }
            "grain" -> {
                val tile = grain()
                val shader = android.graphics.BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                val seed = floor(t * 24f).toInt()
                val m = android.graphics.Matrix()
                m.setTranslate(hash01(seed) * tile.width, hash01(seed + 7) * tile.height)
                shader.setLocalMatrix(m)
                p.shader = shader
                p.alpha = a
                canvas.drawRect(bounds, p)
            }
            "letterbox" -> {
                // Bilah hitam atas dan bawah. Tinggi 4% sampai 34% dari tinggi konten.
                val bar = ch * (0.04f + e.size * 0.3f)
                p.color = withA(e.color, a)
                canvas.drawRect(bounds.left, bounds.top, bounds.right, -ch / 2f + bar, p)
                canvas.drawRect(bounds.left, ch / 2f - bar, bounds.right, bounds.bottom, p)
            }
            "dust" -> {
                // Bintik debu putih yang berpindah 12 kali per detik.
                val seed = floor(t * 12f).toInt()
                val n = (20 + e.size * 120f).toInt()
                p.color = withA(0xFFFFFFFF.toInt(), a)
                for (i in 0 until n) {
                    val x = -cw / 2f + hash01(seed * 131 + i * 7) * cw
                    val y = -ch / 2f + hash01(seed * 97 + i * 13 + 5) * ch
                    val r = 0.8f + hash01(seed * 53 + i * 3) * (1.5f + e.size * 3f)
                    canvas.drawCircle(x, y, r, p)
                }
            }
            "border" -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f + e.size * 14f
                p.color = withA(e.color, maxOf(a, 96))
                canvas.drawRect(-cw / 2f, -ch / 2f, cw / 2f, ch / 2f, p)
            }
            "spotlight" -> {
                // Sorot: area tengah bening, sisanya gelap. Ukuran menentukan radius.
                val r = maxOf(1f, maxOf(cw, ch) * (0.25f + 0.6f * e.size))
                p.shader = RadialGradient(
                    0f, 0f, r,
                    intArrayOf(0x00000000, 0x00000000, withA(0xFF000000.toInt(), a)),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
                )
                canvas.drawRect(bounds, p)
            }
            "haze" -> {
                // Kabut: warna pudar dari bawah ke atas.
                p.shader = LinearGradient(
                    0f, ch / 2f, 0f, -ch / 2f + ch * 0.35f,
                    intArrayOf(withA(e.color, a), withA(e.color, 0)),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawRect(bounds, p)
            }
            "sparkle" -> {
                // Kilau: titik cahaya yang berkedip di posisi tetap.
                val n = (10 + e.size * 40f).toInt()
                for (i in 0 until n) {
                    val x = -cw / 2f + hash01(i * 31 + 1) * cw
                    val y = -ch / 2f + hash01(i * 17 + 3) * ch
                    val ph = hash01(i * 7 + 5) * 6.2831f
                    val tw = 0.5f + 0.5f * sin(t * (2f + hash01(i * 3 + 9) * 4f) + ph)
                    p.color = withA(e.color, (a * tw).toInt())
                    canvas.drawCircle(x, y, (1f + e.size * 3f) * tw, p)
                }
            }
            "rain" -> {
                // Hujan: garis miring yang jatuh terus. Kecepatan dan jumlah ikut ukuran.
                val n = (30 + e.size * 150f).toInt()
                val len = 12f + e.size * 40f
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.5f + e.size * 1.5f
                p.color = withA(e.color, a)
                for (i in 0 until n) {
                    val x = -cw / 2f + hash01(i * 29 + 2) * cw
                    val fall = (0.8f + hash01(i * 11 + 4) * 0.6f) * (0.6f + e.size * 0.8f)
                    val fr = hash01(i * 13 + 8) + t * fall
                    val y = -ch / 2f + (fr - floor(fr)) * ch
                    canvas.drawLine(x, y, x - len * 0.2f, y + len, p)
                }
            }
            "snow" -> {
                // Salju: bintik yang turun pelan dan bergoyang.
                val n = (20 + e.size * 120f).toInt()
                p.color = withA(e.color, a)
                for (i in 0 until n) {
                    val x0 = -cw / 2f + hash01(i * 23 + 6) * cw
                    val speed = (0.08f + hash01(i * 19 + 2) * 0.12f) * (0.5f + e.size)
                    val fr = hash01(i * 37 + 1) + t * speed
                    val y = -ch / 2f + (fr - floor(fr)) * ch
                    val x = x0 + sin(t * 1.3f + i) * 12f
                    val r = (1.5f + e.size * 3.5f) * (0.6f + hash01(i * 41 + 7) * 0.6f)
                    canvas.drawCircle(x, y, r, p)
                }
            }
            "flash" -> {
                // Kilatan putih berkedip. Laju naik sesuai ukuran.
                val rate = 1f + e.size * 6f
                if (floor(t * rate).toInt() % 2 == 0) {
                    p.color = withA(0xFFFFFFFF.toInt(), (a * 0.7f).toInt())
                    canvas.drawRect(bounds, p)
                }
            }
        }
    }

    fun hash01(n: Int): Float {
        var x = n * 374761393 + 1274126177
        x = (x xor (x ushr 13)) * 1103515245
        x = x xor (x ushr 16)
        return (x and 0xFFFF) / 65535f
    }

    fun withA(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    /** Matriks skala per kanal plus offset (offset dalam skala 0..255). */
    private fun scaleOff(sr: Float, sg: Float, sb: Float, or: Float, og: Float, ob: Float): FloatArray = floatArrayOf(
        sr, 0f, 0f, 0f, or,
        0f, sg, 0f, 0f, og,
        0f, 0f, sb, 0f, ob,
        0f, 0f, 0f, 1f, 0f
    )

    private fun contrastM(c: Float): FloatArray {
        val o = 128f * (1f - c)
        return scaleOff(c, c, c, o, o, o)
    }

    /** Pudar: angkat hitam dan turunkan kontras. x 0 = identitas. */
    private fun fadeM(x: Float): FloatArray {
        val c = 1f - 0.35f * x
        val o = 51f * x
        return scaleOff(c, c, c, o, o, o)
    }

    /** Rotasi hue (matriks standar feColorMatrix hueRotate). deg 0 = identitas. */
    private fun hueRotate(deg: Float): FloatArray {
        val r = deg * PI.toFloat() / 180f
        val c = cos(r)
        val s = sin(r)
        return floatArrayOf(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    }

    private fun lerp(a: FloatArray, b: FloatArray, w: Float): FloatArray =
        FloatArray(20) { i -> a[i] + (b[i] - a[i]) * w }

    private fun saturation(s: Float): FloatArray = floatArrayOf(
        0.213f + 0.787f * s, 0.715f - 0.715f * s, 0.072f - 0.072f * s, 0f, 0f,
        0.213f - 0.213f * s, 0.715f + 0.285f * s, 0.072f - 0.072f * s, 0f, 0f,
        0.213f - 0.213f * s, 0.715f - 0.715f * s, 0.072f + 0.928f * s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    /** Komposisi a ∘ b: b diterapkan dulu, lalu a. */
    private fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                var s = 0f
                for (k in 0 until 4) s += a[row * 5 + k] * b[k * 5 + col]
                out[row * 5 + col] = s
            }
            var o = a[row * 5 + 4]
            for (k in 0 until 4) o += a[row * 5 + k] * b[k * 5 + 4]
            out[row * 5 + 4] = o
        }
        return out
    }
}
