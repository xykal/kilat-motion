package com.kilat.motion.core.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.Locale
import java.util.UUID

const val KIND_TEXT = "text"
const val KIND_SHAPE = "shape"
const val KIND_IMAGE = "image"
const val KIND_VECTOR = "vector"
const val KIND_SVG = "svg"
const val KIND_3D = "obj3d"

/** Semua properti yang bisa dianimasikan. */
val PROP_KEYS = listOf(
    "x", "y", "scale", "scaleX", "scaleY", "skewX", "skewY",
    "rot", "rotX", "rotY", "alpha", "typing",
    "fx.blur", "fx.brightness", "fx.contrast", "fx.saturate", "fx.hue", "fx.glow"
)

val BLEND_MODES = listOf(
    "normal", "screen", "multiply", "overlay", "lighten", "darken", "colorDodge", "softLight"
)

val BLEND_LABELS = mapOf(
    "normal" to "Normal", "screen" to "Screen", "multiply" to "Multiply",
    "overlay" to "Overlay", "lighten" to "Lighten", "darken" to "Darken",
    "colorDodge" to "Color Dodge", "softLight" to "Soft Light"
)

val SHAPE_TYPES = listOf("rect", "circle", "triangle", "star", "polygon", "diamond", "hexagon", "pentagon", "heart", "arrow", "cross")
val SHAPE_LABELS = mapOf(
    "rect" to "Kotak", "circle" to "Lingkaran", "triangle" to "Segitiga", "star" to "Bintang", "polygon" to "Segi Banyak",
    "diamond" to "Diamond", "hexagon" to "Heksagon", "pentagon" to "Pentagon", "heart" to "Hati", "arrow" to "Panah", "cross" to "Silang"
)

val FONT_KEYS = listOf(
    "sans", "sans_black", "serif", "serif_bold", "mono", "condensed", "casual", "cursive"
)
val FONT_LABELS = mapOf(
    "sans" to "Sans", "sans_black" to "Sans Black", "serif" to "Serif", "serif_bold" to "Serif Bold",
    "mono" to "Mono", "condensed" to "Condensed", "casual" to "Casual", "cursive" to "Cursive"
)

/**
 * Efek per layer (stack, urutan = urutan tampil). Parameter umum:
 * [amount] 0..1 = kekuatan, [size] 0..1 = ukuran/jarak, [color] = warna bila relevan.
 */
class LayerEffect(
    var type: String = "vignette",
    var amount: Float = 0.5f,
    var size: Float = 0.5f,
    var color: Int = 0xFF000000.toInt(),
    var enabled: Boolean = true
) {
    fun copy(): LayerEffect = LayerEffect(type, amount, size, color, enabled)
}

/** Jenis efek yang didukung renderer. */
val EFFECT_TYPES = listOf(
    "vignette", "scanlines", "grain", "lightleak", "tint", "glitch", "rgbsplit"
)
val EFFECT_LABELS = mapOf(
    "vignette" to "Vignette",
    "scanlines" to "Scanlines",
    "grain" to "Film grain",
    "lightleak" to "Light leak",
    "tint" to "Tint warna",
    "glitch" to "Glitch slice",
    "rgbsplit" to "RGB split"
)
/** Efek yang memakai warna. */
val EFFECT_USES_COLOR = setOf("vignette", "lightleak", "tint")

class Keyframe(var t: Float = 0f, var v: Float = 0f, var ease: String = "easeOut")

class Channel(val keys: MutableList<Keyframe> = ArrayList())

class Layer {
    var id: String = UUID.randomUUID().toString().substring(0, 8)
    var name: String = "Layer"
    var kind: String = KIND_TEXT
    var start: Float = 0f
    var dur: Float = 4f
    var hidden: Boolean = false
    var locked: Boolean = false

    // transform
    var x: Float = 540f
    var y: Float = 960f
    var scale: Float = 1f
    var scaleX: Float = 1f
    var scaleY: Float = 1f
    var skewX: Float = 0f
    var skewY: Float = 0f
    var rot: Float = 0f
    var rotX: Float = 0f   // kemiringan 3D (derajat)
    var rotY: Float = 0f
    var alpha: Float = 1f
    var typing: Float = 1f

    // gaya: bayangan + masking
    var shadowBlur: Float = 0f
    var shadowDx: Float = 0f
    var shadowDy: Float = 12f
    var shadowColor: Int = 0xFF000000.toInt()
    var maskShape: String = "none"   // none | circle | rrect | star
    var maskInvert: Boolean = false

    // kurva easing custom (cubic bezier) untuk keyframe berease "custom"
    var bezX1: Float = 0.3f
    var bezY1: Float = 1.4f
    var bezX2: Float = 0.6f
    var bezY2: Float = 1f

    // vector drawing (coretan bebas): titik relatif pusat "x,y;x,y;..."
    var vecPoints: String = ""
    var vecStrokeW: Float = 14f
    var vecClosed: Boolean = false

