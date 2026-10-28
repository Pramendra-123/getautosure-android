package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity
import com.enginesense.app.vehicle.ai.CommonDtcGlossary
import com.enginesense.app.vehicle.ai.RepairCostEntry
import com.enginesense.app.vehicle.ai.RepairCostReference
import com.enginesense.app.vehiclelookup.RcLookupResult

/** Where a fault in the report came from — only [STORED] codes are findings; the other two come straight from the car's code lists. */
enum class FaultKind { STORED, HIDDEN_PERMANENT, PENDING }

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
 * Everything the pre-purchase PDF shows, worked out once from the scan plus what the buyer added
 * on the Used Car Check screen (dashboard km, RTO lookup, ex-showroom price). Kept free of Android
 * so it can be unit-tested; [com.enginesense.app.report.PrePurchaseReportPdfGenerator] only draws it.
 */
data class PrePurchaseReport(
    val verdict: PurchaseVerdict,
    val headline: String,
    /** The analyzer's red flags plus any from the buyer's own checks (odometer, chassis). */
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
    val valueMissingReason: String?
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
        currentYear: Int
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
        val chassisFlag = if (rc?.chassisMatch == false && vin != null) BuyerSignal(
            title = "Car's computer doesn't match the RC",
            evidence = "The VIN this car's engine computer reports doesn't match the chassis number on its RTO record.",
            advice = "Ask whether the engine computer was replaced, and check the chassis number stamped on the body against the RC."
        ) else null
        val redFlags = insight.fraudRiskFlags + listOfNotNull(odometerFlag, chassisFlag)

        val checks = insight.checks + listOf(
            PurchaseCheck(
                "Odometer: dashboard vs computer",
                when (odometerMatch) {
                    OdometerMatch.MATCHES -> CheckOutcome.CLEAR
                    OdometerMatch.MISMATCH -> CheckOutcome.FLAGGED
                    null -> CheckOutcome.NOT_AVAILABLE
                }
            ),
            PurchaseCheck(
                "Chassis number matches RC",
                when {
                    vin == null || rc == null || rc.chassisMatch == null -> CheckOutcome.NOT_AVAILABLE
                    rc.chassisMatch -> CheckOutcome.CLEAR
                    else -> CheckOutcome.FLAGGED
                }
            )
        )

        // A red flag the seller hasn't explained rules out a clean "good buy", whatever the faults say.
        val capped = insight.verdict == PurchaseVerdict.GOOD_BUY && redFlags.isNotEmpty()
        val verdict = if (capped) PurchaseVerdict.BUY_WITH_CAUTION else insight.verdict
        val headline = if (capped) "No serious faults found, but there's a red flag the seller needs to explain before you buy." else insight.headline

        val faults = faultLines(findings, snapshot)
        val priced = faults.filter { it.countedInTotal }.mapNotNull { it.cost }.distinctBy { it.category }
        val repairMin = priced.sumOf { it.minInr }
        val repairMax = priced.sumOf { it.maxInr }

        val manufactureYear = rc?.manufacturingMonth?.take(4)?.toIntOrNull() ?: buyerEnteredYear
        val km = (dashboardKm ?: ecuKm)?.toInt()
        val (value, valueMissing) = when {
            exShowroomPriceInr == null || exShowroomPriceInr <= 0 ->
                null to "Add the car's ex-showroom price on the Used Car Check screen to see a fair value — this app has no price catalogue, so it isn't guessed."
            manufactureYear == null ->
                null to "Add the year of manufacture (or check RTO records) to see a fair value."
            else -> {
                val age = (currentYear - manufactureYear).toDouble().coerceAtLeast(0.5)
                val fair = FairPriceEstimator.estimate(exShowroomPriceInr, age, km)
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
            worthAsking = insight.cautionNotes,
            checks = checks,
            odometer = odometer,
            rc = rc,
            registration = registration?.takeIf { it.isNotBlank() } ?: rc?.registrationNumber,
            manufactureYear = manufactureYear,
            faults = faults,
            repairMinInr = repairMin,
            repairMaxInr = repairMax,
            value = value,
            valueMissingReason = valueMissing
        )
    }

    private fun faultLines(findings: List<DiagnosticFinding>, snapshot: VehicleSnapshot?): List<FaultCostLine> {
        val stored = findings
            .filter { it.severity != Severity.INFO }
            .sortedBy { it.severity.ordinal }
            .map { f ->
                val code = f.relatedDtcCodes.firstOrNull()
                FaultCostLine(code, f.title, FaultKind.STORED, f.severity, costFor(f.title, code), countedInTotal = true)
            }
        val storedCodes = stored.mapNotNull { it.code?.uppercase() }.toSet() +
            snapshot?.dtcs.orEmpty().filter { it.source == DtcSource.STORED }.map { it.code.uppercase() }
        fun fromCodes(source: DtcSource, kind: FaultKind, counted: Boolean) = snapshot?.dtcs.orEmpty()
            .filter { it.source == source }.map { it.code.uppercase() }.distinct()
            .filter { it !in storedCodes }
            .map { code ->
                val meaning = CommonDtcGlossary.lookup(code)?.meaning ?: "Fault code $code"
                FaultCostLine(code, meaning, kind, severity = null, cost = costFor(meaning, code), countedInTotal = counted)
            }
        return stored + fromCodes(DtcSource.PERMANENT, FaultKind.HIDDEN_PERMANENT, counted = true) +
            fromCodes(DtcSource.PENDING, FaultKind.PENDING, counted = false)
    }

    /** The finding's own wording first, then the code's standard meaning — never its list of possible causes, which would price a guess. */
    private fun costFor(title: String, code: String?): RepairCostEntry? =
        RepairCostReference.findByTitle(title)
            ?: code?.let { CommonDtcGlossary.lookup(it)?.meaning }?.let { RepairCostReference.findByTitle(it) }
}
