package com.fluxa.motion.core.animation

import com.fluxa.motion.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimSplitTest {

    private fun clip(): Layer = Layer().apply {
        kind = KIND_TEXT
        start = 0f
        dur = 4f
        x = 100f
    }

    @Test
    fun `split membagi durasi tanpa celah`() {
        val l = clip()
        val right = Anim.splitAt(l, 1.5f)
        assertNotNull(right)
        assertEquals(0f, l.start, 0.0001f)
        assertEquals(1.5f, l.dur, 0.0001f)
        assertEquals(1.5f, right!!.start, 0.0001f)
        assertEquals(2.5f, right.dur, 0.0001f)
        assertEquals(4f, right.start + right.dur, 0.0001f)
    }

    @Test
    fun `split di tepi klip ditolak`() {
        assertNull(Anim.splitAt(clip(), 0.01f))
        assertNull(Anim.splitAt(clip(), 3.99f))
    }

    @Test
    fun `split mempertahankan nilai animasi di titik potong`() {
        val l = clip()
        Anim.setKey(l, "x", 0f, 0f, "linear")
        Anim.setKey(l, "x", 4f, 400f, "linear")
        val right = Anim.splitAt(l, 2f)!!
        assertEquals(200f, Anim.value(l, "x", 2f), 0.5f)
        assertEquals(200f, Anim.value(right, "x", 2f), 0.5f)
        assertTrue(l.channels["x"]!!.keys.all { it.t <= 2.005f })
        assertTrue(right.channels["x"]!!.keys.all { it.t >= 1.995f })
    }
}
