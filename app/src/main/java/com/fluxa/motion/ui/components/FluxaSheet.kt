package com.fluxa.motion.ui.components

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.fluxa.motion.ui.theme.FluxaColors
import com.fluxa.motion.ui.theme.dp
import com.fluxa.motion.ui.theme.dpf
import com.fluxa.motion.ui.theme.kText
import com.fluxa.motion.ui.theme.navBottomInset
import com.fluxa.motion.ui.theme.roundedRect
import com.fluxa.motion.ui.theme.roundedTop

/**
 * Bottom sheet custom FLUXA — pengganti dialog bawaan Android.
 * Overlay digambar sendiri: dim + kartu meluncur dari bawah dengan handle pill.
 */
object FluxaSheet {

    class Item(val label: String, val desc: String? = null, val icon: FluxaIcon? = null, val iconTint: Int = FluxaColors.Accent, val danger: Boolean = false)

    class Handle internal constructor(
        private val root: ViewGroup,
        private val overlay: FrameLayout,
        private val sheet: LinearLayout
    ) {
        var onDismiss: (() -> Unit)? = null
        private var progressBar: FluxaProgressBar? = null
        private var statusTv: TextView? = null
        private var pctTv: TextView? = null
        var dismissed = false
            private set

        fun dismiss() {
            if (dismissed) return
            dismissed = true
            sheet.animate().translationY(sheet.height.toFloat()).setDuration(150).start()
            overlay.animate().alpha(0f).setDuration(150).withEndAction {
                root.removeView(overlay)
                onDismiss?.invoke()
            }.start()
        }

        internal fun bindProgress(bar: FluxaProgressBar, status: TextView, pct: TextView) {
            progressBar = bar; statusTv = status; pctTv = pct
        }

        fun setProgress(f: Float, text: String) {
            progressBar?.progress = f
            statusTv?.text = text
            pctTv?.text = "${(f * 100).toInt()}%"
        }
    }

    // ---------------- kerangka sheet ----------------
    private fun scaffold(
        activity: Activity,
        title: String?,
        cancellable: Boolean = true
    ): Triple<FrameLayout, LinearLayout, LinearLayout> {
        val root = activity.window.decorView as ViewGroup
        val overlay = FrameLayout(activity)
        overlay.setBackgroundColor(0xF2050609.toInt())
        overlay.alpha = 0f

        val sheet = LinearLayout(activity)
        sheet.orientation = LinearLayout.VERTICAL
        sheet.background = roundedTop(FluxaColors.BgTop, activity.dpf(22f))
        sheet.setPadding(activity.dp(18f), activity.dp(10f), activity.dp(18f), activity.dp(20f) + navBottomInset(activity))

        // pill handle
        val strip = View(activity)
        strip.background = roundedRect(0xFF2C3346.toInt(), activity.dpf(3f))
        val lpStrip = LinearLayout.LayoutParams(activity.dp(42f), activity.dp(4f))
        lpStrip.gravity = Gravity.CENTER_HORIZONTAL
        lpStrip.bottomMargin = activity.dp(10f)
        sheet.addView(strip, lpStrip)

        if (title != null) {
            val headRow = LinearLayout(activity)
            headRow.orientation = LinearLayout.HORIZONTAL
            headRow.gravity = Gravity.CENTER_VERTICAL
            val tv = activity.kText(title, 15f, FluxaColors.Text, bold = true)
            headRow.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (cancellable) {
                val close = FluxaIconButton(activity, FluxaIcon.CLOSE, FluxaColors.Dim, filled = false, sizeDp = 32f)
                close.setOnClickListener { dismissOverlay(root, overlay, sheet) }
                headRow.addView(close, LinearLayout.LayoutParams(activity.dp(32f), activity.dp(32f)))
            }
            val lpH = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpH.bottomMargin = activity.dp(8f)
            sheet.addView(headRow, lpH)
        }

        val body = LinearLayout(activity)
        body.orientation = LinearLayout.VERTICAL
        sheet.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        if (cancellable) {
            overlay.setOnClickListener { dismissOverlay(root, overlay, sheet) }
        }
        sheet.setOnClickListener { /* telan klik supaya tidak tembus ke overlay */ }

        val lpSheet = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpSheet.gravity = Gravity.BOTTOM
        overlay.addView(sheet, lpSheet)
        root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        overlay.animate().alpha(1f).setDuration(160).start()
        sheet.post {
            sheet.translationY = sheet.height.toFloat()
            sheet.animate().translationY(0f).setDuration(200).start()
        }
        return Triple(overlay, sheet, body)
    }

