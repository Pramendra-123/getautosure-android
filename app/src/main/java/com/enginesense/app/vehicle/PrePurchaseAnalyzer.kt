package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity

enum class PurchaseVerdict { GOOD_BUY, BUY_WITH_CAUTION, AVOID }

data class NegotiationPoint(val title: String, val recommendedAction: String, val severity: Severity)

/** One thing to raise with the seller: a short [title], what the scan actually saw ([evidence]), and what to ask ([advice]). */
data class BuyerSignal(val title: String, val evidence: String, val advice: String)

/** FLAGGED matches a red flag ([PrePurchaseInsight.fraudRiskFlags]); WORTH_ASKING matches a caution note. */
enum class CheckOutcome { CLEAR, FLAGGED, WORTH_ASKING, NOT_AVAILABLE }

/** One line of "what we checked" — NOT_AVAILABLE when this car didn't report what the check needs, never shown as a pass. */
data class PurchaseCheck(val label: String, val outcome: CheckOutcome)

data class PrePurchaseInsight(
    val verdict: PurchaseVerdict,
    val headline: String,
    /** Findings worth raising with the seller — Dealer Pro only; everyone still gets [verdict] and [headline] free. */
    val negotiationPoints: List<NegotiationPoint>,
    /** OBD-side fraud signals, e.g. recently cleared codes. Free for everyone. */
    val fraudRiskFlags: List<BuyerSignal> = emptyList(),
    /** Worth asking about but not a sign of tampering — developing faults, driving with the warning light on, a pre-warmed engine. Free for everyone. */
    val cautionNotes: List<BuyerSignal> = emptyList(),
    /** Empty when there was no scan snapshot to check (the findings-only overload). */
    val checks: List<PurchaseCheck> = emptyList(),
    /** Total km from the ECU (PID 0xA6), for comparing against the dashboard. Null when not read. */
    val ecuOdometerKm: Double? = null,
    /** True/false when the car answered whether it reports PID 0xA6; null when that's unknown (no scan data, adapter error). */
    val odometerSupported: Boolean? = null
)

enum class OdometerMatch { MATCHES, MISMATCH }

/**
 * "Should I buy this car?" — the exact same scan/findings pipeline as checking a car you
 * already own, just read with a purchase decision in mind instead of an ownership status.
 * The verdict is free for everyone (it's just a severity-based read of data already
 * computed); the itemized negotiation points are the Dealer Pro layer, since that's where
 * the repeated, professional use of "which findings justify a lower offer" actually is.
 *
 * Deliberately has no price attached to [negotiationPoints] — see RepairCostReferenceScreen
 * and the PrePurchase screens for why: a specific number here needs real market data this
 * app doesn't have, and a wrong one has real financial consequences for someone's
 * negotiation. The findings themselves are real; a price estimate on top of them wouldn't be.
 */
object PrePurchaseAnalyzer {

    fun analyze(findings: List<DiagnosticFinding>, healthScore: Int?): PrePurchaseInsight =
        analyze(findings, healthScore, readiness = null)

    // A seller who reset the codes to hide a fault has usually driven only a few days since —
    // the car's self-tests need roughly this much driving before they all complete again.
    private const val RECENT_CLEAR_MAX_KM = 200.0
    private const val RECENT_CLEAR_MAX_WARM_UPS = 20.0
    // Past this, a not-ready monitor is just a monitor that's slow to complete (EVAP often is),
    // not a sign of a recent reset.
    private const val CLEARED_LONG_AGO_KM = 1000.0

    // ECU and cluster can legitimately drift a little (rounding, a cluster swapped under warranty
    // and recalibrated), so only a clear gap counts.
    private const val ODOMETER_TOLERANCE_KM = 500.0
    private const val ODOMETER_TOLERANCE_FRACTION = 0.02

    // Below this it's a short hop to the garage, not neglect.
    private const val MIL_DISTANCE_NOTE_KM = 50.0

