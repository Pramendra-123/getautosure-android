package com.enginesense.app.owner

import com.enginesense.app.vehicle.FreezeFrame
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.describe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarIntegrityTest {

    private val day = 86_400_000L
    private fun scan(n: Int, km: Double, cvn: String = "1A2B3C4D") = ScanFacts(
        timeMillis = n * 10 * day, kmSinceCleared = km, minutesSinceCleared = km * 2, readyMonitors = 5, supportedMonitors = 5,
        odometerKm = 48_000.0 + km, cvns = listOf(cvn), catalystPassed = true
    )

    @Test
    fun aWellKeptCarPassesEveryCheck() {
        val rows = CarIntegrity.rows(listOf(scan(1, 17_000.0), scan(2, 17_800.0), scan(3, 18_400.0)), null, 66_400)
        assertEquals(listOf("✓ Untouched", "✓ None", "✓ Working", "✓ Ready", "✓ Matches"), rows.map { it.verdict })
        assertTrue(rows.first().result.startsWith("Same on all 3 scans"))
        assertEquals("Not cleared in 18,400 km / 25 days", rows[1].result)
    }

    @Test
    fun tamperingShows() {
        val rows = CarIntegrity.rows(listOf(scan(1, 17_000.0), scan(2, 40.0, cvn = "DEADBEEF")), O2Check(0, 6, 6), 30_000)
        assertEquals("Changed", rows[0].verdict)
        assertEquals("Cleared", rows[1].verdict)
        assertEquals("Mismatch", rows.last().verdict)
        assertTrue(rows.none { it.verdict == "✓ Untouched" })
    }

    @Test
    fun aFirstScanJustAfterClearingIsFlagged() {
        val rows = CarIntegrity.rows(listOf(scan(1, 35.0).copy(cvns = emptyList(), catalystPassed = null, odometerKm = null)), null, null)
        assertEquals(listOf("Recently cleared", "✓ Ready"), rows.map { it.verdict })
    }

    @Test
    fun freezeFrameReadsInPlainWords() {
        val ff = FreezeFrame(
            "P0302",
            mapOf(PidId.RPM to 2800.0, PidId.SPEED_KMH to 64.0, PidId.COOLANT_TEMP_C to 92.0, PidId.ENGINE_LOAD to 71.0, PidId.SHORT_TERM_FUEL_TRIM_1 to 1.0, PidId.LONG_TERM_FUEL_TRIM_1 to 3.0, PidId.RUN_TIME_SINCE_START to 1380.0)
        )
        assertEquals("2,800 rpm · 64 km/h · engine hot 92°C · under load 71% · fuel mix +4% · 23 min after starting", ff.describe())
    }
}
