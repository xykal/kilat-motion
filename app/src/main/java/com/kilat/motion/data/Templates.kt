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
            "jj_velocity", "JJ Velocity", "Speed ramp + balik 3D + motion blur ala preset velocity TikTok",
            0xFF00E5FF.toInt(), 2.6f
        ) { jjVelocity() },
        TemplateInfo(
            "jj_lirik", "JJ Lirik DJ", "Kata per kata menghentak ikuti beat + garis karaoke, tinggal ganti liriknya",
            0xFFFFD166.toInt(), 2.5f
        ) { jjLirik() },
        TemplateInfo(
            "jj_beat130", "JJ Jedag Jedug 130", "Kotak foto menghentak tiap beat 130 BPM + flash - ganti dengan fotomu",
            0xFFFF2D78.toInt(), 1.9f
        ) { jjBeat130() },
        TemplateInfo(
            "jj_soft", "JJ Soft Spoken", "Pastel lembut + light leak + hentakan halus ala preset soft spoken",
            0xFFFFB3C7.toInt(), 3.2f
        ) { jjSoft() },
        TemplateInfo(
            "jj_tipis", "JJ Tipis", "Hentakan super tipis & bersih, favorit buat foto aesthetic",
            0xFF2EE6C0.toInt(), 2.2f
        ) { jjTipis() },
        TemplateInfo(
            "jj_serem", "JJ Kece Serem", "Gelap merah, getar glitch & kedip hitam ala sound serem viral",
            0xFFD90429.toInt(), 1.7f
        ) { jjSerem() }
    )

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

    /** JJ Velocity: speed ramp (pelan -> menghentak), flip 3D, motion blur sesaat, kubus 3D berputar. */
    fun jjVelocity(): Project {
        val p = Project()
        p.name = "JJ Velocity"
        p.duration = 7f
        p.bg = 0xFF05060A.toInt()

        // garis kecepatan menyapu latar
        val lineCols = intArrayOf(0xFF00E5FF.toInt(), 0xFF8B7CFF.toInt(), 0xFF2EE6C0.toInt())
        for (li in 0 until 6) {
            val lineY = 300f + li * 260f
            val spd = Layer().apply {
                kind = KIND_SHAPE; name = "Garis cepat ${li + 1}"; shape = "rect"
                w = 520f + li * 70f; h = 9f; radius = 4f
                fill = lineCols[li % lineCols.size]
                x = 540f; y = lineY; alpha = 0.4f; blend = "screen"
                start = 0f; dur = 7f; fxBlur = 3f
            }
            val dirSign = if (li % 2 == 0) 1f else -1f
            Anim.setKey(spd, "x", 0f, 540f - 700f * dirSign, "linear")
            Anim.setKey(spd, "x", 3.5f, 540f + 700f * dirSign, "linear")
            Anim.setKey(spd, "x", 3.53f, 540f - 700f * dirSign, "linear")
            Anim.setKey(spd, "x", 7f, 540f + 700f * dirSign, "linear")
            p.layers.add(spd)
        }

        // teks utama: ramp pelan lalu menghentak (kurva bezier), flip 3D dua kali
        val title = Layer().apply {
            kind = KIND_TEXT; name = "VELOCITY"; text = "VELOCITY"
            fontKey = "sans_black"; textSize = 172f; bold = true; align = "center"
            color = 0xFFFFFFFF.toInt()
            x = 540f; y = 860f; start = 0f; dur = 7f
            fxGlow = 26f; glowColor = 0xFF00E5FF.toInt()
            shadowBlur = 16f; shadowDx = 0f; shadowDy = 12f
            bezX1 = 0.2f; bezY1 = 1.6f; bezX2 = 0.4f; bezY2 = 1f
        }
        Anim.setKey(title, "scale", 0f, 0.55f, "easeInOut")
        Anim.setKey(title, "scale", 0.85f, 0.72f, "easeIn")
        Anim.setKey(title, "scale", 1.05f, 1.28f, "custom")
        Anim.setKey(title, "scale", 1.5f, 1f, "easeOut")
        Anim.setKey(title, "scale", 3.4f, 1.2f, "custom")
        Anim.setKey(title, "scale", 3.75f, 1f, "easeOut")
        Anim.setKey(title, "scale", 5.6f, 1.24f, "custom")
        Anim.setKey(title, "scale", 6f, 1f, "easeOut")
        Anim.setKey(title, "rotY", 2.1f, 0f, "easeInOut")
        Anim.setKey(title, "rotY", 2.9f, 180f, "easeInOut")
        Anim.setKey(title, "rotY", 4.7f, 180f, "easeInOut")
        Anim.setKey(title, "rotY", 5.5f, 360f, "easeInOut")
        Anim.setKey(title, "fx.blur", 2.1f, 0f, "easeInOut")
        Anim.setKey(title, "fx.blur", 2.5f, 16f, "easeInOut")
        Anim.setKey(title, "fx.blur", 2.9f, 0f, "easeInOut")
        Anim.setKey(title, "fx.blur", 4.7f, 0f, "easeInOut")
        Anim.setKey(title, "fx.blur", 5.1f, 16f, "easeInOut")
        Anim.setKey(title, "fx.blur", 5.5f, 0f, "easeInOut")
        Anim.setKey(title, "alpha", 0f, 0f, "easeOut")
        Anim.setKey(title, "alpha", 0.3f, 1f, "easeOut")
        p.layers.add(title)

        val sub = Layer().apply {
            kind = KIND_TEXT; name = "Sub velocity"; text = "speed ramp x jedag jedug"
            fontKey = "mono"; textSize = 44f; bold = false; align = "center"
            color = 0xFF00E5FF.toInt(); typing = 0f
            x = 540f; y = 1060f; start = 1.2f; dur = 5.8f
        }
        Anim.setKey(sub, "typing", 1.2f, 0f, "linear")
        Anim.setKey(sub, "typing", 2.6f, 1f, "linear")
        p.layers.add(sub)

        // kubus 3D berputar di bawah
        val cube = Layer().apply {
            kind = KIND_3D; name = "Kubus 3D"; objKind = "cube"
            w = 300f; h = 300f; fill = 0xFF8B7CFF.toInt()
            x = 540f; y = 1460f; start = 0.3f; dur = 6.7f
            rotX = 24f; fxGlow = 18f; glowColor = 0xFF8B7CFF.toInt()
        }
        Anim.setKey(cube, "rotY", 0.3f, -40f, "linear")
        Anim.setKey(cube, "rotY", 7f, 680f, "linear")
        Anim.setKey(cube, "alpha", 0.3f, 0f, "easeOut")
        Anim.setKey(cube, "alpha", 0.9f, 1f, "easeOut")
        Anim.setKey(cube, "y", 0.3f, 1520f, "easeInOut")
        Anim.setKey(cube, "y", 3.6f, 1420f, "easeInOut")
        Anim.setKey(cube, "y", 7f, 1520f, "easeInOut")
        p.layers.add(cube)

        // flash di titik hentakan
        val flash = Layer().apply {
            kind = KIND_SHAPE; name = "Flash ramp"; shape = "rect"
            w = 1080f; h = 1920f; fill = 0xFFFFFFFF.toInt()
            x = 540f; y = 960f; alpha = 0f; start = 0f; dur = 7f
        }
        for (ft in floatArrayOf(1.05f, 3.4f, 5.6f)) {
            Anim.setKey(flash, "alpha", ft, 0.45f, "linear")
            Anim.setKey(flash, "alpha", ft + 0.09f, 0f, "linear")
        }
        p.layers.add(flash)

        val note = Layer().apply {
            kind = KIND_TEXT; name = "Catatan"; text = "ganti teksnya, pasang sound velocity favoritmu"
            fontKey = "mono"; textSize = 32f; bold = false; align = "center"
            color = 0xFF8FA1BD.toInt()
            x = 540f; y = 1760f; start = 0f; dur = 7f
        }
        Anim.setKey(note, "alpha", 0.5f, 0f, "easeOut")
        Anim.setKey(note, "alpha", 1.4f, 1f, "easeOut")
        p.layers.add(note)
        return p
    }

    /** JJ Lirik: kata per kata menghentak + garis karaoke mengetik + progress bar. */
    fun jjLirik(): Project {
        val p = Project()
        p.name = "JJ Lirik DJ"
        p.duration = 9.6f
        p.bg = 0xFF0A0A12.toInt()
        val segLen = 1.2f

        val halo = Layer().apply {
            kind = KIND_SHAPE; name = "Halo latar"; shape = "circle"
            w = 1000f; h = 1000f; fill = 0xFF2A1A5E.toInt()
            x = 540f; y = 800f; alpha = 0.55f; blend = "screen"; fxBlur = 24f
            start = 0f; dur = 9.6f
        }
        p.layers.add(halo)

        val words = listOf("DJ", "BIKIN", "GOYANG", "SAMPAI", "PAGI", "JEDAG", "JEDUG", "GAS!")
        val wordCols = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF2EE6C0.toInt(), 0xFFFFD166.toInt(), 0xFF59E3FF.toInt(),
            0xFFFF8FAB.toInt(), 0xFFFFFFFF.toInt(), 0xFF2EE6C0.toInt(), 0xFFFFD166.toInt()
        )
        for (wi in words.indices) {
            val segStart = wi * segLen
            val wordCol = wordCols[wi]
            val wl = Layer().apply {
                kind = KIND_TEXT; name = "Kata ${words[wi]}"; text = words[wi]
                fontKey = "sans_black"; textSize = 215f; bold = true; align = "center"
                color = wordCol
                x = 540f; y = 820f; start = segStart; dur = segLen
                fxGlow = 20f; glowColor = wordCol
                shadowBlur = 14f; shadowDy = 10f
                bezX1 = 0.2f; bezY1 = 1.6f; bezX2 = 0.4f; bezY2 = 1f
            }
            Anim.setKey(wl, "scale", segStart, 1.34f, "custom")
            Anim.setKey(wl, "scale", segStart + 0.16f, 1f, "easeOut")
            Anim.setKey(wl, "scale", segStart + 0.6f, 1.14f, "custom")
            Anim.setKey(wl, "scale", segStart + 0.74f, 1f, "easeOut")
            Anim.setKey(wl, "rot", segStart, if (wi % 2 == 0) -3.5f else 3.5f, "easeOut")
            Anim.setKey(wl, "rot", segStart + 0.25f, 0f, "easeOut")
            Anim.setKey(wl, "y", segStart, 880f, "custom")
            Anim.setKey(wl, "y", segStart + 0.3f, 820f, "easeOut")
            p.layers.add(wl)
        }

        // coretan zigzag di bawah kata (layer vektor)
        val zig = StringBuilder()
        var zx = -350f
        var up = true
        while (zx <= 350f) {
            if (zig.isNotEmpty()) zig.append(';')
            zig.append(zx.toInt()).append(',').append(if (up) -16 else 16)
            up = !up
            zx += 50f
        }
        val scribble = Layer().apply {
            kind = KIND_VECTOR; name = "Coretan beat"
            vecPoints = zig.toString(); vecStrokeW = 11f
            color = 0xFFFF2D78.toInt()
            x = 540f; y = 1000f; w = 700f; h = 40f
            start = 0f; dur = 9.6f
        }
        var beatT = 0f
        while (beatT < 9.6f) {
            Anim.setKey(scribble, "scale", beatT, 1.16f, "easeOut")
            Anim.setKey(scribble, "scale", minOf(9.6f, beatT + 0.18f), 1f, "easeOut")
            beatT += segLen
        }
        p.layers.add(scribble)

        // garis karaoke
        val karaoke = Layer().apply {
            kind = KIND_TEXT; name = "Karaoke"; text = "dj bikin goyang sampai pagi..."
            fontKey = "mono"; textSize = 46f; bold = true; align = "center"
            color = 0xFF2EE6C0.toInt(); typing = 0f
            x = 540f; y = 1580f; start = 0f; dur = 9.6f
        }
        Anim.setKey(karaoke, "typing", 0f, 0f, "linear")
        Anim.setKey(karaoke, "typing", 7.2f, 1f, "linear")
        p.layers.add(karaoke)

        // progress bar tumbuh dari tengah mengikuti lagu
        val barBg = Layer().apply {
            kind = KIND_SHAPE; name = "Bar latar"; shape = "rect"
            w = 880f; h = 14f; radius = 7f; fill = 0xFF232838.toInt()
            x = 540f; y = 1680f; start = 0f; dur = 9.6f
        }
        p.layers.add(barBg)
        val barFill = Layer().apply {
            kind = KIND_SHAPE; name = "Bar jalan"; shape = "rect"
            w = 880f; h = 14f; radius = 7f; fill = 0xFF2EE6C0.toInt()
            x = 540f; y = 1680f; start = 0f; dur = 9.6f
        }
        Anim.setKey(barFill, "scaleX", 0f, 0.01f, "linear")
        Anim.setKey(barFill, "scaleX", 9.6f, 1f, "linear")
        p.layers.add(barFill)

        val top = Layer().apply {
            kind = KIND_TEXT; name = "Label"; text = "LIRIK - GANTI KATA-KATANYA"
            fontKey = "mono"; textSize = 32f; bold = true; align = "center"
            color = 0xFF8FA1BD.toInt()
            x = 540f; y = 200f; start = 0f; dur = 9.6f
        }
        Anim.setKey(top, "alpha", 0f, 0f, "easeOut")
        Anim.setKey(top, "alpha", 0.8f, 1f, "easeOut")
        p.layers.add(top)
        return p
    }

    /** JJ Jedag Jedug 130 BPM: kotak foto (mask kotak lembut) menghentak tiap beat + flash. */
    fun jjBeat130(): Project {
        val p = Project()
        p.name = "JJ Jedag Jedug 130"
        p.duration = 8f
        p.bg = 0xFF07070B.toInt()
        val beat = 0.462f // 130 BPM

        val pulse = Layer().apply {
            kind = KIND_SHAPE; name = "Denyut latar"; shape = "circle"
            w = 980f; h = 980f; fill = 0xFF3B0F2E.toInt()
            x = 540f; y = 880f; alpha = 0.8f; blend = "screen"; fxBlur = 20f
            start = 0f; dur = 8f
        }
        var pt = 0f
        while (pt < 8f) {
            Anim.setKey(pulse, "scale", pt, 1.16f, "easeOut")
            Anim.setKey(pulse, "scale", minOf(8f, pt + 0.2f), 1f, "easeOut")
            pt += beat * 2f
        }
        p.layers.add(pulse)

        fun punchBeat(l: Layer, amp: Float, stepBeats: Int) {
            var bt = 0f
            var flip = false
            while (bt < 8f) {
                Anim.setKey(l, "scale", bt, amp, "custom")
                Anim.setKey(l, "scale", minOf(8f, bt + 0.15f), 1f, "easeOut")
                Anim.setKey(l, "rot", bt, if (flip) 1.6f else -1.6f, "easeOut")
                Anim.setKey(l, "rot", minOf(8f, bt + 0.2f), 0f, "easeOut")
                flip = !flip
                bt += beat * stepBeats
            }
        }

        // kotak foto: ganti layer ini dengan foto asli (mask siap pakai)
        val frame = Layer().apply {
            kind = KIND_SHAPE; name = "FOTO KAMU (ganti layer ini)"; shape = "rect"
            w = 760f; h = 760f; radius = 48f; fill = 0xFF232B42.toInt()
            shapeStrokeW = 7f; shapeStrokeColor = 0xFF2EE6C0.toInt()
            maskShape = "rrect"
            x = 540f; y = 880f; start = 0f; dur = 8f
            shadowBlur = 26f; shadowDy = 18f
            bezX1 = 0.2f; bezY1 = 1.6f; bezX2 = 0.4f; bezY2 = 1f
        }
        punchBeat(frame, 1.13f, 1)
        p.layers.add(frame)

        val frameText = Layer().apply {
            kind = KIND_TEXT; name = "Label foto"; text = "FOTO\nKAMU"
            fontKey = "sans_black"; textSize = 110f; bold = true; align = "center"
            color = 0xFF9FB0C9.toInt()
            x = 540f; y = 880f; start = 0f; dur = 8f
            bezX1 = 0.2f; bezY1 = 1.6f; bezX2 = 0.4f; bezY2 = 1f
        }
        punchBeat(frameText, 1.13f, 1)
        p.layers.add(frameText)

        val title = Layer().apply {
            kind = KIND_TEXT; name = "Judul JJ"; text = "JEDAG JEDUG"
            fontKey = "sans_black"; textSize = 118f; bold = true; align = "center"
            color = 0xFFFFFFFF.toInt()
            x = 540f; y = 300f; start = 0f; dur = 8f
            fxGlow = 22f; glowColor = 0xFFFF2D78.toInt()
            bezX1 = 0.2f; bezY1 = 1.6f; bezX2 = 0.4f; bezY2 = 1f
        }
        punchBeat(title, 1.17f, 2)
        p.layers.add(title)

        val flash = Layer().apply {
            kind = KIND_SHAPE; name = "Flash beat"; shape = "rect"
            w = 1080f; h = 1920f; fill = 0xFFFFFFFF.toInt()
            x = 540f; y = 960f; alpha = 0f; start = 0f; dur = 8f
        }
        var ft = beat * 2f
        while (ft < 8f) {
            Anim.setKey(flash, "alpha", ft, 0.55f, "linear")
            Anim.setKey(flash, "alpha", minOf(8f, ft + 0.08f), 0f, "linear")
            ft += beat * 2f
        }
        p.layers.add(flash)

        val note = Layer().apply {
            kind = KIND_TEXT; name = "Catatan"; text = "130 BPM - pasang sound JJ, ganti kotaknya jadi foto kamu"
            fontKey = "mono"; textSize = 31f; bold = false; align = "center"
            color = 0xFF8FA1BD.toInt()
            x = 540f; y = 1740f; start = 0f; dur = 8f
        }
        Anim.setKey(note, "alpha", 0.4f, 0f, "easeOut")
        Anim.setKey(note, "alpha", 1.2f, 1f, "easeOut")
        p.layers.add(note)
        return p
    }

    /** JJ Soft Spoken: pastel terang, light leak melayang, hentakan selembut sutra. */
    fun jjSoft(): Project {
        val p = Project()
        p.name = "JJ Soft Spoken"
        p.duration = 8f
        p.bg = 0xFFF6EDE8.toInt()

        // bingkai tipis
        val frame = Layer().apply {
            kind = KIND_SHAPE; name = "Bingkai"; shape = "rect"
            w = 940f; h = 1720f; radius = 40f; fill = 0xFFF6EDE8.toInt()
            shapeStrokeW = 3f; shapeStrokeColor = 0xFF4A2B3A.toInt()
            x = 540f; y = 960f; start = 0f; dur = 8f
        }
        Anim.setKey(frame, "alpha", 0f, 0f, "easeOut")
        Anim.setKey(frame, "alpha", 1f, 1f, "easeOut")
        p.layers.add(frame)

        // light leak blobs pastel melayang
        val leakCols = intArrayOf(0xFFFFB3C7.toInt(), 0xFFFFE5B4.toInt(), 0xFFB8E3FF.toInt())
        val leakPos = arrayOf(floatArrayOf(260f, 420f), floatArrayOf(840f, 760f), floatArrayOf(300f, 1420f))
        for (li in 0 until 3) {
            val leak = Layer().apply {
                kind = KIND_SHAPE; name = "Light leak ${li + 1}"; shape = "circle"
                w = 720f + li * 90f; h = 720f + li * 90f
                fill = leakCols[li]
                x = leakPos[li][0]; y = leakPos[li][1]
                alpha = 0.5f; blend = "screen"; fxBlur = 30f
                start = 0f; dur = 8f
            }
            Anim.setKey(leak, "x", 0f, leakPos[li][0], "easeInOut")
            Anim.setKey(leak, "x", 4f, leakPos[li][0] + 90f, "easeInOut")
            Anim.setKey(leak, "x", 8f, leakPos[li][0], "easeInOut")
            Anim.setKey(leak, "y", 0f, leakPos[li][1], "easeInOut")
            Anim.setKey(leak, "y", 4f, leakPos[li][1] - 70f, "easeInOut")
            Anim.setKey(leak, "y", 8f, leakPos[li][1], "easeInOut")
            p.layers.add(leak)
        }

        val title = Layer().apply {
            kind = KIND_TEXT; name = "Soft spoken"; text = "soft\nspoken"
            fontKey = "serif_bold"; textSize = 165f; bold = true; align = "center"
            color = 0xFF4A2B3A.toInt()
            x = 540f; y = 850f; start = 0f; dur = 8f
            shadowBlur = 12f; shadowDy = 8f; shadowColor = 0xFFB98A9A.toInt()
        }
        var softT = 0f
        while (softT < 8f) {
            Anim.setKey(title, "scale", softT, 1.06f, "easeInOut")
            Anim.setKey(title, "scale", minOf(8f, softT + 0.45f), 1f, "easeInOut")
            Anim.setKey(title, "y", softT, 836f, "easeInOut")
            Anim.setKey(title, "y", minOf(8f, softT + 0.5f), 850f, "easeInOut")
            softT += 1f
        }
        Anim.setKey(title, "alpha", 0f, 0f, "easeOut")
        Anim.setKey(title, "alpha", 0.9f, 1f, "easeOut")
        p.layers.add(title)

        // bintang kecil berkelip
        val starPos = arrayOf(floatArrayOf(220f, 600f), floatArrayOf(860f, 520f), floatArrayOf(800f, 1240f))
        for (si in 0 until 3) {
            val star = Layer().apply {
                kind = KIND_SHAPE; name = "Kelip ${si + 1}"; shape = "star"
                w = 54f; h = 54f; fill = 0xFFFFC2D1.toInt()
                x = starPos[si][0]; y = starPos[si][1]; start = 0f; dur = 8f
            }
            var starT = si * 0.33f
            while (starT < 8f) {
                Anim.setKey(star, "scale", starT, 0.3f, "easeInOut")
                Anim.setKey(star, "scale", minOf(8f, starT + 0.5f), 1.05f, "easeInOut")
                Anim.setKey(star, "scale", minOf(8f, starT + 1f), 0.3f, "easeInOut")
                starT += 2f
            }
            p.layers.add(star)
        }

        val quote = Layer().apply {
            kind = KIND_TEXT; name = "Kutipan"; text = "pelan tapi nempel di kepala"
            fontKey = "serif"; textSize = 48f; bold = false; align = "center"
            color = 0xFF8A5A6E.toInt()
            x = 540f; y = 1240f; start = 0.8f; dur = 7.2f
        }
        Anim.setKey(quote, "alpha", 0.8f, 0f, "easeOut")
        Anim.setKey(quote, "alpha", 1.8f, 1f, "easeOut")
        Anim.setKey(quote, "y", 0.8f, 1280f, "easeOut")
        Anim.setKey(quote, "y", 1.8f, 1240f, "easeOut")
        p.layers.add(quote)

        val top = Layer().apply {
            kind = KIND_TEXT; name = "Label atas"; text = "A E S T H E T I C"
            fontKey = "mono"; textSize = 34f; bold = true; align = "center"
            color = 0xFF8A5A6E.toInt()
            x = 540f; y = 240f; start = 0f; dur = 8f
        }
        Anim.setKey(top, "alpha", 0.2f, 0f, "easeOut")
        Anim.setKey(top, "alpha", 1.1f, 1f, "easeOut")
        p.layers.add(top)
        return p
    }

    /** JJ Tipis: minimal bersih, hentakan nyaris tak terasa tapi hidup. */
    fun jjTipis(): Project {
        val p = Project()
        p.name = "JJ Tipis"
        p.duration = 6f
        p.bg = 0xFF0B0D10.toInt()
        val beat = 0.5f // 120 BPM

        val glow = Layer().apply {
            kind = KIND_SHAPE; name = "Napas latar"; shape = "circle"
            w = 900f; h = 900f; fill = 0xFF0E7D6B.toInt()
            x = 540f; y = 900f; alpha = 0.16f; blend = "screen"; fxBlur = 34f
            start = 0f; dur = 6f
        }
        Anim.setKey(glow, "scale", 0f, 1f, "easeInOut")
        Anim.setKey(glow, "scale", 3f, 1.09f, "easeInOut")
        Anim.setKey(glow, "scale", 6f, 1f, "easeInOut")
        p.layers.add(glow)

        val over = Layer().apply {
            kind = KIND_TEXT; name = "Overline"; text = "J J   T I P I S"
            fontKey = "mono"; textSize = 36f; bold = true; align = "center"
            color = 0xFF2EE6C0.toInt()
            x = 540f; y = 740f; start = 0f; dur = 6f
        }
        Anim.setKey(over, "alpha", 0f, 0f, "easeOut")
        Anim.setKey(over, "alpha", 0.7f, 1f, "easeOut")
        p.layers.add(over)

        val main = Layer().apply {
            kind = KIND_TEXT; name = "Teks tipis"; text = "tipis aja"
            fontKey = "sans"; textSize = 104f; bold = true; align = "center"
            color = 0xFFFFFFFF.toInt()
            x = 540f; y = 900f; start = 0f; dur = 6f
        }
        var bt = 0f
        while (bt < 6f) {
            Anim.setKey(main, "scale", bt, 1.07f, "easeOut")
            Anim.setKey(main, "scale", minOf(6f, bt + 0.2f), 1f, "easeOut")
            bt += beat
        }
        Anim.setKey(main, "alpha", 0f, 0f, "easeOut")
        Anim.setKey(main, "alpha", 0.5f, 1f, "easeOut")
        p.layers.add(main)

        val line = Layer().apply {
            kind = KIND_SHAPE; name = "Garis aksen"; shape = "rect"
            w = 240f; h = 4f; radius = 2f; fill = 0xFF2EE6C0.toInt()
            x = 540f; y = 990f; start = 0f; dur = 6f
        }
        Anim.setKey(line, "scaleX", 0.3f, 0.05f, "easeOut")
        Anim.setKey(line, "scaleX", 1.1f, 1f, "easeOut")
        p.layers.add(line)

        val bottom = Layer().apply {
            kind = KIND_TEXT; name = "Catatan bawah"; text = "sound kamu di sini - 120 BPM"
            fontKey = "mono"; textSize = 30f; bold = false; align = "center"
            color = 0xFF687389.toInt()
            x = 540f; y = 1760f; start = 0f; dur = 6f
        }
        Anim.setKey(bottom, "alpha", 0.6f, 0f, "easeOut")
        Anim.setKey(bottom, "alpha", 1.4f, 1f, "easeOut")
        p.layers.add(bottom)
        return p
    }

    /** JJ Kece Serem: merah gelap, jitter glitch keras, kedip hitam, vignette. */
    fun jjSerem(): Project {
        val p = Project()
        p.name = "JJ Kece Serem"
        p.duration = 6f
        p.bg = 0xFF0A0203.toInt()
        val jitterPat = floatArrayOf(10f, -14f, 6f, -6f, 16f, -10f, 4f, -16f, 12f, -4f)

        fun ghost(nm: String, col: Int, sign: Float, baseAlpha: Float): Layer {
            val g = Layer().apply {
                kind = KIND_TEXT; name = nm; text = "SEREM"
                fontKey = "sans_black"; textSize = 225f; bold = true; align = "center"
                color = col; blend = "screen"; alpha = baseAlpha
                x = 540f; y = 900f; start = 0f; dur = 6f
            }
            var gt = 0f
            while (gt < 6f) {
                Anim.setKey(g, "x", gt, 540f + 30f * sign, "linear")
                Anim.setKey(g, "x", minOf(6f, gt + 0.09f), 540f + 8f * sign, "linear")
                gt += 0.5f
            }
            return g
        }
        p.layers.add(ghost("Bayangan merah", 0xFFFF2D40.toInt(), 1f, 0.55f))
        p.layers.add(ghost("Bayangan putih", 0xFFB8E3FF.toInt(), -1f, 0.4f))

        val main = Layer().apply {
            kind = KIND_TEXT; name = "SEREM"; text = "SEREM"
            fontKey = "sans_black"; textSize = 225f; bold = true; align = "center"
            color = 0xFFD90429.toInt()
            x = 540f; y = 900f; start = 0f; dur = 6f
            fxGlow = 34f; glowColor = 0xFFD90429.toInt()
            shadowBlur = 22f; shadowDy = 14f; shadowColor = 0xFF000000.toInt()
        }
        // jitter keras tiap 0.07 dtk
        var jt = 0f
        var ji = 0
        while (jt < 6f) {
            Anim.setKey(main, "x", jt, 540f + jitterPat[ji % jitterPat.size], "linear")
            Anim.setKey(main, "y", jt, 900f + jitterPat[(ji + 3) % jitterPat.size] * 0.5f, "linear")
            jt += 0.07f
            ji++
        }
        var bt = 0f
        while (bt < 6f) {
            Anim.setKey(main, "scale", bt, 1.22f, "linear")
            Anim.setKey(main, "scale", minOf(6f, bt + 0.1f), 1f, "easeOut")
            bt += 0.5f
        }
        p.layers.add(main)

        // goresan vertikal (vektor) di kiri
        val scratchPts = StringBuilder()
        for (sy in -400..400 step 66) {
            if (scratchPts.isNotEmpty()) scratchPts.append(';')
            val wob = if ((sy / 66) % 2 == 0) -14 else 14
            scratchPts.append(wob).append(',').append(sy)
        }
        val scratch = Layer().apply {
            kind = KIND_VECTOR; name = "Goresan"
            vecPoints = scratchPts.toString(); vecStrokeW = 5f
            color = 0xFFFFFFFF.toInt(); alpha = 0.3f
            x = 120f; y = 900f; w = 40f; h = 800f
            start = 0f; dur = 6f
        }
        Anim.setKey(scratch, "y", 0f, 860f, "linear")
        Anim.setKey(scratch, "y", 6f, 980f, "linear")
        p.layers.add(scratch)

        // vignette tepi gelap
        val vigSpecs = arrayOf(
            floatArrayOf(540f, 50f, 1080f, 110f), floatArrayOf(540f, 1870f, 1080f, 110f),
            floatArrayOf(35f, 960f, 80f, 1920f), floatArrayOf(1045f, 960f, 80f, 1920f)
        )
        for (vi in vigSpecs.indices) {
            val vig = Layer().apply {
                kind = KIND_SHAPE; name = "Vignette ${vi + 1}"; shape = "rect"
                w = vigSpecs[vi][2]; h = vigSpecs[vi][3]; fill = 0xFF000000.toInt()
                x = vigSpecs[vi][0]; y = vigSpecs[vi][1]; alpha = 0.55f
                start = 0f; dur = 6f; fxBlur = 18f
            }
            p.layers.add(vig)
        }

        // kedip hitam mendadak
        val blink = Layer().apply {
            kind = KIND_SHAPE; name = "Kedip hitam"; shape = "rect"
            w = 1080f; h = 1920f; fill = 0xFF000000.toInt()
            x = 540f; y = 960f; alpha = 0f; start = 0f; dur = 6f
        }
        for (bl in floatArrayOf(1.5f, 3.5f, 5f)) {
            Anim.setKey(blink, "alpha", bl, 0.92f, "linear")
            Anim.setKey(blink, "alpha", bl + 0.07f, 0f, "linear")
        }
        p.layers.add(blink)

        val sub = Layer().apply {
            kind = KIND_TEXT; name = "Sub serem"; text = "sound serem viral - volume naik"
            fontKey = "mono"; textSize = 36f; bold = false; align = "center"
            color = 0xFFB3061F.toInt(); typing = 0f
            x = 540f; y = 1180f; start = 0.4f; dur = 5.6f
        }
        Anim.setKey(sub, "typing", 0.4f, 0f, "linear")
        Anim.setKey(sub, "typing", 2.4f, 1f, "linear")
        p.layers.add(sub)
        return p
    }
}
