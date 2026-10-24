package com.fluxa.motion.core.audio

/**
 * Pembungkus JNI ke SoundTouch (libfluxa_voice.so). Semua sampel 16-bit interleaved.
 * Kalau library gagal dimuat, [loaded] false dan pemanggil harus menampilkan pesan.
 */
object VoiceNative {
    val loaded: Boolean = try {
        System.loadLibrary("fluxa_voice")
        true
    } catch (e: Throwable) {
        false
    }

    @JvmStatic external fun create(sampleRate: Int, channels: Int): Long
    @JvmStatic external fun setPitch(handle: Long, semitones: Float)

    /** [frames] = jumlah frame (per channel). samples.size minimal frames * channels. */
    @JvmStatic external fun put(handle: Long, samples: ShortArray, frames: Int)

    /** Mengembalikan jumlah frame yang diterima; 0 berarti belum ada hasil. */
    @JvmStatic external fun receive(handle: Long, out: ShortArray, maxFrames: Int): Int
    @JvmStatic external fun flush(handle: Long)
    @JvmStatic external fun destroy(handle: Long)
}
