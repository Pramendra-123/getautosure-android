package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity

data class ForecastInsight(
    val pid: PidId,
    val message: String,
    val severity: Severity
)

/**
 * Premium's "Issue Forecast" — trend-based prediction from scan history, not a single
 * reading. Deliberately simple and explainable: compares the average of the older half of a
 * PID's readings against the newer half across the scans supplied, and only speaks up when
 * the shift is big enough to matter for that specific PID. No ML model, no invented
 * probability — just "this number has been moving in a direction worth knowing about."
 *
 * Needs at least 3 scans with a verified reading for a given PID before it says anything —
 * two points can't tell a trend from noise, and this never guesses to fill the gap.
 */
object IssueForecastAnalyzer {

    private const val MIN_POINTS = 3

    fun analyze(snapshotsNewestFirst: List<VehicleSnapshot>): List<ForecastInsight> {
        val chronological = snapshotsNewestFirst.reversed()
        return listOfNotNull(
            voltageTrend(chronological),
            coolantTrend(chronological),
            fuelTrimTrend(chronological)
        )
    }

    private fun voltageTrend(chronological: List<VehicleSnapshot>): ForecastInsight? {
        val points = chronological.mapNotNull { it.verifiedValue(PidId.CONTROL_MODULE_VOLTAGE) }
        if (points.size < MIN_POINTS) return null
        val delta = secondHalfAvg(points) - firstHalfAvg(points)
        if (delta > -0.3) return null
        return ForecastInsight(
            PidId.CONTROL_MODULE_VOLTAGE,
            "Battery/module voltage has been trending down over your last ${points.size} scans " +
                "(from %.1fV to %.1fV). Worth getting the battery and alternator checked before it leaves you stranded."
                    .format(firstHalfAvg(points), secondHalfAvg(points)),
            if (delta < -0.8) Severity.MODERATE else Severity.MINOR
        )
    }

    private fun coolantTrend(chronological: List<VehicleSnapshot>): ForecastInsight? {
        val points = chronological.mapNotNull { it.verifiedValue(PidId.COOLANT_TEMP_C) }
        if (points.size < MIN_POINTS) return null
        val delta = secondHalfAvg(points) - firstHalfAvg(points)
        if (delta < 8.0) return null
        return ForecastInsight(
            PidId.COOLANT_TEMP_C,
            "Coolant temperature has been running progressively hotter over your last ${points.size} scans " +
                "(from %.0f°C to %.0f°C). Worth checking coolant level and the cooling system before it becomes an overheat."
                    .format(firstHalfAvg(points), secondHalfAvg(points)),
            if (delta > 15.0) Severity.MODERATE else Severity.MINOR
        )
    }

    private fun fuelTrimTrend(chronological: List<VehicleSnapshot>): ForecastInsight? {
        val points = chronological.mapNotNull { it.verifiedValue(PidId.LONG_TERM_FUEL_TRIM_1)?.let { v -> kotlin.math.abs(v) } }
        if (points.size < MIN_POINTS) return null
        val delta = secondHalfAvg(points) - firstHalfAvg(points)
        if (delta < 3.0) return null
        return ForecastInsight(
            PidId.LONG_TERM_FUEL_TRIM_1,
            "Long-term fuel trim correction has been growing over your last ${points.size} scans " +
                "(from %.1f%% to %.1f%% off center). The computer is compensating for something more each time — worth a fuel-system check."
                    .format(firstHalfAvg(points), secondHalfAvg(points)),
            if (delta > 6.0) Severity.MODERATE else Severity.MINOR
        )
    }

    private fun firstHalfAvg(points: List<Double>): Double = points.take(points.size / 2).average()
    private fun secondHalfAvg(points: List<Double>): Double = points.drop(points.size / 2).average()
}
