package com.enginesense.app.vehicle.pricing

import com.enginesense.app.vehicle.DiagnosticFinding
import com.enginesense.app.vehicle.ai.RepairCostReference

/**
 * Turns a scan's diagnostic findings into a refurbishment-cost estimate by reusing
 * [RepairCostReference]'s existing tentative INR ranges — the same "approximate, clearly
 * labeled, never a real quote" data this app already shows elsewhere (Understand Repair, AI
 * Mechanic), not a new pricing source. Findings with no matching cost category are simply
 * skipped rather than guessed at. Deliberately produces a [RefurbishmentEstimate] on its own —
 * refurbishment cost is never combined with a selling price inside this function, that happens
 * only where a caller explicitly does it (see [SimpleBuyPriceCalculator] for the current "Max
 * Buy Price" screen's own, simpler path, which doesn't go through this function at all yet; this
 * one exists for when findings-driven — e.g. future OBD-scan — input is wired up).
 *
 * [confirmedQuotes] lets a caller override a category's indicative range with a real workshop
 * quote (category name -> amount in INR) — that line item is then tagged
 * [CostProvenance.CONFIRMED] instead of [CostProvenance.INDICATIVE], so the result never implies
 * a guess is a verified number. A confirmed quote is included even when no finding matched that
 * category (real-world knowledge the scan didn't catch is still real).
 *
 * Deliberately has no model of which findings might share a single underlying root cause (e.g. a
 * lean-mixture code and a separately-worded O2 sensor finding could, in reality, be one repair) —
 * building that needs a DTC-relationship table this app doesn't have, so categories are deduped
 * only when they match the exact same [com.enginesense.app.vehicle.ai.RepairCostEntry.category]
 * string. This can overstate total cost when multiple findings are really one issue; there is no
 * way to detect that case from the data this function receives.
 */
object RefurbishmentEstimator {

    fun estimate(findings: List<DiagnosticFinding>, confirmedQuotes: Map<String, Int> = emptyMap()): RefurbishmentEstimate {
        val indicativeByCategory = findings
            .mapNotNull { finding -> RepairCostReference.findByTitle(finding.title) }
            .distinctBy { it.category }
            .associate { it.category to RefurbishmentLineItem(it.category, it.minInr, it.maxInr, CostProvenance.INDICATIVE) }

        val merged = indicativeByCategory.toMutableMap()
        confirmedQuotes.forEach { (category, amountInr) ->
            merged[category] = RefurbishmentLineItem(category, amountInr, amountInr, CostProvenance.CONFIRMED)
        }

        val lineItems = merged.values.toList()
        return RefurbishmentEstimate(
            lineItems = lineItems,
            totalMinInr = lineItems.sumOf { it.minInr },
            totalMaxInr = lineItems.sumOf { it.maxInr }
        )
    }
}
