package com.kilat.motion.core.cloud

import com.kilat.motion.BuildConfig

/** Konfigurasi layanan cloud. Tidak ada secret di sini: hanya ID publik dan URL. */
object CloudConfig {
    /** Worker share preset (Cloudflare Worker + KV). Sumber: share-worker/ di repo. */
    const val SHARE_BASE_URL = "https://kilat-share.dikanjut.workers.dev"

    val googleClientId: String get() = BuildConfig.GOOGLE_CLIENT_ID
    val googleRedirectScheme: String get() = BuildConfig.GOOGLE_REDIRECT_SCHEME
    val googleRedirectUri: String get() = "$googleRedirectScheme:/oauth2redirect"

    val googleConfigured: Boolean get() = googleClientId.endsWith(".apps.googleusercontent.com")
}
