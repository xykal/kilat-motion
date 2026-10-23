package com.fluxa.motion.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconsTest {

    @Test
    fun `set ikon lengkap dan unik`() {
        val names = FluxaIcon.values().map { it.name }
        assertEquals(names.size, names.distinct().size)
        assertTrue("minimal 55 ikon custom", names.size >= 55)
    }

    @Test
    fun `ikon inti navigasi tersedia`() {
        val names = FluxaIcon.values().map { it.name }.toSet()
        for (required in listOf("BOLT", "HOME", "FOLDER", "LAYERS", "USER", "PLUS", "PLAY", "EXPORT", "UNDO", "REDO", "SCISSORS", "MAGNET", "CROSSHAIR", "MINUS", "PENCIL", "VECTOR", "CUBE", "CURVE", "LOCK", "UNLOCK")) {
            assertTrue("ikon $required harus ada", required in names)
        }
    }
}
