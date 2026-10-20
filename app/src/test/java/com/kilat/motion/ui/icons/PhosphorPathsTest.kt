package com.kilat.motion.ui.icons

import com.kilat.motion.ui.components.KilatIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhosphorPathsTest {
    @Test
    fun everyKilatIconHasPathData() {
        KilatIcon.values().forEach { icon ->
            val d = PhosphorPaths.data[icon]
            assertTrue("missing path for ${icon.name}", !d.isNullOrBlank())
        }
        assertEquals(KilatIcon.values().size, PhosphorPaths.data.size)
    }
}
