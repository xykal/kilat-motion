package com.kilat.motion.ui.settings

import android.app.Activity
import com.kilat.motion.core.ai.AiClient
import com.kilat.motion.core.ai.AiException
import com.kilat.motion.core.ai.AiLayers
import com.kilat.motion.core.ai.AiSettings
import com.kilat.motion.core.ai.AiProvider
import com.kilat.motion.core.model.Layer
import com.kilat.motion.core.model.Project
import com.kilat.motion.ui.components.KilatIcon
import com.kilat.motion.ui.components.KilatSheet
import com.kilat.motion.ui.components.KilatToast
import com.kilat.motion.ui.components.kEditText
import java.io.File

/** Aksi AI di editor: buat layer dari prompt, dan caption otomatis dari musik. */
object AiActions {

    /** Minta teks prompt ke user, lalu panggil AI. Hasil dikirim ke [onLayers] di UI thread. */
    fun promptLayers(activity: Activity, project: Project, onLayers: (List<Layer>) -> Unit) {
        if (!AiSettings.configured(activity)) {
            KilatSheet.showMessage(
                activity, "Butuh API key",
                "Isi API key ${AiSettings.provider(activity).label} dulu di Profil > AI.",
                icon = KilatIcon.SPARK
            )
            return
        }
        val field = activity.kEditText("", "Contoh: judul neon zoom masuk lalu fade out")
        KilatSheet.show(
            activity, "Buat layer dengan AI", field,
            positiveText = "Buat", positiveIcon = KilatIcon.SPARK,
            onPositive = {
                val prompt = field.text.toString().trim()
                if (prompt.isNotEmpty()) runAi(activity, "Membuat layer", onLayers) {
                    AiLayers.fromPrompt(activity.applicationContext, prompt, project)
                }
            },
            negativeText = "Batal"
        )
    }

    /** Transkrip musik project lalu buat caption per segmen. */
    fun captionsFromMusic(activity: Activity, project: Project, onLayers: (List<Layer>) -> Unit) {
        val path = project.music?.path.orEmpty()
        val file = File(path)
        if (path.isEmpty() || !file.exists()) {
            KilatToast.show(activity, "Tambah musik latar dulu di menu editor", KilatIcon.MUSIC)
            return
        }
        if (AiSettings.key(activity, AiProvider.GROQ).isEmpty()) {
            KilatSheet.showMessage(
                activity, "Butuh API key Groq",
                "Caption otomatis memakai Groq Whisper. Isi key Groq di Profil > AI.",
                icon = KilatIcon.MUSIC
            )
            return
        }
        runAi(activity, "Membuat caption dari musik", onLayers) {
            val segs = AiClient.transcribe(activity.applicationContext, file)
            if (segs.isEmpty()) throw AiException("Tidak ada suara yang terdeteksi")
            AiLayers.captions(segs, project)
        }
    }

    private fun runAi(activity: Activity, title: String, onLayers: (List<Layer>) -> Unit, work: () -> List<Layer>) {
        val handle = KilatSheet.showProgress(activity, title, "Menghubungi AI...")
        CloudSettings.background {
            val result = try {
                Result.success(work())
            } catch (e: AiException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(AiException("Gagal: jaringan atau format balasan bermasalah"))
            }
            activity.runOnUiThread {
                handle.dismiss()
                result.fold(
                    onSuccess = { layers ->
                        if (layers.isEmpty()) KilatToast.show(activity, "Tidak ada layer dibuat", KilatIcon.INFO)
                        else onLayers(layers)
                    },
                    onFailure = { KilatToast.show(activity, it.message ?: "Gagal", KilatIcon.INFO) }
                )
            }
        }
    }
}
