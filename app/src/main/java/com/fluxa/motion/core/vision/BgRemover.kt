package com.fluxa.motion.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.nio.ByteBuffer

/**
 * Hapus background gambar dengan MediaPipe Image Segmenter (model selfie, offline).
 * Model ini untuk orang/wajah. Hasil berupa bitmap ARGB dengan alpha 0 di background.
 */
object BgRemover {

    @Volatile
    private var segmenter: ImageSegmenter? = null

    private fun segmenter(ctx: Context): ImageSegmenter {
        segmenter?.let { return it }
        synchronized(this) {
            segmenter?.let { return it }
            val base = BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build()
            val opts = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.IMAGE)
                .setOutputCategoryMask(true)
                .setOutputConfidenceMasks(false)
                .build()
            val s = ImageSegmenter.createFromOptions(ctx.applicationContext, opts)
            segmenter = s
            return s
        }
    }

    /** Null bila segmentasi gagal. Kategori 0 dianggap background (konvensi MediaPipe). */
    fun cutout(ctx: Context, src: Bitmap): Bitmap? {
        return try {
            val result = segmenter(ctx).segment(BitmapImageBuilder(src).build())
            val cat = result.categoryMask().orElse(null) ?: return null
            val w = cat.width
            val h = cat.height
            val raw = ByteBufferExtractor.extract(cat)
            val bytes = ByteArray(w * h)
            val dup = raw.duplicate()
            dup.rewind()
            dup.get(bytes, 0, minOf(bytes.size, dup.remaining()))
            // alpha: 255 untuk orang, 0 untuk background
            for (i in bytes.indices) bytes[i] = if (bytes[i].toInt() != 0) -1 else 0
            val alpha = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
            alpha.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
            val scaled = if (w == src.width && h == src.height) alpha
                else Bitmap.createScaledBitmap(alpha, src.width, src.height, true)

            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawBitmap(src, 0f, 0f, null)
            val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
            c.drawBitmap(scaled, 0f, 0f, p)
            if (scaled !== alpha) scaled.recycle()
            alpha.recycle()
            out
        } catch (e: Throwable) {
            null
        }
    }

    private const val MODEL_ASSET = "models/selfie_segmenter.tflite"
}
