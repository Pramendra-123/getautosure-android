package com.enginesense.app.vehicle

/** Where an air-flow figure came from — the UI labels anything but [MEASURED] as an estimate. */
enum class AirFlowSource { MEASURED, ESTIMATED_FROM_MAP }

data class AirFlow(val gramsPerSec: Double, val source: AirFlowSource)

/**
 * Air mass flow into the engine, which [MileageAnalyzer] turns into fuel used. Cars with a MAF
 * sensor report it directly. Many cars sold in India (Maruti, Hyundai, VW group 1.0 TSI, ...)
 * have no MAF and report manifold pressure instead — for those it's estimated with the
 * standard "speed-density" method that apps like Torque use:
 *
 *   air (g/s) = MAP × displacement × (RPM / 2 / 60) × VE / (R × intake air temp)
 *
 * — a four-stroke engine fills its full displacement once every two revolutions; the ideal gas
 * law turns that volume at manifold pressure and intake temperature into a mass. VE (volumetric
 * efficiency — how fully the cylinders actually fill) isn't readable over OBD, so a typical
 * modern-engine figure is assumed. That assumption is the main source of error (roughly
 * ±10–15%), which is why the result is always labeled an estimate, never presented as measured.
 *
 * Returns null rather than guessing when an input is missing — e.g. no engine size entered.
 */
object AirFlowEstimator {

    /** Typical for a modern fuel-injected petrol engine across normal driving; not fitted to any one car. */
    const val ASSUMED_VOLUMETRIC_EFFICIENCY = 0.85

    /** Specific gas constant for dry air, J/(kg·K). */
    private const val R_AIR = 287.05

    fun airFlow(
        mafGramsPerSec: Double?,
        mapKpa: Double?,
        rpm: Double?,
        intakeAirTempC: Double?,
        engineCc: Int?,
    ): AirFlow? {
        if (mafGramsPerSec != null) return AirFlow(mafGramsPerSec, AirFlowSource.MEASURED)
        val estimated = speedDensityGramsPerSec(mapKpa, rpm, intakeAirTempC, engineCc) ?: return null
        return AirFlow(estimated, AirFlowSource.ESTIMATED_FROM_MAP)
    }

    fun speedDensityGramsPerSec(mapKpa: Double?, rpm: Double?, intakeAirTempC: Double?, engineCc: Int?): Double? {
        if (mapKpa == null || rpm == null || intakeAirTempC == null || engineCc == null) return null
        if (engineCc <= 0 || mapKpa <= 0.0 || rpm < 0.0) return null
        val pressurePa = mapKpa * 1000.0
        val displacementM3 = engineCc / 1_000_000.0
        val intakeStrokesPerSec = rpm / 2.0 / 60.0
        val tempK = intakeAirTempC + 273.15
        val kgPerSec = pressurePa * displacementM3 * intakeStrokesPerSec * ASSUMED_VOLUMETRIC_EFFICIENCY / (R_AIR * tempK)
        return kgPerSec * 1000.0
    }
}
