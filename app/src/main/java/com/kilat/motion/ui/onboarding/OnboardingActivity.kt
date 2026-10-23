package com.kilat.motion.ui.onboarding

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kilat.motion.data.Prefs
import com.kilat.motion.ui.components.IconView
import com.kilat.motion.ui.components.KilatButton
import com.kilat.motion.ui.components.KilatButtonStyle
import com.kilat.motion.ui.components.KilatIcon
import com.kilat.motion.ui.home.HomeActivity
import com.kilat.motion.ui.theme.KColors
import com.kilat.motion.ui.theme.dp
import com.kilat.motion.ui.theme.dpf
import com.kilat.motion.ui.theme.kText
import com.kilat.motion.ui.theme.kilatSolid
import com.kilat.motion.ui.theme.roundedRect
import kotlin.math.abs

/**
 * Onboarding 4 halaman: ilustrasi Canvas custom, indikator titik pil,
 * navigasi tombol + usap (swipe). Muncul sekali di peluncuran pertama;
 * bisa diulang dari tab Profil.
 */
class OnboardingActivity : Activity() {

    private data class Page(val title: String, val desc: String, val accent: Int)

    private val pages = listOf(
        Page(
            "Selamat datang di Fluxa",
            "Editor motion graphics native Android untuk bikin intro, teks animasi, dan video pendek yang geraknya halus - langsung dari HP kamu.",
            KColors.Accent
        ),
        Page(
            "Animasikan dengan Keyframe",
            "Atur posisi, skala, rotasi, sampai efek glow di titik waktu mana pun. Ketuk ikon wajik, geser playhead, ulangi. Gerakannya dihitung otomatis.",
            KColors.Violet
        ),
        Page(
            "Layer Teks, Bentuk, Gambar",
            "Tumpuk layer sesukamu, pakai preset Fade sampai Glitch, tambah musik latar dengan waveform, lalu atur semuanya di timeline.",
            KColors.Sky
        ),
        Page(
            "Export MP4 Tanpa Watermark",
            "Render 720p sampai 4K, sampai 120 fps, langsung ke galeri lengkap dengan musiknya. Tanpa iklan, tanpa watermark.",
            KColors.Amber
        )
    )

    private var index = 0
    private var replay = false

    private lateinit var art: android.widget.ImageView
    private val artRes = intArrayOf(
        com.kilat.motion.R.drawable.ob_welcome,
        com.kilat.motion.R.drawable.ob_keyframe,
        com.kilat.motion.R.drawable.ob_layers,
        com.kilat.motion.R.drawable.ob_export
    )
    private lateinit var titleTv: TextView
    private lateinit var descTv: TextView
    private lateinit var dotsRow: LinearLayout
    private lateinit var nextBtn: KilatButton
    private lateinit var backBtn: KilatButton
    private lateinit var skipBtn: KilatButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = KColors.Bg
        window.navigationBarColor = KColors.Bg
        replay = intent.getBooleanExtra(EXTRA_REPLAY, false)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(KColors.Bg)
        root.fitsSystemWindows = true
        root.setPadding(dp(22f), dp(10f), dp(22f), dp(20f))

        // ---------- bar atas ----------
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val tile = FrameLayout(this)
        tile.background = roundedRect(KColors.Card, dpf(9f))
        val bolt = android.widget.ImageView(this).apply { setImageResource(com.kilat.motion.R.drawable.brand_logo) }
        val bp = dp(6f)
        bolt.setPadding(bp, bp, bp, bp)
        tile.addView(bolt, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        top.addView(tile, LinearLayout.LayoutParams(dp(30f), dp(30f)))
        val brand = kText("Fluxa", 13f, KColors.Text, bold = true)
        val lpBrand = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpBrand.marginStart = dp(9f)
        top.addView(brand, lpBrand)
        skipBtn = KilatButton(this, "Lewati", null, KilatButtonStyle.SOFT, 11f)
        skipBtn.setOnClickListener { finishOnboarding() }
        top.addView(skipBtn)
        root.addView(top)

        // ---------- ilustrasi ----------
        art = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setImageResource(artRes[0])
        }
        val lpArt = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(art, lpArt)

