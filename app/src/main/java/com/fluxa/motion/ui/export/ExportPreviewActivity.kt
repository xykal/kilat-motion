package com.fluxa.motion.ui.export

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import com.fluxa.motion.core.export.Exporter
import com.fluxa.motion.core.model.Format
import com.fluxa.motion.ui.components.IconView
import com.fluxa.motion.ui.components.FluxaButton
import com.fluxa.motion.ui.components.FluxaButtonStyle
import com.fluxa.motion.ui.components.FluxaIcon
import com.fluxa.motion.ui.components.FluxaIconButton
import com.fluxa.motion.ui.components.FluxaSheet
import com.fluxa.motion.ui.components.FluxaSlider
import com.fluxa.motion.ui.theme.FluxaColors
import com.fluxa.motion.ui.theme.dp
import com.fluxa.motion.ui.theme.dpf
import com.fluxa.motion.ui.theme.kText
import com.fluxa.motion.ui.theme.ovalBg
import com.fluxa.motion.ui.theme.pressEffect
import com.fluxa.motion.ui.theme.roundedRect
import java.io.File

/**
 * Preview hasil export: video bisa diputar, di-scrub, lalu user pilih
 * Simpan ke galeri, Bagikan, atau Buang. File render baru dipindah ke galeri saat disimpan.
 */
class ExportPreviewActivity : Activity() {

    companion object {
        const val EXTRA_PATH = "fluxa.export.path"
        const val EXTRA_LABEL = "fluxa.export.label"
    }

