package com.enginesense.app.dealerreport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DealerSaleReportTest {

    @Test
    fun `the Status image shows only the RTO code of the number`() {
        assertEquals("KA02", DealerSaleSummaryBuilder.rtoCode("KA02MH3313"))
        assertEquals("UP14", DealerSaleSummaryBuilder.rtoCode("up 14 ab 1234"))
        assertEquals("MH12", DealerSaleSummaryBuilder.rtoCode("MH-12-DE-1433"))
        assertEquals("DL3", DealerSaleSummaryBuilder.rtoCode("DL 3C AB 1234"))
    }

    @Test
    fun `Bharat series has no RTO, and junk gives nothing rather than leaking the number`() {
        assertEquals("BH", DealerSaleSummaryBuilder.rtoCode("22BH1234AA"))
        assertNull(DealerSaleSummaryBuilder.rtoCode("1234"))
        assertNull(DealerSaleSummaryBuilder.rtoCode(null))
    }

    @Test
    fun `owner count reads as 1st, 2nd, 3rd owner`() {
        assertEquals("1st owner", DealerSaleSummaryBuilder.ordinalOwner(1))
        assertEquals("2nd owner", DealerSaleSummaryBuilder.ordinalOwner(2))
        assertEquals("3rd owner", DealerSaleSummaryBuilder.ordinalOwner(3))
        assertEquals("4th owner", DealerSaleSummaryBuilder.ordinalOwner(4))
    }

    @Test
    fun `prices are grouped the Indian way`() {
        assertEquals("3,25,000", indianGrouping(325_000))
        assertEquals("12,50,000", indianGrouping(1_250_000))
        assertEquals("1,05,00,000", indianGrouping(10_500_000))
        assertEquals("999", indianGrouping(999))
        assertEquals("1,000", indianGrouping(1_000))
    }

    @Test
    fun `the Status price reads in lakh or crore`() {
        assertEquals("₹12.38 Lakh", priceLabel(1_238_000.0))
        assertEquals("₹1.00 Lakh", priceLabel(100_000.0))
        assertEquals("₹1.05 Crore", priceLabel(10_500_000.0))
        assertEquals("₹85,000", priceLabel(85_000.0))
    }

    @Test
    fun `a pack's GST split adds back up to its price`() {
        DEALER_PACKS.forEach { assertEquals(it.priceInr, it.beforeGstInr + it.gstInr) }
        assertEquals(84, DealerPack(5, 99).beforeGstInr)
        assertEquals(20, DealerPack(5, 99).perCarInr)
        assertEquals(17, DealerPack(30, 499).perCarInr)
    }

    @Test
    fun `the dealer's overall rating is the rounded average of what they rated`() {
        assertNull(DealerInspection().averageStars)
        assertEquals(4, DealerInspection(ratings = mapOf("Tyres" to 4, "AC" to 5, "Exterior and paint" to 4)).averageStars)
        assertEquals(5, DealerInspection(ratings = mapOf("AC" to 5)).averageStars)
    }

    @Test
    fun `an inspection with only a note still counts as filled in`() {
        assertEquals(true, DealerInspection().isEmpty)
        assertEquals(false, DealerInspection(note = "Family car").isEmpty)
    }
}
