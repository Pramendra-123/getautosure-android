package com.enginesense.app.vehicle

import com.enginesense.app.vehicle.ai.RepairCostEntry
import com.enginesense.app.vehicle.ai.RepairCostReference
import kotlin.math.abs
import kotlin.math.sqrt

/** One reading of the live data during the drive. Any value can be missing — cars report different PIDs. */
data class DriveSample(
    val tMs: Long,
    val speedKmh: Double? = null,
    val rpm: Double? = null,
    val coolantC: Double? = null,
    val shortTrim: Double? = null,
    val longTrim: Double? = null,
    val loadPct: Double? = null,
    val throttlePct: Double? = null,
    val volts: Double? = null
)

/** The five legs of the route. Each tests one thing; the buyer can move on any time. */
enum class DriveLeg(val title: String, val tests: String, val instruction: String, val instructionHi: String) {
    TRAFFIC(
        "Slow traffic, about 3 min", "Engine heat, idle, AC under load",
        "Turn the AC on and drive slowly for about three minutes. I'm watching the engine temperature and the idle.",
        "AC चालू करके करीब तीन मिनट धीरे चलाइए। मैं इंजन का तापमान और आइडल देख रहा हूँ।"
    ),
    GENTLE_PULL(
        "Gentle pull from 20 to 60", "Gear changes, gearbox slip",
        "When it's safe, slow to 20 and gently speed up to 60. Keep your foot off the clutch. I'm watching every gear change.",
        "जब सुरक्षित हो, 20 तक धीमे होकर आराम से 60 तक बढ़ाइए। क्लच पर पैर मत रखिए। मैं हर गियर बदलना देख रहा हूँ।"
    ),
    HARD_PULL(
        "One hard acceleration", "Misfires, power, turbo",
        "On a clear, straight road, press the accelerator hard once, up to about 60.",
        "खाली, सीधी सड़क पर एक बार ज़ोर से एक्सीलरेटर दबाइए, करीब 60 तक।"
    ),
    CRUISE(
        "Steady at 60 to 80", "Fuel mixture, engine temperature",
        "Hold a steady 60 to 80 for about a minute.",
        "करीब एक मिनट 60 से 80 पर एक जैसी स्पीड रखिए।"
    ),
    LISTEN(
        "Firm stop, full-lock U-turn, reverse", "You listen: brakes, steering, suspension",
        "Now a firm stop, a full-lock U-turn and a short reverse. Listen, and tap a button if you hear anything.",
        "अब एक ज़ोरदार ब्रेक, पूरा स्टीयरिंग घुमाकर U-टर्न और थोड़ा रिवर्स। ध्यान से सुनिए, कोई आवाज़ आए तो बटन दबाइए।"
    )
}

/** What the buyer can report hearing — OBD can't hear, so these are the buyer's own observations. */
enum class HeardNoise(val label: String, val title: String, val costCategory: String) {
    BRAKES("Brake noise", "You heard brake noise", "Brake pads (front pair)"),
    KNOCK("Knock / clunk", "You heard a knock or clunk over bumps", "Suspension (shock absorber pair)"),
    STEERING("Steering noise", "You heard a noise when turning", "CV joint / drive shaft")
}

enum class DriveCheck { GOOD, WATCH }

/** One line of "How it drove". [cost] is set for WATCH lines with a tentative repair range. */
data class DriveFinding(
    val title: String,
    val detail: String,
    val check: DriveCheck,
    val cost: RepairCostEntry? = null,
    /** Serious enough that a "good buy" becomes "buy with caution" until it's checked. */
    val serious: Boolean = false
)

/** A live message from the co-driver: what it saw, in English and Hindi. */
data class DriveEvent(val tMs: Long, val text: String, val textHi: String, val check: DriveCheck)

data class DriveResult(
    val durationS: Int,
    val distanceKm: Double,
    val legsDone: Int,
    val score: Int,
    val headline: String,
    val findings: List<DriveFinding>,
    val coldStart: Boolean
)

/**
 * Turns the live readings of a test drive into events and a result. Our own rules on live data —
 * not AI — so the same drive gives the same answer. Thresholds are deliberately conservative:
 * a single blip is never a finding, and everything is "worth checking", never a diagnosis.
 */
class DriveAnalyzer(private val automatic: Boolean?) {
    private val samples = ArrayList<DriveSample>()
    private var lastSlipMs = -SLIP_DEBOUNCE_MS
    private var slips = 0
    private var maxCoolant: Double? = null
    private var overheatSaid = false
    private var distanceKm = 0.0
    private val heard = linkedSetOf<HeardNoise>()
    var legsDone = 0
        private set

    val firstCoolant: Double? get() = samples.firstNotNullOfOrNull { it.coolantC }

    fun legDone(count: Int) { legsDone = maxOf(legsDone, count) }
    fun heard(noise: HeardNoise) { heard += noise }

