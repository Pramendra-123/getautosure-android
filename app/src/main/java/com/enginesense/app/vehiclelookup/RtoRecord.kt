package com.enginesense.app.vehiclelookup

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class ValidityStatus { VALID, EXPIRES_SOON, EXPIRED, LIFETIME, UNKNOWN }

/** One "valid until" date from the RC, with what it means today. [display] is the RTO's own wording when the date couldn't be read. */
data class ValidityLine(val label: String, val display: String, val status: ValidityStatus)

/** A label and a value, ready to print — the same rows on every report. */
data class RtoRow(val label: String, val value: String, val concern: Boolean = false)

/**
 * The RTO record turned into report rows, shared by the advanced report, the pre-purchase report
 * and the dealer web report so they can't disagree. Pure (no Android), so it's unit-tested.
 * Values are the RTO's own; nothing here is guessed — a field the record doesn't have is left out.
 */
object RtoRecord {

    /** Within this many days of expiry a validity is flagged as "expires soon". */
    const val EXPIRY_WARNING_DAYS = 30L

    private val FORMATS: List<DateTimeFormatter> = listOf(
        "yyyy-MM-dd", "d-MMM-yyyy", "d-M-yyyy", "d/M/yyyy", "d MMM yyyy", "d/MMM/yyyy"
    ).map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH) }

    private val DISPLAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** Surepass dates come in several shapes ("2027-03-14", "14-Mar-2027", "14/03/2027"); null when none fits. */
    fun parseDate(raw: String?): LocalDate? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // ISO timestamps ("2026-10-27T10:15:00Z") keep just the date.
        val datePart = if (Regex("""^\d{4}-\d{2}-\d{2}""").containsMatchIn(text)) text.take(10) else text
        for (format in FORMATS) {
            try {
                return LocalDate.parse(datePart, format)
            } catch (e: Exception) {
                // try the next shape
            }
        }
        return null
    }

    fun status(raw: String?, today: LocalDate): ValidityStatus {
        if (raw == null) return ValidityStatus.UNKNOWN
        // "LTT" / "lifetime" — road tax paid once for the car's life.
        if (Regex("""^\s*(LTT|LIFE\s*TIME|LIFETIME|OTT)\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw)) return ValidityStatus.LIFETIME
        val date = parseDate(raw) ?: return ValidityStatus.UNKNOWN
        return when {
            date.isBefore(today) -> ValidityStatus.EXPIRED
            ChronoUnit.DAYS.between(today, date) <= EXPIRY_WARNING_DAYS -> ValidityStatus.EXPIRES_SOON
            else -> ValidityStatus.VALID
        }
    }

    private fun displayDate(raw: String): String = parseDate(raw)?.format(DISPLAY) ?: raw

    /** Insurance, PUC, fitness and road tax — the four dates a buyer or owner has to keep current. Missing ones are listed as not on record, not dropped, so the gap is visible. */
    fun validity(rc: RcLookupResult, today: LocalDate): List<ValidityLine> = listOf(
        "Insurance" to rc.insuranceUpto,
        "PUC (pollution)" to rc.puccUpto,
        "Fitness" to rc.fitnessUpto,
        "Road tax" to rc.taxUpto
    ).map { (label, raw) ->
        val status = status(raw, today)
        val display = when {
            raw == null -> "Not on record"
            status == ValidityStatus.LIFETIME -> "Lifetime (paid once)"
            else -> displayDate(raw)
        }
        // The insurer belongs with its policy date rather than on a row of its own.
        val withInsurer = if (label == "Insurance" && raw != null && rc.insuranceCompany != null) "$display · ${rc.insuranceCompany}" else display
        ValidityLine(label, withInsurer, status)
    }

    /** What the car is, per its RC — available in both lookup modes. */
    fun vehicleRows(rc: RcLookupResult): List<RtoRow> = listOfNotNull(
        RtoRow("Registration", rc.registrationNumber),
        listOfNotNull(rc.make, rc.model).joinToString(" ").ifBlank { null }?.let { RtoRow("Make / model", it) },
        rc.bodyType?.let { RtoRow("Body type", it) },
        rc.fuelType?.let { RtoRow("Fuel", it) },
        rc.color?.let { RtoRow("Colour", it) },
        rc.cubicCapacity?.let { cc -> RtoRow("Engine", cc.toDoubleOrNull()?.let { "${it.toInt()} cc" } ?: cc) },
        rc.manufacturingMonth?.let { RtoRow("Manufactured", PurchaseRcCheckViewModel.formatMonth(it) ?: it) },
        rc.registrationDate?.let { RtoRow("Registered on", displayDate(it)) },
        rc.registeredAt?.let { RtoRow("Registering RTO", listOfNotNull(it, rc.rtoCode?.let { c -> "($c)" }).joinToString(" ")) },
        rc.emissionNorms?.let { RtoRow("Emission norms", it) },
        rc.seatCapacity?.let { RtoRow("Seats", it.toString()) },
        rc.cylinders?.let { RtoRow("Cylinders", it.toString()) },
        // An insurer with no policy date has nowhere else to go — otherwise it's shown with the date (see validity).
        rc.insuranceCompany?.takeIf { rc.insuranceUpto == null }?.let { RtoRow("Insurer", it) },
    )

    /** Ownership, loan and legal status — dealer mode only, empty otherwise. Owner's name is never included. */
    fun dealerRows(rc: RcLookupResult): List<RtoRow> {
        if (!rc.hasDealerDetails) return emptyList()
        return listOfNotNull(
            rc.ownerCount?.let { RtoRow("Owners so far", ordinalOwner(it), concern = it >= 3) },
            when (rc.financed) {
                true -> RtoRow("Loan (hypothecation)", "Yes" + (rc.financer?.let { " — $it" } ?: ""), concern = true)
                false -> RtoRow("Loan (hypothecation)", "None on record")
                null -> null
            },
            rc.rcStatus?.let { RtoRow("RC status", it, concern = !isActive(it)) },
            rc.blacklistStatus?.let { RtoRow("Blacklist", it, concern = isBlacklisted(it)) },
            rc.nocDetails?.let { RtoRow("NOC", it) },
            rc.nonUseStatus?.let { RtoRow("Non-use declared", it) },
            maskIdentifier(rc.chassisNumber)?.let { RtoRow("Chassis no. (last 5)", it) },
            maskIdentifier(rc.engineNumber)?.let { RtoRow("Engine no. (last 5)", it) },
            rc.recordUpdatedOn?.let { RtoRow("RTO record updated", displayDate(it)) },
        )
    }

    /** "ACTIVE" is the normal state; anything else (suspended, cancelled, surrendered) is worth a look. */
    fun isActive(rcStatus: String): Boolean = rcStatus.trim().equals("ACTIVE", ignoreCase = true)

    /** Surepass reports "NA"/"None"/"Not Blacklisted" for a clean record; anything else is a listing. */
    fun isBlacklisted(blacklistStatus: String?): Boolean {
        val s = blacklistStatus?.trim()?.lowercase() ?: return false
        if (s.isEmpty()) return false
        return !(s == "na" || s == "n/a" || s == "none" || s == "no" || s == "false" || s.startsWith("not "))
    }

    private fun ordinalOwner(n: Int): String = when {
        n <= 0 -> n.toString()
        n % 100 in 11..13 -> "${n}th owner"
        n % 10 == 1 -> "${n}st owner"
        n % 10 == 2 -> "${n}nd owner"
        n % 10 == 3 -> "${n}rd owner"
        else -> "${n}th owner"
    }

    /** Where the record came from, and when — printed under every RTO section. */
    fun sourceLine(rc: RcLookupResult): String {
        val fetched = rc.fetchedAt?.let { parseDate(it)?.format(DISPLAY) }
        return listOfNotNull(
            "Source: ${rc.source}",
            fetched?.let { "fetched $it" },
            if (rc.partial) "the RTO returned a reduced record for this car" else null
        ).joinToString(" · ") + "."
    }
}
