package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity
import com.enginesense.app.vehicle.ai.CarSize
import com.enginesense.app.vehicle.ai.CommonDtcGlossary
import com.enginesense.app.vehicle.ai.RepairCostEntry
import com.enginesense.app.vehicle.ai.RepairCostReference
import com.enginesense.app.vehicle.ai.forSize
import com.enginesense.app.vehicle.pricing.AiPriceEstimate
import com.enginesense.app.vehicle.pricing.MarketPriceChooser
import com.enginesense.app.vehicle.pricing.PriceBasis
import com.enginesense.app.vehiclelookup.RcLookupResult
import com.enginesense.app.vehiclelookup.RtoRecord
import com.enginesense.app.vehiclelookup.ValidityLine
import com.enginesense.app.vehiclelookup.ValidityStatus
import java.time.LocalDate

/** Where a fault in the report came from — [STORED] codes are findings, the next two come from the car's code lists, [EXTERIOR] is what the buyer saw on the body or cabin, and [DRIVE] what the test drive found. */
enum class FaultKind { STORED, HIDDEN_PERMANENT, PENDING, EXTERIOR, DRIVE }

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
    val kmWithWarningLightOn: Double?,
    /** The seller's range when the dashboard couldn't be read — priced at its middle, never checked. */
    val sellerRange: KmRange? = null,
    /** Km a year from a real reading (dashboard, else the car's computer) and the car's age. */
    val perYear: KmPerYear? = null
) {
    /** The km the price used. */
    val priceKm: Double? get() = dashboardKm ?: sellerRange?.midKm?.toDouble() ?: ecuKm

    /** What the buyer gave, in words: the exact reading or the seller's range. */
    val enteredText: String? get() = dashboardKm?.let { "%,d km".format(it.toLong()) }
        ?: sellerRange?.let { "${it.label} km (seller's word, not checked)" }
}

/**
 * [fair] is the price before repairs: the AI market price when [basis] is AI_MARKET, else our own
 * [FairPriceEstimator] figure. The "after repairs" range takes off the repair-cost range so a buyer
 * sees what the car is worth as it stands. [ai] is kept even when set aside, and [note] says why.
 */
