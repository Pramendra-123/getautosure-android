package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity
import com.enginesense.app.vehicle.ai.CarSize
import com.enginesense.app.vehicle.ai.CommonDtcGlossary
import com.enginesense.app.vehicle.ai.RepairCostEntry
import com.enginesense.app.vehicle.ai.RepairCostReference
import com.enginesense.app.vehicle.ai.forSize
import com.enginesense.app.vehiclelookup.RcLookupResult
import com.enginesense.app.vehiclelookup.RtoRecord
import com.enginesense.app.vehiclelookup.ValidityLine
import com.enginesense.app.vehiclelookup.ValidityStatus
import java.time.LocalDate

/** Where a fault in the report came from — [STORED] codes are findings, the next two come from the car's code lists, and [EXTERIOR] is what the buyer saw on the body or cabin. */
enum class FaultKind { STORED, HIDDEN_PERMANENT, PENDING, EXTERIOR }

/** One row of "Major faults & estimated repair cost". [cost] is null when no tentative range fits — shown as "needs a workshop quote", never guessed. */
data class FaultCostLine(
    val code: String?,
    val title: String,
    val kind: FaultKind,
    val severity: Severity?,
    val cost: RepairCostEntry?,
    /** False for pending faults — not confirmed yet, so not added to the total. */
    val countedInTotal: Boolean
)

/** Everything the car's computer says about distance, plus the dashboard reading the buyer typed in. */
data class OdometerEvidence(
    val ecuKm: Double?,
    /** As [PrePurchaseInsight.odometerSupported]. */
    val ecuSupported: Boolean?,
    val dashboardKm: Double?,
    /** Null unless both readings are present. */
    val match: OdometerMatch?,
    val kmSinceCodesCleared: Double?,
    val warmUpsSinceCodesCleared: Double?,
    val minutesSinceCodesCleared: Double?,
    val kmWithWarningLightOn: Double?
)

/** [fair] from [FairPriceEstimator]; the "after repairs" range takes off the repair-cost range so a buyer sees what the car is worth as it stands. */
data class ValueEstimate(val fair: FairPriceEstimate, val afterRepairsLow: Double, val afterRepairsHigh: Double)

/**
 * A dealer's own asking range, raised or dropped from the estimate as they see fit. Always shown
 * beside the GetAutoSure estimate and labelled as the dealer's — never in place of it — so a buyer
 * can see how the two compare.
 */
data class DealerPrice(val low: Double, val high: Double, val dealerName: String) {
    /** "₹20,000 above the GetAutoSure estimate", comparing range midpoints; null without an estimate. */
    fun comparedTo(estimate: FairPriceEstimate?): String? {
        estimate ?: return null
        val diff = (low + high) / 2 - (estimate.lowEstimate + estimate.highEstimate) / 2
        val amount = "₹" + "%,.0f".format(kotlin.math.abs(diff))
        return when {
            kotlin.math.abs(diff) < 1000 -> "In line with the GetAutoSure estimate"
            diff > 0 -> "$amount above the GetAutoSure estimate"
            else -> "$amount below the GetAutoSure estimate"
        }
    }

    companion object {
        /** A typed range, made sane: swapped if back to front, null if either end is missing or not positive. */
        fun from(low: Double?, high: Double?, dealerName: String): DealerPrice? {
            if (low == null || high == null || low <= 0 || high <= 0) return null
            return DealerPrice(minOf(low, high), maxOf(low, high), dealerName)
        }
    }
}

/**
 * Everything the pre-purchase PDF shows, worked out once from the scan plus what the buyer added
 * on the Used Car Check screen (dashboard km, RTO lookup, make and model). Kept free of Android
 * so it can be unit-tested; [com.enginesense.app.report.PrePurchaseReportPdfGenerator] only draws it.
 */
