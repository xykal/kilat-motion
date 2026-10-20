package com.kilat.motion.ui.launcher

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.kilat.motion.data.Prefs
import com.kilat.motion.ui.components.IconView
import com.kilat.motion.ui.components.KilatIcon
import com.kilat.motion.ui.components.KilatProgressBar
import com.kilat.motion.ui.home.HomeActivity
import com.kilat.motion.ui.onboarding.OnboardingActivity
import com.kilat.motion.ui.theme.KColors
import com.kilat.motion.ui.theme.dp
import com.kilat.motion.ui.theme.dpf
import com.kilat.motion.ui.theme.kText
import com.kilat.motion.ui.theme.kilatSolid

/**
 * Layar pembuka: splash singkat logo KILAT, lalu diarahkan ke onboarding
 * (pengguna baru) atau langsung ke homescreen.
 */
class LauncherActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = KColors.Bg
        window.navigationBarColor = KColors.Bg

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(KColors.Bg)
        root.fitsSystemWindows = true

        val tile = FrameLayout(this)
        tile.background = kilatSolid(dpf(26f))
        tile.elevation = dpf(16f)
        val bolt = IconView(this, KilatIcon.BOLT, 0xFF05131F.toInt())
        val p = dp(22f)
        bolt.setPadding(p, p, p, p)
        tile.addView(bolt, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(tile, LinearLayout.LayoutParams(dp(92f), dp(92f)))

        val brand = kText("KILAT", 30f, KColors.Text, bold = true)
        brand.letterSpacing = 0.18f
        brand.gravity = Gravity.CENTER
        val lpB = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpB.topMargin = dp(18f)
        root.addView(brand, lpB)

        val sub = kText("M O T I O N", 11f, KColors.Accent, bold = true)
        sub.gravity = Gravity.CENTER
        val lpS = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpS.topMargin = dp(4f)
        root.addView(sub, lpS)

        val tag = kText("Motion editor, secepat kilat.", 12f, KColors.Dim)
        tag.gravity = Gravity.CENTER
        val lpT = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpT.topMargin = dp(10f)
        root.addView(tag, lpT)

        val bar = KilatProgressBar(this)
        val lpBar = LinearLayout.LayoutParams(dp(120f), dp(8f))
        lpBar.topMargin = dp(30f)
        root.addView(bar, lpBar)

        setContentView(root)

        tile.scaleX = 0.8f
        tile.scaleY = 0.8f
        tile.alpha = 0f
        tile.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(420).start()

        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 850
        anim.addUpdateListener { bar.progress = it.animatedValue as Float }
        anim.start()

        handler.postDelayed({ route() }, 1000)
    }

    private fun route() {
        if (isFinishing) return
        val next = if (Prefs.isOnboardingDone(this)) HomeActivity::class.java else OnboardingActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
