package com.fluxa.motion.ui.components

import android.graphics.Bitmap
import android.util.Log
import android.graphics.Canvas
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import com.fluxa.motion.core.model.Project
import com.fluxa.motion.core.render.Renderer
import kotlin.math.max
import kotlin.math.min

/** Render frame project jadi bitmap kecil untuk kartu di homescreen. */
object Thumbnails {

    /** DIAGNOSIS SEMENTARA: render tiap layer sendirian lalu catat kecerahannya. */
    private fun diagLayers(project: Project, t: Float) {
        val hidden = project.layers.map { it.hidden }
        project.layers.forEachIndexed { i, l ->
            project.layers.forEach { it.hidden = true }
            l.hidden = false
            val bmp = Bitmap.createBitmap(120, 213, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(120f / project.width, 120f / project.width)
            Renderer.renderFrame(c, project, t, true)
            var sum = 0L
            var n = 0
            for (yy in 0 until bmp.height step 8) for (xx in 0 until bmp.width step 8) {
                val px = bmp.getPixel(xx, yy)
                sum += ((px shr 16) and 255) + ((px shr 8) and 255) + (px and 255)
                n++
            }
            Log.i("FluxaDiag", "${project.name} #$i ${l.name} kind=${l.kind} shape=${l.shape} blend=${l.blend} alpha=${l.alpha} br=${sum / (3 * n)}")
        }
        project.layers.forEachIndexed { i, l -> l.hidden = hidden[i] }
    }

    fun render(project: Project, widthPx: Int, time: Float): Bitmap? {
        if (project.name == "JJ Velocity") diagLayers(project, time)
        return try {
            if (project.width <= 0 || project.height <= 0 || widthPx <= 0) return null
            val w = widthPx
            val h = max(2, (widthPx.toFloat() * project.height / project.width).toInt())
            val bmp = Bitmap.createBitmap(w, min(h, widthPx * 3), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.scale(w.toFloat() / project.width, w.toFloat() / project.width)
            Renderer.renderFrame(canvas, project, time.coerceIn(0f, project.duration), true)
            // diagnosis: rata-rata kecerahan sampel piksel (0..255)
            var sum = 0L
            var n = 0
            for (yy in 0 until bmp.height step 16) for (xx in 0 until bmp.width step 16) {
                val c = bmp.getPixel(xx, yy)
                sum += ((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255)
                n++
            }
            Log.i("FluxaThumb", "${project.name} t=$time kecerahan=${if (n > 0) sum / (3 * n) else -1}")
            bmp
        } catch (e: Throwable) {
            Log.e("FluxaThumb", "render thumbnail gagal: ${project.name}", e)
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
