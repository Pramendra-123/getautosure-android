package com.enginesense.app.owner

import com.enginesense.app.drivelab.DriveSample
import com.enginesense.app.drivelab.DriveTrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class FuelAutomationTest {

    private fun sample(t: Long, fuel: Double?, rpm: Double = 900.0) =
        DriveSample(t, rpm, 20.0, 5.0, 10.0, 20.0, 85.0, 14.0, 14.0, fuel, null, null)

    /** A drive of [n] samples one second apart starting at [start], all at [fuel]%. */
    private fun drive(start: Long, fuel: Double, n: Int = 30) = (0 until n).map { sample(start + it * 1000L, fuel) }

    @Test
    fun aBigJumpBetweenDrivesIsAFillUp() {
        val samples = drive(0, 18.0) + drive(3_600_000, 92.0)
        val fills = FuelAutomation.detectFills(samples)
        assertEquals(1, fills.size)
        assertEquals(74.0, fills[0].addedPercent, 0.01)
        assertEquals(37.0, fills[0].litres(50), 0.01)
        assertTrue(fills[0].fullTank)
    }

    @Test
    fun smallWobblesAndUsageAreNotFillUps() {
        val samples = drive(0, 40.0) + drive(3_600_000, 48.0) + drive(7_200_000, 35.0)
        assertTrue(FuelAutomation.detectFills(samples).isEmpty())
    }

    @Test
    fun carsWithoutAFuelGaugeReadingGetNothing() {
        assertTrue(FuelAutomation.detectFills(drive(0, 0.0).map { it.copy(fuelLevelPercent = null) } + drive(3_600_000, 0.0).map { it.copy(fuelLevelPercent = null) }).isEmpty())
    }

    private fun trip(day: Int, km: Double, litres: Double?): DriveTrip {
        val start = LocalDate.of(2026, 11, day).atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        return DriveTrip(start, start + 3_600_000, km, 80.0, 40.0, 0, 0, 0, 0, 0, 90.0, 300, litres, litres?.let { km / it }, null, null, 90, 100)
    }

    @Test
    fun fillUpsCorrectTheScannersEstimate() {
        val fuel = listOf(
            FuelEntry(1, "2026-11-01", 40.0, 4000, 10_000, fullTank = true),
            FuelEntry(2, "2026-11-20", 30.0, 3000, 10_420, fullTank = true)
        )
        // The scanner thought 25 L were used; really 30 L went back in.
        val trips = listOf(trip(5, 200.0, 12.0), trip(12, 220.0, 13.0))
        val factor = FuelAutomation.calibrationFactor(fuel, trips, ZoneOffset.UTC)!!
        assertEquals(1.2, factor, 0.001)
        val live = FuelAutomation.liveMileage(trips, factor)!!
        assertEquals(420.0 / 30.0, live.kmPerLitre, 0.01)
        assertTrue(live.calibrated)
        assertNull(FuelAutomation.calibrationFactor(fuel.take(1), trips, ZoneOffset.UTC))
    }

    @Test
    fun odometerCountsKmSinceTheLastReading() {
        val trips = listOf(trip(5, 200.0, null), trip(12, 220.0, null))
        val since = LocalDate.of(2026, 11, 10).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(10_220, FuelAutomation.estimatedOdometer(null, 10_000, since, trips))
        assertEquals(55_000, FuelAutomation.estimatedOdometer(55_000.4, 10_000, since, trips))
        assertNull(FuelAutomation.estimatedOdometer(null, null, null, trips))
    }
}
