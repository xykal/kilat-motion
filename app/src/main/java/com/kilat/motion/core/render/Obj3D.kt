package com.kilat.motion.core.render

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Mesh 3D sederhana untuk renderer software Canvas:
 * - primitif bawaan (kubus, Piramida, prisma, oktahedron),
 * - parser file .OBJ (vertex "v" + face "f", poligon di-fan jadi segitiga),
 * - semua mesh dinormalisasi ke radius 1 di sekitar titik asal.
 */
object Obj3D {

    class Mesh(
        val verts: Array<FloatArray>,   // [x, y, z]
        val faces: Array<IntArray>      // indeks vertex (3 = segitiga, 4 = quad)
    )

    fun primitive(kind: String): Mesh = when (kind) {
        "pyramid" -> Mesh(
            arrayOf(
                floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
                floatArrayOf(1f, -1f, 1f), floatArrayOf(-1f, -1f, 1f),
                floatArrayOf(0f, 1.1f, 0f)
            ),
            arrayOf(
                intArrayOf(0, 1, 4), intArrayOf(1, 2, 4), intArrayOf(2, 3, 4), intArrayOf(3, 0, 4),
                intArrayOf(3, 2, 1, 0)
            )
        )
        "prism" -> Mesh(
            arrayOf(
                floatArrayOf(-1f, -0.8f, -1f), floatArrayOf(1f, -0.8f, -1f), floatArrayOf(0f, 0.9f, -1f),
                floatArrayOf(-1f, -0.8f, 1f), floatArrayOf(1f, -0.8f, 1f), floatArrayOf(0f, 0.9f, 1f)
            ),
            arrayOf(
                intArrayOf(0, 2, 1), intArrayOf(3, 4, 5),
                intArrayOf(0, 1, 4, 3), intArrayOf(1, 2, 5, 4), intArrayOf(2, 0, 3, 5)
            )
        )
        "octa" -> Mesh(
            arrayOf(
                floatArrayOf(0f, 1.2f, 0f), floatArrayOf(0f, -1.2f, 0f),
                floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f),
                floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f)
            ),
            arrayOf(
                intArrayOf(0, 4, 2), intArrayOf(0, 2, 5), intArrayOf(0, 5, 3), intArrayOf(0, 3, 4),
                intArrayOf(1, 2, 4), intArrayOf(1, 5, 2), intArrayOf(1, 3, 5), intArrayOf(1, 4, 3)
            )
        )
        else -> Mesh( // cube
            arrayOf(
                floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
                floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
                floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f),
                floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f)
            ),
            arrayOf(
                intArrayOf(0, 1, 2, 3), intArrayOf(5, 4, 7, 6),
                intArrayOf(0, 4, 5, 1), intArrayOf(2, 6, 7, 3),
                intArrayOf(0, 3, 7, 4), intArrayOf(1, 5, 6, 2)
            )
        )
    }

    /** Parse teks .OBJ. Mengembalikan null bila tidak ada vertex/face yang valid. */
    fun parseObj(text: String): Mesh? {
        val verts = ArrayList<FloatArray>()
        val faces = ArrayList<IntArray>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split(Regex("\\s+"))
            when (parts[0]) {
                "v" -> {
                    if (parts.size >= 4) {
                        val x = parts[1].toFloatOrNull()
                        val y = parts[2].toFloatOrNull()
                        val z = parts[3].toFloatOrNull()
                        if (x != null && y != null && z != null) verts.add(floatArrayOf(x, y, z))
                    }
                }
                "f" -> {
                    if (parts.size >= 4) {
                        val idx = ArrayList<Int>()
                        var ok = true
                        for (i in 1 until parts.size) {
                            val token = parts[i].substringBefore('/')
                            var vi = token.toIntOrNull()
                            if (vi == null) { ok = false; break }
                            if (vi < 0) vi = verts.size + vi + 1 // indeks relatif negatif OBJ
                            val zero = vi - 1
                            if (zero < 0 || zero >= verts.size) { ok = false; break }
                            idx.add(zero)
                        }
                        if (ok && idx.size >= 3) {
                            // fan-triangulasi poligon
                            for (i in 1 until idx.size - 1) {
                                faces.add(intArrayOf(idx[0], idx[i], idx[i + 1]))
                            }
                        }
                    }
                }
            }
        }
        if (verts.isEmpty() || faces.isEmpty()) return null
        return normalize(Mesh(verts.toTypedArray(), faces.toTypedArray()))
    }

    /** Geser ke pusat (0,0,0) dan skala agar radius terjauh = 1. Sumbu Y dibalik (OBJ Y-up -> layar Y-down). */
    fun normalize(m: Mesh): Mesh {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (v in m.verts) {
            if (v[0] < minX) minX = v[0]; if (v[0] > maxX) maxX = v[0]
            if (v[1] < minY) minY = v[1]; if (v[1] > maxY) maxY = v[1]
            if (v[2] < minZ) minZ = v[2]; if (v[2] > maxZ) maxZ = v[2]
        }
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val cz = (minZ + maxZ) / 2f
        var radius = 0.001f
        for (v in m.verts) {
            val dx = v[0] - cx; val dy = v[1] - cy; val dz = v[2] - cz
            radius = max(radius, sqrt(dx * dx + dy * dy + dz * dz))
        }
        val s = 1f / radius
        val out = Array(m.verts.size) { i ->
            val v = m.verts[i]
            floatArrayOf((v[0] - cx) * s, -(v[1] - cy) * s, (v[2] - cz) * s)
        }
        return Mesh(out, m.faces)
    }
}
