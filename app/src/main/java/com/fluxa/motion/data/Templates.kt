package com.fluxa.motion.data

import com.fluxa.motion.core.model.*
import com.fluxa.motion.core.animation.Anim

object Templates {

    fun buildDemo(): Project {
        val p = Project()
        p.name = "Demo Fluxa Motion"
        p.duration = 6f
        p.bg = 0xFF060A12.toInt()

        // glow ungu
        val glowA = Layer().apply {
            kind = KIND_SHAPE; name = "Glow ungu"; shape = "circle"
            w = 980f; h = 980f; fill = 0xFF4B2BBD.toInt()
            x = 210f; y = 1560f; alpha = 0.5f; blend = "screen"; start = 0f; dur = 6f
            fxBlur = 26f; fxGlow = 40f; glowColor = 0xFF7C5CFF.toInt(); fxSaturate = 120f
        }
        Anim.setKey(glowA, "y", 0f, 1560f, "easeInOut")
        Anim.setKey(glowA, "y", 3f, 1420f, "easeInOut")
        Anim.setKey(glowA, "y", 6f, 1560f, "easeInOut")

        // glow teal
        val glowB = Layer().apply {
            kind = KIND_SHAPE; name = "Glow teal"; shape = "circle"
            w = 720f; h = 720f; fill = 0xFF0E7D6B.toInt()
            x = 900f; y = 260f; alpha = 0.55f; blend = "screen"; start = 0f; dur = 6f
            fxBlur = 18f; fxGlow = 30f; glowColor = 0xFF2EE6C0.toInt(); fxSaturate = 130f
        }
        Anim.setKey(glowB, "x", 0f, 900f, "easeInOut")
        Anim.setKey(glowB, "x", 3.2f, 780f, "easeInOut")
        Anim.setKey(glowB, "x", 6f, 900f, "easeInOut")

        // bintang
        val star = Layer().apply {
            kind = KIND_SHAPE; name = "Bintang"; shape = "star"
            w = 260f; h = 260f; fill = 0xFFFFD166.toInt()
            x = 540f; y = 520f; start = 0.1f; dur = 5.9f
            fxGlow = 34f; glowColor = 0xFFFFD166.toInt(); fxBright = 110f; fxSaturate = 120f
        }
        Anim.setKey(star, "rot", 0.1f, -40f, "back")
        Anim.setKey(star, "rot", 1.2f, 8f, "easeInOut")
        Anim.setKey(star, "rot", 3.4f, 368f, "linear")
        Anim.setKey(star, "rot", 6f, 740f, "linear")
        Anim.setKey(star, "scale", 0.1f, 0.1f, "back")
        Anim.setKey(star, "scale", 1.1f, 1f, "easeOut")
        Anim.setKey(star, "y", 0.1f, 620f, "back")
        Anim.setKey(star, "y", 1.2f, 500f, "easeInOut")
        Anim.setKey(star, "y", 3f, 520f, "easeInOut")
        Anim.setKey(star, "y", 5f, 500f, "easeInOut")

        // judul
        val title = Layer().apply {
            kind = KIND_TEXT; name = "Judul Fluxa"; text = "FLUXA"
            fontKey = "sans_black"; textSize = 190f; bold = true; align = "center"
            color = 0xFFFFFFFF.toInt()
            x = 540f; y = 900f; start = 0.25f; dur = 5.75f
            fxGlow = 26f; glowColor = 0xFF8B7CFF.toInt()
        }
        Anim.setKey(title, "y", 0.25f, 1010f, "back")
        Anim.setKey(title, "y", 1.15f, 892f, "easeOut")
        Anim.setKey(title, "alpha", 0.25f, 0f, "easeOut")
        Anim.setKey(title, "alpha", 0.7f, 1f, "easeOut")
        Anim.setKey(title, "scale", 0.25f, 0.92f, "easeOut")
        Anim.setKey(title, "scale", 1.05f, 1f, "easeOut")

        // subjudul (typing)
        val subtitle = Layer().apply {
            kind = KIND_TEXT; name = "MOTION"; text = "MOTION"
            fontKey = "sans_black"; textSize = 118f; align = "center"
            color = 0xFF2EE6C0.toInt(); typing = 0f
            x = 540f; y = 1046f; start = 0.6f; dur = 5.4f
            fxGlow = 30f; glowColor = 0xFF2EE6C0.toInt()
        }
        Anim.setKey(subtitle, "typing", 0.6f, 0f, "linear")
        Anim.setKey(subtitle, "typing", 2.1f, 1f, "linear")

        // tagline
        val tagline = Layer().apply {
            kind = KIND_TEXT; name = "Tagline"; text = "motion editor di Android"
            fontKey = "sans"; textSize = 46f; bold = false; align = "center"
            color = 0xFF9FB0C9.toInt()
            x = 540f; y = 1180f; start = 2.15f; dur = 3.85f
        }
        Anim.setKey(tagline, "alpha", 2.15f, 0f, "easeOut")
        Anim.setKey(tagline, "alpha", 2.8f, 1f, "easeOut")
        Anim.setKey(tagline, "y", 2.15f, 1240f, "easeOut")
        Anim.setKey(tagline, "y", 2.9f, 1180f, "easeOut")

        val code = Layer().apply {
            kind = KIND_TEXT; name = "browser note"; text = "100% native | 0 iklan"
            fontKey = "mono"; textSize = 30f; bold = false; align = "center"
            color = 0xFF5F7593.toInt()
            x = 540f; y = 1740f; start = 2.6f; dur = 3.4f
        }
        Anim.setKey(code, "alpha", 2.6f, 0f, "easeOut")
        Anim.setKey(code, "alpha", 3.2f, 1f, "easeOut")

        p.layers.add(glowA)
        p.layers.add(glowB)
        p.layers.add(star)
        p.layers.add(title)
        p.layers.add(subtitle)
        p.layers.add(tagline)
        p.layers.add(code)
        return p
    }

