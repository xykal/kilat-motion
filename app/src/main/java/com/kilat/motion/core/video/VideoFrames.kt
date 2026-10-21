package com.kilat.motion.core.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.kilat.motion.core.render.EffectMath

/**
 * Ambil frame dari file video untuk layer video. Tanpa playback audio.
 * Hasil frame di-cache per file (satu frame terakhir), dan waktu dibulatkan ke fps
 * supaya render yang sama tidak mengulang decode.
 */
object VideoFrames {

    data class Info(val width: Int, val height: Int, val durationSec: Float)

    private class Entry(val retriever: MediaMetadataRetriever) {
        var lastIndex = -1
        var lastFrame: Bitmap? = null
    }

    private const val MAX_ENTRIES = 3
    private val entries = LinkedHashMap<String, Entry>()

    /** Info dasar video (ukuran sudah memperhitungkan rotasi, durasi dalam detik). */
    fun info(path: String): Info? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (w <= 0 || h <= 0) null else Info(w, h, durMs / 1000f)
        } catch (e: Throwable) {
            null
        } finally {
            try { r.release() } catch (e: Throwable) {}
        }
    }

    /** Frame pada waktu [sec] (detik dari awal video). Null kalau gagal. */
    @Synchronized
    fun frameAt(path: String, sec: Float, maxLong: Int = 960): Bitmap? {
        val idx = EffectMath.videoFrameIndex(sec, 30f, Int.MAX_VALUE)
        val e = entryFor(path) ?: return null
        val cached = e.lastFrame
        if (idx == e.lastIndex && cached != null && !cached.isRecycled) return cached
        val us = idx * 1_000_000L / 30L
        val raw = try {
            e.retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (ex: Throwable) {
            null
        } ?: return cached
        val scaled = downscale(raw, maxLong)
        if (scaled !== raw) raw.recycle()
        e.lastFrame?.takeIf { !it.isRecycled }?.recycle()
        e.lastFrame = scaled
        e.lastIndex = idx
        return scaled
    }

    /** Lepas semua retriever dan frame cache. */
    @Synchronized
    fun clear() {
        for (e in entries.values) {
            try { e.retriever.release() } catch (ex: Throwable) {}
            e.lastFrame?.takeIf { !it.isRecycled }?.recycle()
        }
        entries.clear()
    }

    private fun entryFor(path: String): Entry? {
        entries[path]?.let { return it }
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            if (entries.size >= MAX_ENTRIES) {
                val oldestKey = entries.keys.first()
                entries.remove(oldestKey)?.let { old ->
                    try { old.retriever.release() } catch (ex: Throwable) {}
                    old.lastFrame?.takeIf { !it.isRecycled }?.recycle()
                }
            }
            Entry(r).also { entries[path] = it }
        } catch (e: Throwable) {
            try { r.release() } catch (ex: Throwable) {}
            null
        }
    }

    private fun downscale(src: Bitmap, maxLong: Int): Bitmap {
        val (w, h) = EffectMath.fitSize(src.width, src.height, maxLong)
        if (w == src.width && h == src.height) return src
        return try {
            Bitmap.createScaledBitmap(src, w, h, true)
        } catch (e: Throwable) {
            src
        }
    }
}
