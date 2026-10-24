package com.enginesense.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelCombustionAnalyzerTest {

    private fun snapshot(readings: Map<PidId, PidReading> = emptyMap()) = VehicleSnapshot(0L, null, readings, emptyList())
    private fun verified(pid: PidId, value: Double) = pid to PidReading(pid, PidStatus.VERIFIED, value, null)

    @Test
    fun `no trim data at all produces an honest no-data interpretation and a coverage note`() {
        val insight = FuelCombustionAnalyzer.analyze(snapshot(), emptyList())
        assertTrue(insight.interpretation.contains("No fuel trim data"))
        assertTrue(insight.dataCoverageNote!!.contains("short-term fuel trim"))
        assertTrue(insight.dataCoverageNote!!.contains("long-term fuel trim"))
    }

    @Test
    fun `a formal finding's own explanation wins over the templated sentence`() {
        val finding = DiagnosticFinding(
            id = "F1", title = "Lean condition", plainLanguageSummary = "Custom explanation from the rules engine.",
            severity = com.enginesense.app.obd.Severity.MODERATE, recommendedAction = "Get checked."
        )
        val insight = FuelCombustionAnalyzer.analyze(snapshot(mapOf(verified(PidId.LONG_TERM_FUEL_TRIM_1, 15.0))), listOf(finding))
        assertEquals("Custom explanation from the rules engine.", insight.interpretation)
    }

    @Test
    fun `mild positive long-term trim with steady short-term trim reads as worth monitoring, not alarming`() {
        val readings = mapOf(verified(PidId.LONG_TERM_FUEL_TRIM_1, 7.8), verified(PidId.SHORT_TERM_FUEL_TRIM_1, -3.9))
        val insight = FuelCombustionAnalyzer.analyze(snapshot(readings), emptyList())

        assertTrue(insight.interpretation.contains("adding a bit more fuel"))
        assertTrue(insight.interpretation.contains("steady"))
        assertEquals("Long-term fuel trim", insight.primarySignal?.label)
        assertEquals("+7.8%", insight.primarySignal?.value)
    }

    @Test
    fun `trims well within normal reads as nothing concerning`() {
        val readings = mapOf(verified(PidId.LONG_TERM_FUEL_TRIM_1, 1.0), verified(PidId.SHORT_TERM_FUEL_TRIM_1, -1.0))
        val insight = FuelCombustionAnalyzer.analyze(snapshot(readings), emptyList())
        assertTrue(insight.interpretation.contains("Nothing here points to a fuel mixture problem"))
        assertEquals(CategoryState.GOOD, insight.state)
    }

    @Test
    fun `fuel level is never read by the analyzer`() {
        val readings = mapOf(verified(PidId.SHORT_TERM_FUEL_TRIM_1, 0.0), verified(PidId.FUEL_LEVEL, 5.0))
        val insight = FuelCombustionAnalyzer.analyze(snapshot(readings), emptyList())
        assertTrue(insight.supportingSignals.none { it.label.contains("Fuel level", ignoreCase = true) })
    }

    @Test
    fun `complete coverage note is null when both trims are present`() {
        val readings = mapOf(verified(PidId.LONG_TERM_FUEL_TRIM_1, 0.0), verified(PidId.SHORT_TERM_FUEL_TRIM_1, 0.0))
        val insight = FuelCombustionAnalyzer.analyze(snapshot(readings), emptyList())
        assertNull(insight.dataCoverageNote)
    }
}
