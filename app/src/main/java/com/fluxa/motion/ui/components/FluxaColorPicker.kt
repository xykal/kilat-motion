package com.fluxa.motion.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.widget.Toast
import android.widget.HorizontalScrollView
import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.fluxa.motion.core.model.Format
import com.fluxa.motion.ui.theme.FluxaColors
import com.fluxa.motion.ui.theme.dpf
import com.fluxa.motion.ui.theme.kText
import com.fluxa.motion.ui.theme.roundedRect
import kotlin.math.max
import kotlin.math.min

/**
 * Color picker custom ala CapCut/AM: kotak Saturasi-Value, bar Hue, bar Alpha,
 * input hex, info RGB, dan palet preset. Semua digambar sendiri pakai Canvas.
 */
class FluxaColorPickerView(context: Context) : LinearLayout(context) {

    var color: Int = 0xFF2EE6C0.toInt()
        private set
    var onChanged: ((Int) -> Unit)? = null

    private val hsv = FloatArray(3)
    private var alpha = 255

    private val svView = SvView(context)
    private val hueView = BarView(context, isHue = true)
    private val alphaView = BarView(context, isHue = false)
    private val previewBox = View(context)
    private val hexInput: EditText
    private val rgbLabel: TextView
    private var syncingHex = false

    // ambil warna dari media (sampel pixel) — tersembunyi kecuali ada media
    private val mediaLabel = context.kText("AMBIL DARI MEDIA · ketuk untuk sampel", 9.5f, FluxaColors.Dim, bold = true)
    private val sampleView = SampleView(context)
    // warna saya: daftar tersimpan (SharedPreferences)
    private val savedRow = LinearLayout(context)

