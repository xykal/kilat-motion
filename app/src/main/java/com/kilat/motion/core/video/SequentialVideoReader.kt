package com.kilat.motion.core.video

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import com.kilat.motion.core.render.EffectMath
import java.nio.ByteBuffer

/**
 * Decoder sekuensial untuk export. Frame di-decode berurutan lewat MediaCodec,
 * jadi export tidak perlu seek + decode dari keyframe untuk setiap frame.
 *
 * Satu instance dipakai satu thread. Frame yang diminta harus cenderung maju; kalau mundur jauh
 * atau melompat lebih dari [SEEK_GAP_US], reader melakukan seek ke keyframe sebelumnya.
 * Kegagalan apa pun dilempar ke pemanggil, yang lalu beralih ke MediaMetadataRetriever.
 */
class SequentialVideoReader(path: String, private val rotation: Int) : AutoCloseable {

    private class Plane(val data: ByteArray, val row: Int, val pix: Int)

    private class Yuv(val pts: Long, val w: Int, val h: Int, val y: Plane, val u: Plane, val v: Plane)

    private val extractor = MediaExtractor()
    private val codec: MediaCodec
    private val bt709: Boolean
    private var inputDone = false
    private var outputDone = false
    private var cur: Yuv? = null
    private var pending: Yuv? = null

    init {
        extractor.setDataSource(path)
        var trackIdx = -1
        var fmt: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) {
                trackIdx = i
                fmt = f
                break
            }
        }
        if (trackIdx < 0 || fmt == null) throw IllegalStateException("tidak ada track video")
        extractor.selectTrack(trackIdx)
        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: throw IllegalStateException("mime kosong")
        // minSdk 29: KEY_COLOR_STANDARD tersedia. Tanpa tag, video HD dianggap BT.709.
        val std = if (fmt.containsKey(MediaFormat.KEY_COLOR_STANDARD)) fmt.getInteger(MediaFormat.KEY_COLOR_STANDARD) else -1
        val h = fmt.getInteger(MediaFormat.KEY_HEIGHT)
        bt709 = if (std >= 0) std == MediaFormat.COLOR_STANDARD_BT709 else h >= 720
        // minta output YUV yang bisa diambil lewat getOutputImage (tanpa surface)
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        codec = MediaCodec.createDecoderByType(mime)
        codec.configure(fmt, null, null, 0)
        codec.start()
    }

    /**
     * Frame pada waktu [targetUs] (mikrodetik). Pilih frame terakhir yang pts-nya <= target + [halfUs],
     * sehingga hasilnya sama dengan frame terdekat di grid fps.
     */
    fun frameAt(targetUs: Long, halfUs: Long, maxLong: Int): Bitmap? {
        val c = cur
        if (c == null || targetUs < c.pts - halfUs || targetUs - c.pts > SEEK_GAP_US) seek(targetUs)
        val bound = targetUs + halfUs
        pending?.let {
            if (it.pts <= bound) {
                cur = it
                pending = null
            }
        }
        var spins = 0
        while (pending == null && !outputDone) {
            val f = nextDecoded()
            if (f == null) {
                if (++spins > MAX_SPINS) throw IllegalStateException("decoder tidak menghasilkan frame")
                continue
            }
            spins = 0
            if (f.pts <= bound) cur = f else pending = f
        }
        val pick = cur ?: pending ?: return null
        return toBitmap(pick, maxLong)
    }

    private fun seek(targetUs: Long) {
        extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        codec.flush()
        inputDone = false
        outputDone = false
        cur = null
        pending = null
    }

    private fun feedOneInput() {
        if (inputDone) return
        val inIdx = codec.dequeueInputBuffer(0)
        if (inIdx < 0) return
        val buf: ByteBuffer = codec.getInputBuffer(inIdx) ?: return
        val size = extractor.readSampleData(buf, 0)
        if (size < 0) {
            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
        } else {
            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    /** Satu langkah decode. Null kalau belum ada frame keluar (atau sudah habis). */
    private fun nextDecoded(): Yuv? {
        feedOneInput()
        val info = MediaCodec.BufferInfo()
        val idx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
        if (idx < 0) return null   // INFO_TRY_AGAIN_LATER, perubahan format, dsb.
        val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
        try {
            var result: Yuv? = null
            if (info.size > 0) {
                val img: Image = codec.getOutputImage(idx)
                    ?: throw IllegalStateException("output decoder bukan YUV")
                try {
                    result = copyYuv(img, info.presentationTimeUs)
                } finally {
                    img.close()
                }
            }
            return result
        } finally {
            codec.releaseOutputBuffer(idx, false)
            if (eos) outputDone = true
        }
    }

    private fun copyYuv(img: Image, pts: Long): Yuv {
        val py = img.planes[0]
        val pu = img.planes[1]
        val pv = img.planes[2]
        return Yuv(
            pts, img.width, img.height,
            Plane(bytesOf(py.buffer), py.rowStride, py.pixelStride),
            Plane(bytesOf(pu.buffer), pu.rowStride, pu.pixelStride),
            Plane(bytesOf(pv.buffer), pv.rowStride, pv.pixelStride),
        )
    }

    private fun bytesOf(buf: ByteBuffer): ByteArray {
        val d = buf.duplicate()
        d.rewind()
        val arr = ByteArray(d.remaining())
        d.get(arr)
        return arr
    }

    private fun toBitmap(f: Yuv, maxLong: Int): Bitmap {
        val rotated = rotation == 90 || rotation == 270
        val dispW = if (rotated) f.h else f.w
        val dispH = if (rotated) f.w else f.h
        val (dw, dh) = EffectMath.fitSize(dispW, dispH, maxLong)
        val px = YuvConvert.toArgb(
            f.y.data, f.y.row, f.y.pix,
            f.u.data, f.u.row, f.u.pix,
            f.v.data, f.v.row, f.v.pix,
            f.w, f.h, rotation, dw, dh, bt709,
        )
        return Bitmap.createBitmap(px, dw, dh, Bitmap.Config.ARGB_8888)
    }

    override fun close() {
        try { codec.stop() } catch (e: Exception) {}
        try { codec.release() } catch (e: Exception) {}
        try { extractor.release() } catch (e: Exception) {}
        cur = null
        pending = null
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
        private const val SEEK_GAP_US = 1_500_000L
        private const val MAX_SPINS = 500
    }
}