    private fun dismissOverlay(root: ViewGroup, overlay: FrameLayout, sheet: LinearLayout) {
        sheet.animate().translationY(sheet.height.toFloat()).setDuration(150).start()
        overlay.animate().alpha(0f).setDuration(150).withEndAction { root.removeView(overlay) }.start()
    }

    private fun handleOf(activity: Activity, overlay: FrameLayout, sheet: LinearLayout): Handle {
        return Handle(activity.window.decorView as ViewGroup, overlay, sheet)
    }

    // ---------------- daftar pilihan ----------------
    fun showList(activity: Activity, title: String, items: List<Item>, onPick: (Int) -> Unit): Handle {
        val (overlay, sheet, body) = scaffold(activity, title)
        val handle = handleOf(activity, overlay, sheet)
        val list = LinearLayout(activity)
        list.orientation = LinearLayout.VERTICAL
        for ((i, item) in items.withIndex()) {
            val row = LinearLayout(activity)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.isClickable = true
            row.background = roundedRect(FluxaColors.Card, activity.dpf(12f))
            row.setPadding(activity.dp(12f), activity.dp(11f), activity.dp(12f), activity.dp(11f))
            row.pressRow()
            if (item.icon != null) {
                val tile = FrameLayout(activity)
                tile.background = roundedRect(
                    if (item.danger) FluxaColors.DangerInk else FluxaColors.CardHi,
                    activity.dpf(9f)
                )
                val iv = IconView(activity, item.icon, if (item.danger) FluxaColors.Rose else item.iconTint)
                val p = activity.dp(8f)
                iv.setPadding(p, p, p, p)
                tile.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                val lpT = LinearLayout.LayoutParams(activity.dp(36f), activity.dp(36f))
                lpT.rightMargin = activity.dp(11f)
                row.addView(tile, lpT)
            }
            val col = LinearLayout(activity)
            col.orientation = LinearLayout.VERTICAL
            col.addView(activity.kText(item.label, 13f, if (item.danger) FluxaColors.Rose else FluxaColors.Text, bold = true))
            if (item.desc != null) {
                val d = activity.kText(item.desc, 11f, FluxaColors.Dim)
                val lpD = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lpD.topMargin = activity.dp(2f)
                col.addView(d, lpD)
            }
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val chev = IconView(activity, FluxaIcon.CHEVRON_RIGHT, FluxaColors.Faint)
            row.addView(chev, LinearLayout.LayoutParams(activity.dp(16f), activity.dp(16f)))
            row.setOnClickListener {
                handle.dismiss()
                onPick(i)
            }
            val lpR = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (i > 0) lpR.topMargin = activity.dp(7f)
            list.addView(row, lpR)
        }
        if (items.size > 6) {
            val sv = ScrollView(activity)
            sv.isVerticalScrollBarEnabled = false
            sv.addView(list)
            body.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (activity.resources.displayMetrics.heightPixels * 0.5f).toInt()))
        } else {
            body.addView(list)
        }
        return handle
    }

    // ---------------- pesan / konfirmasi / konten custom ----------------
    fun showMessage(activity: Activity, title: String, message: String, button: String = "Oke", icon: FluxaIcon? = null): Handle {
        val msg = activity.kText(message, 12.5f, FluxaColors.Muted)
        msg.setLineSpacing(activity.dpf(3f), 1.05f)
        val sv = ScrollView(activity)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(msg)
        return show(activity, title, sv, positiveText = button, positiveIcon = icon)
    }

    fun showConfirm(
        activity: Activity, title: String, message: String,
        positiveText: String, onPositive: () -> Unit,
        negativeText: String = "Batal", danger: Boolean = false
    ): Handle {
        val msg = activity.kText(message, 12.5f, FluxaColors.Muted)
        msg.setLineSpacing(activity.dpf(3f), 1.05f)
        return show(
            activity, title, msg,
            positiveText = positiveText, onPositive = onPositive,
            negativeText = negativeText,
            positiveStyle = if (danger) FluxaButtonStyle.DANGER_SOFT else FluxaButtonStyle.PRIMARY
        )
    }

    fun show(
        activity: Activity,
        title: String?,
        content: View?,
        positiveText: String? = null,
        positiveIcon: FluxaIcon? = null,
        onPositive: (() -> Unit)? = null,
        negativeText: String? = null,
        onNegative: (() -> Unit)? = null,
        neutralText: String? = null,
        onNeutral: (() -> Unit)? = null,
        positiveStyle: FluxaButtonStyle = FluxaButtonStyle.PRIMARY,
        cancellable: Boolean = true,
        maxHeightFraction: Float = 0.62f
    ): Handle {
        val (overlay, sheet, body) = scaffold(activity, title, cancellable)
        val handle = handleOf(activity, overlay, sheet)
        if (content != null) {
            val lpC = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (content is ScrollView) {
                lpC.height = (activity.resources.displayMetrics.heightPixels * maxHeightFraction).toInt()
            }
            body.addView(content, lpC)
        }
        if (positiveText != null || negativeText != null || neutralText != null) {
            val btnRow = LinearLayout(activity)
            btnRow.orientation = LinearLayout.HORIZONTAL
            val lpB = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpB.topMargin = activity.dp(14f)
            fun addBtn(btn: FluxaButton) {
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (btnRow.childCount > 0) lp.leftMargin = activity.dp(8f)
                btnRow.addView(btn, lp)
            }
            if (negativeText != null) {
                val b = FluxaButton(activity, negativeText, null, FluxaButtonStyle.SOFT)
                b.setOnClickListener { handle.dismiss(); onNegative?.invoke() }
                addBtn(b)
            }
            if (neutralText != null) {
                val b = FluxaButton(activity, neutralText, null, FluxaButtonStyle.GHOST)
                b.setOnClickListener { handle.dismiss(); onNeutral?.invoke() }
                addBtn(b)
            }
            if (positiveText != null) {
                val b = FluxaButton(activity, positiveText, positiveIcon, positiveStyle)
                b.setOnClickListener { handle.dismiss(); onPositive?.invoke() }
                addBtn(b)
            }
            body.addView(btnRow, lpB)
        }
        return handle
    }

    // ---------------- progress (export) ----------------
    fun showProgress(activity: Activity, title: String, initialStatus: String): Handle {
        val wrap = LinearLayout(activity)
        wrap.orientation = LinearLayout.VERTICAL
        val headRow = LinearLayout(activity)
        headRow.orientation = LinearLayout.HORIZONTAL
        headRow.gravity = Gravity.CENTER_VERTICAL
        val filmTile = FrameLayout(activity)
        filmTile.background = roundedRect(FluxaColors.CardHi, activity.dpf(10f))
        val fiv = IconView(activity, FluxaIcon.FILM, FluxaColors.Accent)
        val fp = activity.dp(8f)
        fiv.setPadding(fp, fp, fp, fp)
        filmTile.addView(fiv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val lpF = LinearLayout.LayoutParams(activity.dp(38f), activity.dp(38f))
        lpF.rightMargin = activity.dp(11f)
        headRow.addView(filmTile, lpF)
        val status = activity.kText(initialStatus, 12.5f, FluxaColors.Muted)
        headRow.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val pct = activity.kText("0%", 15f, FluxaColors.Text, bold = true, font = "monospace")
        headRow.addView(pct)
        wrap.addView(headRow)
        val bar = FluxaProgressBar(activity)
        val lpBar = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(10f))
        lpBar.topMargin = activity.dp(14f)
        wrap.addView(bar, lpBar)
        val note = activity.kText("Video dirender frame demi frame di HP ini. Jangan tutup aplikasi dulu ya.", 10.5f, FluxaColors.Faint)
        val lpN = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpN.topMargin = activity.dp(8f)
        wrap.addView(note, lpN)

        val handle = show(activity, title, wrap, cancellable = false)
        handle.bindProgress(bar, status, pct)
        return handle
    }
}

/** Efek tekan khusus baris daftar (alpha turun). */
private fun View.pressRow() {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> v.animate().alpha(0.75f).setDuration(60).start()
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.animate().alpha(1f).setDuration(120).start()
        }
        false
    }
}
