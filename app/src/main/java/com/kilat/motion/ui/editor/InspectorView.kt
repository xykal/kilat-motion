package com.kilat.motion.ui.editor

import com.kilat.motion.core.model.*
import com.kilat.motion.core.animation.Anim

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.kilat.motion.ui.components.KeyframeButton
import com.kilat.motion.ui.components.KilatButton
import com.kilat.motion.ui.components.KilatButtonStyle
import com.kilat.motion.ui.components.KilatChip
import com.kilat.motion.ui.components.KilatDropdown
import com.kilat.motion.ui.components.KilatIcon
import com.kilat.motion.ui.components.KilatIconButton
import com.kilat.motion.ui.components.KilatSlider
import com.kilat.motion.ui.components.kEditText
import kotlin.math.roundToInt

class InspectorView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onEdited(commit: Boolean)
        fun onRequestImage(layer: Layer)
        fun onDelete(layer: Layer)
        fun onDuplicate(layer: Layer)
        fun onApplyPreset(layer: Layer, preset: String)
        fun onClearKeys(layer: Layer)
        fun onSplit(layer: Layer)
        /** dir: +1 = maju (digambar lebih atas), -1 = mundur. */
        fun onReorder(layer: Layer, dir: Int)
    }

    var listener: Listener? = null

    private var proj: Project? = null
    private var cur: Layer? = null
    private var curPlayhead: Float = 0f
    private var binding = false
    private var defaultEase = "easeOut"

    private class RowRef(
        val key: String,
        val seek: KilatSlider?,
        val valueText: TextView?,
        val keyBtn: KeyframeButton,
        val min: Float,
        val step: Float,
        val isInt: Boolean,
        val layer: Layer
    )

    private val rows = ArrayList<RowRef>()
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val cText = 0xFFEEF2F8.toInt()
    private val cMuted = 0xFF98A3B8.toInt()
    private val cDim = 0xFF687389.toInt()
    private val cAccent = 0xFF2EE6C0.toInt()
    private val cCard = 0xFF151821.toInt()

    init {
        orientation = VERTICAL
        setPadding(dp(12f).toInt(), dp(6f).toInt(), dp(12f).toInt(), dp(20f).toInt())
    }

    // ---------------- helpers ----------------
    private fun roundRectBg(fill: Int, radius: Float = dp(12f), stroke: Int? = null): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = radius
        d.setColor(fill)
        if (stroke != null) d.setStroke(dp(1f).toInt(), stroke)
        return d
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(context)
        c.orientation = VERTICAL
        c.background = roundRectBg(cCard)
        c.setPadding(dp(12f).toInt(), dp(10f).toInt(), dp(12f).toInt(), dp(12f).toInt())
        val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8f).toInt()
        c.layoutParams = lp
        return c
    }

    private fun header(title: String, action: View? = null): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val tv = TextView(context)
        tv.text = title.uppercase()
        tv.setTextColor(cDim)
        tv.textSize = 9.5f
        tv.letterSpacing = 0.12f
        tv.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        row.addView(tv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (action != null) row.addView(action)
        val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(6f).toInt()
        row.layoutParams = lp
        return row
    }

    private fun miniBtn(label: String, onClick: () -> Unit): TextView {
        val b = TextView(context)
        b.text = label
        b.setTextColor(cMuted)
        b.textSize = 12f
        b.gravity = Gravity.CENTER
        b.background = roundRectBg(0xFF1B1F2B.toInt(), dp(7f))
        b.setPadding(dp(9f).toInt(), dp(4f).toInt(), dp(9f).toInt(), dp(4f).toInt())
        b.isClickable = true
        b.setOnClickListener { onClick() }
        return b
    }

    private fun miniIconBtn(icon: KilatIcon, tint: Int = cMuted, onClick: () -> Unit): KilatIconButton {
        val b = KilatIconButton(context, icon, tint, filled = true, sizeDp = 34f)
        b.layoutParams = LayoutParams(dp(34f).toInt(), dp(34f).toInt())
        b.setOnClickListener { onClick() }
        return b
    }

    private fun labelTv(text: String, size: Float = 11.5f, color: Int = cMuted, widthDp: Float? = null): TextView {
        val tv = TextView(context)
        tv.text = text
        tv.setTextColor(color)
        tv.textSize = size
        if (widthDp != null) tv.layoutParams = LayoutParams(dp(widthDp).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        return tv
    }

    private fun editText(value: String, numeric: Boolean, onDone: (String) -> Unit): EditText {
        val e = context.kEditText(value, numeric = numeric)
        e.setOnEditorActionListener { v, _, _ -> onDone(v.text.toString()); true }
        e.setOnFocusChangeListener { v, hasFocus -> if (!hasFocus) onDone((v as EditText).text.toString()) }
        return e
    }

    private fun dropdown(labels: List<String>, selected: Int, title: String = "Pilih", onChange: (Int) -> Unit): KilatDropdown {
        val d = KilatDropdown(context)
        d.sheetTitle = title
        d.setItems(labels, selected)
        d.onSelected = { idx -> if (!binding) onChange(idx) }
        return d
    }

    private fun swatchRow(selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        val colors = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF2EE6C0.toInt(), 0xFF59E3FF.toInt(),
            0xFF8B7CFF.toInt(), 0xFFFFD166.toInt(), 0xFFFF5C7A.toInt(), 0xFFFF8A3D.toInt(),
            0xFF22C55E.toInt(), 0xFF9FB0C9.toInt()
        )
        for (c in colors) {
            val v = View(context)
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(c)
            d.setStroke(dp(if (c == selected) 2.4f else 1f).toInt(), if (c == selected) cAccent else 0x33FFFFFF)
            v.background = d
            val lp = LayoutParams(dp(24f).toInt(), dp(24f).toInt())
            lp.rightMargin = dp(6f).toInt()
            row.addView(v, lp)
            v.setOnClickListener {
                onPick(c)
                listener?.onEdited(true)
                rebind()
            }
        }
        return row
    }

    // ---------------- build ----------------
    fun bind(project: Project?, layer: Layer?, playhead: Float) {
        proj = project
        cur = layer
        curPlayhead = playhead
        rebind()
    }

    fun updatePlayhead(t: Float) {
        curPlayhead = t
        syncValues()
    }

    private fun syncValues() {
        val l = cur ?: return
        binding = true
        for (r in rows) {
            val v = Anim.value(l, r.key, curPlayhead)
            r.valueText?.text = if (r.isInt) v.roundToInt().toString() else Format.dec(v, 2)
            if (r.seek != null) {
                val prog = ((v - r.min) / r.step).roundToInt().coerceIn(0, r.seek.max)
                r.seek.progress = prog
            }
            val hasKf = Anim.keyAt(l, r.key, curPlayhead) != null
            val any = l.channels[r.key]?.keys?.isNotEmpty() == true
            r.keyBtn.mode = if (hasKf) 2 else if (any) 1 else 0
        }
        binding = false
    }

    private fun rebind() {
        binding = true
        removeAllViews()
        rows.clear()
        val l = cur
        val p = proj
        if (l == null || p == null) {
            val hint = TextView(context)
            hint.text = "Belum ada layer terpilih.\n\nTambah layer dari tombol di bawah, atau ketuk bar layer di timeline untuk mengedit properti."
            hint.setTextColor(cDim)
            hint.textSize = 12f
            hint.setLineSpacing(dp(3f), 1f)
            hint.setPadding(dp(8f).toInt(), dp(24f).toInt(), dp(8f).toInt(), 0)
            addView(hint)
            binding = false
            return
        }

        // ---- header ----
        val head = LinearLayout(context)
        head.orientation = HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val nameEdit = editText(l.name, false) { s ->
            if (s.isNotBlank()) { l.name = s.trim(); listener?.onEdited(true) }
        }
        head.addView(nameEdit, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val dup = miniIconBtn(KilatIcon.COPY) { listener?.onDuplicate(l) }
        val del = miniIconBtn(KilatIcon.TRASH, 0xFFFF5C7A.toInt()) { listener?.onDelete(l) }
        val lpDup = LayoutParams(dp(34f).toInt(), dp(34f).toInt())
        lpDup.leftMargin = dp(6f).toInt()
        head.addView(dup, lpDup)
        val lpDel = LayoutParams(dp(34f).toInt(), dp(34f).toInt())
        lpDel.leftMargin = dp(6f).toInt()
        head.addView(del, lpDel)
        addView(head)

        val typeTv = TextView(context)
        val kindLabel = when (l.kind) {
            KIND_TEXT -> "TEKS"
            KIND_SHAPE -> "BENTUK"
            else -> "GAMBAR"
        }
        typeTv.text = "$kindLabel  |  ${Anim.allKeyTimes(l).size} keyframe"
        typeTv.setTextColor(cAccent)
        typeTv.textSize = 9.5f
        typeTv.letterSpacing = 0.1f
        val lpType = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lpType.topMargin = dp(6f).toInt()
        addView(typeTv, lpType)

        // ---- aksi layer: pisah di playhead + urutan tumpukan ----
        val actRow = LinearLayout(context)
        actRow.orientation = HORIZONTAL
        val splitBtn = KilatButton(context, "Pisah", KilatIcon.SCISSORS, KilatButtonStyle.GHOST, 11f)
        splitBtn.setOnClickListener { listener?.onSplit(l) }
        actRow.addView(splitBtn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val upBtn = KilatButton(context, "Maju", KilatIcon.LAYER_UP, KilatButtonStyle.GHOST, 11f)
        upBtn.setOnClickListener { listener?.onReorder(l, +1) }
        val lpUp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        lpUp.leftMargin = dp(7f).toInt()
        actRow.addView(upBtn, lpUp)
        val downBtn = KilatButton(context, "Mundur", KilatIcon.LAYER_DOWN, KilatButtonStyle.GHOST, 11f)
        downBtn.setOnClickListener { listener?.onReorder(l, -1) }
        val lpDown = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        lpDown.leftMargin = dp(7f).toInt()
        actRow.addView(downBtn, lpDown)
        val lpAct = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpAct.topMargin = dp(9f).toInt()
        addView(actRow, lpAct)

        // ---- timing ----
        val timing = card()
        timing.addView(header("Waktu Layer"))
        val timeRow = LinearLayout(context)
        timeRow.orientation = HORIZONTAL
        timeRow.gravity = Gravity.CENTER_VERTICAL
        timeRow.addView(labelTv("Mulai", widthDp = 44f))
        timeRow.addView(editText(fmt(l.start), true) { s ->
            s.toFloatOrNull()?.let {
                l.start = it.coerceIn(0f, maxOf(0f, p.duration - 0.2f))
                p.clampLayer(l)
                listener?.onEdited(true)
            }
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        timeRow.addView(labelTv("  Durasi", widthDp = 54f))
        timeRow.addView(editText(fmt(l.dur), true) { s ->
            s.toFloatOrNull()?.let {
                l.dur = it.coerceIn(0.2f, p.duration - l.start)
                listener?.onEdited(true)
            }
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        timing.addView(timeRow)
        addView(timing)

        // ---- tipe konten ----
        when (l.kind) {
            KIND_TEXT -> buildTextSection(l)
            KIND_SHAPE -> buildShapeSection(l)
            KIND_IMAGE -> buildImageSection(l)
        }

        // ---- preset ----
        val presetCard = card()
        presetCard.addView(header("Preset Animasi"))
        val chipRow = LinearLayout(context)
        chipRow.orientation = HORIZONTAL
        val scroll = android.widget.HorizontalScrollView(context)
        scroll.isHorizontalScrollBarEnabled = false
        for (id in Anim.PRESETS) {
            if (id == "typing" && l.kind != KIND_TEXT) continue
            if (id == "kenBurns" && l.kind != KIND_IMAGE) continue
            val chip = KilatChip(context, Anim.PRESET_LABELS[id] ?: id)
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(6f).toInt()
            chipRow.addView(chip, lp)
            chip.setOnClickListener { listener?.onApplyPreset(l, id) }
        }
        scroll.addView(chipRow)
        presetCard.addView(scroll)
        val easeRow = LinearLayout(context)
        easeRow.orientation = HORIZONTAL
        easeRow.gravity = Gravity.CENTER_VERTICAL
        val lpEase = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpEase.topMargin = dp(8f).toInt()
        easeRow.addView(labelTv("Easing baru", widthDp = 84f))
        val easeDrop = dropdown(Anim.EASES.map { Anim.EASE_LABELS[it] ?: it }, Anim.EASES.indexOf(defaultEase), "Easing keyframe") { idx ->
            defaultEase = Anim.EASES[idx]
        }
        easeRow.addView(easeDrop, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        presetCard.addView(easeRow, lpEase)
        addView(presetCard)

        // ---- transform ----
        val transform = card()
        val kfAction = miniIconBtn(KilatIcon.RESET) { listener?.onClearKeys(l) }
        transform.addView(header("Transform", kfAction))
        addProp(transform, l, "x", "Posisi X", 0f, p.width.toFloat(), 1f, false)
        addProp(transform, l, "y", "Posisi Y", 0f, p.height.toFloat(), 1f, false)
        addProp(transform, l, "scale", "Skala", 0.02f, 3f, 0.01f, false)
        addProp(transform, l, "rot", "Rotasi", -180f, 180f, 1f, true)
        addProp(transform, l, "alpha", "Opasitas", 0f, 1f, 0.01f, false)
        if (l.kind == KIND_TEXT) addProp(transform, l, "typing", "Teks muncul", 0f, 1f, 0.01f, false)
        val centerBtn = KilatButton(context, "Tengahkan di Kanvas", KilatIcon.CROSSHAIR, KilatButtonStyle.SOFT, 11.5f)
        centerBtn.setOnClickListener {
            applyValue(l, "x", p.width / 2f)
            applyValue(l, "y", p.height / 2f)
            listener?.onEdited(true)
            syncValues()
        }
        val lpCenter = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpCenter.topMargin = dp(10f).toInt()
        transform.addView(centerBtn, lpCenter)
        addView(transform)

        // ---- efek ----
        val fx = card()
        fx.addView(header("Filter & Efek"))
        addProp(fx, l, "fx.blur", "Blur", 0f, 40f, 0.5f, false)
        addProp(fx, l, "fx.brightness", "Brightness", 20f, 200f, 1f, true)
        addProp(fx, l, "fx.contrast", "Contrast", 20f, 200f, 1f, true)
        addProp(fx, l, "fx.saturate", "Saturasi", 0f, 200f, 1f, true)
        addProp(fx, l, "fx.hue", "Hue", -180f, 180f, 1f, true)
        addProp(fx, l, "fx.glow", "Glow", 0f, 60f, 1f, true)
        val glowLbl = labelTv("Warna glow", widthDp = 84f)
        val lpGlow = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lpGlow.topMargin = dp(8f).toInt()
        fx.addView(glowLbl, lpGlow)
        val glowSw = swatchRow(l.glowColor) { c -> l.glowColor = c }
        val lpSw = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lpSw.topMargin = dp(6f).toInt()
        fx.addView(glowSw, lpSw)
        val blendRow = LinearLayout(context)
        blendRow.orientation = HORIZONTAL
        blendRow.gravity = Gravity.CENTER_VERTICAL
        val lpBlend = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpBlend.topMargin = dp(10f).toInt()
        blendRow.addView(labelTv("Blend", widthDp = 84f))
        val blendDrop = dropdown(BLEND_MODES.map { BLEND_LABELS[it] ?: it }, BLEND_MODES.indexOf(l.blend), "Blend mode") { idx ->
            l.blend = BLEND_MODES[idx]
            listener?.onEdited(true)
        }
        blendRow.addView(blendDrop, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        fx.addView(blendRow, lpBlend)
        addView(fx)

        if (l.kind == KIND_IMAGE) {
            val imgCard = card()
            imgCard.addView(header("Gambar"))
            val btn = miniBtn("Ganti gambar") { listener?.onRequestImage(l) }
            val lpBtn = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lpBtn.topMargin = dp(4f).toInt()
            imgCard.addView(btn, lpBtn)
            addView(imgCard)
        }

        binding = false
        syncValues()
    }

    private fun fmt(v: Float): String {
        val r = Math.round(v * 10f) / 10f
        return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
    }

    private fun buildTextSection(l: Layer) {
        val c = card()
        c.addView(header("Konten Teks"))
        val content = EditText(context)
        content.setText(l.text)
        content.setTextColor(cText)
        content.textSize = 12.5f
        content.minLines = 2
        content.gravity = Gravity.TOP or Gravity.START
        content.background = roundRectBg(0xFF11141C.toInt(), dp(8f))
        content.setPadding(dp(9f).toInt(), dp(8f).toInt(), dp(9f).toInt(), dp(8f).toInt())
        content.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        content.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                l.text = content.text.toString()
                listener?.onEdited(true)
            }
        }
        c.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, dp(64f).toInt()))

        val fontRow = LinearLayout(context)
        fontRow.orientation = HORIZONTAL
        fontRow.gravity = Gravity.CENTER_VERTICAL
        val lpFont = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpFont.topMargin = dp(8f).toInt()
        fontRow.addView(labelTv("Font", widthDp = 50f))
        val fontDrop = dropdown(FONT_KEYS.map { FONT_LABELS[it] ?: it }, FONT_KEYS.indexOf(l.fontKey), "Pilih font") { idx ->
            l.fontKey = FONT_KEYS[idx]
            listener?.onEdited(true)
        }
        fontRow.addView(fontDrop, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        c.addView(fontRow, lpFont)

        val sizeRow = LinearLayout(context)
        sizeRow.orientation = HORIZONTAL
        sizeRow.gravity = Gravity.CENTER_VERTICAL
        val lpSize = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpSize.topMargin = dp(6f).toInt()
        sizeRow.addView(labelTv("Ukuran", widthDp = 50f))
        val sizeSeek = KilatSlider(context)
        sizeSeek.max = 400 - 8
        sizeSeek.progress = (l.textSize.toInt() - 8).coerceIn(0, sizeSeek.max)
        val sizeVal = labelTv(l.textSize.toInt().toString(), 11f, cText)
        sizeSeek.onProgress = { progress ->
            l.textSize = (progress + 8).toFloat()
            sizeVal.text = l.textSize.toInt().toString()
            listener?.onEdited(false)
        }
        sizeSeek.onReleased = { listener?.onEdited(true) }
        sizeRow.addView(sizeSeek, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        sizeRow.addView(sizeVal, LayoutParams(dp(36f).toInt(), LayoutParams.WRAP_CONTENT))
        c.addView(sizeRow, lpSize)

        val alignRow = LinearLayout(context)
        alignRow.orientation = HORIZONTAL
        alignRow.gravity = Gravity.CENTER_VERTICAL
        val lpAlign = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpAlign.topMargin = dp(6f).toInt()
        alignRow.addView(labelTv("Rata", widthDp = 50f))
        val aligns = listOf("left", "center", "right")
        val alignLabels = listOf("Kiri", "Tengah", "Kanan")
        for (i in aligns.indices) {
            val b = TextView(context)
            b.text = alignLabels[i]
            b.textSize = 10.5f
            b.gravity = Gravity.CENTER
            val active = l.align == aligns[i]
            b.setTextColor(if (active) cText else cMuted)
            b.background = roundRectBg(if (active) 0xFF1B1F2B.toInt() else 0xFF0A0C11.toInt(), dp(7f))
            b.setPadding(dp(10f).toInt(), dp(6f).toInt(), dp(10f).toInt(), dp(6f).toInt())
            val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            lp.rightMargin = dp(5f).toInt()
            alignRow.addView(b, lp)
            b.setOnClickListener {
                l.align = aligns[i]
                listener?.onEdited(true)
                rebind()
            }
        }
        c.addView(alignRow, lpAlign)

        val colorLbl = labelTv("Warna", widthDp = 84f)
        val lpColor = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lpColor.topMargin = dp(10f).toInt()
        c.addView(colorLbl, lpColor)
        val sw = swatchRow(l.color) { col -> l.color = col }
        val lpSw = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lpSw.topMargin = dp(6f).toInt()
        c.addView(sw, lpSw)

        val strokeRow = LinearLayout(context)
        strokeRow.orientation = HORIZONTAL
        strokeRow.gravity = Gravity.CENTER_VERTICAL
        val lpStroke = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpStroke.topMargin = dp(8f).toInt()
        strokeRow.addView(labelTv("Outline", widthDp = 50f))
        val strokeSeek = KilatSlider(context)
        strokeSeek.max = 40
        strokeSeek.progress = l.strokeW.toInt().coerceIn(0, 40)
        val strokeVal = labelTv(l.strokeW.toInt().toString(), 11f, cText)
        strokeSeek.onProgress = { progress ->
            l.strokeW = progress.toFloat()
            strokeVal.text = progress.toString()
            listener?.onEdited(false)
        }
        strokeSeek.onReleased = { listener?.onEdited(true) }
        strokeRow.addView(strokeSeek, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        strokeRow.addView(strokeVal, LayoutParams(dp(36f).toInt(), LayoutParams.WRAP_CONTENT))
        c.addView(strokeRow, lpStroke)
        val strokeSw = swatchRow(l.strokeColor) { col -> l.strokeColor = col }
        val lpSSw = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lpSSw.topMargin = dp(6f).toInt()
        c.addView(strokeSw, lpSSw)
        addView(c)
    }

    private fun buildShapeSection(l: Layer) {
        val c = card()
        c.addView(header("Bentuk"))
        val typeRow = LinearLayout(context)
        typeRow.orientation = HORIZONTAL
        typeRow.gravity = Gravity.CENTER_VERTICAL
        typeRow.addView(labelTv("Jenis", widthDp = 60f))
        val sp = dropdown(SHAPE_TYPES.map { SHAPE_LABELS[it] ?: it }, SHAPE_TYPES.indexOf(l.shape), "Jenis bentuk") { idx ->
            l.shape = SHAPE_TYPES[idx]
            listener?.onEdited(true)
        }
        typeRow.addView(sp, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        c.addView(typeRow)

        fun dimRow(label: String, value: Float, maxVal: Int, onChange: (Float) -> Unit) {
            val row = LinearLayout(context)
            row.orientation = HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(6f).toInt()
            row.addView(labelTv(label, widthDp = 60f))
            val seek = KilatSlider(context)
            seek.max = maxVal
            seek.progress = value.toInt().coerceIn(0, maxVal)
            val valTv = labelTv(value.toInt().toString(), 11f, cText)
            seek.onProgress = { progress ->
                onChange(progress.toFloat())
                valTv.text = progress.toString()
                listener?.onEdited(false)
            }
            seek.onReleased = { listener?.onEdited(true) }
            row.addView(seek, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            row.addView(valTv, LayoutParams(dp(36f).toInt(), LayoutParams.WRAP_CONTENT))
            c.addView(row, lp)
        }
        dimRow("Lebar", l.w, 2000) { l.w = maxOf(10f, it) }
        dimRow("Tinggi", l.h, 2000) { l.h = maxOf(10f, it) }
        dimRow("Radius", l.radius, 400) { l.radius = it }

        val fillLbl = labelTv("Isi", widthDp = 84f)
        val lpFill = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lpFill.topMargin = dp(10f).toInt()
        c.addView(fillLbl, lpFill)
        val sw = swatchRow(l.fill) { col -> l.fill = col }
        val lpSw = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lpSw.topMargin = dp(6f).toInt()
        c.addView(sw, lpSw)
        addView(c)
    }

    private fun buildImageSection(l: Layer) {
        val c = card()
        c.addView(header("Gambar"))
        fun dimRow(label: String, value: Float, maxVal: Int, onChange: (Float) -> Unit) {
            val row = LinearLayout(context)
            row.orientation = HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(6f).toInt()
            row.addView(labelTv(label, widthDp = 60f))
            val seek = KilatSlider(context)
            seek.max = maxVal
            seek.progress = value.toInt().coerceIn(0, maxVal)
            val valTv = labelTv(value.toInt().toString(), 11f, cText)
            seek.onProgress = { progress ->
                onChange(progress.toFloat())
                valTv.text = progress.toString()
                listener?.onEdited(false)
            }
            seek.onReleased = { listener?.onEdited(true) }
            row.addView(seek, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            row.addView(valTv, LayoutParams(dp(36f).toInt(), LayoutParams.WRAP_CONTENT))
            c.addView(row, lp)
        }
        dimRow("Lebar", l.imgW, 2000) { l.imgW = maxOf(10f, it) }
        dimRow("Tinggi", l.imgH, 2000) { l.imgH = maxOf(10f, it) }
        addView(c)
    }

    // ---------------- animated prop row ----------------
    private fun addProp(parent: LinearLayout, l: Layer, key: String, label: String, minV: Float, maxV: Float, step: Float, isInt: Boolean) {
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(4f).toInt()
        parent.addView(row, lp)

        row.addView(labelTv(label, widthDp = 74f))
        val seek = KilatSlider(context)
        seek.max = (((maxV - minV) / step).roundToInt()).coerceAtLeast(1)
        val valTv = labelTv("", 11f, cText, 40f)
        val keyBtn = KeyframeButton(context)

        row.addView(seek, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(valTv)
        row.addView(keyBtn, LayoutParams(dp(30f).toInt(), dp(30f).toInt()))

        val ref = RowRef(key, seek, valTv, keyBtn, minV, step, isInt, l)
        rows.add(ref)

        seek.onProgress = { progress ->
            if (!binding) {
                val v = minV + progress * step
                applyValue(l, key, v)
                valTv.text = if (isInt) v.roundToInt().toString() else Format.dec(v, 2)
                listener?.onEdited(false)
            }
        }
        seek.onReleased = { listener?.onEdited(true) }
        keyBtn.setOnClickListener {
            val t = curPlayhead
            if (Anim.keyAt(l, key, t) != null) {
                Anim.removeKey(l, key, t)
            } else {
                Anim.setKey(l, key, t, Anim.value(l, key, t), defaultEase)
            }
            listener?.onEdited(true)
            syncValues()
        }
    }

    private fun applyValue(l: Layer, key: String, v: Float) {
        val ch = l.channels[key]
        if (ch != null && ch.keys.isNotEmpty()) Anim.setKey(l, key, curPlayhead, v, defaultEase)
        else l.setStatic(key, v)
    }
}