    // From cold, an engine can't reach this coolant temperature within this many seconds of
    // starting — so hotter than this, this soon, means it was already warm before this start.
    private const val PRE_WARMED_COOLANT_C = 70.0
    private const val PRE_WARMED_MAX_RUN_SECONDS = 180.0

    fun compareOdometer(ecuKm: Double, dashboardKm: Double): OdometerMatch {
        val tolerance = maxOf(ODOMETER_TOLERANCE_KM, ecuKm * ODOMETER_TOLERANCE_FRACTION)
        return if (kotlin.math.abs(ecuKm - dashboardKm) <= tolerance) OdometerMatch.MATCHES else OdometerMatch.MISMATCH
    }

    fun analyze(
        findings: List<DiagnosticFinding>,
        healthScore: Int?,
        readiness: ReadinessStatus?,
        snapshot: VehicleSnapshot? = null
    ): PrePurchaseInsight {
        val worst = findings.minByOrNull { severityRank(it.severity) }?.severity

        // Codes that aren't also stored: stored ones already show up as findings.
        val dtcs = snapshot?.dtcs.orEmpty()
        val storedCodes = dtcs.filter { it.source == DtcSource.STORED }.map { it.code.uppercase() }.toSet()
        val hiddenPermanent = dtcs.filter { it.source == DtcSource.PERMANENT }.map { it.code.uppercase() }.distinct() - storedCodes
        val pendingOnly = dtcs.filter { it.source == DtcSource.PENDING }.map { it.code.uppercase() }.distinct() - storedCodes

        val verdict = when {
            worst == Severity.CRITICAL -> PurchaseVerdict.AVOID
            worst == Severity.MODERATE || worst == Severity.MINOR -> PurchaseVerdict.BUY_WITH_CAUTION
            healthScore == null -> PurchaseVerdict.BUY_WITH_CAUTION
            healthScore < 60 -> PurchaseVerdict.BUY_WITH_CAUTION
            // A fault the car still remembers (or is about to confirm) rules out a clean "good buy".
            hiddenPermanent.isNotEmpty() || pendingOnly.isNotEmpty() -> PurchaseVerdict.BUY_WITH_CAUTION
            else -> PurchaseVerdict.GOOD_BUY
        }

        val headline = when {
            verdict == PurchaseVerdict.AVOID -> "This scan found a serious issue — walk away or get it fixed before you buy."
            healthScore == null -> "This car's computer didn't report enough data to score — that's a limitation of this scan, not a clean bill of health. Get a physical inspection before deciding."
            verdict == PurchaseVerdict.BUY_WITH_CAUTION -> "Buyable, but go in with your eyes open — a few things are worth raising with the seller."
            else -> "Nothing concerning on this scan. A healthy buy, as far as the OBD data can tell."
        }

        val negotiationPoints = findings
            .filter { it.severity == Severity.CRITICAL || it.severity == Severity.MODERATE || it.severity == Severity.MINOR }
            .sortedBy { severityRank(it.severity) }
            .map { NegotiationPoint(it.title, it.recommendedAction, it.severity) }

        val resetSignal = detectResetSignal(readiness, findings, snapshot)
        val permanentSignal = hiddenPermanent.takeIf { it.isNotEmpty() }?.let { codes ->
            val one = codes.size == 1
            BuyerSignal(
                title = "Permanent fault code${if (one) "" else "s"} still stored",
                evidence = "${codes.joinToString(", ")} — ${if (one) "this can't" else "these can't"} be erased with a scanner, and ${if (one) "it's" else "they're"} " +
                    "no longer in the normal fault list. ${if (one) "It was" else "They were"} likely cleared before the fault was fixed.",
                advice = "Ask what was repaired and for the invoice."
            )
        }
        val fraudRiskFlags = listOfNotNull(resetSignal, permanentSignal)

        val milKm = snapshot?.verifiedValue(PidId.DISTANCE_WITH_MIL_ON)
        val pendingSignal = pendingOnly.takeIf { it.isNotEmpty() }?.let { codes ->
            BuyerSignal(
                title = "Developing fault${if (codes.size == 1) "" else "s"} not yet confirmed",
                evidence = "${codes.joinToString(", ")} — the car has noticed a problem once but hasn't turned on the warning light yet.",
                advice = "Ask the seller about it, or scan again after a longer drive."
            )
        }
        val milSignal = milKm?.takeIf { it >= MIL_DISTANCE_NOTE_KM }?.let { km ->
            BuyerSignal(
                title = "Driven with the warning light on",
                evidence = "${"%,d".format(km.toLong())} km with the check-engine light on — a sign the car may not have been looked after.",
                advice = "Ask why the fault wasn't fixed sooner."
            )
        }
        val preWarmed = preWarmedSignal(snapshot)
        val cautionNotes = listOfNotNull(pendingSignal, milSignal, preWarmed)

        val checks = if (snapshot == null) emptyList() else {
            val hasResetEvidence = readiness != null || listOf(PidId.DISTANCE_SINCE_CODES_CLEARED, PidId.WARM_UPS_SINCE_CODES_CLEARED)
                .any { snapshot.verifiedValue(it) != null }
            val canCheckWarm = snapshot.verifiedValue(PidId.COOLANT_TEMP_C) != null && snapshot.verifiedValue(PidId.RUN_TIME_SINCE_START) != null
            listOf(
                PurchaseCheck("Stored fault codes", if (storedCodes.isNotEmpty()) CheckOutcome.FLAGGED else CheckOutcome.CLEAR),
                PurchaseCheck("Hidden permanent codes", if (permanentSignal != null) CheckOutcome.FLAGGED else CheckOutcome.CLEAR),
                PurchaseCheck("Developing (pending) faults", if (pendingSignal != null) CheckOutcome.WORTH_ASKING else CheckOutcome.CLEAR),
                PurchaseCheck(
                    "Codes cleared before the sale",
                    when { resetSignal != null -> CheckOutcome.FLAGGED; hasResetEvidence -> CheckOutcome.CLEAR; else -> CheckOutcome.NOT_AVAILABLE }
                ),
                PurchaseCheck(
                    "Driven with warning light on",
                    when { milSignal != null -> CheckOutcome.WORTH_ASKING; milKm != null -> CheckOutcome.CLEAR; else -> CheckOutcome.NOT_AVAILABLE }
                ),
                PurchaseCheck(
                    "Engine pre-warmed",
                    when { preWarmed != null -> CheckOutcome.WORTH_ASKING; canCheckWarm -> CheckOutcome.CLEAR; else -> CheckOutcome.NOT_AVAILABLE }
                )
            )
        }

        val odometerSupported = when (snapshot?.reading(PidId.ODOMETER)?.status) {
            PidStatus.VERIFIED, PidStatus.SUPPORTED_NO_DATA, PidStatus.IMPLAUSIBLE -> true
            PidStatus.UNSUPPORTED -> false
            PidStatus.TRANSPORT_ERROR, null -> null
        }

        return PrePurchaseInsight(
            verdict, headline, negotiationPoints, fraudRiskFlags,
            cautionNotes = cautionNotes,
            checks = checks,
            ecuOdometerKm = snapshot?.verifiedValue(PidId.ODOMETER),
            odometerSupported = odometerSupported
        )
    }

