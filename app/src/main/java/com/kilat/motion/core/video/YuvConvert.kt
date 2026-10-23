package com.kilat.motion.core.video

import kotlin.math.roundToInt

/**
 * Konversi YUV 4:2:0 (layout planar atau semi-planar lewat pixelStride/rowStride) ke ARGB 8-bit.
 * Sekalian memutar sesuai rotasi track dan men-sampling ke ukuran tujuan (nearest, dari tengah piksel).
 *
 * Murni Kotlin tanpa API Android supaya bisa diuji di JVM.
 * Rotasi mengikuti arah putar searah jarum jam (sama seperti METADATA_KEY_VIDEO_ROTATION).
 */
object YuvConvert {

    /**
     * Hasil ARGB berukuran dstW x dstH. [srcW]/[srcH] adalah ukuran frame sebelum rotasi.
     * Untuk rotasi 90/270, ukuran tampil dibalik, jadi [dstW]/[dstH] harus sesuai ukuran tampil.
     */
    fun toArgb(
        yData: ByteArray, yRow: Int, yPix: Int,
        uData: ByteArray, uRow: Int, uPix: Int,
        vData: ByteArray, vRow: Int, vPix: Int,
        srcW: Int, srcH: Int,
        rotation: Int, dstW: Int, dstH: Int, bt709: Boolean,
    ): IntArray {
        val rot = ((rotation % 360) + 360) % 360
        val rotated = rot == 90 || rot == 270
        val fullW = if (rotated) srcH else srcW   // ukuran tampil (sebelum skala)
        val fullH = if (rotated) srcW else srcH
        val out = IntArray(dstW * dstH)

        val crv: Float; val cgu: Float; val cgv: Float; val cbu: Float
        if (bt709) {
            crv = 1.793f; cgu = 0.213f; cgv = 0.533f; cbu = 2.112f
        } else {
            crv = 1.596f; cgu = 0.392f; cgv = 0.813f; cbu = 2.017f
        }

        for (dy in 0 until dstH) {
            val ry = ((dy * 2 + 1) * fullH) / (2 * dstH)
            for (dx in 0 until dstW) {
                val rx = ((dx * 2 + 1) * fullW) / (2 * dstW)
                val sx: Int
                val sy: Int
                when (rot) {
                    90 -> { sx = ry; sy = srcH - 1 - rx }
                    270 -> { sx = srcW - 1 - ry; sy = rx }
                    180 -> { sx = srcW - 1 - rx; sy = srcH - 1 - ry }
                    else -> { sx = rx; sy = ry }
                }
                val yi = sy * yRow + sx * yPix
                val cx = sx / 2
                val cy = sy / 2
                val ui = cy * uRow + cx * uPix
                val vi = cy * vRow + cx * vPix

                val y = ((yData[yi].toInt() and 0xFF) - 16) * 1.164f
                val u = (uData[ui].toInt() and 0xFF) - 128f
                val v = (vData[vi].toInt() and 0xFF) - 128f

                val r = clamp255(y + crv * v)
                val g = clamp255(y - cgu * u - cgv * v)
                val b = clamp255(y + cbu * u)
                out[dy * dstW + dx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    private fun clamp255(x: Float): Int = x.roundToInt().coerceIn(0, 255)
}
