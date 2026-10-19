package com.kilat.motion

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : Activity() {

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
    private lateinit var playBtn: TextView
    private lateinit var loopBtn: TextView
    private lateinit var badge: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val musicPlayer by lazy { MusicPlayer(applicationContext) }
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Float): Int = (v * density).roundToInt()

    private val cBg = 0xFF0A0B0F.toInt()
    private val cPanel = 0xFF111319.toInt()
    private val cPanel2 = 0xFF151821.toInt()
    private val cLine = 0xFF222837.toInt()
    private val cText = 0xFFEEF2F8.toInt()
    private val cMuted = 0xFF98A3B8.toInt()
    private val cDim = 0xFF687389.toInt()
    private val cAccent = 0xFF2EE6C0.toInt()

    // =====================================================================
    // lifecycle
    // =====================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF0D0F15.toInt()
        window.navigationBarColor = cBg

        project = ProjectStore.load(this) ?: Demo.build()
        buildUi()
        setPlayhead(0f)
        updateTimecode()
        showInspectorFor(null)
    }

    override fun onPause() {
        playing = false
        musicPlayer.sync(project.music, playhead, false)
        updatePlayButton()
        ProjectStore.save(applicationContext, project)
        super.onPause()
    }

    override fun onDestroy() {
        musicPlayer.release()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (inspectorPanel.visibility == View.VISIBLE) {
            hidePanel()
            return
        }
        super.onBackPressed()
    }

    // =====================================================================
    // UI
    // =====================================================================
    private fun rounded(fill: Int, radius: Float = dp(10f).toFloat(), stroke: Int? = null): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = radius
        d.setColor(fill)
        if (stroke != null) d.setStroke(dp(1f), stroke)
        return d
    }

    private fun textView(text: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(color)
        tv.textSize = size
        if (bold) tv.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        return tv
    }

    private fun toolButton(label: String, accent: Boolean = false, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.textSize = 11f
        tv.gravity = Gravity.CENTER
        tv.setTextColor(if (accent) 0xFF031A15.toInt() else cText)
        tv.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        tv.background = rounded(if (accent) cAccent else 0xFF1B1F2B.toInt(), dp(9f).toFloat(), if (accent) null else cLine)
        tv.setPadding(dp(4f), dp(9f), dp(4f), dp(9f))
        tv.isClickable = true
        tv.setOnClickListener { onClick() }
        return tv
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        root.setBackgroundColor(cBg)

        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        main.fitsSystemWindows = true

        // ---------- top bar ----------
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(14f), dp(8f), dp(8f), dp(6f))
        top.setBackgroundColor(0xFF0D0F15.toInt())

        val logo = textView("⚡", 18f, cAccent, true)
        top.addView(logo, LinearLayout.LayoutParams(dp(24f), ViewGroup.LayoutParams.WRAP_CONTENT))

        val brand = LinearLayout(this)
        brand.orientation = LinearLayout.VERTICAL
        brand.addView(textView("KILAT", 13f, cText, true))
        val sub = textView("MOTION", 7.5f, cDim, true)
        sub.letterSpacing = 0.32f
        brand.addView(sub)
        val lpBrand = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpBrand.marginStart = dp(4f)
        top.addView(brand, lpBrand)

        nameEdit = EditText(this)
        nameEdit.setText(project.name)
        nameEdit.setTextColor(cMuted)
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
        lpName.marginStart = dp(6f)
        top.addView(nameEdit, lpName)

        val menuBtn = textView("⋮", 20f, cMuted, true)
        menuBtn.gravity = Gravity.CENTER
        menuBtn.setPadding(dp(10f), dp(2f), dp(10f), dp(6f))
        menuBtn.isClickable = true
        menuBtn.setOnClickListener { showMenu() }
        top.addView(menuBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34f)))

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

        badge = textView("", 9.5f, cDim, true)
        badge.setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
        badge.background = rounded(0xCC0A0C11.toInt(), dp(6f).toFloat(), 0x22FFFFFF)
        val lpBadge = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpBadge.gravity = Gravity.TOP or Gravity.START
        lpBadge.leftMargin = dp(10f)
        lpBadge.topMargin = dp(8f)
        previewWrap.addView(badge, lpBadge)
        badge.text = "${project.width} × ${project.height}  ·  ${project.fps}fps"

        main.addView(previewWrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---------- transport ----------
        val transport = LinearLayout(this)
        transport.orientation = LinearLayout.HORIZONTAL
        transport.gravity = Gravity.CENTER_VERTICAL
        transport.setPadding(dp(14f), dp(4f), dp(14f), dp(6f))

        playBtn = textView("▶", 16f, 0xFF0A0B0F.toInt(), true)
        playBtn.gravity = Gravity.CENTER
        playBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        playBtn.isClickable = true
        playBtn.setOnClickListener { togglePlay() }
        transport.addView(playBtn, LinearLayout.LayoutParams(dp(42f), dp(42f)))

        timecode = textView("00:00.00 / 00:00.00", 12f, cText, true)
        timecode.typeface = Typeface.create("monospace", Typeface.BOLD)
        val lpTc = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpTc.marginStart = dp(12f)
        transport.addView(timecode, lpTc)

        loopBtn = textView("⟳", 18f, cDim, true)
        loopBtn.gravity = Gravity.CENTER
        loopBtn.setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
        loopBtn.isClickable = true
        loopBtn.setOnClickListener {
            loop = !loop
            loopBtn.setTextColor(if (loop) cAccent else cDim)
            toast(if (loop) "Loop: ON" else "Loop: OFF")
        }
        transport.addView(loopBtn, LinearLayout.LayoutParams(dp(36f), dp(36f)))

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
        bottom.setPadding(dp(10f), dp(6f), dp(10f), dp(10f))
        bottom.setBackgroundColor(0xFF0D0F15.toInt())
        val tools = listOf(
            "＋ Teks" to { addTextLayer() },
            "◆ Bentuk" to { showShapePicker() },
            "🖼 Gambar" to { pickImage(null) },
            "⚙ Properti" to { showPanel() },
            "⤓ Export" to { showExportDialog() }
        )
        for ((i, pair) in tools.withIndex()) {
            val btn = toolButton(pair.first, accent = i == tools.size - 1) { pair.second() }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.marginStart = dp(6f)
            bottom.addView(btn, lp)
        }
        main.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(main, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // ---------- inspector panel ----------
        inspectorPanel = LinearLayout(this)
        inspectorPanel.orientation = LinearLayout.VERTICAL
        inspectorPanel.background = GradientDrawable().apply {
            cornerRadii = floatArrayOf(dp(18f).toFloat(), dp(18f).toFloat(), dp(18f).toFloat(), dp(18f).toFloat(), 0f, 0f, 0f, 0f)
            setColor(0xFF0D0F15.toInt())
            setStroke(dp(1f), cLine)
        }
        inspectorPanel.visibility = View.GONE

        val panelHead = LinearLayout(this)
        panelHead.orientation = LinearLayout.HORIZONTAL
        panelHead.gravity = Gravity.CENTER_VERTICAL
        panelHead.setPadding(dp(16f), dp(10f), dp(10f), dp(4f))
        val handle = TextView(this)
        handle.text = "▬"
        handle.setTextColor(0xFF2C3346.toInt())
        handle.textSize = 12f
        handle.gravity = Gravity.CENTER

        val panelTitle = textView("Properti Layer", 13f, cText, true)
        val lpTitle = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpTitle.marginStart = dp(6f)
        panelHead.addView(panelTitle, lpTitle)
        val closeBtn = textView("✕", 15f, cMuted, true)
        closeBtn.gravity = Gravity.CENTER
        closeBtn.setPadding(dp(10f), dp(2f), dp(10f), dp(2f))
        closeBtn.isClickable = true
        closeBtn.setOnClickListener { hidePanel() }
        panelHead.addView(closeBtn, LinearLayout.LayoutParams(dp(36f), dp(36f)))
        inspectorPanel.addView(panelHead, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // strip handle (visual only)
        val strip = View(this)
        strip.setBackgroundColor(0xFF2C3346.toInt())
        val lpStrip = LinearLayout.LayoutParams(dp(40f), dp(3f))
        lpStrip.gravity = Gravity.CENTER_HORIZONTAL
        lpStrip.topMargin = -dp(4f)
        inspectorPanel.addView(strip, lpStrip)

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
                    toast("Preset ini tidak cocok untuk layer ${layer.kind}")
                    return
                }
                preview.invalidate()
                timeline.invalidate()
                showInspectorFor(layer)
                autosave()
                toast("Preset ${Anim.PRESET_LABELS[preset]} diterapkan")
            }

            override fun onClearKeys(layer: Layer) {
                Anim.clearAll(layer)
                preview.invalidate()
                timeline.invalidate()
                showInspectorFor(layer)
                autosave()
                toast("Semua keyframe dihapus")
            }
        }
        val scroll = ScrollView(this)
        scroll.addView(inspector, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        inspectorPanel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val lpPanel = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpPanel.gravity = Gravity.BOTTOM
        root.addView(inspectorPanel, lpPanel)

        setContentView(root)
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
        ProjectStore.save(applicationContext, project)
    }

    private fun updatePlayButton() {
        playBtn.text = if (playing) "❚❚" else "▶"
        playBtn.textSize = if (playing) 13f else 16f
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
        toast("Teks ditambahkan")
    }

    private fun showShapePicker() {
        val labels = SHAPE_TYPES.map { SHAPE_LABELS[it] ?: it }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Tambah bentuk")
            .setItems(labels) { _, which ->
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
                    fill = cAccent
                    x = p.width / 2f
                    y = p.height / 2f
                    start = playhead.coerceIn(0f, max(0f, p.duration - 0.5f))
                    dur = min(4f, p.duration - start)
                }
                p.layers.add(l)
                afterChange(l.id)
                selectLayer(l.id, openPanel = true)
            }
            .show()
    }

    private fun deleteLayer(layer: Layer) {
        val idx = project.layers.indexOf(layer)
        if (idx < 0) return
        project.layers.removeAt(idx)
        val next = project.layers.getOrNull(max(0, idx - 1))
        selectedId = next?.id
        afterChange(selectedId)
        if (inspectorPanel.visibility == View.VISIBLE) showInspectorFor(next)
        toast("Layer dihapus")
    }

    private fun duplicateLayer(layer: Layer) {
        val copy = layer.duplicate()
        val idx = project.layers.indexOf(layer)
        project.layers.add(idx + 1, copy)
        selectedId = copy.id
        afterChange(selectedId)
        toast("Layer diduplikat")
    }

    // =====================================================================
    // musik latar
    // =====================================================================
    private fun showMusicDialog() {
        val m = project.music
        if (m == null) {
            AlertDialog.Builder(this)
                .setTitle("Musik latar")
                .setMessage("Tambahkan musik dari galeri. Musik akan ikut ter-export ke MP4 (volume di video mengikuti file asli).")
                .setPositiveButton("Pilih musik") { _, _ -> pickAudio() }
                .setNegativeButton("Batal", null)
                .show()
            return
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(10f), dp(20f), dp(2f))
        }
        wrap.addView(textView("♫ ${m.name}", 13f, cText, true))
        wrap.addView(textView("Durasi file: ${"%.1f".format(AudioUtil.durationSeconds(m.path))}s", 11f, cDim))

        var vol = m.volume
        val volLabel = textView("Volume preview: ${(vol * 100).toInt()}%", 11.5f, cMuted)
        val lpV = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpV.topMargin = dp(12f)
        wrap.addView(volLabel, lpV)
        val seek = SeekBar(this)
        seek.max = 100
        seek.progress = (vol * 100).toInt().coerceIn(0, 100)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                vol = progress / 100f
                volLabel.text = "Volume preview: $progress%"
                project.music?.volume = vol
                musicPlayer.sync(project.music, playhead, playing)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { autosave() }
        })
        wrap.addView(seek)

        val posLabel = textView("Mulai: ${"%.1f".format(m.start)}s · Durasi: ${"%.1f".format(m.dur)}s", 11.5f, cMuted)
        val lpP = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpP.topMargin = dp(8f)
        wrap.addView(posLabel, lpP)
        val startSeek = SeekBar(this)
        val maxStart = max(1, project.duration.toInt() - 1)
        startSeek.max = maxStart
        startSeek.progress = m.start.toInt().coerceIn(0, maxStart)
        startSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val mm = project.music ?: return
                mm.start = progress.toFloat()
                if (mm.start + mm.dur > project.duration) mm.dur = max(0.5f, project.duration - mm.start)
                posLabel.text = "Mulai: ${"%.1f".format(mm.start)}s · Durasi: ${"%.1f".format(mm.dur)}s"
                timeline.invalidate()
                musicPlayer.sync(project.music, playhead, playing)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { autosave() }
        })
        wrap.addView(startSeek)

        AlertDialog.Builder(this)
            .setTitle("Musik latar")
            .setView(ScrollView(this).apply { addView(wrap) })
            .setPositiveButton("Tutup") { _, _ -> autosave() }
            .setNeutralButton("Ganti") { _, _ -> pickAudio() }
            .setNegativeButton("Hapus") { _, _ ->
                project.music = null
                musicPlayer.release()
                timeline.invalidate()
                autosave()
                toast("Musik dihapus")
            }
            .show()
    }

    private fun pickAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        try {
            startActivityForResult(intent, REQ_AUDIO)
        } catch (e: Throwable) {
            toast("Tidak bisa membuka file audio")
        }
    }

    private fun importAudioFrom(uri: Uri) {
        toast("Mengimpor musik…")
        Thread {
            val ctx = applicationContext
            val name = queryDisplayName(uri) ?: "musik"
            val path = ProjectStore.importAudio(ctx, uri, name)
            if (path == null) {
                runOnUiThread { toast("Gagal mengimpor musik") }
                return@Thread
            }
            val durSec = AudioUtil.durationSeconds(path)
            val wave = AudioUtil.decodeEnvelope(path)
            runOnUiThread {
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
                toast(if (wave != null) "Musik ditambahkan 🎵" else "Musik ditambahkan (waveform tidak tersedia)")
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
            startActivityForResult(intent, REQ_IMAGE)
        } catch (e: Throwable) {
            toast("Tidak bisa membuka galeri")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == REQ_AUDIO) {
            val auri: Uri = data?.data ?: return
            importAudioFrom(auri)
            return
        }
        if (requestCode != REQ_IMAGE) return
        val uri: Uri = data?.data ?: return
        toast("Mengimpor gambar…")
        Thread {
            val ctx = applicationContext
            val path = ProjectStore.importImage(ctx, uri)
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (path != null) android.graphics.BitmapFactory.decodeFile(path, bounds)
            runOnUiThread {
                if (path == null || bounds.outWidth <= 0) {
                    toast("Gagal membaca gambar")
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
                    toast("Gambar diganti")
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
                    toast("Gambar ditambahkan")
                }
                pendingImageLayer = null
            }
        }.start()
    }

    // =====================================================================
    // export
    // =====================================================================
    private fun showExportDialog() {
        val opts = arrayOf("720p — cepat & kecil", "1080p — HD, lebih lama")
        AlertDialog.Builder(this)
            .setTitle("Export MP4")
            .setItems(opts) { _, which ->
                val longSide = if (which == 0) 1280 else 1920
                runExport(longSide)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun runExport(longSide: Int) {
        if (playing) stopPlay()
        musicPlayer.sync(project.music, playhead, false)
        val p = project
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        val status = textView("Menyiapkan encoder…", 12f, cMuted)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(12f), dp(20f), dp(4f))
            addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10f)
            addView(bar, lp)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Mengekspor video…")
            .setView(wrap)
            .setCancelable(false)
            .create()
        dialog.show()

        Thread {
            val ctx = applicationContext
            fun progressFn(f: Float) {
                runOnUiThread {
                    val pct = (f * 100).roundToInt()
                    bar.progress = pct
                    status.text = "Merender $pct%"
                }
            }
            var result = Exporter.exportVideo(ctx, p, longSide, 30) { f -> progressFn(f) }
            // fallback otomatis: beberapa HP tidak mendukung encode 1080p tertentu
            if (result.error != null && longSide > 1280) {
                runOnUiThread { status.text = "Resolusi tidak didukung, coba 720p…" }
                result = Exporter.exportVideo(ctx, p, 1280, 30) { f -> progressFn(f) }
            }
            runOnUiThread {
                dialog.dismiss()
                if (result.error != null || result.uri == null) {
                    AlertDialog.Builder(this)
                        .setTitle("Export gagal")
                        .setMessage("${result.error ?: "URI tidak valid"}\n\nCoba resolusi 720p, atau pastikan penyimpanan cukup.")
                        .setPositiveButton("Oke", null)
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Export selesai 🎉")
                        .setMessage("Video tersimpan di:\nMovies/KilatMotion\n\n${longSide}p · 30fps · ${"%.1f".format(p.duration)} detik")
                        .setPositiveButton("Bagikan") { _, _ -> shareUri(result.uri, "video/mp4") }
                        .setNegativeButton("Oke", null)
                        .show()
                    toast("Video tersimpan ke galeri")
                }
            }
        }.start()
    }

    private fun exportPng() {
        val p = project
        toast("Menyimpan frame PNG…")
        Thread {
            val uri = Exporter.exportFramePng(applicationContext, p, playhead)
            runOnUiThread {
                if (uri == null) toast("Gagal export PNG")
                else {
                    toast("Frame PNG tersimpan di Pictures/KilatMotion")
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
            toast("Tidak ada aplikasi untuk membagikan")
        }
    }

    // =====================================================================
    // menu & dialogs
    // =====================================================================
    private fun showMenu() {
        val items = arrayOf(
            "🎵 Musik latar",
            "⚙ Pengaturan project",
            "💾 Simpan sekarang",
            "⚡ Muat demo",
            "🆕 Project baru",
            "🖼 Export frame PNG",
            "📖 Panduan",
            "ℹ Tentang"
        )
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showMusicDialog()
                    1 -> showProjectSettings()
                    2 -> { ProjectStore.save(applicationContext, project); toast("Project disimpan") }
                    3 -> { loadDemo() }
                    4 -> { newProject() }
                    5 -> exportPng()
                    6 -> showGuide()
                    7 -> showAbout()
                }
            }
            .show()
    }

    private fun showProjectSettings() {
        val ratios = listOf(
            Triple("9:16 (vertikal)", 1080, 1920),
            Triple("16:9 (landscape)", 1920, 1080),
            Triple("1:1 (kotak)", 1080, 1080),
            Triple("4:5 (feed)", 1080, 1350),
            Triple("3:4", 1080, 1440)
        )
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(12f), dp(20f), dp(4f))
        }
        val ratioLabel = textView("Rasio kanvas", 11.5f, cMuted)
        wrap.addView(ratioLabel)
        val ratioSpinner = Spinner(this)
        ratioSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ratios.map { it.first })
        val curIdx = ratios.indexOfFirst { it.second == project.width && it.third == project.height }.coerceAtLeast(0)
        ratioSpinner.setSelection(curIdx)
        val lpR = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpR.topMargin = dp(6f)
        wrap.addView(ratioSpinner, lpR)

        val durLabel = textView("Durasi (detik, 2–60)", 11.5f, cMuted)
        val lpD = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpD.topMargin = dp(14f)
        wrap.addView(durLabel, lpD)
        val durEdit = EditText(this)
        durEdit.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        durEdit.setText(project.duration.toInt().toString())
        durEdit.setTextColor(cText)
        wrap.addView(durEdit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val bgLabel = textView("Warna latar", 11.5f, cMuted)
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
        for (c in bgColors) {
            val v = View(this)
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(c)
            d.setStroke(dp(2f), if (c == newBg) cAccent else 0x33FFFFFF)
            v.background = d
            val lp = LinearLayout.LayoutParams(dp(28f), dp(28f))
            lp.rightMargin = dp(6f)
            lp.topMargin = dp(6f)
            swRow.addView(v, lp)
            v.setOnClickListener {
                newBg = c
                for (i in 0 until swRow.childCount) {
                    val child = swRow.getChildAt(i)
                    val dd = child.background as GradientDrawable
                    dd.setStroke(dp(2f), if (bgColors[i] == newBg) cAccent else 0x33FFFFFF)
                }
            }
        }
        wrap.addView(swRow)

        val scroll = ScrollView(this)
        scroll.addView(wrap)

        AlertDialog.Builder(this)
            .setTitle("Pengaturan project")
            .setView(scroll)
            .setPositiveButton("Terapkan") { _, _ ->
                val (w, h) = ratios[ratioSpinner.selectedItemPosition].let { Pair(it.second, it.third) }
                applyProjectSettings(w, h, durEdit.text.toString().toFloatOrNull() ?: project.duration, newBg)
            }
            .setNegativeButton("Batal", null)
            .show()
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
                for (ch in l.channels.values) {
                    for (k in ch.keys) {
                        // channel x/y perlu skala sumbu masing-masing
                    }
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
        badge.text = "${p.width} × ${p.height}  ·  ${p.fps}fps"
        afterChange(selectedId)
        showInspectorFor(project.findLayer(selectedId))
        toast("Project diubah ke ${w}×${h}")
    }

    private fun loadDemo() {
        musicPlayer.release()
        project = Demo.build()
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
        badge.text = "${project.width} × ${project.height}  ·  ${project.fps}fps"
        nameEdit.setText(project.name)
        afterChange(null, openPanel = false)
        toast("Demo dimuat ⚡")
    }

    private fun newProject() {
        AlertDialog.Builder(this)
            .setTitle("Project baru?")
            .setMessage("Layer yang sekarang akan diganti. Project tersimpan otomatis, tapi belum ada riwayat versi.")
            .setPositiveButton("Buat baru") { _, _ ->
                musicPlayer.release()
                project = Demo.newProject()
                selectedId = null
                playhead = 0f
                preview.project = project
                timeline.project = project
                preview.select(null)
                timeline.selectedId = null
                timeline.invalidate()
                preview.invalidate()
                setPlayhead(0f)
                badge.text = "${project.width} × ${project.height}  ·  ${project.fps}fps"
                nameEdit.setText(project.name)
                afterChange(null, openPanel = false)
                toast("Project baru dibuat")
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showGuide() {
        val msg = """
            ALUR CEPAT
            • Tambah layer: Teks / Bentuk / Gambar (tombol bawah)
            • Pindah layer: drag di kanvas
            • Resize: tarik handle pojok
            • Rotasi: tarik knob di atas layer
            • Timeline: drag bar = geser waktu, ujung kanan = durasi
            • Keyframe: tab Properti → tombol ◆ per properti
            • Preset: Fade, Slide, Zoom, Spin, Bounce, Typing, Ken Burns, Glitch
            • Export MP4 ke galeri (Movies/KilatMotion)

            TIPS
            • Ketuk ◆ saat playhead di posisi tertentu untuk bikin keyframe
            • Peluk/regangkan? — belum ada pinch, pakai handle pojok
            • Efek Blur pada gambar memakai downscale (ringan)
            • Ghosting? coba turunkan Glow / jumlah layer
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Panduan KILAT Motion")
            .setMessage(msg)
            .setPositiveButton("Oke", null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("KILAT Motion")
            .setMessage("Motion graphics editor native Android.\n\nVersi 1.0.0\nKotlin · Canvas · MediaCodec\nTanpa iklan · tanpa library eksternal.\n\nDibuat dengan ⚡")
            .setPositiveButton("Oke", null)
            .show()
    }

    // =====================================================================
    // misc
    // =====================================================================
    private fun autosave() {
        val token = ++autosaveToken
        handler.postDelayed({
            if (token == autosaveToken) ProjectStore.save(applicationContext, project)
        }, 800)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQ_IMAGE = 1001
        private const val REQ_AUDIO = 1002
    }
}
