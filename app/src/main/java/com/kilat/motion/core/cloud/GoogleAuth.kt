package com.kilat.motion.core.cloud

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Login Google memakai OAuth 2.0 Authorization Code + PKCE lewat browser.
 * Tidak memakai client secret: client Android/native memang tidak punya secret di APK.
 */
object GoogleAuth {

    private const val PREFS = "kilat_auth"
    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    private const val SCOPE = "openid email profile"

    data class Session(val email: String, val name: String, val idToken: String, val expiresAt: Long)

    // ----- PKCE helpers (diuji dengan vektor RFC 7636) -----

    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun randomUrlSafe(bytes: Int): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    /** Ambil klaim dari payload JWT (tanpa verifikasi tanda tangan; token dari TLS ke Google). */
    fun jwtClaims(jwt: String): JSONObject? = try {
        val payload = jwt.split(".")[1]
        JSONObject(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }

    // ----- sesi -----

    fun session(ctx: Context, nowMs: Long = System.currentTimeMillis()): Session? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = p.getString("id_token", null) ?: return null
        val exp = p.getLong("expires_at", 0L)
        if (exp - 60_000L <= nowMs) return null // sudah habis atau hampir habis
        return Session(p.getString("email", "") ?: "", p.getString("name", "") ?: "", token, exp)
    }

    /** Email terakhir yang login, meski sesi sudah habis (untuk tampilan). */
    fun lastEmail(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("email", null)

    fun logout(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ----- alur login -----

    /** Bangun URL login dan simpan verifier + state sementara. */
    fun buildAuthUrl(ctx: Context): String {
        val verifier = randomUrlSafe(48)
        val state = randomUrlSafe(16)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("pending_verifier", verifier)
            .putString("pending_state", state)
            .apply()
        val q = Uri.Builder()
            .appendQueryParameter("client_id", CloudConfig.googleClientId)
            .appendQueryParameter("redirect_uri", CloudConfig.googleRedirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", codeChallenge(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("prompt", "select_account")
            .build()
        return AUTH_ENDPOINT + "?" + q.encodedQuery
    }

    /**
     * Proses callback redirect. Dipanggil dari background thread.
     * Mengembalikan null jika sukses, atau pesan error untuk ditampilkan.
     */
    fun handleRedirect(ctx: Context, uri: Uri): String? {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        uri.getQueryParameter("error")?.let { return "Login dibatalkan ($it)" }
        val code = uri.getQueryParameter("code") ?: return "Callback login tidak berisi kode"
        val state = uri.getQueryParameter("state")
        if (state == null || state != prefs.getString("pending_state", null))
            return "State login tidak cocok, coba login ulang"
        val verifier = prefs.getString("pending_verifier", null) ?: return "Sesi login hilang, coba lagi"

        val body = Net.formEncode(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "client_id" to CloudConfig.googleClientId,
                "redirect_uri" to CloudConfig.googleRedirectUri,
                "code_verifier" to verifier
            )
        ).toByteArray(Charsets.UTF_8)
        val res = Net.request(
            "POST", TOKEN_ENDPOINT, body = body,
            contentType = "application/x-www-form-urlencoded"
        )
        if (!res.ok) return "Tukar kode gagal (${res.code})"

        val json = try { JSONObject(res.body) } catch (e: Exception) { return "Respons login tidak valid" }
        val idToken = json.optString("id_token", "")
        if (idToken.isEmpty()) return "Google tidak mengirim id_token"
        val claims = jwtClaims(idToken) ?: return "id_token tidak bisa dibaca"
        if (claims.optString("aud") != CloudConfig.googleClientId) return "Audience token tidak cocok"
        val exp = claims.optLong("exp", 0L) * 1000L
        val expires = if (exp > 0) exp else System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L

        prefs.edit()
            .putString("id_token", idToken)
            .putString("email", claims.optString("email", ""))
            .putString("name", claims.optString("name", ""))
            .putLong("expires_at", expires)
            .remove("pending_verifier")
            .remove("pending_state")
            .apply()
        return null
    }
}
