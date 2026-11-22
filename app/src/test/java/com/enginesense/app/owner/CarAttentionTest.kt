package com.enginesense.app.owner

import com.enginesense.app.vehiclelookup.ValidityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CarAttentionTest {

    private val today = LocalDate.of(2026, 11, 22)

    @Test
    fun nowComesBeforeComingUpAndWorstFirst() {
        val items = CarAttention.items(
            AttentionInputs(
                scanned = true,
                pendingCodes = 1,
                storedCodes = 2,
                nextService = NextService(20_000, 900, today.plusDays(40), 40),
                renewals = listOf(RenewalItem(RenewalKind.INSURANCE, today.plusDays(10), "", ValidityStatus.EXPIRES_SOON, 10, false))
            )
        )
        assertEquals(listOf("2 fault codes stored", "1 pending fault code", "Service due in ~900 km", "Insurance renews 2 Dec"), items.map { it.title })
        assertTrue(items[0].now)
        assertFalse(items[2].now)
    }

    @Test
    fun aHealthyFirstScanSaysAllGood() {
        val inputs = AttentionInputs(scanned = true, checks = 23)
        assertTrue(CarAttention.items(inputs).isEmpty())
        assertEquals("All good · 23 checks on the last scan", CarAttention.allGoodLine(inputs))
        assertNull(CarAttention.allGoodLine(AttentionInputs(scanned = false)))
    }

    @Test
    fun aMileageDropAndExpiredRenewalAreNow() {
        val items = CarAttention.items(
            AttentionInputs(
                scanned = true,
                mileageDrop = MileageDrop(14, 0, 12.0, 14.0, listOf("More idling than usual"), 1200),
                renewals = listOf(RenewalItem(RenewalKind.PUC, today.minusDays(3), "Expired", ValidityStatus.EXPIRED, -3, false))
            )
        )
        assertEquals("PUC has expired", items.first().title)
        val drop = items.first { it.title.startsWith("Mileage") }
        assertEquals("More idling than usual · ≈₹1,200 a month", drop.detail)
        assertTrue(drop.now)
    }

    @Test
    fun learningTicksOffAndThenGoes() {
        val day1 = CarAttention.learning(scans = 1, tripsWithFuel = 0, driveDays = 0, tanksCompared = 0, firstSeen = today, today = today)!!
        assertEquals(2, day1.doneCount)
        assertEquals(6, day1.steps.size)
        assertNull(CarAttention.learning(3, 5, 20, 1, today, today))
        assertNull("gone after two months even if a step can't finish", CarAttention.learning(1, 0, 0, 0, today.minusDays(61), today))
    }

    @Test
    fun staleAfterThreeWeeks() {
        assertNull(CarAttention.staleLine(today.minusDays(10), today))
        assertNotNull(CarAttention.staleLine(today.minusDays(22), today))
    }
}
