package com.enginesense.app.owner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VitalTrendsTest {

    private fun scan(day: Long, volts: Double? = null, temp: Double? = null, trim: Double? = null, codes: Int = 0) = VitalScan(
        day * 86_400_000L,
        listOfNotNull(volts?.let { Vital.CHARGING to it }, temp?.let { Vital.ENGINE_TEMP to it }, trim?.let { Vital.FUEL_MIX to it }).toMap(),
        codes
    )

    @Test
    fun aSteadyHealthyCarReadsSteady() {
        val t = VitalTrends.trends(listOf(scan(1, temp = 92.0), scan(20, temp = 91.0), scan(40, temp = 92.0))).single()
        assertEquals(Level.GOOD, t.level)
        assertEquals("Steady at 92°C", t.summary)
        assertNull(t.warning)
    }

    @Test
    fun aSlowDropTowardTheEdgeIsFlaggedWhileStillNormal() {
        val t = VitalTrends.trends(listOf(scan(1, volts = 14.3), scan(30, volts = 14.0), scan(60, volts = 13.8), scan(90, volts = 13.7))).single()
        assertEquals(Level.WATCH, t.level)
        assertEquals("14.3 V → 13.7 V", t.summary)
        assertTrue(t.warning!!.contains("slowly dropping"))
    }

    @Test
    fun aReadingThatLeftNormalAndCameBackIsMentioned() {
        val t = VitalTrends.trends(listOf(scan(1, volts = 14.2), scan(20, volts = 12.9), scan(40, volts = 14.2))).single()
        assertEquals(Level.GOOD, t.level)
        assertEquals("14.2 V now · went to 12.9 V once", t.summary)
    }

    @Test
    fun outsideNormalNeedsAttention() {
        val t = VitalTrends.trends(listOf(scan(1, trim = 3.0), scan(30, trim = 13.0))).single()
        assertEquals(Level.ATTENTION, t.level)
        assertTrue(t.warning!!.contains("adding fuel"))
    }

    @Test
    fun oneScanIsNotATrend() {
        assertTrue(VitalTrends.trends(listOf(scan(1, volts = 14.1, temp = 90.0))).isEmpty())
    }

    @Test
    fun sinceLastScanComparesTheTwoNewest() {
        val s = VitalTrends.sinceLastScan(listOf(scan(1, volts = 14.2, temp = 92.0), scan(30, volts = 13.9, temp = 92.0, codes = 1)), 1240.0)
        assertNotNull(s)
        assertEquals(30 * 86_400_000L - 29 * 86_400_000L, s!!.previousMillis)
        assertEquals("14.2 V → 13.9 V", s.changes.first { it.first == "Charging voltage" }.second)
        assertEquals("Same", s.changes.first { it.first == "Engine running temp" }.second)
        assertEquals("0 → 1", s.changes.first { it.first == "Fault codes" }.second)
        assertEquals("1,240 km", s.changes.first { it.first == "Km driven" }.second)
        assertTrue("Fault codes" in s.worse)
        assertNull(VitalTrends.sinceLastScan(listOf(scan(1, volts = 14.0)), null))
    }
}
