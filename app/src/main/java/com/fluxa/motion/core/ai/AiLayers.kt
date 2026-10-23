package com.fluxa.motion.core.ai

import android.content.Context
import com.fluxa.motion.core.animation.Anim
import com.fluxa.motion.core.model.KIND_TEXT
import com.fluxa.motion.core.model.Layer
import com.fluxa.motion.core.model.Project
import org.json.JSONObject

/** Spesifikasi layer teks hasil AI, sudah divalidasi dan dibatasi nilainya. */
data class LayerSpec(
    val text: String,
    val preset: String,
    val xFrac: Float,
    val yFrac: Float,
    val sizeFrac: Float,
    val color: Int,
    val start: Float,
    val dur: Float
)

object AiLayers {

    const val MAX_LAYERS = 6

    private val SYSTEM_PROMPT = """
        Kamu asisten motion graphics untuk aplikasi editor video.
        Balas HANYA dengan JSON valid, tanpa penjelasan, dengan format:
        {"layers":[{"text":"teks","preset":"zoom","x":0.5,"y":0.5,"size":0.1,"color":"#FFFFFF","start":0,"dur":2}]}
        Aturan:
        - maksimal $MAX_LAYERS layer
        - preset salah satu dari: %s
        - x dan y 0 sampai 1 (posisi relatif, 0.5 = tengah)
        - size 0.02 sampai 0.3 (relatif terhadap sisi terpendek)
        - color format #RRGGBB
        - start dan dur dalam detik, total tidak melewati durasi project
    """.trimIndent()

    fun systemPrompt(): String = SYSTEM_PROMPT.format(Anim.PRESETS.joinToString(", "))

    /** Ubah JSON balasan model jadi daftar spec yang aman. Fungsi murni, mudah diuji. */
    fun parseSpecs(root: JSONObject, durationSec: Float): List<LayerSpec> {
        val arr = root.optJSONArray("layers") ?: throw AiException("AI tidak mengembalikan layers")
        val out = ArrayList<LayerSpec>()
        for (i in 0 until arr.length()) {
            if (out.size >= MAX_LAYERS) break
            val o = arr.optJSONObject(i) ?: continue
            val text = o.optString("text").trim().take(120)
            if (text.isEmpty()) continue
            val preset = o.optString("preset").takeIf { it in Anim.PRESETS } ?: "fade"
            val start = o.optDouble("start", 0.0).toFloat().let { clampF(it, 0f, durationSec - 0.2f) }
            val dur = o.optDouble("dur", 2.0).toFloat().let { clampF(it, 0.3f, durationSec - start) }
            out += LayerSpec(
                text = text,
                preset = preset,
                xFrac = o.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                yFrac = o.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                sizeFrac = o.optDouble("size", 0.1).toFloat().coerceIn(0.02f, 0.3f),
                color = parseHexColor(o.optString("color"), 0xFFFFFFFF.toInt()),
                start = start,
                dur = dur
            )
        }
        return out
    }

    fun parseHexColor(hex: String?, fallback: Int): Int {
        val h = hex?.trim()?.removePrefix("#") ?: return fallback
        if (h.length != 6 || h.any { !it.isLetterOrDigit() || it.lowercaseChar() !in "0123456789abcdef" }) return fallback
        return (0xFF000000 or h.toLong(16)).toInt()
    }

    /** Clamp yang tidak melempar exception bila hi < lo (durasi sangat pendek). */
    fun clampF(v: Float, lo: Float, hi: Float): Float = if (hi < lo) lo else v.coerceIn(lo, hi)

    fun toLayer(spec: LayerSpec, project: Project): Layer {
        val layer = Layer().apply {
            kind = KIND_TEXT
            name = spec.text.take(16)
            text = spec.text
            textSize = minOf(project.width, project.height) * spec.sizeFrac
            x = project.width * spec.xFrac
            y = project.height * spec.yFrac
            color = spec.color
            start = spec.start
            dur = spec.dur
        }
        Anim.applyPreset(layer, spec.preset, spec.start, project.duration)
        return layer
    }

    /** Buat layer dari prompt user. Dipanggil dari background thread. */
    fun fromPrompt(ctx: Context, prompt: String, project: Project): List<Layer> {
        val user = "Durasi project: ${project.duration} detik. Ukuran ${project.width}x${project.height}.\n" +
            "Permintaan: $prompt"
        val reply = AiClient.chat(ctx, systemPrompt(), user)
        val specs = parseSpecs(AiClient.extractJson(reply), project.duration)
        if (specs.isEmpty()) throw AiException("AI tidak membuat layer, coba prompt lain")
        return specs.map { toLayer(it, project) }
    }

    /** Caption otomatis dari transkrip Whisper: satu layer per segmen, di bawah tengah. */
    fun captionSpecs(segments: List<AiClient.Segment>, project: Project): List<LayerSpec> =
        segments.take(200).map { s ->
            val start = clampF(s.start, 0f, project.duration)
            val dur = clampF(s.end - s.start, 0.4f, project.duration - start)
            LayerSpec(
                text = s.text, preset = "fade", xFrac = 0.5f, yFrac = 0.82f, sizeFrac = 0.055f,
                color = 0xFFFFFFFF.toInt(), start = start, dur = dur
            )
        }

    fun captions(segments: List<AiClient.Segment>, project: Project): List<Layer> =
        captionSpecs(segments, project).map { toLayer(it, project) }
}
