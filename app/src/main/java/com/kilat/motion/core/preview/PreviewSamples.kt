package com.kilat.motion.core.preview

import com.kilat.motion.core.animation.Anim
import com.kilat.motion.core.model.KIND_TEXT
import com.kilat.motion.core.model.Layer
import com.kilat.motion.core.model.LayerEffect

/**
 * Layer contoh untuk preview visual preset dan efek di panel Inspector.
 * Dirender oleh Renderer yang sama dengan editor, jadi hasilnya sejujurnya.
 */
object PreviewSamples {

    /** Durasi satu siklus preview (detik). Animasi diulang terus. */
    const val DURATION = 1.6f

    /** Strength efek di preview: cukup terlihat tanpa menutupi teks contoh. */
    const val EFFECT_AMOUNT = 0.8f

    fun base(): Layer = Layer().apply {
        kind = KIND_TEXT
        name = "preview"
        text = "Aa"
        start = 0f
        dur = DURATION
        textSize = 50f
        color = 0xFFFFFFFF.toInt()
        fontKey = "sans_black"
    }

    /** Layer contoh dengan satu preset animasi di tengah siklus. */
    fun presetLayer(preset: String): Layer {
        val l = base()
        if (preset == "kenBurns") {
            // Ken Burns hanya berlaku untuk gambar. Di preview, cukup zoom lambat dengan bentuk yang sama.
            Anim.setKey(l, "scale", 0f, 1f, "linear")
            Anim.setKey(l, "scale", DURATION, 1.18f, "linear")
        } else {
            Anim.applyPreset(l, preset, 0.2f, DURATION)
        }
        return l
    }

    /** Layer contoh dengan satu efek aktif. */
    fun effectLayer(type: String): Layer {
        val l = base()
        l.effects.add(LayerEffect(type, EFFECT_AMOUNT, 0.5f, defaultEffectColor(type), true))
        return l
    }

    /** Warna awal untuk efek yang perlu warna (light leak, tint). Sama dengan Inspector. */
    fun defaultEffectColor(type: String): Int = when (type) {
        "lightleak" -> 0xFFFF8A3D.toInt()
        "tint" -> 0xFF2EE6C0.toInt()
        else -> 0xFF000000.toInt()
    }
}
