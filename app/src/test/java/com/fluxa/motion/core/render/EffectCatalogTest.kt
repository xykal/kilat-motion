package com.fluxa.motion.core.render

import com.fluxa.motion.core.model.EFFECT_CATEGORIES
import com.fluxa.motion.core.model.EFFECT_LABELS
import com.fluxa.motion.core.model.EFFECT_TYPES
import com.fluxa.motion.core.model.EFFECT_USES_COLOR
import com.fluxa.motion.core.model.LayerEffect
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectCatalogTest {

    private val identity = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    @Test
    fun tepatTigaPuluhDelapanEfek() {
        assertEquals(38, EFFECT_TYPES.size)
        assertEquals(EFFECT_TYPES.size, EFFECT_TYPES.toSet().size)
    }

    @Test
    fun setiapEfekPunyaLabelDanDidukungRenderer() {
        EFFECT_TYPES.forEach { t ->
            assertNotNull("label $t", EFFECT_LABELS[t])
            assertTrue("renderer belum mendukung $t", t in EffectDraw.SUPPORTED)
        }
    }

    @Test
    fun tidakAdaEfekYatimDiRenderer() {
        assertEquals(EFFECT_TYPES.toSet(), EffectDraw.SUPPORTED)
    }

    @Test
    fun efekWarnaYangMemakaiWarnaAdaDiDaftar() {
        assertTrue(EFFECT_USES_COLOR.all { it in EFFECT_TYPES })
    }

    @Test
    fun tepatDuaPuluhJenisBlurUnik() {
        assertEquals(20, EffectMath.BLUR_TYPES.size)
        assertEquals(20, EffectMath.BLUR_TYPES.map { it.first }.toSet().size)
    }

    @Test
    fun intensitasNolSelaluIdentitas() {
        for (t in EffectDraw.COLOR_TYPES) {
            val m = EffectDraw.colorValues(LayerEffect(type = t, amount = 0f), 0.3f)
            assertNotNull(t, m)
            assertArrayEquals("efek $t", identity, m, 1e-4f)
        }
    }

    @Test
    fun invertPenuhMembalik() {
        val m = EffectDraw.colorValues(LayerEffect(type = "invert", amount = 1f), 0f)!!
        assertEquals(-1f, m[0], 1e-6f)
        assertEquals(255f, m[4], 1e-4f)
    }

    @Test
    fun invertDuaKaliKembaliIdentitas() {
        val inv = LayerEffect(type = "invert", amount = 1f)
        val m = EffectDraw.composeColor(listOf(inv, inv), 0f)!!
        assertArrayEquals(identity, m, 1e-3f)
    }

    @Test
    fun efekBukanWarnaTidakPunyaMatriks() {
        assertNull(EffectDraw.colorValues(LayerEffect(type = "vignette"), 0f))
        assertNull(EffectDraw.colorValues(LayerEffect(type = "shake"), 0f))
    }

    @Test
    fun setiapEfekTepatSatuKategori() {
        val all = EFFECT_CATEGORIES.values.flatten()
        assertEquals(all.size, all.toSet().size)
        assertEquals(EFFECT_TYPES.toSet(), all.toSet())
    }

    @Test
    fun zoomDanGoyangNolSamaSekaliTidakBerubah() {
        assertEquals(1f, EffectDraw.zoomPulseScale(LayerEffect(type = "zoompulse", amount = 0f), 0.4f), 1e-6f)
        assertEquals(0f, EffectDraw.wobbleDeg(LayerEffect(type = "wobble", amount = 0f), 0.4f), 1e-6f)
    }

    @Test
    fun zoomDanGoyangTetapDalamBatas() {
        for (i in 0 until 50) {
            val t = i * 0.037f
            val s = EffectDraw.zoomPulseScale(LayerEffect(type = "zoompulse", amount = 1f, size = 1f), t)
            assertTrue("skala $s", s >= 1f && s <= 1.17f)
            val d = EffectDraw.wobbleDeg(LayerEffect(type = "wobble", amount = 1f, size = 1f), t)
            assertTrue("sudut $d", kotlin.math.abs(d) <= 8.01f)
        }
    }

    @Test
    fun kecerahanNaikSaatDiberiIntensitas() {
        val m = EffectDraw.colorValues(LayerEffect(type = "brightness", amount = 1f), 0f)!!
        assertEquals(90f, m[4], 1e-4f)
        assertEquals(1f, m[0], 1e-6f)
    }

    @Test
    fun shakeNolWaktuTidakBergeser() {
        val (dx, dy) = EffectDraw.shakeOffset(LayerEffect(type = "shake", amount = 0f), 1080f, 1.7f)
        assertEquals(0f, dx, 1e-6f)
        assertEquals(0f, dy, 1e-6f)
    }
}
