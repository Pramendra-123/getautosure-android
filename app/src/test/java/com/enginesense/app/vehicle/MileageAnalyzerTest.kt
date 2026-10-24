package com.enginesense.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MileageAnalyzerTest {

    @Test
    fun `CNG, Electric and unset fuel type are honestly unsupported, not a wrong number`() {
        val samples = listOf(FuelSample(0, 0, 2.0), FuelSample(60_000, 60, 2.0))
        for (fuelType in listOf("CNG", "Electric", "Hybrid", null)) {
            val result = MileageAnalyzer.analyze(samples, fuelType)
            assertTrue("expected unsupported for $fuelType", result.unsupportedFuelType)
            assertNull(result.averageKmPerLitre)
        }
    }

    @Test
    fun `too few samples returns null rather than a guess`() {
        val result = MileageAnalyzer.analyze(listOf(FuelSample(0, 50, 2.0)), "Petrol")
        assertNull(result.averageKmPerLitre)
        assertEquals(false, result.unsupportedFuelType)
    }

    @Test
    fun `a very short trip is flagged as too short rather than producing a noisy average`() {
        // 50 km/h for 10 seconds is only ~140m, well under the minimum distance.
        val samples = listOf(FuelSample(0, 50, 3.0), FuelSample(10_000, 50, 3.0))
        val result = MileageAnalyzer.analyze(samples, "Petrol")
        assertNull(result.averageKmPerLitre)
        assertTrue((result.distanceKm ?: 0.0) < 0.3)
    }

    @Test
    fun `a real trip produces a positive, finite km-per-litre figure`() {
        // Steady 60 km/h for 5 minutes with steady MAF -> a real distance and real fuel used.
        val samples = (0..30).map { i -> FuelSample(elapsedMs = i * 10_000L, speedKmh = 60, mafGramsPerSec = 8.0) }
        val result = MileageAnalyzer.analyze(samples, "Petrol")

        assertTrue((result.distanceKm ?: 0.0) > 4.0)
        assertTrue((result.fuelLitres ?: 0.0) > 0.0)
        val avg = result.averageKmPerLitre
        assertTrue(avg != null && avg > 0.0 && avg.isFinite())
    }

    @Test
    fun `idling with no distance never produces a divide-by-zero or infinite result`() {
        val samples = (0..10).map { i -> FuelSample(elapsedMs = i * 1000L, speedKmh = 0, mafGramsPerSec = 1.5) }
        val result = MileageAnalyzer.analyze(samples, "Petrol")
        assertNull(result.averageKmPerLitre)
    }
}
