package com.enginesense.app.vehicle.ai

import com.enginesense.app.obd.Severity
import com.enginesense.app.vehicle.DiagnosticFinding
import com.enginesense.app.vehicle.DtcReading
import com.enginesense.app.vehicle.DtcSource
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.PidReading
import com.enginesense.app.vehicle.PidStatus
import com.enginesense.app.vehicle.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class AiVehicleContextTest {

    @Test
    fun `from carries every reading status through untouched`() {
        val snapshot = VehicleSnapshot(
            timestampMillis = 42L,
            vehicleBrand = "Tata",
            readings = mapOf(
                PidId.RPM to PidReading(PidId.RPM, PidStatus.VERIFIED, 1500.0, null),
                PidId.CONTROL_MODULE_VOLTAGE to PidReading(PidId.CONTROL_MODULE_VOLTAGE, PidStatus.UNSUPPORTED, null, null)
            ),
            dtcs = listOf(DtcReading("P0301", DtcSource.STORED))
        )
        val findings = listOf(
            DiagnosticFinding(
                id = "DTC_P0301",
                title = "Code P0301",
                plainLanguageSummary = "Cylinder 1 misfire.",
                severity = Severity.CRITICAL,
                relatedDtcCodes = listOf("P0301"),
                recommendedAction = "Stop driving and get this checked."
            )
        )

        val context = AiVehicleContext.from(snapshot, findings)

        assertEquals(42L, context.generatedAtMillis)
        assertEquals("Tata", context.vehicleBrand)
        assertEquals(2, context.signals.size)
        assertEquals("UNSUPPORTED", context.signals.first { it.name == PidId.CONTROL_MODULE_VOLTAGE.displayName }.status)
        assertEquals(1, context.findings.size)
        assertEquals("CRITICAL", context.findings[0].severity)
        assertEquals(listOf("P0301"), context.findings[0].relatedDtcCodes)
    }
}
