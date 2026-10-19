package com.kilat.motion.ui.editor

import com.kilat.motion.core.model.*
import com.kilat.motion.core.animation.Anim
import com.kilat.motion.core.render.Renderer

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
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

@SuppressLint("ClickableViewAccessibility")
class PreviewView(context: Context) : View(context) {

    interface Listener {
        fun onSelect(id: String?)
        fun onEdited(commit: Boolean)
        fun onRequestProperties()
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

    private var viewScale = 1f
    private var offX = 0f
    private var offY = 0f

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x22FFFFFF
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

    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (selectedId != null) listener?.onRequestProperties()
            return true
        }
    })

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
        viewScale = min(w / p.width, h / p.height)
        offX = (w - p.width * viewScale) / 2f
        offY = (h - p.height * viewScale) / 2f
    }

    fun toProject(x: Float, y: Float): Pair<Float, Float> =
        Pair((x - offX) / viewScale, (y - offY) / viewScale)

    fun toViewX(x: Float): Float = offX + x * viewScale
    fun toViewY(y: Float): Float = offY + y * viewScale

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = project ?: return
        updateMapping()

        canvas.drawColor(0xFF07080C.toInt())
        // kanvas project
        tmpRect.set(offX, offY, offX + p.width * viewScale, offY + p.height * viewScale)
        canvas.save()
        canvas.clipRect(tmpRect)
        canvas.translate(offX, offY)
        canvas.scale(viewScale, viewScale)
        Renderer.renderFrame(canvas, p, playhead, true)
        canvas.restore()
        canvas.drawRect(tmpRect, borderPaint)

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

    private fun layerContentSize(l: Layer): Pair<Float, Float> = Pair(l.contentW(), l.contentH())

    private fun hitTest(px: Float, py: Float): Layer? {
        val p = project ?: return null
        for (i in p.layers.indices.reversed()) {
            val l = p.layers[i]
            if (l.hidden) continue
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
                val hMode = handleHit(event.x, event.y)
                if (hMode != MODE_NONE) {
                    val sel = p.findLayer(selectedId) ?: return true
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
                    mode = MODE_NONE
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val l = active ?: return true
                val (px, py) = toProject(event.x, event.y)
                if (hypot(px - startX, py - startY) > 0.6f) moved = true
                when (mode) {
                    MODE_MOVE -> {
                        var nx = origX + (px - startX)
                        var ny = origY + (py - startY)
                        // snap ke tengah
                        val snapT = dp(9f) / viewScale
                        if (abs(nx - p.width / 2f) < snapT) nx = p.width / 2f
                        if (abs(ny - p.height / 2f) < snapT) ny = p.height / 2f
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
                        val snapped = round(nr / 15f) * 15f
                        if (abs(nr - snapped) < 4f) nr = snapped
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
    }
}