    fun add(s: DriveSample): List<DriveEvent> {
        val prev = samples.lastOrNull()
        if (prev != null && s.speedKmh != null && prev.speedKmh != null) {
            val hours = (s.tMs - prev.tMs).coerceIn(0, 5_000) / 3_600_000.0
            distanceKm += (s.speedKmh + prev.speedKmh) / 2 * hours
        }
        samples += s
        val events = mutableListOf<DriveEvent>()

        s.coolantC?.let { c ->
            maxCoolant = maxOf(maxCoolant ?: c, c)
            if (c >= OVERHEAT_C && !overheatSaid) {
                overheatSaid = true
                events += DriveEvent(s.tMs, "Engine is at ${c.toInt()} °C, hotter than it should be. Pull over if it keeps rising.",
                    "इंजन ${c.toInt()} °C पर है, जितना होना चाहिए उससे ज़्यादा। बढ़ता रहे तो गाड़ी रोक दीजिए।", DriveCheck.WATCH)
            }
        }
        slipAt(s)?.let { rise ->
            slips++
            lastSlipMs = s.tMs
            events += DriveEvent(
                s.tMs,
                "RPM jumped ${rise.toInt()} without the car speeding up. That can be the ${if (automatic == false) "clutch" else "gearbox"} slipping. " +
                    if (slips == 1) "Do the same pull once more so I can check." else "That's the second time.",
                "RPM ${rise.toInt()} बढ़ा पर गाड़ी की स्पीड नहीं बढ़ी। ${if (automatic == false) "क्लच" else "गियरबॉक्स"} स्लिप हो सकता है। " +
                    if (slips == 1) "एक बार फिर वैसे ही बढ़ाइए ताकि मैं जाँच सकूँ।" else "यह दूसरी बार हुआ है।",
                DriveCheck.WATCH
            )
        }
        return events
    }

    /** An RPM rise of [SLIP_RPM_RISE]+ within [SLIP_WINDOW_MS] while the speed barely moved, above walking pace. */
    private fun slipAt(s: DriveSample): Double? {
        if (s.tMs - lastSlipMs < SLIP_DEBOUNCE_MS) return null
        val rpm = s.rpm ?: return null
        val speed = s.speedKmh ?: return null
        if (speed < SLIP_MIN_SPEED) return null
        // A hard press makes an automatic kick down a gear — RPM jumps on purpose. Only gentle pulls count.
        if ((s.throttlePct ?: 0.0) > SLIP_MAX_THROTTLE) return null
        val earlier = samples.asReversed().drop(1).takeWhile { s.tMs - it.tMs <= SLIP_WINDOW_MS }
            .filter { it.rpm != null && it.speedKmh != null && it.speedKmh >= SLIP_MIN_SPEED }
        val base = earlier.minByOrNull { it.rpm!! } ?: return null
        val rise = rpm - base.rpm!!
        return if (rise >= SLIP_RPM_RISE && abs(speed - base.speedKmh!!) <= SLIP_MAX_SPEED_GAIN) rise else null
    }

