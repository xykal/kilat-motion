package com.fluxa.motion.ui.editor

import com.fluxa.motion.core.model.*
import com.fluxa.motion.core.animation.Anim
import com.fluxa.motion.ui.components.IconPainter
import com.fluxa.motion.ui.components.FluxaIcon

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.text.TextUtils
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.OverScroller
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
        fun onSplit(layer: Layer)
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

    // inersia setelah geser bebas (fling)
    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val leftW = dp(120f)
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
    // Keyframe dibuat terang dengan tepi gelap supaya terbaca di atas bar layer
    private val propRowBg = Paint().apply { color = 0xFF0B0D13.toInt() }
    private val propNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF98A3B8.toInt() }
    private val diamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE9EEF7.toInt()
        style = Paint.Style.FILL
    }
    private val diamondStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A0C11.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
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
        // Geser bebas seperti CapCut: awal dan akhir timeline boleh sampai ke tengah layar.
        val half = visibleSec() / 2f
        scrollSec = scrollSec.coerceIn(-half, max(-half, p.duration - half))
    }

    override fun computeScroll() {
        if (!scroller.computeScrollOffset()) return
        val z = pxPerSec()
        if (z > 0f) {
            scrollSec = scroller.currX / z
            clampScroll()
        }
        invalidate()
        if (!scroller.isFinished) postInvalidateOnAnimation()
    }

    private fun startFling(event: MotionEvent) {
        val vt = velocityTracker ?: return
        val p = project ?: return
        vt.addMovement(event)
        vt.computeCurrentVelocity(1000)
        val vx = vt.xVelocity
        if (abs(vx) < dp(120f)) return
        val z = pxPerSec()
        val half = visibleSec() / 2f
        val minX = (-half * z).toInt()
        val maxX = (max(-half, p.duration - half) * z).toInt()
        scroller.fling((scrollSec * z).toInt(), 0, (-vx).toInt(), 0, minX, maxX, 0, 0)
        postInvalidateOnAnimation()
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
        invalidate()
        onZoomChanged?.invoke(zoom)
    }

    fun zoomBy(factor: Float) = setZoom(zoom * factor)

    /**
     * Zoom timeline supaya rentang keyframe [layer] (kunci pertama sampai terakhir) memenuhi
     * area trek, dengan jeda di kiri dan kanan. Mengembalikan false jika layer belum punya keyframe.
     */
    fun zoomToKeys(layer: Layer): Boolean {
        if (project == null || width <= 0) return false
        val keys = Anim.allKeyTimes(layer)
        if (keys.isEmpty()) return false
        val a = keys.first()
        val b = keys.last()
        val span = max(0.3f, b - a)
        val visible = span * 1.3f
        val usable = width - leftW
        val target = (usable / (basePxPerSec() * visible)).coerceIn(1f, 10f)
        setZoom(target)
        scrollSec = (a + b) / 2f - (usable / 2f) / pxPerSec()
        clampScroll()
        invalidate()
        return true
    }

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
        val n = rows().size
        if (n == 0) return dp(30f)
        return min(dp(34f), max(dp(16f), avail / n))
    }

    /** Satu baris timeline: baris layer (channel = null) atau baris properti (channel = kunci properti). */
    private class TRow(val layer: Layer, val channel: String?)

    /**
     * Daftar baris dari atas ke bawah. Layer yang dipilih dan punya keyframe menampilkan satu baris
     * per properti yang berkeyframe, seperti Alight Motion.
     */
    private fun rows(): List<TRow> {
        val p = project ?: return emptyList()
        val out = ArrayList<TRow>()
        for (i in p.layers.indices.reversed()) {
            val layer = p.layers[i]
            out.add(TRow(layer, null))
            if (layer.id == selectedId) {
                for (k in PROP_KEYS) {
                    val ch = layer.channels[k] ?: continue
                    if (ch.keys.isNotEmpty()) out.add(TRow(layer, k))
                }
            }
        }
        return out
    }

    private fun layerAtRow(rowIdx: Int): Layer? = rows().getOrNull(rowIdx)?.layer

    // scroll vertikal area layer (baris bisa lebih banyak dari tinggi layar)
    private var vScroll = 0f
    private var lastPanY = 0f

    private fun maxVScroll(): Float {
        val p = project ?: return 0f
        val content = rows().size * rowHeight()
        return max(0f, content - (height - rulerH - musicLaneH()))
    }

    private fun rowAtY(y: Float): Int {
        val rh = rowHeight()
        return ((y - rulerH + vScroll) / rh).toInt()
    }

    private fun c2EE6C0(): Int = 0xFF2EE6C0.toInt()

    private fun kindColor(kind: String): Int = when (kind) {
        KIND_TEXT -> 0xFF6EA8FF.toInt()
        KIND_SHAPE -> 0xFFFFB84D.toInt()
        KIND_IMAGE, KIND_VIDEO -> 0xFF2EE6C0.toInt()
        KIND_VECTOR -> 0xFFFF5C7A.toInt()
        KIND_SVG -> 0xFFFF8A3D.toInt()
        KIND_3D -> 0xFF00E5FF.toInt()
        else -> 0xFF8B7CFF.toInt()
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF4F6FA.toInt()
        style = Paint.Style.FILL
    }
    private val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A0B0F.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val handlePath = Path()

    /** Handle tiga garis melayang di ujung kanan klip terpilih (tarik untuk trim kanan). */
    /** Baris properti: nama properti di kolom kiri, diamond keyframe milik properti itu saja. */
    private fun drawPropRow(c: Canvas, layer: Layer, key: String, top: Float, rh: Float) {
        c.drawRect(0f, top, width.toFloat(), top + rh, propRowBg)
        propNamePaint.textSize = dp(10.5f)
        c.drawText(PROP_NAMES[key] ?: key, dp(40f), top + rh / 2f + dp(3.6f), propNamePaint)
        val ch = layer.channels[key] ?: return
        c.save()
        c.clipRect(leftW, top, width.toFloat(), top + rh)
        val ky = top + rh / 2f
        val r = dp(4f)
        for (k in ch.keys) {
            val kx = timeToX(k.t)
            if (kx < leftW - dp(2f) || kx > width) continue
            playheadHead.reset()
            playheadHead.moveTo(kx, ky - r)
            playheadHead.lineTo(kx + r, ky)
            playheadHead.lineTo(kx, ky + r)
            playheadHead.lineTo(kx - r, ky)
            playheadHead.close()
            c.drawPath(playheadHead, if (abs(k.t - playhead) < 0.03f) diamondActive else diamondPaint)
            c.drawPath(playheadHead, diamondStroke)
        }
        c.restore()
    }

    private fun drawGrip(c: Canvas, edgeX: Float, top: Float, bottom: Float) {
        val w = dp(14f)
        val h = min(bottom - top, dp(28f))
        val cy = (top + bottom) / 2f
        val l = edgeX - w / 2f
        val r = l + w
        gripShadow.color = 0x66000000
        c.drawRoundRect(l, cy - h / 2f + dp(1.5f), r, cy + h / 2f + dp(1.5f), dp(5f), dp(5f), gripShadow)
        c.drawRoundRect(l, cy - h / 2f, r, cy + h / 2f, dp(5f), dp(5f), handlePaint)
        val lw = dp(6f)
        val gap = dp(3.2f)
        for (i in -1..1) {
            val y = cy + i * gap
            c.drawLine(edgeX - lw / 2f, y, edgeX + lw / 2f, y, chevronPaint)
        }
    }

    private val gripShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipNamePaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private var splitRect: android.graphics.RectF? = null
    private var splitLayer: Layer? = null

    /** Pil trim di tepi klip dengan panah keluar: ‹ di kiri, › di kanan. Tanpa teks. */
    private fun drawTrimHandle(c: Canvas, edgeX: Float, top: Float, bottom: Float, isLeft: Boolean) {
        val w = dp(10f)
        val cy = (top + bottom) / 2f
        val hh = min(bottom - top, dp(26f)) / 2f
        val l = if (isLeft) edgeX else edgeX - w
        val r = l + w
        c.drawRoundRect(l, cy - hh, r, cy + hh, dp(4f), dp(4f), handlePaint)
        val cx = (l + r) / 2f
        val ca = dp(2.6f)
        val dir = if (isLeft) -1f else 1f
        val tipX = cx + dir * ca * 0.5f
        val backX = cx - dir * ca * 0.5f
        chevronPaint.strokeWidth = dp(1.6f)
        handlePath.reset()
        handlePath.moveTo(backX, cy - hh * 0.4f)
        handlePath.lineTo(tipX, cy)
        handlePath.lineTo(backX, cy + hh * 0.4f)
        c.drawPath(handlePath, chevronPaint)
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
            val thumbX = leftW + usable * (scrollSec.coerceIn(0f, p.duration) / p.duration)
            dotPaint.color = 0xFF2EE6C0.toInt()
            canvas.drawRoundRect(thumbX, rulerH - dp(3.4f), thumbX + thumbW, rulerH - dp(0.8f), dp(1.3f), dp(1.3f), dotPaint)
        }

        // rows (dipotong di area layer agar tidak menimpa ruler & lane musik)
        splitRect = null
        splitLayer = null
        vScroll = vScroll.coerceIn(0f, maxVScroll())
        canvas.save()
        canvas.clipRect(0f, rulerH, width.toFloat(), height - musicLaneH())
        val rh = rowHeight()
        val rowList = rows()
        for ((row, info) in rowList.withIndex()) {
            val layer = info.layer
            val top = rulerH + row * rh - vScroll
            if (top > height) break
            canvas.drawLine(0f, top + rh, width.toFloat(), top + rh, linePaint)
            if (info.channel != null) {
                drawPropRow(canvas, layer, info.channel, top, rh)
                continue
            }

            // nama layer
            val nameY = top + rh / 2f + dp(3.6f)
            namePaint.alpha = if (layer.hidden) 90 else 255
            val label = TextUtils.ellipsize(layer.name, android.text.TextPaint(namePaint), leftW - dp(70f), TextUtils.TruncateAt.END).toString()
            // preview kecil klip (warna sesuai jenis) di kolom kiri
            val tileC = kindColor(layer.kind)
            barPaint.color = if (layer.hidden) solidMix(tileC, 0.6f) else tileC
            canvas.drawRoundRect(dp(8f), top + rh / 2f - dp(8f), dp(26f), top + rh / 2f + dp(8f), dp(4f), dp(4f), barPaint)
            canvas.drawText(label, dp(40f), nameY, namePaint)
            // dot visibility / ikon kunci
            if (layer.locked) {
                com.fluxa.motion.ui.components.IconPainter.draw(
                    canvas, com.fluxa.motion.ui.components.FluxaIcon.LOCK,
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
            // nama layer di dalam klip, dipotong (ellipsis) sesuai lebar klip
            val clipW = max(x1 + dp(2f), x2) - x1
            val innerW = clipW - dp(28f)
            if (innerW > dp(24f)) {
                clipNamePaint.textSize = dp(10.5f)
                clipNamePaint.color = if (selected) 0xFF05131F.toInt() else 0xFFE9EEF7.toInt()
                val label = android.text.TextUtils.ellipsize(
                    layer.name, clipNamePaint, innerW, android.text.TextUtils.TruncateAt.END
                ).toString()
                canvas.drawText(label, x1 + dp(14f), top + rh / 2f + dp(3.6f), clipNamePaint)
            }
            if (selected) {
                // handle trim di kedua ujung klip terpilih: pil putih dengan panah (kiri & kanan sama)
                drawTrimHandle(canvas, x1, bt, bb, true)
                drawTrimHandle(canvas, x2, bt, bb, false)
                // gunting inline di tepi atas playhead (potong klip tanpa sheet)
                val px = timeToX(playhead)
                if (px > x1 + dp(6f) && px < x2 - dp(6f) && px > leftW + dp(10f)) {
                    val sr = android.graphics.RectF(px - dp(11f), bt - dp(11f), px + dp(11f), bt + dp(11f))
                    splitRect = sr
                    splitLayer = layer
                    gripShadow.color = 0x66000000
                    canvas.drawCircle(px, bt, dp(11f), gripShadow)
                    canvas.drawCircle(px, bt, dp(10f), handlePaint)
                    IconPainter.draw(canvas, FluxaIcon.SCISSORS, px - dp(7f), bt - dp(7f), dp(14f), 0xFF0A0C11.toInt())
                }
            }

            // diamonds keyframe
            val keys = Anim.allKeyTimes(layer)
            for (kt in keys) {
                val kx = timeToX(kt)
                if (kx < leftW - dp(2f) || kx > width) continue
                val ky = top + rh / 2f
                val r = if (dragMode == MODE_KEY && dragLayer === layer && abs(kt - dragKeyNow) < 0.03f) dp(6f) else dp(4.4f)
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
        }
        canvas.restore()

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
            drawTrimHandle(canvas, x1, bt, bb, true)
            drawTrimHandle(canvas, x2, bt, bb, false)
            canvas.restore()
            // label musik di kolom kiri
            namePaint.alpha = 255
            IconPainter.draw(canvas, FluxaIcon.MUSIC, dp(7f), laneTop + mlh / 2f - dp(7f), dp(14f), c2EE6C0())
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
                lastPanY = event.y
                splitRect?.let { sr ->
                    if (sr.contains(event.x, event.y)) {
                        splitLayer?.let { listener?.onSplit(it) }
                        splitRect = null
                        dragMode = MODE_NONE
                        dragging = false
                        return true
                    }
                }
                dragging = true
                scroller.forceFinished(true)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }

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

                val info = rows().getOrNull(rowAtY(event.y))
                val layer = info?.layer
                if (info?.channel != null && layer != null) {
                    // baris properti: ketuk diamond = lompat ke keyframe properti itu (tanpa drag)
                    val kt = layer.channels[info.channel]?.keys?.minByOrNull { abs(timeToX(it.t) - event.x) }?.t
                    if (kt != null && abs(timeToX(kt) - event.x) < dp(14f)) {
                        selectedId = layer.id
                        listener?.onSelect(layer.id)
                        listener?.onSeek(kt)
                    }
                    dragMode = MODE_NONE
                    invalidate()
                    return true
                }
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
                    // ujung terdekat yang di-trim (kiri atau kanan), sisanya geser klip
                    val dl = abs(event.x - x1)
                    val dr = abs(event.x - x2)
                    dragMode = when {
                        dl <= dp(18f) && dl <= dr -> MODE_TRIM_START
                        dr <= dp(18f) -> MODE_TRIM
                        else -> MODE_MOVE
                    }
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
                    if (dragMode == MODE_MOVE || dragMode == MODE_TRIM || dragMode == MODE_TRIM_START || dragMode == MODE_KEY) listener?.onTimesChanged(true)
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
                velocityTracker?.addMovement(event)
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
                        if (abs(event.x - downX) > dp(6f) || abs(event.y - downY) > dp(6f)) panMoved = true
                        if (panMoved) {
                            scrollSec -= dx / z
                            clampScroll()
                            invalidate()
                        }
                        lastPanX = event.x
                        // geser vertikal daftar layer
                        if (panMoved) {
                            vScroll = (vScroll - (event.y - lastPanY)).coerceIn(0f, maxVScroll())
                            invalidate()
                        }
                        lastPanY = event.y
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
                    MODE_TRIM_START -> {
                        val l = dragLayer ?: return true
                        val dt = (event.x - downX) / z
                        val origEnd = origStart + origDur
                        var ns = snap(origStart + dt, l)
                        ns = ns.coerceIn(0f, origEnd - 0.2f)
                        val newStart = round(ns * 1000f) / 1000f
                        l.start = newStart
                        l.dur = round((origEnd - newStart) * 1000f) / 1000f
                        listener?.onTimesChanged(false)
                        invalidate()
                    }
                    MODE_TRIM -> {
                        val l = dragLayer ?: return true
                        val dt = (event.x - downX) / z
                        var end = snap(origStart + origDur + dt, l)
                        end = end.coerceIn(origStart + 0.2f, 3600f)
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
                if (dragMode == MODE_PAN && panMoved && event.actionMasked == MotionEvent.ACTION_UP) startFling(event)
                val tapLike = abs(event.x - downX) < dp(8f) && abs(event.y - downY) < dp(8f)
                if (dragMode == MODE_PAN && !panMoved && event.actionMasked == MotionEvent.ACTION_UP) {
                    listener?.onSeek(tAt(downX))
                }
                if (dragMode == MODE_MOVE || dragMode == MODE_TRIM || dragMode == MODE_TRIM_START) listener?.onTimesChanged(true)
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
        const val MODE_TRIM_START = 8
    }
}

/** Nama tampilan untuk baris properti di timeline. */
private val PROP_NAMES = mapOf(
    "x" to "Posisi X", "y" to "Posisi Y", "scale" to "Skala", "scaleX" to "Skala X", "scaleY" to "Skala Y",
    "skewX" to "Miring X", "skewY" to "Miring Y", "rot" to "Rotasi", "rotX" to "Tilt X", "rotY" to "Tilt Y",
    "alpha" to "Opacity", "typing" to "Teks muncul", "fx.blur" to "Blur", "fx.brightness" to "Kecerahan",
    "fx.contrast" to "Kontras", "fx.saturate" to "Saturasi", "fx.hue" to "Hue", "fx.glow" to "Glow"
)
