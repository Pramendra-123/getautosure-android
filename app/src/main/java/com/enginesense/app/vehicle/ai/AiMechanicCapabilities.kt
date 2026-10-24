package com.enginesense.app.vehicle.ai

/**
 * Free vs Premium entitlement boundary for the AI Mechanic. One chat engine serves both tiers —
 * see [RuleBasedAiMechanicChatService] — this only draws the line on what each tier is *allowed*
 * to reason over, so a future remote/smarter implementation has a boundary to check against
 * instead of needing a separate Premium code path.
 *
 * PREMIUM is not active anywhere yet: [AiMechanicCapabilities] exists so the architecture is
 * ready, per the product requirement to prepare tiering without shipping fake Premium features.
 * The app's current "Advanced AI Mechanic" preview (see AiMechanicScreen's `advancedMode`)
 * deliberately still answers identically to Free — there is no scan-history reasoning built yet,
 * and claiming otherwise would be exactly the invented capability this contract exists to avoid.
 */
enum class AiMechanicTier { FREE, PREMIUM }

object AiMechanicCapabilities {
    /** Reasoning across more than the latest scan — needs real history data wired into [AiVehicleContext] first. */
    fun canUseScanHistoryReasoning(tier: AiMechanicTier): Boolean = tier == AiMechanicTier.PREMIUM

    /** Reasoning across multiple DTCs/sensors together rather than one at a time. */
    fun canUseCrossSensorReasoning(tier: AiMechanicTier): Boolean = tier == AiMechanicTier.PREMIUM

    /** Real repair-cost figures. False for both tiers until a priced data source exists — see RuleBasedAiMechanicChatService's cost-question handling. */
    @Suppress("UNUSED_PARAMETER")
    fun canUseRepairCostReference(tier: AiMechanicTier): Boolean = false
}
