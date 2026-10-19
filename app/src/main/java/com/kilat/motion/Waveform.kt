package com.kilat.motion

import kotlin.math.abs
import kotlin.math.max

/** Utilitas murni (bisa dites di JVM tanpa Android). */
object Waveform {

    /**
     * Ubah sampel PCM 16-bit jadi envelope (0..1) sebanyak [buckets].
     * Dipakai untuk menggambar waveform di timeline.
     */
    fun envelope(samples: ShortArray, buckets: Int): FloatArray {
        if (buckets <= 0) return FloatArray(0)
        val out = FloatArray(buckets)
        if (samples.isEmpty()) return out
        val per = samples.size.toFloat() / buckets
        for (i in 0 until buckets) {
            val s0 = (i * per).toInt().coerceIn(0, samples.size - 1)
            val s1 = (((i + 1) * per).toInt()).coerceIn(s0 + 1, samples.size)
            var peak = 0
            var j = s0
            val step = max(1, (s1 - s0) / 96)
            while (j < s1) {
                val a = abs(samples[j].toInt())
                if (a > peak) peak = a
                j += step
            }
            out[i] = peak / 32768f
        }
        var mx = 0f
        for (v in out) if (v > mx) mx = v
        if (mx > 0.0001f) {
            for (i in out.indices) out[i] = (out[i] / mx).coerceIn(0f, 1f)
        }
        return out
    }

    /** Gabungkan channel interleaved jadi mono (rata-rata). */
    fun toMono(interleaved: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return interleaved
        val frames = interleaved.size / channels
        val mono = ShortArray(frames)
        for (f in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += interleaved[f * channels + c].toInt()
            mono[f] = (sum / channels).toShort()
        }
        return mono
    }
}
