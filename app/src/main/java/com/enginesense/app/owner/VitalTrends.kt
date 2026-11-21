package com.enginesense.app.owner

import kotlin.math.abs

/**
 * The readings that move from scan to scan even when the health score sits at 100, each with
 * the range that's normal for a warmed-up healthy car (the same ranges as the scan report).
 */
enum class Vital(val label: String, val unit: String, val normal: ClosedFloatingPointRange<Double>, private val decimals: Int) {
    CHARGING("Charging voltage", "V", 13.5..14.8, 1),
    ENGINE_TEMP("Engine running temp", "°C", 80.0..105.0, 0),
    FUEL_MIX("Fuel mix (trim)", "%", -10.0..10.0, 0);

    fun format(v: Double): String = when {
        this == FUEL_MIX -> "%+.0f%%".format(v)
        decimals == 0 -> "%.0f%s".format(v, unit)
        else -> "%.${decimals}f %s".format(v, unit)
    }
}

/** One scan's vitals. [values] holds only the readings the car reported. */
data class VitalScan(val timeMillis: Long, val values: Map<Vital, Double>, val faultCodes: Int)

data class VitalTrend(
    val vital: Vital,
    /** Oldest first. */
    val points: List<Double>,
    val level: Level,
    /** "Steady at 92°C", "13.9 → 13.6 V". */
    val summary: String,
    /** Set when the reading needs a word, e.g. "Charging voltage is slowly dropping. Still normal, worth a check." */
    val warning: String?
) {
    /** The y-range to draw: the normal band plus every point, with a little room. */
    val plotRange: ClosedFloatingPointRange<Double>
        get() {
            val lo = minOf(vital.normal.start, points.min())
            val hi = maxOf(vital.normal.endInclusive, points.max())
            val pad = (hi - lo) * 0.15
            return (lo - pad)..(hi + pad)
        }
}

/** "Since your last scan": each vital then and now, and what else changed. */
data class SinceLastScan(val previousMillis: Long, val changes: List<Pair<String, String>>, val worse: Set<String>)

/**
 * Health over time as vital signs instead of one score. A healthy car's score stays at 100, so
 * a score chart is a flat line; the readings underneath drift and show wear early. Pure, tested.
 */
object VitalTrends {


    fun trends(scans: List<VitalScan>): List<VitalTrend> = Vital.entries.mapNotNull { vital ->
        val points = scans.sortedBy { it.timeMillis }.mapNotNull { it.values[vital] }
        if (points.size < 2) null else trend(vital, points)
    }

    private fun trend(vital: Vital, points: List<Double>): VitalTrend {
        val first = points.first()
        val last = points.last()
        val band = vital.normal.endInclusive - vital.normal.start
        val steady = abs(last - first) < band * 0.08
        // A reading that left normal and came back: worth saying, even if it's fine now.
        val excursions = points.dropLast(1).filter { it !in vital.normal }
        val furthest = excursions.maxByOrNull { if (it < vital.normal.start) vital.normal.start - it else it - vital.normal.endInclusive }
        val summary = when {
            furthest != null && last in vital.normal ->
                "${vital.format(last)} now · went to ${vital.format(furthest)} ${if (excursions.size == 1) "once" else "${excursions.size} times"}"
            steady -> "Steady at ${vital.format(last)}"
            else -> "${vital.format(first)} → ${vital.format(last)}"
        }
        // Toward the nearer edge of normal, and now in the outer fifth of the band: wear showing early.
        val towardEdge = if (last < first) last - vital.normal.start else vital.normal.endInclusive - last
        val drifting = !steady && towardEdge in 0.0..(band * 0.2) &&
            points.zipWithNext().count { (a, b) -> if (last < first) b <= a else b >= a } >= (points.size - 1) * 2 / 3
        val outside = last !in vital.normal
        val level = when {
            outside -> Level.ATTENTION
            drifting -> Level.WATCH
            else -> Level.GOOD
        }
        val direction = if (last < first) "dropping" else "rising"
        val warning = when {
            outside -> "${vital.label} is outside the normal ${vital.format(vital.normal.start)}–${vital.format(vital.normal.endInclusive)}. ${advice(vital, last < vital.normal.start)}"
            drifting -> "${vital.label} is slowly $direction. Still normal, worth a check at your next service."
            else -> null
        }
        return VitalTrend(vital, points, level, summary, warning)
    }

    private fun advice(vital: Vital, low: Boolean): String = when (vital) {
        Vital.CHARGING -> if (low) "The alternator may not be charging the battery." else "The alternator may be overcharging."
        Vital.ENGINE_TEMP -> if (low) "The thermostat may be stuck open." else "The engine is running hot — check the coolant."
        Vital.FUEL_MIX -> if (low) "The engine is taking fuel away: often a leaking injector or a sensor." else "The engine is adding fuel: often an air leak, a dirty air filter or a sensor."
    }

    /** The two newest scans compared, plus [kmBetween] when trips recorded it. Null with fewer than two scans. */
    fun sinceLastScan(scans: List<VitalScan>, kmBetween: Double?): SinceLastScan? {
        val sorted = scans.sortedBy { it.timeMillis }
        if (sorted.size < 2) return null
        val (prev, last) = sorted.takeLast(2)
        val changes = mutableListOf<Pair<String, String>>()
        val worse = mutableSetOf<String>()
        Vital.entries.forEach { v ->
            val a = prev.values[v]; val b = last.values[v]
            if (a == null || b == null) return@forEach
            val band = v.normal.endInclusive - v.normal.start
            changes += v.label to if (abs(b - a) < band * 0.08) "Same" else "${v.format(a)} → ${v.format(b)}"
            if (b !in v.normal && a in v.normal) worse += v.label
        }
        changes += "Fault codes" to when {
            last.faultCodes == 0 && prev.faultCodes == 0 -> "None, same"
            last.faultCodes == prev.faultCodes -> "${last.faultCodes}, same"
            else -> "${prev.faultCodes} → ${last.faultCodes}"
        }
        if (last.faultCodes > prev.faultCodes) worse += "Fault codes"
        kmBetween?.takeIf { it >= 1 }?.let { changes += "Km driven" to "%,.0f km".format(it) }
        return SinceLastScan(prev.timeMillis, changes, worse)
    }
}