    init {
        orientation = VERTICAL
        Color.colorToHSV(color, hsv)

        addView(svView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190f)))
        addView(hueView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34f)).apply { topMargin = dp(10f) })
        addView(alphaView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34f)).apply { topMargin = dp(4f) })

        // baris pratinjau + hex + rgb
        val infoRow = LinearLayout(context)
        infoRow.orientation = HORIZONTAL
        infoRow.gravity = Gravity.CENTER_VERTICAL
        val lpInfo = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpInfo.topMargin = dp(12f)
        previewBox.background = roundedRect(color, dpf(10f))
        infoRow.addView(previewBox, LayoutParams(dp(46f), dp(46f)))
        hexInput = EditText(context)
        hexInput.setSingleLine()
        hexInput.inputType = InputType.TYPE_CLASS_TEXT
        hexInput.setTextColor(FluxaColors.Text)
        hexInput.textSize = 13f
        hexInput.background = roundedRect(FluxaColors.Field, dpf(10f))
        hexInput.setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
        hexInput.typeface = android.graphics.Typeface.MONOSPACE
        val lpHex = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpHex.leftMargin = dp(10f)
        infoRow.addView(hexInput, lpHex)
        rgbLabel = context.kText("", 10f, FluxaColors.Muted, font = "monospace")
        val lpRgb = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpRgb.leftMargin = dp(10f)
        infoRow.addView(rgbLabel, lpRgb)
        val copyBtn = context.kText("Salin", 11f, FluxaColors.Accent, bold = true)
        copyBtn.setPadding(dp(10f), dp(8f), dp(6f), dp(8f))
        copyBtn.setOnClickListener { copyHex() }
        infoRow.addView(copyBtn, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(infoRow, lpInfo)
        hexInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !syncingHex) {
                val parsed = Format.parseColor(hexInput.text.toString().trim(), color)
                setColor(parsed, notify = true)
            }
        }

        // palet preset
        val presetLabel = context.kText("PALET CEPAT", 9.5f, FluxaColors.Dim, bold = true)
        val lpPL = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpPL.topMargin = dp(14f)
        addView(presetLabel, lpPL)
        val presets = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF2EE6C0.toInt(), 0xFF59E3FF.toInt(),
            0xFF8B7CFF.toInt(), 0xFFFFD166.toInt(), 0xFFFF5C7A.toInt(), 0xFFFF8A3D.toInt(),
            0xFF22C55E.toInt(), 0xFF00E5FF.toInt(), 0xFFFF2D78.toInt(), 0xFFD90429.toInt(),
            0xFF9FB0C9.toInt(), 0xFF687389.toInt(), 0xFFB8E3FF.toInt(), 0xFFFFB3C7.toInt()
        )
        var row: LinearLayout? = null
        for ((idx, c) in presets.withIndex()) {
            if (idx % 8 == 0) {
                row = LinearLayout(context)
                row.orientation = HORIZONTAL
                val lpR = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lpR.topMargin = dp(8f)
                addView(row, lpR)
            }
            val sw = View(context)
            sw.background = roundedRect(c, dpf(8f))
            val lpS = LayoutParams(0, dp(30f), 1f)
            if (idx % 8 != 0) lpS.leftMargin = dp(6f)
            row?.addView(sw, lpS)
            sw.setOnClickListener { setColor(c, notify = true) }
        }

        // ambil warna dari media
        val lpML = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpML.topMargin = dp(14f)
        addView(mediaLabel, lpML)
        addView(sampleView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150f)).apply { topMargin = dp(8f) })
        mediaLabel.visibility = View.GONE
        sampleView.visibility = View.GONE

        // warna saya (tersimpan)
        val savedLabel = context.kText("WARNA SAYA · tahan untuk hapus", 9.5f, FluxaColors.Dim, bold = true)
        val lpSL = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpSL.topMargin = dp(14f)
        addView(savedLabel, lpSL)
        val savedScroll = HorizontalScrollView(context)
        savedScroll.isHorizontalScrollBarEnabled = false
        savedScroll.addView(savedRow)
        addView(savedScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f) })
        refreshSaved()

        svView.onPick = { s, v ->
            hsv[1] = s; hsv[2] = v; pushFromHsv()
        }
        hueView.onPick = { f ->
            hsv[0] = f * 360f; pushFromHsv()
        }
        alphaView.onPick = { f ->
            alpha = (f * 255f).toInt().coerceIn(0, 255); pushFromHsv()
        }
        refreshAll()
    }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    fun setColor(c: Int, notify: Boolean) {
        color = c
        alpha = Color.alpha(c)
        Color.colorToHSV(c, hsv)
        refreshAll()
        if (notify) onChanged?.invoke(c)
    }

    private fun pushFromHsv() {
        color = Color.HSVToColor(alpha, hsv)
        refreshAll()
        onChanged?.invoke(color)
    }

    private fun refreshAll() {
        previewBox.background = roundedRect(color, dpf(10f))
        syncingHex = true
        hexInput.setText(Format.color(color))
        syncingHex = false
        rgbLabel.text = "R${Color.red(color)}\nG${Color.green(color)}\nB${Color.blue(color)}"
        svView.setHsv(hsv[0], hsv[1], hsv[2])
        hueView.setFraction(hsv[0] / 360f)
        alphaView.setFraction(alpha / 255f)
        alphaView.setBaseColor(color)
    }

    // ---------- kotak saturasi/value ----------
    @SuppressLint("ClickableViewAccessibility")
    private inner class SvView(ctx: Context) : View(ctx) {
        var onPick: ((Float, Float) -> Unit)? = null
        private var hue = 0f
        private var sat = 1f
        private var value = 1f
        private var bmp: Bitmap? = null
        private var bmpHue = -1
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt(); strokeWidth = dpf(2.2f)
        }
        private val ringInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = 0xFF0A0C11.toInt(); strokeWidth = dpf(1f)
        }
        private val rf = RectF()

        fun setHsv(h: Float, s: Float, v: Float) {
            hue = h; sat = s; value = v; invalidate()
        }

        private fun ensureBmp() {
            val hKey = hue.toInt()
            if (bmp != null && bmpHue == hKey) return
            val sz = 128
            val b = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
            val px = IntArray(sz * sz)
            val tmp = FloatArray(3)
            tmp[0] = hue
            for (y in 0 until sz) {
                tmp[2] = 1f - y / (sz - 1).toFloat()
                for (x in 0 until sz) {
                    tmp[1] = x / (sz - 1).toFloat()
                    px[y * sz + x] = Color.HSVToColor(tmp)
                }
            }
            b.setPixels(px, 0, sz, 0, 0, sz, sz)
            bmp = b; bmpHue = hKey
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0) return
            ensureBmp()
            rf.set(0f, 0f, width.toFloat(), height.toFloat())
            paint.isFilterBitmap = true
            bmp?.let { canvas.drawBitmap(it, null, rf, paint) }
            val cx = sat * width
            val cy = (1f - value) * height
            canvas.drawCircle(cx, cy, dpf(9f), ring)
            canvas.drawCircle(cx, cy, dpf(9f), ringInner)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val s = (event.x / max(1, width)).coerceIn(0f, 1f)
                    val v = (1f - event.y / max(1, height)).coerceIn(0f, 1f)
                    onPick?.invoke(s, v)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    parent?.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }
    }

    // ---------- bar hue / alpha ----------
    @SuppressLint("ClickableViewAccessibility")
    private inner class BarView(ctx: Context, private val isHue: Boolean) : View(ctx) {
        var onPick: ((Float) -> Unit)? = null
        private var fraction = 0f
        private var baseColor = 0xFF2EE6C0.toInt()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        private val thumbRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = 0xFF0A0C11.toInt(); strokeWidth = dpf(1f)
        }
        private val rf = RectF()
        private var hueBmp: Bitmap? = null

        fun setFraction(f: Float) {
            fraction = f.coerceIn(0f, 1f); invalidate()
        }

        fun setBaseColor(c: Int) {
            baseColor = c; if (!isHue) invalidate()
        }

        private fun ensureHueBmp() {
            if (hueBmp != null) return
            val wpx = 360
            val b = Bitmap.createBitmap(wpx, 1, Bitmap.Config.ARGB_8888)
            val tmp = floatArrayOf(0f, 1f, 1f)
            for (x in 0 until wpx) {
                tmp[0] = x.toFloat()
                b.setPixel(x, 0, Color.HSVToColor(tmp))
            }
            hueBmp = b
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0) return
            val pad = dpf(9f)
            val top = height / 2f - dpf(7f)
            val bottom = height / 2f + dpf(7f)
            rf.set(pad, top, width - pad, bottom)
            if (isHue) {
                ensureHueBmp()
                paint.isFilterBitmap = true
                hueBmp?.let { canvas.drawBitmap(it, null, rf, paint) }
            } else {
                // pola papan catur di belakang bar alpha
                val cell = dpf(7f)
                paint.color = 0xFF3A4156.toInt()
                canvas.drawRoundRect(rf, dpf(7f), dpf(7f), paint)
                paint.color = 0xFF232838.toInt()
                var cx = pad
                var rowIdx = 0
                while (cx < width - pad) {
                    if (rowIdx % 2 == 0) canvas.drawRect(cx, top, min(width - pad, cx + cell), height / 2f, paint)
                    else canvas.drawRect(cx, height / 2f, min(width - pad, cx + cell), bottom, paint)
                    cx += cell; rowIdx++
                }
                // gradasi warna -> transparan digambar per kolom tipis
                val steps = 48
                val r0 = Color.red(baseColor); val g0 = Color.green(baseColor); val b0 = Color.blue(baseColor)
                for (i in 0 until steps) {
                    val a = ((i + 1f) / steps * 255f).toInt()
                    paint.color = Color.argb(a, r0, g0, b0)
                    val lx = pad + (width - 2 * pad) * i / steps
                    val rx = pad + (width - 2 * pad) * (i + 1) / steps + 1f
                    canvas.drawRect(lx, top, rx, bottom, paint)
                }
            }
            val tx = pad + (width - 2 * pad) * fraction
            canvas.drawCircle(tx, height / 2f, dpf(9f), thumbPaint)
            canvas.drawCircle(tx, height / 2f, dpf(9f), thumbRing)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val pad = dpf(9f)
                    val f = ((event.x - pad) / max(1f, width - 2 * pad)).coerceIn(0f, 1f)
                    onPick?.invoke(f)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    parent?.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }
    }
    fun setMedia(bmp: Bitmap?) {
        sampleView.bmp = bmp
        val vis = if (bmp != null) View.VISIBLE else View.GONE
        mediaLabel.visibility = vis
        sampleView.visibility = vis
        sampleView.invalidate()
    }

    private fun prefs() = context.getSharedPreferences("fluxa_colors", Context.MODE_PRIVATE)

    private fun savedColors(): MutableList<Int> =
        (prefs().getString("list", "") ?: "").split(",").mapNotNull { it.toLongOrNull(16)?.toInt() }.toMutableList()

    private fun persistSaved(list: List<Int>) {
        prefs().edit().putString("list", list.joinToString(",") { Integer.toHexString(it) }).apply()
    }

    private fun refreshSaved() {
        savedRow.removeAllViews()
        for (c in savedColors()) {
            val sw = View(context)
            sw.background = roundedRect(c, dpf(8f))
            val lp = LayoutParams(dp(30f), dp(30f))
            lp.rightMargin = dp(6f)
            savedRow.addView(sw, lp)
            sw.setOnClickListener { setColor(c, notify = true) }
            sw.setOnLongClickListener {
                val list = savedColors()
                list.remove(c)
                persistSaved(list)
                refreshSaved()
                true
            }
        }
        val add = context.kText("+", 16f, FluxaColors.Accent, bold = true)
        add.gravity = Gravity.CENTER
        add.background = roundedRect(FluxaColors.Field, dpf(8f))
        add.setOnClickListener {
            val list = savedColors()
            if (!list.contains(color)) {
                list.add(color)
                persistSaved(list)
            }
            refreshSaved()
        }
        savedRow.addView(add, LayoutParams(dp(30f), dp(30f)))
    }

    private fun copyHex() {
        val text = if (alpha >= 255) String.format("#%06X", color and 0xFFFFFF) else String.format("#%08X", color)
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("warna", text))
        Toast.makeText(context, "Disalin $text", Toast.LENGTH_SHORT).show()
    }

    /** Sampel pixel dari media: ketuk/seret di atas gambar untuk ambil warnanya. */
    private inner class SampleView(ctx: Context) : View(ctx) {
        var bmp: Bitmap? = null
        private val dst = RectF()
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpf(2f)
            color = FluxaColors.Accent
        }
        private var mx = -1f
        private var my = -1f

        override fun onDraw(canvas: Canvas) {
            val b = bmp ?: return
            if (width <= 0 || height <= 0) return
            val sc = min(width / b.width.toFloat(), height / b.height.toFloat())
            val w = b.width * sc
            val h = b.height * sc
            val l = (width - w) / 2f
            val t = (height - h) / 2f
            dst.set(l, t, l + w, t + h)
            canvas.drawBitmap(b, null, dst, null)
            if (mx >= 0f) canvas.drawCircle(mx, my, dpf(9f), ring)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val b = bmp ?: return false
            parent?.requestDisallowInterceptTouchEvent(true)
            if (!dst.contains(e.x, e.y)) return true
            val px = ((e.x - dst.left) / dst.width() * b.width).toInt().coerceIn(0, b.width - 1)
            val py = ((e.y - dst.top) / dst.height() * b.height).toInt().coerceIn(0, b.height - 1)
            mx = e.x
            my = e.y
            val c = b.getPixel(px, py)
            setColor((c and 0x00FFFFFF) or 0xFF000000.toInt(), notify = true)
            invalidate()
            return true
        }
    }
}

object FluxaColorPicker {
    fun show(activity: Activity, initial: Int, title: String = "Pilih Warna", media: Bitmap? = null, onPick: (Int) -> Unit) {
        val picker = FluxaColorPickerView(activity)
        picker.setColor(initial, notify = false)
        picker.setMedia(media)
        FluxaSheet.show(
            activity, title, picker,
            positiveText = "Pakai Warna", positiveIcon = FluxaIcon.CHECK,
            onPositive = { onPick(picker.color) },
            negativeText = "Batal"
        )
    }
}
