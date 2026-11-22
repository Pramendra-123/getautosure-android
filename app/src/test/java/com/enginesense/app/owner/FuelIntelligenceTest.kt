package com.enginesense.app.owner

import com.enginesense.app.drivelab.DriveSample
import com.enginesense.app.drivelab.DriveTrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelIntelligenceTest {

    private val day = 86_400_000L
    private val now = 100 * day

    private fun trip(start: Long, km: Double, litres: Double, idle: Long = 60) =
        DriveTrip(start, start + 1_800_000, km, 80.0, 40.0, idle, 0, 0, 0, 0, 90.0, 300, litres, km / litres, null, null, 90, 100)

    private fun sample(t: Long, trim: Double? = null, fuel: Double? = null, coolant: Double = 90.0) =
        DriveSample(t, 1500.0, 30.0, 5.0, 10.0, 20.0, coolant, 14.0, 14.0, fuel, null, null, trim)

    @Test
    fun mileageSaysHowSureItIs() {
        val trips = listOf(trip(now, 20.0, 1.5), trip(now + day, 20.0, 1.5))
        assertEquals(MileageBasis.CORRECTED, FuelIntelligence.mileage(trips, 1.05, 17.7)!!.basis)
        assertEquals(MileageBasis.SCANNER, FuelIntelligence.mileage(trips, null, 17.7)!!.basis)
        val claimed = FuelIntelligence.mileage(emptyList(), null, 20.0)!!
        assertEquals(MileageBasis.CLAIMED, claimed.basis)
        assertEquals(17.0, claimed.kmPerLitre, 0.01)
        assertNull(FuelIntelligence.mileage(emptyList(), null, null))
    }

    @Test
    fun fuelMixInPlainWords() {
        assertEquals("Balanced", FuelIntelligence.fuelMix(1.5)!!.headline)
        assertEquals(Level.GOOD, FuelIntelligence.fuelMix(7.0)!!.level)
        assertEquals("Adding 12% extra fuel", FuelIntelligence.fuelMix(12.0)!!.headline)
        assertEquals(Level.ATTENTION, FuelIntelligence.fuelMix(-12.0)!!.level)
        assertNull("diesels don't report trims", FuelIntelligence.fuelMix(null))
    }

    @Test
    fun aTankNeedingMoreFuelThanUsualIsOdd() {
        val fills = listOf(0L, 10 * day, 20 * day, 30 * day)
        val trims = listOf(2.0, 3.0, 2.5, 9.0)
        val samples = fills.zip(trims).flatMap { (start, trim) -> (0 until 25).map { sample(start + it * 60_000L, trim) } }
        val tanks = FuelIntelligence.tanks(fills, samples)
        assertEquals(TankVerdict.NOT_ENOUGH, tanks[0].verdict)
        assertEquals(TankVerdict.NOT_ENOUGH, tanks[1].verdict)
        assertEquals(TankVerdict.NORMAL, tanks[2].verdict)
        assertEquals(TankVerdict.ODD, tanks[3].verdict)
    }

    @Test
    fun coldEngineTrimsDontCount() {
        val samples = (0 until 25).map { sample(it * 60_000L, 15.0, coolant = 40.0) }
        assertNull(FuelIntelligence.recentTrim(samples))
    }

    @Test
    fun fuelGoingWhileParkedIsCaught() {
        val drive1 = (0 until 10).map { sample(it * 10_000L, fuel = 60.0) }
        val drive2 = (0 until 10).map { sample(day + it * 10_000L, fuel = 52.0) }
        val drive3 = (0 until 10).map { sample(2 * day + it * 10_000L, fuel = 50.0) }
        val drops = FuelIntelligence.parkedDrops(drive1 + drive2 + drive3)
        assertEquals(1, drops.size)
        assertEquals(8.0, drops.single().droppedPercent, 0.01)
    }

    @Test
    fun aLastingDropInMileageIsFlaggedWithReasons() {
        val baseline = (0 until 8).map { trip(now - 40 * day + it * 3 * day, 20.0, 1.25) } // 16 km/l
        val recent = (0 until 6).map { trip(now - 12 * day + it * 2 * day, 14.0, 1.0, idle = 600) } // 14 km/l, lots of idling
        val drop = FuelIntelligence.mileageDrop(baseline + recent, emptyList(), now, 100.0)!!
        assertEquals(13, drop.percent)
        assertTrue("More idling than usual" in drop.reasons)
        assertTrue(drop.monthlyCostInr!! > 0)
    }

    @Test
    fun aSmallDipOrTooFewTripsIsNotAnAlert() {
        val baseline = (0 until 8).map { trip(now - 40 * day + it * 3 * day, 20.0, 1.25) }
        val slightlyWorse = (0 until 6).map { trip(now - 12 * day + it * 2 * day, 15.0, 1.0) } // 6% down
        assertNull(FuelIntelligence.mileageDrop(baseline + slightlyWorse, emptyList(), now, 100.0))
        val fewTrips = (0 until 2).map { trip(now - 5 * day + it * day, 10.0, 1.0) }
        assertNull(FuelIntelligence.mileageDrop(baseline + fewTrips, emptyList(), now, 100.0))
    }
}
