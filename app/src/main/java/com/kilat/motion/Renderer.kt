package com.kilat.motion

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Renderer project ke Canvas apa pun: preview (View) maupun export (MediaCodec surface).
 * Semua koordinat dalam satuan project (mis. 1080x1920); canvas di-scale oleh pemanggil.
 */
object Renderer {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rectF = RectF()
    private val blurCache = LinkedHashMap<String, Bitmap>()
    private val typefaceCache = HashMap<String, Typeface>()

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

        val cf = colorFilterFor(fx)

        canvas.save()
        canvas.translate(fx["x"] ?: layer.x, fx["y"] ?: layer.y)
        canvas.rotate(fx["rot"] ?: 0f)
        val s = (fx["scale"] ?: 1f).coerceIn(0.01f, 20f)
        canvas.scale(s, s)

        // glow pass (teks & bentuk): gambar versi blur berwarna glow di belakang
        if (glow > 0.5f && layer.kind != KIND_IMAGE) {
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

        drawContent(canvas, layer, fx, fillPaint, strokePaint, isGlow = false)
        canvas.restore()
    }

    private fun drawContent(canvas: Canvas, layer: Layer, fx: HashMap<String, Float>, fp: Paint, sp: Paint, isGlow: Boolean) {
        when (layer.kind) {
            KIND_TEXT -> drawTextLayer(canvas, layer, fx, fp, sp, isGlow)
            KIND_SHAPE -> drawShapeLayer(canvas, layer, fp, sp, isGlow)
            KIND_IMAGE -> drawImageLayer(canvas, layer, fx, fp)
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
        sp.strokeWidth = max(0f, layer.strokeW)

        val lines = layer.text.split("\n")
        if (lines.isEmpty()) return
        val lineH = size * 1.24f
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
            canvas.drawText("🖼 gambar", 0f, 0f, fp)
            return
        }
        val blur = fx["fx.blur"] ?: 0f
        val src = if (blur > 0.5f) blurredBitmap(layer, bmp, blur) else bmp
        rectF.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        fp.isFilterBitmap = true
        canvas.drawBitmap(src, null, rectF, fp)
    }

    private fun blurredBitmap(layer: Layer, src: Bitmap, radius: Float): Bitmap {
        val key = layer.id + "_" + radius.toInt()
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
}
