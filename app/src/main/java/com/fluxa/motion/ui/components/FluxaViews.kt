package com.fluxa.motion.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.fluxa.motion.ui.theme.FluxaColors
import com.fluxa.motion.ui.theme.dp
import com.fluxa.motion.ui.theme.dpf
import com.fluxa.motion.ui.theme.kText
import com.fluxa.motion.ui.theme.pressEffect
import com.fluxa.motion.ui.theme.roundedRect
import kotlin.math.roundToInt

// =====================================================================
// Tombol custom FLUXA (bukan Button bawaan Android)
// =====================================================================
enum class FluxaButtonStyle { PRIMARY, GHOST, SOFT, DANGER_SOFT }

class FluxaButton(
    context: Context,
    label: String,
    icon: FluxaIcon? = null,
    style: FluxaButtonStyle = FluxaButtonStyle.GHOST,
    textSizeSp: Float = 12f
) : LinearLayout(context) {

    private val labelTv: TextView
    private val iconView: IconView?

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        val ink = when (style) {
            FluxaButtonStyle.PRIMARY -> FluxaColors.AccentInk
            FluxaButtonStyle.GHOST -> FluxaColors.Text
            FluxaButtonStyle.SOFT -> FluxaColors.Muted
            FluxaButtonStyle.DANGER_SOFT -> FluxaColors.Rose
        }
        background = when (style) {
            FluxaButtonStyle.PRIMARY -> roundedRect(FluxaColors.Accent, dpf(11f))
            FluxaButtonStyle.GHOST -> roundedRect(FluxaColors.CardHi, dpf(11f))
            FluxaButtonStyle.SOFT -> roundedRect(FluxaColors.Field, dpf(11f))
            FluxaButtonStyle.DANGER_SOFT -> roundedRect(FluxaColors.DangerInk, dpf(11f))
        }
        setPadding(dp(14f), dp(11f), dp(14f), dp(11f))
        iconView = if (icon != null) {
            val iv = IconView(context, icon, ink)
            val lp = LayoutParams(dp(17f), dp(17f))
            lp.rightMargin = dp(7f)
            addView(iv, lp)
            iv
        } else null
        labelTv = context.kText(label, textSizeSp, ink, bold = true)
        labelTv.gravity = Gravity.CENTER
        addView(labelTv)
        pressEffect()
    }

    fun setLabel(s: String) { labelTv.text = s }
    fun setIcon(icon: FluxaIcon) { iconView?.icon = icon }
}

/** Tombol ikon persegi rounded (toolbar, header, panel). */
class FluxaIconButton(
    context: Context,
    icon: FluxaIcon,
    tint: Int = FluxaColors.Muted,
    filled: Boolean = true,
    sizeDp: Float = 36f
) : FrameLayout(context) {

    val iconView: IconView = IconView(context, icon, tint)

    init {
        isClickable = true
        isFocusable = true
        // Dibacakan TalkBack. Tanpa ini tombol ikon tidak punya nama.
        contentDescription = FluxaIconLabels.of(icon)
        if (filled) background = roundedRect(FluxaColors.CardHi, dpf(10f))
        val pad = dp(sizeDp * 0.24f)
        val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        iconView.setPadding(pad, pad, pad, pad)
        addView(iconView, lp)
        pressEffect(0.92f)
    }

    fun setTint(c: Int) { iconView.tintColor = c }
    fun setIcon(i: FluxaIcon) {
        iconView.icon = i
        contentDescription = FluxaIconLabels.of(i)   // mis. Sembunyikan <-> Tampilkan
    }
}

// =====================================================================
// Slider custom (pengganti slider bawaan): track + thumb digambar Canvas
// =====================================================================
@SuppressLint("ClickableViewAccessibility")
class FluxaSlider(context: Context) : View(context) {

    var max: Int = 100
        set(v) { field = v.coerceAtLeast(1); if (progress > field) progress = field; invalidate() }
    var progress: Int = 0
        set(v) { field = v.coerceIn(0, max); invalidate() }

    var onProgress: ((Int) -> Unit)? = null
    var onReleased: (() -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FluxaColors.Line }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FluxaColors.Accent }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val thumbRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FluxaColors.Accent; style = Paint.Style.STROKE
    }
    private val rf = RectF()
    private var dragging = false

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        setMeasuredDimension(w, dp(30f))
    }

    private fun thumbR() = dpf(if (dragging) 9.5f else 8f)
    private fun trackLeft() = paddingLeft + thumbR()
    private fun trackRight() = width - paddingRight - thumbR()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0) return
        val cy = height / 2f
        val left = trackLeft(); val right = trackRight()
        val th = dpf(4.5f)
        rf.set(left, cy - th / 2, right, cy + th / 2)
        canvas.drawRoundRect(rf, th / 2, th / 2, trackPaint)
        val f = if (max == 0) 0f else progress.toFloat() / max
        val fx = left + (right - left) * f
        if (fx > left) {
            rf.set(left, cy - th / 2, fx, cy + th / 2)
            canvas.drawRoundRect(rf, th / 2, th / 2, fillPaint)
        }
        canvas.drawCircle(fx, cy, thumbR(), thumbPaint)
        thumbRing.strokeWidth = dpf(2f)
        canvas.drawCircle(fx, cy, thumbR() - dpf(1f), thumbRing)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromX(e.x)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) updateFromX(e.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                onReleased?.invoke()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun updateFromX(x: Float) {
        val left = trackLeft(); val right = trackRight()
        if (right <= left) return
        val f = ((x - left) / (right - left)).coerceIn(0f, 1f)
        val p = (f * max).roundToInt()
        if (p != progress) {
            progress = p
            onProgress?.invoke(p)
        } else invalidate()
    }
}