data class PrePurchaseReport(
    val verdict: PurchaseVerdict,
    val headline: String,
    /** The analyzer's red flags plus any from the buyer's own checks (odometer, RTO record). */
    val redFlags: List<BuyerSignal>,
    val worthAsking: List<BuyerSignal>,
    val checks: List<PurchaseCheck>,
    val odometer: OdometerEvidence,
    val rc: RcLookupResult?,
    val registration: String?,
    val manufactureYear: Int?,
    val faults: List<FaultCostLine>,
    val repairMinInr: Int,
    val repairMaxInr: Int,
    val value: ValueEstimate?,
    /** Why [value] is missing, phrased for the buyer; null when [value] is set. */
    val valueMissingReason: String?,
    /** Short facts behind the verdict ("Codes last cleared 10,921 km ago"), so a "Good buy" shows its working. Only facts the scan or RC actually gave. */
    val evidence: List<String> = emptyList(),
    /** Hidden (permanent) and developing (pending) codes — the engine health score counts only stored ones, so the report says how many it leaves out. */
    val faultsOutsideScore: Int = 0,
    /** The RC's insurance/PUC/fitness/tax dates and what they mean today; empty without an RTO check. */
    val validity: List<ValidityLine> = emptyList(),
    /** Set only on a dealer's report, when the dealer entered their own price. */
    val dealerPrice: DealerPrice? = null
)

object PrePurchaseReportBuilder {

