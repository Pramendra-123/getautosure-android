package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity
import com.enginesense.app.storage.PersistedTripMileage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MileageTrendAnalyzerTest {

    private fun trip(kmPerLitre: Double) = PersistedTripMileage(0L, 10.0, 10.0 / kmPerLitre, kmPerLitre)

    private fun misfireFinding() = DiagnosticFinding(
        id = "f1", title = "Cylinder 1 misfire", plainLanguageSummary = "Cylinder 1 is misfiring.",
        severity = Severity.CRITICAL, relatedDtcCodes = listOf("P0301"), recommendedAction = "Get it checked."
    )

    // --- analyze(): needs a real personal baseline, never a flat/external assumption ---

    @Test
    fun `fewer than 2 prior trips means no baseline and never a dropped verdict`() {
        val result = MileageTrendAnalyzer.analyze(15.0, listOf(trip(16.0)))
        assertNull(result.baselineKmPerLitre)
        assertFalse(result.isDropSignificant)
    }

    @Test
    fun `a mileage in line with the personal baseline is not flagged as a drop`() {
        val result = MileageTrendAnalyzer.analyze(15.8, listOf(trip(16.0), trip(15.5), trip(16.2)))
        assertFalse(result.isDropSignificant)
    }

    @Test
    fun `a real drop against the personal baseline is flagged`() {
        val result = MileageTrendAnalyzer.analyze(12.0, listOf(trip(16.0), trip(16.0), trip(16.0)))
        assertTrue(result.isDropSignificant)
    }

    // --- currentScanSignal(): no trip history needed at all, works on trip 1 ---

    @Test
    fun `an active misfire is surfaced as a signal even with zero trip history`() {
        val signal = MileageTrendAnalyzer.currentScanSignal(CategoryState.GOOD, "nothing notable", listOf(misfireFinding()))
        assertTrue(signal != null && signal.contains("misfire", ignoreCase = true))
    }

    @Test
    fun `elevated fuel trim alone is surfaced when the category state is watch or attention`() {
        val signal = MileageTrendAnalyzer.currentScanSignal(CategoryState.WATCH, "Long-term fuel trim is reading +13%.", emptyList())
        assertTrue(signal != null && signal.contains("fuel trim", ignoreCase = true))
    }

    @Test
    fun `a genuinely clean scan returns null, never a manufactured concern`() {
        val signal = MileageTrendAnalyzer.currentScanSignal(CategoryState.GOOD, "Nothing here points to a fuel mixture problem.", emptyList())
        assertNull(signal)
    }
}
