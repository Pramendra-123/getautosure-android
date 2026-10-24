package com.enginesense.app.consultation

/**
 * Same gating pattern as [com.enginesense.app.vehicle.ai.AiBackendConfig] — see that class's
 * doc. The `consultations` Edge Function and its migration
 * (supabase/functions/consultations/index.ts,
 * supabase/migrations/20261024000000_consultations.sql) are deployed to the same Supabase
 * project as AI Mechanic's own backend. [ConsultationViewModel] falls back to
 * [MockExpertRepository] only if this is ever flipped off or the URL is cleared.
 */
object ConsultationBackendConfig {
    const val REMOTE_ENABLED: Boolean = true
    const val ENDPOINT_URL: String = "https://xofkkvhkkbtcayibjaul.supabase.co/functions/v1/consultations"

    private val PLACEHOLDER_MARKERS = listOf("REPLACE_", "YOUR_", "placeholder", "example.com", "TODO")

    fun isValidEndpoint(url: String): Boolean =
        url.isNotBlank() && PLACEHOLDER_MARKERS.none { url.contains(it, ignoreCase = true) }

    val isConfigured: Boolean
        get() = REMOTE_ENABLED && isValidEndpoint(ENDPOINT_URL)
}
