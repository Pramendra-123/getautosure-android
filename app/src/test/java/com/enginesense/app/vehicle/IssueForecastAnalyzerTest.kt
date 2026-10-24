package com.enginesense.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueForecastAnalyzerTest {

    private fun snapshot(vararg readings: Pair<PidId, Double>) = VehicleSnapshot(
        0L, null, readings.associate { (pid, value) -> pid to PidReading(pid, PidStatus.VERIFIED, value, null) }, emptyList()
    )

    @Test
    fun `fewer than 3 scans with the PID produces no forecast at all`() {
        val newestFirst = listOf(
            snapshot(PidId.CONTROL_MODULE_VOLTAGE to 12.0),
            snapshot(PidId.CONTROL_MODULE_VOLTAGE to 13.0)
        )
        assertTrue(IssueForecastAnalyzer.analyze(newestFirst).isEmpty())
    }

    @Test
    fun `declining voltage across scans produces a forecast`() {
        // Newest first, as GetAutoSureViewModel.history stores it: most recent scan shows the
        // lowest voltage, oldest shows the highest -> a real decline once put in chronological order.
        val newestFirst = listOf(
            snapshot(PidId.CONTROL_MODULE_VOLTAGE to 12.0),
            snapshot(PidId.CONTROL_MODULE_VOLTAGE to 12.5),
            snapshot(PidId.CONTROL_MODULE_VOLTAGE to 13.8),
            snapshot(PidId.CONTROL_MODULE_VOLTAGE to 14.2)
        )
        val insights = IssueForecastAnalyzer.analyze(newestFirst)
        val voltage = insights.first { it.pid == PidId.CONTROL_MODULE_VOLTAGE }
        assertTrue(voltage.message.contains("trending down"))
    }

    @Test
    fun `stable voltage across scans produces no forecast`() {
        val newestFirst = listOf(
            snapshot(PidId.CONTROL_MODULE_VOLTAGE to 14.1),
            snapshot(PidId.CONTROL_MODULE_VOLTAGE to 14.0),
            snapshot(PidId.CONTROL_MODULE_VOLTAGE to 14.2),
            snapshot(PidId.CONTROL_MODULE_VOLTAGE to 14.1)
        )
        assertTrue(IssueForecastAnalyzer.analyze(newestFirst).none { it.pid == PidId.CONTROL_MODULE_VOLTAGE })
    }

    @Test
    fun `rising coolant baseline across scans produces a forecast`() {
        val newestFirst = listOf(
            snapshot(PidId.COOLANT_TEMP_C to 108.0),
            snapshot(PidId.COOLANT_TEMP_C to 104.0),
            snapshot(PidId.COOLANT_TEMP_C to 91.0),
            snapshot(PidId.COOLANT_TEMP_C to 89.0)
        )
        val insights = IssueForecastAnalyzer.analyze(newestFirst)
        assertTrue(insights.any { it.pid == PidId.COOLANT_TEMP_C })
    }

    @Test
    fun `growing fuel trim magnitude across scans produces a forecast`() {
        val newestFirst = listOf(
            snapshot(PidId.LONG_TERM_FUEL_TRIM_1 to 12.0),
            snapshot(PidId.LONG_TERM_FUEL_TRIM_1 to 9.0),
            snapshot(PidId.LONG_TERM_FUEL_TRIM_1 to 2.0),
            snapshot(PidId.LONG_TERM_FUEL_TRIM_1 to 1.0)
        )
        val insights = IssueForecastAnalyzer.analyze(newestFirst)
        assertTrue(insights.any { it.pid == PidId.LONG_TERM_FUEL_TRIM_1 })
    }

    @Test
    fun `unrelated PIDs having no data does not crash or produce a forecast for them`() {
        val newestFirst = listOf(snapshot(), snapshot(), snapshot())
        assertEquals(emptyList<ForecastInsight>(), IssueForecastAnalyzer.analyze(newestFirst))
    }
}
