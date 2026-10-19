package com.kilat.motion.core.animation

import com.kilat.motion.core.model.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimTest {

    @Test
    fun `linear interpolation middle`() {
        val keys = listOf(Keyframe(0f, 0f, "linear"), Keyframe(1f, 100f, "linear"))
        assertEquals(50f, Anim.evalKeys(keys, 0.5f)!!, 0.01f)
    }

    @Test
    fun `clamp sebelum dan sesudah keyframe`() {
        val keys = listOf(Keyframe(1f, 7f), Keyframe(2f, 9f))
        assertEquals(7f, Anim.evalKeys(keys, 0f)!!, 0.001f)
        assertEquals(9f, Anim.evalKeys(keys, 5f)!!, 0.001f)
    }

    @Test
    fun `easeOut lebih cepat dari linear di awal`() {
        val p = 0.3f
        val eo = Anim.ease("easeOut", p)
        assertTrue("easeOut($p)=$eo harus > linear $p", eo > p)
        assertEquals(0f, Anim.ease("easeOut", 0f), 0.0001f)
        assertEquals(1f, Anim.ease("easeOut", 1f), 0.0001f)
    }

    @Test
    fun `setKey update keyframe yang sama`() {
        val l = Layer()
        Anim.setKey(l, "x", 1f, 10f)
        Anim.setKey(l, "x", 1f, 20f)
        assertEquals(1, l.channels["x"]!!.keys.size)
        assertEquals(20f, l.channels["x"]!!.keys[0].v, 0.001f)
    }

    @Test
    fun `removeKey menghapus channel kosong`() {
        val l = Layer()
        Anim.setKey(l, "alpha", 2f, 0f)
        assertNotNull(Anim.keyAt(l, "alpha", 2f))
        Anim.removeKey(l, "alpha", 2f)
        assertNull(l.channels["alpha"])
    }

    @Test
    fun `preset fade bikin 2 keyframe dan mengembalikan true`() {
        val l = Layer().apply { kind = KIND_TEXT; start = 0f; dur = 4f }
        assertTrue(Anim.applyPreset(l, "fade", 0.5f, 10f))
        assertEquals(2, l.channels["alpha"]!!.keys.size)
    }

    @Test
    fun `preset typing ditolak untuk layer bentuk`() {
        val l = Layer().apply { kind = KIND_SHAPE; start = 0f; dur = 4f }
        assertFalse(Anim.applyPreset(l, "typing", 0.5f, 10f))
        assertNull(l.channels["typing"])
    }

    @Test
    fun `preset kenBurns hanya untuk gambar`() {
        val txt = Layer().apply { kind = KIND_TEXT; start = 0f; dur = 4f }
        assertFalse(Anim.applyPreset(txt, "kenBurns", 1f, 10f))
        val img = Layer().apply { kind = KIND_IMAGE; start = 0f; dur = 4f }
        assertTrue(Anim.applyPreset(img, "kenBurns", 1f, 10f))
        assertEquals(2, img.channels["scale"]!!.keys.size)
    }

    @Test
    fun `duplicate menyalin channels tanpa referensi sama`() {
        val l = Layer()
        Anim.setKey(l, "rot", 0f, 0f, "linear")
        Anim.setKey(l, "rot", 1f, 90f, "linear")
        val c = l.duplicate()
        assertEquals(2, c.channels["rot"]!!.keys.size)
        c.channels["rot"]!!.keys[0].v = 45f
        assertEquals(0f, l.channels["rot"]!!.keys[0].v, 0.001f)
    }

    @Test
    fun `evaluasi layer mengisi semua properti`() {
        val l = Layer().apply {
            x = 100f; y = 200f; scale = 1f; rot = 0f; alpha = 1f
            fxBlur = 4f; fxGlow = 12f
        }
        val ev = Anim.evalLayer(l, 1f)
        assertEquals(100f, ev["x"]!!, 0.001f)
        assertEquals(4f, ev["fx.blur"]!!, 0.001f)
        assertEquals(12f, ev["fx.glow"]!!, 0.001f)
        assertEquals(PROP_KEYS.size, ev.size)
    }

    @Test
    fun `preset glitch bikin banyak keyframe x`() {
        val l = Layer().apply { kind = KIND_TEXT; start = 0f; dur = 4f; x = 500f }
        assertTrue(Anim.applyPreset(l, "glitch", 0.2f, 10f))
        assertTrue(l.channels["x"]!!.keys.size >= 7)
    }

    @Test
    fun `format waktu dan warna`() {
        assertEquals("00:01.50", Format.time(1.5f))
        assertEquals("#FF2EE6C0", Format.color(0xFF2EE6C0.toInt()))
        assertEquals(0xFF2EE6C0.toInt(), Format.parseColor("#FF2EE6C0", 0))
    }
}
