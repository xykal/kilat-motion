package com.kilat.motion.core.animation

import com.kilat.motion.core.model.*

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max
import kotlin.math.round

object Anim {

    val EASES = listOf("easeOut", "easeIn", "easeInOut", "linear", "back", "bounce")
    val EASE_LABELS = mapOf(
        "easeOut" to "Ease Out", "easeIn" to "Ease In", "easeInOut" to "Ease In-Out",
        "linear" to "Linear", "back" to "Back", "bounce" to "Bounce"
    )

    fun ease(name: String, p0: Float): Float {
        val p = p0.coerceIn(0f, 1f)
        return when (name) {
            "linear" -> p
            "easeIn" -> p * p * p
            "easeInOut" -> if (p < 0.5f) 4f * p * p * p else 1f - Math.pow((-2f * p + 2f).toDouble(), 3.0).toFloat() / 2f
            "back" -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                1f + c3 * Math.pow((p - 1f).toDouble(), 3.0).toFloat() + c1 * Math.pow((p - 1f).toDouble(), 2.0).toFloat()
            }
            "bounce" -> {
                val n = 7.5625f
                val d = 2.75f
                var x = p
                if (x < 1f / d) n * x * x
                else if (x < 2f / d) { x -= 1.5f / d; n * x * x + 0.75f }
                else if (x < 2.5f / d) { x -= 2.25f / d; n * x * x + 0.9375f }
                else { x -= 2.625f / d; n * x * x + 0.984375f }
            }
            else -> 1f - Math.pow((1f - p).toDouble(), 3.0).toFloat() // easeOut
        }
    }

    fun evalKeys(keys: List<Keyframe>, t: Float): Float? {
        if (keys.isEmpty()) return null
        if (t <= keys[0].t) return keys[0].v
        val last = keys[keys.size - 1]
        if (t >= last.t) return last.v
        var a = keys[0]
        var b = keys[0]
        for (i in 0 until keys.size - 1) {
            if (t >= keys[i].t && t <= keys[i + 1].t) {
                a = keys[i]
                b = keys[i + 1]
                break
            }
        }
        if (b.t <= a.t) return b.v
        var p = (t - a.t) / (b.t - a.t)
        p = ease(a.ease, p)
        return a.v + (b.v - a.v) * p
    }

    fun value(layer: Layer, key: String, t: Float): Float {
        val ch = layer.channels[key]
        if (ch == null || ch.keys.isEmpty()) return layer.getStatic(key)
        return evalKeys(ch.keys, t) ?: layer.getStatic(key)
    }

    fun evalLayer(layer: Layer, t: Float): HashMap<String, Float> {
        val m = HashMap<String, Float>(24)
        for (k in PROP_KEYS) m[k] = value(layer, k, t)
        layer.ev = m
        return m
    }

    fun channelOf(layer: Layer, key: String): Channel =
        layer.channels.getOrPut(key) { Channel() }

    fun setKey(layer: Layer, key: String, t: Float, v: Float, easeName: String = "easeOut") {
        val time = round(t * 1000f) / 1000f
        val ch = channelOf(layer, key)
        val existing = ch.keys.firstOrNull { abs(it.t - time) < 0.02f }
        if (existing != null) {
            existing.v = v
            existing.ease = easeName
        } else {
            ch.keys.add(Keyframe(time, v, easeName))
            ch.keys.sortBy { it.t }
        }
    }

    fun removeKey(layer: Layer, key: String, t: Float) {
        val ch = layer.channels[key] ?: return
        ch.keys.removeAll { abs(it.t - t) < 0.03f }
        if (ch.keys.isEmpty()) layer.channels.remove(key)
    }

    fun keyAt(layer: Layer, key: String, t: Float): Keyframe? {
        val ch = layer.channels[key] ?: return null
        return ch.keys.firstOrNull { abs(it.t - t) < 0.03f }
    }

    fun allKeyTimes(layer: Layer): List<Float> {
        val set = LinkedHashSet<Float>()
        for (ch in layer.channels.values) for (k in ch.keys) set.add(round(k.t * 1000f) / 1000f)
        val list = ArrayList(set)
        list.sort()
        return list
    }

    fun hasKeys(layer: Layer): Boolean = layer.channels.values.any { it.keys.isNotEmpty() }

    fun clearAll(layer: Layer) {
        layer.channels.clear()
    }

    // ---------------- preset ----------------
    val PRESETS = listOf("fade", "slideUp", "zoom", "spin", "bounce", "typing", "kenBurns", "glitch")
    val PRESET_LABELS = mapOf(
        "fade" to "Fade", "slideUp" to "Slide Up", "zoom" to "Zoom Pop", "spin" to "Spin In",
        "bounce" to "Bounce In", "typing" to "Typing", "kenBurns" to "Ken Burns", "glitch" to "Glitch"
    )

    /** Terapkan preset di sekitar playhead (kecuali kenBurns di seluruh durasi layer). */
    fun applyPreset(layer: Layer, preset: String, playhead: Float, projectDuration: Float): Boolean {
        val start = layer.start
        val end = layer.start + layer.dur
        var a = playhead.coerceIn(start, max(start, end - 0.25f))
        var b = min(end, a + 0.7f)
        if (b - a < 0.22f) b = min(end, a + 0.22f)

        fun reset(vararg keys: String) {
            for (k in keys) layer.channels.remove(k)
        }

        when (preset) {
            "fade" -> {
                reset("alpha")
                setKey(layer, "alpha", a, 0f, "easeOut")
                setKey(layer, "alpha", b, layer.alpha, "easeOut")
            }
            "slideUp" -> {
                reset("alpha", "y")
                setKey(layer, "alpha", a, 0f, "easeOut")
                setKey(layer, "alpha", b, layer.alpha, "easeOut")
                setKey(layer, "y", a, layer.y + 180f, "easeOut")
                setKey(layer, "y", b, layer.y, "easeOut")
            }
            "zoom" -> {
                reset("scale", "alpha")
                setKey(layer, "alpha", a, 0f, "easeOut")
                setKey(layer, "alpha", b, layer.alpha, "easeOut")
                setKey(layer, "scale", a, 0.2f, "back")
                setKey(layer, "scale", b, layer.scale, "easeOut")
            }
            "spin" -> {
                reset("rot", "scale", "alpha")
                setKey(layer, "alpha", a, 0f, "easeOut")
                setKey(layer, "alpha", b, layer.alpha, "easeOut")
                setKey(layer, "rot", a, layer.rot - 260f, "easeOut")
                setKey(layer, "rot", b, layer.rot, "easeOut")
                setKey(layer, "scale", a, 0.15f, "easeOut")
                setKey(layer, "scale", b, layer.scale, "easeOut")
            }
            "bounce" -> {
                reset("y", "alpha", "scale")
                val bb = min(end, a + max(0.6f, min(1f, layer.dur * 0.55f)))
                setKey(layer, "alpha", a, 0f, "easeOut")
                setKey(layer, "alpha", min(bb, a + 0.18f), layer.alpha, "linear")
                setKey(layer, "y", a, layer.y + 260f, "back")
                setKey(layer, "y", bb, layer.y, "easeOut")
                setKey(layer, "scale", a, 0.82f, "easeOut")
                setKey(layer, "scale", bb, layer.scale, "easeOut")
            }
            "typing" -> {
                if (layer.kind != KIND_TEXT) return false
                reset("typing")
                val bb = min(end, a + max(0.8f, min(2.2f, layer.dur * 0.7f)))
                setKey(layer, "typing", a, 0f, "linear")
                setKey(layer, "typing", bb, 1f, "linear")
            }
            "kenBurns" -> {
                if (layer.kind != KIND_IMAGE) return false
                reset("scale")
                setKey(layer, "scale", start, layer.scale, "easeInOut")
                setKey(layer, "scale", end, layer.scale * 1.22f, "linear")
            }
            "glitch" -> {
                reset("x", "alpha")
                val step = 0.045f
                setKey(layer, "x", a, layer.x, "linear")
                for (i in 1..6) {
                    val jitter = (if (i % 2 == 1) 1 else -1) * (30f - i * 4f)
                    setKey(layer, "x", round((a + step * i) * 1000f) / 1000f, layer.x + jitter, "linear")
                }
                setKey(layer, "x", round((a + step * 7) * 1000f) / 1000f, layer.x, "easeOut")
                setKey(layer, "alpha", a, 1f, "linear")
                setKey(layer, "alpha", round((a + 0.06f) * 1000f) / 1000f, 0.35f, "linear")
                setKey(layer, "alpha", round((a + 0.14f) * 1000f) / 1000f, 1f, "linear")
                setKey(layer, "alpha", round((a + 0.2f) * 1000f) / 1000f, 0.6f, "linear")
                setKey(layer, "alpha", round((a + 0.3f) * 1000f) / 1000f, layer.alpha, "easeOut")
            }
            else -> return false
        }
        return true
    }
}