    fun build(
        findings: List<DiagnosticFinding>,
        healthScore: Int?,
        readiness: ReadinessStatus?,
        snapshot: VehicleSnapshot?,
        vin: String?,
        dashboardKm: Double?,
        rc: RcLookupResult?,
        registration: String?,
        exShowroomPriceInr: Double?,
        buyerEnteredYear: Int?,
        currentYear: Int,
        today: LocalDate = LocalDate.now(),
        /** What the buyer counted on the body, tyres and cabin — see [ExteriorDamage]. */
        exteriorCounts: Map<String, Int> = emptyMap(),
        /** Brand as in [com.enginesense.app.vehicle.pricing.ExShowroomPrices] and RTO fuel type — both nudge the fair price. */
        make: String? = null,
        fuelType: String? = null
    ): PrePurchaseReport {
        val insight = PrePurchaseAnalyzer.analyze(findings, healthScore, readiness, snapshot)

        val ecuKm = insight.ecuOdometerKm
        val odometerMatch = if (ecuKm != null && dashboardKm != null) PrePurchaseAnalyzer.compareOdometer(ecuKm, dashboardKm) else null
        val odometer = OdometerEvidence(
            ecuKm = ecuKm,
            ecuSupported = insight.odometerSupported,
            dashboardKm = dashboardKm,
            match = odometerMatch,
            kmSinceCodesCleared = snapshot?.verifiedValue(PidId.DISTANCE_SINCE_CODES_CLEARED),
            warmUpsSinceCodesCleared = snapshot?.verifiedValue(PidId.WARM_UPS_SINCE_CODES_CLEARED),
            minutesSinceCodesCleared = snapshot?.verifiedValue(PidId.TIME_SINCE_CODES_CLEARED),
            kmWithWarningLightOn = snapshot?.verifiedValue(PidId.DISTANCE_WITH_MIL_ON)
        )

        val odometerFlag = if (odometerMatch == OdometerMatch.MISMATCH && ecuKm != null && dashboardKm != null) BuyerSignal(
            title = "Dashboard km doesn't match the car's computer",
            evidence = "Dashboard ${"%,d".format(dashboardKm.toLong())} km, engine computer ${"%,d".format(ecuKm.toLong())} km — " +
                "a gap of ${"%,d".format(kotlin.math.abs(ecuKm - dashboardKm).toLong())} km. It can mean the odometer was changed, " +
                "or that the instrument cluster or engine computer was replaced.",
            advice = "Ask why, and ask for service records and invoices showing the km over the years."
        ) else null
        // Dealer-mode RC details (null in customer mode, so these never fire for a regular buyer).
        val blacklistFlag = rc?.blacklistStatus?.takeIf { RtoRecord.isBlacklisted(it) }?.let { status ->
            BuyerSignal(
                title = "Car is blacklisted on the RTO record",
                evidence = "The RTO lists this car as blacklisted ($status). A blacklisted car usually can't be transferred to you.",
                advice = "Don't pay until the seller gets it cleared at the RTO and the record shows it."
            )
        }
        val rcStatusFlag = rc?.rcStatus?.takeIf { !RtoRecord.isActive(it) }?.let { status ->
            BuyerSignal(
                title = "RC isn't active",
                evidence = "The RTO record shows the RC status as \"$status\", not active.",
                advice = "Ask why, and don't pay until the RC is active again — an inactive RC can block the transfer."
            )
        }
        val loanFlag = if (rc?.financed == true) BuyerSignal(
            title = "Loan on the car",
            evidence = "The RC shows the car is hypothecated" + (rc.financer?.let { " to $it" } ?: "") + " — the lender has a claim on it until the loan is closed.",
            advice = "Ask for the lender's NOC and a signed Form 35, and make sure the loan is closed before you pay."
        ) else null
        // These mean the car's papers or history don't add up — on their own they rule the car out.
        val seriousFlags = listOfNotNull(odometerFlag, blacklistFlag, rcStatusFlag)
        val redFlags = insight.fraudRiskFlags + seriousFlags + listOfNotNull(loanFlag)

        val checks = insight.checks + listOf(
            PurchaseCheck(
                "Odometer: dashboard vs computer",
                when (odometerMatch) {
                    OdometerMatch.MATCHES -> CheckOutcome.CLEAR
                    OdometerMatch.MISMATCH -> CheckOutcome.FLAGGED
                    null -> CheckOutcome.NOT_AVAILABLE
                }
            )
        ) + if (rc?.hasDealerDetails != true) emptyList() else listOf(
            PurchaseCheck(
                "No loan on the car",
                when (rc.financed) { true -> CheckOutcome.FLAGGED; false -> CheckOutcome.CLEAR; null -> CheckOutcome.NOT_AVAILABLE }
            ),
            PurchaseCheck(
                "Not blacklisted",
                when { rc.blacklistStatus == null -> CheckOutcome.NOT_AVAILABLE; blacklistFlag != null -> CheckOutcome.FLAGGED; else -> CheckOutcome.CLEAR }
            ),
            PurchaseCheck(
                "RC active",
                when { rc.rcStatus == null -> CheckOutcome.NOT_AVAILABLE; rcStatusFlag != null -> CheckOutcome.FLAGGED; else -> CheckOutcome.CLEAR }
            )
        )

        // Papers or history that don't add up — or several signs of hidden faults together — mean
        // walk away, however healthy the engine looks. A single unexplained flag rules out "good buy".
        val avoidForFlags = insight.verdict != PurchaseVerdict.AVOID &&
            (seriousFlags.isNotEmpty() || insight.fraudRiskFlags.size >= 2)
        val capped = !avoidForFlags && insight.verdict == PurchaseVerdict.GOOD_BUY && redFlags.isNotEmpty()
        val verdict = when {
            avoidForFlags -> PurchaseVerdict.AVOID
            capped -> PurchaseVerdict.BUY_WITH_CAUTION
            else -> insight.verdict
        }
        val headline = when {
            avoidForFlags && seriousFlags.isNotEmpty() ->
                "This car's records don't add up — ${seriousFlags.first().title.replaceFirstChar { it.lowercase() }}. Don't buy unless the seller can prove why."
            avoidForFlags -> "Several signs that faults were hidden before the sale. Don't buy unless the seller can explain them."
            capped -> "No serious faults found, but there's a red flag the seller needs to explain before you buy."
            else -> insight.headline
        }

        val validity = rc?.let { RtoRecord.validity(it, today) }.orEmpty()
        val validityNotes = validity.filter { it.status == ValidityStatus.EXPIRED }
            .map { line ->
                BuyerSignal(
                    title = "${line.label} has expired",
                    evidence = "The RTO record shows ${line.label.lowercase()} valid until ${line.display}.",
                    advice = "Ask the seller to renew it before the sale, or take the cost off the price."
                )
            }

        val faults = faultLines(findings, snapshot, CarSize.fromExShowroom(exShowroomPriceInr)) + ExteriorDamage.lines(exteriorCounts)
        val priced = faults.filter { it.countedInTotal }.mapNotNull { it.cost }.distinctBy { it.category }
        val repairMin = priced.sumOf { it.minInr }
        val repairMax = priced.sumOf { it.maxInr }

        val manufactureYear = rc?.manufacturingMonth?.take(4)?.toIntOrNull() ?: buyerEnteredYear
        val km = (dashboardKm ?: ecuKm)?.toInt()
        val (value, valueMissing) = when {
            exShowroomPriceInr == null || exShowroomPriceInr <= 0 ->
                null to "Pick the car's make and model on the Used Car Check screen to see a tentative price."
            manufactureYear == null ->
                null to "Add the year of manufacture (or check RTO records) to see a fair value."
            else -> {
                val age = (currentYear - manufactureYear).toDouble().coerceAtLeast(0.5)
                val fair = FairPriceEstimator.estimate(exShowroomPriceInr, age, km, make, fuelType)
                ValueEstimate(
                    fair = fair,
                    afterRepairsLow = (fair.lowEstimate - repairMax).coerceAtLeast(0.0),
                    afterRepairsHigh = (fair.highEstimate - repairMin).coerceAtLeast(0.0)
                ) to null
            }
        }

        return PrePurchaseReport(
            verdict = verdict,
            headline = headline,
            redFlags = redFlags,
            worthAsking = insight.cautionNotes + validityNotes,
            checks = checks,
            odometer = odometer,
            rc = rc,
            registration = registration?.takeIf { it.isNotBlank() } ?: rc?.registrationNumber,
            manufactureYear = manufactureYear,
            faults = faults,
            repairMinInr = repairMin,
            repairMaxInr = repairMax,
            value = value,
            valueMissingReason = valueMissing,
            // Reassurance next to "Avoid" would undercut it — the red flags speak for that verdict.
            evidence = if (verdict == PurchaseVerdict.AVOID) emptyList() else evidence(readiness, snapshot, odometer, rc),
            faultsOutsideScore = faults.count { it.kind == FaultKind.HIDDEN_PERMANENT || it.kind == FaultKind.PENDING },
            validity = validity
        )
    }

