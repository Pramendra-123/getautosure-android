package com.enginesense.app.owner

import com.enginesense.app.vehiclelookup.ValidityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FirstCheckupTest {

    private val today = LocalDate.of(2026, 11, 25)
    private val goodBattery = BatteryHealth(Level.GOOD, "Good", listOf("12.6 V parked, charging at 14.1 V."))

    private fun renewal(kind: RenewalKind, days: Long, status: ValidityStatus = ValidityStatus.VALID) =
        RenewalItem(kind, today.plusDays(days), "", status, days, fromRto = true)

    @Test
    fun aHealthyNewCarIsNeverAnEmptyCard() {
        val lines = FirstCheckup.lines(
            CheckupInputs(
                AttentionInputs(scanned = true, battery = goodBattery, renewals = listOf(renewal(RenewalKind.INSURANCE, 200), renewal(RenewalKind.PUC, 90))),
                kmSinceCleared = 4_000.0, financed = false
            )
        )
        assertEquals(listOf("Engine: no faults", "Battery: good", "Papers in order", "When was the last service?"), lines.map { it.title })
        assertTrue(lines[2].detail!!.contains("Insurance till 13 Jun 2027"))
        assertTrue(lines[2].detail!!.contains("No loan on the RC."))
        assertFalse(FirstCheckup.hasProblems(lines))
    }

    @Test
    fun aSecondHandCarLeadsWithWhatTheSellerMayNotHaveSaid() {
        val lines = FirstCheckup.lines(
            CheckupInputs(
                AttentionInputs(
                    scanned = true, pendingCodes = 1, battery = goodBattery,
                    renewals = listOf(renewal(RenewalKind.PUC, -48, ValidityStatus.EXPIRED), renewal(RenewalKind.INSURANCE, 200))
                ),
                kmSinceCleared = 46.0, warmUpsSinceCleared = 3.0, ownerCount = 2
            )
        )
        // Problems first, then watch, then good news, then the question.
        assertEquals("PUC has expired", lines[0].title)
        assertEquals(CheckupKind.WATCH, lines[1].kind)
        assertTrue(lines.any { it.title == "Warning codes were cleared recently" && it.detail!!.startsWith("Only 46 km and 3 warm-ups since.") })
        assertTrue(lines.any { it.title == "1 fault caught early" })
        assertFalse("pending faults don't also say 'no faults'", lines.any { it.title == "Engine: no faults" })
        assertEquals("Check the service book from the seller.", lines.last().detail)
        assertTrue(FirstCheckup.hasProblems(lines))
        assertEquals("Today · 38 checks on the engine, battery and emissions · 2nd owner per RTO", FirstCheckup.subtitle(38, 2))
    }

    @Test
    fun noPaperDatesAsksForThemAndAnsweredServiceIsNotAskedAgain() {
        val lines = FirstCheckup.lines(CheckupInputs(AttentionInputs(scanned = true), serviceAnswered = true))
        assertEquals(listOf("Engine: no faults", "Add your insurance and PUC dates"), lines.map { it.title })
    }
}
