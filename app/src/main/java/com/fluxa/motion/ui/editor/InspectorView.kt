package com.fluxa.motion.ui.editor

import com.fluxa.motion.core.render.EffectPreview
import com.fluxa.motion.core.model.*
import com.fluxa.motion.core.animation.Anim
import com.fluxa.motion.core.animation.AutoPattern
import com.fluxa.motion.core.animation.BeatKeys

import android.app.Activity
import android.app.AlertDialog
import com.fluxa.motion.core.render.EffectMath
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.fluxa.motion.core.render.Renderer
import com.fluxa.motion.ui.components.IconView
import com.fluxa.motion.ui.components.FluxaTabBar
import com.fluxa.motion.ui.components.KeyframeButton
import com.fluxa.motion.ui.components.RulerSlider
import com.fluxa.motion.ui.components.FluxaColorPicker
import com.fluxa.motion.ui.components.FluxaSwitch
import com.fluxa.motion.ui.components.FluxaButton
import com.fluxa.motion.ui.components.FluxaButtonStyle
import com.fluxa.motion.ui.components.FluxaChip
import com.fluxa.motion.ui.components.LayerPreviewView
import com.fluxa.motion.ui.components.FluxaDropdown
import com.fluxa.motion.ui.components.FluxaIcon
import com.fluxa.motion.ui.components.FluxaIconButton
import com.fluxa.motion.ui.components.FluxaSlider
import com.fluxa.motion.ui.components.FluxaVisualPicker
import com.fluxa.motion.ui.components.PickerArt
import com.fluxa.motion.ui.components.kEditText
import kotlin.math.roundToInt
import com.fluxa.motion.ui.components.SliderRange

