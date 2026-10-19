package com.kilat.motion.ui.editor

import com.kilat.motion.core.model.*
import com.kilat.motion.core.animation.Anim
import com.kilat.motion.ui.components.IconPainter
import com.kilat.motion.ui.components.KilatIcon

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@SuppressLint("ClickableViewAccessibility")
class TimelineView(context: Context) : View(context) {

    interface Listener {
        fun onSeek(t: Float)
        fun onSelect(id: String?)
        fun onTimesChanged(commit: Boolean)
        fun onVisibilityToggle(id: String)
        fun onMusicTap()
        fun onMusicEdited(commit: Boolean)
    }

    var project: Project? = null
        set(value) {
            field = value
            invalidate()
        }
    var playhead: Float = 0f
    var selectedId: String? = null
    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val leftW = dp(96f)
    private val rulerH = dp(26f)

    private val bgPaint = Paint().apply { color = 0xFF0D0F15.toInt() }
    private val leftBgPaint = Paint().apply { color = 0xFF111319.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x14FFFFFF
        strokeWidth = dp(1f)
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22FFFFFF
        strokeWidth = dp(1f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF687389.toInt()
        textSize = dp(8.5f)
        typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL)
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC9D4E4.toInt()
        textSize = dp(10.5f)
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val selBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.6f)
        color = 0xFF2EE6C0.toInt()
    }
    private val diamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A0C11.toInt()
        style = Paint.Style.FILL
    }
    private val diamondStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val diamondActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2EE6C0.toInt()
        style = Paint.Style.FILL
    }
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2EE6C0.toInt()
        strokeWidth = dp(1.8f)
    }
    private val playheadHead = Path()
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun pxPerSec(): Float {
        val p = project ?: return 60f
        val usable = width - leftW - dp(10f)
        if (usable <= 0f) return 60f
        return usable / max(1f, p.duration)
    }

    private fun timeToX(t: Float): Float = leftW + t * pxPerSec()

    private fun musicLaneH(): Float {
        val p = project ?: return 0f
        return if (p.music != null) dp(30f) else 0f
    }

    private fun rowHeight(): Float {
        val p = project ?: return dp(30f)
        val avail = height - rulerH - musicLaneH()
        if (p.layers.isEmpty()) return dp(30f)
        return min(dp(34f), max(dp(16f), avail / p.layers.size))
    }

    private fun layerAtRow(rowIdx: Int): Layer? {
        val p = project ?: return null
        // baris 0 = layer paling atas (index terakhir)
        val idx = p.layers.size - 1 - rowIdx
        return if (idx in p.layers.indices) p.layers[idx] else null
    }

    private fun rowAtY(y: Float): Int {
        val rh = rowHeight()
        return ((y - rulerH) / rh).toInt()
    }

    private fun c2EE6C0(): Int = 0xFF2EE6C0.toInt()

    private fun kindColor(kind: String): Int = when (kind) {
        KIND_TEXT -> 0xFF6EA8FF.toInt()
        KIND_SHAPE -> 0xFFFFB84D.toInt()
        KIND_IMAGE -> 0xFF2EE6C0.toInt()
        else -> 0xFF8B7CFF.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = project ?: return
        canvas.drawColor(0xFF07080C.toInt())
        canvas.drawRect(0f, 0f, leftW, height.toFloat(), leftBgPaint)
        canvas.drawRect(leftW, 0f, width.toFloat(), rulerH, bgPaint)

        val z = pxPerSec()
        // ticks
        val step = when {
            z >= 120f -> 0.5f
            z >= 45f -> 1f
            z >= 20f -> 2f
            else -> 5f
        }
        var t = 0f
        while (t <= p.duration + 0.001f) {
            val x = timeToX(t)
            canvas.drawLine(x, 0f, x, height.toFloat(), tickPaint)
            if (x < width - dp(14f)) {
                val label = if (step < 1f) Format.dec(t) + "s" else "${t.roundToInt()}s"
                canvas.drawText(label, x + dp(3f), dp(10f), labelPaint)
            }
            t += step
        }
        canvas.drawLine(0f, rulerH, width.toFloat(), rulerH, linePaint)

        // rows
        val rh = rowHeight()
        var row = 0
        while (row < p.layers.size) {
            val layer = layerAtRow(row) ?: break
            val top = rulerH + row * rh
            if (top > height) break
            canvas.drawLine(0f, top + rh, width.toFloat(), top + rh, linePaint)

            // nama layer
            val nameY = top + rh / 2f + dp(3.6f)
            namePaint.alpha = if (layer.hidden) 90 else 255
            val label = TextUtils.ellipsize(layer.name, android.text.TextPaint(namePaint), leftW - dp(30f), TextUtils.TruncateAt.END).toString()
            canvas.drawText(label, dp(8f), nameY, namePaint)
            // dot visibility
            dotPaint.color = if (layer.hidden) 0xFF394155.toInt() else 0xFF2EE6C0.toInt()
            canvas.drawCircle(leftW - dp(12f), top + rh / 2f, dp(4f), dotPaint)

            // bar
            val x1 = timeToX(layer.start)
            val x2 = timeToX(layer.start + layer.dur)
            val bt = top + dp(4f)
            val bb = top + rh - dp(4f)
            val c = kindColor(layer.kind)
            barPaint.shader = LinearGradient(x1, 0f, x2, 0f, (c and 0x00FFFFFF) or 0x55000000, c and 0x33FFFFFF, Shader.TileMode.CLAMP)
            val rr = dp(5f)
            canvas.drawRoundRect(x1, bt, max(x1 + dp(2f), x2), bb, rr, rr, barPaint)
            barPaint.shader = null
            barEdgePaint.color = (c and 0x00FFFFFF) or 0x88000000.toInt()
            canvas.drawRoundRect(x1, bt, max(x1 + dp(2f), x2), bb, rr, rr, barEdgePaint)
            if (layer.id == selectedId) {
                canvas.drawRoundRect(x1, bt, max(x1 + dp(2f), x2), bb, rr, rr, selBarPaint)
            }

            // diamonds keyframe
            val keys = Anim.allKeyTimes(layer)
            for (kt in keys) {
                val kx = timeToX(kt)
                if (kx < leftW - dp(2f) || kx > width) continue
                val ky = top + rh / 2f
                val r = dp(3.4f)
                playheadHead.reset()
                playheadHead.moveTo(kx, ky - r)
                playheadHead.lineTo(kx + r, ky)
                playheadHead.lineTo(kx, ky + r)
                playheadHead.lineTo(kx - r, ky)
                playheadHead.close()
                canvas.drawPath(playheadHead, if (abs(kt - playhead) < 0.03f) diamondActive else diamondPaint)
                canvas.drawPath(playheadHead, diamondStroke)
            }
            row++
        }

        // ---- lane musik ----
        val music = p.music
        val mlh = musicLaneH()
        if (music != null && mlh > 0f) {
            val laneTop = height - mlh
            canvas.drawLine(0f, laneTop, width.toFloat(), laneTop, linePaint)
            val x1 = timeToX(music.start)
            val x2 = timeToX(music.start + music.dur)
            val bt = laneTop + dp(4f)
            val bb = height - dp(4f)
            barPaint.shader = null
            barPaint.color = 0x3333FFCC
            val rr = dp(5f)
            canvas.drawRoundRect(x1, bt, max(x1 + dp(2f), x2), bb, rr, rr, barPaint)
            barEdgePaint.color = 0xAA2EE6C0.toInt()
            canvas.drawRoundRect(x1, bt, max(x1 + dp(2f), x2), bb, rr, rr, barEdgePaint)

            // waveform
            val wave = music.wave
            if (wave != null && wave.isNotEmpty() && x2 > x1 + 2f) {
                val wp = Paint(Paint.ANTI_ALIAS_FLAG)
                wp.color = 0xAA2EE6C0.toInt()
                val midY = (bt + bb) / 2f
                val halfH = (bb - bt) / 2f - dp(1.5f)
                val n = wave.size
                var bx = x1 + dp(2f)
                val bxEnd = x2 - dp(2f)
                while (bx < bxEnd) {
                    val frac = ((bx - x1) / max(1f, x2 - x1)).coerceIn(0f, 1f)
                    val idx = (frac * (n - 1)).toInt().coerceIn(0, n - 1)
                    val amp = wave[idx].coerceIn(0f, 1f)
                    val hh = max(dp(0.6f), amp * halfH)
                    canvas.drawRect(bx, midY - hh, bx + dp(1.6f), midY + hh, wp)
                    bx += dp(2.6f)
                }
            }
            // label musik di kolom kiri
            namePaint.alpha = 255
            IconPainter.draw(canvas, KilatIcon.MUSIC, dp(7f), laneTop + mlh / 2f - dp(7f), dp(14f), c2EE6C0())
            canvas.drawText(music.name.take(14), dp(25f), laneTop + mlh / 2f + dp(3.6f), namePaint)
            canvas.drawCircle(leftW - dp(12f), laneTop + mlh / 2f, dp(4f), dotPaint.apply { color = c2EE6C0() })

            // playhead di lane
        }

        // playhead
        val px = timeToX(playhead)
        canvas.drawLine(px, 0f, px, height.toFloat(), playheadPaint)
        playheadHead.reset()
        playheadHead.moveTo(px - dp(7f), 0f)
        playheadHead.lineTo(px + dp(7f), 0f)
        playheadHead.lineTo(px, dp(11f))
        playheadHead.close()
        canvas.drawPath(playheadHead, playheadPaint)
    }

    // ---------------- touch ----------------
    private var dragging = false
    private var dragMode = MODE_NONE
    private var dragLayer: Layer? = null
    private var downX = 0f
    private var downY = 0f
    private var origStart = 0f
    private var origDur = 0f

    private fun tAt(x: Float): Float = ((x - leftW) / pxPerSec()).coerceIn(0f, project?.duration ?: 10f)

    private fun snap(t: Float, exclude: Layer?): Float {
        val p = project ?: return t
        val th = dp(7f) / pxPerSec()
        var best = t
        var bd = th
        val cands = ArrayList<Float>()
        cands.add(0f)
        cands.add(p.duration)
        cands.add(playhead)
        for (l in p.layers) {
            if (l === exclude) continue
            cands.add(l.start)
            cands.add(l.start + l.dur)
        }
        for (c in cands) {
            val d = abs(c - t)
            if (d < bd) {
                bd = d
                best = c
            }
        }
        return best
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val p = project ?: return false
        val z = pxPerSec()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                dragging = true

                if (event.y <= rulerH) {
                    dragMode = MODE_SEEK
                    listener?.onSeek(tAt(event.x))
                    return true
                }
                // lane musik
                val music = p.music
                val mlh = musicLaneH()
                if (music != null && mlh > 0f && event.y > height - mlh) {
                    val x1 = timeToX(music.start)
                    val x2 = timeToX(music.start + music.dur)
                    listener?.onMusicTap()
                    if (event.x >= x1 - dp(6f) && event.x <= x2 + dp(6f)) {
                        origStart = music.start
                        origDur = music.dur
                        dragMode = if (event.x > x2 - dp(16f)) MODE_MUSIC_TRIM else MODE_MUSIC_MOVE
                    } else {
                        dragMode = MODE_NONE
                    }
                    invalidate()
                    return true
                }
                // klik kolom nama
                if (event.x < leftW) {
                    val row = rowAtY(event.y)
                    val layer = layerAtRow(row)
                    if (layer != null) {
                        if (event.x > leftW - dp(26f)) {
                            listener?.onVisibilityToggle(layer.id)
                        } else {
                            selectedId = layer.id
                            listener?.onSelect(layer.id)
                            invalidate()
                        }
                    }
                    dragMode = MODE_NONE
                    return true
                }

                val row = rowAtY(event.y)
                val layer = layerAtRow(row)
                if (layer == null) {
                    dragMode = MODE_SEEK
                    listener?.onSeek(tAt(event.x))
                    return true
                }

                val x1 = timeToX(layer.start)
                val x2 = timeToX(layer.start + layer.dur)
                val t = tAt(event.x)

                // diamond kena?
                for (kt in Anim.allKeyTimes(layer)) {
                    if (abs(timeToX(kt) - event.x) < dp(9f)) {
                        selectedId = layer.id
                        listener?.onSelect(layer.id)
                        listener?.onSeek(kt)
                        dragMode = MODE_NONE
                        invalidate()
                        return true
                    }
                }

                if (event.x >= x1 - dp(4f) && event.x <= x2 + dp(4f)) {
                    selectedId = layer.id
                    listener?.onSelect(layer.id)
                    dragLayer = layer
                    origStart = layer.start
                    origDur = layer.dur
                    dragMode = if (event.x > x2 - dp(16f)) MODE_TRIM else MODE_MOVE
                    invalidate()
                } else {
                    selectedId = layer.id
                    listener?.onSelect(layer.id)
                    dragMode = MODE_SEEK
                    listener?.onSeek(t)
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return true
                when (dragMode) {
                    MODE_SEEK -> listener?.onSeek(tAt(event.x))
                    MODE_MOVE -> {
                        val l = dragLayer ?: return true
                        val dt = (event.x - downX) / z
                        var ns = snap(origStart + dt, l)
                        ns = ns.coerceIn(0f, max(0f, p.duration - l.dur))
                        l.start = round(ns * 1000f) / 1000f
                        listener?.onTimesChanged(false)
                        invalidate()
                    }
                    MODE_TRIM -> {
                        val l = dragLayer ?: return true
                        val dt = (event.x - downX) / z
                        var end = snap(origStart + origDur + dt, l)
                        end = end.coerceIn(origStart + 0.2f, p.duration)
                        l.dur = round((end - origStart) * 1000f) / 1000f
                        listener?.onTimesChanged(false)
                        invalidate()
                    }
                    MODE_MUSIC_MOVE -> {
                        val m = p.music ?: return true
                        val dt = (event.x - downX) / z
                        var ns = snap(origStart + dt, null)
                        ns = ns.coerceIn(0f, max(0f, p.duration - 0.3f))
                        m.start = round(ns * 1000f) / 1000f
                        if (m.start + m.dur > p.duration) m.dur = p.duration - m.start
                        listener?.onMusicEdited(false)
                        invalidate()
                    }
                    MODE_MUSIC_TRIM -> {
                        val m = p.music ?: return true
                        val dt = (event.x - downX) / z
                        var end = snap(origStart + origDur + dt, null)
                        end = end.coerceIn(m.start + 0.3f, p.duration)
                        m.dur = round((end - m.start) * 1000f) / 1000f
                        listener?.onMusicEdited(false)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (dragMode == MODE_MOVE || dragMode == MODE_TRIM) listener?.onTimesChanged(true)
                if (dragMode == MODE_MUSIC_MOVE || dragMode == MODE_MUSIC_TRIM) listener?.onMusicEdited(true)
                dragging = false
                dragMode = MODE_NONE
                dragLayer = null
            }
        }
        return true
    }

    private fun round(v: Float): Float = Math.round(v * 1000f) / 1000f

    private companion object {
        const val MODE_NONE = 0
        const val MODE_SEEK = 1
        const val MODE_MOVE = 2
        const val MODE_TRIM = 3
        const val MODE_MUSIC_MOVE = 4
        const val MODE_MUSIC_TRIM = 5
    }
}
