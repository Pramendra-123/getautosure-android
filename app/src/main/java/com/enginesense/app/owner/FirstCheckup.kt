package com.enginesense.app.owner

import com.enginesense.app.vehiclelookup.ValidityStatus
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How a check-up line reads: fine, worth watching, a problem, or a question for the owner. */
enum class CheckupKind { OK, WATCH, PROBLEM, QUESTION }

/** One line of the first check-up. [route] opens a screen or a Garage tile; [action] is its link text. */
data class CheckupLine(
    val title: String,
    val detail: String?,
    val kind: CheckupKind,
    val route: String? = null,
    val action: String? = null
)

/** What one scan and the saved RTO record can say on day 1. */
data class CheckupInputs(
    val attention: AttentionInputs,
    /** km driven since the fault codes were last cleared, from the scan; null if the car doesn't report it. */
    val kmSinceCleared: Double? = null,
    /** Engine warm-ups since the codes were last cleared. */
    val warmUpsSinceCleared: Double? = null,
    val ownerCount: Int? = null,
    val financed: Boolean? = null,
    /** True once the owner gave a last service or said they don't know. */
    val serviceAnswered: Boolean = false
)

/**
 * "Your car's first check-up": the Garage card after the very first scan. On day 1 there's no
 * trend and often nothing wrong, so instead of an empty warnings card it says what was checked
 * and found, the paper dates (even when nothing is due), and asks the one thing we can't know.
 * Problems first, then the good news, then the question. Pure, so it's unit-tested.
 */
object FirstCheckup {

    /** Codes cleared within this many km are worth saying, even with nothing to compare against. */
    const val RECENT_CLEAR_KM = 200.0
    private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun lines(i: CheckupInputs): List<CheckupLine> {
        val a = i.attention
        val out = mutableListOf<CheckupLine>()

        // Codes cleared just before we saw the car: common right before a sale.
        i.kmSinceCleared?.takeIf { it < RECENT_CLEAR_KM }?.let { km ->
            val since = listOfNotNull(
                "%,.0f km".format(km),
                i.warmUpsSinceCleared?.takeIf { it >= 0 }?.let { w -> "${w.toInt()} warm-up${if (w.toInt() == 1) "" else "s"}" }
            ).joinToString(" and ")
            out += CheckupLine(
                "Warning codes were cleared recently",
                "Only $since since. Often done just before a sale or a service. If a problem was hidden, it usually comes back within a week, so scan again after a few drives.",
                CheckupKind.WATCH
            )
        }

        // Engine.
        when {
            a.milOn || a.storedCodes > 0 -> out += CheckupLine(
                if (a.milOn) "Engine warning light is on" else "${a.storedCodes} fault${if (a.storedCodes == 1) "" else "s"} found in the engine",
                "See if it's safe to drive and what it typically costs.",
                CheckupKind.PROBLEM, "issues", "See what to do"
            )
            a.pendingCodes > 0 -> {}
            else -> out += CheckupLine("Engine: no faults", "No stored or pending fault codes. Warning light off.", CheckupKind.OK)
        }
        if (a.pendingCodes > 0) {
            out += CheckupLine(
                "${a.pendingCodes} fault${if (a.pendingCodes == 1) "" else "s"} caught early",
                "Not on the dashboard yet, so fixing it now is usually cheaper.",
                CheckupKind.WATCH, "issues", "See what to do"
            )
        }

        // Battery.
        a.battery?.let { b ->
            out += when (b.level) {
                Level.ATTENTION -> CheckupLine("Battery needs attention", b.lines.firstOrNull(), CheckupKind.PROBLEM, "battery", "What to check")
                Level.WATCH -> CheckupLine("Battery is getting weaker", b.lines.firstOrNull() ?: "Plan a check before it lets you down.", CheckupKind.WATCH, "battery", "What to check")
                else -> CheckupLine("Battery: good", b.lines.firstOrNull(), CheckupKind.OK, "battery")
            }
        }

        // Papers, from the RTO record or the owner's own dates.
        val papers = a.renewals.filter { it.kind == RenewalKind.INSURANCE || it.kind == RenewalKind.PUC || it.kind == RenewalKind.FITNESS }
        papers.filter { it.status == ValidityStatus.EXPIRED }.forEach { r ->
            out += CheckupLine(
                "${r.kind.label} has expired",
                listOfNotNull(r.date?.let { "Expired ${it.format(DATE)}." }, "Renew to avoid a fine.").joinToString(" "),
                CheckupKind.PROBLEM, "renewals"
            )
        }
        papers.filter { it.status != ValidityStatus.EXPIRED && it.date != null && (it.daysLeft ?: Long.MAX_VALUE) in 0..CarAttention.RENEWAL_SOON_DAYS }.forEach { r ->
            out += CheckupLine("${r.kind.label} expires ${r.date!!.format(SHORT)}", "Renew before then to avoid a fine.", CheckupKind.WATCH, "renewals")
        }
        val valid = papers.filter { it.kind != RenewalKind.FITNESS && it.status != ValidityStatus.EXPIRED && it.date != null && (it.daysLeft ?: 0) > CarAttention.RENEWAL_SOON_DAYS }
        val loan = when (i.financed) {
            true -> "A loan is still shown on the RC."
            false -> "No loan on the RC."
            null -> null
        }
        if (valid.isNotEmpty()) {
            out += CheckupLine(
                "Papers in order",
                (valid.joinToString(" · ") { "${it.kind.label} till ${it.date!!.format(DATE)}" } + ". We'll remind you 30 days before. " + (loan ?: "")).trim(),
                CheckupKind.OK, "renewals"
            )
        } else if (papers.none { it.date != null }) {
            out += CheckupLine("Add your insurance and PUC dates", "So we can remind you before they expire.", CheckupKind.QUESTION, "renewals", "Add dates")
        }

        // The one thing we can't know ourselves.
        if (!i.serviceAnswered) {
            out += CheckupLine(
                "When was the last service?",
                if ((i.ownerCount ?: 1) >= 2) "Check the service book from the seller." else "So we can tell you when the next one is due.",
                CheckupKind.QUESTION, "service"
            )
        } else {
            a.nextService?.takeIf { it.overdue }?.let { s ->
                out += CheckupLine("Service overdue", "It was due ${s.dueDate.format(SHORT)}. Book one soon.", CheckupKind.WATCH, "service", "Book a service")
            }
        }

        return out.sortedBy { order(it.kind) }
    }

    /** "Today · 38 checks on the engine, battery and emissions · 2nd owner per RTO". */
    fun subtitle(checks: Int, ownerCount: Int?): String =
        listOfNotNull(
            "Today · $checks check${if (checks == 1) "" else "s"} on the engine, battery and emissions",
            ownerCount?.takeIf { it >= 2 }?.let { "${ordinal(it)} owner per RTO" }
        ).joinToString(" · ")

    /** True when the check-up found something to act on: the card gets the orange border. */
    fun hasProblems(lines: List<CheckupLine>): Boolean = lines.any { it.kind == CheckupKind.PROBLEM || it.kind == CheckupKind.WATCH }

    const val FOOTER = "Starting point saved. From your next scan, we'll show what changed, and catch problems before a warning light comes on."

    private fun order(k: CheckupKind) = when (k) {
        CheckupKind.PROBLEM -> 0
        CheckupKind.WATCH -> 1
        CheckupKind.OK -> 2
        CheckupKind.QUESTION -> 3
    }

    private fun ordinal(n: Int) = "$n" + when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
}