        // ---------- teks ----------
        val textCol = LinearLayout(this)
        textCol.orientation = LinearLayout.VERTICAL
        textCol.gravity = Gravity.CENTER_HORIZONTAL
        titleTv = kText(pages[0].title, 24f, KColors.Text, bold = true)
        titleTv.gravity = Gravity.CENTER
        textCol.addView(titleTv)
        descTv = kText(pages[0].desc, 13f, KColors.Muted)
        descTv.gravity = Gravity.CENTER
        descTv.setLineSpacing(dpf(3f), 1.08f)
        val lpDesc = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpDesc.topMargin = dp(10f)
        lpDesc.marginStart = dp(8f)
        lpDesc.marginEnd = dp(8f)
        textCol.addView(descTv, lpDesc)
        root.addView(textCol)

        // ---------- indikator titik ----------
        dotsRow = LinearLayout(this)
        dotsRow.orientation = LinearLayout.HORIZONTAL
        dotsRow.gravity = Gravity.CENTER
        val lpDots = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpDots.topMargin = dp(22f)
        lpDots.bottomMargin = dp(18f)
        root.addView(dotsRow, lpDots)
        buildDots()

        // ---------- tombol ----------
        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        backBtn = KilatButton(this, "Balik", KilatIcon.BACK, KilatButtonStyle.GHOST)
        backBtn.visibility = View.INVISIBLE
        backBtn.setOnClickListener { goTo(index - 1) }
        val lpBack = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpBack.rightMargin = dp(10f)
        btnRow.addView(backBtn, lpBack)
        nextBtn = KilatButton(this, "Lanjut", KilatIcon.NEXT, KilatButtonStyle.PRIMARY, 13f)
        nextBtn.setOnClickListener {
            if (index == pages.size - 1) finishOnboarding() else goTo(index + 1)
        }
        btnRow.addView(nextBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(btnRow)

        setContentView(root)

        // usap kiri/kanan untuk pindah halaman
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) < 320f || abs(vx) < abs(vy) * 1.4f) return false
                if (vx < 0) {
                    if (index < pages.size - 1) goTo(index + 1)
                } else {
                    if (index > 0) goTo(index - 1)
                }
                return true
            }
        })
        val touch = View.OnTouchListener { _, e -> gd.onTouchEvent(e); true }
        art.setOnTouchListener(touch)
        textCol.setOnTouchListener(touch)
    }

    private fun buildDots() {
        dotsRow.removeAllViews()
        for (i in pages.indices) {
            val d = View(this)
            val active = i == index
            d.background = roundedRect(if (active) pages[index].accent else KColors.Line, dpf(4f))
            val lp = LinearLayout.LayoutParams(dp(if (active) 24f else 8f), dp(8f))
            lp.rightMargin = dp(6f)
            dotsRow.addView(d, lp)
        }
    }

    private fun goTo(target: Int) {
        val clamped = target.coerceIn(0, pages.size - 1)
        if (clamped == index) return
        val dir = if (clamped > index) 1 else -1
        index = clamped
        val page = pages[index]

        // transisi: geser + pudar
        val views = listOf<View>(art, titleTv, descTv)
        for (v in views) {
            v.animate().cancel()
            v.translationX = dir * dpf(26f)
            v.alpha = 0f
            v.animate().translationX(0f).alpha(1f).setDuration(240).start()
        }
        art.setImageResource(artRes[index.coerceIn(0, artRes.lastIndex)])
        titleTv.text = page.title
        descTv.text = page.desc
        buildDots()

        backBtn.visibility = if (index > 0) View.VISIBLE else View.INVISIBLE
        if (index == pages.size - 1) {
            nextBtn.setLabel("Mulai Berkarya")
            nextBtn.setIcon(KilatIcon.BOLT)
            skipBtn.visibility = View.INVISIBLE
        } else {
            nextBtn.setLabel("Lanjut")
            nextBtn.setIcon(KilatIcon.NEXT)
            skipBtn.visibility = View.VISIBLE
        }
    }

    private fun finishOnboarding() {
        Prefs.setOnboardingDone(this)
        if (replay) {
            finish()
        } else {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }

    companion object {
        const val EXTRA_REPLAY = "extra_onboarding_replay"
    }
}
