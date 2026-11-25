package com.enginesense.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestDriveTest {

    /** A steady pull with one RPM flare of [flare] at [atMs], speed holding. */
    private fun pull(a: DriveAnalyzer, startMs: Long, flare: Double, throttle: Double = 30.0): List<DriveEvent> {
        val events = mutableListOf<DriveEvent>()
        for (i in 0..10) {
            val t = startMs + i * 250L
            val rpm = if (i >= 6) 2_000.0 + flare else 2_000.0
            events += a.add(DriveSample(t, speedKmh = 40.0, rpm = rpm, throttlePct = throttle, coolantC = 90.0))
        }
        return events
    }

    @Test fun `an RPM flare without speed is a slip, two of them a finding`() {
        val a = DriveAnalyzer(automatic = true)
        assertEquals(1, pull(a, 0, 650.0).size)
        assertEquals(1, pull(a, 10_000, 650.0).size)
        val r = a.result()
        val slip = r.findings.first { it.title.startsWith("Gearbox slipped") }
        assertTrue(slip.serious)
        assertEquals(DriveAnalyzer.AUTO_GEARBOX_CHECK, slip.cost)
        assertEquals(3, r.score)
    }

    @Test fun `one slip, a small flare or a kick-down under hard throttle is not a finding`() {
        val a = DriveAnalyzer(automatic = true)
        pull(a, 0, 650.0)
        pull(a, 10_000, 300.0)
        assertTrue(pull(a, 20_000, 900.0, throttle = 85.0).isEmpty())
        assertTrue(a.result().findings.none { it.title.contains("slipped") })
    }

    @Test fun `a manual's slip is the clutch`() {
        val a = DriveAnalyzer(automatic = false)
        pull(a, 0, 700.0); pull(a, 10_000, 700.0)
        assertTrue(a.result().findings.any { it.title == "Clutch slipped 2 times" })
    }

    @Test fun `running hot is called out live and is serious`() {
        val a = DriveAnalyzer(null)
        val events = a.add(DriveSample(0, speedKmh = 10.0, rpm = 900.0, coolantC = 110.0))
        assertEquals(1, events.size)
        assertTrue(a.add(DriveSample(1_000, coolantC = 111.0)).isEmpty())
        assertTrue(a.result().findings.any { it.serious && it.title.startsWith("Ran hot") })
    }

    @Test fun `lean at cruise, steady idle and normal charging`() {
        val a = DriveAnalyzer(null)
        repeat(25) { i -> a.add(DriveSample(i * 500L, speedKmh = 70.0, rpm = 2_200.0, shortTrim = 6.0, longTrim = 9.0, coolantC = 92.0, volts = 14.2)) }
        repeat(25) { i -> a.add(DriveSample(20_000 + i * 500L, speedKmh = 0.0, rpm = 800.0 + (i % 2) * 20, coolantC = 92.0, volts = 14.1)) }
        val f = a.result().findings.associateBy { it.title }
        assertTrue(f.keys.any { it == "Running lean at steady speed" })
        assertEquals(DriveCheck.GOOD, f["Idle steady"]?.check)
        assertEquals(DriveCheck.GOOD, f["Charging normal"]?.check)
    }

    @Test fun `what the buyer heard is recorded with a cost`() {
        val a = DriveAnalyzer(null)
        a.heard(HeardNoise.BRAKES)
        val f = a.result().findings.single()
        assertEquals("You heard brake noise", f.title)
        assertTrue(f.cost != null)
    }

    @Test fun `gearbox type comes from the RTO model text`() {
        assertEquals(true, DriveAnalyzer.isAutomatic("SLAVIA STYLE 1.0 TSI AT"))
        assertEquals(false, DriveAnalyzer.isAutomatic("SWIFT VXI MT"))
        assertNull(DriveAnalyzer.isAutomatic("SWIFT VXI"))
    }

    @Test fun `a serious drive finding turns a good buy into buy with caution and adds its cost`() {
        val a = DriveAnalyzer(automatic = true)
        pull(a, 0, 650.0); pull(a, 10_000, 650.0)
        val drive = a.result()
        val snapshot = VehicleSnapshot(0L, null, mapOf(PidId.ODOMETER to PidReading(PidId.ODOMETER, PidStatus.VERIFIED, 60_000.0, null)), emptyList())
        fun build(d: DriveResult?) = PrePurchaseReportBuilder.build(
            emptyList(), healthScore = 95, readiness = null, snapshot = snapshot, vin = null, dashboardKm = 60_100.0,
            rc = null, registration = null, exShowroomPriceInr = null, buyerEnteredYear = null, currentYear = 2026, drive = d
        )
        assertEquals(PurchaseVerdict.GOOD_BUY, build(null).verdict)
        val r = build(drive)
        assertEquals(PurchaseVerdict.BUY_WITH_CAUTION, r.verdict)
        assertTrue(r.headline.contains("test drive"))
        assertTrue(r.redFlags.isEmpty())
        assertTrue(r.faults.any { it.kind == FaultKind.DRIVE && it.cost == DriveAnalyzer.AUTO_GEARBOX_CHECK })
        assertEquals(CheckOutcome.WORTH_ASKING, r.checks.first { it.label == "Test drive" }.outcome)
        assertFalse(r.worthAsking.isEmpty())
    }
}
