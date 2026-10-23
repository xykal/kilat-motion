package com.fluxa.motion.core.audio

import org.junit.Assert.assertTrue
import org.junit.Test

class BeatDetectorTest {

    /** Envelope sintetis: lonjakan tiap 0.5 detik di atas dasar tenang. */
    private fun syntheticEnvelope(buckets: Int, spanSec: Float, every: Float): FloatArray {
        val step = spanSec / buckets
        val env = FloatArray(buckets) { 0.1f }
        var t = every
        while (t < spanSec - 0.1f) {
            val idx = (t / step).toInt()
            if (idx in 0 until buckets) env[idx] = 1f
            t += every
        }
        return env
    }

    @Test
    fun detectsRegularBeats() {
        val span = 10f
        val env = syntheticEnvelope(250, span, 0.5f)
        val beats = BeatDetector.beatTimes(env, span)
        // ada sekitar 19 lonjakan (0.5, 1.0, ..., 9.5)
        assertTrue("terdeteksi ${beats.size}", beats.size in 15..20)
        for (b in beats) {
            val nearest = (1..19).minOf { k -> kotlin.math.abs(b - k * 0.5f) }
            assertTrue("beat $b jauh dari kisi 0.5 detik", nearest < 0.1f)
        }
    }

    @Test
    fun silenceHasNoBeats() {
        assertTrue(BeatDetector.beatTimes(FloatArray(200) { 0f }, 8f).isEmpty())
    }

    @Test
    fun minimumGapIsRespected() {
        // lonjakan tiap 0.1 detik, tetapi jarak minimum 0.3 detik
        val env = syntheticEnvelope(200, 10f, 0.1f)
        val beats = BeatDetector.beatTimes(env, 10f, minGapSec = 0.3f)
        for (i in 1 until beats.size) {
            assertTrue("jarak ${beats[i] - beats[i - 1]}", beats[i] - beats[i - 1] >= 0.299f)
        }
    }
}
