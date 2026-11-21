package com.enginesense.app.dealerreport

import com.enginesense.app.ScanResult
import com.enginesense.app.vehicle.CategoryState
import com.enginesense.app.vehicle.FaultKind
import com.enginesense.app.vehicle.OdometerMatch
import com.enginesense.app.vehicle.PrePurchaseReport
import com.enginesense.app.vehiclelookup.RtoRecord
import com.enginesense.app.vehiclelookup.ValidityStatus
import java.time.LocalDate

/*
 * Dealer Sathi v2 (docs/dealer-sathi-v2.md): the report a dealer makes after refurbishing a car,
 * to send to buyers and post on WhatsApp Status. It speaks to the dealer's customer — no buyer
 * advice, verdict or fair value. The PDF and the Status image both read [DealerSaleSummary], so
 * the badges can never disagree between them.
 */

/** What the dealer rates in step 3, in this order. Any can be left unrated. */
val INSPECTION_ITEMS = listOf("Exterior and paint", "Interior and seats", "Tyres", "AC", "Brakes and suspension", "Test drive")

/** One-tap ticks under the ratings. */
val INSPECTION_EXTRAS = listOf(
    "Accident-free", "Service records", "Spare key", "Single owner",
    "Low km driven", "Recently serviced", "Well-kept family car", "New tyres", "Original paint", "No insurance claims"
)

/** The dealer's own inspection — 1–5 stars per item (missing = not rated), ticked extras and an optional note. Never checked by GetAutoSure. */
data class DealerInspection(
    val ratings: Map<String, Int> = emptyMap(),
    val extras: List<String> = emptyList(),
    val note: String = ""
) {
    val isEmpty: Boolean get() = ratings.isEmpty() && extras.isEmpty() && note.isBlank()

    /** Mean of the rated items, rounded to the nearest whole star; null when nothing is rated. */
    val averageStars: Int? get() = ratings.values.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt().coerceIn(1, 5) }
}

/** 325000 → "3,25,000": last three digits, then pairs — how prices are written in India. */
fun indianGrouping(value: Long): String {
    val digits = kotlin.math.abs(value).toString()
    if (digits.length <= 3) return (if (value < 0) "-" else "") + digits
    val pairs = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return (if (value < 0) "-" else "") + pairs + "," + digits.takeLast(3)
}

/** How a car's price reads on the Status poster: "₹12.38 Lakh", "₹1.05 Crore", or "₹85,000" under a lakh. */
fun priceLabel(inr: Double): String {
    val rupees = Math.round(inr)
    return when {
        rupees >= 1_00_00_000 -> "₹%.2f Crore".format(java.util.Locale.ROOT, rupees / 1_00_00_000.0)
        rupees >= 1_00_000 -> "₹%.2f Lakh".format(java.util.Locale.ROOT, rupees / 1_00_000.0)
        else -> "₹" + indianGrouping(rupees)
    }
}

/**
 * A Dealer Saathi pack: [cars] report credits for [priceInr], GST included. The amounts are
 * introductory prices; nothing is charged until payments go live.
 */
data class DealerPack(val cars: Int, val priceInr: Int, val popular: Boolean = false) {
    val perCarInr: Int get() = Math.round(priceInr.toDouble() / cars).toInt()

    /** The price split into its pre-GST amount and 18% GST; the two always add up to [priceInr]. */
    val beforeGstInr: Int get() = Math.round(priceInr / 1.18).toInt()
    val gstInr: Int get() = priceInr - beforeGstInr
}

val DEALER_PACKS = listOf(DealerPack(1, 29), DealerPack(5, 99, popular = true), DealerPack(30, 499))

/** A Status/summary badge. [good] picks green ✓ vs amber ! — a badge is shown either way, never hidden for being bad. */
data class SaleBadge(val title: String, val detail: String, val good: Boolean)

/** Everything the cover and the Status image say about the car, worked out once from the scan, the RTO record and the dealer's input. */
data class DealerSaleSummary(
    val carName: String,
    /** "VXI" — the rest of the RTO model text, for the Status title. */
    val variant: String? = null,
    /** "Automatic" / "Manual" when the variant text says (7DCT, AMT, MT…); null otherwise. */
    val transmission: String? = null,
    val year: Int?,
    val fuel: String?,
    val colour: String?,
    val km: Double?,
    val ownerLabel: String?,
    /** Full number, for the PDF. */
    val registration: String?,
    /** "KA02" — the only part of the number the public Status image shows. */
    val rtoCode: String?,
    val healthScore: Int?,
    val healthLabel: String,
    val faultCount: Int,
    val redFlagCount: Int,
    val selfTestsReady: Int?,
    val selfTestsTotal: Int?,
    /** Null without an RTO record; true when insurance, PUC, fitness and tax are valid and there's no loan. */
    val papersOk: Boolean?,
    val odometerMatches: Boolean?,
    val badges: List<SaleBadge>
)

