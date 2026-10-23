package com.fluxa.motion.core.audio

import com.fluxa.motion.core.model.Music

import android.content.Context
import android.media.MediaPlayer
import kotlin.math.abs

/** Preview playback musik latar, disinkronkan dengan playhead. */
class MusicPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var loadedPath: String = ""

    private fun ensure(music: Music): MediaPlayer? {
        if (player != null && loadedPath == music.path) return player
        release()
        return try {
            val mp = MediaPlayer()
            mp.setDataSource(music.path)
            mp.isLooping = false
            mp.prepare()
            player = mp
            loadedPath = music.path
            mp
        } catch (e: Throwable) {
            release()
            null
        }
    }

    /** Dipanggil tiap frame playback / saat seek. */
    fun sync(music: Music?, playhead: Float, playing: Boolean) {
        if (music == null || music.path.isEmpty()) {
            release()
            return
        }
        val mp = ensure(music) ?: return
        try {
            mp.setVolume(music.volume.coerceIn(0f, 1f), music.volume.coerceIn(0f, 1f))
        } catch (e: Throwable) {}
        val inRange = playhead >= music.start - 0.02f && playhead <= music.start + music.dur + 0.02f
        try {
            if (playing && inRange) {
                val targetMs = (((playhead - music.start) + music.offset) * 1000f).toInt().coerceAtLeast(0)
                if (!mp.isPlaying) {
                    mp.seekTo(targetMs)
                    mp.start()
                } else if (abs(mp.currentPosition - targetMs) > 420) {
                    mp.seekTo(targetMs)
                }
            } else {
                if (mp.isPlaying) mp.pause()
            }
        } catch (e: Throwable) {
        }
    }

    fun release() {
        try { player?.stop() } catch (e: Exception) {}
        try { player?.release() } catch (e: Exception) {}
        player = null
        loadedPath = ""
    }
}
