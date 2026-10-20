package com.kilat.motion.ui.home

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.kilat.motion.core.model.Format
import com.kilat.motion.core.model.Project
import com.kilat.motion.data.Prefs
import com.kilat.motion.data.ProjectLibrary
import com.kilat.motion.data.Templates
import com.kilat.motion.ui.components.IconView
import com.kilat.motion.ui.components.KilatBottomNav
import com.kilat.motion.ui.components.KilatButton
import com.kilat.motion.ui.components.KilatButtonStyle
import com.kilat.motion.ui.components.KilatChip
import com.kilat.motion.ui.components.KilatIcon
import com.kilat.motion.ui.components.KilatIconButton
import com.kilat.motion.ui.components.KilatSheet
import com.kilat.motion.ui.components.KilatToast
import com.kilat.motion.ui.components.Thumbnails
import com.kilat.motion.ui.editor.EditorActivity
import com.kilat.motion.ui.onboarding.OnboardingActivity
import com.kilat.motion.ui.theme.KColors
import com.kilat.motion.ui.theme.dp
import com.kilat.motion.ui.theme.dpf
import com.kilat.motion.ui.theme.kSectionLabel
import com.kilat.motion.ui.theme.kText
import com.kilat.motion.ui.theme.kilatSolid
import com.kilat.motion.ui.theme.kilatSolidOval
import com.kilat.motion.ui.theme.ovalBg
import com.kilat.motion.ui.theme.pressEffect
import com.kilat.motion.ui.theme.roundedRect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Homescreen KILAT Motion dengan bottom navigation custom:
 * Beranda | Project | (Buat) | Template | Profil.
 * Thumbnail kartu dirender langsung dari isi project/template.
 */
class HomeActivity : Activity() {

