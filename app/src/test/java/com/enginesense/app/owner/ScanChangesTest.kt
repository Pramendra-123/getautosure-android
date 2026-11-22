package com.enginesense.app.owner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanChangesTest {

    private val day = 86_400_000L
    private val before = ScanFacts(
        timeMillis = 0, score = 100, kmSinceCleared = 3_200.0, minutesSinceCleared = 9_000.0, readyMonitors = 5,
        odometerKm = 40_000.0, cvns = listOf("1A2B3C4D"), calibrationId = "CAL1", catalystPassed = true,
        misfireCylinders = emptySet(), volts = 14.1
    )

    @Test
    fun nothingChangedListsWhatStayedTheSame() {
        val after = before.copy(timeMillis = 10 * day, kmSinceCleared = 3_620.0, minutesSinceCleared = 9_500.0, odometerKm = 40_420.0)
        val c = ScanChanges.compare(before, after, O2Check(0, 6, 1), O2Check(5 * day, 7, 1), 140.0)
        assertTrue(c.changes.isEmpty())
        assertEquals(420.0, c.kmSince!!, 0.01)
        assertEquals(
            listOf("Battery and charging · 14.1 V", "Catalytic converter working", "No codes cleared", "ECU software the same", "No new fault codes", "No misfires"),
            c.same
        )
    }

    @Test
    fun aRemovedConverterIsCaughtFromTheSensorsSelfTestAndCode() {
        val after = before.copy(timeMillis = 10 * day, catalystPassed = false, pendingCodes = setOf("P0420"), kmSinceCleared = 3_500.0, minutesSinceCleared = 9_400.0)
        val c = ScanChanges.compare(before, after, O2Check(0, 6, 1), O2Check(5 * day, 6, 6), null)
        val cat = c.changes.single()
        assertEquals(ChangeKind.CONVERTER, cat.kind)
        assertEquals(Level.ATTENTION, cat.level)
        assertEquals(
            listOf(Triple("Sensor after the converter", "Steady", "Swinging"), Triple("Converter self-test", "Passed", "Failed"), Triple("Code P0420", "None", "Pending")),
            cat.evidence
        )
    }

    @Test
    fun clearedCodesAreNamedWithWhenAndWhatWentNotReady() {
        val withCode = before.copy(storedCodes = setOf("P0301"))
        val after = before.copy(timeMillis = 10 * day, kmSinceCleared = 35.0, minutesSinceCleared = 2_880.0, readyMonitors = 2)
        val change = ScanChanges.compare(withCode, after, null, null, null).changes.single()
        assertEquals("Fault codes were cleared", change.title)
        assertEquals("About 2 days ago, 35 km back. 3 self-tests not ready again", change.detail)
        val noCodesBefore = ScanChanges.compare(before, after, null, null, null).changes.single()
        assertEquals("The car's fault memory was reset", noCodesBefore.title)
    }

    @Test
    fun aDifferentCvnMeansTheEcuSoftwareChanged() {
        val after = before.copy(timeMillis = 10 * day, kmSinceCleared = 3_300.0, minutesSinceCleared = 9_100.0, cvns = listOf("DEADBEEF"))
        val change = ScanChanges.compare(before, after, null, null, null).changes.single()
        assertEquals(ChangeKind.ECU_CHANGED, change.kind)
        assertEquals(Triple("Software fingerprint (CVN)", "1A2B3C4D", "DEADBEEF"), change.evidence.single())
    }

    @Test
    fun newCodesAndMisfiresWorstFirst() {
        val after = before.copy(timeMillis = 10 * day, kmSinceCleared = 3_300.0, minutesSinceCleared = 9_100.0, storedCodes = setOf("P0171"), misfireCylinders = setOf(2))
        val c = ScanChanges.compare(before, after, null, null, null)
        assertEquals(listOf(ChangeKind.NEW_CODES, ChangeKind.MISFIRE), c.changes.map { it.kind })
        assertEquals("Misfires on cylinder 2", c.changes[1].title)
    }

    @Test
    fun aCarThatReportsLittleClaimsLittle() {
        val bare = ScanFacts(timeMillis = 0)
        val c = ScanChanges.compare(bare, bare.copy(timeMillis = day), null, null, null)
        assertTrue(c.changes.isEmpty())
        assertNull(c.kmSince)
        assertEquals(listOf("No new fault codes"), c.same)
    }

    @Test
    fun oxygenSensorSwitchesAreCountedAndStored() {
        assertEquals(3, O2Check.switches(listOf(0.2, 0.7, 0.1, 0.8, 0.8), 0.45))
        val check = O2Check(5L, 6, 5)
        assertTrue(check.rearFollowsFront)
        assertEquals(check, O2Check.fromEvent(5L, check.toEventDetail()))
    }
}
