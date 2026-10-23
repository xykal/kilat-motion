package com.fluxa.motion.ui.settings

import android.app.Activity
import com.fluxa.motion.core.ai.AiClient
import com.fluxa.motion.core.ai.AiException
import com.fluxa.motion.core.ai.AiLayers
import com.fluxa.motion.core.ai.AiSettings
import com.fluxa.motion.core.ai.AiProvider
import com.fluxa.motion.core.model.Layer
import com.fluxa.motion.core.model.Project
import com.fluxa.motion.ui.components.FluxaIcon
import com.fluxa.motion.ui.components.FluxaSheet
import com.fluxa.motion.ui.components.FluxaToast
import com.fluxa.motion.ui.components.kEditText
import java.io.File

/** Aksi AI di editor: buat layer dari prompt, dan caption otomatis dari musik. */
object AiActions {

    /** Minta teks prompt ke user, lalu panggil AI. Hasil dikirim ke [onLayers] di UI thread. */
    fun promptLayers(activity: Activity, project: Project, onLayers: (List<Layer>) -> Unit) {
        if (!AiSettings.configured(activity)) {
            FluxaSheet.showMessage(
                activity, "Butuh API key",
                "Isi API key ${AiSettings.provider(activity).label} dulu di Profil > AI.",
                icon = FluxaIcon.SPARK
            )
            return
        }
        val field = activity.kEditText("", "Contoh: judul neon zoom masuk lalu fade out")
        FluxaSheet.show(
            activity, "Buat layer dengan AI", field,
            positiveText = "Buat", positiveIcon = FluxaIcon.SPARK,
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
            FluxaToast.show(activity, "Tambah musik latar dulu di menu editor", FluxaIcon.MUSIC)
            return
        }
        if (AiSettings.key(activity, AiProvider.GROQ).isEmpty()) {
            FluxaSheet.showMessage(
                activity, "Butuh API key Groq",
                "Caption otomatis memakai Groq Whisper. Isi key Groq di Profil > AI.",
                icon = FluxaIcon.MUSIC
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
        val handle = FluxaSheet.showProgress(activity, title, "Menghubungi AI...")
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
                        if (layers.isEmpty()) FluxaToast.show(activity, "Tidak ada layer dibuat", FluxaIcon.INFO)
                        else onLayers(layers)
                    },
                    onFailure = { FluxaToast.show(activity, it.message ?: "Gagal", FluxaIcon.INFO) }
                )
            }
        }
    }
}
