package com.fluxa.motion.core.ai

import android.content.Context
import com.fluxa.motion.core.cloud.Net
import com.fluxa.motion.core.cloud.HttpResult
import com.fluxa.motion.core.security.SecretStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Penyedia AI yang didukung. Key diisi user sendiri di Profil > AI. Tidak pernah di-bundle. */
enum class AiProvider(val id: String, val label: String, val chatUrl: String) {
    GROQ("groq", "Groq", "https://api.groq.com/openai/v1/chat/completions"),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1/chat/completions");

    companion object {
        fun fromId(id: String?): AiProvider = values().firstOrNull { it.id == id } ?: GROQ
    }
}

class AiException(message: String) : Exception(message)

object AiSettings {
    private const val PREFS = "fluxa_ai"
    const val GROQ_CHAT_MODEL = "llama-3.3-70b-versatile"
    const val GROQ_WHISPER_MODEL = "whisper-large-v3-turbo"
    const val OPENROUTER_DEFAULT_MODEL = "meta-llama/llama-3.3-70b-instruct:free"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun provider(ctx: Context): AiProvider = AiProvider.fromId(p(ctx).getString("provider", null))
    fun setProvider(ctx: Context, v: AiProvider) = p(ctx).edit().putString("provider", v.id).apply()

    /**
     * Key disimpan terenkripsi (Android Keystore). Versi lama menyimpan plaintext di "key_<id>";
     * itu dimigrasikan saat dibaca lalu dihapus.
     */
    fun key(ctx: Context, v: AiProvider): String {
        val sp = p(ctx)
        val legacy = sp.getString("key_${v.id}", null)
        if (legacy != null) {
            val k = legacy.trim()
            sp.edit().putString("enc_key_${v.id}", SecretStore.encrypt(k)).remove("key_${v.id}").apply()
            return k
        }
        val enc = sp.getString("enc_key_${v.id}", "") ?: ""
        return SecretStore.decrypt(enc) ?: ""
    }

    fun setKey(ctx: Context, v: AiProvider, key: String) {
        p(ctx).edit()
            .putString("enc_key_${v.id}", SecretStore.encrypt(key.trim()))
            .remove("key_${v.id}")
            .apply()
    }

    fun openRouterModel(ctx: Context): String =
        p(ctx).getString("openrouter_model", OPENROUTER_DEFAULT_MODEL).takeIf { !it.isNullOrBlank() }
            ?: OPENROUTER_DEFAULT_MODEL
    fun setOpenRouterModel(ctx: Context, m: String) =
        p(ctx).edit().putString("openrouter_model", m.trim()).apply()

    fun configured(ctx: Context): Boolean = key(ctx, provider(ctx)).isNotEmpty()
}

object AiClient {

    /** Panggilan chat. Mengembalikan teks balasan. Dipanggil dari background thread. */
    fun chat(ctx: Context, system: String, user: String): String {
        val prov = AiSettings.provider(ctx)
        val key = AiSettings.key(ctx, prov)
        if (key.isEmpty()) throw AiException("Isi API key ${prov.label} dulu di Profil > AI")
        val model = if (prov == AiProvider.GROQ) AiSettings.GROQ_CHAT_MODEL else AiSettings.openRouterModel(ctx)
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.4)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
        val headers = mutableMapOf("Authorization" to "Bearer $key")
        if (prov == AiProvider.OPENROUTER) {
            headers["HTTP-Referer"] = "https://github.com/xykal/kilat-motion"
            headers["X-Title"] = "Fluxa"
        }
        val res = Net.request(
            "POST", prov.chatUrl, headers = headers,
            body = body.toString().toByteArray(Charsets.UTF_8), contentType = "application/json"
        )
        throwIfError(prov, res)
        val content = JSONObject(res.body).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        return content
    }

    /** Ambil objek JSON pertama dari balasan model (toleran terhadap pagar kode). */
    fun extractJson(text: String): JSONObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("Balasan AI bukan JSON")
        return try {
            JSONObject(text.substring(start, end + 1))
        } catch (e: Exception) {
            throw AiException("Balasan AI tidak bisa dibaca")
        }
    }

    data class Segment(val start: Float, val end: Float, val text: String)

    /** Transkrip audio dengan Groq Whisper (timestamp per segmen). Hanya Groq. */
    fun transcribe(ctx: Context, file: File): List<Segment> {
        val key = AiSettings.key(ctx, AiProvider.GROQ)
        if (key.isEmpty()) throw AiException("Caption butuh API key Groq (Profil > AI)")
        if (file.length() > 25L * 1024 * 1024) throw AiException("Audio lebih dari 25 MB, potong dulu")
        val boundary = "----fluxa" + System.currentTimeMillis()
        val out = ByteArrayOutputStream()
        fun field(name: String, value: String) {
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
        }
        field("model", AiSettings.GROQ_WHISPER_MODEL)
        field("response_format", "verbose_json")
        field("timestamp_granularities[]", "segment")
        out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
        out.write(file.readBytes())
        out.write("\r\n--$boundary--\r\n".toByteArray())

        val res = Net.request(
            "POST", "https://api.groq.com/openai/v1/audio/transcriptions",
            headers = mapOf("Authorization" to "Bearer $key"),
            body = out.toByteArray(),
            contentType = "multipart/form-data; boundary=$boundary",
            timeoutMs = 180_000
        )
        throwIfError(AiProvider.GROQ, res)
        val segs = JSONObject(res.body).optJSONArray("segments") ?: JSONArray()
        val result = ArrayList<Segment>()
        for (i in 0 until segs.length()) {
            val s = segs.getJSONObject(i)
            val text = s.optString("text").trim()
            if (text.isEmpty()) continue
            result += Segment(s.optDouble("start").toFloat(), s.optDouble("end").toFloat(), text)
        }
        return result
    }

    private fun throwIfError(prov: AiProvider, res: HttpResult) {
        if (res.ok) return
        val msg = when (res.code) {
            401, 403 -> "API key ${prov.label} ditolak"
            429 -> "Kuota ${prov.label} habis atau terlalu sering, coba lagi nanti"
            else -> "${prov.label} gagal (${res.code})"
        }
        throw AiException(msg)
    }
}
