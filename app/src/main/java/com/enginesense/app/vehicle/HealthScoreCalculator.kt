package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity
import kotlin.math.max

/**
 * Turns the rules engine's already-decided [DiagnosticFinding]s (plus raw PID
 * availability, for the "we genuinely don't know" case) into one headline score and a
 * handful of category states — the WHOOP/Oura-style summary the home screen needs.
 *
 * This is a presentation layer on top of [DiagnosticRulesEngine], not a second source of
 * truth: every point lost and every category state traces back to a finding the rules
 * engine already produced, or to a [PidStatus] the pipeline already measured. It never
 * invents a problem the rules engine didn't find.
 *
 * Engine/Cooling/Fuel & Combustion deliberately share PIDs (MAP, throttle, ignition timing,
 * catalyst temp) — the same sensor is real evidence for more than one question ("is the
 * engine behaving normally" vs "is the air/fuel mixture healthy"), so a finding tied to one
 * of those PIDs can count against more than one category's score. [categoriesOf] returns a
 * set, not a single category, for exactly this reason.
 */
object HealthScoreCalculator {

    private val ENGINE_PIDS = setOf(
        PidId.RPM, PidId.ENGINE_LOAD, PidId.ABSOLUTE_LOAD_VALUE,
        PidId.ACTUAL_ENGINE_TORQUE_PERCENT, PidId.ENGINE_REFERENCE_TORQUE,
        PidId.THROTTLE_POSITION, PidId.RELATIVE_THROTTLE_POSITION, PidId.ACCELERATOR_PEDAL_POSITION_D,
        PidId.TIMING_ADVANCE, PidId.INTAKE_MANIFOLD_PRESSURE, PidId.RUN_TIME_SINCE_START,
        PidId.INTAKE_AIR_TEMP_C
    )
    private val COOLING_PIDS = setOf(
        PidId.COOLANT_TEMP_C, PidId.INTAKE_AIR_TEMP_C, PidId.AMBIENT_AIR_TEMP_C, PidId.CATALYST_TEMP_B1S1,
        PidId.RPM, PidId.ENGINE_LOAD, PidId.SPEED_KMH, PidId.RUN_TIME_SINCE_START
    )
    private val FUEL_COMBUSTION_PIDS = setOf(
        PidId.SHORT_TERM_FUEL_TRIM_1, PidId.LONG_TERM_FUEL_TRIM_1,
        PidId.INTAKE_MANIFOLD_PRESSURE, PidId.INTAKE_AIR_TEMP_C, PidId.ENGINE_LOAD,
        PidId.THROTTLE_POSITION, PidId.ACCELERATOR_PEDAL_POSITION_D,
        PidId.TIMING_ADVANCE, PidId.CATALYST_TEMP_B1S1
    )
    private val BATTERY_PIDS = setOf(PidId.CONTROL_MODULE_VOLTAGE)

    /** The categories that get a 0-100 number instead of just a Good/Watch/Attention label. */
    private val SCORED_CATEGORIES = setOf(HealthCategory.ENGINE, HealthCategory.COOLING, HealthCategory.FUEL_COMBUSTION)

    fun calculate(snapshot: VehicleSnapshot, findings: List<DiagnosticFinding>): HealthScoreResult {
        val fuelFindings = findings.filter { HealthCategory.FUEL_COMBUSTION in categoriesOf(it) }
        val fuelInsight = FuelCombustionAnalyzer.analyze(snapshot, fuelFindings)

        val categories = HealthCategory.entries.map { category ->
            categoryHealth(category, snapshot, findings, fuelInsight)
        }

        // Emissions/Diagnostics have no PID-capability gate (they're purely finding-driven —
        // see categoryHealth's `relevantPids = emptySet()` for them), so they can never
        // actually report UNAVAILABLE. That means "all 6 categories unavailable" can never be
        // true in practice — checking that would leave this condition dead code, same as the
        // pre-existing (and, it turns out, already-unreachable) "Not enough data" label below
        // used to be. The real signal for "nothing to go on" is: none of the 3 PID-scored
        // categories have any evidence, AND there isn't even a DTC-based finding to deduct
        // from either.
        val noScoredEvidence = categories.filter { it.category in SCORED_CATEGORIES }
            .all { it.state == CategoryState.UNAVAILABLE }
        val noGenuineEvidence = noScoredEvidence && findings.isEmpty()

        // A verified defect, fixed here: this used to start at 100 and only deduct for
        // findings, so a near-empty scan (almost nothing readable, hence no findings to
        // deduct from) showed a misleading 100/100 — "good health" with zero evidence of
        // health, not evidence of zero problems. Null means "we genuinely can't score this",
        // distinct from a real, computed number.
        val score: Int? = if (noGenuineEvidence) {
            null
        } else {
            var s = 100
            for (finding in findings) {
                s -= deductionFor(finding.severity)
            }
            max(0, s)
        }

        val label = when {
            categories.any { it.state == CategoryState.ATTENTION } -> "Your car needs attention soon"
            categories.any { it.state == CategoryState.WATCH } -> "A few things worth watching"
            noGenuineEvidence -> "Not enough data from this vehicle yet"
            else -> "Your car is in good health"
        }

        return HealthScoreResult(score, label, categories, fuelInsight)
    }

    private fun deductionFor(severity: Severity): Int = when (severity) {
        Severity.CRITICAL -> 30
        Severity.MODERATE -> 15
        Severity.MINOR -> 5
        Severity.INFO -> 0
    }

