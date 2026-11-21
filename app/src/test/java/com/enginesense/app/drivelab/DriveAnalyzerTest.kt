package com.enginesense.app.drivelab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveAnalyzerTest {

    private fun running(t: Long, speed: Double, rpm: Double = 1500.0, volts: Double? = null, coolant: Double? = null) =
        DriveSample(t, rpm, speed, 5.0, 20.0, 30.0, coolant, volts, null, null, null, null)

    private fun off(t: Long, volts: Double, rpm: Double? = null) =
        DriveSample(t, rpm, null, null, null, null, null, null, volts, null, null, null)

    /** A steady drive at [speed] km/h, one sample a second. */
    private fun steady(startMs: Long, seconds: Int, speed: Double) = (0 until seconds).map { running(startMs + it * 1000L, speed) }

    @Test
    fun `a gap longer than two minutes splits trips`() {
        val samples = steady(0, 120, 36.0) + steady(120_000L + 5 * 60_000L, 120, 36.0)
        assertEquals(2, DriveAnalyzer.splitTrips(samples).size)
    }

    @Test
    fun `trips under a minute are ignored`() {
        assertEquals(0, DriveAnalyzer.splitTrips(steady(0, 30, 20.0)).size)
    }

    @Test
    fun `distance is speed integrated over time`() {
        // 36 km/h for 100 s = 1 km
        val trip = DriveAnalyzer.tripSummary(steady(0, 101, 36.0), emptyList(), "Petrol")
        assertEquals(1.0, trip.distanceKm, 0.01)
    }

    @Test
    fun `a hard stop counts as one harsh brake, not one per sample`() {
        val samples = steady(0, 60, 60.0) +
            listOf(running(60_000, 45.0), running(61_000, 30.0), running(62_000, 15.0)) +
            steady(63_000, 60, 0.0)
        val trip = DriveAnalyzer.tripSummary(samples, samples, "Petrol")
        assertEquals(1, trip.harshBrakes)
        assertEquals(0, trip.harshAccelerations)
    }

    @Test
    fun `a gentle drive scores full marks and harsh events cost points`() {
        assertEquals(100, DriveAnalyzer.drivingScore(0, 0, 20.0, 0.0, 0.0, 0.0))
        assertTrue(DriveAnalyzer.drivingScore(1, 3, 10.0, 0.1, 0.0, 0.0) < 80)
    }

    @Test
    fun `idle time is engine running with the car stopped`() {
        val trip = DriveAnalyzer.tripSummary(steady(0, 91, 0.0), emptyList(), "Petrol")
        assertEquals(90L, trip.idleSeconds)
    }

    @Test
    fun `cranking dip is the lowest voltage just before the engine caught`() {
        val before = listOf(off(0, 12.6, rpm = 0.0), off(500, 10.1, rpm = 0.0), off(1_000, 11.9, rpm = 0.0))
        val drive = steady(2_000, 90, 20.0)
        val summary = DriveAnalyzer.analyze(before + drive, "Petrol")
        assertEquals(10.1, summary.trips.single().crankingMinVolts!!, 0.001)
        assertEquals(listOf(10.1), summary.battery.crankingDips)
    }

    @Test
    fun `resting voltage needs the engine off for half an hour`() {
        val drive = steady(0, 90, 20.0)
        val end = drive.last().timeMillis
        val parked = listOf(off(end + 60_000, 12.8), off(end + 40 * 60_000L, 12.4))
        val battery = DriveAnalyzer.analyze(drive + parked, "Petrol").battery
        assertEquals(12.4, battery.restingVolts!!, 0.001)
        assertEquals(12.4, battery.lowestRestingVolts!!, 0.001)
    }

    @Test
    fun `a dongle that keeps answering after engine off shows how long it stayed awake`() {
        val drive = steady(0, 90, 20.0)
        val end = drive.last().timeMillis
        val parked = (1..12).map { off(end + it * 5 * 60_000L, 12.5) } // 60 min of contact, every 5 min
        assertEquals(60L, DriveAnalyzer.analyze(drive + parked, "Petrol").battery.longestAwakeAfterOffMinutes)
    }

    @Test
    fun `warm-up time only counts when the trip started cold`() {
        val cold = listOf(running(0, 0.0, coolant = 30.0)) + steady(1_000, 100, 20.0) + listOf(running(101_000, 20.0, coolant = 75.0))
        assertEquals(101L, DriveAnalyzer.tripSummary(cold, emptyList(), "Petrol").warmUpSeconds)
        val warm = listOf(running(0, 0.0, coolant = 85.0)) + steady(1_000, 100, 20.0)
        assertNull(DriveAnalyzer.tripSummary(warm, emptyList(), "Petrol").warmUpSeconds)
    }

    @Test
    fun `csv round trip keeps every field`() {
        val s = DriveSample(123L, 800.0, 12.0, 3.5, 15.0, 25.0, 88.0, 14.1, 14.0, 62.0, 1500.0, 48620.5)
        val back = DriveSample.fromCsv(s.toCsv())
        assertNotNull(back)
        assertEquals(s, back)
        val empty = DriveSample(5L, null, null, null, null, null, null, null, 12.3, null, null, null)
        assertEquals(empty, DriveSample.fromCsv(empty.toCsv()))
    }
}
