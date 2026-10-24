package com.fluxa.motion.ui.components

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.fluxa.motion.ui.theme.FluxaColors
import com.fluxa.motion.ui.theme.dp
import com.fluxa.motion.ui.theme.dpf
import com.fluxa.motion.ui.theme.kText
import com.fluxa.motion.ui.theme.fluxaSolidOval
import com.fluxa.motion.ui.theme.pressEffect
import com.fluxa.motion.ui.theme.roundedRect
import com.fluxa.motion.ui.theme.roundedTop

/**
 * Bottom navigation custom FLUXA untuk homescreen:
 * 4 tab ikon Canvas + tombol Buat (FAB gradient) mengambang di tengah.
 * Tab aktif ditandai pil accent di belakang ikon + label nyala.
 */
class FluxaBottomNav(context: Context) : FrameLayout(context) {

    data class Tab(val id: String, val label: String, val icon: FluxaIcon)

    var onTabSelected: ((String) -> Unit)? = null
    var onCreateClicked: (() -> Unit)? = null

    private val tabViews = LinkedHashMap<String, Triple<LinearLayout, IconView, TextView>>()
    var selectedId: String = ""
        private set

    private var barView: View? = null

    /** Mode compact: bar tab disembunyikan, hanya tombol + di bawah tengah (navigasi lewat sheet). */
    fun setCompact(on: Boolean) {
        barView?.visibility = if (on) View.GONE else View.VISIBLE
    }

    init {
        // FAB punya bayangan yang keluar dari batas view, jadi parent jangan memotongnya
        clipChildren = false
        clipToPadding = false
        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = roundedTop(FluxaColors.BgTop, dpf(20f))
        bar.setPadding(dp(8f), dp(8f), dp(8f), dp(10f))
        val lpBar = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        lpBar.topMargin = dp(24f)
        addView(bar, lpBar)
        barView = bar

        val tabs = listOf(
            Tab("home", "Beranda", FluxaIcon.HOME),
            Tab("projects", "Project", FluxaIcon.FOLDER),
            Tab("templates", "Template", FluxaIcon.LAYERS),
            Tab("profile", "Profil", FluxaIcon.USER)
        )
        // urutan visual: Beranda, Project, [FAB], Template, Profil
        val order = listOf(tabs[0], tabs[1], null, tabs[2], tabs[3])
        for (tab in order) {
            if (tab == null) {
                // slot tengah: ruang kosong seukuran pil tab + label "Buat" di baris yang sama dengan label tab lain.
                // FAB digambar mengambang di atas slot ini.
                val slot = LinearLayout(context)
                slot.orientation = LinearLayout.VERTICAL
                slot.gravity = Gravity.CENTER
                slot.setPadding(dp(4f), dp(4f), dp(4f), dp(2f))
                slot.addView(View(context), LinearLayout.LayoutParams(dp(52f), dp(30f)))
                val slotLabel = context.kText("Buat", 9.5f, FluxaColors.Faint, bold = true)
                slotLabel.gravity = Gravity.CENTER
                val lpSl = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lpSl.topMargin = dp(2f)
                slot.addView(slotLabel, lpSl)
                bar.addView(slot, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                continue
            }
            val item = LinearLayout(context)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.isClickable = true
            item.setPadding(dp(4f), dp(4f), dp(4f), dp(2f))
            val pill = FrameLayout(context)
            val iv = IconView(context, tab.icon, FluxaColors.Dim)
            val p = dp(5f)
            iv.setPadding(p + dp(6f), p, p + dp(6f), p)
            pill.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            item.addView(pill, LinearLayout.LayoutParams(dp(52f), dp(30f)))
            val label = context.kText(tab.label, 9.5f, FluxaColors.Faint, bold = true)
            label.gravity = Gravity.CENTER
            val lpL = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpL.topMargin = dp(2f)
            item.addView(label, lpL)
            item.pressEffect(0.94f)
            item.setOnClickListener { select(tab.id, notify = true) }
            // pill disimpan lewat tag di icon view parent
            tabViews[tab.id] = Triple(item, iv, label)
            // simpan referensi pill di tag item agar bisa diwarnai saat aktif
            item.tag = pill
            bar.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        // FAB Buat di tengah, mengambang di atas bar
        val fab = FrameLayout(context)
        fab.background = fluxaSolidOval()
        fab.isClickable = true
        val plus = IconView(context, FluxaIcon.PLUS, 0xFF05131F.toInt())
        val fp = dp(16f)
        plus.setPadding(fp, fp, fp, fp)
        fab.addView(plus, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        fab.pressEffect(0.9f)
        fab.setOnClickListener { onCreateClicked?.invoke() }
        val lpFab = LayoutParams(dp(58f), dp(58f), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lpFab.topMargin = dp(2f)
        // bayangan FAB: elevasi + warna aksen supaya terlihat di latar gelap
        fab.elevation = dpf(10f)
        fab.outlineSpotShadowColor = FluxaColors.Accent
        fab.outlineAmbientShadowColor = FluxaColors.Accent
        addView(fab, lpFab)

        select("home", notify = false)
    }

    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, h)
        // beri ruang ekstra di atas supaya FAB yang mengambang tidak terpotong parent lain
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    fun select(id: String, notify: Boolean) {
        if (id == selectedId && notify) return
        selectedId = id
        for ((tid, triple) in tabViews) {
            val (item, iv, label) = triple
            val pill = item.tag as? FrameLayout
            if (tid == id) {
                pill?.background = roundedRect(0xFF123B32.toInt(), dpf(15f))
                iv.tintColor = FluxaColors.Accent
                label.setTextColor(FluxaColors.Accent)
                item.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start()
            } else {
                pill?.background = null
                iv.tintColor = FluxaColors.Dim
                label.setTextColor(FluxaColors.Faint)
                item.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
        }
        if (notify) onTabSelected?.invoke(id)
    }
}