    private lateinit var file: File
    private lateinit var video: VideoView
    private lateinit var seek: FluxaSlider
    private lateinit var timeTv: TextView
    private lateinit var playIcon: IconView
    private lateinit var saveBtn: FluxaButton
    private val handler = Handler(Looper.getMainLooper())
    private var userSeeking = false
    private var saved = false
    private var busy = false

    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 120)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path == null || !File(path).exists()) {
            finish()
            return
        }
        file = File(path)
        window.statusBarColor = FluxaColors.Bg
        window.navigationBarColor = FluxaColors.Bg

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(FluxaColors.Bg)

        // header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(12f), dp(40f), dp(12f), dp(10f))
        val back = FluxaIconButton(this, FluxaIcon.CLOSE, FluxaColors.Muted, filled = false, sizeDp = 44f)
        back.setOnClickListener { finish() }
        header.addView(back, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(kText("Preview Export", 16f, FluxaColors.Text, bold = true))
        titles.addView(kText(intent.getStringExtra(EXTRA_LABEL) ?: "", 10.5f, FluxaColors.Muted))
        val lpT = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpT.marginStart = dp(8f)
        header.addView(titles, lpT)
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // area video
        val stage = FrameLayout(this)
        stage.setBackgroundColor(FluxaColors.Field)
        video = VideoView(this)
        stage.addView(video, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        val tapCatcher = View(this)
        tapCatcher.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_UP) togglePlay()
            true
        }
        stage.addView(tapCatcher, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        video.setVideoURI(Uri.fromFile(file))
        video.setOnPreparedListener { mp ->
            mp.isLooping = true
            video.start()
            setPlayingIcon(true)
            handler.post(tick)
        }
        video.setOnErrorListener { _, _, _ ->
            toastLabel("Preview gagal diputar di HP ini. Video tetap bisa disimpan.")
            true
        }

        // kontrol: scrub + waktu + play
        val controls = LinearLayout(this)
        controls.orientation = LinearLayout.VERTICAL
        controls.setPadding(dp(16f), dp(12f), dp(16f), dp(6f))

        seek = FluxaSlider(this)
        seek.max = 1000
        seek.onProgress = { p ->
            userSeeking = true
            val dur = video.duration
            if (dur > 0) {
                video.seekTo((p / 1000f * dur).toInt())
                timeTv.text = "${Format.time(p / 1000f * dur / 1000f)} / ${Format.time(dur / 1000f)}"
            }
        }
        seek.onReleased = { userSeeking = false }
        controls.addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30f)))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        timeTv = kText("00:00.00 / 00:00.00", 11.5f, FluxaColors.Text, bold = true, font = "monospace")
        row.addView(timeTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val playWrap = FrameLayout(this)
        playWrap.background = ovalBg(0xFFFFFFFF.toInt())
        playWrap.isClickable = true
        playIcon = IconView(this, FluxaIcon.PAUSE, FluxaColors.Bg)
        val pp = dp(13f)
        playIcon.setPadding(pp, pp, pp, pp)
        playWrap.addView(playIcon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        playWrap.pressEffect(0.92f)
        playWrap.setOnClickListener { togglePlay() }
        row.addView(playWrap, LinearLayout.LayoutParams(dp(50f), dp(50f)))
        controls.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // aksi
        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.setPadding(dp(16f), dp(8f), dp(16f), dp(22f))
        val discard = FluxaButton(this, "Buang", FluxaIcon.TRASH, FluxaButtonStyle.DANGER_SOFT, 12f)
        discard.setOnClickListener { discardAndClose() }
        actions.addView(discard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val share = FluxaButton(this, "Bagikan", FluxaIcon.SHARE, FluxaButtonStyle.SOFT, 12f)
        share.setOnClickListener { saveThen(share = true) }
        val lpSh = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpSh.marginStart = dp(8f)
        actions.addView(share, lpSh)
        saveBtn = FluxaButton(this, "Simpan", FluxaIcon.SAVE, FluxaButtonStyle.PRIMARY, 12f)
        saveBtn.setOnClickListener { saveThen(share = false) }
        val lpSv = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f)
        lpSv.marginStart = dp(8f)
        actions.addView(saveBtn, lpSv)
        root.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
    }

    private fun setPlayingIcon(playing: Boolean) {
        playIcon.icon = if (playing) FluxaIcon.PAUSE else FluxaIcon.PLAY
    }

    private fun togglePlay() {
        if (video.isPlaying) {
            video.pause()
            setPlayingIcon(false)
        } else {
            video.start()
            setPlayingIcon(true)
        }
    }

    private fun updateProgress() {
        val dur = video.duration
        if (dur <= 0) return
        val pos = video.currentPosition
        if (!userSeeking) {
            seek.progress = (pos * 1000L / dur).toInt()
        }
        timeTv.text = "${Format.time(pos / 1000f)} / ${Format.time(dur / 1000f)}"
    }

    /** Simpan ke Movies/FluxaMotion lalu (opsional) bagikan. File cache dihapus setelah berhasil. */
    private fun saveThen(share: Boolean) {
        if (busy) return
        busy = true
        saveBtn.isEnabled = false
        Thread {
            val uri = Exporter.publishVideo(applicationContext, file)
            runOnUiThread {
                busy = false
                saveBtn.isEnabled = true
                if (isFinishing) return@runOnUiThread
                if (uri == null) {
                    FluxaSheet.showMessage(
                        this, "Gagal menyimpan",
                        "Video tidak bisa dipindahkan ke galeri. Cek ruang penyimpanan lalu coba lagi.",
                        icon = FluxaIcon.INFO
                    )
                    return@runOnUiThread
                }
                saved = true
                file.delete()
                if (share) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(send, "Bagikan video"))
                }
                setResult(RESULT_OK, Intent().setData(uri))
                finish()
            }
        }.start()
    }

    private fun discardAndClose() {
        FluxaSheet.showConfirm(
            this, "Buang hasil export?", "Video yang belum disimpan akan dihapus.",
            positiveText = "Buang", onPositive = { finish() }, danger = true
        )
    }

    private fun toastLabel(msg: String) {
        FluxaSheet.showMessage(this, "Preview", msg, icon = FluxaIcon.INFO)
    }

    override fun onPause() {
        super.onPause()
        if (video.isPlaying) {
            video.pause()
            setPlayingIcon(false)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        // export yang belum disimpan tidak boleh menumpuk di cache
        if (!saved && ::file.isInitialized) file.delete()
        super.onDestroy()
    }
}
