package com.kilat.motion.core.animation

import com.kilat.motion.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimPresetsTest {

    private fun textLayer(): Layer = Layer().apply {
        kind = KIND_TEXT
        start = 1f
        dur = 4f
        x = 540f
        y = 960f
    }

    @Test
    fun `setiap preset punya label`() {
        for (id in Anim.PRESETS) {
            assertTrue("label hilang: $id", Anim.PRESET_LABELS.containsKey(id))
        }
        assertEquals(Anim.PRESETS.size, Anim.PRESET_LABELS.size)
    }

    @Test
    fun `setiap preset menghasilkan channel dengan key terurut dan berjarak aman`() {
        for (id in Anim.PRESETS) {
            if (id == "kenBurns") continue // hanya untuk layer gambar
            val layer = textLayer()
            val ok = Anim.applyPreset(layer, id, playhead = 1.2f, projectDuration = 10f)
            assertTrue("preset $id gagal diterapkan", ok)
            assertTrue("preset $id tidak membuat channel", layer.channels.isNotEmpty())
            for ((key, ch) in layer.channels) {
                val ks = ch.keys
                assertTrue("channel $key kosong pada $id", ks.isNotEmpty())
                for (i in 1 until ks.size) {
                    assertTrue("urutan key rusak: $id/$key", ks[i].t >= ks[i - 1].t)
                    // Anim.setKey menimpa key yang terlalu rapat (<0.02s): jarak harus aman.
                    assertTrue("key terlalu rapat: $id/$key", ks[i].t - ks[i - 1].t >= 0.02f || ks[i].t == ks[i - 1].t)
                }
            }
        }
    }

    @Test
    fun `blurIn dan flash memakai channel efek`() {
        val blur = textLayer()
        Anim.applyPreset(blur, "blurIn", 1.2f, 10f)
        assertTrue(blur.channels.containsKey("fx.blur"))

        val flash = textLayer()
        Anim.applyPreset(flash, "flash", 1.2f, 10f)
        assertTrue(flash.channels.containsKey("fx.brightness"))
    }

    @Test
    fun `preset tidak menyentuh layer lain`() {
        val a = textLayer()
        val b = textLayer()
        Anim.applyPreset(a, "shake", 1.2f, 10f)
        assertFalse(b.channels.isNotEmpty())
    }
}
