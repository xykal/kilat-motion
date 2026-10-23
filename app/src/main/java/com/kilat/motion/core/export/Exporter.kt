package com.kilat.motion.core.export

import com.kilat.motion.core.model.*
import com.kilat.motion.core.render.Renderer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Export MP4 tanpa library eksternal:
 * MediaCodec (H.264, input Surface) + MediaMuxer.
 * Render frame berjalan real-time sesuai durasi project, jadi timestamp video akurat.
 */
object Exporter {

    data class VideoResult(val uri: Uri?, val file: File?, val error: String?)

    private class MuxState {
        var muxer: MediaMuxer? = null
        var track = -1
        var started = false
        var audioTrack = -1
    }

    /** Penulis audio progresif: salin sample terkompresi dari file musik ke muxer. */
    private class AudioWriter(
        val extractor: MediaExtractor,
        val muxer: MediaMuxer,
        val track: Int,
        val startUs: Long,
        val offsetUs: Long,
        val endUs: Long
    ) {
        val info = MediaCodec.BufferInfo()
        val buf: java.nio.ByteBuffer = java.nio.ByteBuffer.allocateDirect(1 shl 19)
        var pending = false
        var eof = false

        fun writeUntil(untilUs: Long) {
            while (!eof) {
                if (!pending) {
                    buf.clear()
                    val size = try { extractor.readSampleData(buf, 0) } catch (e: Throwable) { -1 }
                    if (size < 0) { eof = true; break }
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                        MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    pending = true
                    try { extractor.advance() } catch (e: Throwable) { eof = true }
                }
                val adj = startUs + (info.presentationTimeUs - offsetUs)
                if (adj < 0) { pending = false; continue }
                if (adj > endUs) { eof = true; break }
                if (adj > untilUs) break
                info.presentationTimeUs = adj
                try { muxer.writeSampleData(track, buf, info) } catch (e: Throwable) {}
                pending = false
            }
        }
    }

    /**
     * Export video. Jalur cepat (tanpa menunggu waktu nyata) dipakai kalau libkilat_timing tersedia.
     * Hasilnya diverifikasi durasinya. Kalau gagal atau tidak cocok, ulang dengan jalur real-time.
     */
    fun exportVideo(
        context: Context,
        project: Project,
        longSide: Int,
        fps: Int,
        bitrateMbps: Int = 0,
        onProgress: (Float) -> Unit
    ): VideoResult {
        val frameRate = fps.coerceIn(15, 120)
        if (NativeTiming.available) {
            val fast = exportVideoOnce(context, project, longSide, fps, bitrateMbps, true, onProgress)
            val file = fast.file
            if (fast.error == null && file != null && verifyDuration(file, project.duration, frameRate)) {
                return fast
            }
            file?.delete()
        }
        return exportVideoOnce(context, project, longSide, fps, bitrateMbps, false, onProgress)
    }

