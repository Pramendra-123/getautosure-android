package com.enginesense.app.vehicle

/** Fuel use at one instant — km/L only when moving fast enough for it to mean anything (see [MileageAnalyzer.MIN_SPEED_FOR_KM_PER_LITRE]). */
data class InstantFuelUse(val litresPerHour: Double, val kmPerLitre: Double?)

/** One polled (speed, air-flow) sample pair during a trip-mileage run. Air flow is measured (MAF) or estimated — see [AirFlowEstimator]. */
data class FuelSample(val elapsedMs: Long, val speedKmh: Int, val mafGramsPerSec: Double)

/** A completed trip estimate, or null fields if the trip was too short/unsupported — never a guessed number. */
data class TripMileageResult(
    val distanceKm: Double?,
    val fuelLitres: Double?,
    val averageKmPerLitre: Double?,
    /** True when the fuel type (CNG/Electric/Hybrid/unset) makes a km/L estimate meaningless, not just unmeasured. */
    val unsupportedFuelType: Boolean
)

/**
 * Estimates trip fuel economy (km/L) from mass air flow + vehicle speed — no direct fuel-rate
 * PID exists in [com.enginesense.app.vehicle.PidId], so this uses the same MAF-based approach
 * budget OBD apps use (Torque, DashCommand) when a car doesn't expose one directly: mass air
 * flow divided by the stoichiometric air-fuel ratio gives fuel mass flow, divided by fuel
 * density gives fuel volume flow, integrated over the trip gives total fuel used.
 *
 * This is a real calculation from real sensor data, not invented — but it's an estimate with
 * its own error margin (AFR and density are published averages, not this specific tank's fuel),
 * same honesty framing as [PerformanceAnalyzer]'s GPS-free distance estimate.
 */
object MileageAnalyzer {

    /** Below this, a short stop-start drive produces a noisy, misleading average rather than a real trip figure. */
    private const val MIN_DISTANCE_KM = 0.3

    private data class FuelProperties(val airFuelRatio: Double, val densityGPerMl: Double)

    // Published reference constants (stoichiometric AFR, typical fuel density at ~15°C) — not
    // fitted to any specific vehicle or fuel batch, so treat the result as an estimate.
    private val FUEL_PROPERTIES = mapOf(
        "Petrol" to FuelProperties(airFuelRatio = 14.7, densityGPerMl = 0.74),
        "Diesel" to FuelProperties(airFuelRatio = 14.5, densityGPerMl = 0.84)
    )

    /** Below this speed km/L swings wildly (and is infinite-adjacent near zero) — show L/h instead, the way car trip computers do at idle. */
    const val MIN_SPEED_FOR_KM_PER_LITRE = 5

    /**
     * Fuel use right now, from one air-flow + speed sample — what the Live Data screen shows.
     * Null when the fuel type has no meaningful km/L (CNG/Electric/Hybrid/unset), same rule as [analyze].
     */
    fun instant(airGramsPerSec: Double, speedKmh: Int, fuelType: String?): InstantFuelUse? {
        val properties = FUEL_PROPERTIES[fuelType] ?: return null
        val litresPerHour = airGramsPerSec / properties.airFuelRatio / properties.densityGPerMl * 3600.0 / 1000.0
        val kmPerLitre = if (speedKmh >= MIN_SPEED_FOR_KM_PER_LITRE && litresPerHour > 0.0) speedKmh / litresPerHour else null
        return InstantFuelUse(litresPerHour, kmPerLitre)
    }

    fun analyze(samples: List<FuelSample>, fuelType: String?): TripMileageResult {
        val properties = FUEL_PROPERTIES[fuelType]
        if (properties == null) {
            // CNG is mass-based (km/kg, not km/L), Electric has no MAF-fuel relationship at all,
            // Hybrid mixes ICE + electric unpredictably — honestly unsupported, not a wrong number.
            return TripMileageResult(null, null, null, unsupportedFuelType = true)
        }

        val sorted = samples.sortedBy { it.elapsedMs }
        if (sorted.size < 2) return TripMileageResult(null, null, null, unsupportedFuelType = false)

        var distanceM = 0.0
        var fuelMl = 0.0
        for (i in 1 until sorted.size) {
            val a = sorted[i - 1]
            val b = sorted[i]
            val dtSeconds = (b.elapsedMs - a.elapsedMs) / 1000.0
            if (dtSeconds <= 0) continue

            val avgSpeedMs = ((a.speedKmh + b.speedKmh) / 2.0) / 3.6
            distanceM += avgSpeedMs * dtSeconds

            val avgMafGps = (a.mafGramsPerSec + b.mafGramsPerSec) / 2.0
            val fuelMassGps = avgMafGps / properties.airFuelRatio
            fuelMl += (fuelMassGps / properties.densityGPerMl) * dtSeconds
        }

        val distanceKm = distanceM / 1000.0
        val fuelLitres = fuelMl / 1000.0
        if (distanceKm < MIN_DISTANCE_KM || fuelLitres <= 0.0) {
            return TripMileageResult(distanceKm, fuelLitres, null, unsupportedFuelType = false)
        }

        return TripMileageResult(distanceKm, fuelLitres, distanceKm / fuelLitres, unsupportedFuelType = false)
    }
}
