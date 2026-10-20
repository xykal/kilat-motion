package com.kilat.motion.core.vector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgPathTest {

    @Test
    fun `parse M L H V Z absolut`() {
        val ops = SvgPath.parse("M10 10 H90 V90 H10 Z")
        assertEquals(5, ops.size)
        assertTrue(ops[0] is SvgPath.Op.Move)
        assertEquals(10f, (ops[0] as SvgPath.Op.Move).x, 0.001f)
        assertTrue(ops[1] is SvgPath.Op.Line)
        assertEquals(90f, (ops[1] as SvgPath.Op.Line).x, 0.001f)
        assertEquals(90f, (ops[2] as SvgPath.Op.Line).y, 0.001f)
        assertTrue(ops[4] is SvgPath.Op.Close)
    }

    @Test
    fun `parse relatif dan kurva`() {
        val ops = SvgPath.parse("m5 5 l10 0 c5 0 5 10 0 10 q-5 5 -10 0 z")
        // move(5,5) line(15,5) cubic(...) quad(...) close
        assertEquals(5, ops.size)
        assertEquals(15f, (ops[1] as SvgPath.Op.Line).x, 0.001f)
        val cubic = ops[2] as SvgPath.Op.Cubic
        assertEquals(15f, cubic.x, 0.001f)
        assertEquals(15f, cubic.y, 0.001f)
        val quad = ops[3] as SvgPath.Op.Quad
        assertEquals(5f, quad.x, 0.001f)
        assertEquals(15f, quad.y, 0.001f)
    }

    @Test
    fun `ekstrak d dan viewBox dari svg`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M4 4 L20 20"/></svg>"""
        assertEquals("M4 4 L20 20", SvgPath.extractPathData(svg))
        val vb = SvgPath.extractViewBox(svg)
        assertEquals(24f, vb[2], 0.001f)
        assertEquals(24f, vb[3], 0.001f)
    }

    @Test
    fun `path kosong aman`() {
        assertTrue(SvgPath.parse("").isEmpty())
        assertEquals("", SvgPath.extractPathData("<svg></svg>"))
    }
}
