package com.enginesense.app.owner

import com.enginesense.app.drivelab.DriveSample
import com.enginesense.app.drivelab.DriveTrip
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where a mileage figure comes from, best first. */
enum class MileageBasis(val label: String) {
    CORRECTED("Measured · corrected by your fill-ups"),
    SCANNER("Estimate from the scanner · about ±15%"),
    CLAIMED("Estimate from the company figure")
}

data class MileageView(val kmPerLitre: Double, val basis: MileageBasis, val trips: Int)

/** The engine's fuel correction in plain words. */
data class FuelMix(val trimPercent: Double, val level: Level, val headline: String, val detail: String)

enum class TankVerdict { NORMAL, ODD, NOT_ENOUGH }

/** One tank, from a fill-up until the next: how much correction the engine needed on it. */
data class TankCheck(val filledMillis: Long, val trimPercent: Double?, val usualTrimPercent: Double?, val verdict: TankVerdict)

data class ParkedDrop(val atMillis: Long, val fromPercent: Double, val toPercent: Double) {
    val droppedPercent: Double get() = fromPercent - toPercent
}

data class MileageDrop(
    val percent: Int,
    val sinceMillis: Long,
    val recentKmpl: Double,
    val usualKmpl: Double,
    val reasons: List<String>,
    /** Extra fuel money a month at the recent driving, rounded to ₹100. */
    val monthlyCostInr: Int?
)

/**
 * Fuel intelligence: real mileage, the fuel mix, how each tank behaved and the alerts. Every
 * alert compares the car with its own past, so the scanner's ±15% estimate error cancels out —
 * it works without fill-up data and on diesel cars too. Pure, so it's unit-tested.
 */
object FuelIntelligence {

    const val MIN_PARKED_DROP_PERCENT = 6.0
    const val MIN_DROP_PERCENT = 12
    /** A tank's fuel mix this far from the usual one is worth a word. */
    const val ODD_TANK_POINTS = 5.0
    private const val DAY_MS = 86_400_000L
    private const val RECENT_DAYS = 14L
    private const val BASELINE_DAYS = 60L
    /** Trims only mean something once the engine is warm. */
    private const val WARM_C = 70.0
    private const val MIN_TANK_READINGS = 20
    private const val MIN_TRIPS = 4
    private const val MIN_RECENT_KM = 40.0
    private const val MIN_BASELINE_KM = 80.0

    fun mileage(trips: List<DriveTrip>, factor: Double?, claimedKmpl: Double?): MileageView? {
        FuelAutomation.liveMileage(trips, factor)?.let {
            return MileageView(it.kmPerLitre, if (it.calibrated) MileageBasis.CORRECTED else MileageBasis.SCANNER, it.trips)
        }
        return claimedKmpl?.let { MileageView(it * CarWallet.REAL_WORLD_SHARE, MileageBasis.CLAIMED, 0) }
    }

    /** Null when the car doesn't report fuel trims (most diesels). */
    fun fuelMix(trimPercent: Double?): FuelMix? {
        val t = trimPercent ?: return null
        val amount = "%.0f%%".format(abs(t))
        return when {
            abs(t) < 3 -> FuelMix(t, Level.GOOD, "Balanced", "The engine needs almost no correction. That's what you want.")
            t > 10 -> FuelMix(t, Level.ATTENTION, "Adding $amount extra fuel", "Outside normal. Often an air leak, a dirty air filter or a sensor. Show this to your mechanic.")
            t < -10 -> FuelMix(t, Level.ATTENTION, "Taking $amount fuel away", "Outside normal. Often a leaking injector or a sensor. Show this to your mechanic.")
            t > 0 -> FuelMix(t, Level.GOOD, "Adding $amount extra fuel", "Within normal. Worth watching if it keeps rising.")
            else -> FuelMix(t, Level.GOOD, "Taking $amount fuel away", "Within normal. Worth watching if it keeps falling.")
        }
    }

    /** Warm-engine fuel trim readings, oldest first. */
    private fun warmTrims(samples: List<DriveSample>): List<DriveSample> =
        samples.filter { it.fuelTrimPercent != null && (it.coolantC ?: 0.0) >= WARM_C }.sortedBy { it.timeMillis }

    /** The average warm fuel trim over the newest [count] readings. */
    fun recentTrim(samples: List<DriveSample>, count: Int = 30): Double? =
        warmTrims(samples).takeLast(count).takeIf { it.size >= 5 }?.map { it.fuelTrimPercent!! }?.average()

