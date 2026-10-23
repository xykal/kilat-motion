package com.fluxa.motion.ui.editor

import com.fluxa.motion.core.model.*
import kotlin.math.roundToInt
import com.fluxa.motion.core.animation.Anim
import com.fluxa.motion.core.audio.AudioUtil
import com.fluxa.motion.core.audio.MusicPlayer
import com.fluxa.motion.core.export.Exporter
import com.fluxa.motion.data.ProjectLibrary
import com.fluxa.motion.data.ProjectStore
import com.fluxa.motion.data.Templates
import com.fluxa.motion.core.audio.VideoAudio
import com.fluxa.motion.core.video.VideoFrames
import com.fluxa.motion.ui.picker.MediaPickerActivity
import com.fluxa.motion.ui.export.ExportPreviewActivity
import com.fluxa.motion.ui.export.ExportSettingsSheet

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.fluxa.motion.ui.components.IconView
import com.fluxa.motion.ui.components.FluxaDropdown
import com.fluxa.motion.ui.components.FluxaIcon
import com.fluxa.motion.ui.components.FluxaIconButton
import com.fluxa.motion.ui.components.FluxaButton
import com.fluxa.motion.ui.components.FluxaButtonStyle
import com.fluxa.motion.ui.components.FluxaSheet
import com.fluxa.motion.ui.components.FluxaColorPicker
import com.fluxa.motion.ui.components.FluxaSlider
import com.fluxa.motion.ui.components.FluxaToast
import com.fluxa.motion.ui.components.kEditText
import com.fluxa.motion.ui.theme.FluxaColors
import com.fluxa.motion.ui.theme.dp as kdp
import com.fluxa.motion.ui.theme.dpf as kdpf
import com.fluxa.motion.ui.theme.kText
import com.fluxa.motion.ui.theme.fluxaSolid
import com.fluxa.motion.ui.theme.ovalBg
import com.fluxa.motion.ui.theme.pressEffect
import com.fluxa.motion.ui.theme.roundedRect
import com.fluxa.motion.ui.theme.roundedTop
import kotlin.math.max
import kotlin.math.min
import com.fluxa.motion.ui.settings.AiActions
import com.fluxa.motion.ui.settings.CloudSettings
import com.fluxa.motion.core.animation.AutoPattern
import com.fluxa.motion.core.animation.BeatKeys
import java.io.File

/**
 * Editor Fluxa.
 * Seluruh UI dibangun dari komponen custom (ui/components) dan ikon Canvas
 * (Icons.kt) - tanpa dialog, spinner, seekbar, toast, maupun ikon bawaan Android.
 */
class EditorActivity : Activity() {

    private lateinit var project: Project
    private var playhead = 0f
    private var selectedId: String? = null
    private var playing = false
    private var loop = false
    private var playStart = 0f
    private var playT0 = 0L
    private var autosaveToken = 0

    private lateinit var preview: PreviewView
    private lateinit var timeline: TimelineView
    private lateinit var inspector: InspectorView
    private lateinit var inspectorPanel: LinearLayout
    private lateinit var nameEdit: EditText
    private lateinit var timecode: TextView
    private lateinit var playIcon: IconView
    private lateinit var loopBtn: FluxaIconButton
    private lateinit var undoBtn: FluxaIconButton
    private lateinit var redoBtn: FluxaIconButton
    private lateinit var magnetBtn: FluxaIconButton
    private lateinit var selLabel: TextView
    private lateinit var zoomPct: TextView
    private lateinit var eyeBtn: FluxaIconButton
    private lateinit var lockBtn: FluxaIconButton

    // riwayat undo/redo berbasis snapshot JSON project
    private val undoStack = ArrayList<String>()
    private val redoStack = ArrayList<String>()
    private var lastSnapshot = ""
    private lateinit var badge: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val musicPlayer by lazy { MusicPlayer(applicationContext) }
    private fun dp(v: Float): Int = kdp(v)

    // =====================================================================
    // lifecycle
    // =====================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = FluxaColors.BgTop
        window.navigationBarColor = FluxaColors.Bg

