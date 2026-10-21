package com.kilat.motion.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.LinearLayout
import com.kilat.motion.core.ai.AiProvider
import com.kilat.motion.core.ai.AiSettings
import com.kilat.motion.core.cloud.CloudConfig
import com.kilat.motion.core.cloud.GoogleAuth
import com.kilat.motion.core.cloud.ShareClient
import com.kilat.motion.core.model.Project
import com.kilat.motion.data.ProjectLibrary
import com.kilat.motion.ui.components.KilatDropdown
import com.kilat.motion.ui.components.KilatIcon
import com.kilat.motion.ui.components.KilatSheet
import com.kilat.motion.ui.components.KilatToast
import com.kilat.motion.ui.components.kEditText
import com.kilat.motion.ui.theme.KColors
import com.kilat.motion.ui.theme.dp
import com.kilat.motion.ui.theme.kText
import java.util.concurrent.Executors

/** Sheet akun, impor/bagikan project, dan pengaturan AI. Semua I/O jalan di background. */
object CloudSettings {

    private val io = Executors.newCachedThreadPool()

    fun startLogin(activity: Activity) {
        if (!CloudConfig.googleConfigured) {
            KilatSheet.showMessage(
                activity, "Login belum aktif",
                "Client ID Google untuk Android belum diisi.\n\n" +
                    "Isi googleClientIdAndroid di gradle.properties, lalu build ulang.",
                icon = KilatIcon.USER
            )
            return
        }
        val url = GoogleAuth.buildAuthUrl(activity)
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        KilatToast.show(activity, "Selesaikan login di browser", KilatIcon.USER)
    }

    fun confirmLogout(activity: Activity, onChanged: () -> Unit) {
        KilatSheet.showConfirm(
            activity, "Logout dari Google?",
            "Share project butuh login. Project lokal tidak ikut terhapus.",
            positiveText = "Logout", danger = true,
            onPositive = {
                GoogleAuth.logout(activity)
                KilatToast.show(activity, "Sudah logout", KilatIcon.CHECK)
                onChanged()
            }
        )
    }

    /** Sheet pengaturan AI: pilih provider, isi key sendiri, model OpenRouter. */
    fun showAiSettings(activity: Activity, onChanged: () -> Unit) {
        val col = LinearLayout(activity)
        col.orientation = LinearLayout.VERTICAL

        col.addView(activity.kText("PROVIDER", 10f, KColors.Faint, bold = true))
        val providers = AiProvider.values()
        val drop = KilatDropdown(activity)
        drop.setItems(providers.map { it.label }, providers.indexOf(AiSettings.provider(activity)))
        col.addView(drop, activity.lp(6f))

        col.addView(activity.kText("API KEY GROQ (chat, caption)", 10f, KColors.Faint, bold = true), activity.lp(14f))
        val groq = activity.kEditText(AiSettings.key(activity, AiProvider.GROQ), "gsk_...")
        col.addView(groq, activity.lp(6f))

        col.addView(activity.kText("API KEY OPENROUTER", 10f, KColors.Faint, bold = true), activity.lp(14f))
        val or = activity.kEditText(AiSettings.key(activity, AiProvider.OPENROUTER), "sk-or-...")
        col.addView(or, activity.lp(6f))

        col.addView(activity.kText("MODEL OPENROUTER", 10f, KColors.Faint, bold = true), activity.lp(14f))
        val model = activity.kEditText(AiSettings.openRouterModel(activity), AiSettings.OPENROUTER_DEFAULT_MODEL)
        col.addView(model, activity.lp(6f))

        col.addView(
            activity.kText(
                "Key disimpan di HP ini saja dan tidak pernah ikut di APK. " +
                    "Caption otomatis selalu memakai Groq Whisper.",
                10.5f, KColors.Dim
            ),
            activity.lp(14f)
        )

        KilatSheet.show(
            activity, "Pengaturan AI", col,
            positiveText = "Simpan", positiveIcon = KilatIcon.CHECK,
            onPositive = {
                AiSettings.setProvider(activity, providers[drop.selectedIndex])
                AiSettings.setKey(activity, AiProvider.GROQ, groq.text.toString())
                AiSettings.setKey(activity, AiProvider.OPENROUTER, or.text.toString())
                AiSettings.setOpenRouterModel(activity, model.text.toString())
                KilatToast.show(activity, "Pengaturan AI disimpan", KilatIcon.CHECK)
                onChanged()
            },
            negativeText = "Batal"
        )
    }

    /** Impor project dari ID atau link share. Hasil disimpan sebagai project baru. */
    fun showImport(activity: Activity, onImported: (Project) -> Unit) {
        val field = activity.kEditText("", "ID atau link share")
        KilatSheet.show(
            activity, "Impor project dari link", field,
            positiveText = "Impor", positiveIcon = KilatIcon.SHARE,
            onPositive = {
                val raw = field.text.toString().trim()
                if (raw.isNotEmpty()) {
                    KilatToast.show(activity, "Mengambil project...", KilatIcon.SHARE)
                    io.execute {
                        val p = ShareClient.fetch(raw)
                        activity.runOnUiThread {
                            if (p == null) {
                                KilatToast.show(activity, "Link tidak ditemukan atau rusak", KilatIcon.INFO)
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
        val handle = KilatSheet.showProgress(activity, "Membagikan project", "Mengunggah...")
        io.execute {
            ProjectLibrary.save(activity.applicationContext, project)
            val out = ShareClient.upload(activity.applicationContext, project)
            activity.runOnUiThread {
                handle.dismiss()
                if (!out.ok || out.url == null) {
                    KilatToast.show(activity, out.message, KilatIcon.INFO)
                    return@runOnUiThread
                }
                KilatSheet.show(
                    activity, "Project dibagikan", activity.kText(out.url, 12f, KColors.Text),
                    positiveText = "Salin link", positiveIcon = KilatIcon.COPY,
                    onPositive = {
                        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("KILAT share", out.url))
                        KilatToast.show(activity, "Link disalin", KilatIcon.COPY)
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