    // svg: data path + viewBox
    var svgData: String = ""
    var svgVbX: Float = 0f
    var svgVbY: Float = 0f
    var svgVbW: Float = 100f
    var svgVbH: Float = 100f

    // objek 3D: primitif atau file .obj hasil import
    var objKind: String = "cube"    // cube | pyramid | prism | octa | custom
    var objPath: String = ""

    // text
    var text: String = "TEKS KEREN"
    var fontKey: String = "sans_black"
    var textSize: Float = 120f
    var bold: Boolean = true
    var align: String = "center"
    var color: Int = 0xFFFFFFFF.toInt()
    var strokeW: Float = 0f
    var strokeColor: Int = 0xFF000000.toInt()
    var letterSpacing: Float = 0f   // em (Paint.letterSpacing)
    var lineSpacing: Float = 1.24f  // pengali tinggi baris

    // shape
    var shape: String = "rect"
    var w: Float = 320f
    var h: Float = 320f
    var radius: Float = 0f
    var fill: Int = 0xFF2EE6C0.toInt()
    var shapeSides: Int = 6         // jumlah sisi untuk shape "polygon"
    var shapeStrokeW: Float = 0f
    var shapeStrokeColor: Int = 0xFFFFFFFF.toInt()

    // image
    var imgPath: String = ""
    var imgW: Float = 480f
    var imgH: Float = 480f

    // effects
    var fxBlur: Float = 0f
    var fxBright: Float = 100f
    var fxContrast: Float = 100f
    var fxSaturate: Float = 100f
    var fxHue: Float = 0f
    var fxGlow: Float = 0f
    var glowColor: Int = 0xFF2EE6C0.toInt()
    var blend: String = "normal"
    /** Stack efek (renderer menerapkan sesuai urutan). */
    val effects: MutableList<LayerEffect> = ArrayList()

    // animation channels: propKey -> Channel
    val channels: MutableMap<String, Channel> = LinkedHashMap()

    // ---------- runtime only (jangan disimpan) ----------
    @Transient
    var bitmap: Bitmap? = null
    @Transient
    var lastW: Float = 300f
    @Transient
    var lastH: Float = 120f
    @Transient
    var ev: HashMap<String, Float> = HashMap()
    @Transient
    var vecPath: android.graphics.Path? = null
    @Transient
    var vecSrc: String = ""
    @Transient
    var svgPath: android.graphics.Path? = null
    @Transient
    var svgSrc: String = ""

    fun contentW(): Float = if (lastW > 1f) lastW else 300f
    fun contentH(): Float = if (lastH > 1f) lastH else 120f

    fun ensureBitmap(): Bitmap? {
        val b = bitmap
        if (b != null && !b.isRecycled) return b
        if (imgPath.isEmpty()) return null
        val f = File(imgPath)
        if (!f.exists()) return null
        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        BitmapFactory.decodeFile(f.absolutePath, opts)
        var sample = 1
        val maxDim = maxOf(opts.outWidth, opts.outHeight)
        while (maxDim / sample > 2048) sample *= 2
        val decodeOpts = BitmapFactory.Options()
        decodeOpts.inSampleSize = sample
        val decoded = try {
            BitmapFactory.decodeFile(f.absolutePath, decodeOpts)
        } catch (e: Throwable) {
            null
        }
        bitmap = decoded
        return decoded
    }

    // ---------- nilai statis properti ----------
    fun getStatic(key: String): Float = when (key) {
        "x" -> x
        "y" -> y
        "scale" -> scale
        "scaleX" -> scaleX
        "scaleY" -> scaleY
        "skewX" -> skewX
        "skewY" -> skewY
        "rot" -> rot
        "rotX" -> rotX
        "rotY" -> rotY
        "alpha" -> alpha
        "typing" -> typing
        "fx.blur" -> fxBlur
        "fx.brightness" -> fxBright
        "fx.contrast" -> fxContrast
        "fx.saturate" -> fxSaturate
        "fx.hue" -> fxHue
        "fx.glow" -> fxGlow
        else -> 0f
    }

    fun setStatic(key: String, v: Float) {
        when (key) {
            "x" -> x = v
            "y" -> y = v
            "scale" -> scale = v
            "scaleX" -> scaleX = v
            "scaleY" -> scaleY = v
            "skewX" -> skewX = v
            "skewY" -> skewY = v
            "rot" -> rot = v
            "rotX" -> rotX = v
            "rotY" -> rotY = v
            "alpha" -> alpha = v
            "typing" -> typing = v
            "fx.blur" -> fxBlur = v
            "fx.brightness" -> fxBright = v
            "fx.contrast" -> fxContrast = v
            "fx.saturate" -> fxSaturate = v
            "fx.hue" -> fxHue = v
            "fx.glow" -> fxGlow = v
        }
    }

    fun defaultOf(key: String): Float = when (key) {
        "scale", "scaleX", "scaleY" -> 1f
        "alpha" -> 1f
        "typing" -> 1f
        "fx.brightness" -> 100f
        "fx.contrast" -> 100f
        "fx.saturate" -> 100f
        else -> 0f
    }