// =====================================================================
// Progress bar custom
// =====================================================================
class FluxaProgressBar(context: Context) : View(context) {

    var progress: Float = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FluxaColors.Line }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FluxaColors.Accent }
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FluxaColors.Sky }
    private val rf = RectF()

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), dp(10f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        rf.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(rf, h / 2, h / 2, trackPaint)
        val fw = width * progress
        if (fw > h / 2) {
            rf.set(0f, 0f, fw, h)
            canvas.drawRoundRect(rf, h / 2, h / 2, fillPaint)
            rf.set((fw - h).coerceAtLeast(0f), 0f, fw, h)
            canvas.drawRoundRect(rf, h / 2, h / 2, tipPaint)
        }
    }
}

// =====================================================================
// Tombol keyframe: wajik digambar manual (pengganti teks diamond)
// mode: 0 = tidak ada keyframe, 1 = channel berisi, 2 = ada keyframe di playhead
// =====================================================================
class KeyframeButton(context: Context) : View(context) {

    var mode: Int = 0
        set(v) { field = v; invalidate() }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = android.graphics.Path()

    init {
        isClickable = true
        isFocusable = true
        pressEffect(0.88f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) * 0.30f
        path.reset()
        path.moveTo(cx, cy - r); path.lineTo(cx + r, cy); path.lineTo(cx, cy + r); path.lineTo(cx - r, cy)
        path.close()
        when (mode) {
            2 -> { fillPaint.color = FluxaColors.Accent; canvas.drawPath(path, fillPaint) }
            1 -> { strokePaint.color = FluxaColors.Accent; strokePaint.strokeWidth = dpf(1.6f); canvas.drawPath(path, strokePaint) }
            else -> { strokePaint.color = FluxaColors.Faint; strokePaint.strokeWidth = dpf(1.4f); canvas.drawPath(path, strokePaint) }
        }
    }
}

// =====================================================================
// Chip pill (preset, rasio, filter)
// =====================================================================

/** Toggle switch custom solid flat (pengganti tombol teks Ya/Tidak). */
class FluxaSwitch(context: Context) : View(context) {

