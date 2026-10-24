package com.enginesense.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerformanceAnalyzerTest {

    @Test
    fun `empty run returns all nulls, never a guessed number`() {
        val result = PerformanceAnalyzer.analyze(emptyList())
        assertNull(result.zeroToSixtyMs)
        assertNull(result.zeroToSixtyDistanceM)
        assertNull(result.sixtyToZeroMs)
        assertNull(result.hundredMetreMs)
    }

    @Test
    fun `measures a clean 0-60 run and its distance`() {
        // Linear ramp 0 -> 80 km/h over 5000ms, one sample per 500ms.
        val samples = (0..10).map { i -> SpeedSample(elapsedMs = i * 500L, speedKmh = i * 8) }
        val result = PerformanceAnalyzer.analyze(samples)

        // Crosses 60 km/h at i=8 (speed 64) -> elapsed 4000ms from start (elapsed 0).
        assertEquals(4000L, result.zeroToSixtyMs)
        assertEquals(true, (result.zeroToSixtyDistanceM ?: 0.0) > 0.0)
    }

    @Test
    fun `measures a braking event from 60 back to standstill`() {
        val samples = listOf(
            SpeedSample(0, 0), SpeedSample(1000, 65), SpeedSample(2000, 65),
            SpeedSample(3000, 30), SpeedSample(3500, 0)
        )
        val result = PerformanceAnalyzer.analyze(samples)

        // Start of braking window is the FIRST sample reaching >=60 km/h (elapsed 1000),
        // end is the next standstill (elapsed 3500) -> 2500ms.
        assertEquals(2500L, result.sixtyToZeroMs)
        assertEquals(true, (result.sixtyToZeroDistanceM ?: 0.0) > 0.0)
    }

    @Test
    fun `never reaching 60 km-h leaves the acceleration result null, not a guess`() {
        val samples = listOf(SpeedSample(0, 0), SpeedSample(1000, 20), SpeedSample(2000, 40))
        val result = PerformanceAnalyzer.analyze(samples)
        assertNull(result.zeroToSixtyMs)
        assertNull(result.zeroToSixtyDistanceM)
    }

    @Test
    fun `100m time is measured once integrated distance crosses 100 metres`() {
        // Standstill, then a 1s ramp to 72 km/h, then holding 72 km/h (20 m/s). Trapezoidal
        // distance per second: 10m (the ramp, averaging 36 km/h) then 20m/s after that ->
        // cumulative 10/30/50/70/90/110 at t=1000/2000/.../6000 -> crosses 100m at t=6000,
        // reported as that sample's time (this is a coarse per-sample reading, not
        // interpolated to the exact instant within the segment).
        val samples = listOf(
            SpeedSample(0, 0), SpeedSample(1000, 72), SpeedSample(2000, 72),
            SpeedSample(3000, 72), SpeedSample(4000, 72), SpeedSample(5000, 72), SpeedSample(6000, 72)
        )
        val result = PerformanceAnalyzer.analyze(samples)
        assertEquals(6000L, result.hundredMetreMs)
    }

    @Test
    fun `out-of-order samples are sorted before analysis`() {
        val samples = listOf(SpeedSample(2000, 65), SpeedSample(0, 0), SpeedSample(1000, 70))
        val result = PerformanceAnalyzer.analyze(samples)
        assertEquals(1000L, result.zeroToSixtyMs)
    }
}
