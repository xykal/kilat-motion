package com.kilat.motion.core.render

import com.kilat.motion.core.model.*
import com.kilat.motion.core.animation.Anim
import com.kilat.motion.core.vector.SvgPath

import android.graphics.Bitmap
import android.graphics.Shader
import android.graphics.RadialGradient
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Matrix
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renderer project ke Canvas apa pun: preview (View) maupun export (MediaCodec surface).
 * Semua koordinat dalam satuan project (mis. 1080x1920); canvas di-scale oleh pemanggil.
 */
/** Efek yang ditimpa di atas konten (SRC_ATOP, hanya pada piksel konten). */
private val OVERLAY_EFFECTS = setOf("vignette", "scanlines", "grain", "lightleak")

object Renderer {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val camera = Camera()
    private val camMatrix = Matrix()
    private val maskPath = Path()
    private val meshCache = HashMap<String, Obj3D.Mesh?>()
    private val path = Path()
    private val rectF = RectF()
    private val blurCache = LinkedHashMap<String, Bitmap>()
    private val typefaceCache = HashMap<String, Typeface>()
    private val fxOverlay = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fxLayerPaint = Paint()
    private val fxBounds = RectF()
    private var grainTile: Bitmap? = null

    private fun typefaceFor(key: String): Typeface = typefaceCache.getOrPut(key) {
        try {
            when (key) {
                "sans" -> Typeface.create("sans-serif", Typeface.NORMAL)
                "sans_black" -> Typeface.create("sans-serif-black", Typeface.BOLD)
                "serif" -> Typeface.create("serif", Typeface.NORMAL)
                "serif_bold" -> Typeface.create("serif", Typeface.BOLD)
                "mono" -> Typeface.create("monospace", Typeface.NORMAL)
                "condensed" -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
                "casual" -> Typeface.create("casual", Typeface.NORMAL)
                "cursive" -> Typeface.create("cursive", Typeface.NORMAL)
                else -> Typeface.create("sans-serif", Typeface.NORMAL)
            }
        } catch (e: Throwable) {
            Typeface.DEFAULT
        }
    }

    private fun blendXfer(mode: String): PorterDuffXfermode? = when (mode) {
        "screen" -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        "multiply" -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        "overlay" -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        "lighten" -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        "darken" -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
        "colorDodge" -> PorterDuffXfermode(PorterDuff.Mode.ADD)
        "softLight" -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        else -> null
    }

    /** Filter warna publik (dipakai thumbnail preset filter di Inspector). */
    fun filterFor(brightness: Float, contrast: Float, saturate: Float, hue: Float): ColorMatrixColorFilter {
        val m = HashMap<String, Float>()
        m["fx.brightness"] = brightness
        m["fx.contrast"] = contrast
        m["fx.saturate"] = saturate
        m["fx.hue"] = hue
        return colorFilterFor(m)
    }