    /**
     * Each tank after a fill-up against the tanks before it. Ethanol and poor fuel both make the
     * engine add fuel, so a tank needing clearly more than usual is "odd" — a hint, never proof.
     */
    fun tanks(fillMillis: List<Long>, samples: List<DriveSample>): List<TankCheck> {
        val warm = warmTrims(samples)
        val fills = fillMillis.distinct().sorted()
        val trims = fills.mapIndexed { i, start ->
            val end = fills.getOrNull(i + 1) ?: Long.MAX_VALUE
            warm.filter { it.timeMillis in start until end }.takeIf { it.size >= MIN_TANK_READINGS }?.map { it.fuelTrimPercent!! }?.average()
        }
        return fills.mapIndexed { i, start ->
            val trim = trims[i]
            val earlier = trims.take(i).filterNotNull().takeLast(5)
            val usual = if (earlier.size >= 2) earlier.sorted()[earlier.size / 2] else null
            val verdict = when {
                trim == null || usual == null -> TankVerdict.NOT_ENOUGH
                trim - usual >= ODD_TANK_POINTS -> TankVerdict.ODD
                else -> TankVerdict.NORMAL
            }
            TankCheck(start, trim, usual, verdict)
        }
    }

    /** The gauge lower at the next start than when the car was parked: a leak, or fuel taken out. */
    fun parkedDrops(samples: List<DriveSample>): List<ParkedDrop> =
        FuelAutomation.gaugeAcrossStops(samples)
            .filter { -it.addedPercent >= MIN_PARKED_DROP_PERCENT }
            .map { ParkedDrop(it.atMillis, it.fromPercent, it.toPercent) }

    /**
     * Mileage over the last two weeks against the eight weeks before. Only when it stays down
     * across several trips — one traffic jam isn't a trend — with the likely reasons.
     */
    fun mileageDrop(trips: List<DriveTrip>, samples: List<DriveSample>, now: Long, pricePerLitre: Double?): MileageDrop? {
        val measured = trips.filter { (it.fuelLitres ?: 0.0) > 0 && it.distanceKm > 0 }
        val recentFrom = now - RECENT_DAYS * DAY_MS
        val recent = measured.filter { it.startMillis >= recentFrom }
        val baseline = measured.filter { it.startMillis in (recentFrom - BASELINE_DAYS * DAY_MS) until recentFrom }
        if (recent.size < MIN_TRIPS || baseline.size < MIN_TRIPS) return null
        if (recent.sumOf { it.distanceKm } < MIN_RECENT_KM || baseline.sumOf { it.distanceKm } < MIN_BASELINE_KM) return null
        fun kmpl(t: List<DriveTrip>) = t.sumOf { it.distanceKm } / t.sumOf { it.fuelLitres!! }
        val recentKmpl = kmpl(recent)
        val usualKmpl = kmpl(baseline)
        val percent = ((1 - recentKmpl / usualKmpl) * 100).roundToInt()
        if (percent < MIN_DROP_PERCENT) return null

        val reasons = mutableListOf<String>()
        fun idleShare(t: List<DriveTrip>) = t.sumOf { it.idleSeconds }.toDouble() / t.sumOf { it.durationSeconds }.coerceAtLeast(1)
        if (idleShare(recent) >= idleShare(baseline) * 1.5 && idleShare(recent) >= 0.15) reasons += "More idling than usual"
        fun shortShare(t: List<DriveTrip>) = t.count { it.distanceKm < 5 }.toDouble() / t.size
        if (shortShare(recent) >= shortShare(baseline) + 0.25) reasons += "More short trips, with the engine cold"
        fun trimIn(t: List<DriveTrip>) = warmTrims(samples).filter { s -> t.any { s.timeMillis in it.startMillis..it.endMillis } }
            .takeIf { it.size >= 5 }?.map { it.fuelTrimPercent!! }?.average()
        val trimNow = trimIn(recent)
        val trimBefore = trimIn(baseline)
        if (trimNow != null && trimBefore != null && trimNow - trimBefore >= 4) reasons += "The fuel mix is rising"
        fun warmUp(t: List<DriveTrip>) = t.mapNotNull { it.warmUpSeconds }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val warmNow = warmUp(recent)
        val warmBefore = warmUp(baseline)
        if (warmNow != null && warmBefore != null && warmNow >= warmBefore * 1.5 && warmNow - warmBefore >= 120) reasons += "The engine takes longer to warm up"

        val kmPerMonth = recent.sumOf { it.distanceKm } * 30 / RECENT_DAYS
        val cost = pricePerLitre?.let { p -> (kmPerMonth * (1 / recentKmpl - 1 / usualKmpl) * p / 100).roundToInt() * 100 }?.takeIf { it > 0 }
        return MileageDrop(percent, recent.minOf { it.startMillis }, recentKmpl, usualKmpl, reasons, cost)
    }
}
