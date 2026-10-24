package com.fluxa.motion.core.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Ubah suara file audio: decode -> geser pitch (SoundTouch, native) -> encode AAC ke M4A.
 * Durasi tidak berubah. Dijalankan di background thread, bukan di UI thread.
 */
object VoiceChanger {

    /** Preset: key -> semitone. Dipakai di UI musik. */
    val PRESETS: List<Triple<String, String, Float>> = listOf(
        Triple("normal", "Normal", 0f),
        Triple("tinggi", "Tinggi", 5f),
        Triple("sangat_tinggi", "Sangat tinggi", 9f),
        Triple("rendah", "Rendah", -4f),
        Triple("sangat_rendah", "Sangat rendah", -7f)
    )

    fun semitonesOf(key: String): Float = PRESETS.firstOrNull { it.first == key }?.third ?: 0f

    fun isAvailable(): Boolean = VoiceNative.loaded

    /** Mengembalikan true bila [dst] berhasil ditulis. [src] tidak diubah. */
    fun apply(src: String, dst: String, semitones: Float): Boolean {
        if (!VoiceNative.loaded) return false
        return try {
            val pcm = decodePcm(src) ?: return false
            val shifted = shiftPitch(pcm.data, pcm.sampleRate, pcm.channels, semitones) ?: return false
            encodeAac(shifted, pcm.sampleRate, pcm.channels, dst)
        } catch (e: Throwable) {
            false
        }
    }

    private class Pcm(val data: ShortArray, val sampleRate: Int, val channels: Int)

    private fun decodePcm(path: String): Pcm? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(path)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    fmt = f
                    break
                }
            }
            if (track < 0 || fmt == null) return null
            ex.selectTrack(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
            var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val dec = MediaCodec.createDecoderByType(mime)
            codec = dec
            dec.configure(fmt, null, null, 0)
            dec.start()

            val chunks = ArrayList<ShortArray>()
            var total = 0
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            while (!outDone) {
                if (!inDone) {
                    val ii = dec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = dec.getInputBuffer(ii) ?: return null
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            dec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = dec.dequeueOutputBuffer(info, 10_000)
                when {
                    oi >= 0 -> {
                        if (info.size > 0) {
                            val ob = dec.getOutputBuffer(oi)
                            if (ob != null) {
                                ob.position(info.offset)
                                ob.limit(info.offset + info.size)
                                val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                                val arr = ShortArray(sb.remaining())
                                sb.get(arr)
                                chunks.add(arr)
                                total += arr.size
                            }
                        }
                        dec.releaseOutputBuffer(oi, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true
                    }
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = dec.outputFormat
                        sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
            if (total == 0 || ch <= 0) return null
            val all = ShortArray(total)
            var pos = 0
            for (c in chunks) {
                System.arraycopy(c, 0, all, pos, c.size)
                pos += c.size
            }
            return Pcm(all, sr, ch)
        } catch (e: Exception) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            ex.release()
        }
    }

    private fun shiftPitch(input: ShortArray, sr: Int, ch: Int, semitones: Float): ShortArray? {
        val h = VoiceNative.create(sr, ch)
        if (h == 0L) return null
        try {
            VoiceNative.setPitch(h, semitones)
            val out = ArrayList<ShortArray>()
            var total = 0
            val framesPerPut = 4096
            val buf = ShortArray(framesPerPut * ch)

            fun drain() {
                while (true) {
                    val got = VoiceNative.receive(h, buf, framesPerPut)
                    if (got <= 0) break
                    val n = got * ch
                    out.add(buf.copyOf(n))
                    total += n
                }
            }

            val framesTotal = input.size / ch
            var frame = 0
            while (frame < framesTotal) {
                val n = min(framesPerPut, framesTotal - frame)
                val chunk = input.copyOfRange(frame * ch, (frame + n) * ch)
                VoiceNative.put(h, chunk, n)
                frame += n
                drain()
            }
            VoiceNative.flush(h)
            drain()

            val res = ShortArray(total)
            var pos = 0
            for (c in out) {
                System.arraycopy(c, 0, res, pos, c.size)
                pos += c.size
            }
            return res
        } finally {
            VoiceNative.destroy(h)
        }
    }

    private fun encodeAac(pcm: ShortArray, sr: Int, ch: Int, dst: String): Boolean {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sr, ch)
        fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, if (ch > 1) 192_000 else 128_000)
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var muxer: MediaMuxer? = null
        var started = false
        try {
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val mx = MediaMuxer(dst, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mx
            var track = -1
            val info = MediaCodec.BufferInfo()
            var pos = 0
            var inEos = false
            var outEos = false
            var ptsUs = 0L
            while (!outEos) {
                if (!inEos) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val ib = codec.getInputBuffer(ii) ?: return false
                        ib.clear()
                        if (pos >= pcm.size) {
                            codec.queueInputBuffer(ii, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inEos = true
                        } else {
                            val n = min(ib.capacity() / 2, pcm.size - pos)
                            ib.order(ByteOrder.nativeOrder()).asShortBuffer().put(pcm, pos, n)
                            codec.queueInputBuffer(ii, 0, n * 2, ptsUs, 0)
                            ptsUs += n * 1_000_000L / (sr.toLong() * ch)
                            pos += n
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = mx.addTrack(codec.outputFormat)
                        mx.start()
                        started = true
                    }
                    oi >= 0 -> {
                        val ob = codec.getOutputBuffer(oi)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                        if (info.size > 0 && started && ob != null) {
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            mx.writeSampleData(track, ob, info)
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outEos = true
                    }
                }
            }
            return started
        } catch (e: Exception) {
            return false
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            try {
                if (started) muxer?.stop()
            } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}
