package com.enginesense.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirFlowEstimatorTest {

    @Test
    fun `a measured MAF is used as-is and labeled measured`() {
        val flow = AirFlowEstimator.airFlow(mafGramsPerSec = 4.2, mapKpa = 30.0, rpm = 2000.0, intakeAirTempC = 30.0, engineCc = 1200)!!
        assertEquals(4.2, flow.gramsPerSec, 0.0)
        assertEquals(AirFlowSource.MEASURED, flow.source)
    }

    @Test
    fun `without MAF, speed-density gives the textbook figure and is labeled an estimate`() {
        // 1.0 TSI cruising as in a real Live Data reading: 24 kPa, 1743.5 rpm, 999 cc, 35 °C intake.
        // 24000 Pa × 0.000999 m³ × (1743.5/120) /s × 0.85 / (287.05 × 308.15) ≈ 3.35 g/s
        val flow = AirFlowEstimator.airFlow(null, mapKpa = 24.0, rpm = 1743.5, intakeAirTempC = 35.0, engineCc = 999)!!
        assertEquals(3.35, flow.gramsPerSec, 0.02)
        assertEquals(AirFlowSource.ESTIMATED_FROM_MAP, flow.source)
    }

    @Test
    fun `more manifold pressure or more rpm means more air`() {
        val cruise = AirFlowEstimator.speedDensityGramsPerSec(24.0, 1750.0, 35.0, 999)!!
        val boost = AirFlowEstimator.speedDensityGramsPerSec(150.0, 1750.0, 35.0, 999)!!
        val revving = AirFlowEstimator.speedDensityGramsPerSec(24.0, 3500.0, 35.0, 999)!!
        assertTrue(boost > cruise * 6)
        assertEquals(cruise * 2, revving, 0.001)
    }

    @Test
    fun `any missing input gives no estimate rather than a guess`() {
        assertNull(AirFlowEstimator.airFlow(null, null, 1750.0, 35.0, 999))
        assertNull(AirFlowEstimator.airFlow(null, 24.0, null, 35.0, 999))
        assertNull(AirFlowEstimator.airFlow(null, 24.0, 1750.0, null, 999))
        assertNull(AirFlowEstimator.airFlow(null, 24.0, 1750.0, 35.0, null))
        assertNull(AirFlowEstimator.airFlow(null, 24.0, 1750.0, 35.0, 0))
    }

    @Test
    fun `instant fuel use gives km per litre when moving and only litres per hour at idle`() {
        val cruising = MileageAnalyzer.instant(airGramsPerSec = 3.35, speedKmh = 35, fuelType = "Petrol")!!
        // 3.35 / 14.7 / 0.74 g/ml × 3.6 ≈ 1.109 L/h → 35 / 1.109 ≈ 31.6 km/L
        assertEquals(1.109, cruising.litresPerHour, 0.01)
        assertEquals(31.6, cruising.kmPerLitre!!, 0.3)

        val idle = MileageAnalyzer.instant(airGramsPerSec = 2.0, speedKmh = 0, fuelType = "Petrol")!!
        assertNull(idle.kmPerLitre)
        assertTrue(idle.litresPerHour > 0)
    }

    @Test
    fun `fuel types without a meaningful km per litre get nothing`() {
        assertNull(MileageAnalyzer.instant(3.0, 40, "CNG"))
        assertNull(MileageAnalyzer.instant(3.0, 40, null))
    }
}
