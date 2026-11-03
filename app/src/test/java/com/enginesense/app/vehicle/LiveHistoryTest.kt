package com.enginesense.app.vehicle

import com.enginesense.app.ui.components.graphBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveHistoryTest {

    private fun verified(pid: PidId, v: Double) = pid to PidReading(pid, PidStatus.VERIFIED, v, null)

    @Test
    fun `keeps only the last window of readings and only what the car actually sent`() {
        val history = LiveHistory(windowMs = 1_000)
        history.add(mapOf(verified(PidId.RPM, 800.0)), 0)
        history.add(mapOf(verified(PidId.RPM, 900.0), PidId.SPEED_KMH to PidReading(PidId.SPEED_KMH, PidStatus.UNSUPPORTED, null, null)), 600)
        history.add(mapOf(verified(PidId.RPM, 1000.0)), 1_200)

        val snap = history.snapshot()
        assertEquals(1_200, snap.nowMs)
        // 0 ms fell out of the 1 s window; 600 and 1200 remain.
        assertEquals(listOf(900.0, 1000.0), snap.series.getValue(PidId.RPM).map { it.value })
        assertNull(snap.series[PidId.SPEED_KMH])
    }

    @Test
    fun `a slow-polled sensor isn't padded with repeats between its real readings`() {
        val history = LiveHistory()
        history.add(mapOf(verified(PidId.COOLANT_TEMP_C, 60.0), verified(PidId.RPM, 800.0)), 0)
        history.add(mapOf(verified(PidId.RPM, 820.0)), 600)
        history.add(mapOf(verified(PidId.RPM, 840.0)), 1_200)
        assertEquals(1, history.snapshot().series.getValue(PidId.COOLANT_TEMP_C).size)
        assertEquals(3, history.snapshot().series.getValue(PidId.RPM).size)
    }

    @Test
    fun `status chip matches the reports' wording`() {
        assertEquals(LiveStatus.NORMAL, liveStatus(PidId.CONTROL_MODULE_VOLTAGE, 14.1))
        assertEquals(LiveStatus.CHECK, liveStatus(PidId.CONTROL_MODULE_VOLTAGE, 11.0))
        assertEquals(LiveStatus.WARMING_UP, liveStatus(PidId.COOLANT_TEMP_C, 50.0))
        assertEquals(LiveStatus.NORMAL, liveStatus(PidId.COOLANT_TEMP_C, 90.0))
        assertEquals(LiveStatus.WATCH, liveStatus(PidId.LONG_TERM_FUEL_TRIM_1, 11.7))
        assertEquals(LiveStatus.TYPICAL, liveStatus(PidId.RPM, 838.0))
        assertEquals(LiveStatus.VARIES_WITH_DRIVING, liveStatus(PidId.RPM, 3_200.0)) // revving is not a fault
        assertNull(liveStatus(PidId.TIMING_ADVANCE, 5.0))
    }

    @Test
    fun `a steady reading gets a calm axis, and the normal band always fits`() {
        val steady = listOf(LiveSample(0, 14.10), LiveSample(600, 14.12))
        val (lo, hi) = graphBounds(steady, null, minSpan = 1.5)
        assertTrue(hi - lo >= 1.5)
        assertTrue(lo < 14.10 && hi > 14.12)

        val (bandLo, bandHi) = graphBounds(listOf(LiveSample(0, 50.0)), 80.0..105.0, minSpan = 15.0)
        assertTrue(bandLo < 50.0 && bandHi > 105.0)
    }
}
