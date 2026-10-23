package com.fluxa.motion.core.effects

import com.fluxa.motion.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsModelTest {

    @Test
    fun `setiap jenis efek punya label`() {
        for (t in EFFECT_TYPES) {
            assertTrue("label hilang: $t", EFFECT_LABELS.containsKey(t))
        }
        assertEquals(EFFECT_TYPES.size, EFFECT_LABELS.size)
    }

    @Test
    fun `efek berwarna adalah bagian dari jenis yang dikenal`() {
        assertTrue(EFFECT_TYPES.containsAll(EFFECT_USES_COLOR))
    }

    @Test
    fun `duplicate layer ikut menyalin efek secara independen`() {
        val l = Layer()
        l.effects.add(LayerEffect("vignette", 0.7f, 0.3f, 0xFF112233.toInt(), true))
        val c = l.duplicate()
        assertEquals(1, c.effects.size)
        assertEquals("vignette", c.effects[0].type)
        c.effects[0].amount = 0.1f
        assertEquals(0.7f, l.effects[0].amount, 0.0001f)
    }

    @Test
    fun `salinan efek mempertahankan semua nilai`() {
        val e = LayerEffect("grain", 0.4f, 0.9f, 0xFFABCDEF.toInt(), false)
        val c = e.copy()
        assertEquals(e.type, c.type)
        assertEquals(e.amount, c.amount, 0.0001f)
        assertEquals(e.size, c.size, 0.0001f)
        assertEquals(e.color, c.color)
        assertFalse(c.enabled)
    }
}
