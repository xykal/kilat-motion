package com.fluxa.motion.data

import android.content.Context

/** Preferensi kecil aplikasi (status onboarding dll). */
object Prefs {

    private const val FILE = "fluxa_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done_v1"

    fun isOnboardingDone(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    fun resetOnboarding(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ONBOARDING_DONE, false).apply()
    }
}
