package com.enginesense.app.vehicle.pricing

/**
 * Dealer Sathi's refurbishment-cost shapes — the comparable-matching resale models that used to
 * live here (VehicleSpec, ComparableListing, MatchTier, ResaleEstimate, the old
 * MaxBuyPriceResult, PricingConfig and friends) were removed once the "Max Buy Price" screen
 * switched to a dealer-supplied selling price (see [SimpleBuyPriceCalculator]) instead of
 * estimating resale value from comparables — they're no longer used anywhere in the app.
 */

/** [CONFIRMED] is only ever set by a caller-supplied quote (see [RefurbishmentEstimator]'s `confirmedQuotes` parameter) — every [com.enginesense.app.vehicle.ai.RepairCostReference]-derived figure is [INDICATIVE] by construction, since that reference is itself tentative ballpark data, not a real quote. */
enum class CostProvenance { INDICATIVE, CONFIRMED }

data class RefurbishmentLineItem(
    val category: String,
    val minInr: Int,
    val maxInr: Int,
    val provenance: CostProvenance = CostProvenance.INDICATIVE
)

/** Deliberately its own shape, never folded into a resale/selling-price figure before [SimpleBuyPriceCalculator] combines them explicitly. */
data class RefurbishmentEstimate(
    val lineItems: List<RefurbishmentLineItem>,
    val totalMinInr: Int,
    val totalMaxInr: Int
)
