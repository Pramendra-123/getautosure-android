package com.enginesense.app.owner

import com.enginesense.app.vehiclelookup.ValidityStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One line in "Needs your attention". [now] items need action today; the rest are coming up. */
data class AttentionItem(
    val title: String,
    val detail: String?,
    val level: Level,
    val route: String?,
    val now: Boolean,
    /** The one thing to do, shown as a link under the line ("See what to do"). */
    val action: String? = null
)

/** Everything the card reads, gathered by the screen from the scan, trips, fuel and owner data. */
data class AttentionInputs(
    val scanned: Boolean,
    val storedCodes: Int = 0,
    val pendingCodes: Int = 0,
    val milOn: Boolean = false,
    val battery: BatteryHealth? = null,
    val vitals: List<VitalTrend> = emptyList(),
    val mileageDrop: MileageDrop? = null,
    val parkedDrop: ParkedDrop? = null,
    val nextService: NextService? = null,
    val renewals: List<RenewalItem> = emptyList(),
    /** Readings and checks behind the latest scan, for the "all good" line. */
    val checks: Int = 0
)

/** One step of "Getting to know your car". */
data class LearnStep(
    val title: String,
    /** What the owner gets and what they need to do, in plain words, while not done. */
    val hint: String,
    val done: Boolean,
    /** Shown once the step is done. */
    val doneNote: String = "Done.",
    /** The one-line nudge on the Garage while this is the next step. */
    val nudge: String = hint
)

data class Learning(val steps: List<LearnStep>) {
    val doneCount: Int get() = steps.count { it.done }
    val complete: Boolean get() = steps.all { it.done }
    /** The Garage's one line: only the next step. */
    val nextNudge: String? get() = steps.firstOrNull { !it.done }?.nudge
}

/**
 * "Needs your attention": the app's own conclusion, merging the old issue forecast with the
 * early warnings — now, then coming up, worst first. Plus "Getting to know your car", which
 * tells a new owner what each extra scan and drive unlocks. Pure, so it's unit-tested.
 */
object CarAttention {

    const val SERVICE_SOON_DAYS = 30L
    const val SERVICE_SOON_KM = 1_500
    const val RENEWAL_SOON_DAYS = 30L
    /** No scan or drive for this long: the warnings are going stale. */
    const val STALE_DAYS = 21L
    /** After this, the learning strip goes even if a step can't finish on this car. */
    const val LEARNING_MAX_DAYS = 60L
    private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /**
     * Every line leads with what it means for the owner (money or risk), then why in plain words,
     * then one [AttentionItem.action]. Worst first; "now" before "coming up".
     */
    fun items(i: AttentionInputs): List<AttentionItem> {
        val out = mutableListOf<AttentionItem>()
        if (i.milOn || i.storedCodes > 0) {
            val codes = "${i.storedCodes} fault${if (i.storedCodes == 1) "" else "s"}"
            out += AttentionItem(
                if (i.milOn) "Engine warning light is on" else "$codes found in the engine",
                if (i.milOn && i.storedCodes > 0) "$codes stored. See if it's safe to drive and what it typically costs."
                else "See if it's safe to drive and what it typically costs.",
                Level.ATTENTION, "issues", now = true, action = "See what to do"
            )
        }
        if (i.pendingCodes > 0) {
            out += AttentionItem(
                "${i.pendingCodes} fault${if (i.pendingCodes == 1) "" else "s"} caught early",
                "Not on the dashboard yet, so fixing it now is usually cheaper.",
                Level.WATCH, "issues", now = true, action = "See what to do"
            )
        }
        i.battery?.takeIf { it.level == Level.ATTENTION }?.let {
            out += AttentionItem("Battery needs attention", it.lines.firstOrNull(), Level.ATTENTION, "battery", now = true, action = "What to check")
        }
        i.vitals.filter { it.warning != null && it.level != Level.GOOD }.forEach { t ->
            val outside = t.level == Level.ATTENTION
            out += AttentionItem(
                if (outside) "${t.vital.label} outside normal" else "${t.vital.label} slowly ${if (t.points.last() < t.points.first()) "dropping" else "rising"}",
                listOfNotNull(t.summary, if (outside) "Get it checked soon." else "Not urgent; get it checked at your next service.").joinToString(" "),
                t.level, null, now = true, action = "What it means"
            )
        }
        i.mileageDrop?.let { d ->
            val why = d.reasons.take(2).joinToString(", ") { it.replaceFirstChar { c -> c.lowercase() } }
            out += AttentionItem(
                d.monthlyCostInr?.let { "Costing you ≈${OwnerInsights.inr(it)} more a month" } ?: "Mileage down ${d.percent}%",
                listOfNotNull(
                    if (d.monthlyCostInr != null) "Mileage down ${d.percent}% for 2 weeks." else "For 2 weeks, against your usual.",
                    why.takeIf { it.isNotBlank() }?.let { "Likely: $it." }
                ).joinToString(" "),
                Level.WATCH, "fuel", now = true, action = "See why and what to do"
            )
        }
        i.parkedDrop?.let { p ->
            out += AttentionItem(
                "Fuel dropped %.0f%% while parked".format(p.droppedPercent),
                "Could be a leak. Check under the car for a smell or drips.",
                Level.WATCH, "fuel", now = true, action = "See details"
            )
        }
        i.renewals.filter { it.status == ValidityStatus.EXPIRED }.forEach {
            out += AttentionItem(
                "${it.kind.label} has expired",
                listOfNotNull(it.date?.let { d -> "Expired ${d.format(SHORT)}." }, "Renew to avoid a fine.").joinToString(" "),
                Level.ATTENTION, "renewals", now = true
            )
        }
        i.nextService?.let { s ->
            when {
                s.overdue -> out += AttentionItem("Service overdue", "It was due ${s.dueDate.format(SHORT)}. Book one soon.", Level.WATCH, "service", now = true, action = "Book a service")
                s.daysLeft <= SERVICE_SOON_DAYS || (s.kmLeft != null && s.kmLeft <= SERVICE_SOON_KM) -> out += AttentionItem(
                    if (s.kmLeft != null && s.kmLeft <= SERVICE_SOON_KM) "Service due in about ${"%,d".format(s.kmLeft)} km" else "Service due by ${s.dueDate.format(SHORT)}",
                    null, Level.GOOD, "service", now = false
                )
            }
        }
        i.battery?.takeIf { it.level == Level.WATCH }?.let { out += AttentionItem("Battery is getting weaker", "Plan a check before it lets you down.", Level.WATCH, "battery", now = false) }
        i.renewals.filter { it.status != ValidityStatus.EXPIRED && (it.daysLeft ?: Long.MAX_VALUE) in 0..RENEWAL_SOON_DAYS }.sortedBy { it.daysLeft }.forEach {
            out += AttentionItem("${it.kind.label} expires ${it.date!!.format(SHORT)}", "Renew before then to avoid a fine.", Level.GOOD, "renewals", now = false)
        }
        return out.sortedWith(compareByDescending<AttentionItem> { it.now }.thenBy { rank(it.level) })
    }

