package com.fluxa.motion.core.render

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Geometri bentuk dasar, berpusat di (0,0) dalam kotak w x h.
 * Dipakai Renderer (layer bentuk) dan pratinjau pilihan bentuk di Inspector,
 * supaya tampilan keduanya sama.
 */
object ShapeGeom {
    fun build(kind: String, w0: Float, h0: Float, sides: Int, radius: Float, out: Path) {
        val w = max(1f, w0)
        val h = max(1f, h0)
        val rc = RectF()
        out.reset()
        when (kind) {
            "circle" -> {
                rc.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
                out.addOval(rc, Path.Direction.CW)
            }
            "triangle" -> {
                out.moveTo(0f, -h / 2f)
                out.lineTo(w / 2f, h / 2f)
                out.lineTo(-w / 2f, h / 2f)
                out.close()
            }
            "star" -> {
                val outer = min(w, h) / 2f
                val inner = outer / 2.3f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outer else inner
                    val ang = -Math.PI / 2 + i * Math.PI / 5
                    val px = (Math.cos(ang) * r).toFloat()
                    val py = (Math.sin(ang) * r).toFloat()
                    if (i == 0) out.moveTo(px, py) else out.lineTo(px, py)
                }
                out.close()
            }
            "diamond" -> {
                out.moveTo(0f, -h / 2f); out.lineTo(w / 2f, 0f); out.lineTo(0f, h / 2f); out.lineTo(-w / 2f, 0f); out.close()
            }
            "hexagon", "pentagon" -> {
                val sides = if (kind == "hexagon") 6 else 5
                for (i in 0 until sides) {
                    val ang = -Math.PI / 2 + i * 2 * Math.PI / sides
                    val px = (Math.cos(ang) * w / 2f).toFloat()
                    val py = (Math.sin(ang) * h / 2f).toFloat()
                    if (i == 0) out.moveTo(px, py) else out.lineTo(px, py)
                }
                out.close()
            }
            "heart" -> {
                val hw = w / 2f; val hh = h / 2f
                out.moveTo(0f, hh * 0.9f)
                out.cubicTo(-hw * 1.3f, hh * 0.1f, -hw * 0.9f, -hh * 1.0f, 0f, -hh * 0.35f)
                out.cubicTo(hw * 0.9f, -hh * 1.0f, hw * 1.3f, hh * 0.1f, 0f, hh * 0.9f)
                out.close()
            }
            "arrow" -> {
                val hw = w / 2f; val hh = h / 2f; val sh = hh * 0.5f
                out.moveTo(-hw, -sh); out.lineTo(0f, -sh); out.lineTo(0f, -hh); out.lineTo(hw, 0f)
                out.lineTo(0f, hh); out.lineTo(0f, sh); out.lineTo(-hw, sh); out.close()
            }
            "cross" -> {
                val bx = w * 0.17f; val by = h * 0.17f
                val hw = w / 2f; val hh = h / 2f
                out.moveTo(-bx, -hh); out.lineTo(bx, -hh); out.lineTo(bx, -by); out.lineTo(hw, -by)
                out.lineTo(hw, by); out.lineTo(bx, by); out.lineTo(bx, hh); out.lineTo(-bx, hh)
                out.lineTo(-bx, by); out.lineTo(-hw, by); out.lineTo(-hw, -by); out.lineTo(-bx, -by); out.close()
            }
            "polygon" -> {
                val sides = sides.coerceIn(3, 24)
                for (i in 0 until sides) {
                    val ang = -Math.PI / 2 + i * 2 * Math.PI / sides
                    val px = (Math.cos(ang) * w / 2f).toFloat()
                    val py = (Math.sin(ang) * h / 2f).toFloat()
                    if (i == 0) out.moveTo(px, py) else out.lineTo(px, py)
                }
                out.close()
            }
            else -> {
                rc.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
                val r = min(radius, min(w, h) / 2f)
                if (r > 0.5f) out.addRoundRect(rc, r, r, Path.Direction.CW)
                else out.addRect(rc, Path.Direction.CW)
            }
        }
    }
}
