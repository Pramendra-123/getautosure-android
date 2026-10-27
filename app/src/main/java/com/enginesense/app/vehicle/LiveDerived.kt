package com.enginesense.app.vehicle

/** Values the Live Data screen calculates from the raw PIDs — nothing here is read from the car directly. */
data class LiveDerived(
    val instant: InstantFuelUse? = null,
    /** Null when there's no air-flow figure at all; otherwise whether mileage is measured or estimated. */
    val airFlowSource: AirFlowSource? = null,
    val trip: TripMileageResult? = null,
    /** Manifold minus barometric pressure — positive is turbo boost, negative is vacuum. */
    val boostKpa: Double? = null,
    /** True once boost has gone clearly positive this session — i.e. the car really is turbocharged. */
    val turboSeen: Boolean = false,
    /** Why mileage can't be shown, in words for the driver; null when it can. */
    val mileageUnavailableReason: String? = null,
)

/**
 * Pure: latest readings + vehicle details in, [LiveDerived] out. The ViewModel owns the sample
 * list and the running state; this only does the arithmetic, so it's unit-tested without a car.
 */
object LiveDerivedCalculator {

    /** Above this much positive pressure the car is boosting, not just sensor noise around atmospheric. */
    private const val TURBO_CONFIRM_KPA = 5.0

    fun value(readings: Map<PidId, PidReading>, pid: PidId): Double? =
        readings[pid]?.takeIf { it.status == PidStatus.VERIFIED }?.value

    fun airFlow(readings: Map<PidId, PidReading>, engineCc: Int?): AirFlow? = AirFlowEstimator.airFlow(
        mafGramsPerSec = value(readings, PidId.MAF_AIR_FLOW_RATE),
        mapKpa = value(readings, PidId.INTAKE_MANIFOLD_PRESSURE),
        rpm = value(readings, PidId.RPM),
        intakeAirTempC = value(readings, PidId.INTAKE_AIR_TEMP_C),
        engineCc = engineCc,
    )

    fun compute(
        readings: Map<PidId, PidReading>,
        engineCc: Int?,
        fuelType: String?,
        tripSamples: List<FuelSample>,
        turboSeenSoFar: Boolean,
    ): LiveDerived {
        val map = value(readings, PidId.INTAKE_MANIFOLD_PRESSURE)
        val baro = value(readings, PidId.BAROMETRIC_PRESSURE)
        val boost = if (map != null && baro != null) map - baro else null
        val turboSeen = turboSeenSoFar || (boost != null && boost > TURBO_CONFIRM_KPA)

        val air = airFlow(readings, engineCc)
        val speed = value(readings, PidId.SPEED_KMH)
        val instant = if (air != null && speed != null) MileageAnalyzer.instant(air.gramsPerSec, speed.toInt(), fuelType) else null
        val trip = if (air != null) MileageAnalyzer.analyze(tripSamples, fuelType).takeUnless { it.unsupportedFuelType } else null

        return LiveDerived(
            instant = instant,
            airFlowSource = air?.source,
            trip = trip,
            boostKpa = boost,
            turboSeen = turboSeen,
            mileageUnavailableReason = unavailableReason(readings, engineCc, fuelType, air),
        )
    }

    private fun unavailableReason(readings: Map<PidId, PidReading>, engineCc: Int?, fuelType: String?, air: AirFlow?): String? {
        if (fuelType != "Petrol" && fuelType != "Diesel") {
            return if (fuelType == null) "Set your car's fuel type to see mileage." else "Mileage in km/l isn't available for $fuelType cars."
        }
        if (air != null) return null
        val hasMaf = readings[PidId.MAF_AIR_FLOW_RATE]?.status == PidStatus.VERIFIED
        val hasMap = value(readings, PidId.INTAKE_MANIFOLD_PRESSURE) != null
        return when {
            hasMaf -> null
            !hasMap && readings.containsKey(PidId.INTAKE_MANIFOLD_PRESSURE) -> "This car doesn't report air flow or manifold pressure, so mileage can't be worked out."
            engineCc == null -> "Add your engine size (cc) to see estimated mileage."
            else -> "Waiting for readings…"
        }
    }
}
