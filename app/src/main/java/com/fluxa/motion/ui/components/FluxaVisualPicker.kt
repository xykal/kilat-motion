package com.fluxa.motion.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

/**
 * Pilihan visual berbentuk baris tile, tanpa teks. Setiap tile menggambar
 * pratinjaunya sendiri lewat [art]. Label hanya dipakai sebagai contentDescription
 * untuk TalkBack.
 */
class FluxaVisualPicker(context: Context) : HorizontalScrollView(context) {

    var onPick: ((String) -> Unit)? = null
    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val tiles = ArrayList<Tile>()

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(row)
    }

    fun setItems(
        keys: List<String>,
        selected: String,
        desc: (String) -> String,
        art: (Canvas, RectF, String) -> Unit
    ) {
        row.removeAllViews()
        tiles.clear()
        val d = resources.displayMetrics.density
        for (k in keys) {
            val t = Tile(context, k, art)
            t.isOn = k == selected
            t.contentDescription = desc(k)
            t.isClickable = true
            t.setOnClickListener { select(k) }
            tiles.add(t)
            val lp = LinearLayout.LayoutParams((58 * d).toInt(), (46 * d).toInt())
            lp.rightMargin = (6 * d).toInt()
            row.addView(t, lp)
        }
    }

    private fun select(k: String) {
        for (t in tiles) t.isOn = t.key == k
        onPick?.invoke(k)
    }

    private inner class Tile(
        ctx: Context,
        val key: String,
        private val art: (Canvas, RectF, String) -> Unit
    ) : View(ctx) {

        var isOn: Boolean = false
            set(value) {
                field = value
                invalidate()
            }

        private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFF11141C.toInt()
        }
        private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val box = RectF()
        private val inner = RectF()

        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            val radius = 12 * d
            val sw = (if (isOn) 2f else 1f) * d
            box.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(box, radius, radius, fillP)
            strokeP.strokeWidth = sw
            strokeP.color = if (isOn) 0xFFF4F6FA.toInt() else 0xFF2A3040.toInt()
            box.inset(sw / 2f, sw / 2f)
            canvas.drawRoundRect(box, radius, radius, strokeP)
            val pad = 9 * d
            inner.set(pad, pad, width - pad, height - pad)
            art(canvas, inner, key)
        }
    }
}
