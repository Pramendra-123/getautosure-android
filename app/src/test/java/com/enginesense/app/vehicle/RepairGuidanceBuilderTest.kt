package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairGuidanceBuilderTest {

    private fun dtcFinding(code: String, severity: Severity = Severity.MODERATE, summary: String = "Plain-English summary.") =
        DiagnosticFinding(
            id = "DTC_$code", title = "Code $code", plainLanguageSummary = summary, severity = severity,
            relatedDtcCodes = listOf(code), recommendedAction = "Get it checked."
        )

    @Test
    fun `a code in the reference library fills every section`() {
        val g = RepairGuidanceBuilder.build(dtcFinding("P0171"), scanDateLabel = "27 Oct 2026")
        assertEquals("P0171", g.code)
        assertEquals("System too lean (Bank 1)", g.title)
        assertTrue(g.hasDetailedReference)
        assertEquals(
            listOf("Vacuum leak", "A dirty/failing MAF sensor", "Weak fuel pump", "A clogged fuel injector"),
            g.possibleCauses
        )
        assertTrue(g.checks!!.contains("vacuum leaks"))
        assertTrue(g.drivingAdvice.contains("catalytic converter"))
        assertTrue(g.mechanicNote.contains("P0171"))
        assertTrue(g.mechanicNote.contains("27 Oct 2026"))
        assertTrue(g.afterRepair.any { it.contains("If P0171 comes back") })
    }

    @Test
    fun `a code outside the library leaves causes and checks empty rather than guessing`() {
        val g = RepairGuidanceBuilder.build(dtcFinding("P1234"))
        assertFalse(g.hasDetailedReference)
        assertNull(g.possibleCauses)
        assertNull(g.checks)
        assertEquals("Code P1234", g.title)
        assertEquals("Usually OK to drive gently for now, but get it checked soon.", g.drivingAdvice)
    }

    @Test
    fun `serious findings get stop signs, minor ones don't`() {
        assertTrue(RepairGuidanceBuilder.build(dtcFinding("P0300", Severity.CRITICAL)).stopSigns.isNotEmpty())
        assertTrue(RepairGuidanceBuilder.build(dtcFinding("P0442", Severity.MINOR)).stopSigns.isEmpty())
    }

    @Test
    fun `every part a cause could point to gets a cost range`() {
        val g = RepairGuidanceBuilder.build(dtcFinding("P0420"))
        val categories = g.costEstimates.map { it.category }
        assertTrue(categories.toString(), "Catalytic converter" in categories)
    }

    @Test
    fun `a live-reading finding uses its own recommended action as the next check`() {
        val live = DiagnosticFinding(
            id = "LIVE_LOW_VOLTAGE", title = "Low battery/charging voltage",
            plainLanguageSummary = "System voltage is 11.2V while the engine is running.",
            severity = Severity.MODERATE, recommendedAction = "Get the battery and alternator tested."
        )
        val g = RepairGuidanceBuilder.build(live)
        assertNull(g.code)
        assertEquals("Get the battery and alternator tested.", g.checks)
        assertTrue(g.costEstimates.any { it.category == "Battery replacement" })
        assertTrue(g.mechanicNote.contains("Low battery/charging voltage"))
    }

    @Test
    fun `causes split cleanly on commas and or`() {
        assertEquals(
            listOf("A stuck-open thermostat", "Genuinely low coolant level"),
            RepairGuidanceBuilder.splitCauses("A stuck-open thermostat, or genuinely low coolant level.")
        )
    }
}
