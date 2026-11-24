package com.enginesense.app.vehicle.pricing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for org.json, which is only a stub in plain JVM tests.
@RunWith(RobolectricTestRunner::class)
class MarketPriceChooserTest {

    private fun ai(low: Double, high: Double) = AiPriceEstimate(low, high, null, "medium", listOf("step"), emptyList(), emptyList(), null)

    @Test fun `AI price is used when it is close to the formula`() {
        val c = MarketPriceChooser.choose(ai(900_000.0, 980_000.0), 820_000.0, 960_000.0)!!
        assertEquals(PriceBasis.AI_MARKET, c.basis)
        assertEquals(900_000.0, c.low, 0.0)
        assertNull(c.note)
    }

    @Test fun `formula is used, with a note, when the AI is far off`() {
        val c = MarketPriceChooser.choose(ai(1_800_000.0, 2_000_000.0), 820_000.0, 960_000.0)!!
        assertEquals(PriceBasis.FORMULA, c.basis)
        assertEquals(820_000.0, c.low, 0.0)
        assertNotNull(c.note)
    }

    @Test fun `formula is used when there is no AI answer`() {
        assertEquals(PriceBasis.FORMULA, MarketPriceChooser.choose(null, 820_000.0, 960_000.0)!!.basis)
    }

    @Test fun `AI alone prices a car missing from our price list`() {
        assertEquals(PriceBasis.AI_MARKET, MarketPriceChooser.choose(ai(500_000.0, 560_000.0), null, null)!!.basis)
    }

    @Test fun `no price at all when neither is available`() {
        assertNull(MarketPriceChooser.choose(null, null, null))
    }

    @Test fun `answer is read from the function's JSON`() {
        val e = RemotePriceEstimator.parse(
            """{"lowInr":900000,"highInr":980000,"newPriceInr":0,"confidence":"high","steps":["a","b"],
               "comparables":[{"description":"2019 Creta SX diesel, 62k km","priceInr":950000,"site":"Cars24"}],
               "sources":[{"title":"cars24.com","url":"https://x"}],"cached":true,"pricedAt":"2026-11-24T10:00:00Z"}"""
        )!!
        assertEquals(980_000.0, e.highInr, 0.0)
        assertNull(e.newPriceInr)
        assertEquals(1, e.comparables.size)
        assertEquals("2026-11-24T10:00:00Z", e.pricedAt)
    }

    @Test fun `answer with no explanation is refused`() {
        assertNull(RemotePriceEstimator.parse("""{"lowInr":900000,"highInr":980000,"steps":[]}"""))
    }

    @Test fun `km is banded so typing the odometer doesn't refetch`() {
        val q = AiPriceQuery("Hyundai", "Creta", Variant.MID, 2019, "Diesel", 61_234, "Pune", 2)
        assertEquals(60_000, q.banded().km)
        assertEquals(q.banded(), q.copy(km = 58_900).banded())
    }
}