        project = resolveProject()
        buildUi()
        setPlayhead(0f)
        updateTimecode()
        showInspectorFor(null)
        ProjectLibrary.save(applicationContext, project, touch = false)
        lastSnapshot = ProjectStore.encode(project)
        updateUndoButtons()
    }

    /** Salin audio bawaan template dari assets ke penyimpanan app, lalu pasang sebagai musik project. */
    private fun attachTemplateMusic(templateId: String, project: Project) {
        val asset = Templates.musicAssetFor(templateId) ?: return
        try {
            val out = java.io.File(filesDir, "template_music/${asset.substringAfterLast('/')}")
            if (!out.exists()) {
                out.parentFile?.mkdirs()
                assets.open(asset).use { input -> out.outputStream().use { input.copyTo(it) } }
            }
            project.music = Music().apply {
                path = out.absolutePath
                name = "Sound template"
                start = 0f
                dur = project.duration
                offset = 0f
            }
        } catch (e: java.io.FileNotFoundException) {
            // asset belum ada: template tetap jalan tanpa musik
        }
    }

    /** Project ditentukan dari intent homescreen: buka / template / baru / terakhir. */
    private fun resolveProject(): Project {
        val templateId = intent.getStringExtra(EXTRA_TEMPLATE_ID)
        if (templateId != null) {
            Templates.createFromTemplate(templateId)?.let { tp ->
                attachTemplateMusic(templateId, tp)
                return tp
            }
        }
        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        if (projectId != null) {
            ProjectLibrary.load(applicationContext, projectId)?.let { return it }
        }
        if (intent.hasExtra(EXTRA_NEW_W)) {
            val w = intent.getIntExtra(EXTRA_NEW_W, 1080)
            val h = intent.getIntExtra(EXTRA_NEW_H, 1920)
            return Templates.newProject(w, h)
        }
        if (intent.getBooleanExtra(EXTRA_DEMO, false)) return Templates.buildDemo()
        return ProjectLibrary.latest(applicationContext) ?: Templates.buildDemo()
    }

    override fun onPause() {
        playing = false
        musicPlayer.sync(project.music, playhead, false)
        updatePlayButton()
        ProjectLibrary.save(applicationContext, project, touch = false)
        super.onPause()
    }

    override fun onDestroy() {
        musicPlayer.release()
        // Retriever video tidak boleh tertinggal setelah editor benar-benar ditutup.
        // Saat rotasi (bukan selesai), cache dibiarkan agar tidak membuka ulang video.
        if (isFinishing) VideoFrames.clear()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (inspectorPanel.visibility == View.VISIBLE) {
            hidePanel()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    // =====================================================================
    // UI
    // =====================================================================
    private fun toolItem(icon: FluxaIcon, label: String, accent: Boolean = false, onClick: () -> Unit): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.isClickable = true
        col.setPadding(dp(2f), dp(8f), dp(2f), dp(6f))
        // Gaya CapCut: ikon polos tanpa kotak, label kecil di bawah.
        val iv = IconView(this, icon, if (accent) FluxaColors.Accent else FluxaColors.Text)
        iv.iconInset = 0.1f
        col.addView(iv, LinearLayout.LayoutParams(dp(26f), dp(26f)))
        val tv = kText(label, 10.5f, FluxaColors.Muted, bold = false)
        tv.gravity = Gravity.CENTER
        val lpTv = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpTv.topMargin = dp(5f)
        col.addView(tv, lpTv)
        col.pressEffect(0.92f)
        col.setOnClickListener { onClick() }
        return col
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        root.setBackgroundColor(FluxaColors.Bg)

        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        main.fitsSystemWindows = true

        // ---------- top bar ----------
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(10f), dp(8f), dp(8f), dp(6f))
        top.setBackgroundColor(FluxaColors.BgTop)

        val backBtn = FluxaIconButton(this, FluxaIcon.BACK, FluxaColors.Muted, filled = false, sizeDp = 40f)
        backBtn.setOnClickListener { finish() }
        top.addView(backBtn, LinearLayout.LayoutParams(dp(40f), dp(40f)))

        undoBtn = FluxaIconButton(this, FluxaIcon.UNDO, FluxaColors.Faint, filled = false, sizeDp = 32f)
        undoBtn.setOnClickListener { undo() }
        val lpUndo = LinearLayout.LayoutParams(dp(40f), dp(40f))
        lpUndo.marginStart = dp(6f)
        redoBtn = FluxaIconButton(this, FluxaIcon.REDO, FluxaColors.Faint, filled = false, sizeDp = 32f)
        redoBtn.setOnClickListener { redo() }
        // undo/redo dipasang di transport bar, di samping tombol play

        nameEdit = EditText(this)
        nameEdit.setText(project.name)
        nameEdit.setTextColor(FluxaColors.Text)
        nameEdit.textSize = 14f
        nameEdit.typeface = android.graphics.Typeface.DEFAULT_BOLD
        nameEdit.setSingleLine(true)
        nameEdit.background = null
        nameEdit.setPadding(dp(10f), dp(6f), dp(6f), dp(6f))
        nameEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val s = nameEdit.text.toString().trim()
                if (s.isNotEmpty()) {
                    project.name = s
                    autosave()
                }
            }
        }
        val lpName = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpName.marginStart = dp(2f)
        top.addView(nameEdit, lpName)

        // Tombol ekspor utama: pill teal solid di kanan atas (gaya CapCut).
        val exportBtn = TextView(this).apply {
            text = "Ekspor"
            setTextColor(FluxaColors.AccentInk)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14f), 0, dp(14f), 0)
            background = fluxaSolid(kdpf(17f))
            setOnClickListener { showExportDialog() }
        }
        top.addView(exportBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40f)).apply { marginEnd = dp(6f) })

        val menuBtn = FluxaIconButton(this, FluxaIcon.MORE, FluxaColors.Muted, filled = false, sizeDp = 40f)
        menuBtn.setOnClickListener { showMenu() }
        top.addView(menuBtn, LinearLayout.LayoutParams(dp(40f), dp(40f)))

        main.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---------- preview ----------
        val previewWrap = FrameLayout(this)
        preview = PreviewView(this)
        preview.project = project
        preview.listener = object : PreviewView.Listener {
            override fun onSelect(id: String?) {
                selectLayer(id, openPanel = false)
            }

            override fun onEdited(commit: Boolean) {
                preview.invalidate()
                timeline.invalidate()
                if (commit) {
                    autosave()
                    // saat play jangan bangun ulang inspector, supaya keyframe bisa ditambah sambil memutar
                    if (inspectorPanel.visibility == View.VISIBLE && !playing) showInspectorFor(project.findLayer(selectedId))
                }
            }

            override fun onRequestProperties() {
                showPanel()
            }

            override fun onStroke(points: FloatArray) {
                addVectorLayer(points)
            }
        }
        previewWrap.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // chip zoom preview: muncul saat kanvas di-zoom, ketuk untuk kembali 100%
        val zoomChip = kText("", 9.5f, FluxaColors.Muted, bold = true, font = "monospace")
        zoomChip.setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
        zoomChip.background = roundedRect(0xFF171B27.toInt(), kdpf(6f))
        zoomChip.visibility = View.GONE
        zoomChip.setOnClickListener { preview.resetCanvasZoom() }
        val lpZoomChip = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpZoomChip.gravity = Gravity.TOP or Gravity.END
        lpZoomChip.rightMargin = dp(10f)
        lpZoomChip.topMargin = dp(8f)
        previewWrap.addView(zoomChip, lpZoomChip)
        preview.onZoomChanged = { z ->
            zoomChip.text = "${(z * 100).toInt()}%  ·  Reset"
            zoomChip.visibility = if (z > 1.001f) View.VISIBLE else View.GONE
        }

        badge = kText("", 9.5f, FluxaColors.Dim, bold = true)
        badge.setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
        badge.background = roundedRect(0xFF171B27.toInt(), kdpf(6f))
        val lpBadge = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpBadge.gravity = Gravity.TOP or Gravity.START
        lpBadge.leftMargin = dp(10f)
        lpBadge.topMargin = dp(8f)
        previewWrap.addView(badge, lpBadge)
        updateBadge()

        main.addView(previewWrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---------- transport ----------
        // Tata letak 3 kolom simetris: kiri = timecode, tengah = PLAY (tepat di tengah),
        // kanan = aksi. Kiri & kanan berbobot sama supaya tombol play selalu di tengah layar.
        val transport = LinearLayout(this)
        transport.orientation = LinearLayout.HORIZONTAL
        transport.gravity = Gravity.CENTER_VERTICAL
        transport.setPadding(dp(14f), dp(4f), dp(14f), dp(6f))

        timecode = kText("00:00.00 / 00:00.00", 10f, FluxaColors.Text, bold = true, font = "monospace")
        timecode.maxLines = 1
        timecode.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        transport.addView(timecode, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        transport.addView(undoBtn, LinearLayout.LayoutParams(dp(32f), dp(32f)))
        transport.addView(redoBtn, LinearLayout.LayoutParams(dp(32f), dp(32f)))

        val playWrap = FrameLayout(this)
        playWrap.background = ovalBg(0xFFFFFFFF.toInt())
        playWrap.isClickable = true
        playIcon = IconView(this, FluxaIcon.PLAY, FluxaColors.Bg)
        val pp = dp(12f)
        playIcon.setPadding(pp, pp, pp, pp)
        playWrap.addView(playIcon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        playWrap.pressEffect(0.92f)
        playWrap.setOnClickListener { togglePlay() }
        transport.addView(playWrap, LinearLayout.LayoutParams(dp(50f), dp(50f)))

        val right = LinearLayout(this)
        right.orientation = LinearLayout.HORIZONTAL
        right.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        val frameBack = FluxaIconButton(this, FluxaIcon.BACK, FluxaColors.Muted, filled = false, sizeDp = 30f)
        frameBack.setOnClickListener { stepFrame(-1) }
        right.addView(frameBack, LinearLayout.LayoutParams(dp(30f), dp(30f)))
        val frameNext = FluxaIconButton(this, FluxaIcon.NEXT, FluxaColors.Muted, filled = false, sizeDp = 30f)
        frameNext.setOnClickListener { stepFrame(1) }
        right.addView(frameNext, LinearLayout.LayoutParams(dp(30f), dp(30f)))
        val skipBtn = FluxaIconButton(this, FluxaIcon.SKIP_START, FluxaColors.Muted, filled = false, sizeDp = 32f)
        skipBtn.setOnClickListener {
            setPlayhead(0f)
            if (playing) {
                playStart = 0f
                playT0 = System.nanoTime()
            }
        }
        right.addView(skipBtn, LinearLayout.LayoutParams(dp(36f), dp(36f)))
        magnetBtn = FluxaIconButton(this, FluxaIcon.MAGNET, FluxaColors.Accent, filled = false, sizeDp = 36f)
        magnetBtn.setOnClickListener {
            preview.snapEnabled = !preview.snapEnabled
            magnetBtn.setTint(if (preview.snapEnabled) FluxaColors.Accent else FluxaColors.Dim)
            toast(if (preview.snapEnabled) "Magnet snap: nyala" else "Magnet snap: mati", FluxaIcon.MAGNET)
        }
        right.addView(magnetBtn, LinearLayout.LayoutParams(dp(36f), dp(36f)))
        loopBtn = FluxaIconButton(this, FluxaIcon.LOOP, FluxaColors.Dim, filled = false, sizeDp = 36f)
        loopBtn.setOnClickListener {
            loop = !loop
            loopBtn.setTint(if (loop) FluxaColors.Accent else FluxaColors.Dim)
            toast(if (loop) "Loop nyala" else "Loop mati", FluxaIcon.LOOP)
        }
        right.addView(loopBtn, LinearLayout.LayoutParams(dp(36f), dp(36f)))
        transport.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        main.addView(transport, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---------- timeline ----------
        timeline = TimelineView(this)
        timeline.project = project
        timeline.listener = object : TimelineView.Listener {
            override fun onSeek(t: Float) {
                if (playing) stopPlay()
                setPlayhead(t)
            }

            override fun onSelect(id: String?) {
                // memilih/menggeser klip di timeline TIDAK membuka panel (ala CapCut/AM);
                // panel dibuka sadar lewat tombol Properti atau double-tap kanvas.
                selectLayer(id, openPanel = false)
            }

            override fun onTimesChanged(commit: Boolean) {
                preview.invalidate()
                if (commit) {
                    ensureDuration()
                    timeline.invalidate()
                    autosave()
                    if (inspectorPanel.visibility == View.VISIBLE) showInspectorFor(project.findLayer(selectedId))
                }
            }

            override fun onVisibilityToggle(id: String) {
                val l = project.findLayer(id) ?: return
                l.hidden = !l.hidden
                preview.invalidate()
                timeline.invalidate()
                autosave()
            }

            override fun onMusicTap() {
                if (project.music != null) showMusicDialog()
            }

            override fun onMusicEdited(commit: Boolean) {
                timeline.invalidate()
                if (commit) {
                    autosave()
                    musicPlayer.sync(project.music, playhead, playing)
                }
            }
        }
        // ---------- header timeline: aksi cepat layer + kontrol zoom ----------
        val tlHead = LinearLayout(this)
        tlHead.orientation = LinearLayout.HORIZONTAL
        tlHead.gravity = Gravity.CENTER_VERTICAL
        tlHead.setPadding(dp(12f), dp(2f), dp(8f), dp(2f))
        tlHead.setBackgroundColor(FluxaColors.BgTop)
        selLabel = kText("Timeline", 10.5f, FluxaColors.Dim, bold = true)
        selLabel.maxLines = 1
        tlHead.addView(selLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        fun selectedLayerOrToast(): Layer? {
            val l = project.findLayer(selectedId)
            if (l == null) toast("Pilih layer dulu di timeline", FluxaIcon.INFO)
            return l
        }
        fun quickBtn(icon: FluxaIcon, tint: Int = FluxaColors.Muted, act: () -> Unit): FluxaIconButton {
            val b = FluxaIconButton(this, icon, tint, filled = false, sizeDp = 31f)
            b.setOnClickListener { act() }
            tlHead.addView(b, LinearLayout.LayoutParams(dp(31f), dp(31f)))
            return b
        }
        quickBtn(FluxaIcon.SCISSORS) { showCutSheet() }
        quickBtn(FluxaIcon.COPY) { selectedLayerOrToast()?.let { duplicateLayer(it) } }
        eyeBtn = quickBtn(FluxaIcon.EYE) {
            selectedLayerOrToast()?.let { l ->
                l.hidden = !l.hidden
                preview.invalidate()
                timeline.invalidate()
                updateTimelineHeader()
                autosave()
                toast(if (l.hidden) "Layer disembunyikan" else "Layer ditampilkan", if (l.hidden) FluxaIcon.EYE_OFF else FluxaIcon.EYE)
            }
        }
        lockBtn = quickBtn(FluxaIcon.UNLOCK) {
            selectedLayerOrToast()?.let { l ->
                l.locked = !l.locked
                timeline.invalidate()
                updateTimelineHeader()
                autosave()
                toast(if (l.locked) "Layer dikunci (tidak bisa digeser)" else "Kunci layer dibuka", if (l.locked) FluxaIcon.LOCK else FluxaIcon.UNLOCK)
            }
        }
        quickBtn(FluxaIcon.TRASH, FluxaColors.Rose) { selectedLayerOrToast()?.let { deleteLayer(it) } }

        val spacer = View(this)
        tlHead.addView(spacer, LinearLayout.LayoutParams(dp(6f), dp(31f)))
        // zoom per layer: timeline di-zoom ke rentang keyframe layer terpilih
        quickBtn(FluxaIcon.KEYFRAME) {
            selectedLayerOrToast()?.let { l ->
                if (!timeline.zoomToKeys(l)) toast("Layer ini belum punya keyframe", FluxaIcon.INFO)
            }
        }
        quickBtn(FluxaIcon.MINUS) { timeline.zoomBy(0.8f) }
        zoomPct = kText("100%", 10f, FluxaColors.Muted, bold = true, font = "monospace")
        zoomPct.gravity = Gravity.CENTER
        tlHead.addView(zoomPct, LinearLayout.LayoutParams(dp(42f), ViewGroup.LayoutParams.WRAP_CONTENT))
        quickBtn(FluxaIcon.PLUS) { timeline.zoomBy(1.25f) }
        quickBtn(FluxaIcon.RATIO) { timeline.zoomFit() }
        timeline.onZoomChanged = { z -> zoomPct.text = "${(z * 100).toInt()}%" }
        main.addView(tlHead, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        main.addView(timeline, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(176f)))

        // ---------- bottom toolbar ----------
        val bottomScroll = android.widget.HorizontalScrollView(this)
        bottomScroll.isHorizontalScrollBarEnabled = false
        bottomScroll.setBackgroundColor(FluxaColors.BgTop)
        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.setPadding(dp(8f), dp(4f), dp(8f), dp(8f))
        val tools = listOf(
            Triple(FluxaIcon.TEXT, "Teks") { addTextLayer() },
            Triple(FluxaIcon.SHAPE, "Bentuk") { showShapePicker() },
            Triple(FluxaIcon.IMAGE, "Gambar") { pickImage(null) },
            Triple(FluxaIcon.FILM, "Video") { pickVideoLayer() },
            Triple(FluxaIcon.MUSIC, "Musik") { showMusicDialog() },
            Triple(FluxaIcon.PENCIL, "Coret") { toggleDrawMode() },
            Triple(FluxaIcon.VECTOR, "SVG") { pickSvg() },
            Triple(FluxaIcon.CUBE, "3D") { show3DPicker() },
            Triple(FluxaIcon.SLIDERS, "Properti") { showPanel() }
        )
        for ((_, triple) in tools.withIndex()) {
            val (icon, label, action) = triple
            val item = toolItem(icon, label, accent = false) { action() }
            bottom.addView(item, LinearLayout.LayoutParams(dp(64f), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        bottomScroll.addView(bottom)
        main.addView(bottomScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(main, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // ---------- inspector panel ----------
        inspectorPanel = LinearLayout(this)
        inspectorPanel.orientation = LinearLayout.VERTICAL
        inspectorPanel.background = roundedTop(FluxaColors.BgTop, kdpf(20f))
        inspectorPanel.visibility = View.GONE

        val strip = View(this)
        strip.background = roundedRect(0xFF2C3346.toInt(), kdpf(3f))
        val lpStrip = LinearLayout.LayoutParams(dp(42f), dp(4f))
        lpStrip.gravity = Gravity.CENTER_HORIZONTAL
        lpStrip.topMargin = dp(8f)
        inspectorPanel.addView(strip, lpStrip)

        val panelHead = LinearLayout(this)
        panelHead.orientation = LinearLayout.HORIZONTAL
        panelHead.gravity = Gravity.CENTER_VERTICAL
        panelHead.setPadding(dp(16f), dp(6f), dp(10f), dp(2f))
        val panelTitle = kText("Properti Layer", 13f, FluxaColors.Text, bold = true)
        val lpTitle = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        panelHead.addView(panelTitle, lpTitle)
        val closeBtn = FluxaIconButton(this, FluxaIcon.CLOSE, FluxaColors.Dim, filled = false, sizeDp = 34f)
        closeBtn.setOnClickListener { hidePanel() }
        panelHead.addView(closeBtn, LinearLayout.LayoutParams(dp(34f), dp(34f)))
        inspectorPanel.addView(panelHead, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        inspector = InspectorView(this)
        inspector.listener = object : InspectorView.Listener {
            override fun onEdited(commit: Boolean) {
                preview.invalidate()
                timeline.invalidate()
                if (commit) autosave()
            }

            override fun onRequestImage(layer: Layer) {
                pickImage(layer)
            }

            override fun onRequestFill(layer: Layer, mode: String) {
                pickFill(layer, mode)
            }

            override fun onDelete(layer: Layer) {
                deleteLayer(layer)
            }

            override fun onDuplicate(layer: Layer) {
                duplicateLayer(layer)
            }

            override fun onApplyPreset(layer: Layer, preset: String) {
                val ok = Anim.applyPreset(layer, preset, playhead, project.duration)
                if (!ok) {
                    toast("Preset ini tidak cocok untuk layer ${layer.kind}", FluxaIcon.INFO)
                    return
                }
                preview.invalidate()
                timeline.invalidate()
                showInspectorFor(layer)
                autosave()
                toast("Preset ${Anim.PRESET_LABELS[preset]} diterapkan", FluxaIcon.WAND)
            }

            override fun onSplit(layer: Layer) {
                splitLayerAtPlayhead(layer)
            }

            override fun onReorder(layer: Layer, dir: Int) {
                reorderLayer(layer, dir)
            }

            override fun onAutoKeys(layer: Layer, key: String, intensity: Float, rate: Int) {
                applyAutoKeys(layer, key, intensity, rate)
            }

            override fun onJumpKey(layer: Layer, dir: Int) {
                val times = Anim.allKeyTimes(layer)
                val target = if (dir < 0) {
                    times.lastOrNull { it < playhead - 0.03f }
                } else {
                    times.firstOrNull { it > playhead + 0.03f }
                }
                if (target == null) {
                    toast(if (dir < 0) "Tidak ada keyframe sebelumnya" else "Tidak ada keyframe berikutnya", FluxaIcon.KEYFRAME)
                    return
                }
                if (playing) stopPlay()
                setPlayhead(target)
            }

            override fun onClearKeys(layer: Layer) {
                Anim.clearAll(layer)
                preview.invalidate()
                timeline.invalidate()
                showInspectorFor(layer)
                autosave()
                toast("Semua keyframe dihapus", FluxaIcon.RESET)
            }
        }
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(inspector, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        inspectorPanel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val lpPanel = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpPanel.gravity = Gravity.BOTTOM
        root.addView(inspectorPanel, lpPanel)

        setContentView(root)
    }

    private fun updateBadge() {
        badge.text = "${project.width} x ${project.height}  |  ${project.fps}fps"
    }

    // =====================================================================
    // panel
    // =====================================================================
    private fun showPanel() {
        if (inspectorPanel.visibility == View.VISIBLE) return
        val h = (resources.displayMetrics.heightPixels * 0.52f).toInt()
        val lp = inspectorPanel.layoutParams as FrameLayout.LayoutParams
        lp.height = h
        inspectorPanel.layoutParams = lp
        inspectorPanel.visibility = View.VISIBLE
        inspectorPanel.translationY = h.toFloat()
        inspectorPanel.animate().translationY(0f).setDuration(180).start()
        showInspectorFor(project.findLayer(selectedId))
    }

    private fun hidePanel() {
        inspectorPanel.animate().translationY(inspectorPanel.height.toFloat()).setDuration(160).withEndAction {
            inspectorPanel.visibility = View.GONE
        }.start()
    }

    private fun showInspectorFor(layer: Layer?) {
        inspector.bind(project, layer, playhead)
    }

    // =====================================================================
    // playback
    // =====================================================================
    private fun togglePlay() {
        if (playing) stopPlay() else startPlay()
    }

    private fun startPlay() {
        if (project.duration <= 0f) return
        if (playhead >= project.duration - 0.02f) playhead = 0f
        playing = true
        playStart = playhead
        playT0 = System.nanoTime()
        updatePlayButton()
        handler.post(ticker)
    }

    private fun stopPlay() {
        playing = false
        handler.removeCallbacks(ticker)
        musicPlayer.sync(project.music, playhead, false)
        updatePlayButton()
        ProjectLibrary.save(applicationContext, project, touch = false)
    }

    private fun updatePlayButton() {
        playIcon.icon = if (playing) FluxaIcon.PAUSE else FluxaIcon.PLAY
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!playing) return
            val t = playStart + (System.nanoTime() - playT0) / 1_000_000_000f
            if (t >= project.duration) {
                if (loop) {
                    playhead = 0f
                    playStart = 0f
                    playT0 = System.nanoTime()
                    setPlayhead(0f)
                } else {
                    setPlayhead(project.duration)
                    stopPlay()
                    return
                }
            } else {
                setPlayhead(t)
            }
            handler.postDelayed(this, 16)
        }
    }

    private fun setPlayhead(t: Float) {
        playhead = t.coerceIn(0f, project.duration)
        if (playing) musicPlayer.sync(project.music, playhead, true)
        preview.updateTime(playhead)
        timeline.playhead = playhead
        timeline.invalidate()
        updateTimecode()
        if (inspectorPanel.visibility == View.VISIBLE) inspector.updatePlayhead(playhead)
    }

    private fun updateTimecode() {
        timecode.text = "${Format.time(playhead)} / ${Format.time(project.duration)}"
    }

    private fun updateTimelineHeader() {
        if (!::selLabel.isInitialized) return
        val l = project.findLayer(selectedId)
        selLabel.text = if (l == null) "Timeline" else l.name
        selLabel.setTextColor(if (l == null) FluxaColors.Dim else FluxaColors.Text)
        if (::eyeBtn.isInitialized && l != null) {
            eyeBtn.setIcon(if (l.hidden) FluxaIcon.EYE_OFF else FluxaIcon.EYE)
            eyeBtn.setTint(if (l.hidden) FluxaColors.Faint else FluxaColors.Muted)
        }
        if (::lockBtn.isInitialized && l != null) {
            lockBtn.setIcon(if (l.locked) FluxaIcon.LOCK else FluxaIcon.UNLOCK)
            lockBtn.setTint(if (l.locked) FluxaColors.Amber else FluxaColors.Muted)
        }
    }

    // =====================================================================
    // selection & layer ops
    // =====================================================================
    private fun selectLayer(id: String?, openPanel: Boolean) {
        selectedId = id
        updateTimelineHeader()
        preview.select(id)
        timeline.selectedId = id
        timeline.invalidate()
        if (inspectorPanel.visibility == View.VISIBLE || openPanel) {
            showInspectorFor(project.findLayer(id))
        }
        if (openPanel) showPanel()
    }

    /** Durasi project otomatis melebar mengikuti klip terakhir (tidak pernah mengecilkan). */
    private fun ensureDuration() {
        val end = project.layers.maxOfOrNull { it.start + it.dur } ?: 0f
        if (end > project.duration + 0.001f) {
            project.duration = kotlin.math.ceil(end * 10f) / 10f
        }
    }

    private fun afterChange(selectId: String? = null, openPanel: Boolean = true) {
        ensureDuration()
        if (selectId != null) selectedId = selectId
        preview.project = project
        preview.select(selectedId)
        timeline.project = project
        timeline.selectedId = selectedId
        timeline.invalidate()
        preview.invalidate()
        if (inspectorPanel.visibility == View.VISIBLE) showInspectorFor(project.findLayer(selectedId))
        updateTimelineHeader()
        autosave()
    }

    private fun addTextLayer() {
        val p = project
        val l = Layer().apply {
            kind = KIND_TEXT
            name = "Teks ${p.layers.count { it.kind == KIND_TEXT } + 1}"
            text = "TEKS KEREN"
            textSize = min(p.width, p.height) * 0.105f
            x = p.width / 2f
            y = p.height / 2f
            start = playhead.coerceAtLeast(0f)
            dur = 4f
        }
        p.layers.add(l)
        afterChange(l.id)
        selectLayer(l.id, openPanel = true)
        toast("Teks ditambahkan", FluxaIcon.TEXT)
    }

    // =====================================================================
    // layer vektor / svg / 3d
    // =====================================================================
    private fun toggleDrawMode() {
        preview.drawMode = !preview.drawMode
        if (preview.drawMode) {
            if (playing) stopPlay()
            toast("Mode coret ON: gambar bebas di kanvas, ketuk Coret lagi untuk selesai", FluxaIcon.PENCIL)
        } else {
            toast("Mode coret OFF", FluxaIcon.PENCIL)
        }
    }

    private fun addVectorLayer(points: FloatArray) {
        if (points.size < 4) return
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var i = 0
        while (i + 1 < points.size) {
            if (points[i] < minX) minX = points[i]
            if (points[i] > maxX) maxX = points[i]
            if (points[i + 1] < minY) minY = points[i + 1]
            if (points[i + 1] > maxY) maxY = points[i + 1]
            i += 2
        }
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val sb = StringBuilder()
        var j = 0
        while (j + 1 < points.size) {
            if (sb.isNotEmpty()) sb.append(';')
            sb.append(String.format(java.util.Locale.US, "%.1f,%.1f", points[j] - cx, points[j + 1] - cy))
            j += 2
        }
        val p = project
        val l = Layer().apply {
            kind = KIND_VECTOR
            name = "Coretan ${p.layers.count { it.kind == KIND_VECTOR } + 1}"
            vecPoints = sb.toString()
            vecStrokeW = 16f
            color = FluxaColors.Accent
            fill = 0xFF2EE6C0.toInt()
            x = cx
            y = cy
            w = max(10f, maxX - minX)
            h = max(10f, maxY - minY)
            start = playhead.coerceAtLeast(0f)
            dur = 5f
        }
        p.layers.add(l)
        afterChange(l.id)
        toast("Coretan jadi layer vektor - atur di Properti", FluxaIcon.PENCIL)
    }

    private fun pickSvg() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_SVG)
        } catch (e: Throwable) {
            toast("Tidak bisa membuka pemilih file", FluxaIcon.INFO)
        }
    }

    private fun pickObj() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_OBJ)
        } catch (e: Throwable) {
            toast("Tidak bisa membuka pemilih file", FluxaIcon.INFO)
        }
    }

    private fun show3DPicker() {
        val names = listOf("Kubus", "Piramida", "Prisma segitiga", "Oktahedron (kristal)", "Import file .OBJ...")
        val kinds = listOf("cube", "pyramid", "prism", "octa", "custom")
        val items = names.map { n -> FluxaSheet.Item(n, icon = FluxaIcon.CUBE, iconTint = FluxaColors.Sky) }
        FluxaSheet.showList(this, "Tambah objek 3D", items) { which ->
            if (kinds[which] == "custom") pickObj() else add3DLayer(kinds[which], "", names[which])
        }
    }

    private fun add3DLayer(kind: String, path: String, label: String) {
        val p = project
        val base = min(p.width, p.height) * 0.56f
        val l = Layer().apply {
            this.kind = KIND_3D
            name = "3D $label"
            objKind = kind
            objPath = path
            w = base
            h = base
            fill = 0xFF8B7CFF.toInt()
            x = p.width / 2f
            y = p.height / 2f
            start = playhead.coerceAtLeast(0f)
            dur = 5f
            rotX = 18f
        }
        // putaran perkenalan 360 derajat
        Anim.setKey(l, "rotY", l.start, -40f, "easeInOut")
        Anim.setKey(l, "rotY", l.start + l.dur, 320f, "easeInOut")
        p.layers.add(l)
        afterChange(l.id)
        selectLayer(l.id, openPanel = true)
        toast("Objek 3D ditambahkan - putar via Tilt X/Y di tab 3D", FluxaIcon.CUBE)
    }

    private fun importSvgFrom(uri: Uri) {
        toast("Membaca SVG...", FluxaIcon.VECTOR)
        Thread {
            val text = try {
                contentResolver.openInputStream(uri)?.use { ins ->
                    val bytes = ins.readBytes()
                    if (bytes.size > 512_000) null else String(bytes, Charsets.UTF_8)
                }
            } catch (e: Throwable) {
                null
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (text == null) {
                    toast("Gagal membaca file SVG", FluxaIcon.INFO)
                    return@runOnUiThread
                }
                val d = com.fluxa.motion.core.vector.SvgPath.extractPathData(text)
                if (d.isBlank()) {
                    toast("Tidak ada path SVG yang dikenali di file ini", FluxaIcon.INFO)
                    return@runOnUiThread
                }
                val vb = com.fluxa.motion.core.vector.SvgPath.extractViewBox(text)
                val p = project
                val target = min(p.width, p.height) * 0.6f
                val ratio = vb[2] / max(1f, vb[3])
                val l = Layer().apply {
                    kind = KIND_SVG
                    name = (queryDisplayName(uri) ?: "SVG").substringBeforeLast('.')
                    svgData = d
                    svgVbX = vb[0]; svgVbY = vb[1]; svgVbW = vb[2]; svgVbH = vb[3]
                    w = if (ratio >= 1f) target else target * ratio
                    h = if (ratio >= 1f) target / ratio else target
                    fill = FluxaColors.Accent
                    x = p.width / 2f
                    y = p.height / 2f
                    start = playhead.coerceAtLeast(0f)
                    dur = 5f
                }
                p.layers.add(l)
                afterChange(l.id)
                selectLayer(l.id, openPanel = true)
                toast("SVG diimpor sebagai layer vektor", FluxaIcon.VECTOR)
            }
        }.start()
    }

    private fun importObjFrom(uri: Uri) {
        toast("Membaca model 3D...", FluxaIcon.CUBE)
        Thread {
            val dest = try {
                val dir = java.io.File(applicationContext.filesDir, "models")
                if (!dir.exists()) dir.mkdirs()
                val f = java.io.File(dir, "model_${System.currentTimeMillis()}.obj")
                contentResolver.openInputStream(uri)?.use { ins ->
                    f.outputStream().use { out -> ins.copyTo(out) }
                } ?: throw IllegalStateException("no stream")
                f.absolutePath
            } catch (e: Throwable) {
                null
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (dest == null) {
                    toast("Gagal membaca file .OBJ", FluxaIcon.INFO)
                    return@runOnUiThread
                }
                val label = (queryDisplayName(uri) ?: "Model").substringBeforeLast('.')
                add3DLayer("custom", dest, label)
            }
        }.start()
    }

    private fun showShapePicker() {
        val tints = listOf(FluxaColors.Accent, FluxaColors.Sky, FluxaColors.Amber, FluxaColors.Violet)
        val items = SHAPE_TYPES.mapIndexed { i, s ->
            FluxaSheet.Item(SHAPE_LABELS[s] ?: s, icon = FluxaIcon.SHAPE, iconTint = tints[i % tints.size])
        }
        FluxaSheet.showList(this, "Tambah bentuk", items) { which ->
            val p = project
            val shape = SHAPE_TYPES[which]
            val base = min(p.width, p.height) * 0.46f
            val l = Layer().apply {
                kind = KIND_SHAPE
                name = "${SHAPE_LABELS[shape]} ${p.layers.count { it.kind == KIND_SHAPE } + 1}"
                this.shape = shape
                w = base
                h = if (shape == "triangle") base * 0.9f else base
                radius = if (shape == "rect") base * 0.1f else 0f
                fill = FluxaColors.Accent
                x = p.width / 2f
                y = p.height / 2f
                start = playhead.coerceAtLeast(0f)
                dur = 4f
            }
            p.layers.add(l)
            afterChange(l.id)
            selectLayer(l.id, openPanel = true)
        }
    }

    private fun deleteLayer(layer: Layer) {
        val idx = project.layers.indexOf(layer)
        if (idx < 0) return
        project.layers.removeAt(idx)
        val next = project.layers.getOrNull(max(0, idx - 1))
        selectedId = next?.id
        afterChange(selectedId)
        if (inspectorPanel.visibility == View.VISIBLE) showInspectorFor(next)
        toast("Layer dihapus", FluxaIcon.TRASH)
    }

    private fun duplicateLayer(layer: Layer) {
        val copy = layer.duplicate()
        val idx = project.layers.indexOf(layer)
        project.layers.add(idx + 1, copy)
        selectedId = copy.id
        afterChange(selectedId)
        toast("Layer diduplikat", FluxaIcon.COPY)
    }

    // ---------------- potong / trim / durasi ----------------
    private var markIn: Float? = null
    private var markOut: Float? = null

    /** Buang bagian kiri klip (dari awal sampai playhead). */
    private fun trimLeftAt(layer: Layer) {
        val idx = project.layers.indexOf(layer)
        val right = Anim.splitAt(layer, playhead)
        if (right == null) {
            toast("Playhead harus di tengah klip", FluxaIcon.SCISSORS)
            return
        }
        project.layers.remove(layer)   // layer sekarang berisi bagian kiri: buang
        project.layers.add(idx, right)
        selectedId = right.id
        afterChange(selectedId, openPanel = false)
        toast("Bagian kiri dibuang", FluxaIcon.SKIP_START)
    }

    /** Buang bagian kanan klip (dari playhead sampai akhir). */
    private fun trimRightAt(layer: Layer) {
        val tail = Anim.splitAt(layer, playhead)
        if (tail == null) {
            toast("Playhead harus di tengah klip", FluxaIcon.SCISSORS)
            return
        }
        project.layers.remove(tail)
        selectedId = layer.id
        afterChange(selectedId, openPanel = false)
        toast("Bagian kanan dibuang", FluxaIcon.NEXT)
    }

    /** Potong bagian tengah klip antara In dan Out. Sisa kiri dan kanan tetap ada. */
    private fun cutInOutFrom(layer: Layer) {
        val a = markIn
        val b = markOut
        if (a == null || b == null) {
            toast("Tandai In dan Out dulu", FluxaIcon.LAYER_UP)
            return
        }
        val lo = min(a, b)
        val hi = max(a, b)
        if (hi - lo < 0.05f) {
            toast("Area In-Out terlalu kecil", FluxaIcon.LAYER_UP)
            return
        }
        val idx = project.layers.indexOf(layer)
        val mid = Anim.splitAt(layer, lo)
        if (mid == null) {
            toast("In harus berada di dalam klip", FluxaIcon.LAYER_UP)
            return
        }
        val after = Anim.splitAt(mid, hi)   // null bila Out sudah melewati akhir klip
        project.layers.remove(mid)           // buang potongan tengah
        if (after != null) project.layers.add(idx + 1, after)
        markIn = null
        markOut = null
        selectedId = layer.id
        afterChange(selectedId, openPanel = false)
        toast("Area In-Out dipotong", FluxaIcon.TRASH)
    }

    /** Geser playhead satu frame ke depan (dir=1) atau ke belakang (dir=-1). */
    private fun stepFrame(dir: Int) {
        if (playing) stopPlay()
        val fps = project.fps.coerceAtLeast(1).toFloat()
        val f = Math.round(playhead * fps) + dir
        setPlayhead(f / fps)
    }

    /** Samakan durasi project dengan ujung klip paling akhir (bisa mengecil juga). */
    private fun fitDurationToClips() {
        val end = project.layers.maxOfOrNull { it.start + it.dur } ?: 0f
        project.duration = max(0.5f, kotlin.math.ceil(end * 10f) / 10f)
        setPlayhead(playhead)
        preview.invalidate()
        timeline.invalidate()
        autosave()
        toast("Durasi pas dengan klip: ${Format.time(project.duration)}", FluxaIcon.RATIO)
    }

    private fun selectedLayerOrToastMember(): Layer? {
        val l = project.findLayer(selectedId)
        if (l == null) toast("Pilih layer dulu di timeline", FluxaIcon.INFO)
        return l
    }

    private fun showCutSheet() {
        val inTxt = markIn?.let { Format.time(it) } ?: "belum"
        val outTxt = markOut?.let { Format.time(it) } ?: "belum"
        val items = listOf(
            FluxaSheet.Item("Potong di playhead", "Pisah klip jadi dua bagian", FluxaIcon.SCISSORS, FluxaColors.Accent),
            FluxaSheet.Item("Buang kiri playhead", "Hapus bagian klip di kiri playhead", FluxaIcon.SKIP_START, FluxaColors.Sky),
            FluxaSheet.Item("Buang kanan playhead", "Hapus bagian klip di kanan playhead", FluxaIcon.NEXT, FluxaColors.Sky),
            FluxaSheet.Item("Tandai In di playhead", "In sekarang: $inTxt", FluxaIcon.LAYER_UP, FluxaColors.Amber),
            FluxaSheet.Item("Tandai Out di playhead", "Out sekarang: $outTxt", FluxaIcon.LAYER_DOWN, FluxaColors.Amber),
            FluxaSheet.Item("Potong area In-Out", "Buang bagian tengah klip di antara In dan Out", FluxaIcon.TRASH, FluxaColors.Rose),
            FluxaSheet.Item("Durasi pas ke klip", "Samakan durasi project dengan ujung klip terakhir", FluxaIcon.RATIO, FluxaColors.Green)
        )
        FluxaSheet.showList(this, "Potong & trim", items) { which ->
            when (which) {
                0 -> selectedLayerOrToastMember()?.let { splitLayerAtPlayhead(it) }
                1 -> selectedLayerOrToastMember()?.let { trimLeftAt(it) }
                2 -> selectedLayerOrToastMember()?.let { trimRightAt(it) }
                3 -> {
                    markIn = playhead
                    toast("In di ${Format.time(playhead)}", FluxaIcon.LAYER_UP)
                }
                4 -> {
                    markOut = playhead
                    toast("Out di ${Format.time(playhead)}", FluxaIcon.LAYER_DOWN)
                }
                5 -> selectedLayerOrToastMember()?.let { cutInOutFrom(it) }
                6 -> fitDurationToClips()
            }
        }
    }

    private fun splitLayerAtPlayhead(layer: Layer) {
        val second = Anim.splitAt(layer, playhead)
        if (second == null) {
            toast("Geser playhead ke tengah layer dulu buat memisah", FluxaIcon.SCISSORS)
            return
        }
        val idx = project.layers.indexOf(layer)
        project.layers.add(idx + 1, second)
        selectedId = second.id
        afterChange(selectedId)
        toast("Layer dipisah di ${Format.time(playhead)}", FluxaIcon.SCISSORS)
    }

    private fun reorderLayer(layer: Layer, dir: Int) {
        val idx = project.layers.indexOf(layer)
        if (idx < 0) return
        val nidx = idx + dir
        if (nidx !in project.layers.indices) {
            toast(if (dir > 0) "Layer sudah paling atas" else "Layer sudah paling bawah", FluxaIcon.INFO)
            return
        }
        val tmp = project.layers[idx]
        project.layers[idx] = project.layers[nidx]
        project.layers[nidx] = tmp
        afterChange(layer.id)
    }

    // =====================================================================
    // undo / redo
    // =====================================================================
    private fun pushHistory() {
        if (lastSnapshot.isEmpty()) return
        val now = ProjectStore.encode(project)
        if (now == lastSnapshot) return
        undoStack.add(lastSnapshot)
        while (undoStack.size > 40) undoStack.removeAt(0)
        redoStack.clear()
        lastSnapshot = now
        updateUndoButtons()
    }

    private fun updateUndoButtons() {
        if (!::undoBtn.isInitialized) return
        undoBtn.setTint(if (undoStack.isNotEmpty()) FluxaColors.Text else FluxaColors.Faint)
        redoBtn.setTint(if (redoStack.isNotEmpty()) FluxaColors.Text else FluxaColors.Faint)
    }

    private fun undo() {
        if (undoStack.isEmpty()) {
            toast("Belum ada yang bisa di-undo", FluxaIcon.INFO)
            return
        }
        redoStack.add(lastSnapshot)
        val snap = undoStack.removeAt(undoStack.lastIndex)
        lastSnapshot = snap
        restoreSnapshot(snap)
        updateUndoButtons()
        toast("Undo", FluxaIcon.UNDO)
    }

    private fun redo() {
        if (redoStack.isEmpty()) {
            toast("Belum ada yang bisa di-redo", FluxaIcon.INFO)
            return
        }
        undoStack.add(lastSnapshot)
        val snap = redoStack.removeAt(redoStack.lastIndex)
        lastSnapshot = snap
        restoreSnapshot(snap)
        updateUndoButtons()
        toast("Redo", FluxaIcon.REDO)
    }

    private fun restoreSnapshot(json: String) {
        val decoded = ProjectStore.decode(json) ?: return
        if (playing) {
            playing = false
            handler.removeCallbacks(ticker)
        }
        musicPlayer.sync(project.music, playhead, false)
        decoded.id = project.id
        project = decoded
        if (project.findLayer(selectedId) == null) selectedId = null
        if (playhead > project.duration) playhead = project.duration
        preview.project = project
        timeline.project = project
        preview.select(selectedId)
        timeline.selectedId = selectedId
        nameEdit.setText(project.name)
        updateBadge()
        updatePlayButton()
        if (inspectorPanel.visibility == View.VISIBLE) showInspectorFor(project.findLayer(selectedId))
        setPlayhead(playhead)
        preview.invalidate()
        timeline.invalidate()
        ProjectLibrary.save(applicationContext, project, touch = false)
    }

    // =====================================================================
    // musik latar
    // =====================================================================
    private fun showMusicDialog() {
        val m = project.music
        if (m == null) {
            FluxaSheet.show(
                this, "Musik latar",
                kText(
                    "Tambahkan musik dari galeri. Musik akan ikut ter-export ke MP4 (volume di video mengikuti file asli).",
                    12.5f, FluxaColors.Muted
                ),
                positiveText = "Pilih musik", positiveIcon = FluxaIcon.MUSIC,
                onPositive = { pickAudio() },
                neutralText = "Video → Audio",
                onNeutral = { pickVideoForAudio() },
                negativeText = "Batal"
            )
            return
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2f), 0, dp(2f))
        }
        val headRow = LinearLayout(this)
        headRow.orientation = LinearLayout.HORIZONTAL
        headRow.gravity = Gravity.CENTER_VERTICAL
        val mTile = FrameLayout(this)
        mTile.background = roundedRect(FluxaColors.CardHi, kdpf(10f))
        val mIv = IconView(this, FluxaIcon.MUSIC, FluxaColors.Accent)
        val mp = dp(8f)
        mIv.setPadding(mp, mp, mp, mp)
        mTile.addView(mIv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val lpMT = LinearLayout.LayoutParams(dp(38f), dp(38f))
        lpMT.rightMargin = dp(11f)
        headRow.addView(mTile, lpMT)
        val mCol = LinearLayout(this)
        mCol.orientation = LinearLayout.VERTICAL
        mCol.addView(kText(m.name, 13f, FluxaColors.Text, bold = true))
        mCol.addView(kText("Durasi file: ${Format.dec(AudioUtil.durationSeconds(m.path))}s", 11f, FluxaColors.Dim))
        headRow.addView(mCol)
        wrap.addView(headRow)

        val volLabel = kText("Volume preview: ${(m.volume * 100).toInt()}%", 11.5f, FluxaColors.Muted)
        val lpV = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpV.topMargin = dp(14f)
        wrap.addView(volLabel, lpV)
        val volSlider = FluxaSlider(this)
        volSlider.max = 100
        volSlider.progress = (m.volume * 100).toInt().coerceIn(0, 100)
        volSlider.onProgress = { progress ->
            volLabel.text = "Volume preview: $progress%"
            project.music?.volume = progress / 100f
            musicPlayer.sync(project.music, playhead, playing)
        }
        volSlider.onReleased = { autosave() }
        wrap.addView(volSlider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30f)))

        val posLabel = kText("Mulai: ${Format.dec(m.start)}s | Durasi: ${Format.dec(m.dur)}s", 11.5f, FluxaColors.Muted)
        val lpP = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpP.topMargin = dp(8f)
        wrap.addView(posLabel, lpP)
        val startSlider = FluxaSlider(this)
        val maxStart = max(1, project.duration.toInt() - 1)
        startSlider.max = maxStart
        startSlider.progress = m.start.toInt().coerceIn(0, maxStart)
        startSlider.onProgress = { progress ->
            val mm = project.music
            if (mm != null) {
                mm.start = progress.toFloat()
                if (mm.start + mm.dur > project.duration) mm.dur = max(0.5f, project.duration - mm.start)
                posLabel.text = "Mulai: ${Format.dec(mm.start)}s | Durasi: ${Format.dec(mm.dur)}s"
                timeline.invalidate()
                musicPlayer.sync(project.music, playhead, playing)
            }
        }
        startSlider.onReleased = { autosave() }
        wrap.addView(startSlider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30f)))

        FluxaSheet.show(
            this, "Musik latar", wrap,
            positiveText = "Tutup", positiveIcon = FluxaIcon.CHECK,
            onPositive = { autosave() },
            neutralText = "Ganti",
            onNeutral = { pickAudio() },
            negativeText = "Hapus",
            onNegative = {
                project.music = null
                musicPlayer.release()
                timeline.invalidate()
                autosave()
                toast("Musik dihapus", FluxaIcon.TRASH)
            }
        )
    }

    /** Buka picker custom (grid/list dari MediaStore). Kalau izin ditolak, pakai pemilih sistem. */
    private fun launchPicker(mode: String, req: Int, fallback: () -> Unit) {
        val i = Intent(this, MediaPickerActivity::class.java).putExtra(MediaPickerActivity.EXTRA_MODE, mode)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, req)
        } catch (e: Throwable) {
            fallback()
        }
    }

    private fun pickVideoForAudio() {
        launchPicker(MediaPickerActivity.MODE_VIDEO, REQ_PICK_VIDEO_AUDIO) { pickVideoSystem() }
    }

    private fun pickVideoSystem() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_PICK_VIDEO_AUDIO_SYS)
        } catch (e: Throwable) {
            toast("Tidak bisa membuka video", FluxaIcon.INFO)
        }
    }

    private fun pickAudio() {
        launchPicker(MediaPickerActivity.MODE_AUDIO, REQ_PICK_AUDIO) { pickAudioSystem() }
    }

    private fun pickAudioSystem() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_AUDIO)
        } catch (e: Throwable) {
            toast("Tidak bisa membuka file audio", FluxaIcon.INFO)
        }
    }

    private fun importAudioFrom(uri: Uri, nameOverride: String? = null) {
        toast("Mengimpor musik...", FluxaIcon.MUSIC)
        Thread {
            val ctx = applicationContext
            val name = nameOverride ?: queryDisplayName(uri) ?: "musik"
            val path = ProjectStore.importAudio(ctx, uri, name)
            if (path == null) {
                runOnUiThread { if (!isFinishing) toast("Gagal mengimpor musik", FluxaIcon.INFO) }
                return@Thread
            }
            val durSec = AudioUtil.durationSeconds(path)
            val wave = AudioUtil.decodeEnvelope(path)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val p = project
                val m = Music()
                m.path = path
                m.name = name.substringBeforeLast('.').take(24)
                m.start = 0f
                m.dur = min(p.duration, if (durSec > 0.5f) durSec else p.duration)
                m.offset = 0f
                m.volume = 0.8f
                m.wave = wave
                p.music = m
                timeline.project = p
                timeline.invalidate()
                autosave()
                musicPlayer.sync(p.music, playhead, playing)
                toast(if (wave != null) "Musik ditambahkan" else "Musik ditambahkan (waveform tidak tersedia)", FluxaIcon.MUSIC)
                showMusicDialog()
            }
        }.start()
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (e: Throwable) {
            null
        }
    }

    // =====================================================================
    // image pick
    // =====================================================================
    private var pendingImageLayer: Layer? = null
    /** Shape yang menunggu media untuk isinya (null = pilihan galeri biasa). */
    private var pendingFillLayer: Layer? = null

    private fun pickFill(layer: Layer, mode: String) {
        pendingFillLayer = layer
        if (mode == "video") {
            launchPicker(MediaPickerActivity.MODE_VIDEO, REQ_PICK_VIDEO_LAYER) { pickVideoLayerSystem() }
        } else {
            pendingImageLayer = null
            launchPicker(MediaPickerActivity.MODE_IMAGE, REQ_PICK_IMAGE) { pickImageSystem() }
        }
    }

    private fun pickImage(replace: Layer?) {
        pendingFillLayer = null
        pendingImageLayer = replace
        launchPicker(MediaPickerActivity.MODE_IMAGE, REQ_PICK_IMAGE) { pickImageSystem() }
    }

    private fun pickImageSystem() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_IMAGE)
        } catch (e: Throwable) {
            toast("Tidak bisa membuka galeri", FluxaIcon.INFO)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_EXPORT_PREVIEW) {
            if (resultCode == RESULT_OK) toast("Video tersimpan ke Movies/FluxaMotion", FluxaIcon.FILM)
            return
        }
        if (resultCode != RESULT_OK && resultCode != MediaPickerActivity.RESULT_FALLBACK) return
        if (requestCode == REQ_PICK_IMAGE || requestCode == REQ_PICK_AUDIO || requestCode == REQ_PICK_VIDEO_AUDIO || requestCode == REQ_PICK_VIDEO_LAYER) {
            if (resultCode == MediaPickerActivity.RESULT_FALLBACK) {
                when (requestCode) {
                    REQ_PICK_IMAGE -> pickImageSystem()
                    REQ_PICK_AUDIO -> pickAudioSystem()
                    REQ_PICK_VIDEO_LAYER -> pickVideoLayerSystem()
                    else -> pickVideoSystem()
                }
                return
            }
            if (resultCode != RESULT_OK) return
            val puri: Uri = data?.data ?: return
            when (requestCode) {
                REQ_PICK_IMAGE -> handleImagePicked(puri)
                REQ_PICK_AUDIO -> importAudioFrom(puri)
                REQ_PICK_VIDEO_LAYER -> handleVideoPicked(puri, data?.getStringExtra(MediaPickerActivity.EXTRA_KEY))
                else -> extractAudioFromVideo(puri)
            }
            return
        }
        if (requestCode == REQ_PICK_VIDEO_AUDIO_SYS) {
            val vuri: Uri = data?.data ?: return
            extractAudioFromVideo(vuri)
            return
        }
        if (requestCode == REQ_AUDIO) {
            val auri: Uri = data?.data ?: return
            importAudioFrom(auri)
            return
        }
        if (requestCode == REQ_OBJ) {
            val ouri: Uri = data?.data ?: return
            importObjFrom(ouri)
            return
        }
        if (requestCode == REQ_SVG) {
            val suri: Uri = data?.data ?: return
            importSvgFrom(suri)
            return
        }
        if (requestCode != REQ_IMAGE) return
        val uri: Uri = data?.data ?: return
        handleImagePicked(uri)
    }

    /** Ubah video -> audio: track AAC disalin ke M4A lalu dijadikan musik latar project. */
    private fun extractAudioFromVideo(uri: Uri) {
        toast("Mengambil audio dari video...", FluxaIcon.MUSIC)
        Thread {
            val base = (queryDisplayName(uri) ?: "video").substringBeforeLast('.')
            val out = java.io.File(cacheDir, "vidaudio_${System.currentTimeMillis()}.m4a")
            val res = VideoAudio.extractToM4a(applicationContext, uri, out)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (res.path == null) {
                    FluxaSheet.showMessage(this, "Ubah video ke audio gagal", res.error ?: "Tidak diketahui", icon = FluxaIcon.INFO)
                } else {
                    importAudioFrom(Uri.fromFile(java.io.File(res.path)), "$base (audio)")
                }
            }
        }.start()
    }

    private fun pickVideoLayer() {
        pendingFillLayer = null
        launchPicker(MediaPickerActivity.MODE_VIDEO, REQ_PICK_VIDEO_LAYER) { pickVideoLayerSystem() }
    }

    private fun pickVideoLayerSystem() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        startActivityForResult(intent, REQ_PICK_VIDEO_LAYER)
    }

    private fun handleVideoPicked(uri: Uri, key: String? = null) {
        val fillTarget = pendingFillLayer
        pendingFillLayer = null
        val prog = FluxaSheet.showProgress(this, "Menyiapkan video", "Menyalin video ke penyimpanan app")
        Thread {
            val path = ProjectStore.importVideo(applicationContext, uri, key) { f ->
                runOnUiThread { if (!isFinishing) prog.setProgress(f, "${(f * 100).toInt()}%") }
            }
            val info = if (path != null) VideoFrames.info(path) else null
            runOnUiThread {
                prog.dismiss()
                if (isFinishing) return@runOnUiThread
                if (path == null || info == null || info.width <= 0 || info.height <= 0) {
                    toast("Gagal membaca video", FluxaIcon.INFO)
                    return@runOnUiThread
                }
                if (fillTarget != null) {
                    fillTarget.fillMode = "video"
                    fillTarget.fillPath = path
                    fillTarget.fillBitmap = null
                    afterChange(fillTarget.id)
                    toast("Isi shape diganti video", FluxaIcon.FILM)
                    return@runOnUiThread
                }
                val p = project
                val sc = min(p.width * 0.8f / info.width, p.height * 0.62f / info.height)
                val clipDur = if (info.durationSec > 0.05f) info.durationSec else 5f
                val l = Layer().apply {
                    kind = KIND_VIDEO
                    name = "Video ${p.layers.count { it.kind == KIND_VIDEO } + 1}"
                    imgPath = path
                    imgW = info.width * sc
                    imgH = info.height * sc
                    x = p.width / 2f
                    y = p.height / 2f
                    start = playhead.coerceAtLeast(0f)
                    dur = clipDur
                }
                p.layers.add(l)
                afterChange(l.id)
                selectLayer(l.id, openPanel = true)
                toast("Video ditambahkan", FluxaIcon.FILM)
            }
        }.start()
    }

    private fun handleImagePicked(uri: Uri) {
        val fillTarget = pendingFillLayer
        pendingFillLayer = null
        toast("Mengimpor gambar...", FluxaIcon.IMAGE)
        Thread {
            val ctx = applicationContext
            val path = ProjectStore.importImage(ctx, uri)
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (path != null) android.graphics.BitmapFactory.decodeFile(path, bounds)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (path == null || bounds.outWidth <= 0) {
                    toast("Gagal membaca gambar", FluxaIcon.INFO)
                    return@runOnUiThread
                }
                if (fillTarget != null) {
                    fillTarget.fillMode = "image"
                    fillTarget.fillPath = path
                    fillTarget.fillBitmap = null
                    afterChange(fillTarget.id)
                    toast("Isi shape diganti gambar", FluxaIcon.IMAGE)
                    return@runOnUiThread
                }
                val p = project
                val target = pendingImageLayer
                if (target != null) {
                    target.imgPath = path
                    target.bitmap = null
                    val sc = min(p.width * 0.8f / bounds.outWidth, p.height * 0.6f / bounds.outHeight)
                    target.imgW = bounds.outWidth * sc
                    target.imgH = bounds.outHeight * sc
                    afterChange(target.id)
                    toast("Gambar diganti", FluxaIcon.IMAGE)
                } else {
                    val sc = min(p.width * 0.8f / bounds.outWidth, p.height * 0.62f / bounds.outHeight)
                    val l = Layer().apply {
                        kind = KIND_IMAGE
                        name = "Gambar ${p.layers.count { it.kind == KIND_IMAGE } + 1}"
                        imgPath = path
                        imgW = bounds.outWidth * sc
                        imgH = bounds.outHeight * sc
                        x = p.width / 2f
                        y = p.height / 2f
                        start = playhead.coerceAtLeast(0f)
                        dur = 5f
                    }
                    p.layers.add(l)
                    afterChange(l.id)
                    selectLayer(l.id, openPanel = true)
                    toast("Gambar ditambahkan", FluxaIcon.IMAGE)
                }
                pendingImageLayer = null
            }
        }.start()
    }

    // =====================================================================
    // export
    // =====================================================================
    private fun showExportDialog() {
        val p = project
        ExportSettingsSheet.show(this, p.width, p.height, p.duration) { r ->
            runExport(r.longSide, r.fps, r.mbps)
        }
    }

    /** Format durasi: 45 dtk, 2 mnt 5 dtk, 1 jam 3 mnt. */
    private fun formatDuration(sec: Float): String {
        val s = sec.roundToInt().coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        return when {
            h > 0 -> "$h jam $m mnt"
            m > 0 -> "$m mnt $r dtk"
            else -> "$r dtk"
        }
    }

    /** Jam selesai perkiraan (HH:mm) dari sekarang + sisa detik. */
    private fun clockAfter(sec: Float): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.SECOND, sec.roundToInt().coerceAtLeast(0))
        return String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }

    private fun runExport(longSide: Int, fps: Int = 30, mbps: Int = 0) {
        if (playing) stopPlay()
        musicPlayer.sync(project.music, playhead, false)
        val p = project
        val handle = FluxaSheet.showProgress(this, "Mengekspor video", "Menyiapkan encoder...")

        Thread {
            val ctx = applicationContext
            val startedAt = System.currentTimeMillis()
            fun progressFn(f: Float) {
                // perkiraan sisa waktu dari kecepatan render sejauh ini
                val elapsed = (System.currentTimeMillis() - startedAt) / 1000f
                val pct = (f * 100f).toInt().coerceIn(0, 100)
                val eta = if (f > 0.01f) elapsed * (1f - f) / f else -1f
                val status = if (eta >= 0f) {
                    "Merender frame video · $pct% · sisa ${formatDuration(eta)} (selesai ${clockAfter(eta)})"
                } else {
                    "Merender frame video · $pct% · menghitung perkiraan..."
                }
                runOnUiThread { if (!isFinishing) handle.setProgress(f, status) }
            }
            var result = Exporter.exportVideo(ctx, p, longSide, fps, mbps) { f -> progressFn(f) }
            // fallback otomatis: beberapa HP tidak mendukung encode 1080p tertentu
            if (result.error != null && (longSide > 1280 || fps > 30)) {
                runOnUiThread { if (!isFinishing) handle.setProgress(0f, "Setelan tidak didukung HP ini, turun ke 720p 30fps...") }
                result = Exporter.exportVideo(ctx, p, 1280, 30) { f -> progressFn(f) }
            }
            val res = result
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                handle.dismiss()
                val f = res.file
                if (res.error != null || f == null) {
                    FluxaSheet.showMessage(
                        this, "Export gagal",
                        "${res.error ?: "File tidak valid"}\n\nCoba resolusi, fps, atau bitrate yang lebih rendah, lalu pastikan penyimpanan cukup.",
                        icon = FluxaIcon.INFO
                    )
                } else {
                    // ringkasan waktu render (dan fallback decoder) supaya bisa dibandingkan di HP
                    res.stats?.let { android.widget.Toast.makeText(this, it, android.widget.Toast.LENGTH_LONG).show() }
                    val resLabel = when {
                        longSide >= 3840 -> "4K"
                        longSide >= 1920 -> "1080p"
                        else -> "720p"
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(
                        Intent(this, ExportPreviewActivity::class.java)
                            .putExtra(ExportPreviewActivity.EXTRA_PATH, f.absolutePath)
                            .putExtra(ExportPreviewActivity.EXTRA_LABEL, "$resLabel · ${fps}fps · ${Format.dec(p.duration)} dtk"),
                        REQ_EXPORT_PREVIEW
                    )
                }
            }
        }.start()
    }

    private fun exportPng() {
        val p = project
        toast("Menyimpan frame PNG...", FluxaIcon.IMAGE)
        Thread {
            val uri = Exporter.exportFramePng(applicationContext, p, playhead)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (uri == null) toast("Gagal export PNG", FluxaIcon.INFO)
                else {
                    toast("Frame PNG tersimpan di Pictures/FluxaMotion", FluxaIcon.IMAGE)
                    shareUri(uri, "image/png")
                }
            }
        }.start()
    }

    private fun shareUri(uri: Uri?, mime: String) {
        if (uri == null) return
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Bagikan"))
        } catch (e: Throwable) {
            toast("Tidak ada aplikasi untuk membagikan", FluxaIcon.INFO)
        }
    }

    // =====================================================================
    // menu & dialogs
    // =====================================================================
    private fun showMenu() {
        val items = listOf(
            FluxaSheet.Item("Musik latar", "Musik ikut ter-export ke MP4", FluxaIcon.MUSIC, FluxaColors.Accent),
            FluxaSheet.Item("Pengaturan project", "Rasio, durasi, warna latar", FluxaIcon.SETTINGS, FluxaColors.Sky),
            FluxaSheet.Item("Simpan sekarang", "Tersimpan juga otomatis tiap perubahan", FluxaIcon.SAVE, FluxaColors.Green),
            FluxaSheet.Item("Muat demo", "Ganti isi project ini dengan demo", FluxaIcon.BOLT, FluxaColors.Amber),
            FluxaSheet.Item("Project baru (kosong)", "Isi project ini dikosongkan", FluxaIcon.PLUS, FluxaColors.Violet),
            FluxaSheet.Item("Export frame PNG", "Simpan frame saat ini sebagai gambar", FluxaIcon.IMAGE, FluxaColors.Rose),
            FluxaSheet.Item("Layer dari AI", "Tulis prompt, AI menyusun teks dan animasi", FluxaIcon.SPARK, FluxaColors.Violet),
            FluxaSheet.Item("Caption otomatis", "Transkrip musik jadi teks (Groq Whisper)", FluxaIcon.TEXT, FluxaColors.Sky),
            FluxaSheet.Item("Keyframe ke beat", "Pulsa zoom, goyang, atau kedip mengikuti ketukan lagu", FluxaIcon.SPARK, FluxaColors.Amber),
            FluxaSheet.Item("Bagikan project", "Unggah ke link share (perlu login Google)", FluxaIcon.SHARE, FluxaColors.Green),
            FluxaSheet.Item("Panduan", "Cara pakai editor", FluxaIcon.BOOK, FluxaColors.Sky),
            FluxaSheet.Item("Tentang", "Versi dan info aplikasi", FluxaIcon.INFO, FluxaColors.Muted),
            FluxaSheet.Item("Kembali ke Beranda", "Project tersimpan otomatis", FluxaIcon.HOME, FluxaColors.Accent)
        )
        FluxaSheet.showList(this, "Menu project", items) { which ->
            when (which) {
                0 -> showMusicDialog()
                1 -> showProjectSettings()
                2 -> {
                    ProjectLibrary.save(applicationContext, project)
                    toast("Project disimpan", FluxaIcon.SAVE)
                }
                3 -> loadDemo()
                4 -> newProject()
                5 -> exportPng()
                6 -> AiActions.promptLayers(this, project) { addAiLayers(it) }
                7 -> AiActions.captionsFromMusic(this, project) { addAiLayers(it) }
                8 -> showBeatSheet()
                9 -> CloudSettings.shareProject(this, project)
                10 -> showGuide()
                11 -> showAbout()
                12 -> finish()
            }
        }
    }

    /** Keyframe otomatis dari panel Inspector: pola waktu langsung, pulsa ketukan lewat deteksi beat. */
    private fun applyAutoKeys(layer: Layer, key: String, intensity: Float, rate: Int) {
        val name = key.substringAfter(":")
        if (key.startsWith("t:")) {
            val kind = try { AutoPattern.Kind.valueOf(name) } catch (e: IllegalArgumentException) { return }
            val n = AutoPattern.apply(layer, kind, rate, intensity)
            afterChange(layer.id)
            toast("Pola dipasang ($n keyframe)", FluxaIcon.SPARK)
        } else {
            val style = try { BeatKeys.Style.valueOf(name) } catch (e: IllegalArgumentException) { return }
            val every = intArrayOf(1, 2, 4, 8)[rate.coerceIn(0, 3)]
            applyBeatStyle(style, layer.id, every, intensity)
        }
    }

    /** Pilih gaya pulsa beat, lalu pasang keyframe di layer terpilih. */
    private fun showBeatSheet() {
        val m = project.music
        if (m == null || m.path.isEmpty() || !File(m.path).exists()) {
            toast("Tambah musik latar dulu di menu editor", FluxaIcon.MUSIC)
            return
        }
        if (selectedLayerOrToastMember() == null) return
        val styles = BeatKeys.Style.values()
        val items = styles.map { FluxaSheet.Item(it.label, it.desc, it.icon, FluxaColors.Amber) }
        FluxaSheet.showList(this, "Keyframe ke beat", items) { which -> applyBeatStyle(styles[which]) }
    }

    /** Deteksi beat di background, lalu tulis keyframe ke layer yang dipilih. */
    private fun applyBeatStyle(style: BeatKeys.Style, layerIdArg: String? = null, every: Int = 1, intensity: Float = 1f) {
        val m = project.music ?: run {
            toast("Tambah musik latar dulu di menu editor", FluxaIcon.MUSIC)
            return
        }
        val layerId = layerIdArg ?: selectedId ?: return
        val handle = FluxaSheet.showProgress(this, "Mendeteksi beat", "Membaca audio...")
        Thread {
            val beats = try {
                BeatKeys.detect(m.path, m.start, m.offset, m.dur, project.duration)
            } catch (e: Exception) {
                emptyList()
            }
            runOnUiThread {
                handle.dismiss()
                val layer = project.findLayer(layerId)
                when {
                    beats.isEmpty() -> toast("Beat tidak terdeteksi di musik ini", FluxaIcon.INFO)
                    layer == null -> toast("Layer sudah tidak ada", FluxaIcon.INFO)
                    else -> {
                        val n = BeatKeys.apply(layer, beats, style, every, intensity)
                        afterChange(layer.id)
                        toast("$n pulsa di ketukan lagu", FluxaIcon.SPARK)
                    }
                }
            }
        }.start()
    }

    /** Tambah layer hasil AI ke project, pilih layer terakhir, lalu simpan. */
    private fun addAiLayers(layers: List<Layer>) {
        val p = project
        p.layers.addAll(layers)
        afterChange(layers.last().id)
        ProjectLibrary.save(applicationContext, p)
        toast("${layers.size} layer AI ditambahkan", FluxaIcon.SPARK)
    }

    private fun showProjectSettings() {
        val ratios = Templates.RATIOS
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4f), 0, dp(2f))
        }
        wrap.addView(kText("Rasio kanvas", 11.5f, FluxaColors.Muted))
        val ratioDrop = FluxaDropdown(this)
        ratioDrop.sheetTitle = "Rasio kanvas"
        ratioDrop.setItems(
            ratios.map { "${it.label}  (${it.w} x ${it.h})" },
            ratios.indexOfFirst { it.w == project.width && it.h == project.height }.coerceAtLeast(0)
        )
        val lpR = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpR.topMargin = dp(7f)
        wrap.addView(ratioDrop, lpR)

        val durLabel = kText("Durasi (detik, 2 - 60)", 11.5f, FluxaColors.Muted)
        val lpD = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpD.topMargin = dp(14f)
        wrap.addView(durLabel, lpD)
        val durEdit = kEditText(project.duration.toInt().toString(), hint = "8", numeric = true)
        val lpDE = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpDE.topMargin = dp(7f)
        wrap.addView(durEdit, lpDE)

        val bgLabel = kText("Warna latar", 11.5f, FluxaColors.Muted)
        val lpB = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpB.topMargin = dp(14f)
        wrap.addView(bgLabel, lpB)
        var newBg = project.bg
        val swRow = LinearLayout(this)
        swRow.orientation = LinearLayout.HORIZONTAL
        val bgColors = intArrayOf(
            0xFF05070C.toInt(), 0xFF000000.toInt(), 0xFF101828.toInt(), 0xFF1A0B2E.toInt(),
            0xFF0B1F1A.toInt(), 0xFF2B0F1A.toInt(), 0xFFFFFFFF.toInt(), 0xFF2EE6C0.toInt()
        )
        fun refreshSwatches() {
            for (i in 0 until swRow.childCount) {
                val dd = swRow.getChildAt(i).background as GradientDrawable
                dd.setStroke(dp(2f), if (bgColors[i] == newBg) FluxaColors.Accent else 0xFF2A3040.toInt())
            }
        }
        for (c in bgColors) {
            val v = View(this)
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(c)
            d.setStroke(dp(2f), if (c == newBg) FluxaColors.Accent else 0xFF2A3040.toInt())
            v.background = d
            val lp = LinearLayout.LayoutParams(dp(30f), dp(30f))
            lp.rightMargin = dp(7f)
            lp.topMargin = dp(8f)
            swRow.addView(v, lp)
            v.setOnClickListener {
                newBg = c
                refreshSwatches()
            }
        }
        wrap.addView(swRow)
        val customBgBtn = FluxaButton(this, "Warna Custom...", FluxaIcon.SPARK, FluxaButtonStyle.GHOST, 11.5f)
        customBgBtn.setOnClickListener {
            FluxaColorPicker.show(this, newBg, "Warna Latar Custom") { col ->
                newBg = col
                bgLabel.text = "Warna latar: " + com.fluxa.motion.core.model.Format.color(col)
                refreshSwatches()
            }
        }
        val lpCB = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpCB.topMargin = dp(10f)
        wrap.addView(customBgBtn, lpCB)

        FluxaSheet.show(
            this, "Pengaturan project", wrap,
            positiveText = "Terapkan", positiveIcon = FluxaIcon.CHECK,
            onPositive = {
                val r = ratios[ratioDrop.selectedIndex]
                applyProjectSettings(r.w, r.h, durEdit.text.toString().toFloatOrNull() ?: project.duration, newBg)
            },
            negativeText = "Batal"
        )
    }

    private fun applyProjectSettings(w: Int, h: Int, dur: Float, bg: Int) {
        val p = project
        if (w != p.width || h != p.height) {
            val sx = w.toFloat() / p.width
            val sy = h.toFloat() / p.height
            val s = min(sx, sy)
            for (l in p.layers) {
                l.x *= sx
                l.y *= sy
                if (l.kind == KIND_TEXT) {
                    l.textSize *= s
                    l.strokeW *= s
                }
                if (l.kind == KIND_SHAPE) {
                    l.w *= s
                    l.h *= s
                    l.radius *= s
                }
                if (l.kind == KIND_IMAGE) {
                    l.imgW *= s
                    l.imgH *= s
                }
                l.channels["x"]?.keys?.forEach { it.v *= sx }
                l.channels["y"]?.keys?.forEach { it.v *= sy }
            }
            p.width = w
            p.height = h
        }
        p.bg = bg
        val nd = dur.coerceIn(0.5f, 3600f) // durasi tanpa batas praktis
        p.duration = nd
        for (l in p.layers) {
            if (l.start > nd - 0.2f) l.start = max(0f, nd - 1f)
            if (l.start + l.dur > nd) l.dur = max(0.2f, nd - l.start)
        }
        if (playhead > nd) setPlayhead(nd)
        updateBadge()
        afterChange(selectedId)
        showInspectorFor(project.findLayer(selectedId))
        toast("Project diubah ke ${w} x ${h}", FluxaIcon.RATIO)
    }

    private fun replaceProjectContent(fresh: Project) {
        musicPlayer.release()
        val keepId = project.id
        project = fresh
        project.id = keepId
        selectedId = null
        playhead = 0f
        preview.project = project
        timeline.project = project
        preview.select(null)
        timeline.selectedId = null
        timeline.invalidate()
        preview.invalidate()
        setPlayhead(0f)
        updateTimecode()
        updateBadge()
        nameEdit.setText(project.name)
        afterChange(null, openPanel = false)
    }

    private fun loadDemo() {
        replaceProjectContent(Templates.buildDemo())
        toast("Demo dimuat", FluxaIcon.BOLT)
    }

    private fun newProject() {
        FluxaSheet.showConfirm(
            this, "Project baru?",
            "Isi project yang sedang dibuka akan dikosongkan. Project lain di pustaka tidak ikut terhapus.",
            positiveText = "Kosongkan", danger = true,
            onPositive = {
                replaceProjectContent(Templates.newProject(project.width, project.height))
                toast("Project kosong dibuat", FluxaIcon.PLUS)
            }
        )
    }

    private fun showGuide() {
        val msg = """
            ALUR CEPAT
            - Tambah layer: Teks / Bentuk / Gambar (tombol bawah)
            - Pindah layer: drag di kanvas
            - Resize: tarik handle pojok
            - Rotasi: tarik knob di atas layer
            - Timeline: drag bar = geser waktu, ujung kanan = durasi
            - Undo / Redo: ikon panah melingkar di bar atas (40 langkah riwayat)
            - Pisah layer: panel Properti, tombol Pisah memotong layer tepat di playhead
            - Urutan layer: tombol Maju / Mundur; Tengahkan untuk snap ke tengah kanvas
            - Magnet: ikon magnet di transport untuk snap tengah + rotasi 15 derajat
            - Keyframe: panel Properti, ketuk ikon wajik per properti
            - Preset: Fade, Slide, Zoom, Spin, Bounce, Typing, Ken Burns, Glitch
            - Export MP4 ke galeri (Movies/FluxaMotion)

            TIPS
            - Posisikan playhead, ubah nilai, lalu ketuk wajik untuk bikin keyframe di titik itu
            - Wajik terisi penuh artinya ada keyframe tepat di playhead; ketuk lagi untuk hapus
            - Zoom preview: cubit dua jari (maks 6x), geser pakai dua jari atau satu jari di area kosong saat zoom; ketuk chip di pojok untuk kembali 100%
            - Fokus keyframe: pilih layer, lalu ketuk ikon wajik di header timeline untuk zoom ke rentang keyframe layer itu
            - Resize layer: pakai handle pojok
            - Efek Blur pada gambar memakai downscale supaya ringan
            - Preview terasa berat? turunkan Glow atau kurangi jumlah layer
        """.trimIndent()
        FluxaSheet.showMessage(this, "Panduan Fluxa", msg, icon = FluxaIcon.BOOK)
    }

    private fun showAbout() {
        FluxaSheet.showMessage(
            this, "Fluxa",
            "Motion graphics editor native Android.\n\nVersi 1.4.1\nKotlin | Canvas | MediaCodec\nTanpa iklan, tanpa library eksternal.\nSemua ikon digambar manual pakai Canvas.",
            icon = FluxaIcon.BOLT
        )
    }

    // =====================================================================
    // misc
    // =====================================================================
    private fun autosave() {
        pushHistory()
        val token = ++autosaveToken
        handler.postDelayed({
            if (token == autosaveToken) ProjectLibrary.save(applicationContext, project, touch = false)
        }, 800)
    }

    private fun toast(msg: String, icon: FluxaIcon = FluxaIcon.CHECK) {
        FluxaToast.show(this, msg, icon)
    }

    companion object {
        const val EXTRA_PROJECT_ID = "extra_project_id"
        const val EXTRA_TEMPLATE_ID = "extra_template_id"
        const val EXTRA_NEW_W = "extra_new_w"
        const val EXTRA_NEW_H = "extra_new_h"
        const val EXTRA_DEMO = "extra_demo"

        private const val REQ_IMAGE = 1001
        private const val REQ_AUDIO = 1002
        private const val REQ_OBJ = 1003
        private const val REQ_SVG = 1004
        private const val REQ_PICK_IMAGE = 1101
        private const val REQ_PICK_AUDIO = 1102
        private const val REQ_PICK_VIDEO_AUDIO = 1103
        private const val REQ_PICK_VIDEO_AUDIO_SYS = 1104
        private const val REQ_PICK_VIDEO_LAYER = 1105
        private const val REQ_EXPORT_PREVIEW = 1105
    }
}
