package com.fluxa.motion.core.render

import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/** Mode blend layer. null = normal. Dipakai Renderer dan pratinjau di Inspector. */
object BlendFx {
    fun xfer(mode: String): PorterDuffXfermode? = when (mode) {
        "screen" -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        "multiply" -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        "overlay" -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        "lighten" -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        "darken" -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
        "colorDodge" -> PorterDuffXfermode(PorterDuff.Mode.ADD)
        "softLight" -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        else -> null
    }
}
