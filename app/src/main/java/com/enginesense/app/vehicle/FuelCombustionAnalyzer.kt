package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity
import kotlin.math.abs
import kotlin.math.max

/** One signal shown on the Fuel & Combustion detail screen, with its value already formatted. */
data class SignalReading(val label: String, val value: String)

data class FuelCombustionInsight(
    val score: Int,
    val state: CategoryState,
    val primarySignal: SignalReading?,
    val supportingSignals: List<SignalReading>,
    /** A deterministic, templated sentence built from the actual readings — never a real AI call, never invented. */
    val interpretation: String,
    /** Which inputs this scan didn't have, if any — shown so a partial score is never mistaken for a complete one. */
    val dataCoverageNote: String?
)

/**
 * Fuel & Combustion gets its own scoring model instead of the generic finding-deduction
 * formula [HealthScoreCalculator] uses for Engine/Cooling, per the weighting the product
 * spec calls for:
 *   - Fuel mixture correction (STFT/LTFT) — 40%
 *   - Air intake / load, throttle / driver demand, combustion timing, exhaust/catalyst — 60%
 *
 * Only the fuel-trim component has a genuinely context-independent "healthy" magnitude (SAE
 * guidance: within ±10% is normal, further off means the ECU is compensating for something).
 * The other four buckets — MAP, throttle, timing, catalyst temp — vary legitimately with how
 * hard the engine is working right now, so inventing a standalone "badness" curve for them
 * would misread normal driving as a defect (the same reasoning [PidBenchmarks] already
 * applies to throttle/RPM/load on the All Sensors screen). Their 60% is represented instead
 * by whatever [DiagnosticFinding]s the deterministic rules engine already raised against
 * this category's PIDs — real detected problems only, never a guessed per-PID severity.
 *
 * Fuel level deliberately has no influence here (40% in the tank says nothing about how well
 * combustion is happening) — it belongs on the live dashboard, not this score.
 */
object FuelCombustionAnalyzer {

    /**
     * [categoryFindings] must already be filtered to this category by the caller (see
     * [HealthScoreCalculator.categoriesOf]) — a DTC-based finding like P0171 has no
     * [DiagnosticFinding.relatedPids] to filter by here, so re-deriving the filter from PIDs
     * alone would silently drop DTC-only findings. One source of truth for category
     * membership, not two that can disagree.
     */
    fun analyze(snapshot: VehicleSnapshot, categoryFindings: List<DiagnosticFinding>): FuelCombustionInsight {
        val stft = snapshot.verifiedValue(PidId.SHORT_TERM_FUEL_TRIM_1)
        val ltft = snapshot.verifiedValue(PidId.LONG_TERM_FUEL_TRIM_1)
        val map = snapshot.verifiedValue(PidId.INTAKE_MANIFOLD_PRESSURE)
        val load = snapshot.verifiedValue(PidId.ENGINE_LOAD)
        val relevantFindings = categoryFindings

        val trimMagnitude = listOfNotNull(stft?.let { abs(it) }, ltft?.let { abs(it) }).maxOrNull()
        val trimSubscore = trimMagnitude?.let { mag -> (100 - (((mag - 10.0) / 10.0 * 100.0)).coerceIn(0.0, 100.0)).toInt() }
        val trimState = when {
            trimMagnitude == null -> null
            trimMagnitude <= 10.0 -> CategoryState.GOOD
            trimMagnitude <= 20.0 -> CategoryState.WATCH
            else -> CategoryState.ATTENTION
        }

        val findingSubscore = max(0, 100 - relevantFindings.sumOf { deductionFor(it.severity) })
        val findingState = worstFindingState(relevantFindings)

        val score = if (trimSubscore != null) {
            ((trimSubscore * 0.4) + (findingSubscore * 0.6)).toInt()
        } else {
            findingSubscore
        }
        val state = worstState(listOfNotNull(trimState, findingState) + if (relevantFindings.isEmpty() && trimState == null) listOf(CategoryState.GOOD) else emptyList())

        val missing = mutableListOf<String>()
        if (stft == null) missing.add("short-term fuel trim")
        if (ltft == null) missing.add("long-term fuel trim")
        val coverageNote = if (missing.isEmpty()) null else "This scan didn't report ${missing.joinToString(" or ")}."

        val primary = ltft?.let { SignalReading("Long-term fuel trim", formatPct(it)) }
            ?: stft?.let { SignalReading("Short-term fuel trim", formatPct(it)) }
        val supporting = buildList {
            if (ltft != null && primary?.label != "Short-term fuel trim") stft?.let { add(SignalReading("STFT", formatPct(it))) }
            map?.let { add(SignalReading("MAP", "${it.toInt()} kPa")) }
            load?.let { add(SignalReading("Engine load", formatPct(it))) }
        }

        return FuelCombustionInsight(
            score = score.coerceIn(0, 100),
            state = state,
            primarySignal = primary,
            supportingSignals = supporting,
            interpretation = interpretation(stft, ltft, relevantFindings),
            dataCoverageNote = coverageNote
        )
    }

