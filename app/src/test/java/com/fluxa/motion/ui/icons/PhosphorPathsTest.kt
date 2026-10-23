package com.fluxa.motion.ui.icons

import com.fluxa.motion.ui.components.FluxaIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhosphorPathsTest {
    @Test
    fun everyFluxaIconHasPathData() {
        FluxaIcon.values().forEach { icon ->
            val d = PhosphorPaths.data[icon]
            assertTrue("missing path for ${icon.name}", !d.isNullOrBlank())
        }
        assertEquals(FluxaIcon.values().size, PhosphorPaths.data.size)
    }
}
