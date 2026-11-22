package com.enginesense.app.owner

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One line of "Car integrity": the check, what was found, and the short verdict for the chip. */
data class IntegrityRow(val check: String, val result: String, val verdict: String, val level: Level)

/** Everything the owner report needs from the car's history, beyond this one scan. */
data class ReportHistory(
    val changes: ScanComparison?,
    val integrity: List<IntegrityRow>,
    val vitals: List<VitalTrend>,
    val scanCount: Int,
    val firstScanMillis: Long?
)

/**
 * "Car integrity": checks that show the car hasn't been tampered with — ECU software, cleared
 * codes, the catalytic converter, emissions self-tests and the odometer — across every scan of
 * the car. A row appears only when the car reported what it needs. Pure, so it's unit-tested.
 */
object CarIntegrity {

    private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    /** Codes cleared this recently are worth saying even with nothing to compare against. */
    private const val RECENT_CLEAR_KM = 200.0
    private const val ODOMETER_TOLERANCE_KM = 500.0

    private fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DAY)

    /** [scans] oldest first; [dashboardKm] is the odometer the owner entered, if any. */
    fun rows(scans: List<ScanFacts>, latestO2: O2Check?, dashboardKm: Int?): List<IntegrityRow> {
        if (scans.isEmpty()) return emptyList()
        val last = scans.last()
        val rows = mutableListOf<IntegrityRow>()

        val withCvn = scans.filter { it.cvns.isNotEmpty() }
        if (withCvn.isNotEmpty()) {
            val change = withCvn.zipWithNext().lastOrNull { (a, b) -> a.cvns.toSet() != b.cvns.toSet() }
            rows += when {
                change != null -> IntegrityRow("ECU software (CVN fingerprint)", "Changed between ${day(change.first.timeMillis)} and ${day(change.second.timeMillis)}", "Changed", Level.WATCH)
                withCvn.size == 1 -> IntegrityRow("ECU software (CVN fingerprint)", "Recorded today · ${last.cvns.joinToString(", ")}", "Baseline", Level.GOOD)
                else -> IntegrityRow("ECU software (CVN fingerprint)", "Same on all ${withCvn.size} scans · ${withCvn.last().cvns.joinToString(", ")}", "✓ Untouched", Level.GOOD)
            }
        }

        val resets = scans.zipWithNext().filter { (a, b) ->
            (a.kmSinceCleared != null && b.kmSinceCleared != null && b.kmSinceCleared < a.kmSinceCleared - 1) ||
                (a.minutesSinceCleared != null && b.minutesSinceCleared != null && b.minutesSinceCleared < a.minutesSinceCleared - 1)
        }
        val km = last.kmSinceCleared
        val days = last.minutesSinceCleared?.let { (it / 1440).toInt() }
        val since = listOfNotNull(km?.let { "%,.0f km".format(it) }, days?.let { "$it days" }).joinToString(" / ")
        when {
            resets.isNotEmpty() -> rows += IntegrityRow(
                "Fault codes cleared", "Cleared ${resets.size} time${if (resets.size == 1) "" else "s"} · last before ${day(resets.last().second.timeMillis)}", "Cleared", Level.WATCH
            )
            km != null && km < RECENT_CLEAR_KM -> rows += IntegrityRow("Fault codes cleared", "Cleared recently · only $since ago", "Recently cleared", Level.WATCH)
            since.isNotEmpty() -> rows += IntegrityRow("Fault codes cleared", "Not cleared in $since", "✓ None", Level.GOOD)
        }

        val converter = last.catalystPassed ?: latestO2?.let { if (it.rearFollowsFront) false else if (it.converterWorking) true else null }
        converter?.let {
            rows += if (it) IntegrityRow("Catalytic converter", "Self-test passed · rear sensor steady", "✓ Working", Level.GOOD)
            else IntegrityRow("Catalytic converter", "Not cleaning the exhaust · removed or failed", "Not working", Level.ATTENTION)
        }

        if (last.readyMonitors != null && last.supportedMonitors != null && last.supportedMonitors > 0) {
            val all = last.readyMonitors == last.supportedMonitors
            rows += IntegrityRow("Emissions self-tests", "${last.readyMonitors} of ${last.supportedMonitors} ready", if (all) "✓ Ready" else "Not ready", if (all) Level.GOOD else Level.WATCH)
        }

        last.odometerKm?.let { ecu ->
            rows += when {
                dashboardKm == null -> IntegrityRow("Odometer (car's computer)", "%,.0f km · dashboard not entered".format(ecu), "Compare", Level.UNKNOWN)
                kotlin.math.abs(ecu - dashboardKm) <= maxOf(ODOMETER_TOLERANCE_KM, ecu * 0.02) ->
                    IntegrityRow("Odometer (car's computer)", "%,.0f km · dashboard %,d km".format(ecu, dashboardKm), "✓ Matches", Level.GOOD)
                else -> IntegrityRow("Odometer (car's computer)", "%,.0f km · dashboard %,d km".format(ecu, dashboardKm), "Mismatch", Level.ATTENTION)
            }
        }
        return rows
    }
}
