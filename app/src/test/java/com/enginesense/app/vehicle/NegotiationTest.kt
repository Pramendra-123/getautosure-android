package com.enginesense.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NegotiationTest {

    private val tyres = NegotiationReason("Two front tyres worn", "Look-around", 12_000.0, 16_000.0)
    private val bumper = NegotiationReason("Rear bumper", "Look-around", 4_000.0, 10_000.0)

    @Test fun `the three prices come from the market and the repairs`() {
        // Market ₹10.5–12.2 L (middle 11.35 L), repairs ₹16–26k.
        val p = NegotiationPlanner.plan(1_050_000.0, 1_220_000.0, listOf(tyres, bumper), askingInr = 1_150_000.0)
        assertEquals(1_025_000.0, p.openWith, 0.0)     // 10.50 L − 26k = 10.24 L → 10.25 L
        assertEquals(1_115_000.0, p.fairDeal, 0.0)     // 11.35 L − 21k = 11.14 L → 11.15 L
        assertEquals(1_120_000.0, p.walkAwayOver, 0.0) // 11.35 L − 16k = 11.19 L → 11.20 L
        assertEquals(35_000.0, p.askOffInr!!, 0.0)     // 11.50 L − 11.15 L
    }

    @Test fun `asking above the market adds a reason and the ask is asking minus the fair deal`() {
        val p = NegotiationPlanner.plan(1_050_000.0, 1_220_000.0, listOf(tyres), askingInr = 1_150_000.0)
        assertTrue(p.reasons.any { it.title == "Priced above the market" && it.minInr == 15_000.0 })
        assertEquals(p.askingInr!! - p.fairDeal, p.askOffInr!!, 0.0)
    }

    @Test fun `a fair asking price means nothing to ask off, and no asking price means no ask`() {
        assertEquals(0.0, NegotiationPlanner.plan(1_050_000.0, 1_220_000.0, emptyList(), 1_000_000.0).askOffInr!!, 0.0)
        assertNull(NegotiationPlanner.plan(1_050_000.0, 1_220_000.0, emptyList(), null).askOffInr)
    }

    @Test fun `prices are rounded to 5,000 and stay in order`() {
        val p = NegotiationPlanner.plan(1_050_000.0, 1_220_000.0, listOf(tyres, bumper), null)
        listOf(p.openWith, p.fairDeal, p.walkAwayOver).forEach { assertEquals(0.0, it % 5_000, 0.0) }
        assertTrue(p.openWith <= p.fairDeal && p.fairDeal <= p.walkAwayOver)
    }
}
