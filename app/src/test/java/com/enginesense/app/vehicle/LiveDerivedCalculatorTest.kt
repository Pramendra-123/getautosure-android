package com.enginesense.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDerivedCalculatorTest {

    private fun ok(pid: PidId, value: Double) = pid to PidReading(pid, PidStatus.VERIFIED, value, null)
    private fun unsupported(pid: PidId) = pid to PidReading(pid, PidStatus.UNSUPPORTED, null, null)

    // A real Skoda Slavia 1.0 TSI Live Data reading: no MAF, 24 kPa manifold, 1743.5 rpm, 35 km/h.
    private val slaviaCruise = mapOf(
        unsupported(PidId.MAF_AIR_FLOW_RATE),
        ok(PidId.INTAKE_MANIFOLD_PRESSURE, 24.0),
        ok(PidId.RPM, 1743.5),
        ok(PidId.SPEED_KMH, 35.0),
        ok(PidId.INTAKE_AIR_TEMP_C, 35.0),
        ok(PidId.BAROMETRIC_PRESSURE, 100.0),
    )

    @Test
    fun `a car without MAF gets an estimated mileage once engine size is known`() {
        val d = LiveDerivedCalculator.compute(slaviaCruise, engineCc = 999, fuelType = "Petrol", tripSamples = emptyList(), turboSeenSoFar = false)
        assertEquals(AirFlowSource.ESTIMATED_FROM_MAP, d.airFlowSource)
        assertEquals(31.6, d.instant!!.kmPerLitre!!, 0.5)
        assertNull(d.mileageUnavailableReason)
    }

    @Test
    fun `without engine size it says what's missing instead of guessing`() {
        val d = LiveDerivedCalculator.compute(slaviaCruise, engineCc = null, fuelType = "Petrol", tripSamples = emptyList(), turboSeenSoFar = false)
        assertNull(d.instant)
        assertEquals("Add your engine size (cc) to see estimated mileage.", d.mileageUnavailableReason)
    }

    @Test
    fun `without a fuel type it asks for one`() {
        val d = LiveDerivedCalculator.compute(slaviaCruise, engineCc = 999, fuelType = null, tripSamples = emptyList(), turboSeenSoFar = false)
        assertNull(d.instant)
        assertEquals("Set your car's fuel type to see mileage.", d.mileageUnavailableReason)
    }

    @Test
    fun `a measured MAF needs no engine size`() {
        val withMaf = mapOf(ok(PidId.MAF_AIR_FLOW_RATE, 3.35), ok(PidId.SPEED_KMH, 35.0))
        val d = LiveDerivedCalculator.compute(withMaf, engineCc = null, fuelType = "Petrol", tripSamples = emptyList(), turboSeenSoFar = false)
        assertEquals(AirFlowSource.MEASURED, d.airFlowSource)
        assertNotNull(d.instant)
        assertNull(d.mileageUnavailableReason)
    }

    @Test
    fun `vacuum doesn't count as turbo, real boost does, and it stays seen`() {
        val cruising = LiveDerivedCalculator.compute(slaviaCruise, 999, "Petrol", emptyList(), turboSeenSoFar = false)
        assertEquals(-76.0, cruising.boostKpa!!, 0.01)
        assertFalse(cruising.turboSeen)

        val boosting = LiveDerivedCalculator.compute(slaviaCruise + ok(PidId.INTAKE_MANIFOLD_PRESSURE, 160.0), 999, "Petrol", emptyList(), false)
        assertTrue(boosting.turboSeen)

        val backToCruise = LiveDerivedCalculator.compute(slaviaCruise, 999, "Petrol", emptyList(), turboSeenSoFar = true)
        assertTrue(backToCruise.turboSeen)
    }

    @Test
    fun `trip average comes from the accumulated samples`() {
        // 60 s at a steady 36 km/h (600 m) burning 3.35 g/s of air.
        val samples = (0..60).map { FuelSample(it * 1000L, 36, 3.35) }
        val d = LiveDerivedCalculator.compute(slaviaCruise, 999, "Petrol", samples, false)
        assertEquals(0.6, d.trip!!.distanceKm!!, 0.01)
        assertNotNull(d.trip!!.averageKmPerLitre)
    }
}
