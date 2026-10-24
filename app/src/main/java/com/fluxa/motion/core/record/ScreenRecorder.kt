package com.fluxa.motion.core.record

import android.annotation.SuppressLint
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.ParcelFileDescriptor
import android.view.Surface

/**
 * Perekam layar dalam app: video dari VirtualDisplay (H.264) + audio sistem lewat
 * AudioPlaybackCapture (AAC). Keduanya dimuxing ke MP4 lewat [MediaMuxer].
 *
 * Audio sistem hanya bisa ditangkap dari app yang mengizinkan (usage media/game/unknown).
 * Bila setup audio gagal, rekaman tetap berjalan tanpa audio dan [audioNote] berisi alasannya.
 */
class ScreenRecorder(
    private val projection: MediaProjection,
    private val pfd: ParcelFileDescriptor,
    private val width: Int,
    private val height: Int,
    private val dpi: Int,
    private val wantAudio: Boolean
) {
    private val lock = Object()
    private var muxer: MediaMuxer? = null
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    @Volatile private var running = false

    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var inputSurface: Surface? = null
    private var videoDrain: Thread? = null
    private var audioFeed: Thread? = null
    private var audioDrain: Thread? = null
    private var hasAudio = false

    /** Catatan kalau audio tidak bisa direkam (kosong bila audio berjalan). */
    var audioNote: String? = null
        private set

    fun start() {
        running = true
        muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // audio dulu, supaya status hasAudio sudah final sebelum thread video mulai menulis
        if (wantAudio) {
            try {
                startAudio()
                hasAudio = true
            } catch (e: Exception) {
                audioNote = e.javaClass.simpleName + ": " + (e.message ?: "tidak diketahui")
                hasAudio = false
            }
        } else {
            audioNote = "Audio dimatikan"
        }

        val vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        vf.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        vf.setInteger(MediaFormat.KEY_BIT_RATE, (width.toLong() * height * 4L).toInt().coerceIn(2_000_000, 12_000_000))
        vf.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        val vc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        vc.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surf = vc.createInputSurface()
        vc.start()
        videoCodec = vc
        inputSurface = surf
        virtualDisplay = projection.createVirtualDisplay(
            "fluxa-rec", width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surf, null, null
        )
        videoDrain = Thread({ drainLoop(vc, isVideo = true) }, "rec-video").also { it.start() }
    }

    @SuppressLint("MissingPermission")
    private fun startAudio() {
        val capCfg = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) throw IllegalStateException("buffer audio tidak valid")
        val chunk = 4096
        val rec = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(capCfg)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuf * 2, chunk * 2))
            .build()

        val af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 2)
        af.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        af.setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
        af.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, chunk * 2)
        val ac = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        ac.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        ac.start()
        audioRecord = rec
        audioCodec = ac
        rec.startRecording()
        audioDrain = Thread({ drainLoop(ac, isVideo = false) }, "rec-audio-drain").also { it.start() }
        audioFeed = Thread({ audioLoop(rec, ac, chunk) }, "rec-audio-feed").also { it.start() }
    }

    private fun audioLoop(rec: AudioRecord, ac: MediaCodec, chunk: Int) {
        val buf = ByteArray(chunk)
        var frames = 0L // jumlah frame stereo (4 byte) yang sudah dikirim ke encoder
        while (running) {
            val n = rec.read(buf, 0, buf.size)
            if (n <= 0) continue
            var off = 0
            while (off < n && running) {
                val inIdx = ac.dequeueInputBuffer(10_000)
                if (inIdx < 0) continue
                val ib = ac.getInputBuffer(inIdx) ?: continue
                ib.clear()
                var len = minOf(n - off, ib.capacity())
                len -= len % 4
                if (len <= 0) break
                ib.put(buf, off, len)
                val pts = frames * 1_000_000L / SAMPLE_RATE
                ac.queueInputBuffer(inIdx, 0, len, pts, 0)
                frames += (len / 4).toLong()
                off += len
            }
        }
        // akhir: kirim end-of-stream ke encoder audio
        while (true) {
            val inIdx = try { ac.dequeueInputBuffer(10_000) } catch (e: IllegalStateException) { -1 }
            if (inIdx >= 0) {
                ac.queueInputBuffer(inIdx, 0, 0, frames * 1_000_000L / SAMPLE_RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                break
            }
        }
    }

    /** Dipanggil saat format output sudah tersedia. Harus dipanggil dalam synchronized(lock). */
    private fun startMuxerLocked() {
        muxer?.start()
        muxerStarted = true
        lock.notifyAll()
    }

    private fun drainLoop(codec: MediaCodec, isVideo: Boolean) {
        val info = MediaCodec.BufferInfo()
        var idleAfterStop = 0
        while (true) {
            val idx = try { codec.dequeueOutputBuffer(info, 10_000) } catch (e: IllegalStateException) { break }
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized(lock) {
                    val t = muxer?.addTrack(codec.outputFormat) ?: -1
                    if (isVideo) videoTrack = t else audioTrack = t
                    val ready = videoTrack >= 0 && (!hasAudio || audioTrack >= 0)
                    if (ready && !muxerStarted) startMuxerLocked()
                }
            } else if (idx >= 0) {
                val ob = codec.getOutputBuffer(idx)
                val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                if (ob != null && info.size > 0 && !isConfig) {
                    synchronized(lock) {
                        while (!muxerStarted && running) lock.wait(50)
                        if (muxerStarted) {
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            val track = if (isVideo) videoTrack else audioTrack
                            if (track >= 0) muxer?.writeSampleData(track, ob, info)
                        }
                    }
                }
                codec.releaseOutputBuffer(idx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            } else if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!running) {
                    idleAfterStop++
                    if (idleAfterStop > 300) break // ~3 detik tanpa data setelah stop: anggap selesai
                }
            }
        }
    }

    /** Menghentikan rekaman dan menyelesaikan file. Blocking sebentar (join thread). */
    fun stop() {
        running = false
        audioFeed?.join(4000)
        // lepas display dulu, baru beri tahu encoder video bahwa input sudah habis
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { videoCodec?.signalEndOfInputStream() } catch (_: Exception) {}
        videoDrain?.join(6000)
        audioDrain?.join(6000)
        synchronized(lock) {
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            muxer = null
            muxerStarted = false
        }
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        try { videoCodec?.stop() } catch (_: Exception) {}
        try { videoCodec?.release() } catch (_: Exception) {}
        try { audioCodec?.stop() } catch (_: Exception) {}
        try { audioCodec?.release() } catch (_: Exception) {}
        videoCodec = null
        audioCodec = null
        inputSurface?.release()
        inputSurface = null
        try { pfd.close() } catch (_: Exception) {}
    }

    companion object {
        const val SAMPLE_RATE = 44100
    }
}
