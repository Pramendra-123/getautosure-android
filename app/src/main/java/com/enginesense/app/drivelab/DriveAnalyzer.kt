package com.enginesense.app.drivelab

import com.enginesense.app.vehicle.FuelSample
import com.enginesense.app.vehicle.MileageAnalyzer
import kotlin.math.max
import kotlin.math.roundToInt

/** One engine-on stretch, summarised. Every figure is computed from the recorded samples. */
data class DriveTrip(
    val startMillis: Long,
    val endMillis: Long,
    val distanceKm: Double,
    val maxSpeedKmh: Double?,
    val avgMovingSpeedKmh: Double?,
    val idleSeconds: Long,
    val harshAccelerations: Int,
    val harshBrakes: Int,
    val overSpeedSeconds: Long,
    val highRpmSeconds: Long,
    val maxCoolantC: Double?,
    /** Seconds from start until coolant reached [WARM_COOLANT_C]; null if it never did or wasn't read. */
    val warmUpSeconds: Long?,
    val fuelLitres: Double?,
    val kmPerLitre: Double?,
    /** Lowest voltage seen in the seconds just before the engine caught — the cranking dip, when we caught it. */
    val crankingMinVolts: Double?,
    val chargingVolts: Double?,
    val drivingScore: Int,
    val samples: Int
) {
    val durationSeconds: Long get() = (endMillis - startMillis) / 1000
}

/** Battery and scanner-sleep findings across the whole recording. */
data class BatteryFindings(
    /** Latest voltage read with the engine off for at least [SETTLED_AFTER_OFF_MS] (surface charge gone), else the latest engine-off reading. */
    val restingVolts: Double?,
    val lowestRestingVolts: Double?,
    val crankingDips: List<Double>,
    val chargingVolts: Double?,
    /** Longest time the dongle kept answering after an engine-off — long = it isn't sleeping. */
    val longestAwakeAfterOffMinutes: Long?
)

data class DriveLabSummary(
    val trips: List<DriveTrip>,
    val totalKm: Double,
    val totalFuelLitres: Double?,
    val battery: BatteryFindings,
    val latestOdometerKm: Double?,
    val latestDistanceSinceClearedKm: Double?,
    val fuelLevelFirst: Double?,
    val fuelLevelLast: Double?,
    val engineOnSamples: Int,
    val engineOffSamples: Int
)

/**
 * Turns the raw Drive lab log into trips, a draft driving score, fuel and battery findings.
 * The thresholds are first guesses to be tuned against the real-car trial, not final rules.
 */
object DriveAnalyzer {

    /** Engine-on samples further apart than this are separate trips. */
    const val TRIP_GAP_MS = 120_000L
    const val MIN_TRIP_MS = 60_000L
    /** Readings further apart than this aren't used for acceleration (a missed reply, not a real change). */
    private const val MAX_ACCEL_GAP_MS = 3_000L
    private const val MIN_ACCEL_GAP_MS = 300L
    /** ~0.31 g. */
    const val HARSH_ACCEL_KMH_PER_S = 11.0
    /** ~0.37 g. */
    const val HARSH_BRAKE_KMH_PER_S = -13.0
    /** One event per this window, so a single hard stop isn't counted three times. */
    private const val EVENT_DEBOUNCE_MS = 3_000L
    const val OVER_SPEED_KMH = 100.0
    const val HIGH_RPM = 4_000.0
    const val WARM_COOLANT_C = 70.0
    const val SETTLED_AFTER_OFF_MS = 30 * 60_000L
    private const val CRANK_WINDOW_MS = 15_000L
    /** Integration step cap: a longer gap is a dropped connection, not distance driven. */
    private const val MAX_STEP_MS = 5_000L

    fun analyze(samples: List<DriveSample>, fuelType: String?): DriveLabSummary {
        val sorted = samples.sortedBy { it.timeMillis }
        val tripGroups = splitTrips(sorted)
        val trips = tripGroups.map { tripSummary(it, sorted, fuelType) }
        val fuel = trips.mapNotNull { it.fuelLitres }
        return DriveLabSummary(
            trips = trips.sortedByDescending { it.startMillis },
            totalKm = trips.sumOf { it.distanceKm },
            totalFuelLitres = if (fuel.isEmpty()) null else fuel.sum(),
            battery = battery(sorted, trips),
            latestOdometerKm = sorted.lastOrNull { it.odometerKm != null }?.odometerKm,
            latestDistanceSinceClearedKm = sorted.lastOrNull { it.distanceSinceClearedKm != null }?.distanceSinceClearedKm,
            fuelLevelFirst = sorted.firstOrNull { it.fuelLevelPercent != null }?.fuelLevelPercent,
            fuelLevelLast = sorted.lastOrNull { it.fuelLevelPercent != null }?.fuelLevelPercent,
            engineOnSamples = sorted.count { it.engineOn },
            engineOffSamples = sorted.count { !it.engineOn }
        )
    }

