package com.enginesense.app.owner

import kotlin.math.roundToInt

/**
 * A short look at the two oxygen sensors while driving: how often each crossed its midpoint.
 * The front one switches all the time; behind a working catalytic converter the rear one stays
 * steady. A rear sensor switching like the front means the converter is gone or dead.
 */
data class O2Check(val timeMillis: Long, val frontSwitches: Int, val rearSwitches: Int) {
    private val enough: Boolean get() = frontSwitches >= MIN_FRONT_SWITCHES
    val converterWorking: Boolean get() = enough && rearSwitches <= frontSwitches * 0.3
    val rearFollowsFront: Boolean get() = enough && rearSwitches >= frontSwitches * 0.7

    fun toEventDetail(): String = "front=$frontSwitches;rear=$rearSwitches"

    companion object {
        const val EVENT = "o2_check"
        const val MIN_FRONT_SWITCHES = 3

        fun fromEvent(timeMillis: Long, detail: String): O2Check? {
            val parts = detail.split(";").mapNotNull { p -> p.split("=").takeIf { it.size == 2 }?.let { it[0] to it[1].toIntOrNull() } }.toMap()
            return O2Check(timeMillis, parts["front"] ?: return null, parts["rear"] ?: return null)
        }

        /** Midpoint crossings in a run of readings: 0.45 V for a normal sensor, λ 1.0 for a wide-band one. */
        fun switches(values: List<Double>, midpoint: Double): Int =
            values.zipWithNext().count { (a, b) -> (a - midpoint) * (b - midpoint) < 0 }
    }
}

/** What a scan tells us, pulled out of the scan result so the comparison stays pure and testable. */
data class ScanFacts(
    val timeMillis: Long,
    val score: Int? = null,
    val storedCodes: Set<String> = emptySet(),
    val pendingCodes: Set<String> = emptySet(),
    val kmSinceCleared: Double? = null,
    val minutesSinceCleared: Double? = null,
    val readyMonitors: Int? = null,
    val odometerKm: Double? = null,
    val cvns: List<String> = emptyList(),
    val calibrationId: String? = null,
    val catalystPassed: Boolean? = null,
    /** Cylinders with misfires counted on the latest drive; null when the car doesn't report them. */
    val misfireCylinders: Set<Int>? = null,
    val volts: Double? = null
) {
    val allCodes: Set<String> get() = storedCodes + pendingCodes
}

enum class ChangeKind { CONVERTER, CODES_CLEARED, ECU_CHANGED, NEW_CODES, MISFIRE }

/** One thing that happened between two scans, with the before → after that shows it. */
data class ScanChange(
    val kind: ChangeKind,
    val title: String,
    val detail: String,
    val level: Level,
    val evidence: List<Triple<String, String, String>>,
    val advice: String
)

data class ScanComparison(
    val previousMillis: Long,
    val previousScore: Int?,
    val kmSince: Double?,
    val kmRecorded: Double?,
    val changes: List<ScanChange>,
    /** What was checked and is the same as last time, for the "nothing changed" lines. */
    val same: List<String>
)

/**
 * "Since your last scan": every new scan against the car's previous one, so whatever happened in
 * between — codes cleared, the catalytic converter removed, the ECU remapped — is said in plain
 * words the next time the scanner is plugged in. It says what changed and roughly when, never who.
 * Pure, so it's unit-tested.
 */
object ScanChanges {

    private val CATALYST_CODES = setOf("P0420", "P0421", "P0430", "P0431")

