package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRulesEngineTest {

    private fun snapshot(
        readings: Map<PidId, PidReading> = emptyMap(),
        dtcs: List<DtcReading> = emptyList()
    ) = VehicleSnapshot(
        timestampMillis = 0L,
        vehicleBrand = null,
        readings = readings,
        dtcs = dtcs
    )

    @Test
    fun `no findings for a clean snapshot`() {
        val findings = DiagnosticRulesEngine.evaluate(snapshot(), emptyMap())
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `overheating coolant produces a critical live finding even with no DTC`() {
        val readings = mapOf(
            PidId.COOLANT_TEMP_C to PidReading(PidId.COOLANT_TEMP_C, PidStatus.VERIFIED, 118.0, null)
        )
        val findings = DiagnosticRulesEngine.evaluate(snapshot(readings = readings), emptyMap())

        assertEquals(1, findings.size)
        assertEquals("LIVE_OVERHEATING", findings[0].id)
        assertEquals(Severity.CRITICAL, findings[0].severity)
    }

    @Test
    fun `low voltage produces a moderate finding`() {
        val readings = mapOf(
            PidId.CONTROL_MODULE_VOLTAGE to PidReading(PidId.CONTROL_MODULE_VOLTAGE, PidStatus.VERIFIED, 10.9, null)
        )
        val findings = DiagnosticRulesEngine.evaluate(snapshot(readings = readings), emptyMap())

        assertEquals(1, findings.size)
        assertEquals("LIVE_LOW_VOLTAGE", findings[0].id)
        assertEquals(Severity.MODERATE, findings[0].severity)
    }

    @Test
    fun `implausible reading does not trigger a live threshold finding`() {
        // IMPLAUSIBLE readings are excluded by verifiedValue(), so a bad sensor glitch
        // can't masquerade as a real overheating event.
        val readings = mapOf(
            PidId.COOLANT_TEMP_C to PidReading(PidId.COOLANT_TEMP_C, PidStatus.IMPLAUSIBLE, 999.0, null)
        )
        val findings = DiagnosticRulesEngine.evaluate(snapshot(readings = readings), emptyMap())
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `DTC finding reuses the caller-supplied explanation rather than inventing one`() {
        val dtcs = listOf(DtcReading("P0301", DtcSource.STORED))
        val explanations = mapOf(
            "P0301" to DtcExplanationInput("Cylinder 1 is misfiring.", Severity.CRITICAL)
        )
        val findings = DiagnosticRulesEngine.evaluate(snapshot(dtcs = dtcs), explanations)

        assertEquals(1, findings.size)
        assertEquals("DTC_P0301", findings[0].id)
        assertEquals("Cylinder 1 is misfiring.", findings[0].plainLanguageSummary)
        assertEquals(Severity.CRITICAL, findings[0].severity)
    }

    @Test
    fun `findings are sorted critical first`() {
        val readings = mapOf(
            PidId.COOLANT_TEMP_C to PidReading(PidId.COOLANT_TEMP_C, PidStatus.VERIFIED, 120.0, null),
            PidId.CONTROL_MODULE_VOLTAGE to PidReading(PidId.CONTROL_MODULE_VOLTAGE, PidStatus.VERIFIED, 10.5, null)
        )
        val findings = DiagnosticRulesEngine.evaluate(snapshot(readings = readings), emptyMap())

        assertEquals(2, findings.size)
        assertEquals(Severity.CRITICAL, findings[0].severity)
        assertEquals(Severity.MODERATE, findings[1].severity)
    }
}
