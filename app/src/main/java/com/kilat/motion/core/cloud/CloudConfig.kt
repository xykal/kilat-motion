package com.kilat.motion.core.cloud

import com.kilat.motion.BuildConfig

/** Konfigurasi layanan cloud. Tidak ada secret di sini: hanya ID publik dan URL. */
object CloudConfig {
    /** Worker share preset (Cloudflare Worker + KV). Sumber: share-worker/ di repo. */
    const val SHARE_BASE_URL = "https://kilat-share.dikanjut.workers.dev"

    /** Web client ID dipakai sebagai serverClientId; id_token yang keluar punya aud = ID ini. */
    val googleWebClientId: String get() = BuildConfig.GOOGLE_WEB_CLIENT_ID

    val googleConfigured: Boolean get() = googleWebClientId.endsWith(".apps.googleusercontent.com")
}