    internal fun splitTrips(sorted: List<DriveSample>): List<List<DriveSample>> {
        val trips = mutableListOf<MutableList<DriveSample>>()
        var current = mutableListOf<DriveSample>()
        sorted.filter { it.engineOn }.forEach { s ->
            val last = current.lastOrNull()
            if (last != null && s.timeMillis - last.timeMillis > TRIP_GAP_MS) {
                trips += current
                current = mutableListOf()
            }
            current += s
        }
        if (current.isNotEmpty()) trips += current
        return trips.filter { it.last().timeMillis - it.first().timeMillis >= MIN_TRIP_MS }
    }

    internal fun tripSummary(trip: List<DriveSample>, all: List<DriveSample>, fuelType: String?): DriveTrip {
        var distanceKm = 0.0
        var idleMs = 0L
        var overSpeedMs = 0L
        var highRpmMs = 0L
        var movingMs = 0L
        var accels = 0
        var brakes = 0
        // Not Long.MIN_VALUE: "now - MIN_VALUE" overflows negative and would block every event.
        var lastAccelEventAt = -EVENT_DEBOUNCE_MS - 1
        var lastBrakeEventAt = -EVENT_DEBOUNCE_MS - 1

        trip.zipWithNext().forEach { (a, b) ->
            val dtMs = b.timeMillis - a.timeMillis
            val stepMs = dtMs.coerceAtMost(MAX_STEP_MS)
            val v = a.speedKmh
            if (v != null) {
                distanceKm += v * stepMs / 3_600_000.0
                if (v < 1.0) idleMs += stepMs else movingMs += stepMs
                if (v > OVER_SPEED_KMH) overSpeedMs += stepMs
            }
            if ((a.rpm ?: 0.0) > HIGH_RPM) highRpmMs += stepMs
            val va = a.speedKmh
            val vb = b.speedKmh
            if (va != null && vb != null && dtMs in MIN_ACCEL_GAP_MS..MAX_ACCEL_GAP_MS) {
                val accel = (vb - va) / (dtMs / 1000.0)
                if (accel >= HARSH_ACCEL_KMH_PER_S && b.timeMillis - lastAccelEventAt > EVENT_DEBOUNCE_MS) {
                    accels++; lastAccelEventAt = b.timeMillis
                }
                if (accel <= HARSH_BRAKE_KMH_PER_S && b.timeMillis - lastBrakeEventAt > EVENT_DEBOUNCE_MS) {
                    brakes++; lastBrakeEventAt = b.timeMillis
                }
            }
        }

        val start = trip.first().timeMillis
        val fuelSamples = trip.mapNotNull { s ->
            val air = s.airGramsPerSec ?: return@mapNotNull null
            val speed = s.speedKmh ?: return@mapNotNull null
            FuelSample(s.timeMillis - start, speed.roundToInt(), air)
        }
        val mileage = if (fuelSamples.size >= 2) MileageAnalyzer.analyze(fuelSamples, fuelType) else null

        val coolant = trip.mapNotNull { it.coolantC }
        val warmAt = trip.firstOrNull { (it.coolantC ?: 0.0) >= WARM_COOLANT_C }?.timeMillis
        val firstCoolant = trip.firstOrNull { it.coolantC != null }?.coolantC
        val charging = trip.mapNotNull { it.moduleVolts ?: it.adapterVolts }.median()
        val crank = all.filter { it.timeMillis in (start - CRANK_WINDOW_MS)..(start + 2_000L) }
            .mapNotNull { it.adapterVolts ?: it.moduleVolts }
            .minOrNull()
        val moving = trip.mapNotNull { it.speedKmh }.filter { it >= 1.0 }

        val duration = max(1L, trip.last().timeMillis - start)
        val score = drivingScore(
            harshAccels = accels, harshBrakes = brakes, distanceKm = distanceKm,
            idleShare = idleMs.toDouble() / duration, overSpeedShare = overSpeedMs.toDouble() / duration,
            highRpmShare = highRpmMs.toDouble() / duration
        )

        return DriveTrip(
            startMillis = start,
            endMillis = trip.last().timeMillis,
            distanceKm = distanceKm,
            maxSpeedKmh = trip.mapNotNull { it.speedKmh }.maxOrNull(),
            avgMovingSpeedKmh = if (moving.isEmpty()) null else moving.average(),
            idleSeconds = idleMs / 1000,
            harshAccelerations = accels,
            harshBrakes = brakes,
            overSpeedSeconds = overSpeedMs / 1000,
            highRpmSeconds = highRpmMs / 1000,
            maxCoolantC = coolant.maxOrNull(),
            // Only meaningful when the trip started cold — a warm restart "warms up" instantly.
            warmUpSeconds = if (warmAt != null && firstCoolant != null && firstCoolant < WARM_COOLANT_C) (warmAt - start) / 1000 else null,
            fuelLitres = mileage?.fuelLitres,
            kmPerLitre = mileage?.averageKmPerLitre,
            crankingMinVolts = crank,
            chargingVolts = charging,
            drivingScore = score,
            samples = trip.size
        )
    }

