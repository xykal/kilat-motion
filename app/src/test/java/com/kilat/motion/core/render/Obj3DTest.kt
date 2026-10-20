package com.kilat.motion.core.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class Obj3DTest {

    @Test
    fun `primitif valid dan ternormalisasi kasar`() {
        for (kind in listOf("cube", "pyramid", "prism", "octa")) {
            val m = Obj3D.primitive(kind)
            assertTrue("$kind punya vertex", m.verts.isNotEmpty())
            assertTrue("$kind punya face", m.faces.isNotEmpty())
            for (f in m.faces) {
                assertTrue(f.size >= 3)
                for (idx in f) assertTrue(idx in m.verts.indices)
            }
        }
        assertEquals(8, Obj3D.primitive("cube").verts.size)
        assertEquals(6, Obj3D.primitive("cube").faces.size)
    }

    @Test
    fun `parse obj segitiga dan quad`() {
        val obj = """
            # komentar
            v -1 0 0
            v 1 0 0
            v 1 2 0
            v -1 2 0
            f 1 2 3 4
        """.trimIndent()
        val m = Obj3D.parseObj(obj)
        assertNotNull(m)
        assertEquals(4, m!!.verts.size)
        // quad di-fan jadi 2 segitiga
        assertEquals(2, m.faces.size)
        // ternormalisasi: radius max = 1
        var maxR = 0f
        for (v in m.verts) {
            val r = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            if (r > maxR) maxR = r
        }
        assertEquals(1f, maxR, 0.01f)
    }

    @Test
    fun `parse obj format f dengan slash dan indeks negatif`() {
        val obj = """
            v 0 0 0
            v 2 0 0
            v 1 2 0
            f 1/1/1 2/2/2 3/3/3
            f -3 -2 -1
        """.trimIndent()
        val m = Obj3D.parseObj(obj)
        assertNotNull(m)
        assertEquals(2, m!!.faces.size)
    }

    @Test
    fun `obj kosong atau rusak null`() {
        assertNull(Obj3D.parseObj(""))
        assertNull(Obj3D.parseObj("bukan obj sama sekali"))
        assertNull(Obj3D.parseObj("v 0 0 0\nv 1 1 1"))
    }
}
