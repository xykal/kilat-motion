package com.fluxa.motion.core.animation

import com.fluxa.motion.core.audio.AudioUtil
import com.fluxa.motion.core.audio.BeatDetector
import com.fluxa.motion.core.model.Layer
import com.fluxa.motion.ui.components.FluxaIcon
import kotlin.math.max
import kotlin.math.min

/** Keyframe yang mengikuti ketukan lagu: setiap ketukan memberi pulsa pada satu properti. */
object BeatKeys {

    enum class Style(val key: String, val label: String, val desc: String, val icon: FluxaIcon) {
        ZOOM("scale", "Zoom pulse", "Membesar tiap ketukan lalu kembali", FluxaIcon.SPARK),
        SWING("rot", "Goyang rotasi", "Miring bergantian kiri dan kanan tiap ketukan", FluxaIcon.LOOP),
        DIP("alpha", "Kedip", "Redup sebentar di tiap ketukan", FluxaIcon.EYE_OFF),
        BOUNCE("y", "Lompat", "Naik sedikit di tiap ketukan", FluxaIcon.BOLT),
        SHAKE("x", "Getar", "Bergeser kiri kanan di tiap ketukan", FluxaIcon.TRANSFORM)
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

    /** Nilai dasar properti dari layer, supaya pulsa berangkat dari posisi/ukuran yang sudah diatur. */
    private fun baseOf(layer: Layer, key: String): Float = when (key) {
        "scale" -> layer.scale
        "rot" -> layer.rot
        "alpha" -> layer.alpha
        "y" -> layer.y
        "x" -> layer.x
        else -> 0f
    }

    /**
     * Pasang pulsa di ketukan yang ada di dalam layer.
     * [every]: pakai setiap N ketukan (1 = semua). [intensity]: 1 = standar, 0..3.
     * Keyframe di waktu yang sama akan ditimpa. Mengembalikan jumlah pulsa yang dipasang.
     */
    fun apply(layer: Layer, beats: List<Float>, style: Style, every: Int = 1, intensity: Float = 1f): Int {
        val end = layer.start + layer.dur
        val k = intensity.coerceIn(0f, 3f)
        val step = max(1, every)
        val inRange = beats.filter { it >= layer.start && it <= end - ATTACK }
        var used = 0
        var dir = 1f
        val base = baseOf(layer, style.key)
        for ((i, b) in inRange.withIndex()) {
            if (i % step != 0) continue
            val peak = when (style) {
                Style.ZOOM -> base * (1f + 0.12f * k)
                Style.SWING -> base + 4f * k * dir
                Style.DIP -> max(0.05f, base * (1f - 0.55f * k))
                Style.BOUNCE -> base - 60f * k
                Style.SHAKE -> base + 16f * k * dir
            }
            Anim.setKey(layer, style.key, b, base, "linear")
            Anim.setKey(layer, style.key, b + ATTACK, peak, "easeOut")
            Anim.setKey(layer, style.key, min(b + DECAY, end), base, "easeOut")
            used++
            dir = -dir
        }
        return used
    }
}
