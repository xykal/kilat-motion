package com.fluxa.motion.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderRangeTest {

    @Test
    fun dimMax_keepsBaseForNormalValues() {
        assertEquals(2000, SliderRange.dimMax(640f))
        assertEquals(2000, SliderRange.dimMax(0f))
    }

    @Test
    fun dimMax_growsSoThumbDoesNotPinToEdge() {
        // nilai 2400 harus tetap terlihat di dalam rentang, bukan terpotong di 2000
        val max = SliderRange.dimMax(2400f)
        assertEquals(2400, max)
        assertEquals(2400, SliderRange.progressOf(2400f, max))
    }

    @Test
    fun dimMax_isCapped() {
        assertEquals(SliderRange.DIM_CAP, SliderRange.dimMax(1_000_000f))
        assertEquals(SliderRange.DIM_BASE, SliderRange.dimMax(Float.NaN))
    }

    @Test
    fun progressOf_clampsToRange() {
        assertEquals(0, SliderRange.progressOf(-50f, 2000))
        assertEquals(2000, SliderRange.progressOf(9999f, 2000))
        assertEquals(0, SliderRange.progressOf(Float.NaN, 2000))
    }

    @Test
    fun progressOf_roundsToNearestPixel() {
        assertEquals(11, SliderRange.progressOf(10.6f, 2000))
        assertEquals(10, SliderRange.progressOf(10.4f, 2000))
    }
}
