package com.enginesense.app.storage

import android.content.Context

/**
 * The language owners read their car's problems in: fault cards, AI Mechanic replies and, later,
 * reports. Menus and buttons stay in English for now. [code] matches the fault-card files
 * (assets/fault_cards/cards.<code>.json) and what the AI Mechanic is asked to reply in.
 */
enum class AppLanguage(val code: String, val nativeName: String, val englishName: String) {
    ENGLISH("en", "English", "English"),
    TAMIL("ta", "தமிழ்", "Tamil"),
    TELUGU("te", "తెలుగు", "Telugu"),
    KANNADA("kn", "ಕನ್ನಡ", "Kannada"),
    MALAYALAM("ml", "മലയാളം", "Malayalam"),
    MARATHI("mr", "मराठी", "Marathi"),
    HINDI("hi", "हिंदी", "Hindi");

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }
    }
}

object LanguagePrefs {
    private const val PREFS = "getautosure_app"
    private const val KEY_LANGUAGE = "app_language"

    /** English until the user picks — installs from before languages existed stay as they were. */
    fun get(context: Context): AppLanguage =
        AppLanguage.fromCode(prefs(context).getString(KEY_LANGUAGE, null)) ?: AppLanguage.ENGLISH

    /** First launch: the phone's own language when we support it, so most people just tap Continue. */
    fun suggested(context: Context): AppLanguage =
        AppLanguage.fromCode(prefs(context).getString(KEY_LANGUAGE, null))
            ?: AppLanguage.fromCode(java.util.Locale.getDefault().language)
            ?: AppLanguage.ENGLISH

    fun set(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