    var checked: Boolean = false
        set(v) {
            field = v
            invalidate()
        }
    var onToggle: ((Boolean) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val rf = RectF()

    init {
        isClickable = true
        setOnClickListener {
            checked = !checked
            onToggle?.invoke(checked)
        }
        pressEffect(0.94f)
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        setMeasuredDimension(dp(48f), dp(28f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val w = width.toFloat()
        val r = h / 2f
        trackPaint.color = if (checked) FluxaColors.Accent else 0xFF2A3040.toInt()
        rf.set(0f, 0f, w, h)
        canvas.drawRoundRect(rf, r, r, trackPaint)
        val knobR = r - dpf(3.5f)
        val cx = if (checked) w - r else r
        canvas.drawCircle(cx, r, knobR, knobPaint)
    }
}

// Chip digambar sendiri (flat, tanpa default style AppCompat), jadi TextView biasa cukup.
@SuppressLint("AppCompatCustomView")
class FluxaChip(context: Context, label: String) : TextView(context) {

    var chipSelected: Boolean = false
        set(v) { field = v; refresh() }

    init {
        text = label
        textSize = 11f
        gravity = Gravity.CENTER
        includeFontPadding = false
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        isClickable = true
        setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
        pressEffect(0.95f)
        refresh()
    }

    private fun refresh() {
        if (chipSelected) {
            background = roundedRect(FluxaColors.Accent, dpf(9f))
            setTextColor(FluxaColors.AccentInk)
        } else {
            background = roundedRect(FluxaColors.Field, dpf(9f))
            setTextColor(FluxaColors.Muted)
        }
    }
}

// =====================================================================
// Dropdown custom (pengganti dropdown bawaan): tap -> bottom sheet pilihan
// =====================================================================
class FluxaDropdown(context: Context) : LinearLayout(context) {

    private var items: List<String> = emptyList()
    var selectedIndex: Int = 0
        private set
    var onSelected: ((Int) -> Unit)? = null
    var sheetTitle: String = "Pilih"

    private val labelTv: TextView = context.kText("", 12f, FluxaColors.Text)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        background = roundedRect(FluxaColors.Field, dpf(9f))
        setPadding(dp(10f), dp(9f), dp(8f), dp(9f))
        labelTv.maxLines = 1
        addView(labelTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val chev = IconView(context, FluxaIcon.CHEVRON_DOWN, FluxaColors.Dim)
        addView(chev, LayoutParams(dp(16f), dp(16f)))
        pressEffect(0.98f)
        setOnClickListener {
            val act = findActivity() ?: return@setOnClickListener
            FluxaSheet.showList(
                act, sheetTitle,
                items.mapIndexed { i, s ->
                    FluxaSheet.Item(s, icon = if (i == selectedIndex) FluxaIcon.CHECK else null)
                }
            ) { idx ->
                if (idx != selectedIndex) {
                    select(idx, notify = false)
                    onSelected?.invoke(idx)
                }
            }
        }
    }

    fun setItems(newItems: List<String>, selected: Int) {
        items = newItems
        select(selected.coerceIn(0, (newItems.size - 1).coerceAtLeast(0)), notify = false)
    }

    fun select(idx: Int, notify: Boolean) {
        selectedIndex = idx
        labelTv.text = items.getOrNull(idx) ?: ""
        if (notify) onSelected?.invoke(idx)
    }
}

// =====================================================================
// Text field custom: basis EditText hanya untuk keyboard/input,
// semua visual (background, border, padding, hint) milik FLUXA.
// =====================================================================
fun Context.kEditText(
    value: String,
    hint: String = "",
    numeric: Boolean = false,
    multiLine: Boolean = false,
    textSizeSp: Float = 12.5f
): EditText {
    val e = EditText(this)
    e.setText(value)
    e.hint = hint
    e.setTextColor(FluxaColors.Text)
    e.setHintTextColor(FluxaColors.Faint)
    e.textSize = textSizeSp
    e.background = roundedRect(FluxaColors.Field, dpf(9f))
    e.setPadding(dp(10f), dp(9f), dp(10f), dp(9f))
    e.inputType = when {
        numeric -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        multiLine -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        else -> InputType.TYPE_CLASS_TEXT
    }
    if (!multiLine) e.setSingleLine(true) else e.gravity = Gravity.TOP or Gravity.START
    return e
}

/** Cari Activity dari rantai ContextWrapper (untuk dropdown/sheet). */
fun View.findActivity(): android.app.Activity? {
    var c: Context? = context
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return c as? android.app.Activity
}

/** Nama Indonesia untuk tiap ikon, dipakai sebagai contentDescription (TalkBack). */
object FluxaIconLabels {
    fun of(icon: FluxaIcon): String? = when (icon) {
        FluxaIcon.BACK -> "Kembali"
        FluxaIcon.CLOSE -> "Tutup"
        FluxaIcon.UNDO -> "Urungkan"
        FluxaIcon.REDO -> "Ulangi aksi"
        FluxaIcon.MORE -> "Menu lainnya"
        FluxaIcon.NEXT -> "Berikutnya"
        FluxaIcon.SKIP_START -> "Ke awal"
        FluxaIcon.MAGNET -> "Snap ke klip"
        FluxaIcon.LOOP -> "Putar berulang"
        FluxaIcon.PLAY -> "Putar"
        FluxaIcon.PAUSE -> "Jeda"
        FluxaIcon.RESET -> "Atur ulang"
        FluxaIcon.COPY -> "Duplikat"
        FluxaIcon.TRASH -> "Hapus"
        FluxaIcon.EYE -> "Tampilkan"
        FluxaIcon.EYE_OFF -> "Sembunyikan"
        FluxaIcon.LOCK -> "Kunci"
        FluxaIcon.UNLOCK -> "Buka kunci"
        FluxaIcon.SCISSORS -> "Potong"
        FluxaIcon.PLUS -> "Perbesar"
        FluxaIcon.MINUS -> "Perkecil"
        FluxaIcon.RATIO -> "Sesuaikan ke layar"
        FluxaIcon.BOOK -> "Panduan"
        FluxaIcon.SAVE -> "Simpan"
        FluxaIcon.SHARE -> "Bagikan"
        FluxaIcon.EXPORT -> "Ekspor"
        FluxaIcon.SETTINGS -> "Pengaturan"
        FluxaIcon.HOME -> "Beranda"
        FluxaIcon.LAYER_UP -> "Naikkan layer"
        FluxaIcon.LAYER_DOWN -> "Turunkan layer"
        FluxaIcon.CHEVRON_DOWN -> "Buka"
        FluxaIcon.CHEVRON_RIGHT -> "Lanjut"
        FluxaIcon.CHECK -> "Selesai"
        FluxaIcon.KEYFRAME -> "Keyframe"
        else -> null
    }
}
