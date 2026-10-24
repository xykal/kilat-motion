package com.fluxa.motion.core.render

import android.content.Context
import android.graphics.Typeface

/**
 * Font bawaan dari assets/fonts (lisensi OFL). [init] dipanggil sekali dari editor.
 * Kunci yang tidak ada di sini tetap memakai font sistem seperti sebelumnya.
 */
object FontBank {
    private val files = mapOf(
        "poppins_black" to "fonts/poppins-black.ttf",
        "poppins" to "fonts/poppins-regular.ttf",
        "space_mono" to "fonts/spacemono-regular.ttf",
        "space_mono_bold" to "fonts/spacemono-bold.ttf",
        "playfair" to "fonts/playfair.ttf",
        "bebas" to "fonts/bebas.ttf"
    )

    val KEYS: Set<String> = files.keys

    private var appCtx: Context? = null
    private val cache = HashMap<String, Typeface>()

    fun init(c: Context) {
        if (appCtx == null) appCtx = c.applicationContext
    }

    /** Typeface bawaan untuk [key], atau null bila bukan font bawaan / gagal dimuat. */
    fun bundled(key: String): Typeface? {
        cache[key]?.let { return it }
        val path = files[key] ?: return null
        val c = appCtx ?: return null
        val tf = try {
            Typeface.createFromAsset(c.assets, path)
        } catch (e: Exception) {
            return null
        }
        cache[key] = tf
        return tf
    }
}