    private fun colorFilterFor(fx: HashMap<String, Float>): ColorMatrixColorFilter {
        val sat = fx["fx.saturate"] ?: 100f
        val contrast = (fx["fx.contrast"] ?: 100f) / 100f
        val bright = ((fx["fx.brightness"] ?: 100f) - 100f) * 2.55f
        val hue = fx["fx.hue"] ?: 0f

        val cm = ColorMatrix()
        cm.setSaturation(sat / 100f)
        if (abs(hue) > 0.1f) {
            val rad = Math.toRadians(hue.toDouble())
            val cosA = Math.cos(rad).toFloat()
            val sinA = Math.sin(rad).toFloat()
            val lr = 0.213f
            val lg = 0.715f
            val lb = 0.072f
            val hueM = ColorMatrix(
                floatArrayOf(
                    lr + cosA * (1 - lr) + sinA * (-lr), lg + cosA * (-lg) + sinA * (-lg), lb + cosA * (-lb) + sinA * (1 - lb), 0f, 0f,
                    lr + cosA * (-lr) + sinA * 0.143f, lg + cosA * (1 - lg) + sinA * 0.140f, lb + cosA * (-lb) + sinA * (-0.283f), 0f, 0f,
                    lr + cosA * (-lr) + sinA * (-(1 - lr)), lg + cosA * (-lg) + sinA * lg, lb + cosA * (1 - lb) + sinA * lb, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(hueM)
        }
        val t = (1f - contrast) * 127.5f + bright
        val bm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(bm)
        return ColorMatrixColorFilter(cm)
    }

    /** Gambar seluruh project pada waktu t. Canvas dianggap sudah di-scale ke ukuran project. */
    fun renderFrame(canvas: Canvas, project: Project, t: Float, withBackground: Boolean = true) {
        if (withBackground) canvas.drawColor(project.bg)
        for (layer in project.layers) {
            if (layer.hidden) continue
            drawLayer(canvas, layer, t)
        }
    }

    fun drawLayer(canvas: Canvas, layer: Layer, t: Float) {
        if (t < layer.start - 0.0001f || t > layer.start + layer.dur + 0.0001f) return
        val fx = Anim.evalLayer(layer, t)
        val alpha = (fx["alpha"] ?: 1f).coerceIn(0f, 1f)
        if (alpha <= 0.004f) return

        val blurPx = fx["fx.blur"] ?: 0f
        val glow = fx["fx.glow"] ?: 0f

        fillPaint.reset()
        fillPaint.isAntiAlias = true
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        glowPaint.reset()
        glowPaint.isAntiAlias = true
        shadowPaint.reset()
        shadowPaint.isAntiAlias = true

        val cf = colorFilterFor(fx)

        canvas.save()
        canvas.translate(fx["x"] ?: layer.x, fx["y"] ?: layer.y)
        // Kemiringan 3D (perspektif Camera) untuk layer 2D. Objek 3D diputar
        // langsung oleh proyektor mesh, jadi tidak boleh ditimpa Camera dua kali.
        val rX = (fx["rotX"] ?: 0f).coerceIn(-80f, 80f)
        val rY = (fx["rotY"] ?: 0f).coerceIn(-80f, 80f)
        if (layer.kind != KIND_3D && (abs(rX) > 0.1f || abs(rY) > 0.1f)) {
            camera.save()
            camera.setLocation(0f, 0f, -14f)
            camera.rotateX(rX)
            camera.rotateY(rY)
            camera.getMatrix(camMatrix)
            camera.restore()
            canvas.concat(camMatrix)
        }
        canvas.rotate(fx["rot"] ?: 0f)
        val s = (fx["scale"] ?: 1f).coerceIn(0.01f, 20f)
        val sX = (fx["scaleX"] ?: 1f).coerceIn(-20f, 20f)
        val sY = (fx["scaleY"] ?: 1f).coerceIn(-20f, 20f)
        canvas.scale(s * sX, s * sY)
        val skX = (fx["skewX"] ?: 0f).coerceIn(-2f, 2f)
        val skY = (fx["skewY"] ?: 0f).coerceIn(-2f, 2f)
        if (abs(skX) > 0.001f || abs(skY) > 0.001f) canvas.skew(skX, skY)

        // bayangan (siluet blur bergeser di belakang konten)
        if (layer.shadowBlur > 0.2f) {
            shadowPaint.reset()
            shadowPaint.isAntiAlias = true
            shadowPaint.colorFilter = PorterDuffColorFilter(layer.shadowColor, PorterDuff.Mode.SRC_IN)
            shadowPaint.alpha = (alpha * 150f).toInt().coerceIn(0, 255)
            shadowPaint.maskFilter = BlurMaskFilter(max(0.6f, layer.shadowBlur), BlurMaskFilter.Blur.NORMAL)
            canvas.save()
            canvas.translate(layer.shadowDx, layer.shadowDy)
            drawContent(canvas, layer, fx, shadowPaint, shadowPaint, isGlow = true)
            canvas.restore()
        }

        // glow pass: siluet blur berwarna di belakang konten (semua jenis layer)
        if (glow > 0.5f) {
            glowPaint.colorFilter = PorterDuffColorFilter(layer.glowColor, PorterDuff.Mode.SRC_IN)
            glowPaint.alpha = (alpha * 235f).toInt().coerceIn(0, 255)
            val r = max(0.6f, glow * 0.9f)
            glowPaint.maskFilter = BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL)
            drawContent(canvas, layer, fx, glowPaint, glowPaint, isGlow = true)
        }

        fillPaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        strokePaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        fillPaint.colorFilter = cf
        strokePaint.colorFilter = cf
        fillPaint.xfermode = blendXfer(layer.blend)

        if (blurPx > 0.05f && layer.kind != KIND_IMAGE) {
            val r = max(0.6f, blurPx * 0.7f)
            fillPaint.maskFilter = BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL)
            strokePaint.maskFilter = BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL)
        }

        // masking bentuk di ruang lokal layer
        if (layer.maskShape != "none") {
            buildMaskPath(layer)
            if (layer.maskInvert) canvas.clipOutPath(maskPath) else canvas.clipPath(maskPath)
        }

