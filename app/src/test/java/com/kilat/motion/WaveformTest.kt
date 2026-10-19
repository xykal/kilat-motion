package com.kilat.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformTest {

    @Test
    fun `silence menghasilkan envelope nol`() {
        val env = Waveform.envelope(ShortArray(1000), 50)
        assertEquals(50, env.size)
        assertTrue(env.all { it == 0f })
    }

    @Test
    fun `spike dinormalisasi ke satu`() {
        val s = ShortArray(1000)
        s[500] = 16000
        val env = Waveform.envelope(s, 20)
        assertEquals(1f, env.maxOrNull()!!, 0.0001f)
    }

    @Test
    fun `jumlah bucket sesuai permintaan`() {
        val s = ShortArray(5000) { (Math.sin(it / 20.0) * 8000).toInt().toShort() }
        assertEquals(123, Waveform.envelope(s, 123).size)
    }

    @Test
    fun `toMono merata-ratakan channel`() {
        val stereo = shortArrayOf(1000, 3000, -2000, 0)
        val mono = Waveform.toMono(stereo, 2)
        assertEquals(2, mono.size)
        assertEquals(2000, mono[0].toInt())
        assertEquals(-1000, mono[1].toInt())
    }

    @Test
    fun `toMono mengembalikan array sama untuk mono`() {
        val mono = shortArrayOf(1, 2, 3)
        assertTrue(Waveform.toMono(mono, 1) === mono)
    }

    @Test
    fun `musik default masuk akal`() {
        val m = Music()
        assertEquals(0f, m.start, 0.0001f)
        assertEquals(0.8f, m.volume, 0.0001f)
        assertEquals("Musik", m.name)
    }

    @Test
    fun `project tanpa musik default null`() {
        val p = Project()
        assertEquals(null, p.music)
    }
}
