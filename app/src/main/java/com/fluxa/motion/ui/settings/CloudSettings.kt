package com.fluxa.motion.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import com.fluxa.motion.core.ai.AiProvider
import com.fluxa.motion.core.ai.AiSettings
import com.fluxa.motion.core.cloud.CloudConfig
import com.fluxa.motion.core.cloud.GoogleAuth
import com.fluxa.motion.core.cloud.ShareClient
import com.fluxa.motion.core.model.Project
import com.fluxa.motion.data.ProjectLibrary
import com.fluxa.motion.ui.components.FluxaDropdown
import com.fluxa.motion.ui.components.FluxaIcon
import com.fluxa.motion.ui.components.FluxaSheet
import com.fluxa.motion.ui.components.FluxaToast
import com.fluxa.motion.ui.components.kEditText
import com.fluxa.motion.ui.theme.FluxaColors
import com.fluxa.motion.ui.theme.dp
import com.fluxa.motion.ui.theme.kText
import java.util.concurrent.Executors

/** Sheet akun, impor/bagikan project, dan pengaturan AI. Semua I/O jalan di background. */
object CloudSettings {

    private val io = Executors.newCachedThreadPool()

    /** Login Google native. onChanged dipanggil setelah sukses atau gagal agar halaman Profil segar. */
    fun startLogin(activity: Activity, onChanged: () -> Unit) {
        if (!CloudConfig.googleConfigured) {
            FluxaSheet.showMessage(
                activity, "Login belum aktif",
                "Web client ID Google belum diisi.\n\n" +
                    "Isi googleWebClientId di gradle.properties, lalu build ulang.",
                icon = FluxaIcon.USER
            )
            return
        }
        GoogleAuth.signIn(activity) { ok, message ->
            if (message.isNotEmpty()) {
                FluxaToast.show(activity, message, if (ok) FluxaIcon.CHECK else FluxaIcon.INFO)
            }
            onChanged()
        }
    }

    fun confirmLogout(activity: Activity, onChanged: () -> Unit) {
        FluxaSheet.showConfirm(
            activity, "Logout dari Google?",
            "Share project butuh login. Project lokal tidak ikut terhapus.",
            positiveText = "Logout", danger = true,
            onPositive = {
                GoogleAuth.logout(activity)
                FluxaToast.show(activity, "Sudah logout", FluxaIcon.CHECK)
                onChanged()
            }
        )
    }

    /** Sheet pengaturan AI: pilih provider, isi key sendiri, model OpenRouter. */
    fun showAiSettings(activity: Activity, onChanged: () -> Unit) {
        val col = LinearLayout(activity)
        col.orientation = LinearLayout.VERTICAL

        col.addView(activity.kText("PROVIDER", 10f, FluxaColors.Faint, bold = true))
        val providers = AiProvider.values()
        val drop = FluxaDropdown(activity)
        drop.setItems(providers.map { it.label }, providers.indexOf(AiSettings.provider(activity)))
        col.addView(drop, activity.lp(6f))

        col.addView(activity.kText("API KEY GROQ (chat, caption)", 10f, FluxaColors.Faint, bold = true), activity.lp(14f))
        val groq = activity.kEditText(AiSettings.key(activity, AiProvider.GROQ), "gsk_...")
        col.addView(groq, activity.lp(6f))

        col.addView(activity.kText("API KEY OPENROUTER", 10f, FluxaColors.Faint, bold = true), activity.lp(14f))
        val or = activity.kEditText(AiSettings.key(activity, AiProvider.OPENROUTER), "sk-or-...")
        col.addView(or, activity.lp(6f))

        col.addView(activity.kText("MODEL OPENROUTER", 10f, FluxaColors.Faint, bold = true), activity.lp(14f))
        val model = activity.kEditText(AiSettings.openRouterModel(activity), AiSettings.OPENROUTER_DEFAULT_MODEL)
        col.addView(model, activity.lp(6f))

        col.addView(
            activity.kText(
                "Key disimpan di HP ini saja dan tidak pernah ikut di APK. " +
                    "Caption otomatis selalu memakai Groq Whisper.",
                10.5f, FluxaColors.Dim
            ),
            activity.lp(14f)
        )

        FluxaSheet.show(
            activity, "Pengaturan AI", col,
            positiveText = "Simpan", positiveIcon = FluxaIcon.CHECK,
            onPositive = {
                AiSettings.setProvider(activity, providers[drop.selectedIndex])
                AiSettings.setKey(activity, AiProvider.GROQ, groq.text.toString())
                AiSettings.setKey(activity, AiProvider.OPENROUTER, or.text.toString())
                AiSettings.setOpenRouterModel(activity, model.text.toString())
                FluxaToast.show(activity, "Pengaturan AI disimpan", FluxaIcon.CHECK)
                onChanged()
            },
            negativeText = "Batal"
        )
    }

    /** Impor project dari ID atau link share. Hasil disimpan sebagai project baru. */
    fun showImport(activity: Activity, onImported: (Project) -> Unit) {
        val field = activity.kEditText("", "ID atau link share")
        FluxaSheet.show(
            activity, "Impor project dari link", field,
            positiveText = "Impor", positiveIcon = FluxaIcon.SHARE,
            onPositive = {
                val raw = field.text.toString().trim()
                if (raw.isNotEmpty()) {
                    FluxaToast.show(activity, "Mengambil project...", FluxaIcon.SHARE)
                    io.execute {
                        val p = ShareClient.fetch(raw)
                        activity.runOnUiThread {
                            if (p == null) {
                                FluxaToast.show(activity, "Link tidak ditemukan atau rusak", FluxaIcon.INFO)
                            } else {
                                ProjectLibrary.save(activity.applicationContext, p)
                                onImported(p)
                            }
                        }
                    }
                }
            },
            negativeText = "Batal"
        )
    }

    /** Unggah project ke worker share lalu tampilkan link yang bisa disalin. */
    fun shareProject(activity: Activity, project: Project) {
        val handle = FluxaSheet.showProgress(activity, "Membagikan project", "Mengunggah...")
        io.execute {
            ProjectLibrary.save(activity.applicationContext, project)
            val out = ShareClient.upload(activity.applicationContext, project)
            activity.runOnUiThread {
                handle.dismiss()
                if (!out.ok || out.url == null) {
                    FluxaToast.show(activity, out.message, FluxaIcon.INFO)
                    return@runOnUiThread
                }
                FluxaSheet.show(
                    activity, "Project dibagikan", activity.kText(out.url, 12f, FluxaColors.Text),
                    positiveText = "Salin link", positiveIcon = FluxaIcon.COPY,
                    onPositive = {
                        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Fluxa share", out.url))
                        FluxaToast.show(activity, "Link disalin", FluxaIcon.COPY)
                    },
                    negativeText = "Tutup"
                )
            }
        }
    }

    /** Helper kecil untuk menjalankan tugas background lalu kembali ke UI. */
    fun background(work: () -> Unit) {
        io.execute { work() }
    }

    private fun Activity.lp(topDp: Float) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).also { it.topMargin = dp(topDp) }
}
