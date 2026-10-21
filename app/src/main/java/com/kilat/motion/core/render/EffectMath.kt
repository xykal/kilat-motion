package com.kilat.motion.core.render

import kotlin.math.min

/** Rumus murni efek video dan blur. Tidak memakai Android, sehingga bisa diuji di JVM. */
object EffectMath {

    /** 20 jenis blur. Id disimpan di proyek, label ditampilkan di Inspector. */
    val BLUR_TYPES: List<Pair<String, String>> = listOf(
        "gauss" to "Gaussian",
        "box" to "Box blur",
        "motion_h" to "Blur gerak horizontal",
        "motion_v" to "Blur gerak vertikal",
        "motion_d1" to "Blur gerak diagonal /",
        "motion_d2" to "Blur gerak diagonal \\",
        "streak" to "Streak (jejak cahaya)",
        "zoom_in" to "Zoom blur masuk",
        "zoom_out" to "Zoom blur keluar",
        "spin" to "Spin blur (putar)",
        "bokeh" to "Bokeh",
        "ghost" to "Ghost (gema)",
        "double" to "Double vision",
        "chromatic" to "Chromatic aberration",
        "jitter" to "Getar",
        "frost" to "Frosted (kasar)",
        "bloom" to "Bloom (glow lembut)",
        "pixelate" to "Pixelate",
        "mosaic" to "Mosaic",
        "censor" to "Sensor (kotak hitam)"
    )

    /** Alpha tint putih untuk glass. Input persen (0..100), hasil 0..255. */
    fun glassTintAlpha(percent: Float): Int =
        (percent.coerceIn(0f, 100f) / 100f * 255f).toInt()

    /** Faktor skala untuk proxy kecil latar glass. Sisi terpanjang tidak lebih dari maxSide. */
    fun glassDownscale(w: Int, h: Int, maxSide: Float = 320f): Float {
        val longSide = maxOf(w, h).toFloat()
        if (longSide <= 0f) return 1f
        return min(1f, maxSide / longSide)
    }

    /** Ukuran sisi terpanjang buffer offscreen untuk blur, dalam piksel. */
    const val BLUR_MAX_SIDE = 1400f

    /** Indeks frame video untuk waktu lokal layer. Mengembalikan frame terdekat di dalam rentang. */
    fun videoFrameIndex(localSec: Float, fps: Float, frameCount: Int): Int {
        if (frameCount <= 0) return 0
        val idx = (localSec.coerceAtLeast(0f) * fps.coerceAtLeast(1f)).toInt()
        return min(idx, frameCount - 1)
    }

    /** Ukuran hasil "fit" tanpa melebihi maxLong pada sisi terpanjang. */
    fun fitSize(srcW: Int, srcH: Int, maxLong: Int): Pair<Int, Int> {
        val longSide = maxOf(srcW, srcH)
        if (longSide <= maxLong || longSide <= 0) return Pair(srcW.coerceAtLeast(1), srcH.coerceAtLeast(1))
        val s = maxLong.toFloat() / longSide
        return Pair(
            (srcW * s).toInt().coerceAtLeast(1),
            (srcH * s).toInt().coerceAtLeast(1)
        )
    }
}
