package com.kilat.motion.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import com.kilat.motion.core.model.Project
import com.kilat.motion.core.render.Renderer
import kotlin.math.max
import kotlin.math.min

/** Render frame project jadi bitmap kecil untuk kartu di homescreen. */
object Thumbnails {

    fun render(project: Project, widthPx: Int, time: Float): Bitmap? {
        return try {
            if (project.width <= 0 || project.height <= 0 || widthPx <= 0) return null
            val w = widthPx
            val h = max(2, (widthPx.toFloat() * project.height / project.width).toInt())
            val bmp = Bitmap.createBitmap(w, min(h, widthPx * 3), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.scale(w.toFloat() / project.width, w.toFloat() / project.width)
            Renderer.renderFrame(canvas, project, time.coerceIn(0f, project.duration), true)
            bmp
        } catch (e: Throwable) {
            null
        }
    }

    /** Jadikan sudut ImageView rounded (tanpa drawable bawaan). */
    fun roundCorners(view: ImageView, radiusPx: Float) {
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, radiusPx)
            }
        }
        view.clipToOutline = true
    }
}
