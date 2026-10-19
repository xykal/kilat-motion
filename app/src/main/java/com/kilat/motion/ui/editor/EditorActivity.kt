package com.kilat.motion.ui.editor

import com.kilat.motion.core.model.*
import com.kilat.motion.core.animation.Anim
import com.kilat.motion.core.audio.AudioUtil
import com.kilat.motion.core.audio.MusicPlayer
import com.kilat.motion.core.export.Exporter
import com.kilat.motion.data.ProjectLibrary
import com.kilat.motion.data.ProjectStore
import com.kilat.motion.data.Templates

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
import com.kilat.motion.ui.components.IconView
import com.kilat.motion.ui.components.KilatDropdown
import com.kilat.motion.ui.components.KilatIcon
import com.kilat.motion.ui.components.KilatIconButton
import com.kilat.motion.ui.components.KilatSheet
import com.kilat.motion.ui.components.KilatSlider
import com.kilat.motion.ui.components.KilatToast
import com.kilat.motion.ui.components.kEditText
import com.kilat.motion.ui.theme.KColors
import com.kilat.motion.ui.theme.dp as kdp
import com.kilat.motion.ui.theme.dpf as kdpf
import com.kilat.motion.ui.theme.kText
import com.kilat.motion.ui.theme.kilatGradient
import com.kilat.motion.ui.theme.ovalBg
import com.kilat.motion.ui.theme.pressEffect
import com.kilat.motion.ui.theme.roundedRect
import com.kilat.motion.ui.theme.roundedTop
import kotlin.math.max
import kotlin.math.min

