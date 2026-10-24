package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthScoreCalculatorTest {

    private fun snapshot(readings: Map<PidId, PidReading> = emptyMap(), dtcs: List<DtcReading> = emptyList()) =
        VehicleSnapshot(0L, null, readings, dtcs)

    private fun verified(pid: PidId, value: Double) = pid to PidReading(pid, PidStatus.VERIFIED, value, null)
    private fun unsupported(pid: PidId) = pid to PidReading(pid, PidStatus.UNSUPPORTED, null, null)

    @Test
    fun `fully healthy snapshot scores 100 and every category is good`() {
        val readings = mapOf(
            verified(PidId.RPM, 1726.0),
            verified(PidId.COOLANT_TEMP_C, 90.0),
            verified(PidId.CONTROL_MODULE_VOLTAGE, 14.2),
            verified(PidId.SHORT_TERM_FUEL_TRIM_1, 0.0)
        )
        val result = HealthScoreCalculator.calculate(snapshot(readings = readings), emptyList())

        assertEquals(100, result.score)
        assertTrue(result.categories.all { it.state == CategoryState.GOOD })
    }

    @Test
    fun `scored categories report a 0-100 number, label-only categories do not`() {
        val readings = mapOf(
            verified(PidId.RPM, 1726.0),
            verified(PidId.COOLANT_TEMP_C, 90.0),
            verified(PidId.CONTROL_MODULE_VOLTAGE, 14.2),
            verified(PidId.SHORT_TERM_FUEL_TRIM_1, 0.0)
        )
        val result = HealthScoreCalculator.calculate(snapshot(readings = readings), emptyList())

        assertEquals(100, result.categories.first { it.category == HealthCategory.ENGINE }.score)
        assertEquals(100, result.categories.first { it.category == HealthCategory.COOLING }.score)
        assertEquals(100, result.categories.first { it.category == HealthCategory.FUEL_COMBUSTION }.score)
        assertEquals(null, result.categories.first { it.category == HealthCategory.BATTERY }.score)
        assertEquals(null, result.categories.first { it.category == HealthCategory.EMISSIONS }.score)
        assertEquals(null, result.categories.first { it.category == HealthCategory.DIAGNOSTICS }.score)
    }

    @Test
    fun `a near-empty scan with no readings at all does not score 100 or claim good health`() {
        // Verified defect: this used to start the score at 100 and only deduct for findings,
        // so a scan with nothing readable (hence no findings to deduct from) showed a
        // misleading 100/100 "good health" — zero evidence of health, not evidence of a
        // healthy car. Score must be null, and the label must say so, not claim "good".
        val result = HealthScoreCalculator.calculate(snapshot(), emptyList())

        assertEquals(null, result.score)
        assertEquals("Not enough data from this vehicle yet", result.label)
        // Only the 3 PID-scored categories go UNAVAILABLE here — Emissions/Diagnostics have
        // no PID-capability gate (they're purely finding-driven) so they report GOOD
        // ("nothing found") instead, which is a separate, pre-existing characteristic of
        // categoryHealth(), not something this fix changes.
        assertTrue(
            result.categories.filter { it.category in setOf(HealthCategory.ENGINE, HealthCategory.COOLING, HealthCategory.FUEL_COMBUSTION) }
                .all { it.state == CategoryState.UNAVAILABLE }
        )
    }

    @Test
    fun `a DTC-based finding with zero PID data still produces a real deducted score, not null`() {
        // No PID readings at all, but there IS a real finding (e.g. from a stored DTC) —
        // that's genuine evidence, so a real deducted score should show, not "no data".
        val findings = listOf(
            DiagnosticFinding(
                id = "f1", title = "Emissions fault", plainLanguageSummary = "s",
                severity = Severity.MODERATE, recommendedAction = "a"
            )
        )
        val result = HealthScoreCalculator.calculate(snapshot(), findings)

        assertTrue(result.score != null)
        assertTrue(result.score!! < 100)
    }

    @Test
    fun `partial data in only one scored category still produces a real score, not null`() {
        // Only engine-domain PIDs present — cooling/fuel have no data, but engine does,
        // so there IS genuine evidence and a real score should still be shown.
        val readings = mapOf(verified(PidId.RPM, 1500.0), verified(PidId.ENGINE_LOAD, 30.0))
        val result = HealthScoreCalculator.calculate(snapshot(readings = readings), emptyList())

        assertTrue(result.score != null)
    }

    @Test
    fun `a fuel-air metering DTC like P0171 is categorized as FUEL_COMBUSTION, not ENGINE`() {
        val findings = listOf(
            DiagnosticFinding(
                id = "DTC_P0171",
                title = "Code P0171",
                plainLanguageSummary = "System too lean.",
                severity = Severity.MODERATE,
                relatedDtcCodes = listOf("P0171"),
                recommendedAction = "Get this checked soon."
            )
        )
        val readings = mapOf(verified(PidId.SHORT_TERM_FUEL_TRIM_1, 0.0), verified(PidId.RPM, 1500.0))
        val result = HealthScoreCalculator.calculate(snapshot(readings = readings), findings)

        val fuel = result.categories.first { it.category == HealthCategory.FUEL_COMBUSTION }
        assertEquals(CategoryState.WATCH, fuel.state)
        // Blended score: fuel trim itself is within the normal ±10% band (subscore 100,
        // weight 40%) but the P0171 finding still costs its MODERATE deduction in the
        // finding-based 60% (subscore 85) -> 0.4*100 + 0.6*85 = 91.
        assertEquals(91, fuel.score)
        assertEquals(CategoryState.GOOD, result.categories.first { it.category == HealthCategory.ENGINE }.state)
    }

    @Test
    fun `fuel trim magnitude beyond the normal band drags the Fuel & Combustion score down on its own`() {
        // No findings at all here -- this is the trim-magnitude component (40% weight)
        // acting independently of the rules engine, per the product spec: LTFT=15% is past
        // the ±10% normal band (subscore 100-((15-10)/10*100)=50), STFT=2% is irrelevant
        // since LTFT has the larger magnitude. 0.4*50 + 0.6*100(no findings) = 80.
        val readings = mapOf(verified(PidId.LONG_TERM_FUEL_TRIM_1, 15.0), verified(PidId.SHORT_TERM_FUEL_TRIM_1, 2.0))
        val result = HealthScoreCalculator.calculate(snapshot(readings = readings), emptyList())

        val fuel = result.categories.first { it.category == HealthCategory.FUEL_COMBUSTION }
        assertEquals(CategoryState.WATCH, fuel.state)
        assertEquals(80, fuel.score)
    }

    @Test
    fun `fuel level never influences the Fuel & Combustion score`() {
        val healthyTrims = mapOf(verified(PidId.SHORT_TERM_FUEL_TRIM_1, 0.0))
        val lowFuel = mapOf(verified(PidId.SHORT_TERM_FUEL_TRIM_1, 0.0), verified(PidId.FUEL_LEVEL, 5.0))

        val withPlentyOfFuel = HealthScoreCalculator.calculate(snapshot(readings = healthyTrims), emptyList())
        val withLowFuel = HealthScoreCalculator.calculate(snapshot(readings = lowFuel), emptyList())

        assertEquals(
            withPlentyOfFuel.categories.first { it.category == HealthCategory.FUEL_COMBUSTION }.score,
            withLowFuel.categories.first { it.category == HealthCategory.FUEL_COMBUSTION }.score
        )
    }

    @Test
    fun `critical engine finding drops the score and marks engine for attention`() {
        val findings = listOf(
            DiagnosticFinding(
                id = "DTC_P0301",
                title = "Code P0301",
                plainLanguageSummary = "Cylinder 1 is misfiring.",
                severity = Severity.CRITICAL,
                relatedDtcCodes = listOf("P0301"),
                recommendedAction = "Stop driving soon."
            )
        )
        val result = HealthScoreCalculator.calculate(snapshot(), findings)

        assertEquals(70, result.score)
        val engine = result.categories.first { it.category == HealthCategory.ENGINE }
        assertEquals(CategoryState.ATTENTION, engine.state)
    }

    @Test
    fun `overheating live finding marks cooling for attention via its related PID`() {
        val findings = listOf(
            DiagnosticFinding(
                id = "LIVE_OVERHEATING",
                title = "Engine running hot",
                plainLanguageSummary = "Coolant is 118C.",
                severity = Severity.CRITICAL,
                relatedPids = listOf(PidId.COOLANT_TEMP_C),
                recommendedAction = "Stop driving soon."
            )
        )
        val readings = mapOf(verified(PidId.COOLANT_TEMP_C, 118.0))
        val result = HealthScoreCalculator.calculate(snapshot(readings), findings)

        assertEquals(CategoryState.ATTENTION, result.categories.first { it.category == HealthCategory.COOLING }.state)
    }

    @Test
    fun `a PID the vehicle does not support is UNAVAILABLE, never GOOD`() {
        val readings = mapOf(unsupported(PidId.CONTROL_MODULE_VOLTAGE))
        val result = HealthScoreCalculator.calculate(snapshot(readings), emptyList())

        val battery = result.categories.first { it.category == HealthCategory.BATTERY }
        assertEquals(CategoryState.UNAVAILABLE, battery.state)
    }

    @Test
    fun `score never goes below zero`() {
        val manyCritical = (1..10).map {
            DiagnosticFinding(
                id = "F$it",
                title = "Issue $it",
                plainLanguageSummary = "Bad",
                severity = Severity.CRITICAL,
                recommendedAction = "Stop driving."
            )
        }
        val result = HealthScoreCalculator.calculate(snapshot(), manyCritical)
        assertEquals(0, result.score)
    }

    @Test
    fun `a misfire DTC is categorized as ENGINE even though its explanation text mentions the catalytic converter`() {
        // Real DtcRepository text for misfire codes reads "...can cause rough running and,
        // over time, damage the catalytic converter" — a naive keyword scan for "catalytic"
        // would wrongly bucket this under Emissions. The DTC code's own SAE J2012 subsystem
        // digit (the '3' in P0301 means ignition/misfire) is the reliable signal instead.
        val findings = listOf(
            DiagnosticFinding(
                id = "DTC_P0301",
                title = "Code P0301",
                plainLanguageSummary = "One or more engine cylinders aren't firing correctly — this can cause " +
                    "rough running and, over time, damage the catalytic converter.  (P0301)",
                severity = Severity.CRITICAL,
                relatedDtcCodes = listOf("P0301"),
                recommendedAction = "Stop driving soon."
            )
        )
        val result = HealthScoreCalculator.calculate(snapshot(), findings)

        assertEquals(CategoryState.ATTENTION, result.categories.first { it.category == HealthCategory.ENGINE }.state)
        assertEquals(CategoryState.GOOD, result.categories.first { it.category == HealthCategory.EMISSIONS }.state)
    }

    @Test
    fun `a genuine emissions DTC like P0420 is categorized as EMISSIONS via its SAE subsystem digit`() {
        // readAll() always populates every registered PID's status in real usage, so give
        // ENGINE a real verified reading here too — otherwise an absent map entry correctly
        // (and separately) reads as UNAVAILABLE, which isn't what this test is checking.
        val readings = mapOf(verified(PidId.RPM, 1500.0))
        val findings = listOf(
            DiagnosticFinding(
                id = "DTC_P0420",
                title = "Code P0420",
                plainLanguageSummary = "The catalytic converter isn't cleaning exhaust gases as efficiently as it should.  (P0420)",
                severity = Severity.MODERATE,
                relatedDtcCodes = listOf("P0420"),
                recommendedAction = "Get this checked soon."
            )
        )
        val result = HealthScoreCalculator.calculate(snapshot(readings = readings), findings)

        assertEquals(CategoryState.WATCH, result.categories.first { it.category == HealthCategory.EMISSIONS }.state)
        assertEquals(CategoryState.GOOD, result.categories.first { it.category == HealthCategory.ENGINE }.state)
    }

    @Test
    fun `emissions and diagnostics categories are decided from DTCs alone, never UNAVAILABLE`() {
        val result = HealthScoreCalculator.calculate(snapshot(), emptyList())
        assertEquals(CategoryState.GOOD, result.categories.first { it.category == HealthCategory.EMISSIONS }.state)
        assertEquals(CategoryState.GOOD, result.categories.first { it.category == HealthCategory.DIAGNOSTICS }.state)
    }
}
