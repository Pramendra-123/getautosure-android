package com.enginesense.app.owner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CarWalletTest {

    private val today = LocalDate.of(2026, 11, 21)

    private fun car(
        newPrice: Double? = 1_500_000.0,
        registered: LocalDate? = LocalDate.of(2020, 7, 14),
        odometerKm: Int? = 72_000,
        measured: Double? = null,
        trips: Double? = null,
        health: Int? = 100
    ) = WalletCar(newPrice, registered, odometerKm, 1497, "Petrol", 17.0, measured, trips, health)

    @Test
    fun dayOneIsAllEstimatesFromTheCarItself() {
        val s = CarWallet.summary(OwnerCarData(), car(), today)
        // 72,000 km over ~6.4 years is ~940 km a month.
        assertEquals(KmSource.ODOMETER, s.km.source)
        assertTrue(s.km.km in 900..980)
        assertEquals(0, s.confirmedCount)
        assertEquals(3, s.confirmableCount)
        val fuel = s.line(CostKind.FUEL)
        assertFalse(fuel.confirmed)
        // ~940 × 12 ÷ (17 × 0.85) × ₹103 ≈ ₹80,000.
        assertTrue(fuel.yearlyInr in 75_000..85_000)
        assertTrue(s.line(CostKind.INSURANCE).yearlyInr in 15_000..35_000)
        assertTrue(s.line(CostKind.SERVICE).yearlyInr in 10_000..20_000)
        assertEquals(0, s.line(CostKind.OTHER).yearlyInr)
        assertNotNull(s.valueLostThisYear)
    }

    @Test
    fun theOwnersKmAnswerWinsOverTheOdometer() {
        val s = CarWallet.summary(OwnerCarData(monthlyKm = 500), car(), today)
        assertEquals(KmPerMonth(500, KmSource.OWNER), s.km)
    }

    @Test
    fun unknownAgeAndOdometerFallsBackToAGuessTheAppAsksAbout() {
        val s = CarWallet.summary(OwnerCarData(), car(registered = null, odometerKm = null), today)
        assertEquals(KmSource.GUESS, s.km.source)
        assertEquals("Add your premium", s.line(CostKind.INSURANCE).basis)
    }

    @Test
    fun realPaymentsReplaceTheEstimates() {
        val data = OwnerCarData(
            fuel = listOf(
                FuelEntry(1, "2026-09-21", 30.0, 3_090, null, true),
                FuelEntry(2, "2026-10-21", 30.0, 3_090, null, true),
                FuelEntry(3, "2026-11-20", 30.0, 3_090, null, true)
            ),
            expenses = listOf(ExpenseEntry(4, "2026-07-02", ExpenseCategory.INSURANCE, 24_800)),
            services = listOf(ServiceEntry(5, "2026-03-10", 60_000, "Annual service", 11_200))
        )
        val s = CarWallet.summary(data, car(), today)
        assertTrue(s.allConfirmed)
        // ₹9,270 over 61 days → about ₹55,000 a year.
        assertTrue(s.line(CostKind.FUEL).yearlyInr in 50_000..60_000)
        assertEquals(24_800, s.line(CostKind.INSURANCE).yearlyInr)
        assertEquals(11_200, s.line(CostKind.SERVICE).yearlyInr)
        assertEquals(103.0, s.fuelPricePerLitre, 0.01)
        assertEquals(3_090 * 3 + 24_800 + 11_200, s.spentThisYear)
    }

    @Test
    fun oneFillUpIsNotAYearOfFuel() {
        val data = OwnerCarData(fuel = listOf(FuelEntry(1, "2026-11-20", null, 2_500, null, false)))
        assertFalse(CarWallet.summary(data, car(), today).line(CostKind.FUEL).confirmed)
    }

    @Test
    fun thirdPartyPremiumFollowsEngineSize() {
        assertEquals(2_094, CarWallet.thirdPartyPremium(998))
        assertEquals(3_416, CarWallet.thirdPartyPremium(1497))
        assertEquals(7_897, CarWallet.thirdPartyPremium(1998))
    }

    @Test
    fun alertsAddOnlyClearCarPayments() {
        val fuel = PaymentSmsParser.parseAlert("Rs.2500.00 debited from A/c XX1234 on 20-11-26 to VPA indianoil.fuel@okaxis. Avl Bal Rs.45210.55", today)
        assertEquals(PaymentKind.FUEL, fuel?.kind)
        assertEquals(2500, fuel?.amountInr)
        // A UPI payment to a person, a car service and a credit are left for the owner to share.
        assertNull(PaymentSmsParser.parseAlert("Rs.500 debited from A/c XX1234 to VPA ramesh@ybl", today))
        assertNull(PaymentSmsParser.parseAlert("Rs.4500 debited from A/c XX1234 at Kia Motors service", today))
        assertNull(PaymentSmsParser.parseAlert("Rs.2500 credited to A/c XX1234 from indianoil", today))
        assertEquals(PaymentKind.TOLL, PaymentSmsParser.parseAlert("Rs.135 deducted from your FASTag for KA03NG8216 at Nelamangala toll plaza", today)?.kind)
    }

    @Test
    fun shortAmounts() {
        assertEquals("₹1.1 L", CarWallet.short(110_000))
        assertEquals("₹48,200", CarWallet.short(48_200))
    }
}
