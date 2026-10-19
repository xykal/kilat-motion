package com.kilat.motion.core.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioUtil {

    fun durationSeconds(path: String): Float {
        return try {
            val r = MediaMetadataRetriever()
            r.setDataSource(path)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            r.release()
            ms / 1000f
        } catch (e: Throwable) {
            0f
        }
    }

    /**
     * Decode audio (mp3/m4a/wav/ogg/aac) → envelope waveform.
     * Berjalan di background thread. Return null kalau tidak didukung.
     * Sampel dibatasi [maxSeconds] supaya memori aman.
     */
    fun decodeEnvelope(path: String, buckets: Int = 260, maxSeconds: Float = 150f): FloatArray? {
        if (!File(path).exists()) return null
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(path)
            var trackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIdx = i
                    format = f
                    break
                }
            }
            if (trackIdx < 0 || format == null) return null
            extractor.selectTrack(trackIdx)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2) else 2

            val maxSamples = (sampleRate * maxSeconds).toInt().coerceAtLeast(sampleRate)
            var pcm = ShortArray(1 shl 16)
            var pcmLen = 0

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val deadline = System.currentTimeMillis() + 25_000

            while (!outputDone && System.currentTimeMillis() < deadline) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    if (info.size > 0) {
                        val ob = codec.getOutputBuffer(outIdx)!!
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val shorts = ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val n = shorts.remaining()
                        if (pcmLen + n > pcm.size) {
                            pcm = pcm.copyOf(maxOf(pcm.size * 2, pcmLen + n))
                        }
                        shorts.get(pcm, pcmLen, n)
                        pcmLen += n
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (pcmLen >= maxSamples * channels) outputDone = true
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // format final (mis. sample rate hasil resample) — abaikan, data PCM tetap valid
                }
            }

            if (pcmLen < 64) return null
            val used = ShortArray(minOf(pcmLen, pcm.size))
            System.arraycopy(pcm, 0, used, 0, used.size)
            val mono = Waveform.toMono(used, channels)
            return Waveform.envelope(mono, buckets)
        } catch (e: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (e: Throwable) {}
            try { codec?.release() } catch (e: Throwable) {}
            try { extractor?.release() } catch (e: Throwable) {}
        }
    }

    /** Baca sampel mono langsung (dipakai kalau nanti butuh analisis lanjutan). */
    fun pcmMono(path: String, maxSeconds: Float = 60f): ShortArray? = null
}