    /** Cek durasi video hasil export terhadap durasi project. */
    fun verifyDuration(file: File, expectedSec: Float, fps: Int): Boolean {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(file.absolutePath)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/")) continue
                val durSec = if (f.containsKey(MediaFormat.KEY_DURATION)) {
                    f.getLong(MediaFormat.KEY_DURATION) / 1_000_000f
                } else {
                    // tanpa durasi di header: pakai timestamp sampel terakhir + satu frame
                    ex.selectTrack(i)
                    var last = 0L
                    while (true) {
                        val ts = ex.sampleTime
                        if (ts < 0) break
                        if (ts > last) last = ts
                        if (!ex.advance()) break
                    }
                    last / 1_000_000f + 1f / fps
                }
                val tol = max(0.25f, expectedSec * 0.02f)
                return abs(durSec - expectedSec) <= tol
            }
            false
        } catch (e: Throwable) {
            false
        } finally {
            try { ex.release() } catch (e: Exception) {}
        }
    }

    private fun exportVideoOnce(
        context: Context,
        project: Project,
        longSide: Int,
        fps: Int,
        bitrateMbps: Int,
        native: Boolean,
        onProgress: (Float) -> Unit
    ): VideoResult {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var tmp: File? = null
        try {
            val aspect = project.width.toFloat() / project.height.toFloat()
            var outW: Int
            var outH: Int
            if (project.width >= project.height) {
                outW = longSide
                outH = (longSide / aspect).roundToInt()
            } else {
                outH = longSide
                outW = (longSide * aspect).roundToInt()
            }
            outW = max(2, outW / 2 * 2)
            outH = max(2, outH / 2 * 2)

            val frameRate = fps.coerceIn(15, 120)
            val totalFrames = max(1, (project.duration * frameRate).roundToInt())
            // bitrate mengikuti luas frame x fps (0.10 bit/pixel/frame), dibatasi aman untuk AVC
            // bitrate manual (Mbps) dari pengaturan export; 0 = otomatis dari luas frame x fps
            val bitrate = if (bitrateMbps > 0) {
                bitrateMbps.coerceIn(1, 80) * 1_000_000
            } else {
                (outW.toLong() * outH * frameRate * 0.10).toLong().coerceIn(4_000_000L, 80_000_000L).toInt()
            }
            // instance sendiri: tidak berbagi scratch state & cache dengan UI thread
            val renderer = Renderer()

            tmp = File(context.cacheDir, "kilat_export_${System.currentTimeMillis()}.mp4")
            if (tmp.exists()) tmp.delete()

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()

            muxer = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val state = MuxState()
            state.muxer = muxer

            // ---- siapkan audio (kalau ada musik latar) ----
            var audioExtractor: MediaExtractor? = null
            var audioWriter: AudioWriter? = null
            val music = project.music
            if (music != null && music.path.isNotEmpty()) {
                try {
                    val ex = MediaExtractor()
                    ex.setDataSource(music.path)
                    var ai = -1
                    var afmt: MediaFormat? = null
                    for (i in 0 until ex.trackCount) {
                        val f = ex.getTrackFormat(i)
                        val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                        if (mime.startsWith("audio/")) { ai = i; afmt = f; break }
                    }
                    if (ai >= 0 && afmt != null) {
                        ex.selectTrack(ai)
                        state.audioTrack = muxer.addTrack(afmt)
                        audioExtractor = ex
                        try { ex.seekTo((music.offset * 1_000_000f).toLong(), MediaExtractor.SEEK_TO_CLOSEST_SYNC) } catch (e: Throwable) {}
                        audioWriter = AudioWriter(
                            ex, muxer, state.audioTrack,
                            (music.start * 1_000_000f).toLong(),
                            (music.offset * 1_000_000f).toLong(),
                            ((music.start + music.dur) * 1_000_000f).toLong()
                        )
                    } else {
                        ex.release()
                    }
                } catch (e: Throwable) {
                    audioExtractor = null
                    audioWriter = null
                }
            }

            val scaleX = outW.toFloat() / project.width
            val scaleY = outH.toFloat() / project.height
            val frameNs = 1_000_000_000L / frameRate
            val t0 = SystemClock.elapsedRealtimeNanos()
            var lastPct = -1

            for (frame in 0 until totalFrames) {
                if (!native) {
                    // jalur real-time: pacing biar timestamp & durasi video pas
                    val target = t0 + frame * frameNs
                    while (true) {
                        val now = SystemClock.elapsedRealtimeNanos()
                        val remain = target - now
                        if (remain <= 0) break
                        val ms = remain / 1_000_000
                        if (ms > 1) Thread.sleep(ms - 1) else Thread.yield()
                    }
                }

                val t = frame.toFloat() / frameRate
                var canvas: Canvas? = null
                var attempts = 0
                while (canvas == null && attempts < 60) {
                    canvas = surface.lockCanvas(null)
                    if (canvas == null) Thread.sleep(2)
                    attempts++
                }
                if (canvas != null) {
                    canvas.drawColor(project.bg)
                    canvas.save()
                    canvas.scale(scaleX, scaleY)
                    renderer.renderFrame(canvas, project, t, false)
                    canvas.restore()
                    if (native && !NativeTiming.setPresentationNs(surface, frame * frameNs)) {
                        throw IllegalStateException("timestamp native gagal")
                    }
                    surface.unlockCanvasAndPost(canvas)
                }
                drain(codec, state, false)
                if (state.started) {
                    try { audioWriter?.writeUntil((t * 1_000_000f).toLong() + 200_000L) } catch (e: Throwable) {}
                }

                val pct = (frame * 100 / totalFrames)
                if (pct != lastPct) {
                    lastPct = pct
                    onProgress(frame.toFloat() / totalFrames)
                }
            }

            codec.signalEndOfInputStream()
            drain(codec, state, true)
            try { audioWriter?.writeUntil(Long.MAX_VALUE / 4) } catch (e: Throwable) {}
            try { audioExtractor?.release() } catch (e: Exception) {}
            onProgress(1f)

            try {
                codec.stop()
            } catch (e: Throwable) {
            }
            codec.release()
            codec = null
            try {
                muxer.stop()
                muxer.release()
            } catch (e: Throwable) {
                throw IllegalStateException("Muxer gagal: ${e.message}")
            }
            muxer = null

            // render selesai ke cache; penyimpanan ke galeri dilakukan setelah user review di preview
            return VideoResult(null, tmp, null)
        } catch (e: Throwable) {
            try {
                codec?.release()
            } catch (t: Throwable) {
            }
            try {
                muxer?.release()
            } catch (t: Throwable) {
            }
            try {
                tmp?.delete()
            } catch (t: Throwable) {
            }
            return VideoResult(null, null, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun drain(codec: MediaCodec, state: MuxState, endOfStream: Boolean): Boolean {
        val info = MediaCodec.BufferInfo()
        val timeoutUs = if (endOfStream) 10_000L else 0L
        while (true) {
            val status = try {
                codec.dequeueOutputBuffer(info, timeoutUs)
            } catch (e: Throwable) {
                return true
            }
            when {
                status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return false
                }
                status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val m = state.muxer ?: return true
                    if (state.track < 0) {
                        state.track = m.addTrack(codec.outputFormat)
                    }
                    if (!state.started) {
                        m.start()
                        state.started = true
                    }
                }
                status >= 0 -> {
                    val buf = codec.getOutputBuffer(status)
                    if (buf != null && info.size > 0 && state.started) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        try {
                            state.muxer?.writeSampleData(state.track, buf, info)
                        } catch (e: Throwable) {
                        }
                    }
                    codec.releaseOutputBuffer(status, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                }
            }
        }
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun publishVideo(context: Context, file: File): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "KILAT_${stamp()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/KilatMotion")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Throwable) {
            try {
                resolver.delete(uri, null, null)
            } catch (t: Throwable) {
            }
            return null
        }
    }

    // ---------------- PNG frame ----------------
    fun exportFramePng(context: Context, project: Project, t: Float): Uri? {
        return try {
            val bmp = Bitmap.createBitmap(project.width, project.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            Renderer().renderFrame(canvas, project, t, true)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "KILAT_frame_${stamp()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KilatMotion")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            bmp.recycle()
            uri
        } catch (e: Throwable) {
            null
        }
    }
}
