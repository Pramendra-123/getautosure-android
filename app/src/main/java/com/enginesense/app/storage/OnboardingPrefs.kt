package com.enginesense.app.storage

import android.content.Context

/**
 * Whether this install has been through the welcome / sign-in / add-your-car flow, so the app
 * opens straight on Home next time instead of starting over.
 */
object OnboardingPrefs {
    private const val PREFS = "getautosure_app"
    private const val KEY_DONE = "onboarding_done"

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
    }
}
