package com.enginesense.app.dealerreport

import com.enginesense.app.ScanResult
import com.enginesense.app.vehicle.FaultKind
import com.enginesense.app.vehicle.OdometerMatch
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.PrePurchaseReport
import com.enginesense.app.vehicle.SensorRating
import com.enginesense.app.vehicle.VehicleSnapshot
import com.enginesense.app.vehicle.benchmark
import com.enginesense.app.vehicle.rate
import com.enginesense.app.vehiclelookup.RtoRecord
import com.enginesense.app.vehiclelookup.ValidityStatus
import java.time.LocalDate

/*
 * What page 1's "Key findings" says, worked out from the actual scan and RTO record — never a
 * fixed script. Also which engine readings are out of range and what to do about them.
 */

/** One line in Key findings. [good] = green ✓, false = amber/red !, null = neutral information. */
data class KeyFinding(val text: String, val good: Boolean?)

/** An engine reading outside its normal range at the scan, with what the dealer should do. */
data class ReadingAlert(val pid: PidId, val name: String, val valueText: String, val serious: Boolean, val action: String)

object DealerFindings {

    private val READING_NAMES = mapOf(
        PidId.COOLANT_TEMP_C to "Coolant temperature",
        PidId.ENGINE_OIL_TEMP_C to "Engine oil temperature",
        PidId.CONTROL_MODULE_VOLTAGE to "Battery / charging",
        PidId.SHORT_TERM_FUEL_TRIM_1 to "Short-term fuel trim",
        PidId.LONG_TERM_FUEL_TRIM_1 to "Long-term fuel trim"
    )

    /** Out-of-range readings, most serious first. Low coolant/oil temperature just means the engine was still warming up, so it isn't an alert. */
    fun readingAlerts(snapshot: VehicleSnapshot): List<ReadingAlert> = READING_NAMES.mapNotNull { (pid, name) ->
        val value = snapshot.verifiedValue(pid) ?: return@mapNotNull null
        val range = pid.benchmark()?.typicalRange ?: return@mapNotNull null
        val rating = pid.rate(value)
        if (rating != SensorRating.WATCH && rating != SensorRating.ATTENTION) return@mapNotNull null
        val high = value > range.endInclusive
        if (!high && (pid == PidId.COOLANT_TEMP_C || pid == PidId.ENGINE_OIL_TEMP_C)) return@mapNotNull null
        ReadingAlert(pid, name, valueText(pid, value), rating == SensorRating.ATTENTION, action(pid, high))
    }.sortedByDescending { it.serious }

    fun valueText(pid: PidId, value: Double): String = when (pid) {
        PidId.CONTROL_MODULE_VOLTAGE -> "%.1f V".format(value)
        PidId.SHORT_TERM_FUEL_TRIM_1, PidId.LONG_TERM_FUEL_TRIM_1 -> (if (value >= 0) "+" else "") + "%.1f%%".format(value)
        PidId.COOLANT_TEMP_C, PidId.ENGINE_OIL_TEMP_C -> "${value.toInt()}°C"
        else -> "${value.toInt()} ${pid.unit}".trim()
    }

    private fun action(pid: PidId, high: Boolean): String = when (pid) {
        PidId.COOLANT_TEMP_C -> "Running hot. Check the coolant level and for leaks, the radiator fan, thermostat and radiator cap, then re-scan after a 15-minute drive before the car is handed over."
        PidId.ENGINE_OIL_TEMP_C -> "Oil running hot. Check the oil level and grade and the cooling system, then re-scan after a drive."
        PidId.CONTROL_MODULE_VOLTAGE ->
            if (high) "Charging voltage too high. Have the alternator's voltage regulator tested — overcharging damages the battery."
            else "Low voltage. Test the battery and alternator; a weak battery can cause starting trouble and false fault codes."
        PidId.SHORT_TERM_FUEL_TRIM_1, PidId.LONG_TERM_FUEL_TRIM_1 ->
            if (high) "Engine running lean. Check for vacuum or intake leaks, a dirty air-flow sensor, or weak fuel pressure."
            else "Engine running rich. Check the injectors, oxygen sensor and air filter."
        else -> "Have a mechanic check this reading."
    }

    /**
     * Up to seven lines for page 1, problems first: faults, red flags, readings, odometer,
     * papers, self-tests. Each line is a fact from this scan or this RTO record.
     */
    fun keyFindings(result: ScanResult, report: PrePurchaseReport, today: LocalDate): List<KeyFinding> {
        val problems = mutableListOf<KeyFinding>()
        val good = mutableListOf<KeyFinding>()


        val faults = report.faults.filter { it.kind != FaultKind.EXTERIOR }
        if (faults.isEmpty()) good += KeyFinding("No fault codes — stored, hidden or developing", true)
        else {
            val cost = if (report.repairMaxInr > 0) " (est. ${shortRupees(report.repairMinInr)}–${shortRupees(report.repairMaxInr)})" else ""
            problems += KeyFinding("${faults.size} issue${if (faults.size == 1) "" else "s"} detected: " + faults.take(2).joinToString(", ") { it.code ?: it.title } + (if (faults.size > 2) " and more" else "") + cost, false)
        }

        if (report.redFlags.isEmpty()) good += KeyFinding("0 OBD red flags detected", true)
        else report.redFlags.take(2).forEach { problems += KeyFinding("OBD red flag: ${it.title}", false) }

        readingAlerts(result.snapshot).take(2).forEach { problems += KeyFinding("${it.name} ${it.valueText} — outside the normal range; see the recommended action", false) }

        when (report.odometer.match) {
            OdometerMatch.MATCHES -> good += KeyFinding("Odometer: dashboard matches the engine computer", true)
            OdometerMatch.SMALL_GAP -> problems += KeyFinding("Odometer: small gap between the dashboard and engine computer", false)
            OdometerMatch.MISMATCH -> problems += KeyFinding("Odometer: dashboard and engine computer differ", false)
            null -> Unit
        }

        report.rc?.let { rc ->
            val expired = RtoRecord.validity(rc, today).filter { it.status == ValidityStatus.EXPIRED }.map { it.label }
            if (expired.isNotEmpty()) problems += KeyFinding("Expired: ${expired.joinToString(", ")}", false)
            if (rc.financed == true) problems += KeyFinding("Loan on the car${rc.financer?.let { " ($it)" } ?: ""} — needs a bank NOC", false)
            // Name only what the record actually shows as valid — a PUC "not on record" isn't valid.
            val valid = RtoRecord.validity(rc, today).filter { it.status == ValidityStatus.VALID || it.status == ValidityStatus.LIFETIME || it.status == ValidityStatus.EXPIRES_SOON }.map { it.label }
            if (expired.isEmpty() && rc.financed != true && valid.isNotEmpty()) {
                good += KeyFinding("Papers valid: ${valid.joinToString(", ")}" + if (rc.financed == false) "; no loan" else "", true)
            }
        }

        result.readiness?.monitors?.filter { it.supported }?.takeIf { it.isNotEmpty() }?.let { monitors ->
            val ready = monitors.count { it.ready }
            if (ready == monitors.size) good += KeyFinding("All ${monitors.size} self-tests complete", true)
            else problems += KeyFinding("$ready of ${monitors.size} self-tests complete — codes may have been cleared recently", false)
        }

        return (problems + good).take(7)
    }

    private fun shortRupees(inr: Int): String =
        if (inr >= 1_00_000) "₹%.1f L".format(inr / 1_00_000.0) else "₹" + indianGrouping(inr.toLong())
}
