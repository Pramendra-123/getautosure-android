package com.enginesense.app.vehicle.ai

/**
 * Whether the AI Mechanic chat should attempt a remote call at all. The `ai-mechanic` Supabase
 * Edge Function has been deployed and verified end-to-end with a real device Google sign-in
 * token and a real Gemini response (200 OK, ~9.8s) — see [RemoteAiMechanicChatService]'s class
 * doc for the contract it implements.
 *
 * [REMOTE_ENABLED] alone isn't enough to turn remote on — [isConfigured] also requires a
 * real-looking [ENDPOINT_URL], so flipping the flag on by mistake with an empty/placeholder URL
 * still can't send a request anywhere. [com.enginesense.app.GetAutoSureViewModel] only
 * constructs [RemoteAiMechanicChatService] when [isConfigured] is true.
 */
object AiBackendConfig {
    const val REMOTE_ENABLED: Boolean = true
    const val ENDPOINT_URL: String = "https://xofkkvhkkbtcayibjaul.supabase.co/functions/v1/ai-mechanic"

    private val PLACEHOLDER_MARKERS = listOf("REPLACE_", "YOUR_", "placeholder", "example.com", "TODO")

    /** Pulled out of [isConfigured] so it's unit-testable independent of the two consts above, which can't be swapped at test time. */
    fun isValidEndpoint(url: String): Boolean =
        url.isNotBlank() && PLACEHOLDER_MARKERS.none { url.contains(it, ignoreCase = true) }

    val isConfigured: Boolean
        get() = REMOTE_ENABLED && isValidEndpoint(ENDPOINT_URL)
}
