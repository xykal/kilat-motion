package com.kilat.motion.ui.editor

import com.kilat.motion.core.model.*
import com.kilat.motion.core.animation.Anim
import com.kilat.motion.ui.components.IconPainter
import com.kilat.motion.ui.components.KilatIcon

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.hypot
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
            clampScroll()
            invalidate()
        }
    var playhead: Float = 0f
        set(value) {
            field = value
            ensurePlayheadVisible()
        }
    var selectedId: String? = null
    var listener: Listener? = null

    // ---- viewport timeline: bisa digeser (pan) & di-zoom (pinch / tombol) ----
    var zoom: Float = 1f
        private set
    var scrollSec: Float = 0f
        private set
    var onZoomChanged: ((Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val leftW = dp(96f)
    private val rulerH = dp(26f)

    private val bgPaint = Paint().apply { color = 0xFF0D0F15.toInt() }
    private val leftBgPaint = Paint().apply { color = 0xFF111319.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1B202D.toInt()
        strokeWidth = dp(1f)
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF232A3A.toInt()
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
    private val diamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A0C11.toInt()
        style = Paint.Style.FILL
    }
    private val diamondStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE9EEF7.toInt()
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
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF25C4A2.toInt() }

    private fun basePxPerSec(): Float {
        val p = project ?: return 60f
        val usable = width - leftW - dp(10f)
        if (usable <= 0f) return 60f
        return usable / max(1f, p.duration)
    }

    private fun pxPerSec(): Float = basePxPerSec() * zoom

    private fun visibleSec(): Float {
        val z = pxPerSec()
        return if (z <= 0f) 0f else (width - leftW) / z
    }

    private fun clampScroll() {
        val p = project ?: return
        if (width <= 0) return
        scrollSec = scrollSec.coerceIn(0f, max(0f, p.duration - visibleSec()))
    }

    private fun ensurePlayheadVisible() {
        if (width <= 0) return
        val vis = visibleSec()
        if (vis <= 0f) return
        val before = scrollSec
        if (playhead < scrollSec) scrollSec = playhead
        else if (playhead > scrollSec + vis) scrollSec = playhead - vis * 0.12f
        clampScroll()
        if (scrollSec != before) invalidate()
    }

    /** Zoom timeline di sekitar titik fokus layar (default tengah area trek). */
    fun setZoom(newZoom: Float, focalX: Float = leftW + (width - leftW) / 2f) {
        val oldZ = pxPerSec()
        val focalT = scrollSec + (focalX - leftW) / oldZ
        zoom = newZoom.coerceIn(1f, 10f)
        scrollSec = focalT - (focalX - leftW) / pxPerSec()
        clampScroll()
        if (zoom <= 1.01f) scrollSec = 0f
        invalidate()
        onZoomChanged?.invoke(zoom)
    }

    fun zoomBy(factor: Float) = setZoom(zoom * factor)

    fun zoomFit() {
        zoom = 1f
        scrollSec = 0f
        invalidate()
        onZoomChanged?.invoke(zoom)
    }

    private fun timeToX(t: Float): Float = leftW + (t - scrollSec) * pxPerSec()

    /** Campur warna ke arah permukaan gelap secara opaque (bar solid, bukan kaca). */
    private fun solidMix(c: Int, f: Float = 0.42f): Int {
        val bg = 0xFF12151D.toInt()
        fun ch(a: Int, b: Int) = (b + (a - b) * (1f - f)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or
            (ch((c shr 16) and 0xFF, (bg shr 16) and 0xFF) shl 16) or
            (ch((c shr 8) and 0xFF, (bg shr 8) and 0xFF) shl 8) or
            ch(c and 0xFF, bg and 0xFF)
    }

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
        KIND_VECTOR -> 0xFFFF5C7A.toInt()
        KIND_SVG -> 0xFFFF8A3D.toInt()
        KIND_3D -> 0xFF00E5FF.toInt()
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
        var t = (scrollSec / step).toInt() * step
        val tEnd = min(p.duration, scrollSec + visibleSec() + step)
        while (t <= tEnd + 0.001f) {
            val x = timeToX(t)
            if (x >= leftW) {
                canvas.drawLine(x, 0f, x, height.toFloat(), tickPaint)
                if (x < width - dp(14f)) {
                    val label = if (step < 1f) Format.dec(t) + "s" else "${t.roundToInt()}s"
                    canvas.drawText(label, x + dp(3f), dp(10f), labelPaint)
                }
            }
            t += step
        }
        canvas.drawLine(0f, rulerH, width.toFloat(), rulerH, linePaint)
        // scrollbar mini saat ter-zoom: posisi jendela terlihat
        if (zoom > 1.01f && p.duration > 0f) {
            val usable = width - leftW
            val thumbW = max(dp(28f), usable * (visibleSec() / p.duration))
            val thumbX = leftW + usable * (scrollSec / p.duration)
            dotPaint.color = 0xFF2EE6C0.toInt()
            canvas.drawRoundRect(thumbX, rulerH - dp(3.4f), thumbX + thumbW, rulerH - dp(0.8f), dp(1.3f), dp(1.3f), dotPaint)
        }

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
            // dot visibility / ikon kunci
            if (layer.locked) {
                com.kilat.motion.ui.components.IconPainter.draw(
                    canvas, com.kilat.motion.ui.components.KilatIcon.LOCK,
                    leftW - dp(19f), top + rh / 2f - dp(7f), dp(14f), 0xFFFFD166.toInt()
                )
            } else {
                dotPaint.color = if (layer.hidden) 0xFF394155.toInt() else 0xFF2EE6C0.toInt()
                canvas.drawCircle(leftW - dp(12f), top + rh / 2f, dp(4f), dotPaint)
            }

            // bar (solid flat, ter-clip di area trek agar tidak bocor ke kolom nama)
            canvas.save()
            canvas.clipRect(leftW, 0f, width.toFloat(), height.toFloat())
            val x1 = timeToX(layer.start)
            val x2 = timeToX(layer.start + layer.dur)
            val bt = top + dp(4f)
            val bb = top + rh - dp(4f)
            val c = kindColor(layer.kind)
            // Tanpa garis tepi: klip dibedakan lewat isi. Yang terpilih pakai warna penuh.
            val selected = layer.id == selectedId
            barPaint.color = if (selected) c else solidMix(c)
            val rr = dp(6f)
            canvas.drawRoundRect(x1, bt, max(x1 + dp(2f), x2), bb, rr, rr, barPaint)
            if (selected) {
                // grip trim di ujung kanan bar terpilih
                dotPaint.color = 0xFF2EE6C0.toInt()
                val gy0 = bt + dp(4f)
                val gy1 = bb - dp(4f)
                canvas.drawRoundRect(x2 - dp(8f), gy0, x2 - dp(6.4f), gy1, dp(1f), dp(1f), dotPaint)
                canvas.drawRoundRect(x2 - dp(4.6f), gy0, x2 - dp(3f), gy1, dp(1f), dp(1f), dotPaint)
            }

            // diamonds keyframe
            val keys = Anim.allKeyTimes(layer)
            for (kt in keys) {
                val kx = timeToX(kt)
                if (kx < leftW - dp(2f) || kx > width) continue
                val ky = top + rh / 2f
                val r = if (dragMode == MODE_KEY && dragLayer === layer && abs(kt - dragKeyNow) < 0.03f) dp(5.2f) else dp(3.4f)
                playheadHead.reset()
                playheadHead.moveTo(kx, ky - r)
                playheadHead.lineTo(kx + r, ky)
                playheadHead.lineTo(kx, ky + r)
                playheadHead.lineTo(kx - r, ky)
                playheadHead.close()
                canvas.drawPath(playheadHead, if (abs(kt - playhead) < 0.03f) diamondActive else diamondPaint)
                canvas.drawPath(playheadHead, diamondStroke)
            }
            canvas.restore()
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
            canvas.save()
            canvas.clipRect(leftW, 0f, width.toFloat(), height.toFloat())
            barPaint.color = solidMix(0xFF2EE6C0.toInt(), 0.55f)
            val rr = dp(5f)
            canvas.drawRoundRect(x1, bt, max(x1 + dp(2f), x2), bb, rr, rr, barPaint)

            // waveform
            val wave = music.wave
            if (wave != null && wave.isNotEmpty() && x2 > x1 + 2f) {
                val wp = wavePaint
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
            canvas.restore()
            // label musik di kolom kiri
            namePaint.alpha = 255
            IconPainter.draw(canvas, KilatIcon.MUSIC, dp(7f), laneTop + mlh / 2f - dp(7f), dp(14f), c2EE6C0())
            canvas.drawText(music.name.take(14), dp(25f), laneTop + mlh / 2f + dp(3.6f), namePaint)
            canvas.drawCircle(leftW - dp(12f), laneTop + mlh / 2f, dp(4f), dotPaint.apply { color = c2EE6C0() })

            // playhead di lane
        }

        // playhead
        val px = timeToX(playhead)
        if (px < leftW) return
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
    private var lastPanX = 0f
    private var panMoved = false
    private var musicTapCandidate = false
    private var pinchActive = false
    private var pinchStartDist = 1f
    private var pinchStartZoom = 1f

    private fun pointerDist(e: MotionEvent): Float =
        hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))

    private fun pointerMidX(e: MotionEvent): Float = (e.getX(0) + e.getX(1)) / 2f
    private var dragLayer: Layer? = null
    private var downX = 0f
    private var downY = 0f
    private var origStart = 0f
    private var origDur = 0f
    private var dragKeyFrom = 0f
    private var dragKeyNow = 0f
    private var keyMoved = false

    private fun tAt(x: Float): Float = (((x - leftW) / pxPerSec()) + scrollSec).coerceIn(0f, project?.duration ?: 10f)

    /** Snap playhead saat scrub: nempel ke tepi klip & keyframe terdekat. */
    private fun snapSeek(t: Float): Float {
        val p = project ?: return t
        val th = dp(8f) / pxPerSec()
        var best = t
        var bd = th
        fun consider(c: Float) {
            val d = abs(c - t)
            if (d < bd) { bd = d; best = c }
        }
        consider(0f); consider(p.duration)
        for (l in p.layers) {
            consider(l.start); consider(l.start + l.dur)
            for (kt in Anim.allKeyTimes(l)) consider(kt)
        }
        p.music?.let { consider(it.start); consider(it.start + it.dur) }
        return best
    }

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
                    listener?.onSeek(snapSeek(tAt(event.x)))
                    return true
                }
                // lane musik
                val music = p.music
                val mlh = musicLaneH()
                if (music != null && mlh > 0f && event.y > height - mlh) {
                    val x1 = timeToX(music.start)
                    val x2 = timeToX(music.start + music.dur)
                    if (event.x >= x1 - dp(8f) && event.x <= x2 + dp(8f)) {
                        origStart = music.start
                        origDur = music.dur
                        musicTapCandidate = true
                        dragMode = if (event.x > x2 - dp(20f)) MODE_MUSIC_TRIM else MODE_MUSIC_MOVE
                    } else {
                        dragMode = MODE_PAN
                        lastPanX = event.x
                        panMoved = false
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
                    dragMode = MODE_PAN
                    lastPanX = event.x
                    panMoved = false
                    return true
                }

                val x1 = timeToX(layer.start)
                val x2 = timeToX(layer.start + layer.dur)
                val t = tAt(event.x)

                // diamond kena? -> ketuk = lompat ke keyframe, seret = pindahkan waktunya
                for (kt in Anim.allKeyTimes(layer)) {
                    if (abs(timeToX(kt) - event.x) < dp(12f)) {
                        selectedId = layer.id
                        listener?.onSelect(layer.id)
                        listener?.onSeek(kt)
                        if (layer.locked) {
                            dragMode = MODE_NONE
                            invalidate()
                            return true
                        }
                        dragLayer = layer
                        dragKeyFrom = kt
                        dragKeyNow = kt
                        keyMoved = false
                        dragMode = MODE_KEY
                        invalidate()
                        return true
                    }
                }

                if (event.x >= x1 - dp(6f) && event.x <= x2 + dp(6f)) {
                    selectedId = layer.id
                    listener?.onSelect(layer.id)
                    if (layer.locked) {
                        dragMode = MODE_PAN
                        lastPanX = event.x
                        panMoved = false
                        invalidate()
                        return true
                    }
                    dragLayer = layer
                    origStart = layer.start
                    origDur = layer.dur
                    dragMode = if (event.x > x2 - dp(20f)) MODE_TRIM else MODE_MOVE
                    invalidate()
                } else {
                    selectedId = layer.id
                    listener?.onSelect(layer.id)
                    dragMode = MODE_PAN
                    lastPanX = event.x
                    panMoved = false
                    invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    if (dragMode == MODE_MOVE || dragMode == MODE_TRIM || dragMode == MODE_KEY) listener?.onTimesChanged(true)
                    if (dragMode == MODE_MUSIC_MOVE || dragMode == MODE_MUSIC_TRIM) listener?.onMusicEdited(true)
                    pinchActive = true
                    pinchStartDist = max(1f, pointerDist(event))
                    pinchStartZoom = zoom
                    dragMode = MODE_NONE
                    dragLayer = null
                    musicTapCandidate = false
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (pinchActive) {
                    pinchActive = false
                    dragging = false
                    dragMode = MODE_NONE
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinchActive) {
                    if (event.pointerCount >= 2 && pinchStartDist > 1f) {
                        setZoom(pinchStartZoom * pointerDist(event) / pinchStartDist, pointerMidX(event))
                    }
                    return true
                }
                if (!dragging) return true
                when (dragMode) {
                    MODE_PAN -> {
                        val dx = event.x - lastPanX
                        if (abs(event.x - downX) > dp(6f)) panMoved = true
                        if (panMoved) {
                            scrollSec -= dx / z
                            clampScroll()
                            invalidate()
                        }
                        lastPanX = event.x
                    }
                    MODE_SEEK -> listener?.onSeek(snapSeek(tAt(event.x)))
                    MODE_MOVE -> {
                        val l = dragLayer ?: return true
                        val dt = (event.x - downX) / z
                        var ns = snap(origStart + dt, l)
                        ns = ns.coerceIn(0f, max(0f, p.duration - l.dur))
                        val newStart = round(ns * 1000f) / 1000f
                        val delta = newStart - l.start
                        if (abs(delta) > 0.0005f) {
                            l.start = newStart
                            // keyframe WAJIB ikut klipnya (koordinat keyframe absolut)
                            Anim.shiftKeyframes(l, delta, p.duration)
                        }
                        listener?.onTimesChanged(false)
                        invalidate()
                    }
                    MODE_KEY -> {
                        val l = dragLayer ?: return true
                        val nt = tAt(event.x).coerceIn(l.start, l.start + l.dur)
                        val movedTo = Anim.moveKeyframeGroup(l, dragKeyNow, nt)
                        if (abs(movedTo - dragKeyNow) > 0.001f) {
                            dragKeyNow = movedTo
                            keyMoved = true
                            listener?.onSeek(dragKeyNow)
                            listener?.onTimesChanged(false)
                            invalidate()
                        }
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
                val tapLike = abs(event.x - downX) < dp(8f) && abs(event.y - downY) < dp(8f)
                if (dragMode == MODE_PAN && !panMoved && event.actionMasked == MotionEvent.ACTION_UP) {
                    listener?.onSeek(tAt(downX))
                }
                if (dragMode == MODE_MOVE || dragMode == MODE_TRIM) listener?.onTimesChanged(true)
                if (dragMode == MODE_KEY && keyMoved) listener?.onTimesChanged(true)
                if (dragMode == MODE_MUSIC_MOVE || dragMode == MODE_MUSIC_TRIM) {
                    if (musicTapCandidate && tapLike && event.actionMasked == MotionEvent.ACTION_UP) listener?.onMusicTap()
                    else listener?.onMusicEdited(true)
                }
                dragging = false
                dragMode = MODE_NONE
                dragLayer = null
                pinchActive = false
                musicTapCandidate = false
                panMoved = false
                keyMoved = false
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
        const val MODE_PAN = 6
        const val MODE_KEY = 7
    }
}