    fun newProject(name: String = "Project Baru"): Project {
        val p = Project()
        p.name = name
        p.duration = 8f
        return p
    }

    fun newProject(width: Int, height: Int, name: String = "Project Baru"): Project {
        val p = newProject(name)
        p.width = width
        p.height = height
        return p
    }

    // =====================================================================
    // Katalog template (dipakai homescreen)
    // =====================================================================
    data class TemplateInfo(
        val id: String,
        val title: String,
        val desc: String,
        val accent: Int,
        val previewTime: Float,
        val build: () -> Project
    )

    data class RatioInfo(val label: String, val w: Int, val h: Int, val desc: String)

    val RATIOS = listOf(
        RatioInfo("9:16", 1080, 1920, "Vertikal - Reels, TikTok, Shorts"),
        RatioInfo("16:9", 1920, 1080, "Landscape - YouTube"),
        RatioInfo("1:1", 1080, 1080, "Kotak - feed serbaguna"),
        RatioInfo("4:5", 1080, 1350, "Potret feed Instagram"),
        RatioInfo("3:4", 1080, 1440, "Potret klasik")
    )

    val ALL: List<TemplateInfo> = listOf(
        TemplateInfo(
            "jj_reportase", "JJ Reportase", "Kotak judul putih slide masuk, kartu foto miring blur-in tiap hentakan, glitch tipis & kredit by : nama",
            0xFF59A8FF.toInt(), 2.6f
        ) { jjReportase() },
        TemplateInfo(
            "jj_kartu", "JJ Kartu Miring", "Kartu foto ungu, judul tegas, hentakan lebih cepat + glitch & blur-in",
            0xFF9B5DE5.toInt(), 2.6f
        ) { jjKartuMiring() }
    )

    /** Asset audio bawaan per template (di app/src/main/assets). Kalau file tidak ada, template tetap jalan tanpa musik. */
    fun musicAssetFor(id: String): String? = when (id) {
        "jj_reportase" -> "sound/reportase-a.m4a"
        "jj_kartu" -> "sound/reportase-b.m4a"
        else -> null
    }

