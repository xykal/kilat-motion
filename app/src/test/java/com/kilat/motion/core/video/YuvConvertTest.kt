package com.kilat.motion.core.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YuvConvertTest {

    private fun argbR(p: Int) = (p shr 16) and 0xFF
    private fun argbG(p: Int) = (p shr 8) and 0xFF
    private fun argbB(p: Int) = p and 0xFF

    /** Y dan chroma netral (128) untuk ukuran w x h, pixelStride 1. */
    private fun planesNeutral(w: Int, h: Int, yValue: Int): Triple<ByteArray, ByteArray, ByteArray> {
        val y = ByteArray(w * h) { yValue.toByte() }
        val cw = (w + 1) / 2
        val ch = (h + 1) / 2
        val u = ByteArray(cw * ch) { 128.toByte() }
        val v = ByteArray(cw * ch) { 128.toByte() }
        return Triple(y, u, v)
    }

    @Test
    fun putihTetapPutih() {
        val (y, u, v) = planesNeutral(2, 2, 235)
        val px = YuvConvert.toArgb(y, 2, 1, u, 1, 1, v, 1, 1, 2, 2, 0, 2, 2, false)
        assertEquals(4, px.size)
        for (p in px) assertEquals(255, argbR(p)); // bt601 limited: Y 235 -> 255
        for (p in px) assertEquals(255, argbB(p))
    }

    @Test
    fun hitamTetapHitam() {
        val (y, u, v) = planesNeutral(2, 2, 16)
        val px = YuvConvert.toArgb(y, 2, 1, u, 1, 1, v, 1, 1, 2, 2, 0, 2, 2, true)
        for (p in px) assertEquals(0, argbR(p) + argbG(p) + argbB(p))
    }

    @Test
    fun merahBt601() {
        // Y=81, U=90, V=240 kira-kira merah murni pada BT.601
        val y = byteArrayOf(81.toByte())
        val u = byteArrayOf(90.toByte())
        val v = byteArrayOf(240.toByte())
        val px = YuvConvert.toArgb(y, 1, 1, u, 1, 1, v, 1, 1, 1, 1, 0, 1, 1, false)
        assertTrue("merah: ${argbR(px[0])}", argbR(px[0]) >= 250)
        assertTrue("hijau: ${argbG(px[0])}", argbG(px[0]) <= 5)
        assertTrue("biru: ${argbB(px[0])}", argbB(px[0]) <= 5)
    }

    @Test
    fun rotasi90SearahJarum() {
        // sumber 2x1: kiri hitam, kanan putih. Diputar 90 searah jarum: kiri jadi atas, kanan jadi bawah.
        val y = byteArrayOf(16.toByte(), 235.toByte())
        val u = byteArrayOf(128.toByte())
        val v = byteArrayOf(128.toByte())
        val px = YuvConvert.toArgb(y, 2, 1, u, 1, 1, v, 1, 1, 2, 1, 90, 1, 2, false)
        assertEquals(0, argbR(px[0]))      // atas: hitam
        assertEquals(255, argbR(px[1]))    // bawah: putih
    }

    @Test
    fun rotasi180() {
        val y = byteArrayOf(16.toByte(), 235.toByte())
        val u = byteArrayOf(128.toByte())
        val v = byteArrayOf(128.toByte())
        val px = YuvConvert.toArgb(y, 2, 1, u, 1, 1, v, 1, 1, 2, 1, 180, 2, 1, false)
        assertEquals(255, argbR(px[0]))    // kiri jadi putih
        assertEquals(0, argbR(px[1]))      // kanan jadi hitam
    }

    @Test
    fun chromaPixelStrideDibaca() {
        // gambar 4x2, chroma 2x1 dengan pixelStride 2: byte di indeks 1 dan 3 adalah cadangan (7)
        // yang TIDAK boleh terbaca. Kalau terbaca, warnanya jadi tidak netral.
        val y = ByteArray(8) { 235.toByte() }
        val u = byteArrayOf(128.toByte(), 7, 128.toByte(), 7)
        val v = byteArrayOf(128.toByte(), 7, 128.toByte(), 7)
        val px = YuvConvert.toArgb(y, 4, 1, u, 4, 2, v, 4, 2, 4, 2, 0, 4, 2, false)
        assertEquals(8, px.size)
        for (p in px) assertEquals(255, argbR(p))
        for (p in px) assertEquals(255, argbG(p))
        for (p in px) assertEquals(255, argbB(p))
    }
}
