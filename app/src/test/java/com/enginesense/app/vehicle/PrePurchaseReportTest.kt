package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity
import com.enginesense.app.vehiclelookup.RcLookupResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrePurchaseReportTest {

    private fun snapshot(vararg values: Pair<PidId, Double>, dtcs: List<DtcReading> = emptyList()) = VehicleSnapshot(
        timestampMillis = 0L,
        vehicleBrand = null,
        readings = values.associate { (pid, v) -> pid to PidReading(pid, PidStatus.VERIFIED, v, null) },
        dtcs = dtcs
    )

    private fun rc(chassisMatch: Boolean? = true, manufacturingMonth: String? = "2019-03") = RcLookupResult(
        registrationNumber = "MH12AB1234", make = "MARUTI SUZUKI", model = "SWIFT VXI", fuelType = "PETROL", color = null,
        category = null, manufacturingMonth = manufacturingMonth, registrationDate = "2019-04-02", registeredAt = null,
        cubicCapacity = null, insuranceCompany = null, insuranceUpto = null, puccUpto = null, fitnessUpto = null, taxUpto = null,
        partial = false, source = "test", chassisMatch = chassisMatch
    )

    private fun build(
        findings: List<DiagnosticFinding> = emptyList(),
        snapshot: VehicleSnapshot = snapshot(PidId.ODOMETER to 60_000.0),
        vin: String? = "MA3XXXXXXXX123456",
        dashboardKm: Double? = null,
        rc: RcLookupResult? = null,
        price: Double? = null,
        year: Int? = null
    ) = PrePurchaseReportBuilder.build(
        findings, healthScore = 95, readiness = null, snapshot = snapshot, vin = vin, dashboardKm = dashboardKm,
        rc = rc, registration = null, exShowroomPriceInr = price, buyerEnteredYear = year, currentYear = 2026
    )

    @Test
    fun `a clean car with matching odometer and chassis is a good buy with nothing to pay for`() {
        val report = build(dashboardKm = 60_100.0, rc = rc())
        assertEquals(PurchaseVerdict.GOOD_BUY, report.verdict)
        assertTrue(report.redFlags.isEmpty())
        assertTrue(report.faults.isEmpty())
        assertEquals(0, report.repairMaxInr)
        assertEquals(CheckOutcome.CLEAR, report.checks.first { it.label.startsWith("Odometer") }.outcome)
        assertEquals(CheckOutcome.CLEAR, report.checks.first { it.label.startsWith("Chassis") }.outcome)
        assertEquals("MH12AB1234", report.registration)
    }

    @Test
    fun `an odometer mismatch is a red flag and rules out good buy`() {
        val report = build(dashboardKm = 35_000.0)
        assertEquals(PurchaseVerdict.BUY_WITH_CAUTION, report.verdict)
        assertEquals(1, report.redFlags.size)
        assertTrue(report.redFlags.first().evidence.contains("25,000 km"))
        assertEquals(CheckOutcome.FLAGGED, report.checks.first { it.label.startsWith("Odometer") }.outcome)
        assertEquals(OdometerMatch.MISMATCH, report.odometer.match)
    }

    @Test
    fun `a chassis mismatch is a red flag, and an unknown one is not reported rather than passed`() {
        val mismatch = build(rc = rc(chassisMatch = false))
        assertTrue(mismatch.redFlags.any { it.title.contains("RC") })
        assertEquals(CheckOutcome.FLAGGED, mismatch.checks.first { it.label.startsWith("Chassis") }.outcome)

        val unknown = build(rc = rc(chassisMatch = null))
        assertTrue(unknown.redFlags.isEmpty())
        assertEquals(CheckOutcome.NOT_AVAILABLE, unknown.checks.first { it.label.startsWith("Chassis") }.outcome)
        assertEquals(CheckOutcome.NOT_AVAILABLE, build().checks.first { it.label.startsWith("Odometer") }.outcome)
    }

    @Test
    fun `hidden permanent codes are priced into the total, developing ones are listed but not counted`() {
        val snap = snapshot(dtcs = listOf(DtcReading("P0420", DtcSource.PERMANENT), DtcReading("P0171", DtcSource.PENDING)))
        val report = build(snapshot = snap)

        val hidden = report.faults.first { it.code == "P0420" }
        assertEquals(FaultKind.HIDDEN_PERMANENT, hidden.kind)
        assertEquals("Catalytic converter", hidden.cost?.category)
        val pending = report.faults.first { it.code == "P0171" }
        assertEquals(FaultKind.PENDING, pending.kind)
        assertFalse(pending.countedInTotal)
        assertEquals(hidden.cost!!.minInr, report.repairMinInr)
        assertEquals(hidden.cost!!.maxInr, report.repairMaxInr)
    }

    @Test
    fun `a stored finding is not listed twice when its code is also in the permanent list`() {
        val finding = DiagnosticFinding("cat", "Catalyst efficiency below threshold", "s", Severity.MODERATE, listOf("P0420"), recommendedAction = "Check it.")
        val snap = snapshot(dtcs = listOf(DtcReading("P0420", DtcSource.STORED), DtcReading("P0420", DtcSource.PERMANENT)))
        val report = build(findings = listOf(finding), snapshot = snap)
        assertEquals(1, report.faults.size)
        assertEquals(FaultKind.STORED, report.faults.single().kind)
    }

    @Test
    fun `fair value uses the RTO year over the typed one and takes repairs off for the as-it-stands range`() {
        val snap = snapshot(PidId.ODOMETER to 60_000.0, dtcs = listOf(DtcReading("P0420", DtcSource.PERMANENT)))
        val report = build(snapshot = snap, rc = rc(manufacturingMonth = "2021-06"), price = 800_000.0, year = 2015)

        assertEquals(2021, report.manufactureYear)
        val value = assertNotNullValue(report.value)
        assertEquals(5, value.fair.ageYears)
        assertEquals(value.fair.lowEstimate - report.repairMaxInr, value.afterRepairsLow, 0.01)
        assertEquals(value.fair.highEstimate - report.repairMinInr, value.afterRepairsHigh, 0.01)
        assertNull(report.valueMissingReason)
    }

    @Test
    fun `fair value is never guessed without a price or a year`() {
        val noPrice = build(year = 2019)
        assertNull(noPrice.value)
        assertTrue(noPrice.valueMissingReason!!.contains("ex-showroom"))

        val noYear = build(price = 800_000.0)
        assertNull(noYear.value)
        assertTrue(noYear.valueMissingReason!!.contains("year"))
    }

    private fun <T> assertNotNullValue(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
