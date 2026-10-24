package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity

enum class PurchaseVerdict { GOOD_BUY, BUY_WITH_CAUTION, AVOID }

data class NegotiationPoint(val title: String, val recommendedAction: String, val severity: Severity)

data class PrePurchaseInsight(
    val verdict: PurchaseVerdict,
    val headline: String,
    /** Findings worth raising with the seller — Dealer Pro only; everyone still gets [verdict] and [headline] free. */
    val negotiationPoints: List<NegotiationPoint>
)

/**
 * "Should I buy this car?" — the exact same scan/findings pipeline as checking a car you
 * already own, just read with a purchase decision in mind instead of an ownership status.
 * The verdict is free for everyone (it's just a severity-based read of data already
 * computed); the itemized negotiation points are the Dealer Pro layer, since that's where
 * the repeated, professional use of "which findings justify a lower offer" actually is.
 *
 * Deliberately has no price attached to [negotiationPoints] — see RepairCostReferenceScreen
 * and the PrePurchase screens for why: a specific number here needs real market data this
 * app doesn't have, and a wrong one has real financial consequences for someone's
 * negotiation. The findings themselves are real; a price estimate on top of them wouldn't be.
 */
object PrePurchaseAnalyzer {

    fun analyze(findings: List<DiagnosticFinding>, healthScore: Int?): PrePurchaseInsight {
        val worst = findings.minByOrNull { severityRank(it.severity) }?.severity

        val verdict = when {
            worst == Severity.CRITICAL -> PurchaseVerdict.AVOID
            worst == Severity.MODERATE || worst == Severity.MINOR -> PurchaseVerdict.BUY_WITH_CAUTION
            healthScore == null -> PurchaseVerdict.BUY_WITH_CAUTION
            healthScore < 60 -> PurchaseVerdict.BUY_WITH_CAUTION
            else -> PurchaseVerdict.GOOD_BUY
        }

        val headline = when {
            verdict == PurchaseVerdict.AVOID -> "This scan found a serious issue — walk away or get it fixed before you buy."
            healthScore == null -> "This car's computer didn't report enough data to score — that's a limitation of this scan, not a clean bill of health. Get a physical inspection before deciding."
            verdict == PurchaseVerdict.BUY_WITH_CAUTION -> "Buyable, but go in with your eyes open — a few things are worth raising with the seller."
            else -> "Nothing concerning on this scan. A healthy buy, as far as the OBD data can tell."
        }

        val negotiationPoints = findings
            .filter { it.severity == Severity.CRITICAL || it.severity == Severity.MODERATE || it.severity == Severity.MINOR }
            .sortedBy { severityRank(it.severity) }
            .map { NegotiationPoint(it.title, it.recommendedAction, it.severity) }

        return PrePurchaseInsight(verdict, headline, negotiationPoints)
    }

    private fun severityRank(severity: Severity): Int = when (severity) {
        Severity.CRITICAL -> 0
        Severity.MODERATE -> 1
        Severity.MINOR -> 2
        Severity.INFO -> 3
    }
}
