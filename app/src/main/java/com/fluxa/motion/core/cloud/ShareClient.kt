package com.fluxa.motion.core.cloud

import android.content.Context
import com.fluxa.motion.core.model.Project
import com.fluxa.motion.data.ProjectStore
import org.json.JSONObject

/**
 * Share project/preset lewat Cloudflare Worker. Upload wajib login Google,
 * worker memverifikasi id_token. Panggil dari background thread.
 */
object ShareClient {

    class Outcome(val ok: Boolean, val message: String, val id: String? = null, val url: String? = null)

    fun upload(ctx: Context, project: Project): Outcome {
        val session = GoogleAuth.session(ctx)
            ?: return Outcome(false, "Login Google dulu di Profil untuk membagikan")
        val payload = JSONObject()
            .put("name", project.name.take(80))
            .put("data", ProjectStore.projectToJson(project))
            .toString()
            .toByteArray(Charsets.UTF_8)
        val res = Net.request(
            "POST", CloudConfig.SHARE_BASE_URL + "/presets",
            headers = mapOf("Authorization" to "Bearer ${session.idToken}"),
            body = payload, contentType = "application/json"
        )
        return when (res.code) {
            201 -> {
                val j = JSONObject(res.body)
                Outcome(true, "Project dibagikan", j.optString("id"), j.optString("url"))
            }
            401 -> Outcome(false, "Sesi login habis, login ulang di Profil")
            413 -> Outcome(false, "Project terlalu besar untuk dibagikan")
            503 -> Outcome(false, "Server share belum dikonfigurasi")
            else -> Outcome(false, "Gagal membagikan (${res.code})")
        }
    }

    /** Ambil project dari ID share. Hasilnya project baru (id baru, bukan menimpa). */
    fun fetch(id: String): Project? {
        val clean = id.trim().substringAfterLast("/").filter { it.isLetterOrDigit() }.lowercase()
        if (clean.length < 6) return null
        val res = Net.request("GET", "${CloudConfig.SHARE_BASE_URL}/presets/$clean")
        if (!res.ok) return null
        val data = JSONObject(res.body).optJSONObject("data") ?: return null
        return try {
            ProjectStore.jsonToProject(data).also {
                it.id = java.util.UUID.randomUUID().toString().substring(0, 12)
                it.updatedAt = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            null
        }
    }
}