    fun byId(id: String): TemplateInfo? = ALL.firstOrNull { it.id == id }

    fun createFromTemplate(id: String): Project? {
        val t = byId(id) ?: return null
        val p = t.build()
        p.updatedAt = System.currentTimeMillis()
        return p
    }

    // =====================================================================
    // Builder template (semua gaya Jedag Jedug yang lagi viral)
    // =====================================================================

    /** JJ Reportase: gaya kartu berita viral. Ganti kartu "FOTO KAMU" dan teks judul/kredit dengan punyamu. */
    fun jjReportase(): Project = reportStyle("JJ Reportase", 0xFF0A1424.toInt(), 0xFF2A5C9A.toInt(), 0.4528f, 0.39f, "PENAMPAKAN VIRAL")

    fun jjKartuMiring(): Project = reportStyle("JJ Kartu Miring", 0xFF120A1E.toInt(), 0xFF6A2AB0.toInt(), 0.678f, 0.2f, "KABAR TERBARU")

    /** Gaya kartu berita viral. beat = detik per ketukan, ph = fase pukulan pertama. */
    private fun reportStyle(projName: String, bgColor: Int, blobColor: Int, beat: Float, ph: Float, headline: String): Project {
        val p = Project()
        p.name = projName
        p.duration = 10f
        p.bg = bgColor

        // glow latar berdenyut tiap 2 beat
        val glow = Layer().apply {
            kind = KIND_SHAPE; name = "Glow latar"; shape = "circle"
            w = 1000f; h = 1000f; fill = blobColor
            x = 540f; y = 900f; alpha = 0.55f; blend = "screen"; fxBlur = 60f
            start = 0f; dur = 10f
        }
        Anim.setKey(glow, "scale", 0f, 1f, "linear")
        var gt = ph
        while (gt < 10f) {
            Anim.setKey(glow, "scale", gt, 1.12f, "easeOut")
            Anim.setKey(glow, "scale", minOf(10f, gt + 0.2f), 1f, "easeOut")
            gt += beat * 2f
        }
        p.layers.add(glow)

        // kartu foto: slide naik, lalu tiap 2 beat hentak + miring bergantian + blur-in
        val card = Layer().apply {
            kind = KIND_SHAPE; name = "FOTO KAMU (ganti)"; shape = "rect"
            w = 700f; h = 880f; radius = 40f; fill = 0xFF1E2A3F.toInt()
            shapeStrokeW = 6f; shapeStrokeColor = 0xFFFFFFFF.toInt()
            maskShape = "rrect"
            x = 540f; y = 1000f; start = 0f; dur = 10f
            shadowBlur = 40f; shadowDy = 24f
        }
        Anim.setKey(card, "y", 0f, 1500f, "easeOut")
        Anim.setKey(card, "y", 0.6f, 1000f, "easeOut")
        Anim.setKey(card, "scale", 0f, 1f, "linear")
        Anim.setKey(card, "rot", 0f, 0f, "linear")
        Anim.setKey(card, "fx.blur", 0f, 0f, "linear")
        var bt = ph
        var flip = false
        while (bt < 10f) {
            Anim.setKey(card, "scale", bt, 1.08f, "easeOut")
            Anim.setKey(card, "scale", minOf(10f, bt + 0.14f), 1f, "easeOut")
            Anim.setKey(card, "rot", bt, if (flip) 2.5f else -2.5f, "easeOut")
            Anim.setKey(card, "rot", minOf(10f, bt + 0.18f), 0f, "easeOut")
            Anim.setKey(card, "fx.blur", bt, 18f, "easeOut")
            Anim.setKey(card, "fx.blur", minOf(10f, bt + 0.16f), 0f, "easeOut")
            flip = !flip
            bt += beat * 2f
        }
        p.layers.add(card)

        // kotak judul putih: slide masuk dari kiri, lalu berdenyut tiap beat
        val headBox = Layer().apply {
            kind = KIND_SHAPE; name = "Kotak judul"; shape = "rect"
            w = 940f; h = 190f; radius = 18f; fill = 0xFFFFFFFF.toInt()
            x = 540f; y = 300f; start = 0f; dur = 10f
            shadowBlur = 20f; shadowDy = 8f
        }
        val headTxt = Layer().apply {
            kind = KIND_TEXT; name = "Judul"; text = headline
            fontKey = "sans_black"; textSize = 66f; bold = true; align = "center"
            color = 0xFF0A0A0A.toInt()
            x = 540f; y = 322f; start = 0f; dur = 10f
        }
        for (l in listOf(headBox, headTxt)) {
            Anim.setKey(l, "x", 0f, -700f, "easeOut")
            Anim.setKey(l, "x", 0.5f, 540f, "easeOut")
        }
        var pt = ph
        while (pt < 10f) {
            Anim.setKey(headBox, "scale", pt, 1.04f, "easeOut")
            Anim.setKey(headBox, "scale", minOf(10f, pt + 0.12f), 1f, "easeOut")
            Anim.setKey(headTxt, "scale", pt, 1.04f, "easeOut")
            Anim.setKey(headTxt, "scale", minOf(10f, pt + 0.12f), 1f, "easeOut")
            pt += beat
        }
        p.layers.add(headBox)
        p.layers.add(headTxt)

        // glitch slice tipis, muncul tiap 2 beat dengan geser sebentar
        var g = ph + beat * 2f
        var idx = 0
        while (g < 9.5f) {
            val yy = if (idx % 2 == 0) 520f else 1480f
            val sl = Layer().apply {
                kind = KIND_SHAPE; name = "Glitch ${idx + 1}"; shape = "rect"
                w = 1080f; h = 34f; fill = 0xFFFFFFFF.toInt()
                x = 540f; y = yy; alpha = 0f; blend = "screen"; start = 0f; dur = 10f
            }
            Anim.setKey(sl, "alpha", 0f, 0f, "linear")
            Anim.setKey(sl, "alpha", g - 0.03f, 0f, "linear")
            Anim.setKey(sl, "alpha", g, 0.7f, "linear")
            Anim.setKey(sl, "alpha", g + 0.06f, 0f, "linear")
            Anim.setKey(sl, "x", g, if (idx % 2 == 0) 500f else 580f, "linear")
            Anim.setKey(sl, "x", g + 0.06f, 540f, "linear")
            p.layers.add(sl)
            g += beat * 4f
            idx++
        }

        // kredit bawah: fade in pelan
        val credit = Layer().apply {
            kind = KIND_TEXT; name = "Kredit"; text = "by : NAMA KAMU"
            fontKey = "mono"; textSize = 34f; bold = false; align = "center"
            color = 0xFFBFD0E6.toInt()
            x = 540f; y = 1790f; start = 0f; dur = 10f
        }
        Anim.setKey(credit, "alpha", 0f, 0f, "linear")
        Anim.setKey(credit, "alpha", 0.4f, 0f, "easeOut")
        Anim.setKey(credit, "alpha", 1.0f, 1f, "easeOut")
        p.layers.add(credit)
        // progress bar tipis di atas: tumbuh dari kiri selama 10 detik (scaleX + geser x supaya tepi kiri tetap)
        val progress = Layer().apply {
            kind = KIND_SHAPE; name = "Progress bar"; shape = "rect"
            w = 1080f; h = 8f; fill = 0xFFFFFFFF.toInt(); alpha = 0.9f
            x = 0f; y = 24f; start = 0f; dur = 10f
        }
        Anim.setKey(progress, "scaleX", 0f, 0f, "linear")
        Anim.setKey(progress, "scaleX", 10f, 1f, "linear")
        Anim.setKey(progress, "x", 0f, 0f, "linear")
        Anim.setKey(progress, "x", 10f, 540f, "linear")
        p.layers.add(progress)

        return p
    }
}
