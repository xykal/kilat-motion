package com.fluxa.motion.ui.components

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Rentang slider murni (tanpa Android) supaya bisa diuji di JVM. */
object SliderRange {

    /** Batas dasar slider dimensi (lebar/tinggi) dalam satuan project. */
    const val DIM_BASE = 2000

    /** Batas atas absolut agar slider tetap bisa dipakai. */
    const val DIM_CAP = 20000

    /**
     * Maks slider untuk sebuah dimensi. Kalau nilai layer melebihi [base], rentang
     * dinaikkan supaya thumb tidak menempel di ujung dan tidak berbohong soal nilainya.
     */
    fun dimMax(value: Float, base: Int = DIM_BASE): Int {
        if (value.isNaN() || value.isInfinite()) return base
        return max(base, min(DIM_CAP, ceil(value.toDouble()).toInt()))
    }

    /** Posisi thumb untuk nilai tertentu, dibatasi ke [0, max]. */
    fun progressOf(value: Float, max: Int): Int {
        if (value.isNaN()) return 0
        return kotlin.math.round(value).toInt().coerceIn(0, max.coerceAtLeast(0))
    }
}