    private fun categoryHealth(
        category: HealthCategory,
        snapshot: VehicleSnapshot,
        findings: List<DiagnosticFinding>,
        fuelInsight: FuelCombustionInsight
    ): CategoryHealth {
        val categoryFindings = findings.filter { category in categoriesOf(it) }
        val worst = categoryFindings.minByOrNull { severityRank(it.severity) }?.severity

        val relevantPids = when (category) {
            HealthCategory.ENGINE -> ENGINE_PIDS
            HealthCategory.COOLING -> COOLING_PIDS
            HealthCategory.FUEL_COMBUSTION -> FUEL_COMBUSTION_PIDS
            HealthCategory.BATTERY -> BATTERY_PIDS
            HealthCategory.EMISSIONS, HealthCategory.DIAGNOSTICS -> emptySet()
        }

        val relevantReadings = relevantPids.mapNotNull { snapshot.readings[it] }
        val hasCapability = relevantPids.isEmpty() || relevantReadings.any { it.status != PidStatus.UNSUPPORTED }
        val hasImplausibleReading = relevantReadings.any { it.status == PidStatus.IMPLAUSIBLE }

        if (!hasCapability && categoryFindings.isEmpty()) {
            return CategoryHealth(category, CategoryState.UNAVAILABLE, "This vehicle doesn't report the data this needs.", score = null)
        }

        if (category == HealthCategory.FUEL_COMBUSTION) {
            return CategoryHealth(category, fuelInsight.state, fuelInsight.interpretation, fuelInsight.score)
        }

        val scored = category in SCORED_CATEGORIES
        val categoryScore = if (scored) max(0, 100 - categoryFindings.sumOf { deductionFor(it.severity) } - (if (hasImplausibleReading && categoryFindings.isEmpty()) 10 else 0)) else null

        return when {
            worst == Severity.CRITICAL ->
                CategoryHealth(category, CategoryState.ATTENTION, categoryFindings.first { it.severity == worst }.title, categoryScore)
            worst == Severity.MODERATE || worst == Severity.MINOR ->
                CategoryHealth(category, CategoryState.WATCH, categoryFindings.first { it.severity == worst }.title, categoryScore)
            hasImplausibleReading ->
                CategoryHealth(category, CategoryState.WATCH, "A reading looked unreliable on this scan.", categoryScore)
            else ->
                CategoryHealth(category, CategoryState.GOOD, "Nothing found here.", categoryScore)
        }
    }

    /**
     * Which health categories a finding counts against — a set, not one category, since
     * Engine/Cooling/Fuel & Combustion deliberately share evidence PIDs (see class doc).
     * DTC-based findings still resolve to exactly one category via their SAE J2012 subsystem
     * digit, which is unambiguous by design.
     */
    /** Public so Premium's Detailed Repair Diagnosis can filter findings per category the same way the score does — one source of truth for category membership, not two that can disagree. */
    fun categoriesOf(finding: DiagnosticFinding): Set<HealthCategory> {
        if (finding.relatedPids.any { it in BATTERY_PIDS }) return setOf(HealthCategory.BATTERY)

        if (finding.relatedPids.isNotEmpty()) {
            val matched = buildSet {
                if (finding.relatedPids.any { it in COOLING_PIDS }) add(HealthCategory.COOLING)
                if (finding.relatedPids.any { it in FUEL_COMBUSTION_PIDS }) add(HealthCategory.FUEL_COMBUSTION)
                if (finding.relatedPids.any { it in ENGINE_PIDS }) add(HealthCategory.ENGINE)
            }
            if (matched.isNotEmpty()) return matched
        }

        finding.relatedDtcCodes.firstOrNull()?.let { code -> categoryFromDtcCode(code)?.let { return setOf(it) } }

        val text = (finding.title + " " + finding.plainLanguageSummary).lowercase()
        return setOf(
            when {
                "coolant" in text || "overheat" in text || "thermostat" in text -> HealthCategory.COOLING
                "voltage" in text || "battery" in text -> HealthCategory.BATTERY
                "fuel trim" in text || "lean" in text || "rich" in text -> HealthCategory.FUEL_COMBUSTION
                "misfir" in text || "knock" in text || "throttle" in text -> HealthCategory.ENGINE
                "catalyst" in text || "catalytic" in text || "evap" in text || "oxygen" in text || "o2 sensor" in text -> HealthCategory.EMISSIONS
                else -> HealthCategory.DIAGNOSTICS
            }
        )
    }

    /**
     * SAE J2012: for a code like "P0301", the character after the letter is 0 (generic) or
     * 1 (manufacturer-specific); the next character is the subsystem — 1/2 fuel & air
     * metering (FUEL_COMBUSTION), 3 ignition/misfire and 0/5 fuel-system-adjacent/idle
     * control (ENGINE), 4 auxiliary emission controls (EMISSIONS), 6+ computer/transmission
     * (not covered by our six categories, so DIAGNOSTICS).
     */
    private fun categoryFromDtcCode(code: String): HealthCategory? {
        if (code.isEmpty() || code[0].uppercaseChar() != 'P') return null
        val digits = code.drop(1).takeWhile { it.isDigit() }
        if (digits.length < 2) return null
        return when (digits[1]) {
            '1', '2' -> HealthCategory.FUEL_COMBUSTION
            '0', '3', '5' -> HealthCategory.ENGINE
            '4' -> HealthCategory.EMISSIONS
            else -> HealthCategory.DIAGNOSTICS
        }
    }

    private fun severityRank(severity: Severity): Int = when (severity) {
        Severity.CRITICAL -> 0
        Severity.MODERATE -> 1
        Severity.MINOR -> 2
        Severity.INFO -> 3
    }
}
