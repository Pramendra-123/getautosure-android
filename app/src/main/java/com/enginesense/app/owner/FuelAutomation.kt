package com.enginesense.app.owner

import com.enginesense.app.drivelab.DriveAnalyzer
import com.enginesense.app.drivelab.DriveSample
import com.enginesense.app.drivelab.DriveTrip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A fill-up spotted from the car's fuel gauge: the level jumped between switching off and the next start. */
data class DetectedFill(val atMillis: Long, val fromPercent: Double, val toPercent: Double) {
    val addedPercent: Double get() = toPercent - fromPercent
    fun litres(tankLitres: Int): Double = addedPercent / 100.0 * tankLitres
    /** Near the top: counts as a full tank for working out mileage. */
    val fullTank: Boolean get() = toPercent >= 90.0
}

/** Mileage from the scanner's fuel estimate, and how far it's been corrected by real fill-ups. */
data class LiveMileage(val kmPerLitre: Double, val trips: Int, val calibrated: Boolean)

/**
 * Taking the typing out of fuel tracking: fill-ups spotted from the fuel gauge (cars that report
 * it), mileage from the scanner corrected by real fill-ups, and the odometer kept up to date from
 * the km the trip recorder counts. Pure, so it's unit-tested.
 */
object FuelAutomation {

    /** A smaller rise is the gauge settling or the car parked on a slope, not a fill-up. */
    const val MIN_JUMP_PERCENT = 15.0
    private const val READINGS_PER_SIDE = 10

    /** Fuel-gauge jumps between one drive's end and the next drive's start. Needs the dongle left plugged in. */
    fun detectFills(samples: List<DriveSample>): List<DetectedFill> =
        gaugeAcrossStops(samples).filter { it.addedPercent >= MIN_JUMP_PERCENT }

    /**
     * The fuel gauge at the end of each drive and the start of the next, as a [DetectedFill]
     * whatever the direction: a rise is a fill-up, a fall means fuel went while parked.
     */
    fun gaugeAcrossStops(samples: List<DriveSample>): List<DetectedFill> {
        val runs = mutableListOf<MutableList<DriveSample>>()
        samples.sortedBy { it.timeMillis }
            .filter { it.engineOn && it.fuelLevelPercent != null }
            .forEach { s ->
                val last = runs.lastOrNull()?.lastOrNull()
                if (last == null || s.timeMillis - last.timeMillis > DriveAnalyzer.TRIP_GAP_MS) runs += mutableListOf(s) else runs.last() += s
            }
        return runs.zipWithNext().map { (before, after) ->
            val from = median(before.takeLast(READINGS_PER_SIDE).map { it.fuelLevelPercent!! })
            val to = median(after.take(READINGS_PER_SIDE).map { it.fuelLevelPercent!! })
            DetectedFill(after.first().timeMillis, from, to)
        }
    }

    private fun median(values: List<Double>): Double = values.sorted().let { v ->
        if (v.size % 2 == 1) v[v.size / 2] else (v[v.size / 2 - 1] + v[v.size / 2]) / 2
    }

    private fun day(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /**
     * The scanner works fuel out from the air the engine takes in, which is right to about ±15%.
     * Between two full tanks we know the real litres, so real ÷ estimated corrects every trip after.
     * Null until there are two full tanks with trips recorded between them.
     */
    fun calibrationFactor(fuel: List<FuelEntry>, trips: List<DriveTrip>, zone: ZoneId = ZoneId.systemDefault()): Double? {
        val full = fuel.filter { it.fullTank }.sortedBy { it.date }
        if (full.size < 2) return null
        val start = runCatching { LocalDate.parse(full[full.size - 2].date) }.getOrNull() ?: return null
        val end = runCatching { LocalDate.parse(full.last().date) }.getOrNull() ?: return null
        val between = fuel.filter { it.date > start.toString() && it.date <= end.toString() }
        if (between.any { it.litres == null }) return null
        val realLitres = between.sumOf { it.litres!! }
        val estimated = trips.filter { day(it.startMillis, zone) in start..end }.mapNotNull { it.fuelLitres }.sum()
        if (realLitres <= 0.0 || estimated <= 0.0) return null
        return (realLitres / estimated).takeIf { it in 0.5..2.0 }
    }

    /** Mileage over the recent trips the scanner measured, corrected by [factor] when there is one. */
    fun liveMileage(trips: List<DriveTrip>, factor: Double?, lastTrips: Int = 20): LiveMileage? {
        val measured = trips.sortedByDescending { it.startMillis }.filter { it.fuelLitres != null && it.fuelLitres > 0 }.take(lastTrips)
        if (measured.isEmpty()) return null
        val km = measured.sumOf { it.distanceKm }
        val litres = measured.sumOf { it.fuelLitres!! } * (factor ?: 1.0)
        if (km < 1.0 || litres <= 0.0) return null
        return LiveMileage(km / litres, measured.size, factor != null)
    }

    /**
     * Today's odometer: the car's own reading when it reports one, else the last reading the owner
     * gave (service, fill-up or when the car was added) plus the km recorded since then.
     */
    fun estimatedOdometer(
        carReportedKm: Double?,
        lastKnownKm: Int?,
        lastKnownMillis: Long?,
        trips: List<DriveTrip>
    ): Int? {
        carReportedKm?.let { return it.toInt() }
        val base = lastKnownKm ?: return null
        val since = lastKnownMillis ?: return base
        return base + trips.filter { it.startMillis > since }.sumOf { it.distanceKm }.toInt()
    }

    /** The newest odometer reading the owner gave, with roughly when. */
    fun lastKnownOdometer(data: OwnerCarData, profileKm: Int?, carAddedMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Long>? {
        val dated = (data.services.mapNotNull { s -> s.odometerKm?.let { it to s.date } } + data.fuel.mapNotNull { f -> f.odometerKm?.let { it to f.date } })
            .mapNotNull { (km, date) -> runCatching { km to LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull() }
        val newest = dated.maxByOrNull { it.second }
        val fromProfile = profileKm?.let { it to carAddedMillis }
        return listOfNotNull(newest, fromProfile).maxByOrNull { it.second }
    }
}
