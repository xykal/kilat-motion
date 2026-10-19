package com.kilat.motion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {

    @Test
    fun `id template unik`() {
        val ids = Templates.ALL.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(Templates.ALL.size >= 8)
    }

    @Test
    fun `semua template menghasilkan project valid`() {
        for (t in Templates.ALL) {
            val p = t.build()
            assertTrue("${t.id} harus punya layer", p.layers.isNotEmpty())
            assertTrue("${t.id} durasi valid", p.duration > 0f)
            assertTrue("${t.id} dimensi valid", p.width > 0 && p.height > 0)
            assertTrue("${t.id} previewTime dalam durasi", t.previewTime <= p.duration)
            assertTrue("${t.id} id project terisi", p.id.isNotEmpty())
        }
    }

    @Test
    fun `createFromTemplate dikenal dan tidak dikenal`() {
        assertNotNull(Templates.createFromTemplate(Templates.ALL.first().id))
        assertNull(Templates.createFromTemplate("tidak-ada"))
    }

    @Test
    fun `rasio kanvas standar`() {
        assertEquals(5, Templates.RATIOS.size)
        for (r in Templates.RATIOS) {
            assertTrue(r.w > 0 && r.h > 0)
        }
        val vertikal = Templates.RATIOS.first { it.label == "9:16" }
        assertTrue(vertikal.h > vertikal.w)
    }

    @Test
    fun `project baru berukuran sesuai rasio`() {
        val p = Templates.newProject(1080, 1080)
        assertEquals(1080, p.width)
        assertEquals(1080, p.height)
        assertTrue(p.layers.isEmpty())
    }
}