    private fun preWarmedSignal(snapshot: VehicleSnapshot?): BuyerSignal? {
        val coolantC = snapshot?.verifiedValue(PidId.COOLANT_TEMP_C) ?: return null
        val runSeconds = snapshot.verifiedValue(PidId.RUN_TIME_SINCE_START) ?: return null
        if (coolantC < PRE_WARMED_COOLANT_C || runSeconds > PRE_WARMED_MAX_RUN_SECONDS) return null
        val runText = if (runSeconds < 60) "under a minute" else "${(runSeconds / 60).toInt()} minute${if (runSeconds < 120) "" else "s"}"
        return BuyerSignal(
            title = "Engine was already warm",
            evidence = "Coolant was ${coolantC.toInt()}°C just $runText after starting. A warm engine can hide hard starting, smoke and rattles.",
            advice = "If you didn't just drive it yourself, ask to see it start from cold — ideally after standing overnight."
        )
    }

    private fun detectResetSignal(readiness: ReadinessStatus?, findings: List<DiagnosticFinding>, snapshot: VehicleSnapshot?): BuyerSignal? {
        val distanceKm = snapshot?.verifiedValue(PidId.DISTANCE_SINCE_CODES_CLEARED)
        val warmUps = snapshot?.verifiedValue(PidId.WARM_UPS_SINCE_CODES_CLEARED)
        val minutes = snapshot?.verifiedValue(PidId.TIME_SINCE_CODES_CLEARED)

        // The car's own counters are direct evidence, so they win over the readiness inference.
        // Every counter the car reports must agree it was recent — one odd counter alone isn't enough.
        val counters = listOfNotNull(distanceKm?.let { it <= RECENT_CLEAR_MAX_KM }, warmUps?.let { it <= RECENT_CLEAR_MAX_WARM_UPS })
        if (counters.isNotEmpty() && counters.all { it }) {
            return recentlyClearedSignal(distanceKm, warmUps, minutes, readiness)
        }
        if (distanceKm != null && distanceKm >= CLEARED_LONG_AGO_KM) return null

        return readinessOnlySignal(readiness, findings)
    }