data class ValueEstimate(
    val fair: FairPriceEstimate,
    val afterRepairsLow: Double,
    val afterRepairsHigh: Double,
    val basis: PriceBasis = PriceBasis.FORMULA,
    val ai: AiPriceEstimate? = null,
    /** Our formula's own range, for comparison; null when the car isn't in our price list. */
    val formula: FairPriceEstimate? = null,
    val note: String? = null
)

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
    val dealerPrice: DealerPrice? = null,
    /** The guided test drive, when the buyer did one. */
    val drive: DriveResult? = null
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
        fuelType: String? = null,
        /** The AI market price for this car, when the price-estimate function answered; our formula is the backup. */
        aiPrice: AiPriceEstimate? = null,
        /** Accident-repair signs the buyer saw on the look-around, in plain words; any one is a red flag. */
        accidentSigns: List<String> = emptyList(),
        /** The buyer's range when the dashboard can't be read; ignored when [dashboardKm] is given. */
        kmRange: KmRange? = null,
        /** The guided test drive, when the buyer did one. */
        drive: DriveResult? = null
    ): PrePurchaseReport {
        val driveWatch = drive?.findings.orEmpty().filter { it.check == DriveCheck.WATCH }
        val driveNotes = driveWatch.map { f ->
            BuyerSignal(
                title = "Test drive: " + f.title.replaceFirstChar { it.lowercase() },
                evidence = f.detail,
                advice = if (f.cost != null) "Get it checked before you pay, or take the repair cost off the price." else "Drive it again for longer before you decide."
            )
        }
        val insight = PrePurchaseAnalyzer.analyze(findings, healthScore, readiness, snapshot)
        val manufactureYear = rc?.manufacturingMonth?.take(4)?.toIntOrNull() ?: buyerEnteredYear
        val sellerRange = kmRange.takeIf { dashboardKm == null }

        val ecuKm = insight.ecuOdometerKm
        val carAge = OdometerReading.ageYears(RtoRecord.parseDate(rc?.registrationDate), manufactureYear, today)
        val perYear = (dashboardKm ?: ecuKm)?.takeIf { it >= 100 }?.let { km -> carAge?.let { OdometerReading.perYear(km, it) } }
        val odometerMatch = if (ecuKm != null && dashboardKm != null) PrePurchaseAnalyzer.compareOdometer(ecuKm, dashboardKm) else null
        val odometer = OdometerEvidence(
            ecuKm = ecuKm,
            ecuSupported = insight.odometerSupported,
            dashboardKm = dashboardKm,
            match = odometerMatch,
            kmSinceCodesCleared = snapshot?.verifiedValue(PidId.DISTANCE_SINCE_CODES_CLEARED),
            warmUpsSinceCodesCleared = snapshot?.verifiedValue(PidId.WARM_UPS_SINCE_CODES_CLEARED),
            minutesSinceCodesCleared = snapshot?.verifiedValue(PidId.TIME_SINCE_CODES_CLEARED),
            kmWithWarningLightOn = snapshot?.verifiedValue(PidId.DISTANCE_WITH_MIL_ON),
            sellerRange = sellerRange,
            perYear = perYear
        )
        val kmNotes = listOfNotNull(
            if (odometerMatch == OdometerMatch.SMALL_GAP && ecuKm != null && dashboardKm != null) BuyerSignal(
                title = "Small gap between the dashboard and the car's computer",
                evidence = "Dashboard ${"%,d".format(dashboardKm.toLong())} km, engine computer ${"%,d".format(ecuKm.toLong())} km: " +
                    "${"%,d".format(kotlin.math.abs(ecuKm - dashboardKm).toLong())} km apart. That's often a typo or a cluster replaced and set close to the old reading. A rolled-back odometer is usually much further off.",
                advice = "Read the dashboard again. If the gap is real, ask whether the cluster was ever replaced, and check the service book."
            ) else null,
            sellerRange?.let { range ->
                BuyerSignal(
                    title = "Odometer not checked",
                    evidence = "The dashboard couldn't be read, so the km (${range.label}) is the seller's word. A dead or replaced instrument cluster is one way the km gets reset." +
                        (ecuKm?.let { e ->
                            if (range.contains(e)) " The car's computer says ${"%,d".format(e.toLong())} km, which fits."
                            else " The car's computer says ${"%,d".format(e.toLong())} km, which doesn't fit."
                        } ?: ""),
                    advice = "Ask why the dashboard doesn't work, and check the km on the last service bill."
                )
            },
            perYear?.takeIf { it.pace == KmPace.SUSPICIOUSLY_LOW }?.let { p ->
                BuyerSignal(
                    title = "Very low km for its age",
                    evidence = "About ${"%,d".format(p.kmPerYear)} km a year over ${"%.1f".format(p.ageYears)} years, far under the usual 12–15,000. Very low km is a common sign of a rolled-back odometer.",
                    advice = "Check the service book: each service should show more km than the one before."
                )
            },
            perYear?.takeIf { it.pace == KmPace.HIGH }?.let { p ->
                BuyerSignal(
                    title = "Driven a lot: about ${"%,d".format(p.kmPerYear)} km a year",
                    evidence = "That's above the usual 12–15,000 km a year, so expect more wear and a lower price. The price already counts the km.",
                    advice = "Look closely at the clutch, suspension and tyres, and ask for the service history."
                )
            }
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
        // A loan is normal paperwork, not a red flag: it's closed as part of the transfer. It's a step
        // to get right, so it sits with the things worth asking about and never changes the verdict.
        val loanNote = if (rc?.financed == true) BuyerSignal(
            title = "Loan on the car" + (rc.financer?.let { " with $it" } ?: ""),
            evidence = "The RC shows a loan (hypothecation). That's common, and it's closed when the car is transferred to you.",
            advice = "Before you pay in full, get the bank's NOC and a signed Form 35, or pay the bank directly to close the loan."
        ) else null
        // Accident repair the buyer spotted on the look-around — a red flag, but not one that rules the car out alone.
        val accidentFlag = accidentSigns.takeIf { it.isNotEmpty() }?.let { signs ->
            BuyerSignal(
                title = "Signs of accident repair",
                evidence = "You saw: " + signs.joinToString("; ").replaceFirstChar { it.lowercase() } + ".",
                advice = "Ask: \"Has this car had any body work? Can I see the insurance claim history?\" Get a body shop to look before you pay."
            )
        }
        // These mean the car's papers or history don't add up — on their own they rule the car out.
        val seriousFlags = listOfNotNull(odometerFlag, blacklistFlag, rcStatusFlag)
        val redFlags = insight.fraudRiskFlags + seriousFlags + listOfNotNull(accidentFlag)

        val checks = insight.checks + listOf(
            PurchaseCheck(
                "Odometer: dashboard vs computer",
                when (odometerMatch) {
                    OdometerMatch.MATCHES -> CheckOutcome.CLEAR
                    OdometerMatch.SMALL_GAP -> CheckOutcome.WORTH_ASKING
                    OdometerMatch.MISMATCH -> CheckOutcome.FLAGGED
                    null -> CheckOutcome.NOT_AVAILABLE
                }
            ),
            PurchaseCheck(
                "Test drive",
                when {
                    drive == null || drive.findings.isEmpty() -> CheckOutcome.NOT_AVAILABLE
                    driveWatch.isNotEmpty() -> CheckOutcome.WORTH_ASKING
                    else -> CheckOutcome.CLEAR
                }
            )
        ) + if (rc?.hasDealerDetails != true) emptyList() else listOf(
            PurchaseCheck(
                "No loan on the car",
                when (rc.financed) { true -> CheckOutcome.WORTH_ASKING; false -> CheckOutcome.CLEAR; null -> CheckOutcome.NOT_AVAILABLE }
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
        // A gearbox that slips or an engine that runs hot on the drive isn't a red flag — nobody hid it —
        // but it's not a good buy until it's checked.
        val driveSerious = driveWatch.firstOrNull { it.serious }
        val cappedByDrive = !avoidForFlags && !capped && insight.verdict == PurchaseVerdict.GOOD_BUY && driveSerious != null
        val verdict = when {
            avoidForFlags -> PurchaseVerdict.AVOID
            capped || cappedByDrive -> PurchaseVerdict.BUY_WITH_CAUTION
            else -> insight.verdict
        }
        val headline = when {
            avoidForFlags && seriousFlags.isNotEmpty() ->
                "This car's records don't add up — ${seriousFlags.first().title.replaceFirstChar { it.lowercase() }}. Don't buy unless the seller can prove why."
            avoidForFlags -> "Several signs that faults were hidden before the sale. Don't buy unless the seller can explain them."
            capped && redFlags.size == 1 ->
                "No serious faults found, but ask the seller about this before you buy: ${redFlags.first().title.replaceFirstChar { it.lowercase() }}."
            capped -> "No serious faults found, but ask the seller about these ${redFlags.size} red flags before you buy: " +
                redFlags.joinToString("; ") { it.title.replaceFirstChar { c -> c.lowercase() } } + "."
            cappedByDrive -> "No serious faults in the scan, but the test drive found this: ${driveSerious!!.title.replaceFirstChar { it.lowercase() }}. Get it checked before you buy."
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

        val faults = faultLines(findings, snapshot, CarSize.fromExShowroom(exShowroomPriceInr)) + ExteriorDamage.lines(exteriorCounts) +
            driveWatch.filter { it.cost != null }.map { FaultCostLine(null, it.title, FaultKind.DRIVE, null, it.cost, countedInTotal = true) }
        val priced = faults.filter { it.countedInTotal }.mapNotNull { it.cost }.distinctBy { it.category }
        val repairMin = priced.sumOf { it.minInr }
        val repairMax = priced.sumOf { it.maxInr }

        val km = odometer.priceKm?.toInt()
        val age = manufactureYear?.let { (currentYear - it).toDouble().coerceAtLeast(0.5) }
        val formula = if (age != null && exShowroomPriceInr != null && exShowroomPriceInr > 0)
            FairPriceEstimator.estimate(exShowroomPriceInr, age, km, make, fuelType) else null
        val choice = if (age != null) MarketPriceChooser.choose(aiPrice, formula?.lowEstimate, formula?.highEstimate) else null
        val (value, valueMissing) = when {
            manufactureYear == null ->
                null to "Add the year of manufacture (or check RTO records) to see a fair value."
            choice == null ->
                null to "Pick the car's make and model on the Used Car Check screen to see a tentative price."
            else -> {
                val fair = if (choice.basis == PriceBasis.FORMULA && formula != null) formula else {
                    val newPrice = aiPrice?.newPriceInr ?: exShowroomPriceInr
                    FairPriceEstimate(
                        lowEstimate = choice.low,
                        highEstimate = choice.high,
                        depreciationPercent = newPrice?.takeIf { it > 0 }
                            ?.let { (100 * (1 - (choice.low + choice.high) / 2 / it)).toInt().coerceIn(0, 95) } ?: 0,
                        ageYears = age!!.toInt(),
                        beyondPublishedSchedule = age > 10.0
                    )
                }
                ValueEstimate(
                    fair = fair,
                    afterRepairsLow = (fair.lowEstimate - repairMax).coerceAtLeast(0.0),
                    afterRepairsHigh = (fair.highEstimate - repairMin).coerceAtLeast(0.0),
                    basis = choice.basis,
                    ai = aiPrice,
                    formula = formula,
                    note = choice.note
                ) to null
            }
        }

        return PrePurchaseReport(
            verdict = verdict,
            headline = headline,
            redFlags = redFlags,
            worthAsking = insight.cautionNotes + kmNotes + driveNotes + validityNotes + listOfNotNull(loanNote),
            drive = drive,
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

/**
 * The report in plain lines for the AI Mechanic, so "what was the red flag?" or "is the loan a
 * problem?" get answered from what the buyer sees, not guessed. [carName] as shown on the report.
 */
fun PrePurchaseReport.aiSummaryLines(carName: String?): List<String> = buildList {
    fun inr(v: Double) = "₹" + "%,.0f".format(v)
    carName?.let { add("Car being checked: $it" + (registration?.let { r -> " ($r)" } ?: "") + (manufactureYear?.let { y -> ", made $y" } ?: "")) }
    add("Verdict: ${verdict.name.lowercase().replace('_', ' ')}. $headline")
    if (redFlags.isEmpty()) add("Red flags: none.")
    redFlags.forEach { add("Red flag — ${it.title}: ${it.evidence} What to do: ${it.advice}") }
    worthAsking.forEach { add("Worth asking (not a red flag) — ${it.title}: ${it.evidence} What to do: ${it.advice}") }
    checks.forEach { add("Check — ${it.label}: ${it.outcome.name.lowercase().replace('_', ' ')}") }
    listOfNotNull(
        odometer.enteredText?.let { "dashboard $it" },
        odometer.ecuKm?.let { "car's computer ${"%,d".format(it.toLong())} km" },
        odometer.perYear?.let { "about ${"%,d".format(it.kmPerYear)} km a year" }
    ).takeIf { it.isNotEmpty() }?.let { add("Odometer: " + it.joinToString(", ") + ".") }
    drive?.let { d ->
        add("Test drive: ${d.score}/5, ${d.durationS / 60} min, ${"%.1f".format(d.distanceKm)} km, ${d.legsDone} of 5 legs. ${d.headline}")
        d.findings.forEach { f -> add("Test drive ${if (f.check == DriveCheck.WATCH) "issue" else "ok"} — ${f.title}: ${f.detail}") }
    } ?: add("Test drive: not done yet, so the gearbox, brakes and suspension aren't checked.")
    rc?.let { r ->
        val rto = listOfNotNull(
            r.model?.let { "model on RC $it" },
            r.ownerCount?.let { "owners so far $it" },
            r.rcStatus?.let { "RC status $it" },
            r.financed?.let { if (it) "loan on the car" + (r.financer?.let { f -> " with $f" } ?: "") else "no loan" },
            r.insuranceUpto?.let { "insurance until $it" }
        )
        if (rto.isNotEmpty()) add("RTO record: " + rto.joinToString(", ") + ".")
    }
    faults.forEach { f -> add("Repair item — ${f.title}" + (f.cost?.let { c -> ": ${inr(c.minInr.toDouble())}–${inr(c.maxInr.toDouble())}" } ?: ": needs a workshop quote")) }
    if (repairMaxInr > 0) add("Repairs total: ${inr(repairMinInr.toDouble())}–${inr(repairMaxInr.toDouble())}.")
    value?.let { v ->
        val source = if (v.basis == com.enginesense.app.vehicle.pricing.PriceBasis.AI_MARKET) "AI market check of current listings" else "GetAutoSure's own formula"
        add("Tentative price in good order: ${inr(v.fair.lowEstimate)}–${inr(v.fair.highEstimate)} ($source); after repairs ${inr(v.afterRepairsLow)}–${inr(v.afterRepairsHigh)}.")
        v.ai?.steps?.takeIf { v.basis == com.enginesense.app.vehicle.pricing.PriceBasis.AI_MARKET }?.let { add("How the price was worked out: " + it.joinToString(" ")) }
    }
}