object DealerSaleSummaryBuilder {

    fun build(result: ScanResult, report: PrePurchaseReport, today: LocalDate): DealerSaleSummary {
        val rc = report.rc
        // Make and model from the RTO (VAHAN) record only, never guessed from the VIN.
        val carName = CarNameFormatter.carName(rc?.make, rc?.model, rc?.fuelType) ?: "Your next car"
        val faultCount = report.faults.count { it.kind != FaultKind.PENDING && it.kind != FaultKind.EXTERIOR }
        val readiness = result.readiness
        val supported = readiness?.monitors?.filter { it.supported }
        val selfTestsReady = supported?.count { it.ready }
        val papersOk = rc?.let { record ->
            val datesOk = RtoRecord.validity(record, today).filter { it.status != ValidityStatus.UNKNOWN }
                .all { it.status == ValidityStatus.VALID || it.status == ValidityStatus.LIFETIME }
            datesOk && record.financed != true
        }
        val odometerMatches = report.odometer.match?.let { it == OdometerMatch.MATCHES }

        val badges = buildList {
            add(SaleBadge(result.health.score?.let { "Health $it/100" } ?: "Health checked", "Car computer scan", healthGood(result)))
            when (odometerMatches) {
                true -> add(SaleBadge("Odometer OK", "No red flags from OBD", true))
                false -> add(SaleBadge("Odometer: check", "Dashboard and computer differ", false))
                null -> if (selfTestsReady != null && !supported.isNullOrEmpty())
                    add(SaleBadge("Self-tests $selfTestsReady/${supported.size}", if (selfTestsReady == supported.size) "All complete" else "Some not complete yet", selfTestsReady == supported.size))
            }
            add(if (faultCount == 0) SaleBadge("No faults", "0 fault codes found", true) else SaleBadge("$faultCount fault${if (faultCount == 1) "" else "s"} found", "Listed in the full report", false))
            when (papersOk) {
                true -> add(SaleBadge("Papers OK", "Insurance, PUC, no loan", true))
                false -> add(SaleBadge("Papers: check", "See the RTO record", false))
                null -> Unit
            }
            if (size < 4 && report.redFlags.isEmpty()) add(SaleBadge("No red flags", "0 found by the OBD scan", true))
        }.take(4)

        return DealerSaleSummary(
            carName = carName,
            variant = CarNameFormatter.variant(rc?.make, rc?.model, rc?.fuelType),
            transmission = CarNameFormatter.transmission(rc?.model),
            year = report.manufactureYear,
            fuel = CarNameFormatter.fuel(rc?.fuelType),
            colour = rc?.color?.let(::titleCase),
            km = report.odometer.dashboardKm ?: report.odometer.ecuKm,
            ownerLabel = rc?.ownerCount?.let { ordinalOwner(it) },
            registration = report.registration ?: rc?.registrationNumber,
            rtoCode = rtoCode(report.registration ?: rc?.registrationNumber),
            healthScore = result.health.score,
            healthLabel = healthLabel(result),
            faultCount = faultCount,
            redFlagCount = report.redFlags.size,
            selfTestsReady = selfTestsReady,
            selfTestsTotal = supported?.size,
            papersOk = papersOk,
            odometerMatches = odometerMatches,
            badges = badges
        )
    }

    /**
     * The RTO part of a registration number — state letters plus district digits: "KA02MH3313" →
     * "KA02", "dl 3c ab 1234" → "DL3". Bharat series ("22BH1234AA") has no RTO, so it's "BH".
     * Null when the number doesn't look like either.
     */
    fun rtoCode(registration: String?): String? {
        val plate = registration?.uppercase()?.filter { it.isLetterOrDigit() } ?: return null
        Regex("^[A-Z]{2}[0-9]{1,2}").find(plate)?.let { return it.value }
        if (Regex("^[0-9]{2}BH").containsMatchIn(plate)) return "BH"
        return null
    }

    fun ordinalOwner(count: Int): String = when (count) {
        1 -> "1st owner"
        2 -> "2nd owner"
        3 -> "3rd owner"
        else -> "${count}th owner"
    }

    private fun worstState(result: ScanResult): CategoryState? {
        val states = result.health.categories.map { it.state }
        return when {
            CategoryState.ATTENTION in states -> CategoryState.ATTENTION
            CategoryState.WATCH in states -> CategoryState.WATCH
            states.all { it == CategoryState.UNAVAILABLE } -> null
            else -> CategoryState.GOOD
        }
    }

    private fun healthGood(result: ScanResult) = worstState(result) == CategoryState.GOOD