        drawWithEffects(canvas, layer, fx, t, fillPaint, strokePaint)
        canvas.restore()
    }

    // ---------------- EFEK (stack per layer) ----------------

    /**
     * Terapkan stack efek di ruang lokal layer. Efek yang butuh offscreen
     * (tint, rgb split, overlay) memakai saveLayer. Glitch cukup memotong
     * konten per irisan horizontal. Urutan tampil = urutan di [Layer.effects].
     */
    private fun drawWithEffects(canvas: Canvas, layer: Layer, fx: HashMap<String, Float>, t: Float, fp: Paint, sp: Paint) {
        val active = layer.effects.filter { it.enabled && it.amount > 0.001f }
        if (active.isEmpty()) {
            drawContent(canvas, layer, fx, fp, sp, isGlow = false)
            return
        }
        val (cw, ch) = contentDims(layer)
        val pad = max(cw, ch) * 0.5f + 60f
        fxBounds.set(-cw / 2f - pad, -ch / 2f - pad, cw / 2f + pad, ch / 2f + pad)

        val glitch = active.firstOrNull { it.type == "glitch" }
        val rgb = active.firstOrNull { it.type == "rgbsplit" }
        val tint = active.firstOrNull { it.type == "tint" }
        val overlays = active.filter { it.type in OVERLAY_EFFECTS }
        val needsLayer = rgb != null || tint != null || overlays.isNotEmpty()

        if (needsLayer) {
            // Mode blend & alpha layer dipindah ke saveLayer supaya tetap berlaku.
            fxLayerPaint.reset()
            fxLayerPaint.alpha = fp.alpha
            fxLayerPaint.xfermode = fp.xfermode
            fp.alpha = 255
            sp.alpha = 255
            fp.xfermode = null
            canvas.saveLayer(fxBounds, fxLayerPaint)
        }

        when {
            glitch != null -> drawGlitch(canvas, layer, fx, fp, sp, glitch, t, cw)
            rgb != null -> drawRgbSplit(canvas, layer, fx, fp, sp, rgb)
            else -> drawContent(canvas, layer, fx, fp, sp, isGlow = false)
        }

        if (tint != null) {
            fxOverlay.reset()
            fxOverlay.isAntiAlias = true
            fxOverlay.color = withAlpha(tint.color, (tint.amount * 255f).toInt())
            fxOverlay.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            canvas.drawRect(fxBounds, fxOverlay)
        }
        for (e in overlays) drawOverlay(canvas, e, cw, ch, t)

        if (needsLayer) canvas.restore()
    }

    /** Irisan horizontal yang digeser acak, berubah 12 kali per detik. */
    private fun drawGlitch(canvas: Canvas, layer: Layer, fx: HashMap<String, Float>, fp: Paint, sp: Paint, e: LayerEffect, t: Float, cw: Float) {
        val bands = (3f + e.size * 9f).toInt().coerceIn(3, 12)
        val seed = floor(t * 12f).toInt()
        val h = fxBounds.height() / bands
        val maxShift = e.amount * 0.14f * cw
        for (i in 0 until bands) {
            val top = fxBounds.top + i * h
            val pick = hash01(seed * 31 + i)
            val dx = if (pick < 0.4f) 0f else (hash01(seed * 17 + i * 7 + 3) * 2f - 1f) * maxShift
            canvas.save()
            canvas.clipRect(fxBounds.left, top, fxBounds.right, top + h + 0.5f)
            canvas.translate(dx, 0f)
            drawContent(canvas, layer, fx, fp, sp, isGlow = false)
            canvas.restore()
        }
    }

    /** Pisah kanal R/G/B dengan offset horizontal, digabung aditif. */
    private fun drawRgbSplit(canvas: Canvas, layer: Layer, fx: HashMap<String, Float>, fp: Paint, sp: Paint, e: LayerEffect) {
        val d = e.amount * 36f * (0.5f + e.size)
        val channels = listOf(
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f) to -d,
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f) to 0f,
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f) to d
        )
        for ((m, dx) in channels) {
            val pf = Paint(fp)
            pf.colorFilter = ColorMatrixColorFilter(ColorMatrix(m))
            pf.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            pf.alpha = 255
            val ps = Paint(sp)
            ps.colorFilter = pf.colorFilter
            ps.xfermode = pf.xfermode
            ps.alpha = 255
            canvas.save()
            canvas.translate(dx, 0f)
            drawContent(canvas, layer, fx, pf, ps, isGlow = false)
            canvas.restore()
        }
    }

    private fun drawOverlay(canvas: Canvas, e: LayerEffect, cw: Float, ch: Float, t: Float) {
        fxOverlay.reset()
        fxOverlay.isAntiAlias = true
        fxOverlay.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        val a = (e.amount * 255f).toInt().coerceIn(0, 255)
        when (e.type) {
            "vignette" -> {
                val r = max(1f, hypot(cw, ch) / 2f * (0.35f + 0.65f * (1f - e.size)))
                fxOverlay.shader = RadialGradient(
                    0f, 0f, r,
                    intArrayOf(withAlpha(e.color, 0), withAlpha(e.color, a)),
                    floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP
                )
                canvas.drawRect(fxBounds, fxOverlay)
            }
            "lightleak" -> {
                val cx = -cw / 2f + e.size * cw
                val cy = -ch / 2f
                fxOverlay.shader = RadialGradient(
                    cx, cy, max(cw, ch) * 0.9f,
                    intArrayOf(withAlpha(e.color, a), withAlpha(e.color, 0)),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                )
                canvas.drawRect(fxBounds, fxOverlay)
            }
            "scanlines" -> {
                val step = 4f + e.size * 16f
                val thick = step * 0.45f
                fxOverlay.color = withAlpha(0xFF000000.toInt(), a)
                var y = fxBounds.top
                while (y < fxBounds.bottom) {
                    canvas.drawRect(fxBounds.left, y, fxBounds.right, min(fxBounds.bottom, y + thick), fxOverlay)
                    y += step
                }
            }
            "grain" -> {
                val tile = grainBitmap()
                val shader = android.graphics.BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                val seed = floor(t * 24f).toInt()
                val m = Matrix()
                m.setTranslate(hash01(seed) * tile.width, hash01(seed + 7) * tile.height)
                shader.setLocalMatrix(m)
                fxOverlay.shader = shader
                fxOverlay.alpha = a
                canvas.drawRect(fxBounds, fxOverlay)
            }
        }
    }

    private fun grainBitmap(): Bitmap {
        grainTile?.let { return it }
        val n = 96
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        for (y in 0 until n) for (x in 0 until n) {
            val v = (hash01(x * 97 + y * 131 + 5) * 255f).toInt()
            b.setPixel(x, y, Color.argb(70, v, v, v))
        }
        grainTile = b
        return b
    }

    private fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    /** Hash deterministik ke 0..1 (dipakai efek acak yang stabil per frame). */
    private fun hash01(n: Int): Float {
        var x = n * 374761393 + 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0x7fffffff).toFloat() / 2147483647f
    }

    private fun drawContent(canvas: Canvas, layer: Layer, fx: HashMap<String, Float>, fp: Paint, sp: Paint, isGlow: Boolean) {

        when (layer.kind) {
            KIND_TEXT -> drawTextLayer(canvas, layer, fx, fp, sp, isGlow)
            KIND_SHAPE -> drawShapeLayer(canvas, layer, fp, sp, isGlow)
            KIND_IMAGE -> drawImageLayer(canvas, layer, fx, fp)
            KIND_VECTOR -> drawVectorLayer(canvas, layer, fp, sp, isGlow)
            KIND_SVG -> drawSvgLayer(canvas, layer, fp, sp, isGlow)
            KIND_3D -> drawObj3DLayer(canvas, layer, fx, fp, sp, isGlow)
        }
    }

    // ---------------- TEXT ----------------
    private fun drawTextLayer(canvas: Canvas, layer: Layer, fx: HashMap<String, Float>, fp: Paint, sp: Paint, isGlow: Boolean) {
        val size = max(4f, layer.textSize)
        fp.textSize = size
        fp.typeface = typefaceFor(layer.fontKey)
        sp.textSize = size
        sp.typeface = fp.typeface
        fp.style = Paint.Style.FILL
        sp.style = Paint.Style.STROKE
        fp.letterSpacing = layer.letterSpacing
        sp.letterSpacing = layer.letterSpacing
        sp.strokeWidth = max(0f, layer.strokeW)
        if (isGlow) {
            fp.color = Color.WHITE // ditint oleh colorFilter SRC_IN pada pass siluet
        } else {
            fp.color = layer.color
            sp.color = layer.strokeColor
        }

        val lines = layer.text.split("\n")
        if (lines.isEmpty()) return
        val lineH = size * layer.lineSpacing.coerceIn(0.8f, 2f)
        var maxW = 0f
        val widths = FloatArray(lines.size)
        for (i in lines.indices) {
            widths[i] = fp.measureText(lines[i])
            if (widths[i] > maxW) maxW = widths[i]
        }
        val totalH = max(lineH, lines.size * lineH)
        layer.lastW = max(10f, maxW)
        layer.lastH = totalH

        val alignment = when (layer.align) {
            "left" -> Paint.Align.LEFT
            "right" -> Paint.Align.RIGHT
            else -> Paint.Align.CENTER
        }
        fp.textAlign = alignment
        sp.textAlign = alignment
        val anchorX = when (layer.align) {
            "left" -> -maxW / 2f
            "right" -> maxW / 2f
            else -> 0f
        }

        // typing reveal
        val typing = (fx["typing"] ?: 1f).coerceIn(0f, 1f)
        val fullLen = lines.fold(0) { acc, s -> acc + s.length } + max(0, lines.size - 1)
        var remaining = if (typing >= 0.999f) Int.MAX_VALUE else Math.round(typing * fullLen).toInt()

        val ascent = fp.fontMetrics.ascent
        val descent = fp.fontMetrics.descent
        var lastLineIdx = 0
        var lastShown = ""
        for (i in lines.indices) {
            var line = lines[i]
            if (remaining != Int.MAX_VALUE) {
                if (remaining <= 0) break
                if (line.length > remaining) line = line.substring(0, remaining)
                remaining -= lines[i].length
                if (remaining > 0) remaining -= 1 // newline
            }
            lastLineIdx = i
            lastShown = line
            if (line.isEmpty()) continue
            val baseline = -totalH / 2f + lineH * i + lineH / 2f - (ascent + descent) / 2f
            if (layer.strokeW > 0f && !isGlow) canvas.drawText(line, anchorX, baseline, sp)
            canvas.drawText(line, anchorX, baseline, fp)
        }

        // caret saat typing
        if (typing > 0.001f && typing < 0.999f && !isGlow) {
            val baseline = -totalH / 2f + lineH * lastLineIdx + lineH / 2f - (ascent + descent) / 2f
            val w = fp.measureText(lastShown)
            val cx = when (layer.align) {
                "left" -> anchorX + w
                "right" -> anchorX - w
                else -> -maxW / 2f + w
            }
            if (System.currentTimeMillis() / 420 % 2 == 0L) {
                val cp = Paint(Paint.ANTI_ALIAS_FLAG)
                cp.color = layer.color
                cp.alpha = fp.alpha
                canvas.drawRect(cx + size * 0.04f, baseline + ascent, cx + size * 0.04f + size * 0.07f, baseline + descent, cp)
            }
        }
    }

    // ---------------- SHAPE ----------------
    private fun drawShapeLayer(canvas: Canvas, layer: Layer, fp: Paint, sp: Paint, isGlow: Boolean) {
        val w = max(1f, layer.w)
        val h = max(1f, layer.h)
        layer.lastW = w
        layer.lastH = h
        path.reset()
        when (layer.shape) {
            "circle" -> {
                rectF.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
                path.addOval(rectF, Path.Direction.CW)
            }
            "triangle" -> {
                path.moveTo(0f, -h / 2f)
                path.lineTo(w / 2f, h / 2f)
                path.lineTo(-w / 2f, h / 2f)
                path.close()
            }
            "star" -> {
                val outer = min(w, h) / 2f
                val inner = outer / 2.3f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outer else inner
                    val ang = -Math.PI / 2 + i * Math.PI / 5
                    val px = (Math.cos(ang) * r).toFloat()
                    val py = (Math.sin(ang) * r).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            "diamond" -> {
                path.moveTo(0f, -h / 2f); path.lineTo(w / 2f, 0f); path.lineTo(0f, h / 2f); path.lineTo(-w / 2f, 0f); path.close()
            }
            "hexagon", "pentagon" -> {
                val sides = if (layer.shape == "hexagon") 6 else 5
                for (i in 0 until sides) {
                    val ang = -Math.PI / 2 + i * 2 * Math.PI / sides
                    val px = (Math.cos(ang) * w / 2f).toFloat()
                    val py = (Math.sin(ang) * h / 2f).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            "heart" -> {
                val hw = w / 2f; val hh = h / 2f
                path.moveTo(0f, hh * 0.9f)
                path.cubicTo(-hw * 1.3f, hh * 0.1f, -hw * 0.9f, -hh * 1.0f, 0f, -hh * 0.35f)
                path.cubicTo(hw * 0.9f, -hh * 1.0f, hw * 1.3f, hh * 0.1f, 0f, hh * 0.9f)
                path.close()
            }
            "arrow" -> {
                val hw = w / 2f; val hh = h / 2f; val sh = hh * 0.5f
                path.moveTo(-hw, -sh); path.lineTo(0f, -sh); path.lineTo(0f, -hh); path.lineTo(hw, 0f)
                path.lineTo(0f, hh); path.lineTo(0f, sh); path.lineTo(-hw, sh); path.close()
            }
            "cross" -> {
                val bx = w * 0.17f; val by = h * 0.17f
                val hw = w / 2f; val hh = h / 2f
                path.moveTo(-bx, -hh); path.lineTo(bx, -hh); path.lineTo(bx, -by); path.lineTo(hw, -by)
                path.lineTo(hw, by); path.lineTo(bx, by); path.lineTo(bx, hh); path.lineTo(-bx, hh)
                path.lineTo(-bx, by); path.lineTo(-hw, by); path.lineTo(-hw, -by); path.lineTo(-bx, -by); path.close()
            }
            "polygon" -> {
                val sides = layer.shapeSides.coerceIn(3, 24)
                for (i in 0 until sides) {
                    val ang = -Math.PI / 2 + i * 2 * Math.PI / sides
                    val px = (Math.cos(ang) * w / 2f).toFloat()
                    val py = (Math.sin(ang) * h / 2f).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            else -> {
                rectF.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
                val r = min(layer.radius, min(w, h) / 2f)
                if (r > 0.5f) path.addRoundRect(rectF, r, r, Path.Direction.CW)
                else path.addRect(rectF, Path.Direction.CW)
            }
        }
        if (isGlow) {
            fp.style = Paint.Style.FILL
            fp.color = Color.WHITE
            canvas.drawPath(path, fp)
            return
        }
        fp.style = Paint.Style.FILL
        fp.color = layer.fill
        canvas.drawPath(path, fp)
        if (layer.shapeStrokeW > 0.5f) {
            sp.style = Paint.Style.STROKE
            sp.strokeWidth = layer.shapeStrokeW
            sp.color = layer.shapeStrokeColor
            canvas.drawPath(path, sp)
        }
    }

    // ---------------- IMAGE ----------------
    private fun drawImageLayer(canvas: Canvas, layer: Layer, fx: HashMap<String, Float>, fp: Paint) {
        val w = max(1f, layer.imgW)
        val h = max(1f, layer.imgH)
        layer.lastW = w
        layer.lastH = h
        val bmp = layer.ensureBitmap()
        if (bmp == null) {
            fp.style = Paint.Style.FILL
            fp.color = 0xFF1B2230.toInt()
            rectF.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
            canvas.drawRoundRect(rectF, 24f, 24f, fp)
            fp.color = 0xFF5B6B85.toInt()
            fp.textSize = min(w, h) * 0.12f
            fp.textAlign = Paint.Align.CENTER
            canvas.drawText("GAMBAR", 0f, 0f, fp)
            return
        }
        val blur = fx["fx.blur"] ?: 0f
        val src = if (blur > 0.5f) blurredBitmap(layer, bmp, blur) else bmp
        rectF.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        fp.isFilterBitmap = true
        canvas.drawBitmap(src, null, rectF, fp)
    }

    private fun blurredBitmap(layer: Layer, src: Bitmap, radius: Float): Bitmap {
        // kunci ikut ukuran & generasi pixel: blur harus ikut berubah saat konten/keyframe/resolusi berubah
        val key = layer.id + "_" + radius.toInt() + "_" + src.width + "x" + src.height + "_" + src.generationId
        blurCache[key]?.let { if (!it.isRecycled) return it }
        if (src.width < 4 || src.height < 4) return src
        val factor = (1f + radius / 3f).coerceIn(1f, 24f)
        val tw = max(2, (src.width / factor).toInt())
        val th = max(2, (src.height / factor).toInt())
        val small = try {
            Bitmap.createScaledBitmap(src, tw, th, true)
        } catch (e: Throwable) {
            return src
        }
        val pad = max(1, (radius / 2f).toInt())
        val padded = try {
            Bitmap.createBitmap(tw + pad * 2, th + pad * 2, Bitmap.Config.ARGB_8888)
        } catch (e: Throwable) {
            return small
        }
        val c = Canvas(padded)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        p.alpha = 255
        // tepi di-blur lembut: gambar beberapa offset transparan
        val base = Paint(Paint.FILTER_BITMAP_FLAG)
        base.alpha = 90
        for (dx in -pad..pad step max(1, pad / 3)) {
            for (dy in -pad..pad step max(1, pad / 3)) {
                c.drawBitmap(small, (pad + dx).toFloat(), (pad + dy).toFloat(), base)
            }
        }
        c.drawBitmap(small, pad.toFloat(), pad.toFloat(), p)
        if (blurCache.size > 12) {
            val it = blurCache.keys.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
        blurCache[key] = padded
        return padded
    }

    fun invalidateBlurCache() {
        blurCache.clear()
    }

    // ---------------- MASK ----------------
    private fun buildMaskPath(layer: Layer) {
        val dims = contentDims(layer)
        val cw = dims.first
        val chh = dims.second
        maskPath.reset()
        when (layer.maskShape) {
            "circle" -> {
                rectF.set(-cw / 2f, -chh / 2f, cw / 2f, chh / 2f)
                maskPath.addOval(rectF, Path.Direction.CW)
            }
            "star" -> {
                val outer = min(cw, chh) / 2f
                val inner = outer / 2.3f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outer else inner
                    val ang = -Math.PI / 2 + i * Math.PI / 5
                    val px = (Math.cos(ang) * r).toFloat()
                    val py = (Math.sin(ang) * r).toFloat()
                    if (i == 0) maskPath.moveTo(px, py) else maskPath.lineTo(px, py)
                }
                maskPath.close()
            }
            else -> { // rrect
                rectF.set(-cw / 2f, -chh / 2f, cw / 2f, chh / 2f)
                val r = min(cw, chh) * 0.16f
                maskPath.addRoundRect(rectF, r, r, Path.Direction.CW)
            }
        }
    }

    private fun contentDims(layer: Layer): Pair<Float, Float> = when (layer.kind) {
        KIND_TEXT -> {
            fillPaint.textSize = max(4f, layer.textSize)
            fillPaint.typeface = typefaceFor(layer.fontKey)
            var mw = 10f
            val lines = layer.text.split("\n")
            for (ln in lines) mw = max(mw, fillPaint.measureText(ln))
            Pair(mw, max(layer.textSize * 1.24f, lines.size * layer.textSize * 1.24f))
        }
        KIND_IMAGE -> Pair(max(1f, layer.imgW), max(1f, layer.imgH))
        else -> Pair(max(1f, layer.w), max(1f, layer.h))
    }

    // ---------------- VECTOR (coretan bebas) ----------------
    fun vectorPathOf(layer: Layer): Path {
        val cached = layer.vecPath
        if (cached != null && layer.vecSrc == layer.vecPoints) return cached
        val pts = parseVecPoints(layer.vecPoints)
        val built = buildSmoothPath(pts, layer.vecClosed)
        layer.vecPath = built
        layer.vecSrc = layer.vecPoints
        return built
    }

    fun parseVecPoints(s: String): FloatArray {
        if (s.isBlank()) return FloatArray(0)
        val parts = s.split(';')
        val out = FloatArray(parts.size * 2)
        var n = 0
        for (part in parts) {
            val xy = part.split(',')
            if (xy.size != 2) continue
            val px = xy[0].trim().toFloatOrNull() ?: continue
            val py = xy[1].trim().toFloatOrNull() ?: continue
            out[n++] = px
            out[n++] = py
        }
        return if (n == out.size) out else out.copyOf(n)
    }

    fun buildSmoothPath(pts: FloatArray, closed: Boolean): Path {
        val built = Path()
        val count = pts.size / 2
        if (count == 0) return built
        if (count == 1) {
            built.addCircle(pts[0], pts[1], 1.5f, Path.Direction.CW)
            return built
        }
        built.moveTo(pts[0], pts[1])
        for (i in 1 until count - 1) {
            val mx = (pts[i * 2] + pts[(i + 1) * 2]) / 2f
            val my = (pts[i * 2 + 1] + pts[(i + 1) * 2 + 1]) / 2f
            built.quadTo(pts[i * 2], pts[i * 2 + 1], mx, my)
        }
        built.lineTo(pts[(count - 1) * 2], pts[(count - 1) * 2 + 1])
        if (closed) built.close()
        return built
    }

    private fun drawVectorLayer(canvas: Canvas, layer: Layer, fp: Paint, sp: Paint, isGlow: Boolean) {
        layer.lastW = max(2f, layer.w)
        layer.lastH = max(2f, layer.h)
        val pth = vectorPathOf(layer)
        if (layer.vecClosed) {
            fp.style = Paint.Style.FILL
            fp.color = if (isGlow) Color.WHITE else layer.fill
            canvas.drawPath(pth, fp)
        }
        sp.style = Paint.Style.STROKE
        sp.strokeCap = Paint.Cap.ROUND
        sp.strokeJoin = Paint.Join.ROUND
        sp.strokeWidth = max(0.5f, layer.vecStrokeW)
        sp.color = if (isGlow) Color.WHITE else layer.color
        canvas.drawPath(pth, sp)
    }

    // ---------------- SVG ----------------
    private fun svgPathOf(layer: Layer): Path {
        val cached = layer.svgPath
        if (cached != null && layer.svgSrc == layer.svgData) return cached
        val built = Path()
        for (op in SvgPath.parse(layer.svgData)) {
            when (op) {
                is SvgPath.Op.Move -> built.moveTo(op.x, op.y)
                is SvgPath.Op.Line -> built.lineTo(op.x, op.y)
                is SvgPath.Op.Cubic -> built.cubicTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
                is SvgPath.Op.Quad -> built.quadTo(op.x1, op.y1, op.x, op.y)
                is SvgPath.Op.Close -> built.close()
            }
        }
        layer.svgPath = built
        layer.svgSrc = layer.svgData
        return built
    }

    private fun drawSvgLayer(canvas: Canvas, layer: Layer, fp: Paint, sp: Paint, isGlow: Boolean) {
        val tw = max(2f, layer.w)
        val th = max(2f, layer.h)
        layer.lastW = tw
        layer.lastH = th
        val src = svgPathOf(layer)
        val sc = min(tw / max(1f, layer.svgVbW), th / max(1f, layer.svgVbH))
        canvas.save()
        canvas.translate(-(layer.svgVbX + layer.svgVbW / 2f) * sc, -(layer.svgVbY + layer.svgVbH / 2f) * sc)
        canvas.scale(sc, sc)
        fp.style = Paint.Style.FILL
        fp.color = if (isGlow) Color.WHITE else layer.fill
        canvas.drawPath(src, fp)
        if (layer.shapeStrokeW > 0.5f && !isGlow) {
            sp.style = Paint.Style.STROKE
            sp.strokeCap = Paint.Cap.ROUND
            sp.strokeJoin = Paint.Join.ROUND
            sp.strokeWidth = layer.shapeStrokeW / max(0.01f, sc)
            sp.color = layer.shapeStrokeColor
            canvas.drawPath(src, sp)
        }
        canvas.restore()
    }

    // ---------------- OBJEK 3D ----------------
    private fun meshFor(layer: Layer): Obj3D.Mesh? {
        if (layer.objKind != "custom") return Obj3D.primitive(layer.objKind)
        val pathKey = layer.objPath
        if (pathKey.isEmpty()) return Obj3D.primitive("cube")
        meshCache[pathKey]?.let { return it }
        if (meshCache.containsKey(pathKey)) return Obj3D.primitive("cube")
        val parsed = try {
            val f = File(pathKey)
            if (f.exists()) Obj3D.parseObj(f.readText()) else null
        } catch (e: Throwable) {
            null
        }
        meshCache[pathKey] = parsed
        return parsed ?: Obj3D.primitive("cube")
    }

    private fun shadeColor(base: Int, f: Float): Int {
        val ff = f.coerceIn(0f, 1.15f)
        fun ch(c: Int) = (c * ff).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch((base shr 16) and 0xFF) shl 16) or
            (ch((base shr 8) and 0xFF) shl 8) or ch(base and 0xFF)
    }

    private fun drawObj3DLayer(canvas: Canvas, layer: Layer, fx: HashMap<String, Float>, fp: Paint, sp: Paint, isGlow: Boolean) {
        val size = max(4f, min(layer.w, layer.h))
        layer.lastW = max(2f, layer.w)
        layer.lastH = max(2f, layer.h)
        val mesh = meshFor(layer) ?: return
        val ax = Math.toRadians(((fx["rotX"] ?: 0f).toDouble()))
        val ay = Math.toRadians(((fx["rotY"] ?: 0f).toDouble()))
        val cxr = cos(ax).toFloat(); val sxr = sin(ax).toFloat()
        val cyr = cos(ay).toFloat(); val syr = sin(ay).toFloat()
        val n = mesh.verts.size
        val xs = FloatArray(n); val ys = FloatArray(n); val zs = FloatArray(n)
        val persp = 3.2f
        for (i in 0 until n) {
            val v = mesh.verts[i]
            // rotasi Y lalu X
            var vx = v[0] * cyr + v[2] * syr
            var vz = -v[0] * syr + v[2] * cyr
            var vy = v[1]
            var wch = 0f
            wch = vy
            vy = wch * cxr - vz * sxr
            vz = wch * sxr + vz * cxr
            val denom = persp + vz
            val pr = if (denom > 0.2f) persp / denom else persp / 0.2f
            xs[i] = vx * (size / 2f) * pr
            ys[i] = vy * (size / 2f) * pr
            zs[i] = vz
            @Suppress("UNUSED_EXPRESSION") vx
        }
        // urutkan face dari terjauh (z besar) ke terdekat
        val order = mesh.faces.indices.sortedByDescending { fi ->
            var sum = 0f
            for (vi in mesh.faces[fi]) sum += zs[vi]
            sum
        }
        // arah cahaya tetap
        val lx = -0.45f; val ly = -0.65f; val lz = -0.6f
        fp.style = Paint.Style.FILL
        for (fi in order) {
            val face = mesh.faces[fi]
            if (face.size < 3) continue
            val a = face[0]; val b = face[1]; val c = face[2]
            // normal dari ruang layar 3D (x,y,z hasil rotasi belum diproyeksikan penuh - pakai zs + posisi asli terotasi)
            val va = mesh.verts[a]; val vb = mesh.verts[b]; val vc = mesh.verts[c]
            fun rotP(v: FloatArray): FloatArray {
                val rx1 = v[0] * cyr + v[2] * syr
                var rz1 = -v[0] * syr + v[2] * cyr
                val ry1 = v[1] * cxr - rz1 * sxr
                rz1 = v[1] * sxr + rz1 * cxr
                return floatArrayOf(rx1, ry1, rz1)
            }
            val ra = rotP(va); val rb = rotP(vb); val rc = rotP(vc)
            val ux = rb[0] - ra[0]; val uy = rb[1] - ra[1]; val uz = rb[2] - ra[2]
            val vx2 = rc[0] - ra[0]; val vy2 = rc[1] - ra[1]; val vz2 = rc[2] - ra[2]
            var nx = uy * vz2 - uz * vy2
            var ny = uz * vx2 - ux * vz2
            var nz = ux * vy2 - uy * vx2
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            var shade = 0.75f
            if (len > 0.0001f) {
                nx /= len; ny /= len; nz /= len
                if (nz > 0f) { nx = -nx; ny = -ny; nz = -nz } // hadapkan normal ke kamera
                val dot = nx * lx + ny * ly + nz * lz
                shade = 0.38f + 0.62f * max(0f, dot)
            }
            path.reset()
            path.moveTo(xs[a], ys[a])
            for (k in 1 until face.size) path.lineTo(xs[face[k]], ys[face[k]])
            path.close()
            fp.color = if (isGlow) Color.WHITE else shadeColor(layer.fill, shade)
            canvas.drawPath(path, fp)
        }
    }
}
