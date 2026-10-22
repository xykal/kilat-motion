package com.kilat.motion.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecretFormatTest {

    private val iv = ByteArray(12) { it.toByte() }
    private val ct = "gsk_contoh_rahasia".toByteArray(Charsets.UTF_8)

    @Test
    fun packLaluUnpackKembaliSama() {
        val packed = SecretFormat.pack(iv, ct)
        val (riv, rct) = SecretFormat.unpack(packed)!!
        assertArrayEquals(iv, riv)
        assertArrayEquals(ct, rct)
    }

    @Test
    fun hasilPackTidakMengandungPlaintext() {
        val packed = SecretFormat.pack(iv, ct)
        assertEquals(false, packed.contains("gsk_contoh"))
    }

    @Test
    fun tanpaTitikDuaDitolak() {
        assertNull(SecretFormat.unpack("abcdef"))
    }

    @Test
    fun ivBukanDuaBelasByteDitolak() {
        val shortIv = SecretFormat.pack(ByteArray(8), ct)
        assertNull(SecretFormat.unpack(shortIv))
    }

    @Test
    fun ciphertextKosongDitolak() {
        val packed = SecretFormat.pack(iv, ByteArray(0))
        assertNull(SecretFormat.unpack(packed))
    }

    @Test
    fun base64RusakDitolak() {
        assertNull(SecretFormat.unpack("%%%:@@@"))
    }
}
