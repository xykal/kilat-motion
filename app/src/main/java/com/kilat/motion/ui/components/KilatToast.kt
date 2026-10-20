package com.kilat.motion.ui.components

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.kilat.motion.ui.theme.KColors
import com.kilat.motion.ui.theme.dp
import com.kilat.motion.ui.theme.dpf
import com.kilat.motion.ui.theme.kText
import com.kilat.motion.ui.theme.roundedRect

/**
 * Toast in-app custom KILAT (pengganti Toast bawaan Android):
 * pil gelap melayang di bawah layar dengan ikon Canvas sendiri.
 */
object KilatToast {

    private val handler = Handler(Looper.getMainLooper())
    private var current: View? = null
    private var token = 0

    fun show(activity: Activity, message: String, icon: KilatIcon = KilatIcon.CHECK) {
        val root = activity.window.decorView as? ViewGroup ?: return
        current?.let { root.removeView(it) }
        val pill = LinearLayout(activity)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.background = roundedRect(0xFF171B27.toInt(), activity.dpf(24f))
        pill.setPadding(activity.dp(10f), activity.dp(9f), activity.dp(16f), activity.dp(9f))

        val iconTile = FrameLayout(activity)
        iconTile.background = roundedRect(KColors.Accent, activity.dpf(13f))
        val iv = IconView(activity, icon, KColors.AccentInk)
        val p = activity.dp(5f)
        iv.setPadding(p, p, p, p)
        iconTile.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val lpT = LinearLayout.LayoutParams(activity.dp(26f), activity.dp(26f))
        lpT.rightMargin = activity.dp(9f)
        pill.addView(iconTile, lpT)
        pill.addView(activity.kText(message, 12f, KColors.Text, bold = true))

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.bottomMargin = activity.dp(92f)
        pill.layoutParams = lp
        pill.alpha = 0f
        pill.translationY = activity.dpf(14f)
        root.addView(pill)
        current = pill
        pill.animate().alpha(1f).translationY(0f).setDuration(180).start()

        val my = ++token
        handler.postDelayed({
            if (my == token) {
                pill.animate().alpha(0f).translationY(activity.dpf(10f)).setDuration(160)
                    .withEndAction { root.removeView(pill); if (current === pill) current = null }
                    .start()
            }
        }, 2200)
    }
}
