package com.fluxa.motion.core.preview

import com.fluxa.motion.core.animation.Anim
import com.fluxa.motion.core.model.EFFECT_TYPES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSamplesTest {

    @Test
    fun everyPreset_hasAnimationInPreview() {
        // Setiap preset harus punya keyframe di layer contoh, bukan preview kosong
        for (p in Anim.PRESETS) {
            val layer = PreviewSamples.presetLayer(p)
            assertTrue("preset $p tidak punya keyframe di preview", layer.channels.values.any { it.keys.isNotEmpty() })
        }
    }

    @Test
    fun presetLayer_isLoopableWithinDuration() {
        val l = PreviewSamples.presetLayer("zoom")
        assertEquals(0f, l.start, 0f)
        assertEquals(PreviewSamples.DURATION, l.dur, 0f)
    }

    @Test
    fun everyEffect_hasSampleWithOneActiveEffect() {
        for (type in EFFECT_TYPES) {
            val l = PreviewSamples.effectLayer(type)
            assertEquals(1, l.effects.size)
            assertEquals(type, l.effects[0].type)
            assertTrue(l.effects[0].enabled)
            assertEquals(PreviewSamples.EFFECT_AMOUNT, l.effects[0].amount, 0.0001f)
        }
    }

    @Test
    fun defaultEffectColor_matchesInspectorDefaults() {
        assertEquals(0xFFFF8A3D.toInt(), PreviewSamples.defaultEffectColor("lightleak"))
        assertEquals(0xFF2EE6C0.toInt(), PreviewSamples.defaultEffectColor("tint"))
        assertEquals(0xFF000000.toInt(), PreviewSamples.defaultEffectColor("vignette"))
    }
}
