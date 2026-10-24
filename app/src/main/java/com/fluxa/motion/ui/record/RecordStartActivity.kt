package com.fluxa.motion.ui.record

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.fluxa.motion.core.record.ScreenRecordService

/**
 * Titik masuk rekam layar dari dalam app: minta izin (mikrofon untuk audio sistem, notifikasi),
 * lalu izin tangkap layar, lalu menjalankan [ScreenRecordService].
 */
class RecordStartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ScreenRecordService.isRecording) {
            toast("Rekaman sedang berjalan. Ketuk Berhenti di notifikasi untuk menyelesaikan.")
            finish()
            return
        }
        val need = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isEmpty()) askCapture() else requestPermissions(need.toTypedArray(), REQ_PERM)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERM) return
        val micIdx = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
        if (micIdx >= 0 && grantResults.getOrNull(micIdx) != PackageManager.PERMISSION_GRANTED) {
            toast("Izin mikrofon ditolak: audio sistem tidak ikut direkam")
        }
        askCapture()
    }

    private fun askCapture() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                val i = Intent(this, ScreenRecordService::class.java)
                    .setAction(ScreenRecordService.ACTION_START)
                    .putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(ScreenRecordService.EXTRA_DATA, data)
                startForegroundService(i)
            } else {
                toast("Rekam layar dibatalkan")
            }
            finish()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val REQ_PERM = 7101
        private const val REQ_CAPTURE = 7102
    }
}
