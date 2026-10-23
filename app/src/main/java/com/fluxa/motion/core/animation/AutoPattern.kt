package com.fluxa.motion.core.animation

import com.fluxa.motion.core.model.Layer
import kotlin.math.max

/**
 * Pola keyframe otomatis berdasarkan waktu (tanpa musik). Semua nilai dihitung dari
 * nilai dasar layer, jadi posisi, ukuran, dan opacity yang sudah diatur tetap jadi acuan.
 */
object AutoPattern {

    enum class Kind(val key: String) {
        ZOOM_IN("scale"),
        ZOOM_OUT("scale"),
        SHAKE("x"),
        FLOAT("y"),
        SPIN("rot"),
        FADE("alpha")
    }

    /** Rapat per indeks slider (0..3), dalam detik. */
    private val RATES_SEC = floatArrayOf(0.25f, 0.5f, 1f, 2f)

    /** Batas jumlah keyframe per pola, agar project tetap ringan. */
    private const val MAX_STEPS = 400

    /**
     * Pasang pola [kind] di seluruh durasi layer.
     * [rate]: indeks rapat 0..3. [intensity]: 1 = standar, 0..3.
     * Mengembalikan jumlah keyframe yang dipasang.
     */
    fun apply(layer: Layer, kind: Kind, rate: Int, intensity: Float): Int {
        if (layer.dur <= 0.05f) return 0
        val start = layer.start
        val end = layer.start + layer.dur
        val k = intensity.coerceIn(0f, 3f)
        val every = max(RATES_SEC[rate.coerceIn(0, RATES_SEC.size - 1)], layer.dur / MAX_STEPS)

        return when (kind) {
            Kind.ZOOM_IN -> {
                val s = layer.scale
                ramp(layer, kind.key, start, end, s, s * (1f + 0.35f * k))
            }
            Kind.ZOOM_OUT -> {
                val s = layer.scale
                ramp(layer, kind.key, start, end, s * (1f + 0.35f * k), s)
            }
            Kind.SHAKE -> {
                val b = layer.x
                val amp = 18f * k
                steps(layer, kind.key, start, end, every, "easeInOut", b) { i ->
                    if (i == 0) b else if (i % 2 == 1) b + amp else b - amp
                }
            }
            Kind.FLOAT -> {
                val b = layer.y
                val amp = 30f * k
                steps(layer, kind.key, start, end, every, "easeInOut", b) { i ->
                    if (i == 0) b else if (i % 2 == 1) b - amp else b + amp
                }
            }
            Kind.FADE -> {
                val b = layer.alpha
                val lo = max(0.05f, b * (1f - 0.6f * k))
                steps(layer, kind.key, start, end, every, "easeInOut", b) { i ->
                    if (i % 2 == 0) b else lo
                }
            }
            Kind.SPIN -> {
                val b = layer.rot
                val turn = 45f * k
                val n = (layer.dur / every).toInt()
                steps(layer, kind.key, start, end, every, "linear", b + turn * n) { i -> b + turn * i }
            }
        }
    }

    /** Dua keyframe: awal dan akhir, dengan easing di antaranya. */
    private fun ramp(layer: Layer, key: String, start: Float, end: Float, from: Float, to: Float): Int {
        Anim.setKey(layer, key, start, from, "linear")
        Anim.setKey(layer, key, end, to, "easeInOut")
        return 2
    }

    /**
     * Keyframe berulang setiap [every] detik. [valueAt] menentukan nilai di langkah ke-i.
     * Langkah terakhir selalu di [endValue] pada waktu akhir layer.
     */
    private fun steps(
        layer: Layer,
        key: String,
        start: Float,
        end: Float,
        every: Float,
        ease: String,
        endValue: Float,
        valueAt: (Int) -> Float
    ): Int {
        var n = 0
        Anim.setKey(layer, key, start, valueAt(0), "linear")
        n++
        var t = start
        var i = 0
        while (true) {
            t += every
            i++
            if (t >= end - 0.001f) break
            Anim.setKey(layer, key, t, valueAt(i), ease)
            n++
        }
        Anim.setKey(layer, key, end, endValue, "easeOut")
        return n + 1
    }
}
