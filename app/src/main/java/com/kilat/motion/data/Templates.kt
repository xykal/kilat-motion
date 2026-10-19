package com.kilat.motion.data

import com.kilat.motion.core.model.*
import com.kilat.motion.core.animation.Anim

object Templates {

    fun buildDemo(): Project {
        val p = Project()
        p.name = "Demo Kilat Motion"
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
            kind = KIND_TEXT; name = "Judul KILAT"; text = "KILAT"
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
            "demo_kilat", "Intro KILAT", "Logo reveal neon + typing + bintang berputar",
            0xFF2EE6C0.toInt(), 2.6f
        ) { buildDemo() },
        TemplateInfo(
            "intro_bold", "Intro Bold", "Judul gede masuk menghentak, cocok buat opener",
            0xFFFFD166.toInt(), 1.6f
        ) { introBold() },
        TemplateInfo(
            "quote_minimal", "Quote Minimal", "Kutipan bersih 1:1 dengan efek mengetik",
            0xFF59E3FF.toInt(), 3.2f
        ) { quoteMinimal() },
        TemplateInfo(
            "promo_pop", "Promo Pop", "Diskon meletup warna-warni buat jualan",
            0xFFFF5C7A.toInt(), 1.8f
        ) { promoPop() },
        TemplateInfo(
            "lower_third", "Lower Third 16:9", "Nama + jabatan meluncur ala siaran TV",
            0xFF8B7CFF.toInt(), 2.2f
        ) { lowerThird() }
    )

    fun byId(id: String): TemplateInfo? = ALL.firstOrNull { it.id == id }

    fun createFromTemplate(id: String): Project? {
        val t = byId(id) ?: return null
        val p = t.build()
        p.updatedAt = System.currentTimeMillis()
        return p
    }

    // =====================================================================
    // Builder template lain
    // =====================================================================
    fun introBold(): Project {
        val p = Project()
        p.name = "Intro Bold"
        p.duration = 5f
        p.bg = 0xFF0A0A0A.toInt()
        val cx = p.width / 2f
        val cy = p.height / 2f

        val bar = Layer().apply {
            kind = KIND_SHAPE; name = "Bar aksen"; shape = "rect"
            w = 620f; h = 26f; radius = 13f; fill = 0xFFFFD166.toInt()
            x = cx; y = cy + 150f; start = 0f; dur = 5f
        }
        Anim.setKey(bar, "scale", 0.5f, 0.02f, "easeOut")
        Anim.setKey(bar, "scale", 1.15f, 1f, "back")
        Anim.setKey(bar, "alpha", 0.5f, 0f, "easeOut")
        Anim.setKey(bar, "alpha", 0.8f, 1f, "easeOut")

        val title = Layer().apply {
            kind = KIND_TEXT; name = "Judul"; text = "HALO\nDUNIA"
            fontKey = "sans_black"; textSize = 230f; bold = true; align = "center"
            color = 0xFFFFFFFF.toInt()
            x = cx; y = cy; start = 0f; dur = 5f
        }
        Anim.setKey(title, "scale", 0f, 1.7f, "easeOut")
        Anim.setKey(title, "scale", 0.85f, 1f, "back")
        Anim.setKey(title, "alpha", 0f, 0f, "easeOut")
        Anim.setKey(title, "alpha", 0.35f, 1f, "easeOut")

        val sub = Layer().apply {
            kind = KIND_TEXT; name = "Subjudul"; text = "selamat datang di channel ini"
            fontKey = "sans"; textSize = 52f; bold = false; align = "center"
            color = 0xFFFFD166.toInt()
            x = cx; y = cy + 260f; start = 1f; dur = 4f
        }
        Anim.setKey(sub, "y", 1f, cy + 320f, "easeOut")
        Anim.setKey(sub, "y", 1.7f, cy + 260f, "easeOut")
        Anim.setKey(sub, "alpha", 1f, 0f, "easeOut")
        Anim.setKey(sub, "alpha", 1.45f, 1f, "easeOut")

        p.layers.add(bar)
        p.layers.add(title)
        p.layers.add(sub)
        return p
    }

    fun quoteMinimal(): Project {
        val p = Project()
        p.name = "Quote Minimal"
        p.width = 1080
        p.height = 1080
        p.duration = 6f
        p.bg = 0xFFF4F1EA.toInt()
        val cx = p.width / 2f
        val cy = p.height / 2f

        val line = Layer().apply {
            kind = KIND_SHAPE; name = "Garis"; shape = "rect"
            w = 110f; h = 10f; radius = 5f; fill = 0xFF111319.toInt()
            x = cx; y = cy - 190f; start = 0f; dur = 6f
        }
        val quote = Layer().apply {
            kind = KIND_TEXT; name = "Kutipan"; text = "kecil-kecil\nkonsisten,\nbesar hasilnya."
            fontKey = "serif_bold"; textSize = 104f; bold = true; align = "center"
            color = 0xFF111319.toInt(); typing = 0f
            x = cx; y = cy + 30f; start = 0.2f; dur = 5.8f
        }
        Anim.setKey(quote, "typing", 0.2f, 0f, "linear")
        Anim.setKey(quote, "typing", 4.2f, 1f, "linear")
        val author = Layer().apply {
            kind = KIND_TEXT; name = "Penulis"; text = "@namakamu"
            fontKey = "mono"; textSize = 42f; bold = false; align = "center"
            color = 0xFF687389.toInt()
            x = cx; y = cy + 330f; start = 4.2f; dur = 1.8f
        }
        Anim.setKey(author, "alpha", 4.2f, 0f, "easeOut")
        Anim.setKey(author, "alpha", 4.8f, 1f, "easeOut")

        p.layers.add(line)
        p.layers.add(quote)
        p.layers.add(author)
        return p
    }

    fun promoPop(): Project {
        val p = Project()
        p.name = "Promo Pop"
        p.width = 1080
        p.height = 1350
        p.duration = 5f
        p.bg = 0xFF2B0F1A.toInt()
        val cx = p.width / 2f
        val cy = p.height / 2f

        val burst = Layer().apply {
            kind = KIND_SHAPE; name = "Ledakan"; shape = "star"
            w = 980f; h = 980f; fill = 0xFFFF5C7A.toInt()
            x = cx; y = cy - 60f; start = 0f; dur = 5f
        }
        Anim.setKey(burst, "scale", 0f, 0.1f, "back")
        Anim.setKey(burst, "scale", 0.9f, 1f, "back")
        Anim.setKey(burst, "rot", 0f, 0f, "linear")
        Anim.setKey(burst, "rot", 5f, 360f, "linear")

        val off = Layer().apply {
            kind = KIND_TEXT; name = "Diskon"; text = "DISKON\n50%"
            fontKey = "sans_black"; textSize = 220f; bold = true; align = "center"
            color = 0xFFFFFFFF.toInt()
            x = cx; y = cy - 60f; start = 0.3f; dur = 4.7f
        }
        Anim.setKey(off, "scale", 0.3f, 0.2f, "bounce")
        Anim.setKey(off, "scale", 1.4f, 1f, "bounce")

        val cta = Layer().apply {
            kind = KIND_TEXT; name = "CTA"; text = "HANYA HARI INI - GAS CHECKOUT"
            fontKey = "sans_black"; textSize = 56f; bold = true; align = "center"
            color = 0xFFFFD166.toInt()
            x = cx; y = p.height - 150f; start = 1.2f; dur = 3.8f
        }
        Anim.setKey(cta, "alpha", 1.2f, 0f, "easeOut")
        Anim.setKey(cta, "alpha", 1.6f, 1f, "easeOut")
        Anim.setKey(cta, "alpha", 2.2f, 0.25f, "easeOut")
        Anim.setKey(cta, "alpha", 2.8f, 1f, "easeOut")
        Anim.setKey(cta, "alpha", 3.6f, 0.25f, "easeOut")
        Anim.setKey(cta, "alpha", 4.2f, 1f, "easeOut")

        p.layers.add(burst)
        p.layers.add(off)
        p.layers.add(cta)
        return p
    }

    fun lowerThird(): Project {
        val p = Project()
        p.name = "Lower Third"
        p.width = 1920
        p.height = 1080
        p.duration = 6f
        p.bg = 0xFF101828.toInt()

        val plate = Layer().apply {
            kind = KIND_SHAPE; name = "Plat nama"; shape = "rect"
            w = 1040f; h = 170f; radius = 18f; fill = 0xFF0A0C11.toInt()
            x = 620f; y = 900f; start = 0.4f; dur = 5.6f
            fxGlow = 18f; glowColor = 0xFF8B7CFF.toInt()
        }
        Anim.setKey(plate, "x", 0.4f, -460f, "easeOut")
        Anim.setKey(plate, "x", 1.4f, 620f, "easeOut")

        val strip = Layer().apply {
            kind = KIND_SHAPE; name = "Strip aksen"; shape = "rect"
            w = 22f; h = 170f; radius = 8f; fill = 0xFF8B7CFF.toInt()
            x = 112f; y = 900f; start = 0.4f; dur = 5.6f
        }
        Anim.setKey(strip, "x", 0.4f, -968f, "easeOut")
        Anim.setKey(strip, "x", 1.4f, 112f, "easeOut")

        val nameT = Layer().apply {
            kind = KIND_TEXT; name = "Nama"; text = "NAMA KAMU"
            fontKey = "sans_black"; textSize = 92f; bold = true; align = "left"
            color = 0xFFFFFFFF.toInt()
            x = 660f; y = 872f; start = 0.9f; dur = 5.1f
        }
        Anim.setKey(nameT, "x", 0.9f, 540f, "easeOut")
        Anim.setKey(nameT, "x", 1.8f, 660f, "easeOut")
        Anim.setKey(nameT, "alpha", 0.9f, 0f, "easeOut")
        Anim.setKey(nameT, "alpha", 1.4f, 1f, "easeOut")

        val role = Layer().apply {
            kind = KIND_TEXT; name = "Jabatan"; text = "Motion Designer"
            fontKey = "sans"; textSize = 52f; bold = false; align = "left"
            color = 0xFF9FB0C9.toInt()
            x = 660f; y = 952f; start = 1.2f; dur = 4.8f
        }
        Anim.setKey(role, "x", 1.2f, 560f, "easeOut")
        Anim.setKey(role, "x", 2.1f, 660f, "easeOut")
        Anim.setKey(role, "alpha", 1.2f, 0f, "easeOut")
        Anim.setKey(role, "alpha", 1.8f, 1f, "easeOut")

        p.layers.add(plate)
        p.layers.add(strip)
        p.layers.add(nameT)
        p.layers.add(role)
        return p
    }
}
