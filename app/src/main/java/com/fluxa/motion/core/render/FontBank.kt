package com.fluxa.motion.core.render

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Font untuk teks layer.
 * - Font bawaan dari assets/fonts (lisensi OFL), kunci tetap.
 * - Font impor (.ttf/.otf) disalin ke filesDir/fonts dengan kunci "c_<ts>__<nama>".
 * Kunci yang tidak dikenali tetap memakai font sistem seperti sebelumnya.
 * [init] dipanggil dari Activity sebelum rendering.
 */
object FontBank {
    private val assetFiles = mapOf(
        "poppins_black" to "fonts/poppins-black.ttf",
        "poppins" to "fonts/poppins-regular.ttf",
        "space_mono" to "fonts/spacemono-regular.ttf",
        "space_mono_bold" to "fonts/spacemono-bold.ttf",
        "playfair" to "fonts/playfair.ttf",
        "bebas" to "fonts/bebas.ttf"
    )

    val KEYS: Set<String> = assetFiles.keys

    private const val CUSTOM_PREFIX = "c_"
    private const val MAX_BYTES = 15L * 1024 * 1024

    private var appCtx: Context? = null
    private val cache = HashMap<String, Typeface>()
    private val customFiles = LinkedHashMap<String, File>()

    fun init(c: Context) {
        if (appCtx != null) return
        appCtx = c.applicationContext
        fontDir(c).listFiles()?.forEach { f ->
            if (f.isFile && f.name.startsWith(CUSTOM_PREFIX) && f.name.endsWith(".ttf")) {
                customFiles[f.name.removeSuffix(".ttf")] = f
            }
        }
    }

    /** Kunci font impor yang tersedia, urut dari yang paling baru dibuat. */
    fun customKeys(): List<String> = customFiles.keys.toList().reversed()

    fun isCustom(key: String): Boolean = customFiles.containsKey(key)

    /** Label tampilan: nama file asli untuk font impor, kunci apa adanya untuk lainnya. */
    fun labelOf(key: String): String =
        if (isCustom(key)) key.substringAfter("__", key) else key

    /** Typeface untuk kunci bawaan atau impor, atau null bila bukan font itu / gagal dimuat. */
    fun bundled(key: String): Typeface? {
        cache[key]?.let { return it }
        val file = customFiles[key]
        val tf: Typeface? = if (file != null) {
            try { Typeface.createFromFile(file) } catch (e: Exception) { null }
        } else {
            val path = assetFiles[key] ?: return null
            val c = appCtx ?: return null
            try { Typeface.createFromAsset(c.assets, path) } catch (e: Exception) { null }
        }
        if (tf != null) cache[key] = tf
        return tf
    }

    /** Salin font dari [uri] ke penyimpanan app. Mengembalikan kunci baru atau null bila file tidak valid. */
    fun importFont(c: Context, uri: Uri): String? {
        val name = sanitize(displayName(c, uri))
        val key = CUSTOM_PREFIX + System.currentTimeMillis().toString(36) + "__" + name
        val dst = File(fontDir(c), "$key.ttf")
        try {
            val input = c.contentResolver.openInputStream(uri) ?: return null
            input.use { i ->
                dst.outputStream().use { o ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = i.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IllegalStateException("font terlalu besar")
                        o.write(buf, 0, n)
                    }
                }
            }
            // validasi: file yang bukan font akan melempar exception di sini
            Typeface.createFromFile(dst)
        } catch (e: Exception) {
            dst.delete()
            return null
        }
        customFiles[key] = dst
        return key
    }

    private fun fontDir(c: Context): File = File(c.filesDir, "fonts").apply { mkdirs() }

    private fun displayName(c: Context, uri: Uri): String {
        try {
            c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cur.getString(idx) ?: "font"
                }
            }
        } catch (e: Exception) {
        }
        return uri.lastPathSegment ?: "font"
    }

    private fun sanitize(raw: String): String {
        val base = raw.substringBeforeLast('.').ifEmpty { raw }
        val s = base.map { if (it.isLetterOrDigit() || it == '-' || it == ' ') it else '_' }.joinToString("").trim()
        return s.replace(' ', '_').take(40).ifEmpty { "font" }
    }
}
