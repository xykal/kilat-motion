package com.fluxa.motion.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import androidx.core.graphics.PathParser
import com.fluxa.motion.ui.icons.PhosphorPaths
import com.fluxa.motion.ui.theme.FluxaColors
import kotlin.math.min

/**
 * Ikon FLUXA. Gaya: SOLID (Phosphor Fill), konsisten di seluruh app.
 * Sumber path: Phosphor Icons (MIT), lihat ui/icons/PhosphorPaths.kt.
 * Ikon digambar dari path data yang sama dengan VectorDrawable, lalu di-cache
 * sebagai android.graphics.Path. Tidak ada emoji dan tidak ada glyph teks.
 */
enum class FluxaIcon {
    BOLT, HOME, FOLDER, LAYERS, USER, PLUS,
    TEXT, SHAPE, IMAGE, SLIDERS, EXPORT, PLAY, PAUSE, LOOP, RESET,
    MUSIC, SETTINGS, SAVE, TRASH, COPY, CLOSE, BACK, NEXT,
    CHEVRON_DOWN, CHEVRON_RIGHT, MORE, KEYFRAME, EYE, EYE_OFF,
    CHECK, SPARK, FILM, CLOCK, RATIO, BOOK, INFO, GRID, STAR, SHARE, WAND,
    UNDO, REDO, SCISSORS, LAYER_UP, LAYER_DOWN, MAGNET, SKIP_START, CROSSHAIR, MINUS, PENCIL, VECTOR, CUBE, CURVE, LOCK, UNLOCK,
    TRANSFORM, ANIMATE, COLOR, STYLE
}

object IconPainter {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cache = HashMap<FluxaIcon, Path>()

    private fun pathOf(icon: FluxaIcon): Path =
        cache.getOrPut(icon) { PathParser.createPathFromPathData(PhosphorPaths.data.getValue(icon)) }

    /**
     * Gambar ikon [icon] di kotak [size] x [size] mulai dari ([x], [y]).
     * [c2] dipertahankan untuk kompatibilitas pemanggil lama; ikon solid
     * hanya memakai satu warna.
     */
    fun draw(canvas: Canvas, icon: FluxaIcon, x: Float, y: Float, size: Float, c1: Int, c2: Int? = null) {
        fill.color = c1
        val s = size / PhosphorPaths.VIEWPORT
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(s, s)
        canvas.drawPath(pathOf(icon), fill)
        canvas.restore()
    }
}

/** View ikon custom. Ikon digambar presisi di tengah area view. */
class IconView(
    context: Context,
    icon: FluxaIcon = FluxaIcon.BOLT,
    tint: Int = FluxaColors.Text,
    tint2: Int? = null
) : View(context) {

    var icon: FluxaIcon = icon
        set(v) { field = v; contentDescription = FluxaIconLabels.of(v); invalidate() }
    var tintColor: Int = tint
        set(v) { field = v; invalidate() }
    var tintColor2: Int? = tint2
        set(v) { field = v; invalidate() }
    /** Napas ikon dari tepi view (fraksi ukuran, 0..0.3). Konsisten di semua ikon. */
    var iconInset: Float = 0.08f
        set(v) { field = v; invalidate() }

    init {
        contentDescription = FluxaIconLabels.of(icon)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom
        if (w <= 0 || h <= 0) return
        val size = min(w, h) * (1f - iconInset * 2f)
        val x = paddingLeft + (w - size) / 2f
        val y = paddingTop + (h - size) / 2f
        IconPainter.draw(canvas, icon, x, y, size, tintColor, tintColor2)
    }
}
