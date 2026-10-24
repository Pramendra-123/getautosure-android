package com.enginesense.app.vehicle

import com.enginesense.app.storage.PersistedTripMileage

data class MileageTrendResult(
    val currentKmPerLitre: Double,
    /** Average of prior trips; null until at least [MileageTrendAnalyzer.MIN_PRIOR_TRIPS] exist — a single trip has nothing to compare against. */
    val baselineKmPerLitre: Double?,
    val percentChange: Double?,
    val isDropSignificant: Boolean
)

/**
 * Two separate questions, deliberately not conflated:
 *
 * 1. [currentScanSignal] — "is there something in the latest health scan that could plausibly be
 *    costing mileage right now?" Needs no trip history at all — an elevated fuel trim or an active
 *    misfire finding is a real signal on its own, so this can answer on trip 1. Returns null when
 *    nothing's actually notable, never a manufactured concern.
 *
 * 2. [analyze] — "did this trip's mileage actually drop compared to this car's own history?" This
 *    DOES need a real baseline to mean anything — comparing against a lab-rated or flat-percentage
 *    "expected" figure produces false drops for ordinary city driving (confirmed against V3Cars'
 *    own real-world-vs-ARAI test data: deviation ranges from -11% to -55% across models, nowhere
 *    near a flat number), so this only ever compares a trip to this same car's own prior trips.
 */
object MileageTrendAnalyzer {

    private const val MIN_PRIOR_TRIPS = 2
    private const val DROP_THRESHOLD_PERCENT = 10.0

    fun analyze(currentKmPerLitre: Double, priorTrips: List<PersistedTripMileage>): MileageTrendResult {
        if (priorTrips.size < MIN_PRIOR_TRIPS) {
            return MileageTrendResult(currentKmPerLitre, null, null, isDropSignificant = false)
        }

        val baseline = priorTrips.map { it.averageKmPerLitre }.average()
        val percentChange = ((currentKmPerLitre - baseline) / baseline) * 100.0

        return MileageTrendResult(
            currentKmPerLitre = currentKmPerLitre,
            baselineKmPerLitre = baseline,
            percentChange = percentChange,
            isDropSignificant = percentChange <= -DROP_THRESHOLD_PERCENT
        )
    }

    /** Independent of trip history — see class doc. Returns null when nothing in the latest scan is actually notable. */
    fun currentScanSignal(
        fuelCombustionState: CategoryState,
        fuelCombustionInterpretation: String,
        activeFindings: List<DiagnosticFinding>
    ): String? {
        val misfire = activeFindings.firstOrNull {
            it.title.contains("misfire", ignoreCase = true) || it.relatedDtcCodes.any { code -> code.startsWith("P03") }
        }
        if (misfire != null) {
            return "${misfire.title} — ${misfire.plainLanguageSummary} A misfiring cylinder burns fuel without producing power, which directly costs mileage. ${misfire.recommendedAction}"
        }
        if (activeFindings.isNotEmpty()) {
            val f = activeFindings.first()
            return "${f.title} — ${f.plainLanguageSummary} ${f.recommendedAction}"
        }
        if (fuelCombustionState == CategoryState.WATCH || fuelCombustionState == CategoryState.ATTENTION) {
            return fuelCombustionInterpretation
        }
        return null
    }
}
