package com.fluxa.motion.core.render

import com.fluxa.motion.core.model.*
import com.fluxa.motion.core.animation.Anim
import com.fluxa.motion.core.vector.SvgPath

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
import com.fluxa.motion.core.video.VideoFrames
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renderer project ke Canvas apa pun: preview (View) maupun export (MediaCodec surface).
 * Semua koordinat dalam satuan project (mis. 1080x1920); canvas di-scale oleh pemanggil.
 */
/** Efek yang ditimpa di atas konten (SRC_ATOP, hanya pada piksel konten). */
private val OVERLAY_EFFECTS = EffectDraw.OVERLAY_TYPES

class Renderer(
    /** Sumber frame video. UI memakai VideoFrames.ui; export memberi instance sendiri. */
    val frames: VideoFrames = VideoFrames.ui,
    /** Sisi panjang maksimum frame video. Preview 960; export menyesuaikan ukuran output. */
    var videoMaxLong: Int = 960,
) {

    /**
     * Instance bersama untuk thread UI (preview, thumbnail, inspector, layer preview).
     * Hanya dipakai dari main thread. Export TIDAK boleh memakai instance ini: ia membuat Renderer() sendiri,
     * karena scratch state di dalam class (Paint, Path, Matrix, cache) tidak aman dipakai dari dua thread.
     */
    companion object {
        val ui: Renderer by lazy { Renderer() }

        fun renderFrame(canvas: Canvas, project: Project, t: Float, withBackground: Boolean = true) =
            ui.renderFrame(canvas, project, t, withBackground)

        fun drawLayer(canvas: Canvas, layer: Layer, t: Float) = ui.drawLayer(canvas, layer, t)

        fun filterFor(brightness: Float, contrast: Float, saturate: Float, hue: Float): ColorMatrixColorFilter =
            ui.filterFor(brightness, contrast, saturate, hue)

        fun invalidateBlurCache() = ui.invalidateBlurCache()
    }

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
    // accessOrder=true: eviksi membuang entri yang paling lama tidak dipakai (LRU), bukan yang paling lama dibuat
    private val blurCache = LinkedHashMap<String, Bitmap>(16, 0.75f, true)
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

    private fun blendXfer(mode: String): PorterDuffXfermode? = BlendFx.xfer(mode)

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
    /**
     * Ganti warna Paint tanpa menghapus alpha yang sudah disetel (opacity layer).
     * Paint.color = X menimpa alpha dengan alpha warna X, sehingga opacity layer hilang.
     */
    private fun Paint.rgbKeepAlpha(c: Int) {
        val a = this.alpha
        this.color = c
        this.alpha = (Color.alpha(c) * a + 127) / 255
    }

    fun renderFrame(canvas: Canvas, project: Project, t: Float, withBackground: Boolean = true) {
        val needsBackdrop = project.layers.any { !it.hidden && it.fxGlass > 0.5f }
        if (!needsBackdrop) {
            if (withBackground) canvas.drawColor(project.bg)
            for (layer in project.layers) {
                if (layer.hidden) continue
                drawLayer(canvas, layer, t)
            }
            return
        }
        // Mode kaca: frame disusun dulu di bitmap kerja supaya layer glass bisa membaca latar di bawahnya.
        // Bitmap diisi warna latar project; Exporter juga menggambar warna yang sama.
        val w = canvas.width
        val h = canvas.height
        if (w <= 0 || h <= 0) return
        val base = Matrix()
        canvas.getMatrix(base)
        val work = if (reuseWork) {
            workCache?.takeIf { !it.isRecycled && it.width == w && it.height == h }
                ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    workCache?.recycle()
                    workCache = it
                }
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        val wc = Canvas(work)
        // SRC: menimpa seluruh bitmap, penting karena bitmap bisa dipakai ulang dan latar bisa semi-transparan
        wc.drawColor(project.bg, PorterDuff.Mode.SRC)
        wc.setMatrix(base)
        val prevBack = backdrop
        val prevBase = baseMatrix
        backdrop = work
        baseMatrix = base
        try {
            for (layer in project.layers) {
                if (layer.hidden) continue
                drawLayer(wc, layer, t)
            }
        } finally {
            backdrop = prevBack
            baseMatrix = prevBase
        }
        canvas.save()
        canvas.setMatrix(null)
        canvas.drawBitmap(work, 0f, 0f, null)
        canvas.restore()
        if (!reuseWork) work.recycle()
    }

    fun drawLayer(canvas: Canvas, layer: Layer, t: Float) {
        if (t < layer.start - 0.0001f || t > layer.start + layer.dur + 0.0001f) return
        curT = t
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
        val rX = fx["rotX"] ?: 0f
        val rY = fx["rotY"] ?: 0f
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
        // Transform tanpa batas. Hanya nol yang dicegah agar matriks tidak mengempis.
        val s = (fx["scale"] ?: 1f).let { if (it > -0.0001f && it < 0.0001f) 0.0001f else it }
        val sX = fx["scaleX"] ?: 1f
        val sY = fx["scaleY"] ?: 1f
        canvas.scale(s * sX, s * sY)
        val skX = fx["skewX"] ?: 0f
        val skY = fx["skewY"] ?: 0f
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

        if (layer.fxGlass > 0.5f) drawGlassBackdrop(canvas, layer, alpha)
        if (layer.blurType != "none" && layer.blurAmount > 0.5f) drawBlurredLayer(canvas, layer, fx, t, fillPaint, strokePaint)
        else drawWithEffects(canvas, layer, fx, t, fillPaint, strokePaint)
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
        val colorFx = active.filter { it.type in EffectDraw.COLOR_TYPES }
        val colorMatrix = EffectDraw.composeColor(colorFx, t)
        val shake = active.firstOrNull { it.type == "shake" }
        val needsLayer = rgb != null || tint != null || overlays.isNotEmpty() || colorMatrix != null

        if (needsLayer) {
            // Mode blend & alpha layer dipindah ke saveLayer supaya tetap berlaku.
            fxLayerPaint.reset()
            fxLayerPaint.alpha = fp.alpha
            fxLayerPaint.xfermode = fp.xfermode
            fxLayerPaint.colorFilter = colorMatrix?.let { ColorMatrixColorFilter(ColorMatrix(it)) }
            fp.alpha = 255
            sp.alpha = 255
            fp.xfermode = null
            canvas.saveLayer(fxBounds, fxLayerPaint)
        }

        // Shake menggeser konten saja (bukan overlay), dengan save/restore supaya stack tetap seimbang.
        val shakeOff = shake?.let { EffectDraw.shakeOffset(it, cw, t) }
        if (shakeOff != null) {
            canvas.save()
            canvas.translate(shakeOff.first, shakeOff.second)
        }
        when {
            glitch != null -> drawGlitch(canvas, layer, fx, fp, sp, glitch, t, cw)
            rgb != null -> drawRgbSplit(canvas, layer, fx, fp, sp, rgb)
            else -> drawContent(canvas, layer, fx, fp, sp, isGlow = false)
        }
        if (shakeOff != null) canvas.restore()

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
        EffectDraw.drawOverlay(canvas, fxOverlay, e, fxBounds, cw, ch, t) { grainBitmap() }
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
            KIND_VIDEO -> drawVideoLayer(canvas, layer, fx, fp)
            KIND_VECTOR -> drawVectorLayer(canvas, layer, fp, sp, isGlow)
            KIND_SVG -> drawSvgLayer(canvas, layer, fp, sp, isGlow)
            KIND_3D -> drawObj3DLayer(canvas, layer, fx, fp, sp, isGlow)
        }
    }

    private var curT = 0f

    /** Bitmap frame yang sedang disusun (mode kaca) dan matriks dasar project di dalamnya. */
    private var backdrop: Bitmap? = null
    /**
     * true = pakai ulang satu bitmap kerja (mode kaca) antar frame. Aman HANYA kalau canvas tujuan
     * software (export: Surface.lockCanvas), karena drawBitmap menyalin piksel saat itu juga.
     * Preview (HWUI) tetap false.
     */
    var reuseWork = false
    private var workCache: Bitmap? = null
    private var baseMatrix: Matrix? = null
    private val glassTintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val GLASS_CORNER = 24f

    private fun drawVideoLayer(canvas: Canvas, layer: Layer, fx: HashMap<String, Float>, fp: Paint) {
        val w = max(1f, layer.imgW)
        val h = max(1f, layer.imgH)
        layer.lastW = w
        layer.lastH = h
        rectF.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        val frame = frames.frameAt(layer.imgPath, max(0f, curT - layer.start), videoMaxLong)
        if (frame == null) {
            fp.style = Paint.Style.FILL
            fp.rgbKeepAlpha(0xFF1B2230.toInt())
            canvas.drawRoundRect(rectF, 24f, 24f, fp)
            fp.rgbKeepAlpha(0xFF5B6B85.toInt())
            fp.textSize = min(w, h) * 0.12f
            fp.textAlign = Paint.Align.CENTER
            canvas.drawText("VIDEO", 0f, 0f, fp)
            return
        }
        fp.isFilterBitmap = true
        canvas.drawBitmap(frame, null, rectF, fp)
    }

    /**
     * Layer dirender ke offscreen, lalu diblur dengan jenis yang dipilih, dan ditempel kembali.
     * Ukuran offscreen dibatasi BLUR_MAX_SIDE agar memori dan waktu render tetap wajar.
     */
    private fun drawBlurredLayer(canvas: Canvas, layer: Layer, fx: HashMap<String, Float>, t: Float, fp: Paint, sp: Paint) {
        val r = layer.blurAmount.coerceAtLeast(0f) * 0.6f
        val (cw, ch) = contentDims(layer)
        val pad = max(cw, ch) * 0.5f + r * 1.5f + 40f
        val bw = cw + pad * 2f
        val bh = ch + pad * 2f
        val s = min(1f, EffectMath.BLUR_MAX_SIDE / max(bw, bh))
        val ow = max(2, (bw * s).toInt())
        val oh = max(2, (bh * s).toInt())
        val off = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        off.eraseColor(0)
        val oc = Canvas(off)
        oc.scale(s, s)
        oc.translate(bw / 2f, bh / 2f)
        val innerFill = Paint(fp).apply { alpha = 255; xfermode = null }
        val innerStroke = Paint(sp).apply { alpha = 255; xfermode = null }
        drawWithEffects(oc, layer, fx, t, innerFill, innerStroke)
        val out = BlurOps.apply(off, layer.blurType, r * s)
        canvas.save()
        canvas.translate(-bw / 2f, -bh / 2f)
        canvas.scale(1f / s, 1f / s)
        val dp = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = fp.alpha; xfermode = fp.xfermode }
        canvas.drawBitmap(out, 0f, 0f, dp)
        canvas.restore()
        if (out !== off) out.recycle()
        off.recycle()
    }

    /**
     * Panel kaca: latar di bawah layer diblur (hanya latar, bukan konten layer), lalu diberi tint putih
     * dan border tipis. Dipanggil di dalam transform layer, jadi mengikuti posisi, rotasi, dan skala.
     */
    private fun drawGlassBackdrop(canvas: Canvas, layer: Layer, alpha: Float) {
        val src = backdrop ?: return
        val base = baseMatrix ?: return
        val (cw, ch) = contentDims(layer)
        val hw = cw / 2f
        val hh = ch / 2f
        rectF.set(-hw, -hh, hw, hh)
        val bv = FloatArray(9)
        base.getValues(bv)
        val k = sqrt(abs(bv[0] * bv[4] - bv[1] * bv[3])).coerceAtLeast(0.0001f)
        val devM = Matrix()
        canvas.getMatrix(devM)
        val devPath = Path()
        devPath.addRoundRect(rectF, GLASS_CORNER, GLASS_CORNER, Path.Direction.CW)
        devPath.transform(devM)

        val f = EffectMath.glassDownscale(src.width, src.height)
        val sw = max(1, (src.width * f).toInt())
        val sh = max(1, (src.height * f).toInt())
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val blurred = BlurOps.apply(small, "gauss", layer.fxGlass * k * f)

        canvas.save()
        canvas.setMatrix(null)
        canvas.clipPath(devPath)
        val bp = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = (255f * alpha).toInt().coerceIn(0, 255) }
        canvas.drawBitmap(blurred, null, RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), bp)
        canvas.restore()
        if (blurred !== small) blurred.recycle()
        small.recycle()

        val tintA = (EffectMath.glassTintAlpha(layer.glassTint) * alpha).toInt().coerceIn(0, 255)
        if (tintA > 0) {
            glassTintPaint.reset()
            glassTintPaint.isAntiAlias = true
            glassTintPaint.color = Color.argb(tintA, 255, 255, 255)
            canvas.drawRoundRect(rectF, GLASS_CORNER, GLASS_CORNER, glassTintPaint)
        }
        if (layer.glassBorder > 0.05f) {
            glassBorderPaint.reset()
            glassBorderPaint.isAntiAlias = true
            glassBorderPaint.style = Paint.Style.STROKE
            glassBorderPaint.strokeWidth = layer.glassBorder
            glassBorderPaint.color = Color.argb((90f * alpha).toInt().coerceIn(0, 255), 255, 255, 255)
            val inset = layer.glassBorder / 2f
            rectF.set(-hw + inset, -hh + inset, hw - inset, hh - inset)
            canvas.drawRoundRect(rectF, GLASS_CORNER, GLASS_CORNER, glassBorderPaint)
            rectF.set(-hw, -hh, hw, hh)
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
            fp.rgbKeepAlpha(Color.WHITE) // ditint oleh colorFilter SRC_IN pada pass siluet
        } else {
            fp.rgbKeepAlpha(layer.color)
            sp.rgbKeepAlpha(layer.strokeColor)
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
                cp.rgbKeepAlpha(layer.color)
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
        ShapeGeom.build(layer.shape, w, h, layer.shapeSides, layer.radius, path)
        if (isGlow) {
            fp.style = Paint.Style.FILL
            fp.rgbKeepAlpha(Color.WHITE)
            canvas.drawPath(path, fp)
            return
        }
        fp.style = Paint.Style.FILL
        // isi media (gambar atau frame video) di dalam bentuk, dengan cover-crop di kotak bentuk
        if (!isGlow && layer.fillMode != "color") {
            val bmp = fillBitmapFor(layer)
            if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                val s = max(w / bmp.width, h / bmp.height)
                val m = Matrix()
                m.setScale(s, s)
                m.postTranslate(-bmp.width * s / 2f, -bmp.height * s / 2f)
                fp.shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            }
        }
        fp.rgbKeepAlpha(layer.fill)
        canvas.drawPath(path, fp)
        fp.shader = null
        if (layer.shapeStrokeW > 0.5f) {
            sp.style = Paint.Style.STROKE
            sp.strokeWidth = layer.shapeStrokeW
            sp.rgbKeepAlpha(layer.shapeStrokeColor)
            canvas.drawPath(path, sp)
        }
    }

    private fun fillBitmapFor(layer: Layer): Bitmap? =
        if (layer.fillMode == "video") frames.frameAt(layer.fillPath, max(0f, curT - layer.start), videoMaxLong)
        else layer.ensureFillBitmap()

    // ---------------- IMAGE ----------------
    private fun drawImageLayer(canvas: Canvas, layer: Layer, fx: HashMap<String, Float>, fp: Paint) {
        val w = max(1f, layer.imgW)
        val h = max(1f, layer.imgH)
        layer.lastW = w
        layer.lastH = h
        val bmp = layer.ensureBitmap()
        if (bmp == null) {
            fp.style = Paint.Style.FILL
            fp.rgbKeepAlpha(0xFF1B2230.toInt())
            rectF.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
            canvas.drawRoundRect(rectF, 24f, 24f, fp)
            fp.rgbKeepAlpha(0xFF5B6B85.toInt())
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
        KIND_IMAGE, KIND_VIDEO -> Pair(max(1f, layer.imgW), max(1f, layer.imgH))
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
            fp.rgbKeepAlpha(if (isGlow) Color.WHITE else layer.fill)
            canvas.drawPath(pth, fp)
        }
        sp.style = Paint.Style.STROKE
        sp.strokeCap = Paint.Cap.ROUND
        sp.strokeJoin = Paint.Join.ROUND
        sp.strokeWidth = max(0.5f, layer.vecStrokeW)
        sp.rgbKeepAlpha(if (isGlow) Color.WHITE else layer.color)
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
        fp.rgbKeepAlpha(if (isGlow) Color.WHITE else layer.fill)
        canvas.drawPath(src, fp)
        if (layer.shapeStrokeW > 0.5f && !isGlow) {
            sp.style = Paint.Style.STROKE
            sp.strokeCap = Paint.Cap.ROUND
            sp.strokeJoin = Paint.Join.ROUND
            sp.strokeWidth = layer.shapeStrokeW / max(0.01f, sc)
            sp.rgbKeepAlpha(layer.shapeStrokeColor)
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
            fp.rgbKeepAlpha(if (isGlow) Color.WHITE else shadeColor(layer.fill, shade))
            canvas.drawPath(path, fp)
        }
    }
}
