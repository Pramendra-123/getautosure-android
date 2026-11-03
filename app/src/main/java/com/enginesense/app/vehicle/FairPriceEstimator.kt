package com.enginesense.app.vehicle

/** A range, not a point figure — this is a rough estimate, not a verified valuation. */
data class FairPriceEstimate(
    val lowEstimate: Double,
    val highEstimate: Double,
    val depreciationPercent: Int,
    val ageYears: Int,
    val beyondPublishedSchedule: Boolean
)

/**
 * Tentative used-car price from the car's new (ex-showroom) price, its age and its km. The age
 * curve follows what used cars typically fetch in India (about 12% off after a year, a little
 * under half after seven) — it used to be IRDAI's insurance table, which writes value off much
 * faster than the market does and put a 2019 Creta at about ₹6 lakh against ₹8–9.5 lakh real
 * prices. Still a rough guide: brand, condition and city move real prices a lot.
 */
object FairPriceEstimator {

    /** Age in years to % below the new price, between which ages are blended. */
    private val CURVE = listOf(
        0.0 to 0, 1.0 to 12, 2.0 to 20, 3.0 to 27, 4.0 to 33, 5.0 to 38,
        6.0 to 43, 7.0 to 47, 8.0 to 51, 9.0 to 55, 10.0 to 58
    )

    /** Past ten years prices vary too much car to car for the curve; it carries on gently and is flagged. */
    private const val PCT_PER_YEAR_BEYOND_10 = 3
    private const val MAX_DEPRECIATION_PCT = 75

    /** ~15,000 km/year is the commonly cited average for India — not a fitted statistic. */
    private const val EXPECTED_KM_PER_YEAR = 15000

    fun estimate(exShowroomPrice: Double, ageYears: Double, odometerKm: Int?): FairPriceEstimate {
        val beyondCurve = ageYears > 10.0
        val basePct = if (beyondCurve) {
            (58 + (ageYears - 10.0) * PCT_PER_YEAR_BEYOND_10).toInt()
        } else {
            val upper = CURVE.indexOfFirst { it.first >= ageYears }.coerceAtLeast(1)
            val (a0, p0) = CURVE[upper - 1]
            val (a1, p1) = CURVE[upper]
            (p0 + (p1 - p0) * ((ageYears - a0) / (a1 - a0))).toInt()
        }.coerceAtMost(MAX_DEPRECIATION_PCT)

        // A small, capped nudge — not a precise statistical adjustment. Cars well above or
        // below the expected km for their age shift a few points, never enough to dominate
        // the age-based figure above.
        val expectedKm = (ageYears.coerceAtLeast(0.5) * EXPECTED_KM_PER_YEAR).toInt()
        val kmAdjustment = odometerKm?.let { km ->
            val diffRatio = (km - expectedKm).toDouble() / expectedKm.coerceAtLeast(1)
            (diffRatio * 5).toInt().coerceIn(-5, 5)
        } ?: 0

        val totalPct = (basePct + kmAdjustment).coerceIn(5, MAX_DEPRECIATION_PCT)
        val midValue = exShowroomPrice * (1 - totalPct / 100.0)
        val low = midValue * 0.92
        val high = midValue * 1.08

        return FairPriceEstimate(
            lowEstimate = low,
            highEstimate = high,
            depreciationPercent = totalPct,
            ageYears = ageYears.toInt(),
            beyondPublishedSchedule = beyondCurve
        )
    }
}
