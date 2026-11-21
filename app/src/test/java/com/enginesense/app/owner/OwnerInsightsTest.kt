package com.enginesense.app.owner

import com.enginesense.app.vehicle.IgnitionType
import com.enginesense.app.vehicle.ReadinessMonitor
import com.enginesense.app.vehicle.ReadinessStatus
import com.enginesense.app.vehiclelookup.RcLookupResult
import com.enginesense.app.vehiclelookup.ValidityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class OwnerInsightsTest {

    private val today = LocalDate.of(2026, 11, 21)

    private fun rc(puc: String? = null, insurance: String? = null, tax: String? = null) = RcLookupResult(
        registrationNumber = "KA03NG8216", make = "Kia", model = "Seltos", fuelType = "Petrol", color = null, category = null,
        manufacturingMonth = null, registrationDate = null, registeredAt = null, cubicCapacity = null, insuranceCompany = null,
        insuranceUpto = insurance, puccUpto = puc, fitnessUpto = null, taxUpto = tax, partial = false, source = "test"
    )

    @Test
    fun renewalsUseTheRtoDatesWithACountdownNearExpiry() {
        val items = OwnerInsights.renewals(rc(puc = "2026-12-03", insurance = "2027-08-18", tax = "LTT"), OwnerCarData(), today)
        val puc = items.first { it.kind == RenewalKind.PUC }
        assertEquals(12L, puc.daysLeft)
        assertEquals("Expires in 12 days · 3 Dec", puc.display)
        assertEquals(ValidityStatus.EXPIRES_SOON, puc.status)
        assertTrue(puc.fromRto)
        assertEquals("Valid till 18 Aug 2027", items.first { it.kind == RenewalKind.INSURANCE }.display)
        assertEquals(ValidityStatus.LIFETIME, items.first { it.kind == RenewalKind.ROAD_TAX }.status)
        assertEquals("Add the expiry date", items.first { it.kind == RenewalKind.LICENCE }.display)
    }

    @Test
    fun aDateTheOwnerSetWinsOverTheRtoRecord() {
        val data = OwnerCarData(renewalDates = mapOf(RenewalKind.PUC to "2027-06-01"))
        val puc = OwnerInsights.renewals(rc(puc = "2026-12-03"), data, today).first { it.kind == RenewalKind.PUC }
        assertEquals(LocalDate.of(2027, 6, 1), puc.date)
        assertFalse(puc.fromRto)
        assertEquals(ValidityStatus.VALID, puc.status)
    }

    @Test
    fun remindersGoOutThirtySevenAndOneDayBefore() {
        val data = OwnerCarData(renewalDates = mapOf(
            RenewalKind.PUC to today.plusDays(7).toString(),
            RenewalKind.INSURANCE to today.plusDays(8).toString(),
            RenewalKind.LICENCE to today.plusDays(30).toString()
        ))
        val due = OwnerInsights.remindersDue(OwnerInsights.renewals(null, data, today)).map { it.kind }.toSet()
        assertEquals(setOf(RenewalKind.PUC, RenewalKind.LICENCE), due)
    }

    @Test
    fun nextServiceIsByKmOrDateWhicheverComesFirst() {
        val data = OwnerCarData(services = listOf(
            ServiceEntry(1, "2026-03-14", 30_000, "General service", 6800),
            ServiceEntry(2, "2026-09-12", 38_200, "General service", 6800)
        ))
        val next = OwnerInsights.nextService(data, currentOdometerKm = 45_900, today = today)!!
        assertEquals(48_200, next.dueKm)
        assertEquals(2_300, next.kmLeft)
        assertEquals(LocalDate.of(2027, 9, 12), next.dueDate)
        assertFalse(next.overdue)
        assertTrue(OwnerInsights.nextService(data, 49_000, today)!!.overdue)
        assertNull(OwnerInsights.nextService(OwnerCarData(), 1000, today))
    }

    @Test
    fun mileageIsWorkedOutBetweenTwoFullTanks() {
        val fuel = listOf(
            FuelEntry(1, "2026-11-01", 30.0, 3000, 40_000, fullTank = true),
            FuelEntry(2, "2026-11-08", 10.0, 1000, 40_200, fullTank = false),
            FuelEntry(3, "2026-11-18", 20.0, 2000, 40_420, fullTank = true)
        )
        assertEquals(14.0, OwnerInsights.fuelMileage(fuel)!!, 0.001)
        assertNull(OwnerInsights.fuelMileage(fuel.take(2)))
    }

    @Test
    fun monthSpendAddsFuelServiceAndOtherCosts() {
        val data = OwnerCarData(
            services = listOf(ServiceEntry(1, "2026-11-02", null, "Wash and polish", 1300)),
            fuel = listOf(FuelEntry(1, "2026-11-18", 19.6, 2000, null, true), FuelEntry(2, "2026-10-30", 20.0, 2200, null, true)),
            expenses = listOf(ExpenseEntry(1, "2026-11-10", ExpenseCategory.TOLL, 950))
        )
        assertEquals(MonthSpend(total = 4250, fuel = 2000, service = 1300, other = 950), OwnerInsights.monthSpend(data, YearMonth.of(2026, 11)))
    }

    @Test
    fun pucVerdictFollowsTheLightAndTheSelfTests() {
        fun readiness(mil: Boolean, readyAll: Boolean) = ReadinessStatus(
            milOn = mil, dtcCount = 0, ignitionType = IgnitionType.SPARK,
            monitors = listOf(ReadinessMonitor("Catalyst", true, readyAll), ReadinessMonitor("EGR", true, true), ReadinessMonitor("Heater", false, false))
        )
        assertEquals(Level.GOOD, OwnerInsights.pucVerdict(readiness(mil = false, readyAll = true), 0).level)
        assertEquals(Level.WATCH, OwnerInsights.pucVerdict(readiness(mil = false, readyAll = false), 0).level)
        assertEquals(Level.ATTENTION, OwnerInsights.pucVerdict(readiness(mil = true, readyAll = true), 0).level)
        assertEquals(Level.UNKNOWN, OwnerInsights.pucVerdict(null, 0).level)
    }

    @Test
    fun batteryTakesTheWorstOfItsReadings() {
        assertEquals(Level.GOOD, OwnerInsights.batteryHealth(14.1, 12.6, 10.4, 14.2).level)
        assertEquals(Level.ATTENTION, OwnerInsights.batteryHealth(14.1, 12.6, 9.2, 14.2).level)
        assertEquals(Level.WATCH, OwnerInsights.batteryHealth(null, 12.1, null, null).level)
        assertEquals(Level.UNKNOWN, OwnerInsights.batteryHealth(null, null, null, null).level)
    }

    @Test
    fun carValueComesFromTheTableAndAge() {
        val v = OwnerInsights.carValue("Kia", "Seltos", 2020, 60_000, today)
        assertNotNull(v)
        assertTrue(v!!.lowEstimate < v.highEstimate)
        assertNull(OwnerInsights.carValue("Kia", "Unknown model", 2020, null, today))
    }
}
