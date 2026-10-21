package com.kilat.motion.core.cloud

import java.net.HttpURLConnection
import java.net.URL

/** Hasil HTTP sederhana. Dipanggil dari background thread saja. */
class HttpResult(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
}

/** HTTP minimal memakai HttpURLConnection (tanpa library tambahan). */
object Net {
    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        contentType: String? = null,
        timeoutMs: Int = 60_000
    ): HttpResult {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("Accept", "application/json")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (contentType != null) c.setRequestProperty("Content-Type", contentType)
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return HttpResult(code, text)
        } finally {
            c.disconnect()
        }
    }

    fun formEncode(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            java.net.URLEncoder.encode(k, "UTF-8") + "=" + java.net.URLEncoder.encode(v, "UTF-8")
        }
}
