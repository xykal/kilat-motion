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
    fun `preset jedag bikin punch skala di playhead`() {
        val l = Layer().apply { kind = KIND_TEXT; start = 0f; dur = 4f; x = 540f; scale = 1f }
        assertTrue(Anim.applyPreset(l, "jedag", 1f, 10f))
        val scaleKeys = l.channels["scale"]!!.keys
        assertTrue(scaleKeys.size >= 3)
        assertTrue("punch harus di atas skala dasar", scaleKeys.maxOf { it.v } > 1.2f)
        assertTrue("shake x harus ada", l.channels["x"]!!.keys.size >= 4)
    }

    @Test
    fun `cubic bezier monoton dan bisa overshoot`() {
        assertEquals(0f, Anim.cubicBezier(0f, 0.2f, 1.6f, 0.4f, 1f), 0.001f)
        assertEquals(1f, Anim.cubicBezier(1f, 0.2f, 1.6f, 0.4f, 1f), 0.001f)
        // kurva pantul JJ harus overshoot di atas 1 di tengah jalan
        var maxV = 0f
        var i = 0
        while (i <= 20) {
            val v = Anim.cubicBezier(i / 20f, 0.2f, 1.6f, 0.4f, 1f)
            if (v > maxV) maxV = v
            i++
        }
        assertTrue("bezier JJ harus overshoot", maxV > 1.05f)
        // bezier linear (0,0,1,1) ~ identitas
        assertEquals(0.5f, Anim.cubicBezier(0.5f, 0f, 0f, 1f, 1f), 0.02f)
    }

    @Test
    fun `keyframe custom mengikuti kurva layer`() {
        val l = Layer().apply {
            kind = KIND_TEXT; start = 0f; dur = 4f; x = 100f
            bezX1 = 0.2f; bezY1 = 1.6f; bezX2 = 0.4f; bezY2 = 1f
        }
        Anim.setKey(l, "x", 0f, 100f, "custom")
        Anim.setKey(l, "x", 2f, 300f, "easeOut")
        val mid = Anim.value(l, "x", 1f)
        assertTrue("nilai tengah bezier harus melampaui linear (200): $mid", mid > 215f)
    }

    @Test
    fun `geser klip menggeser semua keyframe ikut`() {
        val l = Layer().apply { kind = KIND_TEXT; start = 1f; dur = 4f }
        Anim.setKey(l, "x", 1f, 100f, "linear")
        Anim.setKey(l, "x", 3f, 200f, "linear")
        Anim.setKey(l, "alpha", 2f, 0.5f, "linear")
        // klip dipindah +2 detik (seperti MODE_MOVE timeline)
        l.start = 3f
        Anim.shiftKeyframes(l, 2f, 10f)
        assertEquals(listOf(3f, 4f, 5f), Anim.allKeyTimes(l))
        assertEquals(100f, Anim.value(l, "x", 3f), 0.001f)
        assertEquals(200f, Anim.value(l, "x", 5f), 0.001f)
    }

    @Test
    fun `seret diamond memindah grup keyframe dan hapus di titik`() {
        val l = Layer().apply { kind = KIND_TEXT; start = 0f; dur = 6f }
        Anim.setKey(l, "x", 1f, 100f, "linear")
        Anim.setKey(l, "y", 1f, 50f, "linear")
        Anim.setKey(l, "x", 4f, 300f, "linear")
        val moved = Anim.moveKeyframeGroup(l, 1f, 2.5f)
        assertEquals(2.5f, moved, 0.001f)
        assertEquals(listOf(2.5f, 4f), Anim.allKeyTimes(l))
        val removed = Anim.removeKeysAtTime(l, 2.5f)
        assertEquals(2, removed)
        assertEquals(listOf(4f), Anim.allKeyTimes(l))
    }

    @Test
    fun `salin tempel keyframe antar waktu`() {
        val l = Layer().apply { kind = KIND_TEXT; start = 0f; dur = 8f; x = 0f }
        Anim.setKey(l, "x", 1f, 111f, "back")
        Anim.setKey(l, "scale", 1f, 1.5f, "easeOut")
        Anim.setKey(l, "x", 5f, 400f, "linear")
        val clip = Anim.copyKeysAt(l, 1f)
        assertEquals(2, clip.size)
        Anim.pasteKeysAt(l, clip, 3f)
        assertEquals(111f, Anim.value(l, "x", 3f), 0.001f)
        assertEquals(1.5f, Anim.value(l, "scale", 3f), 0.001f)
        assertEquals("back", Anim.keyAt(l, "x", 3f)!!.ease)
    }

    @Test
    fun `format waktu dan warna`() {
        assertEquals("00:01.50", Format.time(1.5f))
        assertEquals("#FF2EE6C0", Format.color(0xFF2EE6C0.toInt()))
        assertEquals(0xFF2EE6C0.toInt(), Format.parseColor("#FF2EE6C0", 0))
    }

    @Test
    fun `splitAt memotong durasi dan menjaga nilai di titik pisah`() {
        val l = Layer()
        l.start = 1f; l.dur = 4f
        Anim.setKey(l, "x", 1f, 100f, "linear")
        Anim.setKey(l, "x", 5f, 300f, "linear")
        val second = Anim.splitAt(l, 3f)
        assertNotNull(second)
        assertEquals(2f, l.dur, 0.001f)
        assertEquals(3f, second!!.start, 0.001f)
        assertEquals(2f, second.dur, 0.001f)
        // nilai x di t=3 adalah 200 (linear) dan harus sama di kedua sisi
        assertEquals(200f, Anim.value(l, "x", 3f), 0.01f)
        assertEquals(200f, Anim.value(second, "x", 3f), 0.01f)
        assertEquals(300f, Anim.value(second, "x", 5f), 0.01f)
        assertTrue(l.channels["x"]!!.keys.all { it.t <= 3.01f })
        assertTrue(second.channels["x"]!!.keys.all { it.t >= 2.99f })
    }

    @Test
    fun `splitAt menolak titik di luar rentang`() {
        val l = Layer()
        l.start = 2f; l.dur = 2f
        assertNull(Anim.splitAt(l, 1f))
        assertNull(Anim.splitAt(l, 2f))
        assertNull(Anim.splitAt(l, 4f))
        assertNull(Anim.splitAt(l, 5f))
        assertNotNull(Anim.splitAt(l, 3f))
    }
}
