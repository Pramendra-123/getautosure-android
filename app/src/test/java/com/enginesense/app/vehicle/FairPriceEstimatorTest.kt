package com.enginesense.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FairPriceEstimatorTest {

    @Test
    fun `a 2 year old car is 20 percent below its new price`() {
        val est = FairPriceEstimator.estimate(exShowroomPrice = 1000000.0, ageYears = 2.0, odometerKm = null)
        assertEquals(20, est.depreciationPercent)
        assertFalse(est.beyondPublishedSchedule)
    }

    @Test
    fun `ages between points on the curve are blended`() {
        val est = FairPriceEstimator.estimate(exShowroomPrice = 1000000.0, ageYears = 6.5, odometerKm = null)
        assertEquals(45, est.depreciationPercent)
    }

    @Test
    fun `a 7 year old mid-size SUV lands near real used prices, not insurance write-down`() {
        // A 2019 Creta, mid variant at about ₹15.8 lakh new, sells for roughly ₹8–9.5 lakh.
        val est = FairPriceEstimator.estimate(exShowroomPrice = 1_580_000.0, ageYears = 7.0, odometerKm = null)
        assertTrue(est.lowEstimate in 700_000.0..900_000.0)
        assertTrue(est.highEstimate in 800_000.0..1_000_000.0)
    }

    @Test
    fun `a car over 10 years old is flagged as a loose guide`() {
        val est = FairPriceEstimator.estimate(exShowroomPrice = 500000.0, ageYears = 12.0, odometerKm = null)
        assertTrue(est.beyondPublishedSchedule)
        assertTrue(est.depreciationPercent > 58)
    }

    @Test
    fun `high odometer reading for the car's age increases depreciation, within a small bound`() {
        val normalKm = FairPriceEstimator.estimate(exShowroomPrice = 800000.0, ageYears = 2.0, odometerKm = 30000)
        val highKm = FairPriceEstimator.estimate(exShowroomPrice = 800000.0, ageYears = 2.0, odometerKm = 90000)
        assertTrue(highKm.depreciationPercent > normalKm.depreciationPercent)
        assertTrue(highKm.depreciationPercent - normalKm.depreciationPercent <= 10)
    }

    @Test
    fun `the estimate is a range, not a false-precision point figure`() {
        val est = FairPriceEstimator.estimate(exShowroomPrice = 1000000.0, ageYears = 3.0, odometerKm = null)
        assertTrue(est.lowEstimate < est.highEstimate)
    }

    @Test
    fun `a Maruti holds its value better than a Skoda of the same age and price`() {
        val maruti = FairPriceEstimator.estimate(1_000_000.0, 5.0, null, make = "Maruti Suzuki")
        val skoda = FairPriceEstimator.estimate(1_000_000.0, 5.0, null, make = "Skoda")
        val unknown = FairPriceEstimator.estimate(1_000_000.0, 5.0, null)
        assertTrue(maruti.depreciationPercent < unknown.depreciationPercent)
        assertTrue(skoda.depreciationPercent > unknown.depreciationPercent)
    }

    @Test
    fun `brand barely matters for a nearly new car`() {
        val maruti = FairPriceEstimator.estimate(1_000_000.0, 0.5, null, make = "Maruti Suzuki")
        val unknown = FairPriceEstimator.estimate(1_000_000.0, 0.5, null)
        assertTrue(unknown.depreciationPercent - maruti.depreciationPercent <= 1)
    }

    @Test
    fun `an old diesel loses more than the same petrol car, a young one doesn't`() {
        val oldDiesel = FairPriceEstimator.estimate(1_000_000.0, 8.0, null, fuelType = "Diesel")
        val oldPetrol = FairPriceEstimator.estimate(1_000_000.0, 8.0, null, fuelType = "Petrol")
        assertEquals(oldPetrol.depreciationPercent + 3, oldDiesel.depreciationPercent)
        val youngDiesel = FairPriceEstimator.estimate(1_000_000.0, 3.0, null, fuelType = "DIESEL")
        val youngPetrol = FairPriceEstimator.estimate(1_000_000.0, 3.0, null, fuelType = "Petrol")
        assertEquals(youngPetrol.depreciationPercent, youngDiesel.depreciationPercent)
    }

    @Test
    fun `depreciation never exceeds the capped maximum even for a very old car`() {
        val est = FairPriceEstimator.estimate(exShowroomPrice = 300000.0, ageYears = 25.0, odometerKm = null)
        assertTrue(est.depreciationPercent <= 80)
        assertTrue(est.lowEstimate > 0)
    }
}