    private fun recentlyClearedSignal(distanceKm: Double?, warmUps: Double?, minutes: Double?, readiness: ReadinessStatus?): BuyerSignal {
        val since = listOfNotNull(
            distanceKm?.let { "${it.toInt()} km" },
            warmUps?.let { "${it.toInt()} engine warm-up${if (it.toInt() == 1) "" else "s"}" },
            minutes?.let { "${it.toInt()} min of running" }
        ).joinToString(" · ")
        val notReady = readiness?.monitors?.filter { it.supported && !it.ready }.orEmpty()
        val selfTests = if (notReady.isEmpty()) "" else " Self-tests not finished yet: ${notReady.joinToString(", ") { it.name }}."
        return BuyerSignal(
            title = "Fault codes were cleared recently",
            evidence = "Only $since since the reset.$selfTests",
            advice = "Ask why they were cleared and what the fault was. Unless the car is nearly new, a reset just before a sale is a red flag."
        )
    }

    /** Classic OBD fingerprint of recently cleared codes: MIL off, no/very few stored DTCs, but supported monitors not yet complete. */
    private fun readinessOnlySignal(readiness: ReadinessStatus?, findings: List<DiagnosticFinding>): BuyerSignal? {
        if (readiness == null) return null
        if (readiness.milOn) return null
        if (readiness.dtcCount > 1) return null

        val supportedNotReady = readiness.monitors.filter { it.supported && !it.ready }
        if (supportedNotReady.isEmpty()) return null

        // If there are active critical/moderate findings, this is likely a real fault, not a clear-code signal
        val hasActiveFaults = findings.any { it.severity == Severity.CRITICAL || it.severity == Severity.MODERATE }
        if (hasActiveFaults) return null

        return BuyerSignal(
            title = "Codes may have been cleared recently",
            evidence = "The warning light is off and no faults are stored, but self-tests haven't finished: ${supportedNotReady.joinToString(", ") { it.name }}.",
            advice = "Ask the seller to drive the car for a full cycle, then scan again."
        )
    }

    private fun severityRank(severity: Severity): Int = when (severity) {
        Severity.CRITICAL -> 0
        Severity.MODERATE -> 1
        Severity.MINOR -> 2
        Severity.INFO -> 3
    }
}
