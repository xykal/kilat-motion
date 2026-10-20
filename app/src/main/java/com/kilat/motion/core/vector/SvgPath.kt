package com.kilat.motion.core.vector

import kotlin.math.abs

/**
 * Parser path SVG (atribut d="") menjadi daftar operasi gambar murni
 * (tanpa android.graphics.Path supaya bisa diuji di JVM).
 *
 * Perintah yang didukung: M L H V C S Q T A Z (absolut + relatif).
 * Busur (A) disederhanakan menjadi garis ke titik akhir.
 */
object SvgPath {

    sealed class Op {
        data class Move(val x: Float, val y: Float) : Op()
        data class Line(val x: Float, val y: Float) : Op()
        data class Cubic(
            val x1: Float, val y1: Float, val x2: Float, val y2: Float,
            val x: Float, val y: Float
        ) : Op()
        data class Quad(val x1: Float, val y1: Float, val x: Float, val y: Float) : Op()
        object Close : Op()
    }

    private val tokenRe = Regex("[MmLlHhVvCcSsQqTtAaZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?")

    fun parse(d: String): List<Op> {
        val ops = ArrayList<Op>()
        val tokens = tokenRe.findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = 'M'
        var cx = 0f
        var cy = 0f
        var startX = 0f
        var startY = 0f
        var prevCubicX2: Float? = null
        var prevCubicY2: Float? = null
        var prevQuadX1: Float? = null
        var prevQuadY1: Float? = null

        fun isCmd(tok: String) = tok.length == 1 && tok[0].isLetter()
        fun num(): Float? {
            if (i >= tokens.size) return null
            val v = tokens[i].toFloatOrNull() ?: return null
            i++
            return v
        }
        fun hasNum(): Boolean = i < tokens.size && !isCmd(tokens[i])

        while (i < tokens.size) {
            if (isCmd(tokens[i])) {
                cmd = tokens[i][0]
                i++
                if (cmd == 'Z' || cmd == 'z') {
                    ops.add(Op.Close)
                    cx = startX; cy = startY
                    prevCubicX2 = null; prevQuadX1 = null
                    continue
                }
            }
            val rel = cmd.isLowerCase()
            when (cmd.uppercaseChar()) {
                'M' -> {
                    var first = true
                    while (hasNum()) {
                        val x = num() ?: break
                        val y = num() ?: break
                        val ax = if (rel) cx + x else x
                        val ay = if (rel) cy + y else y
                        if (first) {
                            ops.add(Op.Move(ax, ay))
                            startX = ax; startY = ay
                            first = false
                        } else {
                            ops.add(Op.Line(ax, ay))
                        }
                        cx = ax; cy = ay
                    }
                    prevCubicX2 = null; prevQuadX1 = null
                }
                'L' -> {
                    while (hasNum()) {
                        val x = num() ?: break
                        val y = num() ?: break
                        cx = if (rel) cx + x else x
                        cy = if (rel) cy + y else y
                        ops.add(Op.Line(cx, cy))
                    }
                    prevCubicX2 = null; prevQuadX1 = null
                }
                'H' -> {
                    while (hasNum()) {
                        val x = num() ?: break
                        cx = if (rel) cx + x else x
                        ops.add(Op.Line(cx, cy))
                    }
                    prevCubicX2 = null; prevQuadX1 = null
                }
                'V' -> {
                    while (hasNum()) {
                        val y = num() ?: break
                        cy = if (rel) cy + y else y
                        ops.add(Op.Line(cx, cy))
                    }
                    prevCubicX2 = null; prevQuadX1 = null
                }
                'C' -> {
                    while (hasNum()) {
                        val x1 = num() ?: break; val y1 = num() ?: break
                        val x2 = num() ?: break; val y2 = num() ?: break
                        val x = num() ?: break; val y = num() ?: break
                        val bx = if (rel) cx else 0f
                        val by = if (rel) cy else 0f
                        ops.add(Op.Cubic(bx + x1, by + y1, bx + x2, by + y2, bx + x, by + y))
                        prevCubicX2 = bx + x2; prevCubicY2 = by + y2
                        prevQuadX1 = null
                        cx = bx + x; cy = by + y
                    }
                }
                'S' -> {
                    while (hasNum()) {
                        val x2 = num() ?: break; val y2 = num() ?: break
                        val x = num() ?: break; val y = num() ?: break
                        val bx = if (rel) cx else 0f
                        val by = if (rel) cy else 0f
                        val rx = if (prevCubicX2 != null) 2 * cx - prevCubicX2 else cx
                        val ry = if (prevCubicY2 != null) 2 * cy - prevCubicY2 else cy
                        ops.add(Op.Cubic(rx, ry, bx + x2, by + y2, bx + x, by + y))
                        prevCubicX2 = bx + x2; prevCubicY2 = by + y2
                        prevQuadX1 = null
                        cx = bx + x; cy = by + y
                    }
                }
                'Q' -> {
                    while (hasNum()) {
                        val x1 = num() ?: break; val y1 = num() ?: break
                        val x = num() ?: break; val y = num() ?: break
                        val bx = if (rel) cx else 0f
                        val by = if (rel) cy else 0f
                        ops.add(Op.Quad(bx + x1, by + y1, bx + x, by + y))
                        prevQuadX1 = bx + x1; prevQuadY1 = by + y1
                        prevCubicX2 = null
                        cx = bx + x; cy = by + y
                    }
                }
                'T' -> {
                    while (hasNum()) {
                        val x = num() ?: break; val y = num() ?: break
                        val bx = if (rel) cx else 0f
                        val by = if (rel) cy else 0f
                        val rx = if (prevQuadX1 != null) 2 * cx - prevQuadX1 else cx
                        val ry = if (prevQuadY1 != null) 2 * cy - (prevQuadY1 ?: cy) else cy
                        ops.add(Op.Quad(rx, ry, bx + x, by + y))
                        prevQuadX1 = rx; prevQuadY1 = ry
                        prevCubicX2 = null
                        cx = bx + x; cy = by + y
                    }
                }
                'A' -> {
                    while (hasNum()) {
                        // rx ry rot largeArc sweep x y -> sederhanakan jadi garis ke titik akhir
                        num(); num(); num(); num(); num()
                        val x = num() ?: break
                        val y = num() ?: break
                        cx = if (rel) cx + x else x
                        cy = if (rel) cy + y else y
                        ops.add(Op.Line(cx, cy))
                    }
                    prevCubicX2 = null; prevQuadX1 = null
                }
                else -> {
                    // token tak dikenal: lewati satu agar tidak macet
                    if (hasNum()) num() else i++
                }
            }
            // M/m berikutnya tanpa perintah = lineto implisit sudah ditangani loop dalam M;
            // perintah lain berlanjut dengan huruf yang sama (loop while hasNum di tiap cabang).
        }
        return ops
    }