class InspectorView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onEdited(commit: Boolean)
        fun onRequestImage(layer: Layer)
        /** Minta media untuk isi shape. mode: "image" atau "video". */
        fun onRequestFill(layer: Layer, mode: String)
        fun onDelete(layer: Layer)
        fun onDuplicate(layer: Layer)
        fun onApplyPreset(layer: Layer, preset: String)
        fun onClearKeys(layer: Layer)
        /** Minta file font .ttf/.otf untuk layer teks. */
        fun onRequestFontImport(layer: Layer)
        /** Hapus background gambar layer (MediaPipe). */
        fun onRemoveBackground(layer: Layer)
        /** Kembalikan gambar asli sebelum hapus background. */
        fun onRestoreBackground(layer: Layer)
        fun onJumpKey(layer: Layer, dir: Int)
        fun onSplit(layer: Layer)
        /** dir: +1 = maju (digambar lebih atas), -1 = mundur. */
        fun onReorder(layer: Layer, dir: Int)
        /** Pasang keyframe otomatis. key: "t:<POLA>" atau "b:<GAYA>". rate: indeks rapat 0..3. */
        fun onAutoKeys(layer: Layer, key: String, intensity: Float, rate: Int)
    }

    var listener: Listener? = null

    private var proj: Project? = null
    private var cur: Layer? = null
    private var curPlayhead: Float = 0f
    private var binding = false
    private var defaultEase = "easeOut"
    private var autoKey = "t:ZOOM_IN"
    private var autoIntensity = 1f
    private var autoRate = 2
    private var activeCat = "Konten"
    private var keyClipboard: Map<String, Anim.KeyCopy> = emptyMap()
    private var filterSample: Bitmap? = null
    private val filterThumbs = HashMap<String, Bitmap>()

    private class RowRef(
        val key: String,
        val seek: FluxaSlider?,
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
        c.background = roundRectBg(cCard, radius = dp(14f))
        c.setPadding(dp(14f).toInt(), dp(12f).toInt(), dp(14f).toInt(), dp(14f).toInt())
        val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10f).toInt()
        c.layoutParams = lp
        return c
    }

    private fun header(title: String, action: View? = null): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val tv = TextView(context)
        tv.text = title
        tv.setTextColor(cText)
        tv.textSize = 13f
        tv.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
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

    private fun miniIconBtn(icon: FluxaIcon, tint: Int = cMuted, onClick: () -> Unit): FluxaIconButton {
        val b = FluxaIconButton(context, icon, tint, filled = true, sizeDp = 34f)
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

    private fun dropdown(labels: List<String>, selected: Int, title: String = "Pilih", onChange: (Int) -> Unit): FluxaDropdown {
        val d = FluxaDropdown(context)
        d.sheetTitle = title
        d.setItems(labels, selected)
        d.onSelected = { idx -> if (!binding) onChange(idx) }
        return d
    }

    /** Pilihan visual (tile bergambar, tanpa teks). Mengikuti aturan binding seperti dropdown. */
    private fun visualPicker(
        keys: List<String>,
        selected: String,
        desc: (String) -> String,
        art: (Canvas, RectF, String) -> Unit,
        onChange: (String) -> Unit
    ): FluxaVisualPicker {
        val p = FluxaVisualPicker(context)
        p.setItems(keys, selected, desc, art)
        p.onPick = { k -> if (!binding) onChange(k) }
        return p
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
            d.setStroke(dp(if (c == selected) 2.4f else 1f).toInt(), if (c == selected) cAccent else 0xFF2A3040.toInt())
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
        // tile pelangi: buka color picker custom (HSV + alpha + hex)
        val custom = TextView(context)
        custom.text = "+"
        custom.textSize = 15f
        custom.gravity = Gravity.CENTER
        custom.setTextColor(cText)
        val cd = GradientDrawable()
        cd.shape = GradientDrawable.OVAL
        cd.setColor(0xFF171B27.toInt())
        cd.setStroke(dp(1.6f).toInt(), cAccent)
        custom.background = cd
        val lpC = LayoutParams(dp(24f).toInt(), dp(24f).toInt())
        row.addView(custom, lpC)
        custom.setOnClickListener {
            val act = context as? Activity ?: return@setOnClickListener
            FluxaColorPicker.show(act, selected) { col ->
                onPick(col)
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

    private val rulerOf = HashMap<Any, RulerSlider>()
    private var pendingRuler: RulerSlider? = null

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
            rulerOf[r]?.let { if (!it.dragging) it.value = v }
            val hasKf = Anim.keyAt(l, r.key, curPlayhead) != null
            val any = l.channels[r.key]?.keys?.isNotEmpty() == true
            r.keyBtn.mode = if (hasKf) 2 else if (any) 1 else 0
        }
        binding = false
    }

    private fun rebind() {
        binding = true
        removeAllViews()
        rows.clear(); rulerOf.clear()
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
        val dup = miniIconBtn(FluxaIcon.COPY) { listener?.onDuplicate(l) }
        val del = miniIconBtn(FluxaIcon.TRASH, 0xFFFF5C7A.toInt()) { listener?.onDelete(l) }
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
            KIND_VECTOR -> "VEKTOR"
            KIND_SVG -> "SVG"
            KIND_3D -> "OBJEK 3D"
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
        val splitBtn = FluxaButton(context, "Pisah", FluxaIcon.SCISSORS, FluxaButtonStyle.GHOST, 11f)
        splitBtn.setOnClickListener { listener?.onSplit(l) }
        actRow.addView(splitBtn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val upBtn = FluxaButton(context, "Maju", FluxaIcon.LAYER_UP, FluxaButtonStyle.GHOST, 11f)
        upBtn.setOnClickListener { listener?.onReorder(l, +1) }
        val lpUp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        lpUp.leftMargin = dp(7f).toInt()
        actRow.addView(upBtn, lpUp)
        val downBtn = FluxaButton(context, "Mundur", FluxaIcon.LAYER_DOWN, FluxaButtonStyle.GHOST, 11f)
        downBtn.setOnClickListener { listener?.onReorder(l, -1) }
        val lpDown = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        lpDown.leftMargin = dp(7f).toInt()
        actRow.addView(downBtn, lpDown)
        val lpAct = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpAct.topMargin = dp(9f).toInt()
        addView(actRow, lpAct)

        // ---- tab kategori (konten/transform/animasi/efek dipisah, tidak digabung) ----
        val catNames = listOf("Konten", "Transform", "Animasi", "Efek", "Warna", "Gaya", "3D")
        if (activeCat !in catNames) activeCat = "Konten"
        val catBoxes = LinkedHashMap<String, LinearLayout>()
        val catIcons = mapOf(
            "Konten" to FluxaIcon.SHAPE,
            "Transform" to FluxaIcon.TRANSFORM,
            "Animasi" to FluxaIcon.ANIMATE,
            "Efek" to FluxaIcon.SPARK,
            "Warna" to FluxaIcon.COLOR,
            "Gaya" to FluxaIcon.STYLE,
            "3D" to FluxaIcon.CUBE
        )
        val tabs = FluxaTabBar(
            context,
            catNames.map { (catIcons[it] ?: FluxaIcon.SHAPE) to it },
            catNames.indexOf(activeCat)
        ) { idx ->
            activeCat = catNames[idx]
            for ((n2, box2) in catBoxes) box2.visibility = if (n2 == activeCat) View.VISIBLE else View.GONE
        }
        val chipScroll = android.widget.HorizontalScrollView(context)
        chipScroll.isHorizontalScrollBarEnabled = false
        chipScroll.addView(tabs)
        val lpChipScroll = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpChipScroll.topMargin = dp(10f).toInt()
        addView(chipScroll, lpChipScroll)
        for (cn in catNames) {
            val box = LinearLayout(context)
            box.orientation = VERTICAL
            box.visibility = if (cn == activeCat) View.VISIBLE else View.GONE
            catBoxes[cn] = box
            addView(box, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        val kontenCat = catBoxes["Konten"]!!
        val transCat = catBoxes["Transform"]!!
        val animCat = catBoxes["Animasi"]!!
        val efekCat = catBoxes["Efek"]!!
        val warnaCat = catBoxes["Warna"]!!
        val gayaCat = catBoxes["Gaya"]!!
        val d3Cat = catBoxes["3D"]!!

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
        animCat.addView(timing)

        // ---- tipe konten ----
        when (l.kind) {
            KIND_TEXT -> buildTextSection(l, kontenCat)
            KIND_SHAPE -> buildShapeSection(l, kontenCat)
            KIND_IMAGE -> buildImageSection(l, kontenCat)
            KIND_VECTOR -> buildVectorSection(l, kontenCat)
            KIND_SVG -> buildSvgSection(l, kontenCat)
            KIND_3D -> build3DSection(l, kontenCat)
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
            val tile = LinearLayout(context)
            tile.orientation = VERTICAL
            tile.gravity = Gravity.CENTER_HORIZONTAL
            tile.isClickable = true
            val box = android.widget.FrameLayout(context)
            box.background = roundRectBg(0xFF11141C.toInt(), dp(12f))
            box.addView(
                LayerPreviewView(context).also { it.showPreset(id) },
                android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            tile.addView(box, LayoutParams(dp(68f).toInt(), dp(46f).toInt()))
            tile.contentDescription = Anim.PRESET_LABELS[id] ?: id
            val lp = LayoutParams(dp(68f).toInt(), LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(7f).toInt()
            chipRow.addView(tile, lp)
            tile.setOnClickListener { listener?.onApplyPreset(l, id) }
        }
        scroll.addView(chipRow)
        presetCard.addView(scroll)
        val easeRow = LinearLayout(context)
        easeRow.orientation = HORIZONTAL
        easeRow.gravity = Gravity.CENTER_VERTICAL
        val lpEase = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpEase.topMargin = dp(8f).toInt()
        easeRow.addView(labelTv("Easing baru", widthDp = 84f))
        val easeDrop = visualPicker(Anim.EASES, defaultEase, { Anim.EASE_LABELS[it] ?: it }, { c, r, k -> PickerArt.easing(c, r, k) }) { k ->
            defaultEase = k
        }
        easeRow.addView(easeDrop, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        presetCard.addView(easeRow, lpEase)
        animCat.addView(presetCard)
        buildAutoKeyCard(l, animCat)
        buildCurveCard(l, animCat)

        // ---- transform ----
        val transform = card()
        val kfAction = miniIconBtn(FluxaIcon.RESET) { listener?.onClearKeys(l) }
        transform.addView(header("Transform", kfAction))
        addProp(transform, l, "x", "Posisi X", 0f, p.width.toFloat(), 1f, false)
        addProp(transform, l, "y", "Posisi Y", 0f, p.height.toFloat(), 1f, false)
        addProp(transform, l, "scale", "Skala", 0.02f, 3f, 0.01f, false)
        addProp(transform, l, "scaleX", "Skala X", -3f, 3f, 0.01f, false)
        addProp(transform, l, "scaleY", "Skala Y", -3f, 3f, 0.01f, false)
        addProp(transform, l, "skewX", "Miring X", -1f, 1f, 0.01f, false)
        addProp(transform, l, "skewY", "Miring Y", -1f, 1f, 0.01f, false)
        addProp(transform, l, "rot", "Rotasi", -180f, 180f, 1f, true)
        addProp(transform, l, "alpha", "Opasitas", 0f, 1f, 0.01f, false)
        // preset squash & stretch: skala X melebar & Y menyempit tiap 0,5 detik, lalu kembali
        val ssBtn = TextView(context)
        ssBtn.text = "Squash & stretch"
        ssBtn.setTextColor(com.fluxa.motion.ui.theme.FluxaColors.Accent)
        ssBtn.textSize = 11f
        ssBtn.typeface = android.graphics.Typeface.DEFAULT_BOLD
        ssBtn.gravity = Gravity.CENTER
        ssBtn.setPadding(dp(12f).toInt(), dp(7f).toInt(), dp(12f).toInt(), dp(7f).toInt())
        ssBtn.background = GradientDrawable().apply { cornerRadius = dp(10f); setColor(com.fluxa.motion.ui.theme.FluxaColors.Field) }
        ssBtn.setOnClickListener {
            applySquashStretch(l, l.dur)
            listener?.onEdited(true)
            rebind()
        }
        transform.addView(ssBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f).toInt() })
        if (l.kind == KIND_TEXT) addProp(transform, l, "typing", "Teks muncul", 0f, 1f, 0.01f, false)
        val flipRow = LinearLayout(context)
        flipRow.orientation = HORIZONTAL
        val lpFlip = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpFlip.topMargin = dp(10f).toInt()
        val flipH = FluxaButton(context, "Balik Kiri-Kanan", FluxaIcon.COPY, FluxaButtonStyle.GHOST, 11f)
        flipH.setOnClickListener {
            applyValue(l, "scaleX", -Anim.value(l, "scaleX", curPlayhead))
            listener?.onEdited(true); syncValues()
        }
        flipRow.addView(flipH, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val flipV = FluxaButton(context, "Balik Atas-Bawah", FluxaIcon.COPY, FluxaButtonStyle.GHOST, 11f)
        flipV.setOnClickListener {
            applyValue(l, "scaleY", -Anim.value(l, "scaleY", curPlayhead))
            listener?.onEdited(true); syncValues()
        }
        val lpFlipV = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        lpFlipV.leftMargin = dp(7f).toInt()
        flipRow.addView(flipV, lpFlipV)
        transform.addView(flipRow, lpFlip)
        val centerBtn = FluxaButton(context, "Tengahkan di Kanvas", FluxaIcon.CROSSHAIR, FluxaButtonStyle.SOFT, 11.5f)
        centerBtn.setOnClickListener {
            applyValue(l, "x", p.width / 2f)
            applyValue(l, "y", p.height / 2f)
            listener?.onEdited(true)
            syncValues()
        }
        val lpCenter = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpCenter.topMargin = dp(10f).toInt()
        transform.addView(centerBtn, lpCenter)
        transCat.addView(transform)

        // ---- efek ----
        val fx = card()
        fx.addView(header("Efek Cahaya"))
        addProp(fx, l, "fx.blur", "Blur", 0f, 40f, 0.5f, false)
        addProp(fx, l, "fx.glow", "Glow", 0f, 60f, 1f, true)
        fx.addView(header("Blur layer"))
        val lpBlur = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpBlur.topMargin = dp(6f).toInt()
        fx.addView(blurStrip(l), lpBlur)
        staticSlider(fx, "Jumlah", l.blurAmount, 200, 1f) { l.blurAmount = it; listener?.onEdited(true) }
        fx.addView(header("Kaca (glass)"))
        staticSlider(fx, "Blur latar", l.fxGlass, 60, 1f) { l.fxGlass = it; listener?.onEdited(true) }
        staticSlider(fx, "Tint putih", l.glassTint, 100, 1f) { l.glassTint = it; listener?.onEdited(true) }
        staticSlider(fx, "Border", l.glassBorder, 8, 1f) { l.glassBorder = it; listener?.onEdited(true) }
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
        val blendDrop = visualPicker(BLEND_MODES, l.blend, { BLEND_LABELS[it] ?: it }, { c, r, k -> PickerArt.blend(c, r, k) }) { k ->
            l.blend = k
            listener?.onEdited(true)
        }
        blendRow.addView(blendDrop, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        fx.addView(blendRow, lpBlend)
        efekCat.addView(fx)
        val stackCard = card()
        buildEffectStack(l, stackCard)
        efekCat.addView(stackCard)
        buildColorCard(l, warnaCat)
        buildStyleCard(l, gayaCat)
        build3DCard(l, d3Cat)

        if (l.kind == KIND_IMAGE) {
            val imgCard = card()
            imgCard.addView(header("Gambar"))
            val btn = miniBtn("Ganti gambar") { listener?.onRequestImage(l) }
            val lpBtn = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lpBtn.topMargin = dp(4f).toInt()
            imgCard.addView(btn, lpBtn)
            val bgRow = LinearLayout(context)
            bgRow.orientation = HORIZONTAL
            bgRow.gravity = Gravity.CENTER_VERTICAL
            val bgBtn = miniBtn("Hapus background") { listener?.onRemoveBackground(l) }
            bgRow.addView(bgBtn)
            if (l.imgOrigPath.isNotEmpty()) {
                val restore = miniBtn("Kembalikan asli") { listener?.onRestoreBackground(l) }
                val lpR = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                lpR.marginStart = dp(6f).toInt()
                bgRow.addView(restore, lpR)
            }
            val lpBg = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lpBg.topMargin = dp(6f).toInt()
            imgCard.addView(bgRow, lpBg)
            val hintBg = labelTv("Untuk gambar orang/wajah. Hasil disimpan sebagai PNG transparan.", 10f, cDim)
            val lpHb = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lpHb.topMargin = dp(4f).toInt()
            imgCard.addView(hintBg, lpHb)
            kontenCat.addView(imgCard)
        }

        binding = false
        syncValues()
    }

    private fun fmt(v: Float): String {
        val r = Math.round(v * 10f) / 10f
        return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
    }

    private fun buildTextSection(l: Layer, parent: LinearLayout) {
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
        val fontKeys = FONT_KEYS + com.fluxa.motion.core.render.FontBank.customKeys()
        val fontDrop = visualPicker(fontKeys, l.fontKey, { FONT_LABELS[it] ?: com.fluxa.motion.core.render.FontBank.labelOf(it) }, { c, r, k -> PickerArt.font(c, r, k) }) { k ->
            l.fontKey = k
            listener?.onEdited(true)
        }
        fontRow.addView(fontDrop, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        c.addView(fontRow, lpFont)
        val importFontRow = LinearLayout(context)
        importFontRow.orientation = HORIZONTAL
        importFontRow.gravity = Gravity.CENTER_VERTICAL
        val lpImportFont = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpImportFont.topMargin = dp(6f).toInt()
        importFontRow.addView(labelTv("", widthDp = 50f))
        importFontRow.addView(miniButton("Impor font .ttf / .otf") { listener?.onRequestFontImport(l) })
        c.addView(importFontRow, lpImportFont)

        val sizeRow = LinearLayout(context)
        sizeRow.orientation = HORIZONTAL
        sizeRow.gravity = Gravity.CENTER_VERTICAL
        val lpSize = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpSize.topMargin = dp(6f).toInt()
        sizeRow.addView(labelTv("Ukuran", widthDp = 50f))
        val sizeSeek = FluxaSlider(context)
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
        staticSlider(c, "Spasi huruf", l.letterSpacing, 60, 100f) { l.letterSpacing = it }
        staticSlider(c, "Spasi baris", l.lineSpacing, 120, 100f, 0.8f) { l.lineSpacing = it }

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
        val strokeSeek = FluxaSlider(context)
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
        parent.addView(c)
    }

    private fun buildShapeSection(l: Layer, parent: LinearLayout) {
        val c = card()
        c.addView(header("Bentuk"))
        val typeRow = LinearLayout(context)
        typeRow.orientation = HORIZONTAL
        typeRow.gravity = Gravity.CENTER_VERTICAL
        typeRow.addView(labelTv("Jenis", widthDp = 60f))
        val sp = visualPicker(SHAPE_TYPES, l.shape, { SHAPE_LABELS[it] ?: it }, { c, r, k -> PickerArt.shape(c, r, k) }) { k ->
            l.shape = k
            listener?.onEdited(true)
            rebind()
        }
        typeRow.addView(sp, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        c.addView(typeRow)

        val fillRow = LinearLayout(context)
        fillRow.orientation = HORIZONTAL
        fillRow.gravity = Gravity.CENTER_VERTICAL
        val lpFill = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpFill.topMargin = dp(8f).toInt()
        fillRow.addView(labelTv("Isi", widthDp = 60f))
        val fillPicker = visualPicker(
            listOf("color", "image", "video"), l.fillMode,
            { k -> when (k) { "image" -> "Isi gambar"; "video" -> "Isi video"; else -> "Warna" } },
            { cv, r, k -> PickerArt.fillKind(cv, r, k, l.fill) }
        ) { k ->
            if (k == "color") {
                l.fillMode = "color"
                listener?.onEdited(true)
                rebind()
            } else {
                listener?.onRequestFill(l, k)
            }
        }
        fillRow.addView(fillPicker, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        c.addView(fillRow, lpFill)
        if (l.fillMode == "color") {
            val sw = swatchRow(l.fill) { col -> l.fill = col }
            val lpSw = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lpSw.topMargin = dp(6f).toInt()
            c.addView(sw, lpSw)
        }

        // kecepatan putar media (video/gambar bergerak)
        if (l.imgPath.isNotEmpty()) {
            val spRow = LinearLayout(context)
            spRow.orientation = HORIZONTAL
            spRow.gravity = Gravity.CENTER_VERTICAL
            val lpSp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lpSp.topMargin = dp(8f).toInt()
            spRow.addView(labelTv("Kecepatan", widthDp = 60f))
            val speedRuler = RulerSlider(context)
            speedRuler.step = 0.05f
            speedRuler.value = l.speed
            val speedTv = labelTv(Format.dec(l.speed, 2) + "x", widthDp = 52f)
            speedRuler.onChange = { v, commit ->
                val nv = v.coerceIn(0.1f, 8f)
                l.speed = nv
                speedTv.text = Format.dec(nv, 2) + "x"
                listener?.onEdited(commit)
            }
            spRow.addView(speedRuler, LayoutParams(0, dp(34f).toInt(), 1f))
            spRow.addView(speedTv, LayoutParams(dp(52f).toInt(), LayoutParams.WRAP_CONTENT))
            c.addView(spRow, lpSp)
        }

        fun dimRow(label: String, value: Float, maxVal: Int, onChange: (Float) -> Unit) {
            val row = LinearLayout(context)
            row.orientation = HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(6f).toInt()
            row.addView(labelTv(label, widthDp = 60f))
            val seek = FluxaSlider(context)
            val rangeMax = SliderRange.dimMax(value, maxVal)
            seek.max = rangeMax
            seek.progress = SliderRange.progressOf(value, rangeMax)
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
        if (l.shape == "polygon") {
            dimRow("Jumlah sisi", l.shapeSides.toFloat(), 12) { l.shapeSides = it.toInt().coerceIn(3, 12) }
        }

        parent.addView(c)
    }

    private fun buildImageSection(l: Layer, parent: LinearLayout) {
        val c = card()
        c.addView(header("Gambar"))
        fun dimRow(label: String, value: Float, maxVal: Int, onChange: (Float) -> Unit) {
            val row = LinearLayout(context)
            row.orientation = HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(6f).toInt()
            row.addView(labelTv(label, widthDp = 60f))
            val seek = FluxaSlider(context)
            val rangeMax = SliderRange.dimMax(value, maxVal)
            seek.max = rangeMax
            seek.progress = SliderRange.progressOf(value, rangeMax)
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
        parent.addView(c)
    }


    // ---------------- slider statis (bukan keyframe) ----------------
    private fun defaultEffectColor(type: String): Int = com.fluxa.motion.core.preview.PreviewSamples.defaultEffectColor(type)

    /**
     * Semua jenis blur sebagai deretan thumbnail. Setiap thumbnail dirender dari gambar contoh
     * dengan BlurOps yang sama dengan renderer, jadi yang terlihat = hasil di layer.
     */
    private fun blurStrip(l: Layer): android.view.View {
        val scroll = android.widget.HorizontalScrollView(context)
        scroll.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        val options: List<Pair<String?, String>> =
            listOf<Pair<String?, String>>(null to "Tanpa blur") + EffectMath.BLUR_TYPES.map { it.first to it.second }
        for ((id, label) in options) {
            val selected = if (id == null) l.blurType == "none" else l.blurType == id
            val tile = LinearLayout(context)
            tile.orientation = VERTICAL
            tile.gravity = Gravity.CENTER_HORIZONTAL
            tile.isClickable = true
            val img = android.widget.ImageView(context)
            img.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            img.setImageBitmap(EffectPreview.blur(id))
            img.background = roundRectBg(0xFF11141C.toInt(), dp(10f), if (selected) cAccent else null)
            img.clipToOutline = true
            tile.addView(img, LayoutParams(dp(78f).toInt(), dp(52f).toInt()))
            val tv = labelTv(label, 9f, if (selected) cAccent else cMuted)
            tv.gravity = Gravity.CENTER
            val lpTv = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lpTv.topMargin = dp(4f).toInt()
            tile.addView(tv, lpTv)
            tile.setOnClickListener {
                l.blurType = id ?: "none"
                listener?.onEdited(true)
                rebind()
            }
            val lpTile = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lpTile.rightMargin = dp(7f).toInt()
            row.addView(tile, lpTile)
        }
        scroll.addView(row)
        return scroll
    }

    /** Stack efek: deretan chip untuk menambah, lalu kartu per efek (urutan = urutan tampil). */
    private fun buildEffectStack(l: Layer, parent: LinearLayout) {
        parent.addView(header("Stack efek"))
        val addScroll = android.widget.HorizontalScrollView(context)
        addScroll.isHorizontalScrollBarEnabled = false
        val addRow = LinearLayout(context)
        addRow.orientation = HORIZONTAL
        for (type in EFFECT_TYPES) {
            val tile = LinearLayout(context)
            tile.orientation = VERTICAL
            tile.gravity = Gravity.CENTER_HORIZONTAL
            tile.isClickable = true
            val box = android.widget.FrameLayout(context)
            box.background = roundRectBg(0xFF11141C.toInt(), dp(12f))
            box.addView(
                android.widget.ImageView(context).also {
                    it.setImageBitmap(EffectPreview.effect(type))
                    it.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                },
                android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            tile.addView(box, LayoutParams(dp(68f).toInt(), dp(46f).toInt()))
            tile.contentDescription = "Tambah " + (EFFECT_LABELS[type] ?: type)
            tile.setOnClickListener {
                l.effects.add(LayerEffect(type = type, amount = 0.5f, size = 0.5f, color = defaultEffectColor(type)))
                listener?.onEdited(true)
                rebind()
            }
            val lpTile = LayoutParams(dp(68f).toInt(), LayoutParams.WRAP_CONTENT)
            lpTile.rightMargin = dp(7f).toInt()
            addRow.addView(tile, lpTile)
        }
        addScroll.addView(addRow)
        parent.addView(addScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        if (l.effects.isEmpty()) {
            val hint = labelTv("Belum ada efek. Ketuk salah satu di atas untuk menambah.", 11f, cDim)
            val lpHint = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lpHint.topMargin = dp(8f).toInt()
            parent.addView(hint, lpHint)
            return
        }

        for ((idx, e) in l.effects.withIndex()) {
            val box = LinearLayout(context)
            box.orientation = VERTICAL
            box.background = roundRectBg(0xFF11141C.toInt(), dp(12f))
            box.setPadding(dp(10f).toInt(), dp(8f).toInt(), dp(10f).toInt(), dp(10f).toInt())

            val head = LinearLayout(context)
            head.orientation = HORIZONTAL
            head.gravity = Gravity.CENTER_VERTICAL
            head.addView(View(context), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            head.addView(
                android.widget.ImageView(context).also {
                    it.setImageBitmap(EffectPreview.effect(e.type))
                    it.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    it.contentDescription = EFFECT_LABELS[e.type] ?: e.type
                },
                LayoutParams(dp(64f).toInt(), dp(40f).toInt())
            )
            val sw = FluxaSwitch(context)
            sw.checked = e.enabled
            sw.onToggle = { v ->
                e.enabled = v
                listener?.onEdited(true)
            }
            head.addView(sw, LayoutParams(dp(48f).toInt(), dp(28f).toInt()))
            val del = miniIconBtn(FluxaIcon.TRASH, 0xFFFF5C7A.toInt()) {
                l.effects.remove(e)
                listener?.onEdited(true)
                rebind()
            }
            head.addView(del, LayoutParams(dp(34f).toInt(), dp(34f).toInt()))
            box.addView(head, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

            staticSlider(box, "Kekuatan", e.amount * 100f, 100, 1f) { v ->
                e.amount = (v / 100f).coerceIn(0f, 1f)
            }
            staticSlider(box, "Ukuran", e.size * 100f, 100, 1f) { v ->
                e.size = (v / 100f).coerceIn(0f, 1f)
            }
            if (e.type in EFFECT_USES_COLOR) {
                val swRow = swatchRow(e.color) { c ->
                    e.color = c
                    listener?.onEdited(true)
                }
                val lpSw = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                lpSw.topMargin = dp(6f).toInt()
                box.addView(swRow, lpSw)
            }

            val lpBox = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lpBox.topMargin = dp(8f).toInt()
            parent.addView(box, lpBox)
        }
    }

    private fun staticSlider(parent: LinearLayout, label: String, value: Float, maxInt: Int, div: Float, minV: Float = 0f, onChange: (Float) -> Unit) {
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(6f).toInt()
        row.addView(labelTv(label, widthDp = 84f))
        val seek = FluxaSlider(context)
        seek.max = maxInt
        seek.progress = SliderRange.progressOf((value - minV) * div, maxInt)
        val valTv = labelTv(Format.dec(value, if (div > 10f) 2 else if (div > 1f) 1 else 0), 11f, cText)
        seek.onProgress = { progress ->
            val v = minV + progress / div
            onChange(v)
            valTv.text = Format.dec(v, if (div > 10f) 2 else if (div > 1f) 1 else 0)
            listener?.onEdited(false)
        }
        seek.onReleased = { listener?.onEdited(true) }
        row.addView(seek, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        valTv.isClickable = true
        valTv.setOnClickListener {
            promptNumber(label, value) { v ->
                onChange(v)
                valTv.text = Format.dec(v, if (div > 10f) 2 else if (div > 1f) 1 else 0)
                listener?.onEdited(true)
            }
        }
        row.addView(valTv, LayoutParams(dp(40f).toInt(), LayoutParams.WRAP_CONTENT))
        parent.addView(row, lp)
    }

    /** Dialog input angka. Tidak ada batas nilai; hanya angka yang diterima. */
    private fun promptNumber(title: String, current: Float, onSet: (Float) -> Unit) {
        val input = EditText(context).apply {
            setText(Format.dec(current, 3))
            setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setTextColor(cText)
            setPadding(dp(16f).toInt(), dp(8f).toInt(), dp(16f).toInt(), dp(8f).toInt())
        }
        val dlg = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                input.text.toString().replace(',', '.').toFloatOrNull()?.let(onSet)
            }
            .setNegativeButton("Batal", null)
            .create()
        dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(cCard))
        dlg.show()
    }

    private fun swatchBlock(parent: LinearLayout, label: String, selected: Int, onPick: (Int) -> Unit) {
        val lbl = labelTv(label, widthDp = 84f)
        val lpL = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lpL.topMargin = dp(10f).toInt()
        parent.addView(lbl, lpL)
        val sw = swatchRow(selected, onPick)
        val lpS = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lpS.topMargin = dp(6f).toInt()
        parent.addView(sw, lpS)
    }

    private fun filterThumb(vals: FloatArray): Bitmap {
        val key = vals.joinToString(",")
        filterThumbs[key]?.let { return it }
        val sample = filterSample ?: run {
            val b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)
            val pt = Paint(Paint.ANTI_ALIAS_FLAG)
            pt.color = 0xFF4FB2FF.toInt(); cv.drawRect(0f, 0f, 120f, 120f, pt)
            pt.color = 0xFFFFD166.toInt(); cv.drawCircle(88f, 30f, 15f, pt)
            val mtn = Path()
            mtn.moveTo(0f, 120f); mtn.lineTo(34f, 58f); mtn.lineTo(68f, 120f); mtn.close()
            pt.color = 0xFF6C4BD8.toInt(); cv.drawPath(mtn, pt)
            val mtn2 = Path()
            mtn2.moveTo(44f, 120f); mtn2.lineTo(86f, 52f); mtn2.lineTo(120f, 120f); mtn2.close()
            pt.color = 0xFF22C55E.toInt(); cv.drawPath(mtn2, pt)
            pt.color = 0xFFFF8A3D.toInt(); cv.drawRect(0f, 102f, 120f, 120f, pt)
            filterSample = b
            b
        }
        val out = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        val op = Paint(Paint.ANTI_ALIAS_FLAG)
        op.colorFilter = Renderer.filterFor(vals[0], vals[1], vals[2], vals[3])
        oc.drawBitmap(sample, 0f, 0f, op)
        filterThumbs[key] = out
        return out
    }

    // ---------------- Warna & Filter ----------------
    private fun buildColorCard(l: Layer, parent: LinearLayout) {
        val c = card()
        c.addView(header("Warna & Filter"))
        val filters = listOf(
            Triple("Normal", floatArrayOf(100f, 100f, 100f, 0f), "reset"),
            Triple("Vivid", floatArrayOf(106f, 112f, 145f, 0f), ""),
            Triple("Hangat", floatArrayOf(104f, 104f, 118f, 12f), ""),
            Triple("Dingin", floatArrayOf(101f, 104f, 112f, -14f), ""),
            Triple("Mono", floatArrayOf(100f, 108f, 0f, 0f), ""),
            Triple("Sepia", floatArrayOf(102f, 96f, 62f, 18f), ""),
            Triple("Pudar", floatArrayOf(112f, 84f, 78f, 0f), "")
        )
        val rowF = LinearLayout(context)
        rowF.orientation = HORIZONTAL
        val scF = android.widget.HorizontalScrollView(context)
        scF.isHorizontalScrollBarEnabled = false
        for ((fname, vals) in filters.map { it.first to it.second }) {
            val tile = LinearLayout(context)
            tile.orientation = VERTICAL
            tile.gravity = Gravity.CENTER_HORIZONTAL
            tile.isClickable = true
            val iv = ImageView(context)
            iv.setImageBitmap(filterThumb(vals))
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            tile.addView(iv, LayoutParams(dp(54f).toInt(), dp(54f).toInt()))
            val lbl = labelTv(fname, 9.5f, cMuted)
            lbl.gravity = Gravity.CENTER
            val lpLbl = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lpLbl.topMargin = dp(4f).toInt()
            tile.addView(lbl, lpLbl)
            val lp = LayoutParams(dp(62f).toInt(), LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(8f).toInt()
            rowF.addView(tile, lp)
            tile.setOnClickListener {
                applyValue(l, "fx.brightness", vals[0])
                applyValue(l, "fx.contrast", vals[1])
                applyValue(l, "fx.saturate", vals[2])
                applyValue(l, "fx.hue", vals[3])
                listener?.onEdited(true)
                syncValues()
            }
        }
        scF.addView(rowF)
        c.addView(scF)
        addProp(c, l, "fx.brightness", "Brightness", 20f, 200f, 1f, true)
        addProp(c, l, "fx.contrast", "Contrast", 20f, 200f, 1f, true)
        addProp(c, l, "fx.saturate", "Saturasi", 0f, 200f, 1f, true)
        addProp(c, l, "fx.hue", "Hue putar", -180f, 180f, 1f, true)
        parent.addView(c)
    }

    // ---------------- Gaya: shadow, garis tepi, masking ----------------
    private fun buildStyleCard(l: Layer, parent: LinearLayout) {
        val c = card()
        c.addView(header("Bayangan (Shadow)"))
        staticSlider(c, "Blur bayangan", l.shadowBlur, 80, 1f) { l.shadowBlur = it }
        staticSlider(c, "Geser X", l.shadowDx, 120, 1f, -60f) { l.shadowDx = it }
        staticSlider(c, "Geser Y", l.shadowDy, 120, 1f, -60f) { l.shadowDy = it }
        swatchBlock(c, "Warna bayangan", l.shadowColor) { col -> l.shadowColor = col }
        parent.addView(c)

        if (l.kind == KIND_SHAPE || l.kind == KIND_SVG) {
            val c2 = card()
            c2.addView(header("Garis Tepi (Border)"))
            staticSlider(c2, "Lebar garis", l.shapeStrokeW, 60, 1f) { l.shapeStrokeW = it }
            swatchBlock(c2, "Warna garis", l.shapeStrokeColor) { col -> l.shapeStrokeColor = col }
            parent.addView(c2)
        }

        val c3 = card()
        c3.addView(header("Masking"))
        val maskKinds = listOf("none", "rrect", "circle", "star")
        val maskLabels = listOf("Tidak ada", "Kotak lembut", "Lingkaran", "Bintang")
        val maskRow = LinearLayout(context)
        maskRow.orientation = HORIZONTAL
        maskRow.gravity = Gravity.CENTER_VERTICAL
        maskRow.addView(labelTv("Bentuk mask", widthDp = 84f))
        val maskDrop = visualPicker(maskKinds, l.maskShape, { maskLabels[maskKinds.indexOf(it).coerceAtLeast(0)] }, { c, r, k -> PickerArt.mask(c, r, k) }) { k ->
            l.maskShape = k
            listener?.onEdited(true)
        }
        maskRow.addView(maskDrop, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        c3.addView(maskRow)
        val invRow = LinearLayout(context)
        invRow.orientation = HORIZONTAL
        invRow.gravity = Gravity.CENTER_VERTICAL
        val lpInv = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpInv.topMargin = dp(10f).toInt()
        invRow.addView(labelTv("Balik mask", widthDp = 84f))
        val invSwitch = FluxaSwitch(context)
        invSwitch.checked = l.maskInvert
        invSwitch.onToggle = { on ->
            l.maskInvert = on
            listener?.onEdited(true)
        }
        invRow.addView(invSwitch)
        c3.addView(invRow, lpInv)
        val hintMask = labelTv("Mask memotong layer mengikuti bentuk. Untuk sudut membulat di gambar, pakai Kotak lembut.", 10f, cDim)
        val lpHint = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lpHint.topMargin = dp(8f).toInt()
        c3.addView(hintMask, lpHint)

        // mask grup & clip ke bawah
        val clipRow = LinearLayout(context)
        clipRow.orientation = HORIZONTAL
        clipRow.gravity = Gravity.CENTER_VERTICAL
        val lpClip = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpClip.topMargin = dp(12f).toInt()
        clipRow.addView(labelTv("Mask ke bawah", widthDp = 84f))
        val clipSwitch = FluxaSwitch(context)
        clipSwitch.checked = l.clipBelow
        clipSwitch.onToggle = { on ->
            l.clipBelow = on
            listener?.onEdited(true)
            rebind()
        }
        clipRow.addView(clipSwitch)
        c3.addView(clipRow, lpClip)

        val members = proj?.layers?.count { it.groupId.isNotEmpty() && it.groupId == l.groupId } ?: 0
        val grpRow = LinearLayout(context)
        grpRow.orientation = HORIZONTAL
        grpRow.gravity = Gravity.CENTER_VERTICAL
        val lpGrp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpGrp.topMargin = dp(10f).toInt()
        grpRow.addView(labelTv("Grup", widthDp = 84f))
        val grpInfo = labelTv(if (l.groupId.isEmpty()) "Belum dalam grup" else "Grup: $members layer", 11f, cDim)
        grpRow.addView(grpInfo, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        grpRow.addView(miniButton("Gabung ke bawah") { joinBelow(l); listener?.onEdited(true); rebind() })
        if (l.groupId.isNotEmpty()) {
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.marginStart = dp(6f).toInt()
            grpRow.addView(miniButton("Lepas") { l.groupId = ""; l.groupMask = false; listener?.onEdited(true); rebind() }, lp)
        }
        c3.addView(grpRow, lpGrp)

        val gmRow = LinearLayout(context)
        gmRow.orientation = HORIZONTAL
        gmRow.gravity = Gravity.CENTER_VERTICAL
        val lpGm = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpGm.topMargin = dp(10f).toInt()
        gmRow.addView(labelTv("Mask grup", widthDp = 84f))
        val gmSwitch = FluxaSwitch(context)
        gmSwitch.checked = l.groupMask
        gmSwitch.isEnabled = l.groupId.isNotEmpty()
        gmSwitch.alpha = if (l.groupId.isNotEmpty()) 1f else 0.4f
        gmSwitch.onToggle = { on ->
            setGroupMask(l, on)
            listener?.onEdited(true)
            rebind()
        }
        gmRow.addView(gmSwitch)
        c3.addView(gmRow, lpGm)

        val hintGroup = labelTv("Mask ke bawah memotong layer tepat di bawahnya. Gabung ke bawah mengelompokkan layer; Mask grup memotong seluruh grup.", 10f, cDim)
        val lpHintG = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lpHintG.topMargin = dp(8f).toInt()
        c3.addView(hintGroup, lpHintG)
        parent.addView(c3)
    }

    // ---------------- 3D tilt ----------------
    private fun build3DCard(l: Layer, parent: LinearLayout) {
        val c = card()
        c.addView(header("Kemiringan 3D"))
        addProp(c, l, "rotX", "Tilt X", -80f, 80f, 1f, true)
        addProp(c, l, "rotY", "Tilt Y", -80f, 80f, 1f, true)
        val hint = labelTv(
            if (l.kind == KIND_3D) "Objek 3D diputar penuh oleh Tilt X/Y + Rotasi (tab Transform). Keyframe-kan Tilt Y dari -180 ke 180 untuk putaran penuh."
            else "Semua layer bisa dimiringkan 3D. Keyframe-kan Tilt Y untuk efek kartu berbalik.",
            10f, cDim
        )
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8f).toInt()
        c.addView(hint, lp)
        parent.addView(c)
    }

    // ---------------- kurva easing ----------------
    private fun autoKeyLabel(key: String): String = when (key) {
        "t:ZOOM_IN" -> "Zoom masuk"
        "t:ZOOM_OUT" -> "Zoom keluar"
        "t:SHAKE" -> "Getar"
        "t:FLOAT" -> "Melayang"
        "t:SPIN" -> "Putar"
        "t:FADE" -> "Kedip berulang"
        "b:ZOOM" -> "Zoom di ketukan"
        "b:SWING" -> "Goyang di ketukan"
        "b:DIP" -> "Kedip di ketukan"
        "b:BOUNCE" -> "Lompat di ketukan"
        "b:SHAKE" -> "Getar di ketukan"
        else -> key
    }

    /** Keyframe otomatis: pola waktu dan pulsa ketukan, dengan intensitas dan rapat. */
    private fun buildAutoKeyCard(l: Layer, parent: LinearLayout) {
        val c = card()
        c.addView(header("Keyframe otomatis"))
        val keys = AutoPattern.Kind.values().map { "t:${it.name}" } + BeatKeys.Style.values().map { "b:${it.name}" }
        val picker = visualPicker(keys, autoKey, { autoKeyLabel(it) }, { cv, r, k -> PickerArt.autoKey(cv, r, k) }) { k ->
            autoKey = k
        }
        val lpPick = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpPick.topMargin = dp(6f).toInt()
        c.addView(picker, lpPick)

        fun sliderRow(label: String, max: Int, value: Int, onChange: (Int) -> Unit) {
            val row = LinearLayout(context)
            row.orientation = HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8f).toInt()
            row.addView(labelTv(label, widthDp = 84f))
            val seek = FluxaSlider(context)
            seek.max = max
            seek.progress = value.coerceIn(0, max)
            seek.onProgress = { p -> onChange(p) }
            row.addView(seek, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            c.addView(row, lp)
        }
        sliderRow("Intensitas", 200, (autoIntensity * 100).toInt()) { autoIntensity = it / 100f }
        sliderRow("Rapat", 3, autoRate) { autoRate = it }

        val actRow = LinearLayout(context)
        actRow.orientation = HORIZONTAL
        actRow.gravity = Gravity.CENTER_VERTICAL
        actRow.addView(View(context), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val go = miniIconBtn(FluxaIcon.SPARK, 0xFF0A0B0F.toInt()) {
            listener?.onAutoKeys(l, autoKey, autoIntensity, autoRate)
        }
        go.contentDescription = "Pasang keyframe otomatis"
        actRow.addView(go)
        val lpAct = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpAct.topMargin = dp(10f).toInt()
        c.addView(actRow, lpAct)
        parent.addView(c)
    }

    private fun buildCurveCard(l: Layer, parent: LinearLayout) {
        val c = card()
        c.addView(header("Kurva Gerakan (Grafik)"))
        val curve = CurveView(context)
        curve.setCurve(l.bezX1, l.bezY1, l.bezX2, l.bezY2)
        val valTv = labelTv("", 10.5f, cMuted)
        fun refreshLabel() {
            valTv.text = "P1 ${Format.dec(l.bezX1, 2)}, ${Format.dec(l.bezY1, 2)}  |  P2 ${Format.dec(l.bezX2, 2)}, ${Format.dec(l.bezY2, 2)}"
        }
        refreshLabel()
        curve.onChange = { commit ->
            l.bezX1 = curve.x1; l.bezY1 = curve.y1; l.bezX2 = curve.x2; l.bezY2 = curve.y2
            refreshLabel()
            listener?.onEdited(commit)
        }
        c.addView(curve, LayoutParams(LayoutParams.MATCH_PARENT, dp(168f).toInt()))
        c.addView(valTv)
        val presetRow = LinearLayout(context)
        presetRow.orientation = HORIZONTAL
        val lpPR = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpPR.topMargin = dp(8f).toInt()
        val curvePresets = listOf(
            "Pantul JJ" to floatArrayOf(0.2f, 1.6f, 0.4f, 1f),
            "Halus" to floatArrayOf(0.42f, 0f, 0.58f, 1f),
            "Nendang" to floatArrayOf(0.7f, 0f, 0.2f, 1.4f),
            "Linear" to floatArrayOf(0f, 0f, 1f, 1f),
            "Ease In" to floatArrayOf(0.42f, 0f, 1f, 1f),
            "Ease Out" to floatArrayOf(0f, 0f, 0.58f, 1f),
            "Elastis" to floatArrayOf(0.3f, 1.5f, 0.5f, 1f),
            "Tajam" to floatArrayOf(0.9f, 0f, 0.1f, 1f)
        )
        for ((cname, cvals) in curvePresets) {
            val chip = FluxaChip(context, cname)
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(6f).toInt()
            presetRow.addView(chip, lp)
            chip.setOnClickListener {
                l.bezX1 = cvals[0]; l.bezY1 = cvals[1]; l.bezX2 = cvals[2]; l.bezY2 = cvals[3]
                curve.setCurve(l.bezX1, l.bezY1, l.bezX2, l.bezY2)
                refreshLabel()
                listener?.onEdited(true)
            }
        }
        val scPR = android.widget.HorizontalScrollView(context)
        scPR.isHorizontalScrollBarEnabled = false
        scPR.addView(presetRow)
        c.addView(scPR, lpPR)
        // navigasi antar keyframe + hapus keyframe tepat di playhead
        val navRow = LinearLayout(context)
        navRow.orientation = HORIZONTAL
        val lpNav = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpNav.topMargin = dp(8f).toInt()
        val prevKBtn = FluxaButton(context, "Keyframe Sebelumnya", FluxaIcon.BACK, FluxaButtonStyle.GHOST, 11f)
        prevKBtn.setOnClickListener { listener?.onJumpKey(l, -1) }
        navRow.addView(prevKBtn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val nextKBtn = FluxaButton(context, "Keyframe Berikutnya", FluxaIcon.NEXT, FluxaButtonStyle.GHOST, 11f)
        nextKBtn.setOnClickListener { listener?.onJumpKey(l, +1) }
        val lpNextK = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        lpNextK.leftMargin = dp(7f).toInt()
        navRow.addView(nextKBtn, lpNextK)
        c.addView(navRow, lpNav)
        val delKeyBtn = FluxaButton(context, "Hapus Keyframe di Playhead", FluxaIcon.TRASH, FluxaButtonStyle.DANGER_SOFT, 11f)
        delKeyBtn.setOnClickListener {
            Anim.removeKeysAtTime(l, curPlayhead)
            listener?.onEdited(true)
            syncValues()
        }
        val lpDelK = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpDelK.topMargin = dp(7f).toInt()
        c.addView(delKeyBtn, lpDelK)
        val cpRow = LinearLayout(context)
        cpRow.orientation = HORIZONTAL
        val lpCP = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpCP.topMargin = dp(7f).toInt()
        val copyKBtn = FluxaButton(context, "Salin Keyframe", FluxaIcon.COPY, FluxaButtonStyle.GHOST, 11f)
        copyKBtn.setOnClickListener {
            keyClipboard = Anim.copyKeysAt(l, curPlayhead)
            listener?.onEdited(false)
        }
        cpRow.addView(copyKBtn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val pasteKBtn = FluxaButton(context, "Tempel di Sini", FluxaIcon.KEYFRAME, FluxaButtonStyle.GHOST, 11f)
        pasteKBtn.setOnClickListener {
            if (keyClipboard.isNotEmpty()) {
                Anim.pasteKeysAt(l, keyClipboard, curPlayhead)
                listener?.onEdited(true)
                syncValues()
            }
        }
        val lpPaste = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        lpPaste.leftMargin = dp(7f).toInt()
        cpRow.addView(pasteKBtn, lpPaste)
        c.addView(cpRow, lpCP)

        val applyRow = LinearLayout(context)
        applyRow.orientation = HORIZONTAL
        val lpAR = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lpAR.topMargin = dp(8f).toInt()
        val hereBtn = FluxaButton(context, "Keyframe di Sini", FluxaIcon.KEYFRAME, FluxaButtonStyle.GHOST, 11f)
        hereBtn.setOnClickListener {
            var n = 0
            for ((_, ch) in l.channels) for (k in ch.keys) {
                if (kotlin.math.abs(k.t - curPlayhead) < 0.03f) { k.ease = "custom"; n++ }
            }
            if (n == 0) {
                // belum ada keyframe di playhead: jadikan easing default untuk keyframe baru
                defaultEase = "custom"
            }
            listener?.onEdited(true)
        }
        applyRow.addView(hereBtn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val allBtn = FluxaButton(context, "Semua Keyframe", FluxaIcon.CURVE, FluxaButtonStyle.SOFT, 11f)
        allBtn.setOnClickListener {
            for ((_, ch) in l.channels) for (k in ch.keys) k.ease = "custom"
            listener?.onEdited(true)
        }
        val lpAll = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        lpAll.leftMargin = dp(7f).toInt()
        applyRow.addView(allBtn, lpAll)
        c.addView(applyRow, lpAR)
        val hint = labelTv("Tarik titik kuning untuk membentuk kurva. Keyframe berease Kurva Bezier mengikuti bentuk ini secara langsung.", 10f, cDim)
        val lpH = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lpH.topMargin = dp(8f).toInt()
        c.addView(hint, lpH)
        parent.addView(c)
    }

    // ---------------- section vector / svg / 3d ----------------
    private fun buildVectorSection(l: Layer, parent: LinearLayout) {
        val c = card()
        c.addView(header("Coretan Vektor"))
        staticSlider(c, "Tebal garis", l.vecStrokeW, 80, 1f) { l.vecStrokeW = maxOf(1f, it) }
        swatchBlock(c, "Warna garis", l.color) { col -> l.color = col }
        swatchBlock(c, "Warna isi (tertutup)", l.fill) { col -> l.fill = col }
        val closedRow = LinearLayout(context)
        closedRow.orientation = HORIZONTAL
        closedRow.gravity = Gravity.CENTER_VERTICAL
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10f).toInt()
        closedRow.addView(labelTv("Bentuk tertutup", widthDp = 110f))
        val closedSwitch = FluxaSwitch(context)
        closedSwitch.checked = l.vecClosed
        closedSwitch.onToggle = { on ->
            l.vecClosed = on
            l.vecSrc = ""
            listener?.onEdited(true)
        }
        closedRow.addView(closedSwitch)
        c.addView(closedRow, lp)
        parent.addView(c)
    }

    private fun buildSvgSection(l: Layer, parent: LinearLayout) {
        val c = card()
        c.addView(header("Vektor SVG"))
        staticSlider(c, "Lebar", l.w, SliderRange.dimMax(l.w), 1f) { l.w = maxOf(10f, it) }
        staticSlider(c, "Tinggi", l.h, SliderRange.dimMax(l.h), 1f) { l.h = maxOf(10f, it) }
        swatchBlock(c, "Warna isi", l.fill) { col -> l.fill = col }
        parent.addView(c)
    }

    private fun build3DSection(l: Layer, parent: LinearLayout) {
        val c = card()
        c.addView(header("Objek 3D"))
        val primNames = listOf("Kubus", "Piramida", "Prisma segitiga", "Oktahedron", "File .OBJ")
        val primKinds = listOf("cube", "pyramid", "prism", "octa", "custom")
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(labelTv("Bentuk", widthDp = 60f))
        val dd = dropdown(primNames, primKinds.indexOf(l.objKind).coerceAtLeast(0), "Bentuk 3D") { idx ->
            l.objKind = primKinds[idx]
            listener?.onEdited(true)
        }
        row.addView(dd, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        c.addView(row)
        staticSlider(c, "Ukuran", l.w, 2000, 1f) { v -> l.w = maxOf(20f, v); l.h = maxOf(20f, v) }
        swatchBlock(c, "Warna objek", l.fill) { col -> l.fill = col }
        parent.addView(c)
    }

    // ---------------- animated prop row ----------------
    private fun miniButton(label: String, onClick: () -> Unit): TextView {
        val tv = TextView(context)
        tv.text = label
        tv.setTextColor(com.fluxa.motion.ui.theme.FluxaColors.Accent)
        tv.textSize = 11f
        tv.typeface = android.graphics.Typeface.DEFAULT_BOLD
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(10f).toInt(), dp(6f).toInt(), dp(10f).toInt(), dp(6f).toInt())
        tv.background = GradientDrawable().apply { cornerRadius = dp(10f); setColor(com.fluxa.motion.ui.theme.FluxaColors.Field) }
        tv.setOnClickListener { onClick() }
        return tv
    }

    /** Gabung layer ke layer tepat di bawahnya. Bila sudah ada grup di bawah, ikut grup itu. */
    private fun joinBelow(l: Layer) {
        val layers = proj?.layers ?: return
        val idx = layers.indexOf(l)
        if (idx <= 0) return
        val below = layers[idx - 1]
        val gid = below.groupId.ifEmpty { java.util.UUID.randomUUID().toString().substring(0, 8) }
        below.groupId = gid
        l.groupId = gid
    }

    /** Hanya satu layer per grup yang jadi mask grup. */
    private fun setGroupMask(l: Layer, on: Boolean) {
        if (l.groupId.isEmpty()) return
        if (on) proj?.layers?.forEach { if (it !== l && it.groupId == l.groupId) it.groupMask = false }
        l.groupMask = on
    }

    /** Squash & stretch: tiap 0,5 detik skala X melebar dan Y menyempit, lalu kembali ke normal. */
    private fun applySquashStretch(l: Layer, dur: Float) {
        Anim.setKey(l, "scaleX", 0f, 1f, "linear")
        Anim.setKey(l, "scaleY", 0f, 1f, "linear")
        var t = 0.5f
        while (t < dur) {
            Anim.setKey(l, "scaleX", t, 1.2f, "easeOut")
            Anim.setKey(l, "scaleY", t, 0.82f, "easeOut")
            val back = minOf(dur, t + 0.18f)
            Anim.setKey(l, "scaleX", back, 1f, "easeOut")
            Anim.setKey(l, "scaleY", back, 1f, "easeOut")
            t += 0.5f
        }
    }

    private fun addProp(parent: LinearLayout, l: Layer, key: String, label: String, minV: Float, maxV: Float, step: Float, isInt: Boolean) {
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(4f).toInt()
        parent.addView(row, lp)

        row.addView(labelTv(label, widthDp = 74f))
        val seek = FluxaSlider(context)
        seek.max = (((maxV - minV) / step).roundToInt()).coerceAtLeast(1)
        val valTv = labelTv("", 11f, cText, 40f)
        val keyBtn = KeyframeButton(context)

        // penggaris geser (tanpa batas): menggantikan slider & tombol −/+
        seek.visibility = View.GONE
        row.addView(seek, LayoutParams(0, 0))
        val ruler = RulerSlider(context)
        ruler.step = step * 5f
        ruler.value = Anim.value(l, key, curPlayhead)
        row.addView(ruler, LayoutParams(0, dp(34f).toInt(), 1f))
        valTv.gravity = Gravity.CENTER
        row.addView(valTv, LayoutParams(dp(58f).toInt(), LayoutParams.WRAP_CONTENT))
        row.addView(keyBtn, LayoutParams(dp(30f).toInt(), dp(30f).toInt()))
        ruler.onChange = { v, commit ->
            if (!binding) {
                applyValue(l, key, v)
                valTv.text = if (isInt) v.roundToInt().toString() else Format.dec(v, 2)
                listener?.onEdited(commit)
                if (commit) syncValues()
            }
        }
        pendingRuler = ruler

        val ref = RowRef(key, seek, valTv, keyBtn, minV, step, isInt, l)
        rows.add(ref)
        pendingRuler?.let { rulerOf[ref] = it }
        pendingRuler = null

        seek.onProgress = { progress ->
            if (!binding) {
                val v = minV + progress * step
                applyValue(l, key, v)
                valTv.text = if (isInt) v.roundToInt().toString() else Format.dec(v, 2)
                listener?.onEdited(false)
            }
        }
        seek.onReleased = { listener?.onEdited(true) }
        valTv.isClickable = true
        valTv.setOnClickListener {
            promptNumber(label, Anim.value(l, key, curPlayhead)) { v ->
                applyValue(l, key, v)
                valTv.text = if (isInt) v.roundToInt().toString() else Format.dec(v, 2)
                listener?.onEdited(true)
                syncValues()
            }
        }
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