    private lateinit var pageHost: FrameLayout
    private lateinit var bottomNav: KilatBottomNav
    private var currentTab = "home"
    private var firstResume = true
    private val templateThumbCache = HashMap<String, android.graphics.Bitmap?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = KColors.BgTop
        window.navigationBarColor = KColors.Bg
        buildUi()
        showPage("home")
    }

    override fun onResume() {
        super.onResume()
        if (firstResume) {
            firstResume = false
            return
        }
        // segarkan halaman (project mungkin berubah setelah dari editor)
        showPage(currentTab)
    }

    // =====================================================================
    // kerangka
    // =====================================================================
    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(KColors.Bg)
        root.fitsSystemWindows = true

        // header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setBackgroundColor(KColors.BgTop)
        header.setPadding(dp(16f), dp(10f), dp(12f), dp(10f))
        val tile = FrameLayout(this)
        tile.background = kilatSolid(dpf(11f))
        val bolt = IconView(this, KilatIcon.BOLT, 0xFF05131F.toInt())
        val bp = dp(8f)
        bolt.setPadding(bp, bp, bp, bp)
        tile.addView(bolt, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        header.addView(tile, LinearLayout.LayoutParams(dp(36f), dp(36f)))
        val brandCol = LinearLayout(this)
        brandCol.orientation = LinearLayout.VERTICAL
        val b1 = kText("KILAT Motion", 14f, KColors.Text, bold = true)
        brandCol.addView(b1)
        brandCol.addView(kText("Motion editor di saku kamu", 10f, KColors.Dim))
        val lpBrand = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpBrand.marginStart = dp(10f)
        header.addView(brandCol, lpBrand)
        val guideBtn = KilatIconButton(this, KilatIcon.BOOK, KColors.Muted, filled = true, sizeDp = 36f)
        guideBtn.setOnClickListener { showGuide() }
        header.addView(guideBtn, LinearLayout.LayoutParams(dp(36f), dp(36f)))
        root.addView(header)

        pageHost = FrameLayout(this)
        root.addView(pageHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        bottomNav = KilatBottomNav(this)
        bottomNav.onTabSelected = { id -> showPage(id) }
        bottomNav.onCreateClicked = { showCreateSheet() }
        root.addView(bottomNav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
    }

    private fun showPage(id: String) {
        currentTab = id
        val page = when (id) {
            "projects" -> buildProjectsPage()
            "templates" -> buildTemplatesPage()
            "profile" -> buildProfilePage()
            else -> buildHomePage()
        }
        page.alpha = 0f
        page.translationY = dpf(10f)
        pageHost.removeAllViews()
        pageHost.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        page.animate().alpha(1f).translationY(0f).setDuration(180).start()
    }

    private fun pageScroll(content: LinearLayout): ScrollView {
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        content.setPadding(dp(16f), dp(14f), dp(16f), dp(28f))
        sv.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return sv
    }

    // =====================================================================
    // komponen kartu
    // =====================================================================
    private fun sectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(kSectionLabel(title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (actionLabel != null) {
            val a = kText(actionLabel, 11f, KColors.Accent, bold = true)
            a.isClickable = true
            a.setPadding(dp(8f), dp(4f), dp(2f), dp(4f))
            a.setOnClickListener { onAction?.invoke() }
            row.addView(a)
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(20f)
        lp.bottomMargin = dp(10f)
        row.layoutParams = lp
        return row
    }

    private fun thumbView(project: Project, time: Float, radiusDp: Float = 12f): ImageView {
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.setBackgroundColor(project.bg)
        Thumbnails.render(project, 360, time)?.let { iv.setImageBitmap(it) }
        Thumbnails.roundCorners(iv, dpf(radiusDp))
        return iv
    }

    private fun templateThumbView(info: Templates.TemplateInfo, radiusDp: Float = 12f): ImageView {
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        val bmp = templateThumbCache.getOrPut(info.id) { Thumbnails.render(info.build(), 360, info.previewTime) }
        if (bmp != null) iv.setImageBitmap(bmp) else iv.setBackgroundColor(KColors.Panel)
        Thumbnails.roundCorners(iv, dpf(radiusDp))
        return iv
    }

    private fun iconTile(icon: KilatIcon, tint: Int, sizeDp: Float = 40f, bg: Int = KColors.CardHi): FrameLayout {
        val tile = FrameLayout(this)
        tile.background = roundedRect(bg, dpf(11f))
        val iv = IconView(this, icon, tint)
        val p = dp(sizeDp * 0.23f)
        iv.setPadding(p, p, p, p)
        tile.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tile.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        return tile
    }

    private fun menuRow(icon: KilatIcon, tint: Int, label: String, desc: String, danger: Boolean = false, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.isClickable = true
        row.background = roundedRect(KColors.Card, dpf(14f))
        row.setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
        val tile = iconTile(icon, if (danger) KColors.Rose else tint, 40f, if (danger) KColors.DangerInk else KColors.CardHi)
        val lpT = tile.layoutParams as LinearLayout.LayoutParams
        lpT.rightMargin = dp(12f)
        row.addView(tile)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(kText(label, 13f, if (danger) KColors.Rose else KColors.Text, bold = true))
        val d = kText(desc, 11f, KColors.Dim)
        val lpD = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpD.topMargin = dp(2f)
        col.addView(d, lpD)
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(IconView(this, KilatIcon.CHEVRON_RIGHT, KColors.Faint), LinearLayout.LayoutParams(dp(16f), dp(16f)))
        row.pressEffect(0.98f)
        row.setOnClickListener { onClick() }
        return row
    }

    private fun addSpaced(parent: LinearLayout, view: View, topDp: Float = 8f) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(topDp)
        parent.addView(view, lp)
    }

    private fun formatDate(ts: Long): String {
        return try {
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID")).format(Date(ts))
        } catch (e: Throwable) {
            ""
        }
    }

    private fun projectMeta(p: Project): String =
        "${p.width} x ${p.height} | ${Format.dec(p.duration, 0)} dtk | ${p.layers.size} layer"

    // =====================================================================
    // TAB: Beranda
    // =====================================================================
    private fun buildHomePage(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        col.addView(kText("Halo, Kreator.", 24f, KColors.Text, bold = true))
        val sub = kText("Mau bikin motion apa hari ini?", 12.5f, KColors.Muted)
        val lpSub = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpSub.topMargin = dp(4f)
        col.addView(sub, lpSub)

        // kartu Buat Project (hero gradient)
        val hero = FrameLayout(this)
        hero.background = kilatSolid(dpf(20f))
        hero.isClickable = true
        hero.elevation = dpf(8f)
        val decor = IconView(this, KilatIcon.BOLT, 0xFF1FA98F.toInt())
        val lpDecor = FrameLayout.LayoutParams(dp(150f), dp(150f), Gravity.END or Gravity.CENTER_VERTICAL)
        lpDecor.rightMargin = -dp(30f)
        hero.addView(decor, lpDecor)
        val heroCol = LinearLayout(this)
        heroCol.orientation = LinearLayout.VERTICAL
        heroCol.setPadding(dp(18f), dp(18f), dp(18f), dp(18f))
        heroCol.addView(kText("Buat Project Baru", 20f, 0xFF05131F.toInt(), bold = true))
        heroCol.addView(kText("Mulai dari kanvas kosong, pilih rasio favoritmu.", 12f, 0xCC05131F.toInt()))
        val chipRow = LinearLayout(this)
        chipRow.orientation = LinearLayout.HORIZONTAL
        val chipScroll = HorizontalScrollView(this)
        chipScroll.isHorizontalScrollBarEnabled = false
        chipScroll.addView(chipRow)
        for (r in Templates.RATIOS) {
            val chip = KilatChip(this, r.label)
            val lpC = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpC.rightMargin = dp(7f)
            chipRow.addView(chip, lpC)
            chip.setOnClickListener { openEditorNew(r.w, r.h) }
        }
        val lpChip = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpChip.topMargin = dp(14f)
        heroCol.addView(chipScroll, lpChip)
        hero.addView(heroCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        hero.setOnClickListener { showCreateSheet() }
        hero.pressEffect(0.98f)
        addSpaced(col, hero, 16f)

        // lanjutkan project terakhir
        val latest = ProjectLibrary.latest(this)
        if (latest != null) {
            col.addView(sectionHeader("Lanjutkan", "Semua project") { bottomNav.select("projects", true) })
            col.addView(projectCard(latest))
        }

        // template
        col.addView(sectionHeader("Template KILAT", "Lihat semua") { bottomNav.select("templates", true) })
        val tRow = LinearLayout(this)
        tRow.orientation = LinearLayout.HORIZONTAL
        val tScroll = HorizontalScrollView(this)
        tScroll.isHorizontalScrollBarEnabled = false
        tScroll.addView(tRow)
        for (info in Templates.ALL) {
            val card = templateCardSmall(info)
            val lp = LinearLayout.LayoutParams(dp(152f), ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(10f)
            tRow.addView(card, lp)
        }
        col.addView(tScroll)

        // mulai cepat
        col.addView(sectionHeader("Mulai Cepat"))
        val grid1 = LinearLayout(this)
        grid1.orientation = LinearLayout.HORIZONTAL
        grid1.addView(quickTile(KilatIcon.PLUS, KColors.Accent, "Kosong 9:16", "Kanvas vertikal baru") {
            openEditorNew(1080, 1920)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val g2 = quickTile(KilatIcon.BOLT, KColors.Amber, "Mainkan Demo", "Lihat contoh animasi") {
            openEditorDemo()
        }
        val lpG2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpG2.leftMargin = dp(10f)
        grid1.addView(g2, lpG2)
        col.addView(grid1)
        val grid2 = LinearLayout(this)
        grid2.orientation = LinearLayout.HORIZONTAL
        val lpGrid2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpGrid2.topMargin = dp(10f)
        grid2.addView(quickTile(KilatIcon.LAYERS, KColors.Violet, "Jelajahi Template", "${Templates.ALL.size} template siap edit") {
            bottomNav.select("templates", true)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val g4 = quickTile(KilatIcon.BOOK, KColors.Sky, "Panduan", "Belajar keyframe cepat") { showGuide() }
        val lpG4 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpG4.leftMargin = dp(10f)
        grid2.addView(g4, lpG4)
        col.addView(grid2, lpGrid2)

        return pageScroll(col)
    }

    private fun quickTile(icon: KilatIcon, tint: Int, label: String, desc: String, onClick: () -> Unit): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.isClickable = true
        card.background = roundedRect(KColors.Card, dpf(16f))
        card.setPadding(dp(14f), dp(13f), dp(14f), dp(13f))
        card.addView(iconTile(icon, tint, 42f))
        val l = kText(label, 13f, KColors.Text, bold = true)
        val lpL = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpL.topMargin = dp(10f)
        card.addView(l, lpL)
        val d = kText(desc, 10.5f, KColors.Dim)
        val lpD = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpD.topMargin = dp(3f)
        card.addView(d, lpD)
        card.pressEffect(0.97f)
        card.setOnClickListener { onClick() }
        return card
    }

    private fun templateCardSmall(info: Templates.TemplateInfo): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.isClickable = true
        card.background = roundedRect(KColors.Card, dpf(16f))
        card.setPadding(dp(8f), dp(8f), dp(8f), dp(11f))
        val thumb = templateThumbView(info)
        card.addView(thumb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(168f)))
        val t = kText(info.title, 12.5f, KColors.Text, bold = true)
        val lpT = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpT.topMargin = dp(9f)
        lpT.marginStart = dp(3f)
        card.addView(t, lpT)
        val d = kText(info.desc, 10f, KColors.Dim)
        d.maxLines = 2
        val lpD = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpD.topMargin = dp(3f)
        lpD.marginStart = dp(3f)
        card.addView(d, lpD)
        card.pressEffect(0.97f)
        card.setOnClickListener { openEditorTemplate(info.id) }
        return card
    }

    private fun projectCard(p: Project): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.isClickable = true
        row.background = roundedRect(KColors.Card, dpf(16f))
        row.setPadding(dp(10f), dp(10f), dp(12f), dp(10f))
        val thumbTime = minOf(2.4f, p.duration * 0.45f)
        val thumb = thumbView(p, thumbTime)
        val lpTh = LinearLayout.LayoutParams(dp(58f), dp(78f))
        lpTh.rightMargin = dp(12f)
        row.addView(thumb, lpTh)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val name = kText(p.name, 13.5f, KColors.Text, bold = true)
        name.maxLines = 1
        col.addView(name)
        val meta = kText(projectMeta(p), 10.5f, KColors.Dim)
        val lpM = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpM.topMargin = dp(3f)
        col.addView(meta, lpM)
        val date = kText("Diubah ${formatDate(p.updatedAt)}", 10f, KColors.Faint)
        val lpDt = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpDt.topMargin = dp(2f)
        col.addView(date, lpDt)
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val playTile = FrameLayout(this)
        playTile.background = ovalBg(0xFFFFFFFF.toInt())
        val pv = IconView(this, KilatIcon.PLAY, KColors.Bg)
        val pp = dp(7f)
        pv.setPadding(pp, pp, pp, pp)
        playTile.addView(pv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        row.addView(playTile, LinearLayout.LayoutParams(dp(34f), dp(34f)))
        row.pressEffect(0.98f)
        row.setOnClickListener { openEditorProject(p.id) }
        return row
    }

    // =====================================================================
    // TAB: Project
    // =====================================================================
    private fun buildProjectsPage(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(kText("Project Saya", 22f, KColors.Text, bold = true))

        val projects = ProjectLibrary.list(this)
        val count = kText("${projects.size} project tersimpan di HP ini", 12f, KColors.Muted)
        val lpC = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpC.topMargin = dp(4f)
        lpC.bottomMargin = dp(14f)
        col.addView(count, lpC)

        if (projects.isEmpty()) {
            val empty = LinearLayout(this)
            empty.orientation = LinearLayout.VERTICAL
            empty.gravity = Gravity.CENTER_HORIZONTAL
            empty.background = roundedRect(KColors.Card, dpf(18f))
            empty.setPadding(dp(20f), dp(30f), dp(20f), dp(26f))
            val circle = FrameLayout(this)
            circle.background = ovalBg(KColors.CardHi)
            val iv = IconView(this, KilatIcon.FOLDER, KColors.Dim)
            val p = dp(20f)
            iv.setPadding(p, p, p, p)
            circle.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            empty.addView(circle, LinearLayout.LayoutParams(dp(76f), dp(76f)))
            val t = kText("Belum ada project", 15f, KColors.Text, bold = true)
            val lpT = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpT.topMargin = dp(16f)
            empty.addView(t, lpT)
            val d = kText("Project barumu akan muncul di sini dan tersimpan otomatis selama kamu mengedit.", 11.5f, KColors.Dim)
            d.gravity = Gravity.CENTER
            val lpD = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpD.topMargin = dp(6f)
            empty.addView(d, lpD)
            val btn = KilatButton(this, "Buat Project", KilatIcon.PLUS, KilatButtonStyle.PRIMARY)
            btn.setOnClickListener { showCreateSheet() }
            val lpB = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpB.topMargin = dp(18f)
            empty.addView(btn, lpB)
            col.addView(empty)
            return pageScroll(col)
        }

        for ((i, p) in projects.withIndex()) {
            val card = LinearLayout(this)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.isClickable = true
            card.background = roundedRect(KColors.Card, dpf(16f))
            card.setPadding(dp(10f), dp(10f), dp(8f), dp(10f))
            val thumb = thumbView(p, minOf(2.4f, p.duration * 0.45f))
            val lpTh = LinearLayout.LayoutParams(dp(62f), dp(84f))
            lpTh.rightMargin = dp(12f)
            card.addView(thumb, lpTh)
            val info = LinearLayout(this)
            info.orientation = LinearLayout.VERTICAL
            val name = kText(p.name, 13.5f, KColors.Text, bold = true)
            name.maxLines = 2
            info.addView(name)
            val meta = kText(projectMeta(p), 10.5f, KColors.Dim)
            val lpM = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpM.topMargin = dp(3f)
            info.addView(meta, lpM)
            val date = kText(formatDate(p.updatedAt), 10f, KColors.Faint)
            val lpDt = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpDt.topMargin = dp(2f)
            info.addView(date, lpDt)
            if (p.music != null) {
                val mRow = LinearLayout(this)
                mRow.orientation = LinearLayout.HORIZONTAL
                mRow.gravity = Gravity.CENTER_VERTICAL
                mRow.addView(IconView(this, KilatIcon.MUSIC, KColors.Accent), LinearLayout.LayoutParams(dp(13f), dp(13f)))
                val mn = kText(" " + (p.music?.name ?: ""), 10f, KColors.Accent)
                mn.maxLines = 1
                mRow.addView(mn)
                val lpMR = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lpMR.topMargin = dp(4f)
                info.addView(mRow, lpMR)
            }
            card.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val del = KilatIconButton(this, KilatIcon.TRASH, KColors.Rose, filled = false, sizeDp = 36f)
            del.setOnClickListener { confirmDelete(p) }
            card.addView(del, LinearLayout.LayoutParams(dp(36f), dp(36f)))
            card.pressEffect(0.98f)
            card.setOnClickListener { openEditorProject(p.id) }
            if (i > 0) addSpaced(col, card, 10f) else col.addView(card)
        }
        return pageScroll(col)
    }

    private fun confirmDelete(p: Project) {
        KilatSheet.showConfirm(
            this, "Hapus project?",
            "\"${p.name}\" akan dihapus permanen dari HP ini. File gambar dan musik yang pernah diimpor untuk project lain tidak ikut terhapus.",
            positiveText = "Hapus", danger = true,
            onPositive = {
                ProjectLibrary.delete(this, p.id)
                KilatToast.show(this, "Project dihapus", KilatIcon.TRASH)
                showPage(currentTab)
            }
        )
    }

    // =====================================================================
    // TAB: Template
    // =====================================================================
    private fun buildTemplatesPage(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(kText("Template", 22f, KColors.Text, bold = true))
        val sub = kText("Titik mulai siap edit. Ketuk template, langsung masuk editor sebagai project baru.", 12f, KColors.Muted)
        val lpS = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpS.topMargin = dp(4f)
        lpS.bottomMargin = dp(14f)
        col.addView(sub, lpS)

        for ((i, info) in Templates.ALL.withIndex()) {
            val proj = info.build()
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.background = roundedRect(KColors.Card, dpf(18f))
            card.setPadding(dp(10f), dp(10f), dp(10f), dp(13f))
            val thumb = templateThumbView(info, 13f)
            card.addView(thumb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200f)))
            val titleRow = LinearLayout(this)
            titleRow.orientation = LinearLayout.HORIZONTAL
            titleRow.gravity = Gravity.CENTER_VERTICAL
            val dot = View(this)
            dot.background = ovalBg(info.accent)
            titleRow.addView(dot, LinearLayout.LayoutParams(dp(9f), dp(9f)))
            val t = kText(info.title, 14f, KColors.Text, bold = true)
            val lpT = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lpT.marginStart = dp(8f)
            titleRow.addView(t, lpT)
            titleRow.addView(kText("${proj.width} x ${proj.height}", 10f, KColors.Faint, bold = true, font = "monospace"))
            val lpTR = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpTR.topMargin = dp(11f)
            card.addView(titleRow, lpTR)
            val d = kText(info.desc, 11.5f, KColors.Dim)
            val lpD = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpD.topMargin = dp(4f)
            card.addView(d, lpD)
            val btn = KilatButton(this, "Pakai Template", KilatIcon.WAND, KilatButtonStyle.PRIMARY, 12f)
            btn.setOnClickListener { openEditorTemplate(info.id) }
            val lpB = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpB.topMargin = dp(12f)
            card.addView(btn, lpB)
            if (i > 0) addSpaced(col, card, 12f) else col.addView(card)
        }
        return pageScroll(col)
    }

    // =====================================================================
    // TAB: Profil
    // =====================================================================
    private fun buildProfilePage(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val avatar = FrameLayout(this)
        avatar.background = kilatSolidOval()
        val av = IconView(this, KilatIcon.USER, 0xFF05131F.toInt())
        val ap = dp(16f)
        av.setPadding(ap, ap, ap, ap)
        avatar.addView(av, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        head.addView(avatar, LinearLayout.LayoutParams(dp(62f), dp(62f)))
        val nameCol = LinearLayout(this)
        nameCol.orientation = LinearLayout.VERTICAL
        nameCol.addView(kText("Kreator Kilat", 17f, KColors.Text, bold = true))
        nameCol.addView(kText("KILAT Motion 1.3.0", 11f, KColors.Dim))
        val lpN = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpN.marginStart = dp(13f)
        head.addView(nameCol, lpN)
        col.addView(head)

        // statistik
        val projects = ProjectLibrary.list(this)
        val totalLayers = projects.sumOf { it.layers.size }
        val stats = LinearLayout(this)
        stats.orientation = LinearLayout.HORIZONTAL
        val lpStats = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpStats.topMargin = dp(18f)
        fun statCard(icon: KilatIcon, tint: Int, value: String, label: String): LinearLayout {
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            c.gravity = Gravity.CENTER_HORIZONTAL
            c.background = roundedRect(KColors.Card, dpf(15f))
            c.setPadding(dp(8f), dp(13f), dp(8f), dp(12f))
            val tile = iconTile(icon, tint, 36f)
            (tile.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL
            c.addView(tile)
            val v = kText(value, 17f, KColors.Text, bold = true)
            v.gravity = Gravity.CENTER
            val lpV = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpV.topMargin = dp(8f)
            lpV.gravity = Gravity.CENTER_HORIZONTAL
            c.addView(v, lpV)
            val l = kText(label, 9.5f, KColors.Faint, bold = true)
            l.gravity = Gravity.CENTER
            val lpL2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpL2.topMargin = dp(2f)
            lpL2.gravity = Gravity.CENTER_HORIZONTAL
            c.addView(l, lpL2)
            return c
        }
        stats.addView(statCard(KilatIcon.FOLDER, KColors.Accent, projects.size.toString(), "PROJECT"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val s2 = statCard(KilatIcon.LAYERS, KColors.Violet, Templates.ALL.size.toString(), "TEMPLATE")
        val lpS2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpS2.leftMargin = dp(9f)
        stats.addView(s2, lpS2)
        val s3 = statCard(KilatIcon.GRID, KColors.Sky, totalLayers.toString(), "TOTAL LAYER")
        val lpS3 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpS3.leftMargin = dp(9f)
        stats.addView(s3, lpS3)
        col.addView(stats, lpStats)

        col.addView(sectionHeader("Aplikasi"))
        col.addView(menuRow(KilatIcon.BOOK, KColors.Sky, "Panduan", "Cara pakai editor dan keyframe") { showGuide() })
        addSpaced(col, menuRow(KilatIcon.SPARK, KColors.Amber, "Ulangi Onboarding", "Lihat lagi tur pembuka aplikasi") {
            Prefs.resetOnboarding(this)
            val i = Intent(this, OnboardingActivity::class.java)
            i.putExtra(OnboardingActivity.EXTRA_REPLAY, true)
            startActivity(i)
        })
        addSpaced(col, menuRow(KilatIcon.INFO, KColors.Muted, "Tentang KILAT Motion", "Versi 1.3.0 - native, tanpa iklan") { showAbout() })
        addSpaced(col, menuRow(KilatIcon.TRASH, KColors.Rose, "Hapus Semua Project", "Kosongkan pustaka project di HP ini", danger = true) {
            KilatSheet.showConfirm(
                this, "Hapus semua project?",
                "Seluruh ${projects.size} project akan dihapus permanen. Aksi ini tidak bisa dibatalkan.",
                positiveText = "Hapus Semua", danger = true,
                onPositive = {
                    ProjectLibrary.deleteAll(this)
                    KilatToast.show(this, "Semua project dihapus", KilatIcon.TRASH)
                    showPage("profile")
                }
            )
        })

        val foot = kText("Dibuat dengan Kotlin, Canvas, dan MediaCodec.\nSemua ikon digambar manual - tanpa emoji.", 10.5f, KColors.Faint)
        foot.gravity = Gravity.CENTER
        val lpF = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpF.topMargin = dp(24f)
        col.addView(foot, lpF)
        return pageScroll(col)
    }

    // =====================================================================
    // aksi global
    // =====================================================================
    private fun showCreateSheet() {
        val tints = listOf(KColors.Accent, KColors.Sky, KColors.Violet, KColors.Amber, KColors.Rose)
        val items = Templates.RATIOS.mapIndexed { i, r ->
            KilatSheet.Item("${r.label}  (${r.w} x ${r.h})", r.desc, KilatIcon.RATIO, tints[i % tints.size])
        }
        KilatSheet.showList(this, "Project baru - pilih rasio", items) { which ->
            val r = Templates.RATIOS[which]
            openEditorNew(r.w, r.h)
        }
    }

    private fun openEditorProject(id: String) {
        val i = Intent(this, EditorActivity::class.java)
        i.putExtra(EditorActivity.EXTRA_PROJECT_ID, id)
        startActivity(i)
    }

    private fun openEditorTemplate(id: String) {
        val i = Intent(this, EditorActivity::class.java)
        i.putExtra(EditorActivity.EXTRA_TEMPLATE_ID, id)
        startActivity(i)
        KilatToast.show(this, "Template dipakai sebagai project baru", KilatIcon.WAND)
    }

    private fun openEditorNew(w: Int, h: Int) {
        val i = Intent(this, EditorActivity::class.java)
        i.putExtra(EditorActivity.EXTRA_NEW_W, w)
        i.putExtra(EditorActivity.EXTRA_NEW_H, h)
        startActivity(i)
    }

    private fun openEditorDemo() {
        val i = Intent(this, EditorActivity::class.java)
        i.putExtra(EditorActivity.EXTRA_DEMO, true)
        startActivity(i)
    }

    private fun showGuide() {
        val msg = """
            ALUR CEPAT
            - Buat project dari tombol Buat, atau pakai template
            - Tambah layer: Teks / Bentuk / Gambar
            - Pindah layer di kanvas, atur waktunya di timeline
            - Undo/Redo, pisah layer di playhead, urutan Maju/Mundur, dan magnet snap
            - Keyframe: panel Properti, ketuk ikon wajik per properti
            - Tambah musik latar dari menu titik tiga di editor
            - Export MP4 720p/1080p ke Movies/KilatMotion

            TIPS
            - Wajik terisi artinya ada keyframe tepat di playhead
            - Pakai preset untuk gerakan siap pakai: Fade, Zoom, Bounce, Typing, Glitch
            - Project tersimpan otomatis; semua tersimpan lokal di HP kamu
        """.trimIndent()
        KilatSheet.showMessage(this, "Panduan KILAT Motion", msg, icon = KilatIcon.BOOK)
    }

    private fun showAbout() {
        KilatSheet.showMessage(
            this, "KILAT Motion",
            "Motion graphics editor native Android.\n\nVersi 1.3.0\nKotlin | Canvas | MediaCodec\nTanpa iklan, tanpa watermark, tanpa library eksternal.\nSeluruh tampilan dan ikon dibuat custom pakai Canvas.",
            icon = KilatIcon.BOLT
        )
    }
}
