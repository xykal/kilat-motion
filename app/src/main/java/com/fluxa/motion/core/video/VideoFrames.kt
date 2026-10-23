package com.fluxa.motion.core.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.fluxa.motion.core.render.EffectMath

/**
 * Ambil frame dari file video untuk layer video. Tanpa playback audio.
 * Hasil frame di-cache per file (satu frame terakhir per file), dan waktu dibulatkan ke fps
 * supaya render yang sama tidak mengulang decode.
 *
 * [sequential] = true (export): frame di-decode berurutan lewat [SequentialVideoReader].
 * Kalau decoder gagal, file itu otomatis beralih ke MediaMetadataRetriever.
 * false (UI/preview): selalu MediaMetadataRetriever.
 *
 * Satu instance dipakai satu thread: [ui] untuk UI, dan export membuat instance sendiri.
 * Bitmap hasil cache TIDAK di-recycle saat diganti, karena pemanggil mungkin masih menggambarnya.
 */
class VideoFrames(private val sequential: Boolean = false) {

    data class Info(val width: Int, val height: Int, val durationSec: Float)

    private class Entry(val path: String, val retriever: MediaMetadataRetriever, val rotation: Int) {
        var lastIndex = -1
        var lastMax = -1
        var lastFrame: Bitmap? = null
        var seq: SequentialVideoReader? = null
        var seqFailed = false
    }

    private val entries = LinkedHashMap<String, Entry>()

    // statistik untuk export (ms per frame)
    private var decodeNs = 0L
    private var frameCalls = 0
    private var seqFallbacks = 0

    /** Info dasar video (ukuran sudah memperhitungkan rotasi, durasi dalam detik). */
    @Synchronized
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
            try { r.release() } catch (e: Exception) {}
        }
    }

    /**
     * Frame pada waktu [sec] (detik dari awal video), sisi panjang dibatasi [maxLong].
     * Null kalau gagal.
     */
    @Synchronized
    fun frameAt(path: String, sec: Float, maxLong: Int = 960): Bitmap? {
        val idx = EffectMath.videoFrameIndex(sec, 30f, Int.MAX_VALUE)
        val e = entryFor(path) ?: return null
        val cached = e.lastFrame
        // cache hanya dipakai kalau waktu DAN resolusi sama (export butuh resolusi lebih tinggi dari preview)
        if (idx == e.lastIndex && maxLong == e.lastMax && cached != null && !cached.isRecycled) return cached
        val t0 = System.nanoTime()
        val frame = (if (sequential && !e.seqFailed) fromSequential(e, idx, maxLong) else null)
            ?: fromRetriever(e, idx, maxLong)
            ?: return cached
        decodeNs += System.nanoTime() - t0
        frameCalls++
        e.lastFrame = frame
        e.lastIndex = idx
        e.lastMax = maxLong
        return frame
    }

    private fun fromSequential(e: Entry, idx: Int, maxLong: Int): Bitmap? {
        return try {
            val r = e.seq ?: SequentialVideoReader(e.path, e.rotation).also { e.seq = it }
            r.frameAt(idx * 1_000_000L / 30L, HALF_FRAME_US, maxLong)
        } catch (ex: Throwable) {
            // decoder gagal di perangkat ini: sisa file ini lewat MediaMetadataRetriever
            e.seqFailed = true
            seqFallbacks++
            try { e.seq?.close() } catch (x: Exception) {}
            e.seq = null
            null
        }
    }

    private fun fromRetriever(e: Entry, idx: Int, maxLong: Int): Bitmap? {
        val us = idx * 1_000_000L / 30L
        val raw = try {
            e.retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (ex: Throwable) {
            null
        } ?: return null
        val scaled = downscale(raw, maxLong)
        if (scaled !== raw) raw.recycle()   // raw baru dari decoder, belum dibagikan
        return scaled
    }

    /** Ringkasan untuk ditampilkan setelah export: jumlah frame, rata-rata ms per frame, dan fallback. */
    @Synchronized
    fun statsLine(): String {
        if (frameCalls == 0) return "0 frame"
        val avg = decodeNs / 1_000_000.0 / frameCalls
        val fb = if (seqFallbacks > 0) ", $seqFallbacks file pindah ke decoder cadangan" else ""
        return "$frameCalls frame, rata-rata %.1f ms/frame$fb".format(avg)
    }

    /** Lepas semua retriever dan decoder, buang frame cache. Bitmap dibiarkan untuk GC. */
    @Synchronized
    fun clear() {
        for (e in entries.values) {
            try { e.seq?.close() } catch (ex: Exception) {}
            e.seq = null
            try { e.retriever.release() } catch (ex: Exception) {}
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
                    try { old.seq?.close() } catch (ex: Exception) {}
                    try { old.retriever.release() } catch (ex: Exception) {}
                }
            }
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            Entry(path, r, rot).also { entries[path] = it }
        } catch (e: Throwable) {
            try { r.release() } catch (ex: Exception) {}
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

    companion object {
        private const val MAX_ENTRIES = 3
        private const val HALF_FRAME_US = 16_666L

        /** Instance untuk UI (preview, inspector, editor). Hanya dipakai dari main thread. */
        val ui: VideoFrames by lazy { VideoFrames() }

        fun info(path: String): Info? = ui.info(path)

        fun frameAt(path: String, sec: Float, maxLong: Int = 960): Bitmap? = ui.frameAt(path, sec, maxLong)

        fun clear() = ui.clear()
    }
}
