package com.kilat.motion.core.animation

import com.kilat.motion.core.audio.AudioUtil
import com.kilat.motion.core.audio.BeatDetector
import com.kilat.motion.core.model.Layer
import com.kilat.motion.ui.components.KilatIcon
import kotlin.math.min

/** Keyframe yang mengikuti ketukan lagu: setiap beat dapat pulsa pada satu properti. */
object BeatKeys {

    enum class Style(val key: String, val label: String, val desc: String, val base: Float, val peak: Float, val icon: KilatIcon) {
        ZOOM("scale", "Zoom pulse", "Membesar tiap ketukan lalu kembali", 1f, 1.12f, KilatIcon.SPARK),
        SWING("rot", "Goyang rotasi", "Miring bergantian kiri dan kanan tiap ketukan", 0f, 4f, KilatIcon.LOOP),
        DIP("alpha", "Kedip", "Redup sebentar di tiap ketukan", 1f, 0.45f, KilatIcon.EYE_OFF)
    }

    /** Lama satu pulsa: naik cepat, lalu meluruh kembali ke nilai dasar. */
    const val ATTACK = 0.03f
    const val DECAY = 0.16f

    /** Resolusi analisis: 25 bucket per detik (sekitar 40 ms), dibatasi agar memori aman. */
    private const val BUCKETS_PER_SEC = 25
    private const val MAX_BUCKETS = 4000
    private const val MAX_SECONDS = 150f

    /**
     * Deteksi ketukan di file audio, lalu ubah ke waktu project.
     * Dipanggil dari background thread (membaca dan decode audio).
     */
    fun detect(
        path: String,
        musicStart: Float,
        musicOffset: Float,
        musicDur: Float,
        projectDuration: Float
    ): List<Float> {
        val span = min(AudioUtil.durationSeconds(path), MAX_SECONDS)
        if (span <= 1f) return emptyList()
        val buckets = min(MAX_BUCKETS, (span * BUCKETS_PER_SEC).toInt())
        val env = AudioUtil.decodeEnvelope(path, buckets, MAX_SECONDS) ?: return emptyList()
        val fileBeats = BeatDetector.beatTimes(env, span)
        return fileBeats
            .filter { it >= musicOffset && it <= musicOffset + musicDur }
            .map { musicStart + (it - musicOffset) }
            .filter { it >= 0f && it <= projectDuration }
    }

    /**
     * Pasang pulsa di setiap ketukan yang ada di dalam layer. Keyframe di waktu yang sama
     * akan ditimpa. Mengembalikan jumlah ketukan yang dipakai.
     */
    fun apply(layer: Layer, beats: List<Float>, style: Style): Int {
        var used = 0
        var dir = 1f
        val end = layer.start + layer.dur
        for (b in beats) {
            if (b < layer.start || b > end - ATTACK) continue
            val peak = if (style == Style.SWING) style.peak * dir else style.peak
            Anim.setKey(layer, style.key, b, style.base, "linear")
            Anim.setKey(layer, style.key, b + ATTACK, peak, "easeOut")
            Anim.setKey(layer, style.key, min(b + DECAY, end), style.base, "easeOut")
            used++
            dir = -dir
        }
        return used
    }
}
