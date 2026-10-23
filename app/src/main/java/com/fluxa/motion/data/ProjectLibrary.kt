package com.fluxa.motion.data

import android.content.Context
import com.fluxa.motion.core.model.Project
import java.io.File

/**
 * Pustaka project: banyak project tersimpan sebagai file JSON terpisah
 * di files/projects/<id>.json. Homescreen membaca daftarnya dari sini.
 * File autosave lama (satu project) tetap dicerminkan untuk kompatibilitas.
 */
object ProjectLibrary {

    private fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "projects")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun fileOf(ctx: Context, id: String): File {
        val safe = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir(ctx), "$safe.json")
    }

    /** Pindahkan autosave lama (format satu project) ke pustaka, sekali saja. */
    fun migrateLegacy(ctx: Context) {
        try {
            val existing = dir(ctx).listFiles { f -> f.name.endsWith(".json") }
            if (existing != null && existing.isNotEmpty()) return
            val legacy = ProjectStore.load(ctx) ?: return
            writeFile(ctx, legacy)
        } catch (e: Throwable) {
            // migrasi best-effort
        }
    }

    private fun writeFile(ctx: Context, p: Project) {
        fileOf(ctx, p.id).writeText(ProjectStore.encode(p))
    }

    /** Simpan project ke pustaka (+ cermin autosave lama). */
    fun save(ctx: Context, p: Project, touch: Boolean = true) {
        try {
            if (touch) p.updatedAt = System.currentTimeMillis()
            writeFile(ctx, p)
            ProjectStore.save(ctx, p)
        } catch (e: Throwable) {
            // simpan best-effort; jangan crash editor
        }
    }

    fun load(ctx: Context, id: String): Project? {
        return try {
            val f = fileOf(ctx, id)
            if (!f.exists()) return null
            ProjectStore.decode(f.readText())
        } catch (e: Throwable) {
            null
        }
    }

    /** Semua project, terbaru di atas. */
    fun list(ctx: Context): List<Project> {
        migrateLegacy(ctx)
        val out = ArrayList<Project>()
        try {
            val files = dir(ctx).listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()
            for (f in files) {
                try {
                    ProjectStore.decode(f.readText())?.let { out.add(it) }
                } catch (e: Throwable) {
                    // lewati file rusak
                }
            }
        } catch (e: Throwable) {
            // abaikan
        }
        out.sortByDescending { it.updatedAt }
        return out
    }

    fun latest(ctx: Context): Project? = list(ctx).firstOrNull()

    fun delete(ctx: Context, id: String) {
        try {
            fileOf(ctx, id).delete()
        } catch (e: Throwable) {
            // abaikan
        }
    }

    fun deleteAll(ctx: Context) {
        try {
            dir(ctx).listFiles()?.forEach { it.delete() }
            ProjectStore.clearLegacy(ctx)
        } catch (e: Throwable) {
            // abaikan
        }
    }

    fun count(ctx: Context): Int {
        return try {
            dir(ctx).listFiles { f -> f.name.endsWith(".json") }?.size ?: 0
        } catch (e: Throwable) {
            0
        }
    }
}
