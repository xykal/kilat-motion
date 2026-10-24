package com.fluxa.motion.core.record

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service perekam layar. Hidup selama rekaman berjalan, walau user pindah ke app lain.
 * Berhenti lewat tombol "Berhenti" di notifikasi. File tersimpan di Movies/Fluxa lewat MediaStore.
 */
class ScreenRecordService : Service() {

    private var projection: MediaProjection? = null
    private var recorder: ScreenRecorder? = null
    private var outUri: Uri? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inIntent = intent ?: return START_NOT_STICKY
        when (inIntent.action) {
            ACTION_STOP -> stopRecording()
            ACTION_START -> {
                if (recorder != null) return START_NOT_STICKY
                val resultCode = inIntent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    inIntent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    inIntent.getParcelableExtra(EXTRA_DATA)
                }
                // Android 14 mewajibkan startForeground dengan tipe mediaProjection sebelum getMediaProjection
                startForegroundWithNotification()
                if (data == null || resultCode != Activity.RESULT_OK) {
                    fail("Izin tangkap layar tidak valid")
                } else {
                    startRecording(resultCode, data)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Rekam layar", NotificationManager.IMPORTANCE_LOW))
        }
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        val n = builder
            .setContentTitle("Fluxa sedang merekam layar")
            .setContentText("Ketuk Berhenti untuk menyelesaikan rekaman")
            .setSmallIcon(applicationInfo.icon)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "Berhenti",
                    stopIntent
                ).build()
            )
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val proj = mpm.getMediaProjection(resultCode, data)
        if (proj == null) {
            fail("Tidak bisa memulai tangkap layar")
            return
        }
        projection = proj
        // wajib didaftarkan sebelum createVirtualDisplay (Android 14)
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopRecording()
            }
        }, main)

        val dm = resources.displayMetrics
        val (w, h) = recordSize(dm.widthPixels, dm.heightPixels)
        val name = "Fluxa-Rekam-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Fluxa")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            fail("Gagal membuat file rekaman")
            return
        }
        outUri = uri
        val pfd = contentResolver.openFileDescriptor(uri, "rw")
        if (pfd == null) {
            fail("Gagal membuka file rekaman")
            return
        }
        val rec = ScreenRecorder(proj, pfd, w, h, dm.densityDpi, wantAudio = true)
        recorder = rec
        isRecording = true
        try {
            rec.start()
            toast("Rekam layar dimulai")
        } catch (e: Exception) {
            fail("Gagal mulai rekam: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    /** Menghentikan rekaman. Penyelesaian file dikerjakan di thread terpisah karena ada join. */
    private fun stopRecording() {
        val rec = recorder ?: return
        recorder = null
        Thread({
            try { rec.stop() } catch (_: Exception) {}
            try { projection?.stop() } catch (_: Exception) {}
            projection = null
            val uri = outUri
            outUri = null
            if (uri != null) {
                try {
                    contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                } catch (_: Exception) {}
            }
            isRecording = false
            val audioOk = rec.audioNote == null
            main.post {
                toast(
                    if (audioOk) "Rekaman tersimpan di Movies/Fluxa"
                    else "Rekaman tersimpan tanpa audio (${rec.audioNote})"
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, "rec-stop").start()
    }

    private fun fail(msg: String) {
        toast(msg)
        isRecording = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun toast(msg: String) {
        main.post { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
    }

    /** Ukuran rekaman: sisi terpanjang maksimal 1600 px, kelipatan 16 (ramah encoder). */
    private fun recordSize(w: Int, h: Int): Pair<Int, Int> {
        val long = maxOf(w, h).toFloat()
        val s = minOf(1f, 1600f / long)
        fun fit(v: Float): Int = ((v.toInt() / 16) * 16).coerceAtLeast(16)
        return Pair(fit(w * s), fit(h * s))
    }

    override fun onDestroy() {
        if (recorder != null) stopRecording()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.fluxa.motion.record.START"
        const val ACTION_STOP = "com.fluxa.motion.record.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL_ID = "fluxa_rec"
        private const val NOTIF_ID = 4217

        @Volatile
        var isRecording: Boolean = false
            private set
    }
}
