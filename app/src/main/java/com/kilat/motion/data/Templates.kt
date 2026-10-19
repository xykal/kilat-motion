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
            "lower_third", "Lower Third 16:9", "Nama + jabatan meluncur ala siaran TV + badge LIVE",
            0xFF8B7CFF.toInt(), 2.2f
        ) { lowerThird() },
        TemplateInfo(
            "countdown", "Hitung Mundur", "3-2-1 menghentak dengan cincin berdenyut, siap buat teaser",
            0xFF59E3FF.toInt(), 3.6f
        ) { countdown() },
        TemplateInfo(
            "kinetic_type", "Kinetic Type", "Kata-kata gede meluncur bergantian ala iklan modern",
            0xFFFF8A3D.toInt(), 3.4f
        ) { kineticType() },
        TemplateInfo(
            "product_showcase", "Showcase Produk", "Kartu produk + badge harga memantul + CTA, buat jualan",
            0xFFFF5C7A.toInt(), 2.8f
        ) { productShowcase() }
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

        // bintang latar raksasa berputar pelan
        val backdrop = Layer().apply {
            kind = KIND_SHAPE; name = "Bintang latar"; shape = "star"
            w = 1100f; h = 1100f; fill = 0xFF8B7CFF.toInt()
            x = cx; y = cy; alpha = 0.13f; start = 0f; dur = 5f
        }
        Anim.setKey(backdrop, "rot", 0f, 0f, "linear")
        Anim.setKey(backdrop, "rot", 5f, 360f, "linear")
        Anim.setKey(backdrop, "scale", 0f, 0.6f, "easeOut")
        Anim.setKey(backdrop, "scale", 1.4f, 1f, "easeOut")
        // dua bintang aksen di pojok, muncul memantul bergantian
        fun accentStar(name: String, sx: Float, sy: Float, at: Float, col: Int): Layer {
            val s = Layer().apply {
                kind = KIND_SHAPE; this.name = name; shape = "star"
                w = 92f; h = 92f; fill = col
                x = sx; y = sy; start = at; dur = 5f - at
                fxGlow = 18f; glowColor = col
            }
            Anim.setKey(s, "scale", at, 0.1f, "bounce")
            Anim.setKey(s, "scale", at + 0.8f, 1f, "bounce")
            Anim.setKey(s, "rot", at, -30f, "easeOut")
            Anim.setKey(s, "rot", at + 0.8f, 10f, "easeOut")
            return s
        }
        // outro: semua memudar rapi di akhir
        Anim.setKey(title, "alpha", 4.35f, 1f, "easeOut")
        Anim.setKey(title, "alpha", 5f, 0f, "easeOut")
        Anim.setKey(sub, "alpha", 4.35f, 1f, "easeOut")
        Anim.setKey(sub, "alpha", 5f, 0f, "easeOut")
        Anim.setKey(bar, "alpha", 4.35f, 1f, "easeOut")
        Anim.setKey(bar, "alpha", 5f, 0f, "easeOut")

        p.layers.add(backdrop)
        p.layers.add(accentStar("Aksen kiri", 150f, 420f, 0.9f, 0xFFFFD166.toInt()))
        p.layers.add(accentStar("Aksen kanan", 930f, 1500f, 1.25f, 0xFF59E3FF.toInt()))
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

        val halo = Layer().apply {
            kind = KIND_SHAPE; name = "Halo krem"; shape = "circle"
            w = 820f; h = 820f; fill = 0xFFE9DFCE.toInt()
            x = cx; y = cy; start = 0f; dur = 6f
        }
        Anim.setKey(halo, "scale", 0f, 0.5f, "easeOut")
        Anim.setKey(halo, "scale", 1.2f, 1f, "easeOut")
        val mark = Layer().apply {
            kind = KIND_TEXT; name = "Tanda kutip"; text = "\""
            fontKey = "serif_bold"; textSize = 260f; bold = true; align = "center"
            color = 0xFF111319.toInt(); alpha = 0.14f
            x = cx; y = cy - 300f; start = 0f; dur = 6f
        }
        Anim.setKey(mark, "y", 0f, cy - 250f, "easeOut")
        Anim.setKey(mark, "y", 1.4f, cy - 300f, "easeOut")
        Anim.setKey(line, "scale", 0f, 0.05f, "easeOut")
        Anim.setKey(line, "scale", 0.9f, 1f, "back")

        p.layers.add(halo)
        p.layers.add(mark)
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
        // hujan confetti: 8 bentuk warna-warni jatuh + berputar
        val confColors = intArrayOf(
            0xFFFFD166.toInt(), 0xFF59E3FF.toInt(), 0xFF2EE6C0.toInt(), 0xFF8B7CFF.toInt(),
            0xFFFFFFFF.toInt(), 0xFFFF8A3D.toInt(), 0xFFFFD166.toInt(), 0xFF59E3FF.toInt()
        )
        val confShapes = listOf("rect", "circle", "star", "rect", "circle", "rect", "star", "circle")
        for (i in 0 until 8) {
            val at = 0.35f + i * 0.22f
            val sx = 110f + i * 125f
            val size = 26f + (i % 3) * 10f
            val c = Layer().apply {
                kind = KIND_SHAPE; name = "Confetti ${i + 1}"; shape = confShapes[i]
                w = size; h = size * 0.7f; radius = 6f; fill = confColors[i]
                x = sx; y = -60f; start = at; dur = 5f - at
            }
            Anim.setKey(c, "y", at, -60f, "linear")
            Anim.setKey(c, "y", 5f, p.height + 80f, "linear")
            Anim.setKey(c, "x", at, sx, "easeInOut")
            Anim.setKey(c, "x", at + 1.6f, sx + 46f, "easeInOut")
            Anim.setKey(c, "x", 5f, sx - 30f, "easeInOut")
            Anim.setKey(c, "rot", at, 0f, "linear")
            Anim.setKey(c, "rot", 5f, 540f + i * 60f, "linear")
            p.layers.add(c)
        }
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

        val liveBg = Layer().apply {
            kind = KIND_SHAPE; name = "Badge LIVE"; shape = "rect"
            w = 170f; h = 64f; radius = 14f; fill = 0xFFFF3B5C.toInt()
            x = 1780f; y = 92f; start = 0.8f; dur = 5.2f
        }
        Anim.setKey(liveBg, "y", 0.8f, 30f, "back")
        Anim.setKey(liveBg, "y", 1.5f, 92f, "back")
        Anim.setKey(liveBg, "alpha", 1.8f, 1f, "easeOut")
        Anim.setKey(liveBg, "alpha", 2.6f, 0.45f, "easeOut")
        Anim.setKey(liveBg, "alpha", 3.4f, 1f, "easeOut")
        Anim.setKey(liveBg, "alpha", 4.2f, 0.45f, "easeOut")
        Anim.setKey(liveBg, "alpha", 5f, 1f, "easeOut")
        val liveTx = Layer().apply {
            kind = KIND_TEXT; name = "Teks LIVE"; text = "LIVE"
            fontKey = "sans_black"; textSize = 44f; bold = true; align = "center"
            color = 0xFFFFFFFF.toInt()
            x = 1780f; y = 106f; start = 0.8f; dur = 5.2f
        }
        Anim.setKey(liveTx, "y", 0.8f, 44f, "back")
        Anim.setKey(liveTx, "y", 1.5f, 106f, "back")
        val clock = Layer().apply {
            kind = KIND_TEXT; name = "Jam siaran"; text = "19:30 WIB"
            fontKey = "mono"; textSize = 40f; bold = false; align = "center"
            color = 0xFF9FB0C9.toInt()
            x = 1780f; y = 980f; start = 1.6f; dur = 4.4f
        }
        Anim.setKey(clock, "alpha", 1.6f, 0f, "easeOut")
        Anim.setKey(clock, "alpha", 2.2f, 1f, "easeOut")

        p.layers.add(plate)
        p.layers.add(strip)
        p.layers.add(nameT)
        p.layers.add(role)
        p.layers.add(liveBg)
        p.layers.add(liveTx)
        p.layers.add(clock)
        return p
    }

    // =====================================================================
    // Template baru v1.2
    // =====================================================================
    fun countdown(): Project {
        val p = Project()
        p.name = "Hitung Mundur"
        p.duration = 5.5f
        p.bg = 0xFF070B14.toInt()
        val cx = p.width / 2f
        val cy = p.height / 2f - 80f

        val ring = Layer().apply {
            kind = KIND_SHAPE; name = "Cincin denyut"; shape = "circle"
            w = 640f; h = 640f; fill = 0xFF12233D.toInt()
            x = cx; y = cy; start = 0f; dur = 5.5f
            fxGlow = 30f; glowColor = 0xFF59E3FF.toInt()
        }
        var tt = 0f
        while (tt < 3f) {
            Anim.setKey(ring, "scale", tt, 1.22f, "easeOut")
            Anim.setKey(ring, "scale", tt + 0.55f, 1f, "easeOut")
            tt += 1f
        }
        Anim.setKey(ring, "scale", 3f, 1.35f, "back")
        Anim.setKey(ring, "scale", 3.8f, 1.05f, "easeOut")

        val label = Layer().apply {
            kind = KIND_TEXT; name = "Label atas"; text = "BERSIAP..."
            fontKey = "mono"; textSize = 52f; bold = false; align = "center"
            color = 0xFF9FB0C9.toInt(); typing = 0f
            x = cx; y = 330f; start = 0f; dur = 3f
        }
        Anim.setKey(label, "typing", 0f, 0f, "linear")
        Anim.setKey(label, "typing", 1.2f, 1f, "linear")
        Anim.setKey(label, "alpha", 2.7f, 1f, "easeOut")
        Anim.setKey(label, "alpha", 3f, 0f, "easeOut")

        val numbers = listOf("3", "2", "1")
        val numColors = intArrayOf(0xFFFFFFFF.toInt(), 0xFF59E3FF.toInt(), 0xFFFFD166.toInt())
        p.layers.add(ring)
        p.layers.add(label)
        for ((i, n) in numbers.withIndex()) {
            val at = i.toFloat()
            val num = Layer().apply {
                kind = KIND_TEXT; name = "Angka $n"; text = n
                fontKey = "sans_black"; textSize = 360f; bold = true; align = "center"
                color = numColors[i]
                x = cx; y = cy + 120f; start = at; dur = 1f
                fxGlow = 34f; glowColor = numColors[i]
            }
            Anim.setKey(num, "scale", at, 1.8f, "easeOut")
            Anim.setKey(num, "scale", at + 0.4f, 1f, "back")
            Anim.setKey(num, "alpha", at, 0f, "easeOut")
            Anim.setKey(num, "alpha", at + 0.14f, 1f, "easeOut")
            Anim.setKey(num, "alpha", at + 0.82f, 1f, "easeOut")
            Anim.setKey(num, "alpha", at + 1f, 0f, "easeOut")
            p.layers.add(num)
        }

        val go = Layer().apply {
            kind = KIND_TEXT; name = "Mulai"; text = "MULAI!"
            fontKey = "sans_black"; textSize = 230f; bold = true; align = "center"
            color = 0xFF2EE6C0.toInt()
            x = cx; y = cy + 80f; start = 3f; dur = 2.5f
            fxGlow = 40f; glowColor = 0xFF2EE6C0.toInt()
        }
        Anim.setKey(go, "scale", 3f, 0.2f, "bounce")
        Anim.setKey(go, "scale", 4.1f, 1f, "bounce")

        val meter = Layer().apply {
            kind = KIND_SHAPE; name = "Bar waktu"; shape = "rect"
            w = 680f; h = 16f; radius = 8f; fill = 0xFFFFD166.toInt()
            x = cx; y = 1620f; start = 0f; dur = 3f
        }
        Anim.setKey(meter, "scale", 0f, 1f, "linear")
        Anim.setKey(meter, "scale", 3f, 0.04f, "linear")

        p.layers.add(go)
        p.layers.add(meter)
        return p
    }

    fun kineticType(): Project {
        val p = Project()
        p.name = "Kinetic Type"
        p.duration = 6f
        p.bg = 0xFF0E0E12.toInt()
        val cx = p.width / 2f

        // garis diagonal latar bergerak pelan
        for (i in 0 until 3) {
            val stripe = Layer().apply {
                kind = KIND_SHAPE; name = "Garis latar ${i + 1}"; shape = "rect"
                w = 1500f; h = 90f; fill = 0xFFFFFFFF.toInt()
                x = cx; y = 420f + i * 520f; rot = 24f; alpha = 0.06f
                start = 0f; dur = 6f
            }
            Anim.setKey(stripe, "x", 0f, cx - 90f, "linear")
            Anim.setKey(stripe, "x", 6f, cx + 90f, "linear")
            p.layers.add(stripe)
        }

        val words = listOf(
            Triple("BIKIN", 0xFFFFFFFF.toInt(), 700f),
            Triple("KONTEN", 0xFF2EE6C0.toInt(), 930f),
            Triple("VIRAL", 0xFFFFD166.toInt(), 1160f)
        )
        for ((i, wd) in words.withIndex()) {
            val at = 0.25f + i * 0.95f
            val fromLeft = i % 2 == 0
            val word = Layer().apply {
                kind = KIND_TEXT; name = "Kata ${wd.first}"; text = wd.first
                fontKey = "sans_black"; textSize = 200f; bold = true; align = "center"
                color = wd.second
                x = cx; y = wd.third; start = at; dur = 6f - at
            }
            Anim.setKey(word, "x", at, if (fromLeft) -520f else 1600f, "easeOut")
            Anim.setKey(word, "x", at + 0.75f, cx, "back")
            Anim.setKey(word, "rot", at, if (fromLeft) -5f else 5f, "easeOut")
            Anim.setKey(word, "rot", at + 0.75f, 0f, "easeOut")
            val bar = Layer().apply {
                kind = KIND_SHAPE; name = "Bar ${wd.first}"; shape = "rect"
                w = 560f; h = 18f; radius = 9f; fill = wd.second
                x = cx; y = wd.third + 78f; start = at + 0.18f; dur = 6f - at - 0.18f
            }
            Anim.setKey(bar, "scale", at + 0.18f, 0.04f, "easeOut")
            Anim.setKey(bar, "scale", at + 0.85f, 1f, "back")
            p.layers.add(word)
            p.layers.add(bar)
        }

        val tag = Layer().apply {
            kind = KIND_TEXT; name = "Hashtag"; text = "#fyp  #motion  #edit"
            fontKey = "mono"; textSize = 46f; bold = false; align = "center"
            color = 0xFF9FB0C9.toInt(); typing = 0f
            x = cx; y = 1420f; start = 3.3f; dur = 2.7f
        }
        Anim.setKey(tag, "typing", 3.3f, 0f, "linear")
        Anim.setKey(tag, "typing", 5.2f, 1f, "linear")
        p.layers.add(tag)
        return p
    }

    fun productShowcase(): Project {
        val p = Project()
        p.name = "Showcase Produk"
        p.width = 1080
        p.height = 1350
        p.duration = 6f
        p.bg = 0xFFF6EFE6.toInt()
        val cx = p.width / 2f

        val sun = Layer().apply {
            kind = KIND_SHAPE; name = "Bintang mentari"; shape = "star"
            w = 980f; h = 980f; fill = 0xFFFFD9C7.toInt()
            x = cx; y = 600f; alpha = 0.75f; start = 0f; dur = 6f
        }
        Anim.setKey(sun, "rot", 0f, 0f, "linear")
        Anim.setKey(sun, "rot", 6f, 180f, "linear")
        Anim.setKey(sun, "scale", 0f, 0.5f, "easeOut")
        Anim.setKey(sun, "scale", 1.1f, 1f, "easeOut")

        val card = Layer().apply {
            kind = KIND_SHAPE; name = "Kartu produk"; shape = "rect"
            w = 580f; h = 650f; radius = 44f; fill = 0xFFFFFFFF.toInt()
            x = cx; y = 610f; start = 0.2f; dur = 5.8f
            fxGlow = 22f; glowColor = 0xFFFFB59E.toInt()
        }
        Anim.setKey(card, "scale", 0.2f, 0.3f, "bounce")
        Anim.setKey(card, "scale", 1.5f, 1f, "bounce")
        Anim.setKey(card, "y", 0.2f, 720f, "easeOut")
        Anim.setKey(card, "y", 1.2f, 600f, "easeOut")
        Anim.setKey(card, "y", 3.4f, 622f, "easeInOut")
        Anim.setKey(card, "y", 6f, 600f, "easeInOut")

        val prod = Layer().apply {
            kind = KIND_TEXT; name = "Nama produk"; text = "PRODUK\nBARU"
            fontKey = "sans_black"; textSize = 150f; bold = true; align = "center"
            color = 0xFF2B2118.toInt()
            x = cx; y = 610f; start = 0.7f; dur = 5.3f
        }
        Anim.setKey(prod, "y", 0.7f, 700f, "back")
        Anim.setKey(prod, "y", 1.6f, 592f, "back")
        Anim.setKey(prod, "y", 3.4f, 614f, "easeInOut")
        Anim.setKey(prod, "y", 6f, 592f, "easeInOut")
        Anim.setKey(prod, "alpha", 0.7f, 0f, "easeOut")
        Anim.setKey(prod, "alpha", 1.1f, 1f, "easeOut")

        val badge = Layer().apply {
            kind = KIND_SHAPE; name = "Badge harga"; shape = "circle"
            w = 205f; h = 205f; fill = 0xFFFF5C7A.toInt()
            x = 845f; y = 330f; rot = -12f; start = 1.1f; dur = 4.9f
            fxGlow = 20f; glowColor = 0xFFFF5C7A.toInt()
        }
        Anim.setKey(badge, "scale", 1.1f, 0.1f, "bounce")
        Anim.setKey(badge, "scale", 2.2f, 1f, "bounce")
        val price = Layer().apply {
            kind = KIND_TEXT; name = "Harga"; text = "199K"
            fontKey = "sans_black"; textSize = 84f; bold = true; align = "center"
            color = 0xFFFFFFFF.toInt(); rot = -12f
            x = 845f; y = 358f; start = 1.1f; dur = 4.9f
        }
        Anim.setKey(price, "scale", 1.1f, 0.1f, "bounce")
        Anim.setKey(price, "scale", 2.2f, 1f, "bounce")

        val ctaBg = Layer().apply {
            kind = KIND_SHAPE; name = "Tombol CTA"; shape = "rect"
            w = 600f; h = 104f; radius = 52f; fill = 0xFF0E7D6B.toInt()
            x = cx; y = 1135f; start = 1.7f; dur = 4.3f
        }
        Anim.setKey(ctaBg, "y", 1.7f, 1260f, "back")
        Anim.setKey(ctaBg, "y", 2.5f, 1135f, "back")
        Anim.setKey(ctaBg, "alpha", 1.7f, 0f, "easeOut")
        Anim.setKey(ctaBg, "alpha", 2f, 1f, "easeOut")
        val ctaTx = Layer().apply {
            kind = KIND_TEXT; name = "Teks CTA"; text = "BELI SEKARANG"
            fontKey = "sans_black"; textSize = 56f; bold = true; align = "center"
            color = 0xFFFFFFFF.toInt()
            x = cx; y = 1154f; start = 1.7f; dur = 4.3f
        }
        Anim.setKey(ctaTx, "y", 1.7f, 1279f, "back")
        Anim.setKey(ctaTx, "y", 2.5f, 1154f, "back")
        Anim.setKey(ctaTx, "alpha", 1.7f, 0f, "easeOut")
        Anim.setKey(ctaTx, "alpha", 2f, 1f, "easeOut")

        val brand = Layer().apply {
            kind = KIND_TEXT; name = "Brand"; text = "@tokokamu"
            fontKey = "mono"; textSize = 42f; bold = false; align = "center"
            color = 0xFF8A7A68.toInt()
            x = cx; y = 130f; start = 2.2f; dur = 3.8f
        }
        Anim.setKey(brand, "alpha", 2.2f, 0f, "easeOut")
        Anim.setKey(brand, "alpha", 2.8f, 1f, "easeOut")

        p.layers.add(sun)
        p.layers.add(card)
        p.layers.add(prod)
        // kilau bintang kecil bermunculan bergantian
        val sparkPos = listOf(180f to 260f, 910f to 620f, 230f to 980f, 880f to 1020f)
        for ((i, pos) in sparkPos.withIndex()) {
            val at = 2.4f + i * 0.45f
            val sp = Layer().apply {
                kind = KIND_SHAPE; name = "Kilau ${i + 1}"; shape = "star"
                w = 64f; h = 64f; fill = 0xFFFFB020.toInt()
                x = pos.first; y = pos.second; start = at; dur = 6f - at
                fxGlow = 16f; glowColor = 0xFFFFB020.toInt()
            }
            Anim.setKey(sp, "scale", at, 0.1f, "back")
            Anim.setKey(sp, "scale", at + 0.55f, 1f, "back")
            Anim.setKey(sp, "rot", at, 0f, "linear")
            Anim.setKey(sp, "rot", 6f, 220f, "linear")
            p.layers.add(sp)
        }
        p.layers.add(badge)
        p.layers.add(price)
        p.layers.add(ctaBg)
        p.layers.add(ctaTx)
        p.layers.add(brand)
        return p
    }
}