    private fun healthLabel(result: ScanResult) = when (worstState(result)) {
        CategoryState.ATTENTION -> "Needs attention"
        CategoryState.WATCH -> "Fair health"
        CategoryState.GOOD -> "Good health"
        else -> "Health checked"
    }

    /** RTO strings come in capitals ("DIESEL", "SILVER GREY"). */
    private fun titleCase(s: String) = s.trim().lowercase().split(Regex("\\s+")).joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}

/** Where a Status badge comes from — the picker groups them the same way. */
enum class BadgeGroup { SCAN, RTO, DEALER }

/** One badge the dealer can put on the Status picture. [available] is false when it isn't true for this car (a failed check, a tick not given). */
data class StatusBadgeOption(val id: String, val group: BadgeGroup, val badge: SaleBadge, val available: Boolean)

/**
 * The badges a dealer picks for the Status picture: the health score always first, then any 3 of
 * these. Only what's true for this car can be picked. Dealer ticks show without a "declared by"
 * note — it's the dealer's own Status; the PDF and web report carry that note.
 */
object StatusBadges {
    const val HEALTH = "health"
    const val MAX = 4

    private val DEALER_DETAILS = mapOf(
        "Accident-free" to "No accident history",
        "Service records" to "Records available",
        "Spare key" to "Both keys",
        "Single owner" to "One owner",
        "Low km driven" to "Below-average km",
        "Recently serviced" to "Fresh service",
        "Well-kept family car" to "Cared for",
        "New tyres" to "Recently replaced",
        "Original paint" to "No repaints",
        "No insurance claims" to "Clean claim history"
    )

    fun options(summary: DealerSaleSummary, report: PrePurchaseReport, inspection: DealerInspection): List<StatusBadgeOption> {
        val rc = report.rc
        val health = summary.badges.firstOrNull() ?: SaleBadge("Health checked", "Car computer scan", true)
        return buildList {
            add(StatusBadgeOption(HEALTH, BadgeGroup.SCAN, health, true))
            add(StatusBadgeOption("no_faults", BadgeGroup.SCAN, SaleBadge("No faults", "0 fault codes found", true), summary.faultCount == 0))
            val ready = summary.selfTestsReady
            val total = summary.selfTestsTotal
            if (ready != null && total != null && total > 0) {
                add(StatusBadgeOption("self_tests", BadgeGroup.SCAN, SaleBadge("Self-tests $ready/$total", "All complete", true), ready == total))
            }
            add(StatusBadgeOption("no_red_flags", BadgeGroup.SCAN, SaleBadge("No red flags", "0 found by the OBD scan", true), summary.redFlagCount == 0))
            add(StatusBadgeOption("odometer", BadgeGroup.SCAN, SaleBadge("Odometer OK", "Dashboard matches computer", true), summary.odometerMatches == true))
            add(StatusBadgeOption("papers", BadgeGroup.RTO, SaleBadge("Papers OK", "Insurance, PUC, no loan", true), summary.papersOk == true))
            add(StatusBadgeOption("first_owner", BadgeGroup.RTO, SaleBadge("1st owner", "From the RTO record", true), rc?.ownerCount == 1))
            add(StatusBadgeOption("no_loan", BadgeGroup.RTO, SaleBadge("No loan", "From the RTO record", true), rc?.financed == false))
            INSPECTION_EXTRAS.forEach { extra ->
                add(StatusBadgeOption("tick:$extra", BadgeGroup.DEALER, SaleBadge(extra, DEALER_DETAILS[extra] ?: "", true), extra in inspection.extras))
            }
        }
    }

    /**
     * Health first, then a mix buyers notice: up to 2 of the dealer's ticks, then one RTO item, then
     * scan items; anything left over fills the gaps. (Scan-only defaults made every Status look alike.)
     */
    fun defaults(options: List<StatusBadgeOption>): List<String> {
        val open = options.filter { it.available && it.id != HEALTH }
        fun group(g: BadgeGroup) = open.filter { it.group == g }
        val preferred = group(BadgeGroup.DEALER).take(2) + group(BadgeGroup.RTO).take(1) + group(BadgeGroup.SCAN)
        return listOf(HEALTH) + (preferred + open).distinctBy { it.id }.take(MAX - 1).map { it.id }
    }

    /** The badges to draw, in the dealer's order, ignoring anything no longer available. */
    fun chosen(options: List<StatusBadgeOption>, ids: List<String>): List<SaleBadge> {
        val byId = options.associateBy { it.id }
        val picked = (listOf(HEALTH) + ids.filter { it != HEALTH }).mapNotNull { byId[it]?.takeIf { o -> o.available } }.distinctBy { it.id }
        return picked.take(MAX).map { it.badge }
    }
}