    fun result(): DriveResult {
        val findings = mutableListOf<DriveFinding>()
        val durationS = ((samples.lastOrNull()?.tMs ?: 0) - (samples.firstOrNull()?.tMs ?: 0)).toInt() / 1000

        // Gearbox / clutch: two slips is a finding, one is noise or a foot on the clutch.
        when {
            slips >= 2 -> findings += DriveFinding(
                if (automatic == false) "Clutch slipped $slips times" else "Gearbox slipped $slips times",
                "RPM jumped without the car speeding up. " + if (automatic == false)
                    "On a manual it usually means a worn clutch." else "On an automatic it can be worn clutch packs, low fluid or a solenoid. Get it checked before you pay.",
                DriveCheck.WATCH,
                if (automatic == false) cost("Clutch replacement") else AUTO_GEARBOX_CHECK,
                serious = true
            )
            samples.any { (it.speedKmh ?: 0.0) >= 40 } -> findings += DriveFinding("Gear changes smooth", "No slipping seen.", DriveCheck.GOOD)
        }

        // Cooling.
        maxCoolant?.let { m ->
            findings += if (m >= OVERHEAT_C) DriveFinding("Ran hot: up to ${m.toInt()} °C", "Most engines stay near 85–100 °C. A cooling-system fault can wreck the engine.", DriveCheck.WATCH, cost("Radiator / cooling system"), serious = true)
            else if (m >= 75) DriveFinding("Engine temperature normal", "Stayed at or under ${m.toInt()} °C.", DriveCheck.GOOD)
            else DriveFinding("Engine didn't fully warm up", "Highest was ${m.toInt()} °C. Drive longer to check overheating.", DriveCheck.WATCH)
        }

        // Fuel mixture at a steady cruise.
        val cruise = samples.filter { (it.speedKmh ?: 0.0) >= 40 && it.shortTrim != null && it.longTrim != null }
        if (cruise.size >= MIN_CRUISE_SAMPLES) {
            val trim = cruise.map { it.shortTrim!! + it.longTrim!! }.average()
            findings += when {
                trim > TRIM_LIMIT -> DriveFinding("Running lean at steady speed", "Fuel trim +${trim.toInt()}%. Often a vacuum leak or a dirty air-flow sensor.", DriveCheck.WATCH, cost("Vacuum leak repair"))
                trim < -TRIM_LIMIT -> DriveFinding("Running rich at steady speed", "Fuel trim ${trim.toInt()}%. Often a leaking injector or a sensor.", DriveCheck.WATCH, RepairCostReference.fallbackFor("P0172"))
                else -> DriveFinding("Fuel mixture normal", "Fuel trim ${if (trim >= 0) "+" else ""}${trim.toInt()}% at steady speed.", DriveCheck.GOOD)
            }
        }

        // Idle when warm: a wobbling idle is the usual sign of a misfire.
        val idle = samples.filter { (it.speedKmh ?: 1.0) == 0.0 && it.rpm != null && (it.coolantC ?: 0.0) >= 70 }.map { it.rpm!! }
        if (idle.size >= MIN_IDLE_SAMPLES) {
            val mean = idle.average()
            val sd = sqrt(idle.map { (it - mean) * (it - mean) }.average())
            findings += if (sd > IDLE_WOBBLE_RPM) DriveFinding("Idle is unsteady", "RPM wobbles by about ±${sd.toInt()} at idle. Often a plug, coil or a dirty throttle.", DriveCheck.WATCH, RepairCostReference.fallbackFor("P0300"))
            else DriveFinding("Idle steady", "About ${mean.toInt()} rpm, ±${sd.toInt()}.", DriveCheck.GOOD)
        }

        // Charging while the engine runs.
        val volts = samples.filter { (it.rpm ?: 0.0) > 600 }.mapNotNull { it.volts }
        if (volts.size >= 5) {
            val v = volts.average()
            findings += when {
                v < 13.0 -> DriveFinding("Battery isn't charging well", "%.1f V with the engine running; it should be 13.5–14.7 V.".format(v), DriveCheck.WATCH, cost("Alternator"))
                v > 15.2 -> DriveFinding("Charging voltage too high", "%.1f V with the engine running; it should be 13.5–14.7 V.".format(v), DriveCheck.WATCH, cost("Alternator"))
                else -> DriveFinding("Charging normal", "%.1f V with the engine running.".format(v), DriveCheck.GOOD)
            }
        }

        heard.forEach { n -> findings += DriveFinding(n.title, "What you heard; OBD can't hear. Get it looked at.", DriveCheck.WATCH, cost(n.costCategory)) }

        val watch = findings.count { it.check == DriveCheck.WATCH }
        val serious = findings.any { it.serious }
        val score = when {
            serious && watch >= 3 -> 2
            serious -> 3
            watch >= 2 -> 3
            watch == 1 -> 4
            else -> 5
        }
        val headline = when {
            findings.isEmpty() -> "Not enough data from the drive to say."
            serious -> "Drives, but get this checked: " + findings.first { it.serious }.title.replaceFirstChar { it.lowercase() } + "."
            watch > 0 -> "Drives well, with ${if (watch == 1) "one thing" else "$watch things"} to ask about."
            else -> "Drives well. Nothing worrying found."
        }
        return DriveResult(durationS, distanceKm, legsDone, if (findings.isEmpty()) 0 else score, headline, findings, coldStart = (firstCoolant ?: 99.0) < COLD_START_C)
    }

    companion object {
        const val SLIP_RPM_RISE = 500.0
        const val SLIP_WINDOW_MS = 1_500L
        const val SLIP_MAX_SPEED_GAIN = 2.0
        const val SLIP_MIN_SPEED = 15.0
        const val SLIP_DEBOUNCE_MS = 5_000L
        const val SLIP_MAX_THROTTLE = 60.0
        const val OVERHEAT_C = 108.0
        const val COLD_START_C = 45.0
        const val TRIM_LIMIT = 12.0
        const val MIN_CRUISE_SAMPLES = 20
        const val MIN_IDLE_SAMPLES = 20
        const val IDLE_WOBBLE_RPM = 80.0

        /** No single table row fits an automatic that slips: it runs from a fluid change to clutch packs. */
        val AUTO_GEARBOX_CHECK = RepairCostEntry("Automatic gearbox check and repair", 15_000, 60_000, "Fluid and solenoid up to clutch packs; a workshop check first", emptyList())

        private fun cost(category: String): RepairCostEntry =
            RepairCostReference.entries.firstOrNull { it.category == category } ?: RepairCostReference.fallbackFor(null)

        /** Automatic when the RTO model text says so (AT, AMT, CVT, DCT, DSG); null when it doesn't say. */
        fun isAutomatic(rcModel: String?): Boolean? {
            val m = rcModel?.uppercase() ?: return null
            return if (Regex("\\b(AT|AMT|CVT|DCT|DSG|IVT|TC|AGS)\\b").containsMatchIn(m)) true
            else if (Regex("\\b(MT|MANUAL)\\b").containsMatchIn(m)) false else null
        }
    }
}
