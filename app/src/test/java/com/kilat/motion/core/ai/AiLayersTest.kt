package com.kilat.motion.core.ai

import com.kilat.motion.core.animation.Anim
import com.kilat.motion.core.model.Project
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiLayersTest {

    private fun root(json: String) = JSONObject(json)

    @Test
    fun parseSpecs_limitsToSixAndSkipsEmptyText() {
        val arr = (1..8).joinToString(",") { """{"text":"T$it","preset":"zoom","start":0,"dur":1}""" }
        val specs = AiLayers.parseSpecs(root("""{"layers":[$arr,{"text":"  ","preset":"fade"}]}"""), 10f)
        assertEquals(AiLayers.MAX_LAYERS, specs.size)
    }

    @Test
    fun parseSpecs_unknownPresetFallsBackToFade() {
        val specs = AiLayers.parseSpecs(root("""{"layers":[{"text":"Halo","preset":"tidakAda"}]}"""), 5f)
        assertEquals("fade", specs[0].preset)
    }

    @Test
    fun parseSpecs_clampsCoordinatesAndSize() {
        val s = AiLayers.parseSpecs(
            root("""{"layers":[{"text":"A","x":9,"y":-3,"size":5,"color":"#00FF00","start":0,"dur":1}]}"""), 5f
        )[0]
        assertEquals(1f, s.xFrac, 0.0001f)
        assertEquals(0f, s.yFrac, 0.0001f)
        assertEquals(0.3f, s.sizeFrac, 0.0001f)
        assertEquals(0xFF00FF00.toInt(), s.color)
    }

    @Test
    fun parseSpecs_keepsStartInsideDuration() {
        val s = AiLayers.parseSpecs(root("""{"layers":[{"text":"A","start":40,"dur":30}]}"""), 6f)[0]
        assertTrue(s.start <= 5.8f)
        assertTrue(s.start + s.dur <= 6.0001f + 0.3f)
    }

    @Test
    fun parseHexColor_validAndInvalid() {
        assertEquals(0xFFFF0000.toInt(), AiLayers.parseHexColor("#FF0000", 0))
        assertEquals(42, AiLayers.parseHexColor("#GGGGGG", 42))
        assertEquals(42, AiLayers.parseHexColor(null, 42))
    }

    @Test
    fun extractJson_toleratesCodeFence() {
        val j = AiClient.extractJson("Ini hasilnya:\n```json\n{\"layers\":[]}\n```")
        assertTrue(j.has("layers"))
    }

    @Test(expected = AiException::class)
    fun extractJson_rejectsNonJson() {
        AiClient.extractJson("maaf, tidak bisa")
    }

    @Test
    fun captions_doNotThrowOnShortProject() {
        val p = Project().apply { duration = 0.3f; width = 1080; height = 1920 }
        val layers = AiLayers.captions(listOf(AiClient.Segment(1f, 2f, "Halo dunia")), p)
        assertEquals(1, layers.size)
        assertTrue(layers[0].start <= p.duration)
        assertEquals(Anim.PRESETS.contains("fade"), true)
    }
}
