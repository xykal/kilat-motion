package com.kilat.motion

object Demo {

    fun build(): Project {
        val p = Project()
        p.name = "Demo ⚡ Kilat Motion"
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
            kind = KIND_TEXT; name = "Tagline"; text = "motion editor di Android ⚡"
            fontKey = "sans"; textSize = 46f; bold = false; align = "center"
            color = 0xFF9FB0C9.toInt()
            x = 540f; y = 1180f; start = 2.15f; dur = 3.85f
        }
        Anim.setKey(tagline, "alpha", 2.15f, 0f, "easeOut")
        Anim.setKey(tagline, "alpha", 2.8f, 1f, "easeOut")
        Anim.setKey(tagline, "y", 2.15f, 1240f, "easeOut")
        Anim.setKey(tagline, "y", 2.9f, 1180f, "easeOut")

        val code = Layer().apply {
            kind = KIND_TEXT; name = "browser note"; text = "100% native · 0 iklan"
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
}
