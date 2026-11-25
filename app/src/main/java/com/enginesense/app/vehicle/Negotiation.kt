package com.enginesense.app.vehicle

import kotlin.math.roundToLong

/** One reason to pay less, with the rupees it's worth. Always something the check found — never invented. */
data class NegotiationReason(val title: String, val source: String, val minInr: Double, val maxInr: Double)

/**
 * What to ask for. The numbers are ours — the market price and the fixed repair-cost table — so the
 * same check gives the same plan; AI only writes the words around it.
 *
 * - Open with: market low − repairs high
 * - Fair deal: market middle − repairs middle
 * - Walk away over: market middle − repairs low
 * All rounded to ₹5,000, the way people haggle.
 */
data class NegotiationPlan(
    val askingInr: Double?,
    val openWith: Double,
    val fairDeal: Double,
    val walkAwayOver: Double,
    /** Asking minus the fair deal; null without an asking price, 0 when the asking price is already fair. */
    val askOffInr: Double?,
    val reasons: List<NegotiationReason>,
    val repairsMin: Double,
    val repairsMax: Double
)

object NegotiationPlanner {
    private const val ROUND_TO = 5_000.0

    fun plan(report: PrePurchaseReport, askingInr: Double?, extra: List<NegotiationReason> = emptyList()): NegotiationPlan? {
        val fair = report.value?.fair ?: return null
        // The same repairs the report totals: confirmed faults and the look-around, one per category.
        val repairs = report.faults
            .filter { it.countedInTotal && it.cost != null }
            .distinctBy { it.cost!!.category }
            .map { NegotiationReason(it.title, sourceOf(it.kind), it.cost!!.minInr.toDouble(), it.cost.maxInr.toDouble()) } + extra
        return plan(fair.lowEstimate, fair.highEstimate, repairs, askingInr)
    }

    fun plan(marketLow: Double, marketHigh: Double, repairs: List<NegotiationReason>, askingInr: Double?): NegotiationPlan {
        val marketMid = (marketLow + marketHigh) / 2
        val repairsMin = repairs.sumOf { it.minInr }
        val repairsMax = repairs.sumOf { it.maxInr }

        val openWith = round(marketLow - repairsMax)
        val fairDeal = round(marketMid - (repairsMin + repairsMax) / 2)
        val walkAway = round(marketMid - repairsMin)

        val asking = askingInr?.takeIf { it > 0 }
        val overMarket = asking?.let { it - marketMid }?.takeIf { it >= 1_000 }
        val reasons = repairs + listOfNotNull(
            overMarket?.let { NegotiationReason("Priced above the market", "Asking is above the middle of the fair price", it, it) }
        )
        return NegotiationPlan(
            askingInr = asking,
            openWith = minOf(openWith, fairDeal),
            fairDeal = fairDeal,
            walkAwayOver = maxOf(walkAway, fairDeal),
            askOffInr = asking?.let { round(it - fairDeal).coerceAtLeast(0.0) },
            reasons = reasons,
            repairsMin = repairsMin,
            repairsMax = repairsMax
        )
    }

    private fun sourceOf(kind: FaultKind) = when (kind) {
        FaultKind.EXTERIOR -> "Look-around"
        FaultKind.STORED -> "OBD scan · fault code"
        FaultKind.HIDDEN_PERMANENT -> "OBD scan · hidden code"
        FaultKind.PENDING -> "OBD scan · developing fault"
        FaultKind.DRIVE -> "Test drive"
    }

    private fun round(v: Double): Double = ((v / ROUND_TO).roundToLong() * ROUND_TO).coerceAtLeast(0.0)
}
