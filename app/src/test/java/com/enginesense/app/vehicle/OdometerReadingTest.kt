package com.enginesense.app.vehicle

import com.enginesense.app.vehiclelookup.RcLookupResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OdometerReadingTest {

    private val today = LocalDate.of(2026, 11, 24)

    @Test fun `a half-typed or missing reading is not a km`() {
        assertNull(OdometerReading.exactKm(""))
        assertNull(OdometerReading.exactKm("48"))
        assertEquals(48_250.0, OdometerReading.exactKm("48250")!!, 0.0)
    }

    @Test fun `the exact reading wins and a range is priced at its middle`() {
        assertEquals(48_250, OdometerReading.kmForPrice(48_250.0, KmRange.K30_50))
        assertEquals(40_000, OdometerReading.kmForPrice(null, KmRange.K30_50))
        assertNull(OdometerReading.kmForPrice(null, null))
    }

    @Test fun `km a year sorts heavy, normal and suspiciously low use`() {
        assertEquals(KmPace.HIGH, OdometerReading.perYear(48_000.0, 2.0).pace)
        assertEquals(KmPace.NORMAL, OdometerReading.perYear(26_000.0, 2.0).pace)
        assertEquals(KmPace.SUSPICIOUSLY_LOW, OdometerReading.perYear(12_000.0, 6.0).pace)
        // A young car that's barely driven is just a young car.
        assertEquals(KmPace.NORMAL, OdometerReading.perYear(2_000.0, 1.5).pace)
    }

    @Test fun `age comes from the registration date, else mid-year of the make year`() {
        assertEquals(2.0, OdometerReading.ageYears(LocalDate.of(2024, 11, 24), null, today)!!, 0.01)
        assertEquals(2.4, OdometerReading.ageYears(null, 2024, today)!!, 0.05)
        assertNull(OdometerReading.ageYears(null, null, today))
    }

    private fun rc(registrationDate: String) = RcLookupResult(
        registrationNumber = "KA01MX4821", make = "SKODA", model = "SLAVIA", fuelType = "PETROL", color = null,
        category = null, manufacturingMonth = "2019-03", registrationDate = registrationDate, registeredAt = null,
        cubicCapacity = null, insuranceCompany = null, insuranceUpto = null, puccUpto = null, fitnessUpto = null, taxUpto = null,
        partial = false, source = "test"
    )

    private fun report(dashboardKm: Double?, range: KmRange? = null, ecuKm: Double? = null) = PrePurchaseReportBuilder.build(
        emptyList(), healthScore = 95, readiness = null,
        snapshot = ecuKm?.let { VehicleSnapshot(0L, null, mapOf(PidId.ODOMETER to PidReading(PidId.ODOMETER, PidStatus.VERIFIED, it, null)), emptyList()) },
        vin = null, dashboardKm = dashboardKm, rc = rc("2019-04-02"), registration = null,
        exShowroomPriceInr = 800_000.0, buyerEnteredYear = null, currentYear = 2026, today = today, kmRange = range
    )

    @Test fun `a range is the seller's word and is worth asking about`() {
        val r = report(dashboardKm = null, range = KmRange.K30_50, ecuKm = 45_000.0)
        assertEquals(40_000.0, r.odometer.priceKm!!, 0.0)
        assertEquals("30–50k km (seller's word, not checked)", r.odometer.enteredText)
        val note = r.worthAsking.first { it.title == "Odometer not checked" }
        assertTrue(note.evidence.contains("which fits"))
        assertTrue(r.redFlags.isEmpty())
    }

    @Test fun `very low km for an old car is worth asking about, not a red flag`() {
        val r = report(dashboardKm = 12_000.0)
        assertTrue(r.worthAsking.any { it.title == "Very low km for its age" })
        assertTrue(r.redFlags.isEmpty())
        assertTrue(report(dashboardKm = 160_000.0).worthAsking.any { it.title.startsWith("Driven a lot") })
        assertTrue(report(dashboardKm = 80_000.0).worthAsking.none { it.title.contains("km") })
    }

    @Test fun `a small gap is worth asking about and doesn't change the verdict`() {
        val r = report(dashboardKm = 49_250.0, ecuKm = 48_190.0)
        assertEquals(OdometerMatch.SMALL_GAP, r.odometer.match)
        assertTrue(r.redFlags.isEmpty())
        assertTrue(r.worthAsking.any { it.title.startsWith("Small gap") && it.evidence.contains("1,060 km apart") })
        assertEquals(CheckOutcome.WORTH_ASKING, r.checks.first { it.label.startsWith("Odometer") }.outcome)
        assertTrue(report(dashboardKm = 43_000.0, ecuKm = 48_190.0).redFlags.any { it.title.startsWith("Dashboard km doesn't match") })
    }

    @Test fun `the price uses the km the buyer gave`() {
        val low = report(dashboardKm = 30_000.0).value!!.fair.lowEstimate
        val high = report(dashboardKm = 150_000.0).value!!.fair.lowEstimate
        assertTrue("1.5 lakh km should price lower than 30k", high < low)
    }
}