    fun duplicate(): Layer {
        val c = Layer()
        c.id = UUID.randomUUID().toString().substring(0, 8)
        c.name = name + " copy"
        c.kind = kind
        c.start = start
        c.dur = dur
        c.hidden = hidden
        c.locked = locked
        c.x = x + 40f
        c.y = y + 30f
        c.scale = scale
        c.scaleX = scaleX
        c.scaleY = scaleY
        c.skewX = skewX
        c.skewY = skewY
        c.rot = rot
        c.rotX = rotX
        c.rotY = rotY
        c.shadowBlur = shadowBlur
        c.shadowDx = shadowDx
        c.shadowDy = shadowDy
        c.shadowColor = shadowColor
        c.maskShape = maskShape
        c.maskInvert = maskInvert
        c.bezX1 = bezX1; c.bezY1 = bezY1; c.bezX2 = bezX2; c.bezY2 = bezY2
        c.vecPoints = vecPoints
        c.vecStrokeW = vecStrokeW
        c.vecClosed = vecClosed
        c.svgData = svgData
        c.svgVbX = svgVbX; c.svgVbY = svgVbY; c.svgVbW = svgVbW; c.svgVbH = svgVbH
        c.objKind = objKind
        c.objPath = objPath
        c.alpha = alpha
        c.typing = typing
        c.text = text
        c.fontKey = fontKey
        c.textSize = textSize
        c.bold = bold
        c.align = align
        c.color = color
        c.strokeW = strokeW
        c.letterSpacing = letterSpacing
        c.lineSpacing = lineSpacing
        c.strokeColor = strokeColor
        c.shape = shape
        c.w = w
        c.h = h
        c.radius = radius
        c.fill = fill
        c.shapeSides = shapeSides
        c.shapeStrokeW = shapeStrokeW
        c.shapeStrokeColor = shapeStrokeColor
        c.imgPath = imgPath
        c.imgW = imgW
        c.imgH = imgH
        c.fxBlur = fxBlur
        c.fxBright = fxBright
        c.fxContrast = fxContrast
        c.fxSaturate = fxSaturate
        c.fxHue = fxHue
        c.fxGlow = fxGlow
        c.glowColor = glowColor
        c.blend = blend
        for ((k, ch) in channels) {
            val n = Channel()
            for (kf in ch.keys) n.keys.add(Keyframe(kf.t, kf.v, kf.ease))
            c.channels[k] = n
        }
        for (e in effects) c.effects.add(e.copy())
        return c
    }
}

class Music {
    var path: String = ""
    var name: String = "Musik"
    var start: Float = 0f      // kapan mulai di timeline (detik)
    var dur: Float = 10f       // berapa lama tampil
    var offset: Float = 0f     // mulai dari detik berapa di file
    var volume: Float = 0.8f   // preview (export pakai volume asli file)
    var wave: FloatArray? = null // envelope waveform (runtime + disimpan)
}

class Project {
    var id: String = UUID.randomUUID().toString().substring(0, 12)
    var name: String = "Project Baru"
    var updatedAt: Long = System.currentTimeMillis()
    var width: Int = 1080
    var height: Int = 1920
    var bg: Int = 0xFF05070C.toInt()
    var duration: Float = 10f
    var fps: Int = 30
    val layers: MutableList<Layer> = ArrayList()
    var music: Music? = null

    fun findLayer(id: String?): Layer? {
        if (id == null) return null
        for (l in layers) if (l.id == id) return l
        return null
    }

    fun clampLayer(l: Layer) {
        if (l.start < 0f) l.start = 0f
        if (l.start > duration - 0.2f) l.start = maxOf(0f, duration - 0.2f)
        if (l.dur < 0.2f) l.dur = 0.2f
        if (l.start + l.dur > duration) l.dur = duration - l.start
    }

    fun newLayerIndex(layer: Layer): Int = layers.indexOf(layer)
}

object Format {
    fun time(t: Float): String {
        val tt = maxOf(0f, t)
        val m = (tt / 60f).toInt()
        val s = tt - m * 60f
        return String.format(Locale.US, "%02d:%05.2f", m, s)
    }

    fun color(c: Int): String = String.format(Locale.US, "#%08X", c)

    /** Format desimal titik (bukan koma) di semua locale perangkat. */
    fun dec(v: Float, digits: Int = 1): String = String.format(Locale.US, "%." + digits + "f", v)

    fun parseColor(hex: String?, def: Int): Int {
        if (hex == null) return def
        return try {
            when {
                hex.length == 9 && hex.startsWith("#") -> hex.substring(1).toLong(16).toInt()
                hex.length == 7 && hex.startsWith("#") -> (0xFF000000L or hex.substring(1).toLong(16)).toInt()
                else -> def
            }
        } catch (e: Throwable) {
            def
        }
    }
}
