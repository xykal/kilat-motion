package com.fluxa.motion.core.cloud

import android.app.Activity
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.fluxa.motion.core.security.SecretStore
import org.json.JSONObject
import java.util.Base64

/**
 * Login Google lewat Credential Manager (akun native, tanpa browser dan tanpa redirect).
 * Hasilnya id_token dengan aud = Web client ID. Tidak ada secret di APK.
 */
object GoogleAuth {

    private const val PREFS = "fluxa_auth"

    data class Session(val email: String, val name: String, val idToken: String, val expiresAt: Long)

    /** Ambil klaim dari payload JWT. Dipakai untuk membaca exp dan aud (tanpa verifikasi tanda tangan). */
    fun jwtClaims(jwt: String): JSONObject? = try {
        val payload = jwt.split(".")[1]
        JSONObject(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }

    // ----- sesi -----

    fun session(ctx: Context, nowMs: Long = System.currentTimeMillis()): Session? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        migrateLegacyToken(p)
        val token = SecretStore.decrypt(p.getString("id_token_enc", "") ?: "")
            ?.takeIf { it.isNotEmpty() } ?: return null
        val exp = p.getLong("expires_at", 0L)
        if (exp - 60_000L <= nowMs) return null // sudah habis atau hampir habis
        return Session(p.getString("email", "") ?: "", p.getString("name", "") ?: "", token, exp)
    }

    /** Versi lama menyimpan id_token plaintext. Dipindah ke bentuk terenkripsi, lalu plaintext dihapus. */
    private fun migrateLegacyToken(p: android.content.SharedPreferences) {
        val legacy = p.getString("id_token", null) ?: return
        p.edit().putString("id_token_enc", SecretStore.encrypt(legacy)).remove("id_token").apply()
    }

    /** Email terakhir yang login, meski sesi sudah habis (untuk tampilan). */
    fun lastEmail(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("email", null)

    fun logout(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ----- alur login -----

    /**
     * Tampilkan pilihan akun Google. Callback dipanggil di main thread.
     * ok = true bila sukses. message kosong berarti dibatalkan user (tidak perlu ditampilkan).
     */
    fun signIn(activity: Activity, onDone: (ok: Boolean, message: String) -> Unit) {
        if (!CloudConfig.googleConfigured) {
            onDone(false, "Login Google belum dikonfigurasi")
            return
        }
        // GetSignInWithGoogleOption selalu menampilkan semua akun + opsi tambah akun
        val option = GetSignInWithGoogleOption.Builder(CloudConfig.googleWebClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        CredentialManager.create(activity).getCredentialAsync(
            activity,
            request,
            null,
            ContextCompat.getMainExecutor(activity),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    val err = saveCredential(activity.applicationContext, result.credential)
                    if (err == null) {
                        onDone(true, "Login sebagai ${lastEmail(activity).orEmpty()}")
                    } else {
                        onDone(false, err)
                    }
                }

                override fun onError(e: GetCredentialException) {
                    when (e) {
                        is GetCredentialCancellationException -> onDone(false, "")
                        is NoCredentialException ->
                            onDone(false, "Tambah akun Google di HP ini dulu, lalu coba lagi")
                        else -> onDone(false, "Login gagal (${e.type})")
                    }
                }
            }
        )
    }

    /** Simpan id_token dari credential. Mengembalikan null bila sukses, atau pesan error. */
    private fun saveCredential(ctx: Context, credential: androidx.credentials.Credential): String? {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) return "Jenis akun tidak didukung"

        val google = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: Exception) {
            return "Respons login Google tidak valid"
        }
        val idToken = google.idToken
        val claims = jwtClaims(idToken) ?: return "id_token tidak bisa dibaca"
        if (claims.optString("aud") != CloudConfig.googleWebClientId) return "Audience token tidak cocok"
        val exp = claims.optLong("exp", 0L) * 1000L
        val expires = if (exp > 0) exp else System.currentTimeMillis() + 3_600_000L

        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("id_token_enc", SecretStore.encrypt(idToken))
            .remove("id_token")
            .putString("email", google.id)
            .putString("name", google.displayName ?: "")
            .putLong("expires_at", expires)
            .apply()
        return null
    }
}
