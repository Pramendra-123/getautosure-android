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
 * Depreciation-based fair price estimate, built on IRDAI's published standard motor
 * insurance depreciation schedule (age-based %, public, industry-standard — not any
 * company's internal or proprietary pricing model). Deliberately simple: age is the
 * primary driver, odometer is a small bounded nudge, nothing else. No ex-showroom price
 * catalog exists in this app, so the caller must supply it — we never guess a vehicle's
 * original price.
 */
object FairPriceEstimator {

    /** Age in years (upper bound of each band) to cumulative depreciation %, per IRDAI's published schedule. */
    private val IRDAI_SCHEDULE = listOf(
        0.5 to 5,
        1.0 to 15,
        2.0 to 20,
        3.0 to 30,
        4.0 to 40,
        5.0 to 50
    )

    /**
     * IRDAI's table stops at 5 years — beyond that, insurers negotiate case by case, there's
     * no official published rate. This continues a flat extrapolation and the result is
     * explicitly flagged [FairPriceEstimate.beyondPublishedSchedule] so the UI can disclose
     * it rather than presenting it as an official figure.
     */
    private const val EXTRAPOLATED_PCT_PER_YEAR_BEYOND_5 = 5
    private const val MAX_DEPRECIATION_PCT = 80

    /** ~15,000 km/year is the commonly cited average for India — not a fitted statistic. */
    private const val EXPECTED_KM_PER_YEAR = 15000

    fun estimate(exShowroomPrice: Double, ageYears: Double, odometerKm: Int?): FairPriceEstimate {
        val beyondSchedule = ageYears > 5.0
        val basePct = if (!beyondSchedule) {
            IRDAI_SCHEDULE.firstOrNull { ageYears <= it.first }?.second ?: 50
        } else {
            val extraYears = ageYears - 5.0
            (50 + (extraYears * EXTRAPOLATED_PCT_PER_YEAR_BEYOND_5)).toInt()
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
            beyondPublishedSchedule = beyondSchedule
        )
    }
}
