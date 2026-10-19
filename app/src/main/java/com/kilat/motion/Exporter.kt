package com.kilat.motion

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
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
    }

    fun exportVideo(
        context: Context,
        project: Project,
        longSide: Int,
        fps: Int,
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

            val totalFrames = max(1, (project.duration * fps).roundToInt())
            val frameRate = fps.coerceIn(15, 60)
            val bitrate = (outW * outH * 4).coerceIn(2_000_000, 14_000_000)

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

            val scaleX = outW.toFloat() / project.width
            val scaleY = outH.toFloat() / project.height
            val frameNs = 1_000_000_000L / frameRate
            val t0 = SystemClock.elapsedRealtimeNanos()
            var lastPct = -1

            for (frame in 0 until totalFrames) {
                // pacing real-time biar timestamp & durasi video pas
                val target = t0 + frame * frameNs
                while (true) {
                    val now = SystemClock.elapsedRealtimeNanos()
                    val remain = target - now
                    if (remain <= 0) break
                    val ms = remain / 1_000_000
                    if (ms > 1) Thread.sleep(ms - 1) else Thread.yield()
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
                    Renderer.renderFrame(canvas, project, t, false)
                    canvas.restore()
                    surface.unlockCanvasAndPost(canvas)
                }
                drain(codec, state, false)

                val pct = (frame * 100 / totalFrames)
                if (pct != lastPct) {
                    lastPct = pct
                    onProgress(frame.toFloat() / totalFrames)
                }
            }

            codec.signalEndOfInputStream()
            drain(codec, state, true)
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

            val uri = publishVideo(context, tmp)
            return VideoResult(uri, tmp, null)
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
                    if (!state.started) {
                        state.track = m.addTrack(codec.outputFormat)
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

    private fun publishVideo(context: Context, file: File): Uri? {
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
            Renderer.renderFrame(canvas, project, t, true)
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
