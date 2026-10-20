package com.kilat.motion.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/**
 * Ubah video -> audio: ambil track audio dari file video (MP4/MOV/3GP) TANPA re-encode
 * (sample AAC disalin apa adanya ke container M4A). Cepat dan tidak menurunkan kualitas.
 */
object VideoAudio {

    data class Result(val path: String?, val error: String?)

    fun extractToM4a(context: Context, uri: Uri, outFile: File): Result {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(context, uri, null)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = i
                    fmt = f
                    break
                }
            }
            if (track < 0 || fmt == null) return Result(null, "Video ini tidak punya track audio.")
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime != MediaFormat.MIMETYPE_AUDIO_AAC) {
                return Result(null, "Audio video ini ($mime) belum didukung. Coba video dengan audio AAC.")
            }
            ex.selectTrack(track)
            if (outFile.exists()) outFile.delete()
            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val dst = muxer.addTrack(fmt)
            muxer.start()
            val buf = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            var base = -1L
            var samples = 0
            while (true) {
                buf.clear()
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                val ts = ex.sampleTime
                if (base < 0) base = ts
                info.offset = 0
                info.size = n
                info.presentationTimeUs = ts - base
                info.flags = if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(dst, buf, info)
                samples++
                ex.advance()
            }
            muxer.stop()
            muxer.release()
            if (samples == 0) {
                outFile.delete()
                Result(null, "Track audio kosong.")
            } else {
                Result(outFile.absolutePath, null)
            }
        } catch (e: Throwable) {
            outFile.delete()
            Result(null, "Gagal ekstrak audio: ${e.message ?: "tidak diketahui"}")
        } finally {
            ex.release()
        }
    }
}
