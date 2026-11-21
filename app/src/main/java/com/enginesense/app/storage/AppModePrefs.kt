package com.enginesense.app.storage

import android.content.Context

/**
 * Who the user said they are on first launch — it picks the home screen and the bottom tabs.
 * Switching it in Profile keeps every car, report and booking; it only changes what's in front.
 */
enum class AppMode(val title: String, val subtitle: String) {
    OWN_CAR("I own a car", "Health score, battery, service alerts and repair costs for your car."),
    BUYING("I'm buying a used car", "Check a car before you pay: hidden faults, RTO record, repair costs and a fair price."),
    DEALER("I'm a dealer or mechanic", "Scan many cars and share branded reports and WhatsApp Status posters.")
}

object AppModePrefs {
    private const val PREFS = "getautosure_app"
    private const val KEY_MODE = "app_mode"

    /** Installs from before modes existed were all owner installs, so that's the default. */
    fun get(context: Context): AppMode =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null)
            ?.let { name -> AppMode.entries.firstOrNull { it.name == name } }
            ?: AppMode.OWN_CAR

    fun set(context: Context, mode: AppMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode.name).apply()
    }
}