    /**
     * Draft 0–100 score: harsh events are weighted per 10 km so a long drive isn't punished for
     * being long; idling, time over [OVER_SPEED_KMH] and time over [HIGH_RPM] take off up to 15,
     * 20 and 10 points. To be tuned once we see real numbers.
     */
    fun drivingScore(harshAccels: Int, harshBrakes: Int, distanceKm: Double, idleShare: Double, overSpeedShare: Double, highRpmShare: Double): Int {
        val per10Km = 10.0 / max(distanceKm, 2.0)
        val eventPenalty = (harshBrakes * 6.0 + harshAccels * 4.0) * per10Km
        val idlePenalty = (idleShare * 30.0).coerceAtMost(15.0)
        val speedPenalty = (overSpeedShare * 60.0).coerceAtMost(20.0)
        val rpmPenalty = (highRpmShare * 50.0).coerceAtMost(10.0)
        return (100.0 - eventPenalty - idlePenalty - speedPenalty - rpmPenalty).coerceIn(0.0, 100.0).roundToInt()
    }

    internal fun battery(sorted: List<DriveSample>, trips: List<DriveTrip>): BatteryFindings {
        val offSamples = sorted.filter { !it.engineOn && it.adapterVolts != null }
        // Rest readings: engine-off samples at least SETTLED_AFTER_OFF_MS after the last running sample.
        var lastOnAt: Long? = null
        val settled = mutableListOf<Double>()
        var awakeStart: Long? = null
        var longestAwakeMs = 0L
        var lastOffSampleAt: Long? = null
        for (s in sorted) {
            if (s.engineOn) {
                lastOnAt = s.timeMillis
                awakeStart = null
                lastOffSampleAt = null
                continue
            }
            val on = lastOnAt
            if (on != null) {
                // A gap of more than 10 min in engine-off contact = the dongle (or the link) slept,
                // so the "still awake" stretch ends there.
                val prev = lastOffSampleAt
                if (prev != null && s.timeMillis - prev > AWAKE_GAP_MS) awakeStart = null
                val start = awakeStart ?: (if (prev == null || s.timeMillis - prev <= AWAKE_GAP_MS) on else s.timeMillis)
                awakeStart = start
                longestAwakeMs = max(longestAwakeMs, s.timeMillis - start)
                if (s.timeMillis - on >= SETTLED_AFTER_OFF_MS) s.adapterVolts?.let { settled += it }
            }
            lastOffSampleAt = s.timeMillis
        }
        return BatteryFindings(
            restingVolts = settled.lastOrNull() ?: offSamples.lastOrNull()?.adapterVolts,
            lowestRestingVolts = settled.minOrNull(),
            crankingDips = trips.mapNotNull { it.crankingMinVolts }.filter { it < 12.0 },
            chargingVolts = trips.mapNotNull { it.chargingVolts }.median(),
            longestAwakeAfterOffMinutes = if (longestAwakeMs > 0) longestAwakeMs / 60_000L else null
        )
    }

    private fun List<Double>.median(): Double? {
        if (isEmpty()) return null
        val s = sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    private const val AWAKE_GAP_MS = 10 * 60_000L
}