    /** Gabungkan semua atribut d="..." dari teks SVG. */
    fun extractPathData(svg: String): String {
        val re = Regex("""\bd\s*=\s*"([^"]+)"""")
        val parts = re.findAll(svg).map { it.groupValues[1] }.toList()
        if (parts.isNotEmpty()) return parts.joinToString(" ")
        // fallback bentuk dasar: rect / circle sederhana
        val rect = Regex("""<rect[^>]*x="([\d.\-]+)"[^>]*y="([\d.\-]+)"[^>]*width="([\d.\-]+)"[^>]*height="([\d.\-]+)"""")
            .find(svg)
        if (rect != null) {
            val (x, y, w, h) = rect.destructured
            val xf = x.toFloat(); val yf = y.toFloat(); val wf = w.toFloat(); val hf = h.toFloat()
            return "M$xf $yf H${xf + wf} V${yf + hf} H$xf Z"
        }
        val circle = Regex("""<circle[^>]*cx="([\d.\-]+)"[^>]*cy="([\d.\-]+)"[^>]*r="([\d.\-]+)"""").find(svg)
        if (circle != null) {
            val (cxS, cyS, rS) = circle.destructured
            val cxf = cxS.toFloat(); val cyf = cyS.toFloat(); val rf = rS.toFloat()
            val k = rf * 0.5523f
            return "M${cxf - rf} $cyf C${cxf - rf} ${cyf - k} ${cxf - k} ${cyf - rf} $cxf ${cyf - rf} " +
                "C${cxf + k} ${cyf - rf} ${cxf + rf} ${cyf - k} ${cxf + rf} $cyf " +
                "C${cxf + rf} ${cyf + k} ${cxf + k} ${cyf + rf} $cxf ${cyf + rf} " +
                "C${cxf - k} ${cyf + rf} ${cxf - rf} ${cyf + k} ${cxf - rf} $cyf Z"
        }
        return ""
    }

    /** viewBox sebagai [minX, minY, width, height]; default 0 0 100 100. */
    fun extractViewBox(svg: String): FloatArray {
        val vb = Regex("""viewBox\s*=\s*"([\d.\-\s,]+)"""").find(svg)
        if (vb != null) {
            val nums = vb.groupValues[1].trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
            if (nums.size == 4 && abs(nums[2]) > 0.01f && abs(nums[3]) > 0.01f) {
                return floatArrayOf(nums[0], nums[1], nums[2], nums[3])
            }
        }
        val w = Regex("""\bwidth="(\d+(?:\.\d+)?)"""").find(svg)?.groupValues?.get(1)?.toFloatOrNull()
        val h = Regex("""\bheight="(\d+(?:\.\d+)?)"""").find(svg)?.groupValues?.get(1)?.toFloatOrNull()
        return floatArrayOf(0f, 0f, w ?: 100f, h ?: 100f)
    }
}