    fun compare(prev: ScanFacts, curr: ScanFacts, o2Before: O2Check?, o2After: O2Check?, kmRecorded: Double?): ScanComparison {
        val changes = mutableListOf<ScanChange>()
        val same = mutableListOf<String>()

        // Catalytic converter: any one of three signs that it stopped working since last time.
        val evidence = mutableListOf<Triple<String, String, String>>()
        if (o2After?.rearFollowsFront == true && o2Before?.rearFollowsFront != true) {
            evidence += Triple("Sensor after the converter", if (o2Before?.converterWorking == true) "Steady" else "Not measured", "Swinging")
        }
        if (prev.catalystPassed == true && curr.catalystPassed == false) evidence += Triple("Converter self-test", "Passed", "Failed")
        val newCatCodes = curr.allCodes.intersect(CATALYST_CODES) - prev.allCodes
        newCatCodes.forEach { evidence += Triple("Code $it", "None", if (it in curr.storedCodes) "Stored" else "Pending") }
        if (evidence.isNotEmpty()) {
            changes += ScanChange(
                ChangeKind.CONVERTER, "Catalytic converter isn't working any more", "Removed or failed", Level.ATTENTION, evidence,
                "The converter is either removed or no longer cleaning the exhaust. Have someone look underneath the car. " +
                    "A replacement usually costs ₹20,000–₹1,00,000, and the car will fail PUC without one."
            )
        } else if (curr.catalystPassed == true || o2After?.converterWorking == true) {
            same += "Catalytic converter working"
        }

        // Codes cleared or battery disconnected: the "since cleared" counters went back to zero.
        val reset = (prev.kmSinceCleared != null && curr.kmSinceCleared != null && curr.kmSinceCleared < prev.kmSinceCleared - 1) ||
            (prev.minutesSinceCleared != null && curr.minutesSinceCleared != null && curr.minutesSinceCleared < prev.minutesSinceCleared - 1)
        if (reset) {
            val hadCodes = prev.allCodes.isNotEmpty()
            val ago = listOfNotNull(curr.minutesSinceCleared?.let { "about ${humanMinutes(it)} ago" }, curr.kmSinceCleared?.let { "%.0f km back".format(it) }).joinToString(", ")
            val notReady = if (prev.readyMonitors != null && curr.readyMonitors != null && curr.readyMonitors < prev.readyMonitors) {
                val n = prev.readyMonitors - curr.readyMonitors
                "$n self-test${if (n == 1) "" else "s"} not ready again"
            } else null
            val ev = mutableListOf<Triple<String, String, String>>()
            if (prev.kmSinceCleared != null && curr.kmSinceCleared != null) ev += Triple("Km since codes cleared", "%,.0f".format(prev.kmSinceCleared), "%,.0f".format(curr.kmSinceCleared))
            if (prev.readyMonitors != null && curr.readyMonitors != null) ev += Triple("Self-tests ready", "${prev.readyMonitors}", "${curr.readyMonitors}")
            if (hadCodes) ev += Triple("Fault codes", prev.allCodes.sorted().joinToString(", "), curr.allCodes.sorted().joinToString(", ").ifBlank { "None" })
            changes += ScanChange(
                ChangeKind.CODES_CLEARED,
                if (hadCodes) "Fault codes were cleared" else "The car's fault memory was reset",
                listOfNotNull(ago.replaceFirstChar { it.uppercase() }.ifBlank { null }, notReady).joinToString(". "),
                Level.WATCH, ev,
                if (hadCodes) "Clearing codes hides a problem; it doesn't fix it. If you didn't ask for it, the fault may come back — ask what work was done."
                else "This happens when codes are cleared with a scanner or the battery is disconnected. If you didn't have work done, ask who did."
            )
        } else if (prev.kmSinceCleared != null && curr.kmSinceCleared != null) {
            same += "No codes cleared"
        }

        // ECU software: its fingerprint (CVN) or calibration id is different.
        val cvnChanged = prev.cvns.isNotEmpty() && curr.cvns.isNotEmpty() && prev.cvns.toSet() != curr.cvns.toSet()
        val calChanged = prev.calibrationId != null && curr.calibrationId != null && prev.calibrationId != curr.calibrationId
        if (cvnChanged || calChanged) {
            val ev = mutableListOf<Triple<String, String, String>>()
            if (cvnChanged) ev += Triple("Software fingerprint (CVN)", prev.cvns.joinToString(", "), curr.cvns.joinToString(", "))
            if (calChanged) ev += Triple("Calibration", prev.calibrationId!!, curr.calibrationId!!)
            changes += ScanChange(
                ChangeKind.ECU_CHANGED, "ECU software changed", "A remap or a dealer update", Level.WATCH, ev,
                "A dealer software update can do this. If no one updated it, the engine may have been remapped — that can hide faults, raise emissions and affect your warranty."
            )
        } else if (prev.cvns.isNotEmpty() && curr.cvns.isNotEmpty()) {
            same += "ECU software the same"
        }

        // New fault codes (catalyst ones are already counted above).
        val newCodes = (curr.allCodes - prev.allCodes - CATALYST_CODES).sorted()
        if (newCodes.isNotEmpty()) {
            val stored = newCodes.any { it in curr.storedCodes }
            changes += ScanChange(
                ChangeKind.NEW_CODES,
                "${newCodes.size} new fault code${if (newCodes.size == 1) "" else "s"}", newCodes.joinToString(", "),
                if (stored) Level.ATTENTION else Level.WATCH,
                newCodes.map { Triple("Code $it", "None", if (it in curr.storedCodes) "Stored" else "Pending") },
                "Open the code to see what it means, how urgent it is and a tentative repair cost."
            )
        } else {
            same += "No new fault codes"
        }

        // Misfires on a cylinder that had none.
        val newMisfires = curr.misfireCylinders?.let { it - (prev.misfireCylinders ?: emptySet()) }.orEmpty().sorted()
        if (newMisfires.isNotEmpty()) {
            changes += ScanChange(
                ChangeKind.MISFIRE, "Misfires on cylinder ${newMisfires.joinToString(", ")}", "Counted by the car's own self-test", Level.WATCH,
                newMisfires.map { Triple("Cylinder $it", "No misfires", "Misfiring") },
                "Often a worn spark plug or ignition coil — cheap to fix early, costly if it damages the catalytic converter."
            )
        } else if (curr.misfireCylinders != null) {
            same += "No misfires"
        }

        curr.volts?.takeIf { it in 12.0..15.0 }?.let { same.add(0, "Battery and charging · %.1f V".format(it)) }

        val kmSince = when {
            prev.odometerKm != null && curr.odometerKm != null -> curr.odometerKm - prev.odometerKm
            !reset && prev.kmSinceCleared != null && curr.kmSinceCleared != null -> curr.kmSinceCleared - prev.kmSinceCleared
            else -> null
        }?.takeIf { it >= 0 }
        return ScanComparison(prev.timeMillis, prev.score, kmSince, kmRecorded, changes.sortedByDescending { it.level == Level.ATTENTION }, same)
    }

    fun humanMinutes(minutes: Double): String {
        val m = minutes.roundToInt()
        return when {
            m < 90 -> "$m minutes"
            m < 36 * 60 -> "${(m / 60.0).roundToInt()} hours"
            else -> "${(m / 1440.0).roundToInt()} days"
        }
    }
}
