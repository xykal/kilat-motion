package com.kilat.motion.ui.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.kilat.motion.core.cloud.GoogleAuth
import com.kilat.motion.ui.components.KilatToast
import com.kilat.motion.ui.components.KilatIcon
import java.util.concurrent.Executors

/**
 * Menerima callback OAuth dari browser (skema com.googleusercontent.apps.*:/oauth2redirect).
 * Menukar kode lalu langsung kembali ke aplikasi. Tidak menampilkan UI sendiri.
 */
class AuthRedirectActivity : Activity() {

    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent?.data
        if (uri == null) {
            finishBack(null)
            return
        }
        worker.execute {
            val err = try {
                GoogleAuth.handleRedirect(applicationContext, uri)
            } catch (e: Exception) {
                "Login gagal: jaringan bermasalah"
            }
            runOnUiThread { finishBack(err) }
        }
    }

    private fun finishBack(err: String?) {
        if (err == null) {
            val email = GoogleAuth.lastEmail(this).orEmpty()
            KilatToast.show(this, if (email.isNotEmpty()) "Login sebagai $email" else "Login berhasil", KilatIcon.CHECK)
        } else {
            KilatToast.show(this, err, KilatIcon.INFO)
        }
        // kembali ke aplikasi (Beranda/Profil) bila ada, tanpa membuat tumpukan baru
        startActivity(Intent(this, com.kilat.motion.ui.home.HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