/**
 * Editor KILAT Motion.
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
    private lateinit var loopBtn: KilatIconButton
    private lateinit var undoBtn: KilatIconButton
    private lateinit var redoBtn: KilatIconButton
    private lateinit var magnetBtn: KilatIconButton

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
        window.statusBarColor = KColors.BgTop
        window.navigationBarColor = KColors.Bg

        project = resolveProject()
        buildUi()
        setPlayhead(0f)
        updateTimecode()
        showInspectorFor(null)
        ProjectLibrary.save(applicationContext, project, touch = false)
        lastSnapshot = ProjectStore.encode(project)
        updateUndoButtons()
    }

    /** Project ditentukan dari intent homescreen: buka / template / baru / terakhir. */
    private fun resolveProject(): Project {
        val templateId = intent.getStringExtra(EXTRA_TEMPLATE_ID)
        if (templateId != null) {
            Templates.createFromTemplate(templateId)?.let { return it }
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
    private fun toolItem(icon: KilatIcon, label: String, accent: Boolean = false, onClick: () -> Unit): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.isClickable = true
        col.setPadding(dp(2f), dp(5f), dp(2f), dp(4f))
        val tile = FrameLayout(this)
        tile.background = if (accent) kilatGradient(kdpf(13f))
        else roundedRect(KColors.CardHi, kdpf(13f))
        val iv = IconView(this, icon, if (accent) 0xFF05131F.toInt() else KColors.Text)
        val pad = dp(10f)
        iv.setPadding(pad, pad, pad, pad)
        tile.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        col.addView(tile, LinearLayout.LayoutParams(dp(42f), dp(42f)))
        val tv = kText(label, 9.5f, if (accent) KColors.Accent else KColors.Muted, bold = true)
        tv.gravity = Gravity.CENTER
        val lpTv = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpTv.topMargin = dp(4f)
        col.addView(tv, lpTv)
        col.pressEffect(0.93f)
        col.setOnClickListener { onClick() }
        return col
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        root.setBackgroundColor(KColors.Bg)

        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        main.fitsSystemWindows = true

        // ---------- top bar ----------
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(10f), dp(8f), dp(8f), dp(6f))
        top.setBackgroundColor(KColors.BgTop)

        val backBtn = KilatIconButton(this, KilatIcon.BACK, KColors.Muted, filled = false, sizeDp = 34f)
        backBtn.setOnClickListener { finish() }
        top.addView(backBtn, LinearLayout.LayoutParams(dp(34f), dp(34f)))

        val logoTile = FrameLayout(this)
        logoTile.background = kilatGradient(kdpf(10f))
        val bolt = IconView(this, KilatIcon.BOLT, 0xFF05131F.toInt())
        val lpBoltPad = dp(7f)
        bolt.setPadding(lpBoltPad, lpBoltPad, lpBoltPad, lpBoltPad)
        logoTile.addView(bolt, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val lpLogo = LinearLayout.LayoutParams(dp(32f), dp(32f))
        lpLogo.leftMargin = dp(4f)
        top.addView(logoTile, lpLogo)

        undoBtn = KilatIconButton(this, KilatIcon.UNDO, KColors.Faint, filled = false, sizeDp = 34f)
        undoBtn.setOnClickListener { undo() }
        val lpUndo = LinearLayout.LayoutParams(dp(34f), dp(34f))
        lpUndo.marginStart = dp(6f)
        top.addView(undoBtn, lpUndo)
        redoBtn = KilatIconButton(this, KilatIcon.REDO, KColors.Faint, filled = false, sizeDp = 34f)
        redoBtn.setOnClickListener { redo() }
        top.addView(redoBtn, LinearLayout.LayoutParams(dp(34f), dp(34f)))

        nameEdit = EditText(this)
        nameEdit.setText(project.name)
        nameEdit.setTextColor(KColors.Muted)
        nameEdit.textSize = 12f
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

        val menuBtn = KilatIconButton(this, KilatIcon.MORE, KColors.Muted, filled = true, sizeDp = 34f)
        menuBtn.setOnClickListener { showMenu() }
        top.addView(menuBtn, LinearLayout.LayoutParams(dp(34f), dp(34f)))

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
                    if (inspectorPanel.visibility == View.VISIBLE) showInspectorFor(project.findLayer(selectedId))
                }
            }

            override fun onRequestProperties() {
                showPanel()
            }
        }
        previewWrap.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        badge = kText("", 9.5f, KColors.Dim, bold = true)
        badge.setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
        badge.background = roundedRect(0xCC0A0C11.toInt(), kdpf(6f))
        val lpBadge = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpBadge.gravity = Gravity.TOP or Gravity.START
        lpBadge.leftMargin = dp(10f)
        lpBadge.topMargin = dp(8f)
        previewWrap.addView(badge, lpBadge)
        updateBadge()

        main.addView(previewWrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---------- transport ----------
        val transport = LinearLayout(this)
        transport.orientation = LinearLayout.HORIZONTAL
        transport.gravity = Gravity.CENTER_VERTICAL
        transport.setPadding(dp(14f), dp(4f), dp(14f), dp(6f))

        val skipBtn = KilatIconButton(this, KilatIcon.SKIP_START, KColors.Muted, filled = false, sizeDp = 38f)
        skipBtn.setOnClickListener {
            setPlayhead(0f)
            if (playing) {
                playStart = 0f
                playT0 = System.nanoTime()
            }
        }
        transport.addView(skipBtn, LinearLayout.LayoutParams(dp(38f), dp(38f)))

        val playWrap = FrameLayout(this)
        playWrap.background = ovalBg(0xFFFFFFFF.toInt())
        playWrap.isClickable = true
        playIcon = IconView(this, KilatIcon.PLAY, KColors.Bg)
        val pp = dp(12f)
        playIcon.setPadding(pp, pp, pp, pp)
        playWrap.addView(playIcon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        playWrap.pressEffect(0.92f)
        playWrap.setOnClickListener { togglePlay() }
        transport.addView(playWrap, LinearLayout.LayoutParams(dp(44f), dp(44f)))

        timecode = kText("00:00.00 / 00:00.00", 12f, KColors.Text, bold = true, font = "monospace")
        val lpTc = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpTc.marginStart = dp(12f)
        transport.addView(timecode, lpTc)

        magnetBtn = KilatIconButton(this, KilatIcon.MAGNET, KColors.Accent, filled = false, sizeDp = 38f)
        magnetBtn.setOnClickListener {
            preview.snapEnabled = !preview.snapEnabled
            magnetBtn.setTint(if (preview.snapEnabled) KColors.Accent else KColors.Dim)
            toast(if (preview.snapEnabled) "Magnet snap: nyala" else "Magnet snap: mati", KilatIcon.MAGNET)
        }
        transport.addView(magnetBtn, LinearLayout.LayoutParams(dp(38f), dp(38f)))

        loopBtn = KilatIconButton(this, KilatIcon.LOOP, KColors.Dim, filled = false, sizeDp = 38f)
        loopBtn.setOnClickListener {
            loop = !loop
            loopBtn.setTint(if (loop) KColors.Accent else KColors.Dim)
            toast(if (loop) "Loop nyala" else "Loop mati", KilatIcon.LOOP)
        }
        transport.addView(loopBtn, LinearLayout.LayoutParams(dp(38f), dp(38f)))

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
                selectLayer(id, openPanel = true)
            }

            override fun onTimesChanged(commit: Boolean) {
                preview.invalidate()
                if (commit) {
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
        main.addView(timeline, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(172f)))

        // ---------- bottom toolbar ----------
        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.setPadding(dp(8f), dp(4f), dp(8f), dp(8f))
        bottom.setBackgroundColor(KColors.BgTop)
        val tools = listOf(
            Triple(KilatIcon.TEXT, "Teks") { addTextLayer() },
            Triple(KilatIcon.SHAPE, "Bentuk") { showShapePicker() },
            Triple(KilatIcon.IMAGE, "Gambar") { pickImage(null) },
            Triple(KilatIcon.SLIDERS, "Properti") { showPanel() },
            Triple(KilatIcon.EXPORT, "Export") { showExportDialog() }
        )
        for ((i, triple) in tools.withIndex()) {
            val (icon, label, action) = triple
            val item = toolItem(icon, label, accent = i == tools.size - 1) { action() }
            bottom.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        main.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(main, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // ---------- inspector panel ----------
        inspectorPanel = LinearLayout(this)
        inspectorPanel.orientation = LinearLayout.VERTICAL
        inspectorPanel.background = roundedTop(KColors.BgTop, kdpf(20f))
        inspectorPanel.elevation = kdpf(14f)
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
        val panelTitle = kText("Properti Layer", 13f, KColors.Text, bold = true)
        val lpTitle = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        panelHead.addView(panelTitle, lpTitle)
        val closeBtn = KilatIconButton(this, KilatIcon.CLOSE, KColors.Dim, filled = false, sizeDp = 34f)
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

            override fun onDelete(layer: Layer) {
                deleteLayer(layer)
            }

            override fun onDuplicate(layer: Layer) {
                duplicateLayer(layer)
            }

            override fun onApplyPreset(layer: Layer, preset: String) {
                val ok = Anim.applyPreset(layer, preset, playhead, project.duration)
                if (!ok) {
                    toast("Preset ini tidak cocok untuk layer ${layer.kind}", KilatIcon.INFO)
                    return
                }
                preview.invalidate()
                timeline.invalidate()
                showInspectorFor(layer)
                autosave()
                toast("Preset ${Anim.PRESET_LABELS[preset]} diterapkan", KilatIcon.WAND)
            }

            override fun onSplit(layer: Layer) {
                splitLayerAtPlayhead(layer)
            }

            override fun onReorder(layer: Layer, dir: Int) {
                reorderLayer(layer, dir)
            }

            override fun onClearKeys(layer: Layer) {
                Anim.clearAll(layer)
                preview.invalidate()
                timeline.invalidate()
                showInspectorFor(layer)
                autosave()
                toast("Semua keyframe dihapus", KilatIcon.RESET)
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
        playIcon.icon = if (playing) KilatIcon.PAUSE else KilatIcon.PLAY
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

    // =====================================================================
    // selection & layer ops
    // =====================================================================
    private fun selectLayer(id: String?, openPanel: Boolean) {
        selectedId = id
        preview.select(id)
        timeline.selectedId = id
        timeline.invalidate()
        if (inspectorPanel.visibility == View.VISIBLE || openPanel) {
            showInspectorFor(project.findLayer(id))
        }
        if (openPanel) showPanel()
    }

    private fun afterChange(selectId: String? = null, openPanel: Boolean = true) {
        if (selectId != null) selectedId = selectId
        preview.project = project
        preview.select(selectedId)
        timeline.project = project
        timeline.selectedId = selectedId
        timeline.invalidate()
        preview.invalidate()
        if (inspectorPanel.visibility == View.VISIBLE) showInspectorFor(project.findLayer(selectedId))
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
            start = playhead.coerceIn(0f, max(0f, p.duration - 0.5f))
            dur = min(4f, p.duration - start)
        }
        p.layers.add(l)
        afterChange(l.id)
        selectLayer(l.id, openPanel = true)
        toast("Teks ditambahkan", KilatIcon.TEXT)
    }

    private fun showShapePicker() {
        val tints = listOf(KColors.Accent, KColors.Sky, KColors.Amber, KColors.Violet)
        val items = SHAPE_TYPES.mapIndexed { i, s ->
            KilatSheet.Item(SHAPE_LABELS[s] ?: s, icon = KilatIcon.SHAPE, iconTint = tints[i % tints.size])
        }
        KilatSheet.showList(this, "Tambah bentuk", items) { which ->
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
                fill = KColors.Accent
                x = p.width / 2f
                y = p.height / 2f
                start = playhead.coerceIn(0f, max(0f, p.duration - 0.5f))
                dur = min(4f, p.duration - start)
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
        toast("Layer dihapus", KilatIcon.TRASH)
    }

    private fun duplicateLayer(layer: Layer) {
        val copy = layer.duplicate()
        val idx = project.layers.indexOf(layer)
        project.layers.add(idx + 1, copy)
        selectedId = copy.id
        afterChange(selectedId)
        toast("Layer diduplikat", KilatIcon.COPY)
    }

    private fun splitLayerAtPlayhead(layer: Layer) {
        val second = Anim.splitAt(layer, playhead)
        if (second == null) {
            toast("Geser playhead ke tengah layer dulu buat memisah", KilatIcon.SCISSORS)
            return
        }
        val idx = project.layers.indexOf(layer)
        project.layers.add(idx + 1, second)
        selectedId = second.id
        afterChange(selectedId)
        toast("Layer dipisah di ${Format.time(playhead)}", KilatIcon.SCISSORS)
    }

    private fun reorderLayer(layer: Layer, dir: Int) {
        val idx = project.layers.indexOf(layer)
        if (idx < 0) return
        val nidx = idx + dir
        if (nidx !in project.layers.indices) {
            toast(if (dir > 0) "Layer sudah paling atas" else "Layer sudah paling bawah", KilatIcon.INFO)
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
        undoBtn.setTint(if (undoStack.isNotEmpty()) KColors.Text else KColors.Faint)
        redoBtn.setTint(if (redoStack.isNotEmpty()) KColors.Text else KColors.Faint)
    }

    private fun undo() {
        if (undoStack.isEmpty()) {
            toast("Belum ada yang bisa di-undo", KilatIcon.INFO)
            return
        }
        redoStack.add(lastSnapshot)
        val snap = undoStack.removeAt(undoStack.lastIndex)
        lastSnapshot = snap
        restoreSnapshot(snap)
        updateUndoButtons()
        toast("Undo", KilatIcon.UNDO)
    }

    private fun redo() {
        if (redoStack.isEmpty()) {
            toast("Belum ada yang bisa di-redo", KilatIcon.INFO)
            return
        }
        undoStack.add(lastSnapshot)
        val snap = redoStack.removeAt(redoStack.lastIndex)
        lastSnapshot = snap
        restoreSnapshot(snap)
        updateUndoButtons()
        toast("Redo", KilatIcon.REDO)
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
            KilatSheet.show(
                this, "Musik latar",
                kText(
                    "Tambahkan musik dari galeri. Musik akan ikut ter-export ke MP4 (volume di video mengikuti file asli).",
                    12.5f, KColors.Muted
                ),
                positiveText = "Pilih musik", positiveIcon = KilatIcon.MUSIC,
                onPositive = { pickAudio() },
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
        mTile.background = roundedRect(KColors.CardHi, kdpf(10f))
        val mIv = IconView(this, KilatIcon.MUSIC, KColors.Accent)
        val mp = dp(8f)
        mIv.setPadding(mp, mp, mp, mp)
        mTile.addView(mIv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val lpMT = LinearLayout.LayoutParams(dp(38f), dp(38f))
        lpMT.rightMargin = dp(11f)
        headRow.addView(mTile, lpMT)
        val mCol = LinearLayout(this)
        mCol.orientation = LinearLayout.VERTICAL
        mCol.addView(kText(m.name, 13f, KColors.Text, bold = true))
        mCol.addView(kText("Durasi file: ${Format.dec(AudioUtil.durationSeconds(m.path))}s", 11f, KColors.Dim))
        headRow.addView(mCol)
        wrap.addView(headRow)

        val volLabel = kText("Volume preview: ${(m.volume * 100).toInt()}%", 11.5f, KColors.Muted)
        val lpV = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpV.topMargin = dp(14f)
        wrap.addView(volLabel, lpV)
        val volSlider = KilatSlider(this)
        volSlider.max = 100
        volSlider.progress = (m.volume * 100).toInt().coerceIn(0, 100)
        volSlider.onProgress = { progress ->
            volLabel.text = "Volume preview: $progress%"
            project.music?.volume = progress / 100f
            musicPlayer.sync(project.music, playhead, playing)
        }
        volSlider.onReleased = { autosave() }
        wrap.addView(volSlider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30f)))

        val posLabel = kText("Mulai: ${Format.dec(m.start)}s | Durasi: ${Format.dec(m.dur)}s", 11.5f, KColors.Muted)
        val lpP = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpP.topMargin = dp(8f)
        wrap.addView(posLabel, lpP)
        val startSlider = KilatSlider(this)
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

        KilatSheet.show(
            this, "Musik latar", wrap,
            positiveText = "Tutup", positiveIcon = KilatIcon.CHECK,
            onPositive = { autosave() },
            neutralText = "Ganti",
            onNeutral = { pickAudio() },
            negativeText = "Hapus",
            onNegative = {
                project.music = null
                musicPlayer.release()
                timeline.invalidate()
                autosave()
                toast("Musik dihapus", KilatIcon.TRASH)
            }
        )
    }

    private fun pickAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_AUDIO)
        } catch (e: Throwable) {
            toast("Tidak bisa membuka file audio", KilatIcon.INFO)
        }
    }

    private fun importAudioFrom(uri: Uri) {
        toast("Mengimpor musik...", KilatIcon.MUSIC)
        Thread {
            val ctx = applicationContext
            val name = queryDisplayName(uri) ?: "musik"
            val path = ProjectStore.importAudio(ctx, uri, name)
            if (path == null) {
                runOnUiThread { if (!isFinishing) toast("Gagal mengimpor musik", KilatIcon.INFO) }
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
                toast(if (wave != null) "Musik ditambahkan" else "Musik ditambahkan (waveform tidak tersedia)", KilatIcon.MUSIC)
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

    private fun pickImage(replace: Layer?) {
        pendingImageLayer = replace
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_IMAGE)
        } catch (e: Throwable) {
            toast("Tidak bisa membuka galeri", KilatIcon.INFO)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == REQ_AUDIO) {
            val auri: Uri = data?.data ?: return
            importAudioFrom(auri)
            return
        }
        if (requestCode != REQ_IMAGE) return
        val uri: Uri = data?.data ?: return
        toast("Mengimpor gambar...", KilatIcon.IMAGE)
        Thread {
            val ctx = applicationContext
            val path = ProjectStore.importImage(ctx, uri)
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (path != null) android.graphics.BitmapFactory.decodeFile(path, bounds)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (path == null || bounds.outWidth <= 0) {
                    toast("Gagal membaca gambar", KilatIcon.INFO)
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
                    toast("Gambar diganti", KilatIcon.IMAGE)
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
                        start = playhead.coerceIn(0f, max(0f, p.duration - 0.5f))
                        dur = min(5f, p.duration - start)
                    }
                    p.layers.add(l)
                    afterChange(l.id)
                    selectLayer(l.id, openPanel = true)
                    toast("Gambar ditambahkan", KilatIcon.IMAGE)
                }
                pendingImageLayer = null
            }
        }.start()
    }

    // =====================================================================
    // export
    // =====================================================================
    private fun showExportDialog() {
        val items = listOf(
            KilatSheet.Item("720p", "Cepat dirender, ukuran file kecil", KilatIcon.FILM, KColors.Sky),
            KilatSheet.Item("1080p", "Full HD, render lebih lama", KilatIcon.FILM, KColors.Violet)
        )
        KilatSheet.showList(this, "Export MP4", items) { which ->
            runExport(if (which == 0) 1280 else 1920)
        }
    }

    private fun runExport(longSide: Int) {
        if (playing) stopPlay()
        musicPlayer.sync(project.music, playhead, false)
        val p = project
        val handle = KilatSheet.showProgress(this, "Mengekspor video", "Menyiapkan encoder...")

        Thread {
            val ctx = applicationContext
            fun progressFn(f: Float) {
                runOnUiThread { if (!isFinishing) handle.setProgress(f, "Merender frame video...") }
            }
            var result = Exporter.exportVideo(ctx, p, longSide, 30) { f -> progressFn(f) }
            // fallback otomatis: beberapa HP tidak mendukung encode 1080p tertentu
            if (result.error != null && longSide > 1280) {
                runOnUiThread { if (!isFinishing) handle.setProgress(0f, "Resolusi tidak didukung, coba 720p...") }
                result = Exporter.exportVideo(ctx, p, 1280, 30) { f -> progressFn(f) }
            }
            val res = result
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                handle.dismiss()
                if (res.error != null || res.uri == null) {
                    KilatSheet.showMessage(
                        this, "Export gagal",
                        "${res.error ?: "URI tidak valid"}\n\nCoba resolusi 720p, atau pastikan penyimpanan cukup.",
                        icon = KilatIcon.INFO
                    )
                } else {
                    val resLabel = if (longSide <= 1280) "720p" else "1080p"
                    KilatSheet.show(
                        this, "Export selesai",
                        kText(
                            "Video tersimpan di Movies/KilatMotion.\n\n$resLabel | 30fps | ${Format.dec(p.duration)} detik. Bagikan sekarang?",
                            12.5f, KColors.Muted
                        ),
                        positiveText = "Bagikan", positiveIcon = KilatIcon.SHARE,
                        onPositive = { shareUri(res.uri, "video/mp4") },
                        negativeText = "Oke"
                    )
                    toast("Video tersimpan ke galeri", KilatIcon.FILM)
                }
            }
        }.start()
    }

    private fun exportPng() {
        val p = project
        toast("Menyimpan frame PNG...", KilatIcon.IMAGE)
        Thread {
            val uri = Exporter.exportFramePng(applicationContext, p, playhead)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (uri == null) toast("Gagal export PNG", KilatIcon.INFO)
                else {
                    toast("Frame PNG tersimpan di Pictures/KilatMotion", KilatIcon.IMAGE)
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
            toast("Tidak ada aplikasi untuk membagikan", KilatIcon.INFO)
        }
    }

    // =====================================================================
    // menu & dialogs
    // =====================================================================
    private fun showMenu() {
        val items = listOf(
            KilatSheet.Item("Musik latar", "Musik ikut ter-export ke MP4", KilatIcon.MUSIC, KColors.Accent),
            KilatSheet.Item("Pengaturan project", "Rasio, durasi, warna latar", KilatIcon.SETTINGS, KColors.Sky),
            KilatSheet.Item("Simpan sekarang", "Tersimpan juga otomatis tiap perubahan", KilatIcon.SAVE, KColors.Green),
            KilatSheet.Item("Muat demo", "Ganti isi project ini dengan demo", KilatIcon.BOLT, KColors.Amber),
            KilatSheet.Item("Project baru (kosong)", "Isi project ini dikosongkan", KilatIcon.PLUS, KColors.Violet),
            KilatSheet.Item("Export frame PNG", "Simpan frame saat ini sebagai gambar", KilatIcon.IMAGE, KColors.Rose),
            KilatSheet.Item("Panduan", "Cara pakai editor", KilatIcon.BOOK, KColors.Sky),
            KilatSheet.Item("Tentang", "Versi dan info aplikasi", KilatIcon.INFO, KColors.Muted),
            KilatSheet.Item("Kembali ke Beranda", "Project tersimpan otomatis", KilatIcon.HOME, KColors.Accent)
        )
        KilatSheet.showList(this, "Menu project", items) { which ->
            when (which) {
                0 -> showMusicDialog()
                1 -> showProjectSettings()
                2 -> {
                    ProjectLibrary.save(applicationContext, project)
                    toast("Project disimpan", KilatIcon.SAVE)
                }
                3 -> loadDemo()
                4 -> newProject()
                5 -> exportPng()
                6 -> showGuide()
                7 -> showAbout()
                8 -> finish()
            }
        }
    }

    private fun showProjectSettings() {
        val ratios = Templates.RATIOS
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4f), 0, dp(2f))
        }
        wrap.addView(kText("Rasio kanvas", 11.5f, KColors.Muted))
        val ratioDrop = KilatDropdown(this)
        ratioDrop.sheetTitle = "Rasio kanvas"
        ratioDrop.setItems(
            ratios.map { "${it.label}  (${it.w} x ${it.h})" },
            ratios.indexOfFirst { it.w == project.width && it.h == project.height }.coerceAtLeast(0)
        )
        val lpR = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpR.topMargin = dp(7f)
        wrap.addView(ratioDrop, lpR)

        val durLabel = kText("Durasi (detik, 2 - 60)", 11.5f, KColors.Muted)
        val lpD = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpD.topMargin = dp(14f)
        wrap.addView(durLabel, lpD)
        val durEdit = kEditText(project.duration.toInt().toString(), hint = "8", numeric = true)
        val lpDE = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpDE.topMargin = dp(7f)
        wrap.addView(durEdit, lpDE)

        val bgLabel = kText("Warna latar", 11.5f, KColors.Muted)
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
                dd.setStroke(dp(2f), if (bgColors[i] == newBg) KColors.Accent else 0x33FFFFFF)
            }
        }
        for (c in bgColors) {
            val v = View(this)
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(c)
            d.setStroke(dp(2f), if (c == newBg) KColors.Accent else 0x33FFFFFF)
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

        KilatSheet.show(
            this, "Pengaturan project", wrap,
            positiveText = "Terapkan", positiveIcon = KilatIcon.CHECK,
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
        val nd = dur.coerceIn(2f, 60f)
        p.duration = nd
        for (l in p.layers) {
            if (l.start > nd - 0.2f) l.start = max(0f, nd - 1f)
            if (l.start + l.dur > nd) l.dur = max(0.2f, nd - l.start)
        }
        if (playhead > nd) setPlayhead(nd)
        updateBadge()
        afterChange(selectedId)
        showInspectorFor(project.findLayer(selectedId))
        toast("Project diubah ke ${w} x ${h}", KilatIcon.RATIO)
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
        toast("Demo dimuat", KilatIcon.BOLT)
    }

    private fun newProject() {
        KilatSheet.showConfirm(
            this, "Project baru?",
            "Isi project yang sedang dibuka akan dikosongkan. Project lain di pustaka tidak ikut terhapus.",
            positiveText = "Kosongkan", danger = true,
            onPositive = {
                replaceProjectContent(Templates.newProject(project.width, project.height))
                toast("Project kosong dibuat", KilatIcon.PLUS)
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
            - Export MP4 ke galeri (Movies/KilatMotion)

            TIPS
            - Posisikan playhead, ubah nilai, lalu ketuk wajik untuk bikin keyframe di titik itu
            - Wajik terisi penuh artinya ada keyframe tepat di playhead; ketuk lagi untuk hapus
            - Belum ada pinch zoom, pakai handle pojok untuk resize
            - Efek Blur pada gambar memakai downscale supaya ringan
            - Preview terasa berat? turunkan Glow atau kurangi jumlah layer
        """.trimIndent()
        KilatSheet.showMessage(this, "Panduan KILAT Motion", msg, icon = KilatIcon.BOOK)
    }

    private fun showAbout() {
        KilatSheet.showMessage(
            this, "KILAT Motion",
            "Motion graphics editor native Android.\n\nVersi 1.2.0\nKotlin | Canvas | MediaCodec\nTanpa iklan, tanpa library eksternal.\nSemua ikon digambar manual pakai Canvas.",
            icon = KilatIcon.BOLT
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

    private fun toast(msg: String, icon: KilatIcon = KilatIcon.CHECK) {
        KilatToast.show(this, msg, icon)
    }

    companion object {
        const val EXTRA_PROJECT_ID = "extra_project_id"
        const val EXTRA_TEMPLATE_ID = "extra_template_id"
        const val EXTRA_NEW_W = "extra_new_w"
        const val EXTRA_NEW_H = "extra_new_h"
        const val EXTRA_DEMO = "extra_demo"

        private const val REQ_IMAGE = 1001
        private const val REQ_AUDIO = 1002
    }
}
