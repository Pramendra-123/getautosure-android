package com.enginesense.app.vehiclelookup

/**
 * Same gating pattern as [com.enginesense.app.dealerreport.DealerReportBackendConfig]. The
 * `vehicle-lookup` Edge Function (supabase/functions/vehicle-lookup/index.ts) is deployed to the
 * same Supabase project as the rest of this app's backend — currently against Surepass's SANDBOX.
 */
object VehicleLookupBackendConfig {
    const val REMOTE_ENABLED: Boolean = true
    const val ENDPOINT_URL: String = "https://xofkkvhkkbtcayibjaul.supabase.co/functions/v1/vehicle-lookup"

    private val PLACEHOLDER_MARKERS = listOf("REPLACE_", "YOUR_", "placeholder", "example.com", "TODO")

    fun isValidEndpoint(url: String): Boolean =
        url.isNotBlank() && PLACEHOLDER_MARKERS.none { url.contains(it, ignoreCase = true) }

    val isConfigured: Boolean
        get() = REMOTE_ENABLED && isValidEndpoint(ENDPOINT_URL)
}
