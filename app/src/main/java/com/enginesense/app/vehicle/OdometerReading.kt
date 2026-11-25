package com.enginesense.app.vehicle

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The buyer's fallback when the dashboard can't be read (dead or blank cluster): a range, priced at
 * its middle. A range can't be checked against the car's computer, so it's always "the seller's word".
 */
enum class KmRange(val label: String, val lowKm: Int, val highKm: Int) {
    UNDER_10K("Under 10k", 0, 10_000),
    K10_20("10–20k", 10_000, 20_000),
    K20_30("20–30k", 20_000, 30_000),
    K30_50("30–50k", 30_000, 50_000),
    K50_75("50–75k", 50_000, 75_000),
    K75_100("75k–1 lakh", 75_000, 100_000),
    L1_1_5("1–1.5 lakh", 100_000, 150_000),
    OVER_1_5L("Over 1.5 lakh", 150_000, 200_000);

    val midKm: Int get() = (lowKm + highKm) / 2

    fun contains(km: Double): Boolean = km >= lowKm && (km < highKm || this == OVER_1_5L)
}

/** How fast the car has been driven for its age, from the km and the RTO registration date. */
enum class KmPace { NORMAL, HIGH, SUSPICIOUSLY_LOW }

data class KmPerYear(val kmPerYear: Int, val ageYears: Double, val pace: KmPace)

object OdometerReading {
    /** India's usual 12–15,000 km a year; above 20,000 is heavy use, priced lower. */
    const val HIGH_KM_PER_YEAR = 20_000
    /** Under 3,000 a year on a car older than three years is a common sign of a rolled-back odometer. */
    const val LOW_KM_PER_YEAR = 3_000
    const val LOW_CHECK_MIN_AGE_YEARS = 3.0

    /** A reading under 100 km is a half-typed number or a missing one, not a brand-new car. */
    fun exactKm(input: String): Double? = input.toDoubleOrNull()?.takeIf { it >= 100 }

    /** The km the price uses: the dashboard reading, else the middle of the seller's range. */
    fun kmForPrice(dashboardKm: Double?, range: KmRange?): Int? = dashboardKm?.toInt() ?: range?.midKm

    /** Age from the RTO registration date, else mid-year of the year it was made. Null when neither is known. */
    fun ageYears(registrationDate: LocalDate?, year: Int?, today: LocalDate): Double? {
        val since = registrationDate ?: year?.let { LocalDate.of(it, 7, 1) } ?: return null
        val days = ChronoUnit.DAYS.between(since, today)
        return (days / 365.25).coerceAtLeast(0.25)
    }

    fun perYear(km: Double, ageYears: Double): KmPerYear {
        val rate = (km / ageYears).toInt()
        val pace = when {
            rate > HIGH_KM_PER_YEAR -> KmPace.HIGH
            rate < LOW_KM_PER_YEAR && ageYears >= LOW_CHECK_MIN_AGE_YEARS -> KmPace.SUSPICIOUSLY_LOW
            else -> KmPace.NORMAL
        }
        return KmPerYear(rate, ageYears, pace)
    }
}