    /** Clean-bill facts only — each one is something the scan or RC positively showed, never the absence of data. */
    private fun evidence(readiness: ReadinessStatus?, snapshot: VehicleSnapshot?, odometer: OdometerEvidence, rc: RcLookupResult?): List<String> = buildList {
        if (readiness != null && !readiness.milOn) add("Warning light off")
        odometer.kmSinceCodesCleared?.takeIf { it >= 1000 }?.let { add("Codes last cleared ${"%,d".format(it.toLong())} km ago") }
        readiness?.monitors?.filter { it.supported }?.takeIf { it.isNotEmpty() && it.all { m -> m.ready } }?.let { supported ->
            add("All ${supported.size} self-tests complete")
        }
        snapshot?.verifiedValue(PidId.CONTROL_MODULE_VOLTAGE)?.takeIf { it in 13.5..14.8 }?.let { add("Charging ${"%.1f".format(it)} V") }
        if (odometer.match == OdometerMatch.MATCHES) add("Dashboard km matches the computer")
    }

    /** [size] narrows each repair range to the car's class when the model is known. */
    private fun faultLines(findings: List<DiagnosticFinding>, snapshot: VehicleSnapshot?, size: CarSize?): List<FaultCostLine> {
        val stored = findings
            .filter { it.severity != Severity.INFO && !it.isUnconfirmedCode }
            .sortedBy { it.severity.ordinal }
            .map { f ->
                val code = f.relatedDtcCodes.firstOrNull()
                // Always a tentative range, as everywhere else: the usual cost for that system when no part matches.
                FaultCostLine(code, f.title, FaultKind.STORED, f.severity, (costFor(f.title, code) ?: RepairCostReference.fallbackFor(code)).forSize(size), countedInTotal = true)
            }
        val storedCodes = stored.mapNotNull { it.code?.uppercase() }.toSet() +
            snapshot?.dtcs.orEmpty().filter { it.source == DtcSource.STORED }.map { it.code.uppercase() }
        fun fromCodes(source: DtcSource, kind: FaultKind, counted: Boolean) = snapshot?.dtcs.orEmpty()
            .filter { it.source == source }.map { it.code.uppercase() }.distinct()
            .filter { it !in storedCodes }
            .map { code ->
                val meaning = CommonDtcGlossary.lookup(code)?.meaning ?: "Fault code $code"
                FaultCostLine(code, meaning, kind, severity = null, cost = costFor(meaning, code)?.forSize(size), countedInTotal = counted)
            }
        return stored + fromCodes(DtcSource.PERMANENT, FaultKind.HIDDEN_PERMANENT, counted = true) +
            fromCodes(DtcSource.PENDING, FaultKind.PENDING, counted = false)
    }

    /** The finding's own wording first, then the code's standard meaning — never its list of possible causes, which would price a guess. */
    private fun costFor(title: String, code: String?): RepairCostEntry? =
        RepairCostReference.findByTitle(title)
            ?: code?.let { CommonDtcGlossary.lookup(it)?.meaning }?.let { RepairCostReference.findByTitle(it) }
}
