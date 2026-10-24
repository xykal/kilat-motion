package com.fluxa.motion.ui.editor

import com.fluxa.motion.core.model.*
import com.fluxa.motion.core.animation.Anim
import com.fluxa.motion.core.render.Renderer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

@SuppressLint("ClickableViewAccessibility")
class PreviewView(context: Context) : View(context) {

    interface Listener {
        fun onSelect(id: String?)
        fun onEdited(commit: Boolean)
        fun onRequestProperties()
        fun onStroke(points: FloatArray)
    }

    var project: Project? = null
        set(value) {
            field = value
            invalidate()
        }
    var playhead: Float = 0f
    var selectedId: String? = null
    var listener: Listener? = null
    /** Magnet: snap posisi ke tengah kanvas + snap rotasi 15 derajat. */
    var snapEnabled: Boolean = true

    /** Mode gambar bebas: sentuhan jadi coretan vektor, bukan geser layer. */
    var drawMode: Boolean = false
    private val strokePts = ArrayList<Float>()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFFFF5C7A.toInt()
    }
    private val strokePath = android.graphics.Path()

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private var viewScale = 1f
    private var offX = 0f
    private var offY = 0f

    /** Zoom kanvas preview: 1x = pas di layar, maks 6x. Geser lewat pan (dalam pixel view). */
    var canvasZoom = 1f
        private set
    private var panX = 0f
    private var panY = 0f
    var onZoomChanged: ((Float) -> Unit)? = null

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = 0xFF252B3A.toInt()
    }
    private val canvasShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        setShadowLayer(dp(20f), 0f, dp(8f), 0xB3000000.toInt())
    }
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
        color = 0xFF2EE6C0.toInt()
        pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0C11.toInt() }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
        color = 0xFF2EE6C0.toInt()
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2EE6C0.toInt() }
    private val knobInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF06231D.toInt() }
    private val tmpRect = RectF()

    // Properti dibuka HANYA lewat long-press diam (tanpa geser). Double-tap sengaja
    // dihapus: dulu double-tap yang tak sengaja pas drag bikin sheet kebuka sendiri.
    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            if (selectedId != null && !dragMoved) listener?.onRequestProperties()
        }
    }).apply { setIsLongpressEnabled(true) }
    private var dragMoved = false

    fun updateTime(t: Float) {
        if (abs(t - playhead) > 0.0005f) {
            playhead = t
            invalidate()
        }
    }

    fun select(id: String?) {
        selectedId = id
        invalidate()
    }

    private fun updateMapping() {
        val p = project ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        viewScale = min(w / p.width, h / p.height) * canvasZoom
        offX = (w - p.width * viewScale) / 2f + panX
        offY = (h - p.height * viewScale) / 2f + panY
    }

    /** Batas geser supaya kanvas tidak lepas dari layar. */
    private fun clampPan(p: Project, s: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cw = p.width * s
        val ch = p.height * s
        val mx = if (cw > w) (cw - w) / 2f else 0f
        val my = if (ch > h) (ch - h) / 2f else 0f
        panX = panX.coerceIn(-mx, mx)
        panY = panY.coerceIn(-my, my)
    }

    /** Zoom 1x-6x dengan titik (midX, midY) tetap di bawah jari. */
    private fun applyCamera(midX: Float, midY: Float, dist: Float) {
        val p = project ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val z = (camZoom0 * dist / camDist0).coerceIn(1f, MAX_ZOOM)
        val s = min(w / p.width, h / p.height) * z
        canvasZoom = z
        panX = midX - camFocusX * s - (w - p.width * s) / 2f
        panY = midY - camFocusY * s - (h - p.height * s) / 2f
        if (z <= 1.001f) { canvasZoom = 1f; panX = 0f; panY = 0f }
        clampPan(p, s)
        updateMapping()
        invalidate()
        onZoomChanged?.invoke(canvasZoom)
    }

    /** Kembali ke 1x (pas di layar). */
    fun resetCanvasZoom() {
        canvasZoom = 1f
        panX = 0f
        panY = 0f
        invalidate()
        onZoomChanged?.invoke(1f)
    }

    private fun pointerDist(e: MotionEvent): Float =
        hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))

    private fun pointerMidX(e: MotionEvent): Float = (e.getX(0) + e.getX(1)) / 2f
    private fun pointerMidY(e: MotionEvent): Float = (e.getY(0) + e.getY(1)) / 2f

    fun toProject(x: Float, y: Float): Pair<Float, Float> =
        Pair((x - offX) / viewScale, (y - offY) / viewScale)

    fun toViewX(x: Float): Float = offX + x * viewScale
    fun toViewY(y: Float): Float = offY + y * viewScale

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = project ?: return
        updateMapping()

        canvas.drawColor(0xFF07080C.toInt())
        // kanvas project: bayangan lembut + tepi gradasi aksen
        tmpRect.set(offX, offY, offX + p.width * viewScale, offY + p.height * viewScale)
        canvas.drawRoundRect(tmpRect, dp(8f), dp(8f), canvasShadowPaint)
        canvas.save()
        canvas.clipRect(tmpRect)
        canvas.translate(offX, offY)
        canvas.scale(viewScale, viewScale)
        Renderer.renderFrame(canvas, p, playhead, true)
        canvas.restore()
        // bingkai datar: warna solid, tanpa gradien
        borderPaint.shader = null
        borderPaint.color = 0x9932E6C0.toInt()
        canvas.drawRoundRect(tmpRect, dp(8f), dp(8f), borderPaint)

        // coretan yang sedang digambar (mode gambar bebas)
        if (strokePts.size >= 4) {
            strokePath.reset()
            strokePath.moveTo(toViewX(strokePts[0]), toViewY(strokePts[1]))
            var si = 2
            while (si + 1 < strokePts.size) {
                strokePath.lineTo(toViewX(strokePts[si]), toViewY(strokePts[si + 1]))
                si += 2
            }
            strokePaint.strokeWidth = dp(2.4f)
            canvas.drawPath(strokePath, strokePaint)
        }

        // overlay seleksi
        val sel = p.findLayer(selectedId) ?: return
        if (sel.hidden) return
        if (playhead < sel.start || playhead > sel.start + sel.dur) return
        val ev = Anim.evalLayer(sel, playhead)
        val cx = offX + (ev["x"] ?: sel.x) * viewScale
        val cy = offY + (ev["y"] ?: sel.y) * viewScale
        val sc = (ev["scale"] ?: 1f) * viewScale
        val bw = sel.contentW() * sc
        val bh = sel.contentH() * sc
        val rotDeg = ev["rot"] ?: 0f
        val rotRad = rotDeg * Math.PI.toFloat() / 180f

        canvas.save()
        canvas.rotate(rotDeg, cx, cy)
        val pad = dp(3f)
        tmpRect.set(cx - bw / 2f - pad, cy - bh / 2f - pad, cx + bw / 2f + pad, cy + bh / 2f + pad)
        canvas.drawRect(tmpRect, selPaint)

        val hs = dp(5.5f)
        val corners = arrayOf(
            Pair(tmpRect.left, tmpRect.top), Pair(tmpRect.right, tmpRect.top),
            Pair(tmpRect.right, tmpRect.bottom), Pair(tmpRect.left, tmpRect.bottom)
        )
        for (c in corners) {
            canvas.drawRect(c.first - hs, c.second - hs, c.first + hs, c.second + hs, handleFill)
            canvas.drawRect(c.first - hs, c.second - hs, c.first + hs, c.second + hs, handleStroke)
        }
        // knob rotasi
        val knobY = tmpRect.top - dp(26f)
        canvas.drawLine(cx, tmpRect.top, cx, knobY + dp(5f), selPaint)
        canvas.drawCircle(cx, knobY, dp(7f), knobPaint)
        canvas.drawCircle(cx, knobY, dp(3.2f), knobInner)
        canvas.restore()
    }

    // ---------------- interaksi ----------------
    private var mode = MODE_NONE
    private var active: Layer? = null
    private var startX = 0f
    private var startY = 0f
    private var origX = 0f
    private var origY = 0f
    private var origScale = 1f
    private var origRot = 0f
    private var startDist = 1f
    private var startAngle = 0f
    private var moved = false
    private var lastX = 0f
    private var lastY = 0f
    private var camZoom0 = 1f
    private var camDist0 = 1f
    private var camFocusX = 0f
    private var camFocusY = 0f

    private fun layerContentSize(l: Layer): Pair<Float, Float> = Pair(l.contentW(), l.contentH())

    private fun hitTest(px: Float, py: Float): Layer? {
        val p = project ?: return null
        for (i in p.layers.indices.reversed()) {
            val l = p.layers[i]
            if (l.hidden || l.locked) continue
            if (playhead < l.start || playhead > l.start + l.dur) continue
            val ev = Anim.evalLayer(l, playhead)
            val cx = ev["x"] ?: l.x
            val cy = ev["y"] ?: l.y
            val sc = (ev["scale"] ?: 1f).coerceAtLeast(0.01f)
            val rot = (ev["rot"] ?: 0f) * Math.PI.toFloat() / 180f
            val dx = px - cx
            val dy = py - cy
            val lx = dx * cos(-rot) - dy * sin(-rot)
            val ly = dx * sin(-rot) + dy * cos(-rot)
            val (w, h) = layerContentSize(l)
            val padP = dp(10f) / viewScale
            if (abs(lx) <= w * sc / 2f + padP && abs(ly) <= h * sc / 2f + padP) return l
        }
        return null
    }

    private fun handleHit(viewX: Float, viewY: Float): Int {
        val p = project ?: return MODE_NONE
        val sel = p.findLayer(selectedId) ?: return MODE_NONE
        if (playhead < sel.start || playhead > sel.start + sel.dur) return MODE_NONE
        val ev = Anim.evalLayer(sel, playhead)
        val cx = offX + (ev["x"] ?: sel.x) * viewScale
        val cy = offY + (ev["y"] ?: sel.y) * viewScale
        val sc = (ev["scale"] ?: 1f) * viewScale
        val bw = sel.contentW() * sc
        val bh = sel.contentH() * sc
        val rotDeg = ev["rot"] ?: 0f
        val rot = rotDeg * Math.PI.toFloat() / 180f

        fun world(lx: Float, ly: Float): Pair<Float, Float> =
            Pair(cx + lx * cos(rot) - ly * sin(rot), cy + lx * sin(rot) + ly * cos(rot))

        val knob = world(0f, -bh / 2f - dp(29f))
        if (hypot(viewX - knob.first, viewY - knob.second) < dp(20f)) return MODE_ROTATE

        val corners = listOf(
            world(-bw / 2f - dp(3f), -bh / 2f - dp(3f)),
            world(bw / 2f + dp(3f), -bh / 2f - dp(3f)),
            world(bw / 2f + dp(3f), bh / 2f + dp(3f)),
            world(-bw / 2f - dp(3f), bh / 2f + dp(3f))
        )
        for (c in corners) if (hypot(viewX - c.first, viewY - c.second) < dp(20f)) return MODE_SCALE
        return MODE_NONE
    }

    private fun liveSet(l: Layer, key: String, v: Float) {
        val ch = l.channels[key]
        if (ch != null && ch.keys.isNotEmpty()) Anim.setKey(l, key, playhead, v)
        else l.setStatic(key, v)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawMode) {
            updateMapping()
            val (qx, qy) = toProject(event.x, event.y)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    strokePts.clear()
                    strokePts.add(qx); strokePts.add(qy)
                }
                MotionEvent.ACTION_MOVE -> {
                    val n = strokePts.size
                    if (n >= 2) {
                        val lx = strokePts[n - 2]
                        val ly = strokePts[n - 1]
                        if (hypot(qx - lx, qy - ly) > 3f) {
                            strokePts.add(qx); strokePts.add(qy)
                            invalidate()
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (strokePts.size >= 4) {
                        listener?.onStroke(FloatArray(strokePts.size) { idx -> strokePts[idx] })
                    }
                    strokePts.clear()
                    invalidate()
                }
            }
            return true
        }
        gesture.onTouchEvent(event)
        val p = project ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateMapping()
                val (px, py) = toProject(event.x, event.y)
                startX = px
                startY = py
                moved = false
                dragMoved = false
                val hMode = handleHit(event.x, event.y)
                if (hMode != MODE_NONE) {
                    val sel = p.findLayer(selectedId) ?: return true
                    if (sel.locked) return true
                    active = sel
                    mode = hMode
                    val ev = Anim.evalLayer(sel, playhead)
                    origX = ev["x"] ?: sel.x
                    origY = ev["y"] ?: sel.y
                    origScale = ev["scale"] ?: 1f
                    origRot = ev["rot"] ?: 0f
                    startDist = maxOf(6f, hypot(px - origX, py - origY))
                    startAngle = atan2(py - origY, px - origX) * 180f / Math.PI.toFloat()
                    return true
                }
                val hit = hitTest(px, py)
                if (hit != null) {
                    if (hit.id != selectedId) {
                        selectedId = hit.id
                        listener?.onSelect(hit.id)
                    }
                    active = hit
                    mode = MODE_MOVE
                    val ev = Anim.evalLayer(hit, playhead)
                    origX = ev["x"] ?: hit.x
                    origY = ev["y"] ?: hit.y
                    origScale = ev["scale"] ?: 1f
                    origRot = ev["rot"] ?: 0f
                    invalidate()
                } else {
                    if (selectedId != null) {
                        selectedId = null
                        listener?.onSelect(null)
                        invalidate()
                    }
                    // area kosong saat zoom: geser kanvas
                    mode = if (canvasZoom > 1f) MODE_PAN else MODE_NONE
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    // dua jari: hentikan geser layer, lanjut ke zoom kanvas
                    if (moved && active != null) listener?.onEdited(true)
                    active = null
                    updateMapping()
                    val mx = pointerMidX(event)
                    val my = pointerMidY(event)
                    camFocusX = (mx - offX) / viewScale
                    camFocusY = (my - offY) / viewScale
                    camZoom0 = canvasZoom
                    camDist0 = max(10f, pointerDist(event))
                    mode = MODE_CAM
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (mode == MODE_CAM) {
                    // sisa satu jari: lanjut geser kanvas dari posisi jari itu
                    val remain = if (event.actionIndex == 0) 1 else 0
                    lastX = event.getX(remain)
                    lastY = event.getY(remain)
                    mode = MODE_PAN
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == MODE_CAM && event.pointerCount >= 2) {
                    applyCamera(pointerMidX(event), pointerMidY(event), max(10f, pointerDist(event)))
                    return true
                }
                if (mode == MODE_PAN) {
                    val pp = project ?: return true
                    panX += event.x - lastX
                    panY += event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    clampPan(pp, viewScale)
                    updateMapping()
                    invalidate()
                    return true
                }
                val l = active ?: return true
                val (px, py) = toProject(event.x, event.y)
                if (hypot(px - startX, py - startY) > 0.6f) { moved = true; dragMoved = true }
                when (mode) {
                    MODE_MOVE -> {
                        var nx = origX + (px - startX)
                        var ny = origY + (py - startY)
                        // snap ke tengah (magnet)
                        if (snapEnabled) {
                            val snapT = dp(9f) / viewScale
                            if (abs(nx - p.width / 2f) < snapT) nx = p.width / 2f
                            if (abs(ny - p.height / 2f) < snapT) ny = p.height / 2f
                        }
                        liveSet(l, "x", nx)
                        liveSet(l, "y", ny)
                    }
                    MODE_SCALE -> {
                        val dist = maxOf(6f, hypot(px - origX, py - origY))
                        var ns = origScale * (dist / startDist)
                        ns = ns.coerceIn(0.02f, 12f)
                        liveSet(l, "scale", ns)
                    }
                    MODE_ROTATE -> {
                        val ang = atan2(py - origY, px - origX) * 180f / Math.PI.toFloat()
                        var nr = origRot + (ang - startAngle)
                        if (snapEnabled) {
                            val snapped = round(nr / 15f) * 15f
                            if (abs(nr - snapped) < 4f) nr = snapped
                        }
                        liveSet(l, "rot", nr)
                    }
                }
                invalidate()
                listener?.onEdited(false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (moved && active != null) listener?.onEdited(true)
                active = null
                mode = MODE_NONE
            }
        }
        return true
    }

    private companion object {
        const val MODE_NONE = 0
        const val MODE_MOVE = 1
        const val MODE_SCALE = 2
        const val MODE_ROTATE = 3
        const val MODE_PAN = 4
        const val MODE_CAM = 5
        const val MAX_ZOOM = 6f
    }
}
