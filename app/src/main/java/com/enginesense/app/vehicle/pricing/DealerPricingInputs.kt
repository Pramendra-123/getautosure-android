package com.enginesense.app.vehicle.pricing

import com.enginesense.app.obd.Severity
import com.enginesense.app.vehicle.DiagnosticFinding

/**
 * Pure, UI-framework-free parsing and validation for Dealer Sathi's pricing screens — kept
 * separate from any Composable so it's unit-testable without Compose or Robolectric, matching
 * this project's established pattern (no screen in this codebase has its own test file; the
 * testable logic always lives in a plain Kotlin layer the screen calls into).
 *
 * The comparable-row/vehicle-spec parsing that used to live here was removed along with the
 * comparable-matching engine it fed — [com.enginesense.app.ui.screens.DealerPricingScreen] now
 * builds its target-vehicle fields directly from dropdown selections (see
 * [DealerVehicleOptions]), no free-text parsing needed for them.
 */

/** Blank, unparseable, zero or negative input all become "no amount entered" (0.0) — a negative cost or profit figure would silently work backwards (increasing, not decreasing, the max buy price), so it's rejected the same as a missing value rather than accepted at face value. */
fun parseNonNegativeAmount(raw: String): Double = raw.trim().toDoubleOrNull()?.takeIf { it >= 0.0 } ?: 0.0

/**
 * One [DiagnosticFinding] per dealer-ticked [com.enginesense.app.vehicle.ai.RepairCostReference]
 * category — using the category's own name as the finding title reliably routes it back through
 * [RefurbishmentEstimator]'s existing keyword matching (every category name is itself a keyword
 * match for its own entry), rather than re-implementing that lookup here. Not currently called by
 * the live screen (it builds its refurb total directly from selected
 * [com.enginesense.app.vehicle.ai.RepairCostEntry] values — see
 * [SimpleBuyPriceCalculator.totalRefurbishmentInr]); kept for whenever findings-driven input
 * (e.g. an eventual OBD-scan auto-fill) is wired up.
 */
fun findingsForSelectedCategories(categories: Collection<String>): List<DiagnosticFinding> =
    categories.map { category ->
        DiagnosticFinding(
            id = category,
            title = category,
            plainLanguageSummary = "",
            severity = Severity.INFO,
            recommendedAction = ""
        )
    }

/** A blank, unparseable, or non-positive confirmed-quote entry for a category is simply omitted — "no confirmed quote supplied", never coerced to 0 or accepted negative, so [RefurbishmentEstimator] keeps using the indicative range for that category rather than letting a typo'd negative number work backwards and inflate the max buy price. */
fun parseConfirmedQuotes(rawByCategory: Map<String, String>): Map<String, Int> =
    rawByCategory.mapNotNull { (category, raw) -> raw.trim().toIntOrNull()?.takeIf { it > 0 }?.let { category to it } }.toMap()
