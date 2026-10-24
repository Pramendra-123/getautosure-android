package com.enginesense.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FairPriceEstimatorTest {

    @Test
    fun `a 2 year old car depreciates by the IRDAI 2-year band, 20 percent`() {
        val est = FairPriceEstimator.estimate(exShowroomPrice = 1000000.0, ageYears = 2.0, odometerKm = null)
        assertEquals(20, est.depreciationPercent)
        assertFalse(est.beyondPublishedSchedule)
    }

    @Test
    fun `a 10 year old car is flagged as beyond IRDAI's published 5-year schedule`() {
        val est = FairPriceEstimator.estimate(exShowroomPrice = 500000.0, ageYears = 10.0, odometerKm = null)
        assertTrue(est.beyondPublishedSchedule)
        assertTrue(est.depreciationPercent > 50)
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
    fun `depreciation never exceeds the capped maximum even for a very old car`() {
        val est = FairPriceEstimator.estimate(exShowroomPrice = 300000.0, ageYears = 25.0, odometerKm = null)
        assertTrue(est.depreciationPercent <= 80)
        assertTrue(est.lowEstimate > 0)
    }
}
