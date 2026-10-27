package com.enginesense.app.dealerreport

/**
 * Same gating pattern as [com.enginesense.app.vehicle.ai.AiBackendConfig] and
 * [com.enginesense.app.consultation.ConsultationBackendConfig] — see either's doc. Starts
 * The `reports` Edge Function and its migration (supabase/functions/reports/index.ts,
 * supabase/migrations/20261025000000_vehicle_reports.sql) are deployed to the same Supabase
 * project as the rest of this app's backend.
 */
object DealerReportBackendConfig {
    const val REMOTE_ENABLED: Boolean = true
    const val ENDPOINT_URL: String = "https://xofkkvhkkbtcayibjaul.supabase.co/functions/v1/reports"

    private val PLACEHOLDER_MARKERS = listOf("REPLACE_", "YOUR_", "placeholder", "example.com", "TODO")

    fun isValidEndpoint(url: String): Boolean =
        url.isNotBlank() && PLACEHOLDER_MARKERS.none { url.contains(it, ignoreCase = true) }

    val isConfigured: Boolean
        get() = REMOTE_ENABLED && isValidEndpoint(ENDPOINT_URL)
}
