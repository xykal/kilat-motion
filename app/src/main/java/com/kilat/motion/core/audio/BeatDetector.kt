package com.kilat.motion.core.audio

import kotlin.math.max
import kotlin.math.min

/**
 * Deteksi onset (ketukan) dari envelope amplitudo. Murni Kotlin, bisa dites di JVM.
 * env: bucket merata yang mencakup [spanSec] detik audio.
 */
object BeatDetector {

    fun beatTimes(
        env: FloatArray,
        spanSec: Float,
        sensitivity: Float = 1.6f,
        minGapSec: Float = 0.3f
    ): List<Float> {
        val n = env.size
        if (n < 8 || spanSec <= 0f) return emptyList()
        val step = spanSec / n

        // fluks: kenaikan energi positif antar bucket
        val flux = FloatArray(n)
        for (i in 1 until n) flux[i] = max(0f, env[i] - env[i - 1])

        // rata-rata lokal ±0.5 detik sebagai acuan adaptif
        val win = max(4, (0.5f / step).toInt())
        val out = ArrayList<Float>()
        var last = -1e9f
        for (i in 1 until n - 1) {
            var sum = 0f
            var cnt = 0
            for (j in max(0, i - win)..min(n - 1, i + win)) {
                sum += flux[j]
                cnt++
            }
            val thr = (sum / cnt) * sensitivity + 0.01f
            val isPeak = flux[i] > thr && flux[i] >= flux[i - 1] && flux[i] > flux[i + 1]
            if (isPeak) {
                val t = (i + 0.5f) * step
                if (t - last >= minGapSec) {
                    out += t
                    last = t
                }
            }
        }
        return out
    }
}
