package com.enginesense.app.vehicle.pricing

import com.enginesense.app.vehicle.ai.RepairCostEntry

/**
 * Max buy price = selling price (dealer-supplied directly, not derived from comparables) -
 * refurbishment - margin. Replaced the earlier comparable-matching engine (which estimated
 * resale value from comparable sales) entirely — that engine and its models were removed once
 * this simpler, dealer-supplied-selling-price flow took over; this screen no longer estimates
 * resale value itself.
 */
data class SimpleBuyPriceResult(
    /** Non-negative, for display compatibility with a UI that always wants a number to show — see [rawBuyPriceInr] for the real one. */
    val buyPriceInr: Double,
    /** The true, possibly-negative result — a deal with negative economics must read as not viable, never an innocuous "buy for ₹0" recommendation. */
    val rawBuyPriceInr: Double,
    val isViable: Boolean,
    val sellingPriceInr: Double,
    val refurbishmentInr: Double,
    val marginPercent: Int,
    val marginAmountInr: Double
)

object SimpleBuyPriceCalculator {

    /** Uses each repair's midpoint — min and max are both just ends of a tentative range, and the midpoint is the single most representative figure to subtract, matching how each item's estimate is displayed to the dealer. */
    fun totalRefurbishmentInr(selectedRepairs: List<RepairCostEntry>, manualAdditionalCostInr: Double): Double =
        selectedRepairs.sumOf { (it.minInr + it.maxInr) / 2.0 } + manualAdditionalCostInr

    fun calculate(sellingPriceInr: Double, refurbishmentInr: Double, marginPercent: Int): SimpleBuyPriceResult {
        val marginAmount = sellingPriceInr * (marginPercent / 100.0)
        val raw = sellingPriceInr - refurbishmentInr - marginAmount
        return SimpleBuyPriceResult(
            buyPriceInr = raw.coerceAtLeast(0.0),
            rawBuyPriceInr = raw,
            isViable = raw > 0.0,
            sellingPriceInr = sellingPriceInr,
            refurbishmentInr = refurbishmentInr,
            marginPercent = marginPercent,
            marginAmountInr = marginAmount
        )
    }
}
