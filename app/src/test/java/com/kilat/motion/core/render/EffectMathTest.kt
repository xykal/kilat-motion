package com.kilat.motion.core.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectMathTest {

    @Test
    fun blurTypesAreTwentyUniqueIds() {
        assertEquals(20, EffectMath.BLUR_TYPES.size)
        assertEquals(20, EffectMath.BLUR_TYPES.map { it.first }.toSet().size)
        assertTrue(EffectMath.BLUR_TYPES.none { it.first == "none" })
    }

    @Test
    fun videoFrameIndexClampsToRange() {
        assertEquals(0, EffectMath.videoFrameIndex(-1f, 30f, 100))
        assertEquals(30, EffectMath.videoFrameIndex(1f, 30f, 100))
        assertEquals(99, EffectMath.videoFrameIndex(999f, 30f, 100))
        assertEquals(0, EffectMath.videoFrameIndex(1f, 30f, 0))
    }

    @Test
    fun fitSizeKeepsSmallSourceAndScalesLargeOne() {
        assertEquals(Pair(640, 360), EffectMath.fitSize(640, 360, 960))
        assertEquals(Pair(960, 540), EffectMath.fitSize(1920, 1080, 960))
        assertEquals(Pair(540, 960), EffectMath.fitSize(1080, 1920, 960))
    }

    @Test
    fun glassTintAlphaMapsPercent() {
        assertEquals(0, EffectMath.glassTintAlpha(0f))
        assertEquals(255, EffectMath.glassTintAlpha(100f))
        assertEquals(255, EffectMath.glassTintAlpha(250f))
        assertEquals(127, EffectMath.glassTintAlpha(50f))
    }

    @Test
    fun glassDownscaleKeepsSmallAndLimitsLarge() {
        assertEquals(1f, EffectMath.glassDownscale(200, 100), 0.0001f)
        assertEquals(320f / 1920f, EffectMath.glassDownscale(1920, 1080), 0.0001f)
    }
}
