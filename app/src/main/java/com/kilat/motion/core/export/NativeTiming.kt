package com.kilat.motion.core.export

import android.util.Log
import android.view.Surface

/**
 * Jembatan ke libkilat_timing.so. Kalau library tidak ada (mis. ABI tidak didukung),
 * [available] = false dan Exporter memakai jalur real-time yang lama.
 */
object NativeTiming {

    private const val TAG = "NativeTiming"

    private external fun nativeProbe(): Boolean
    private external fun nativeSetPresentationNs(surface: Surface, ns: Long): Boolean

    /** True hanya kalau library ada DAN perangkat menyediakan simbol timestamp. */
    val available: Boolean = try {
        System.loadLibrary("kilat_timing")
        nativeProbe()
    } catch (e: Throwable) {
        Log.w(TAG, "timestamp native tidak tersedia, export pakai jalur real-time", e)
        false
    }

    /** Atur timestamp frame berikutnya. False kalau gagal, dan pemanggil harus mundur. */
    fun setPresentationNs(surface: Surface, ns: Long): Boolean {
        if (!available) return false
        return try {
            nativeSetPresentationNs(surface, ns)
        } catch (e: Throwable) {
            false
        }
    }
}