    private fun interpretation(stft: Double?, ltft: Double?, findings: List<DiagnosticFinding>): String {
        findings.firstOrNull()?.let { return it.plainLanguageSummary }

        if (stft == null && ltft == null) {
            return "No fuel trim data this scan — can't assess the air/fuel mixture correction."
        }
        val ltftText = ltft?.let {
            when {
                it > 10.0 -> "the computer is adding noticeably more fuel over time (long-term trim ${formatPct(it)})"
                it > 5.0 -> "the computer is adding a bit more fuel over time (long-term trim ${formatPct(it)})"
                it < -10.0 -> "the computer is cutting back fuel noticeably over time (long-term trim ${formatPct(it)})"
                it < -5.0 -> "the computer is cutting back fuel slightly over time (long-term trim ${formatPct(it)})"
                else -> "long-term fuel correction is within a normal range (${formatPct(it)})"
            }
        }
        val stftText = stft?.let {
            when {
                abs(it) > 10.0 -> "short-term correction is currently swinging more than usual (${formatPct(it)})"
                abs(it) > 5.0 -> "short-term correction is currently slightly ${if (it < 0) "negative" else "positive"} (${formatPct(it)})"
                else -> "short-term correction is steady (${formatPct(it)})"
            }
        }
        val sentence = listOfNotNull(ltftText, stftText).joinToString(", while ")
            .replaceFirstChar { it.uppercase() }

        val concerning = (ltft != null && abs(ltft) > 5.0) || (stft != null && abs(stft) > 10.0)
        val tail = if (concerning) {
            " This isn't enough by itself to confirm a fault, but it's worth monitoring."
        } else {
            " Nothing here points to a fuel mixture problem right now."
        }
        return "$sentence.$tail"
    }

    private fun worstFindingState(findings: List<DiagnosticFinding>): CategoryState? {
        if (findings.isEmpty()) return null
        return when (findings.minOf { severityRank(it.severity) }) {
            0 -> CategoryState.ATTENTION
            1, 2 -> CategoryState.WATCH
            else -> CategoryState.GOOD
        }
    }

    private fun worstState(states: List<CategoryState>): CategoryState {
        if (states.isEmpty()) return CategoryState.UNAVAILABLE
        val rank = { s: CategoryState -> when (s) { CategoryState.ATTENTION -> 0; CategoryState.WATCH -> 1; CategoryState.GOOD -> 2; CategoryState.UNAVAILABLE -> 3 } }
        return states.minBy(rank)
    }

    private fun deductionFor(severity: Severity): Int = when (severity) {
        Severity.CRITICAL -> 30
        Severity.MODERATE -> 15
        Severity.MINOR -> 5
        Severity.INFO -> 0
    }

    private fun severityRank(severity: Severity): Int = when (severity) {
        Severity.CRITICAL -> 0
        Severity.MODERATE -> 1
        Severity.MINOR -> 2
        Severity.INFO -> 3
    }

    private fun formatPct(value: Double): String = (if (value >= 0) "+" else "") + "%.1f%%".format(value)
}