    /** Something the owner should act on: the Garage shows "Needs your attention" for these. */
    fun needsAction(items: List<AttentionItem>): Boolean = items.any { it.now && it.level != Level.GOOD }

    private fun rank(l: Level) = when (l) {
        Level.ATTENTION -> 0
        Level.WATCH -> 1
        Level.GOOD -> 2
        Level.UNKNOWN -> 3
    }

    /** "All good · 23 checks on the last scan" when nothing needs action now. */
    fun allGoodLine(i: AttentionInputs): String? =
        if (!i.scanned) null else "All good · ${i.checks} check${if (i.checks == 1) "" else "s"} on the last scan"

    fun learning(
        scans: Int,
        tripsWithFuel: Int,
        driveDays: Long,
        tanksCompared: Int,
        firstSeen: LocalDate?,
        today: LocalDate
    ): Learning? {
        if (firstSeen != null && firstSeen.plusDays(LEARNING_MAX_DAYS).isBefore(today)) return null
        val l = Learning(
            listOf(
                LearnStep(
                    "Health check", "Plug in the scanner with the engine on and tap Scan.", scans >= 1,
                    doneNote = "Done. Your first scan is saved.", nudge = "Plug in the scanner with the engine on and tap Scan."
                ),
                LearnStep(
                    "Starting point saved", "Saved with your first scan.", scans >= 1,
                    doneNote = "Done. Every next scan is compared with it."
                ),
                LearnStep(
                    "Your real mileage",
                    "Drive 3 times with the scanner plugged in. ${tripsWithFuel.coerceAtMost(3)} of 3 so far.",
                    tripsWithFuel >= 3,
                    doneNote = "Done. See it under Fuel & mileage.",
                    nudge = "Leave the scanner plugged in. After ${(3 - tripsWithFuel).coerceAtLeast(1)} more drive${if (3 - tripsWithFuel == 1) "" else "s"} you'll see your real mileage."
                ),
                LearnStep(
                    "What changed since last time", "Scan again any day.", scans >= 2,
                    doneNote = "Done. Shown after every scan.", nudge = "Scan again any day to see what changed since the first check-up."
                ),
                LearnStep(
                    "Early warnings", "After 3 scans or 2 weeks of driving, we spot problems before a warning light comes on.",
                    scans >= 3 || driveDays >= 14,
                    doneNote = "Done. We now watch for slow changes.",
                    nudge = "Keep driving with the scanner plugged in. Soon we'll spot problems before a warning light."
                ),
                LearnStep(
                    "Fuel quality check", "After 3 fill-ups, we can tell if a pump's fuel was poor.", tanksCompared >= 1,
                    doneNote = "Done. See each tank under Fuel & mileage.", nudge = "After 3 fill-ups, we'll tell you if a pump's fuel was poor."
                )
            )
        )
        return l.takeUnless { it.complete }
    }

    /** "We haven't seen your car in 3 weeks" once the newest scan or drive is that old. */
    fun staleLine(lastSeen: LocalDate?, today: LocalDate): String? {
        val seen = lastSeen ?: return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(seen, today)
        if (days < STALE_DAYS) return null
        return "We haven't seen your car in ${days / 7} weeks. Plug in to keep the warnings up to date."
    }
}
