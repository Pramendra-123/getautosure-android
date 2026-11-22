package com.enginesense.app.owner

import com.enginesense.app.vehiclelookup.ValidityStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One line in "Needs your attention". [now] items need action today; the rest are coming up. */
data class AttentionItem(val title: String, val detail: String?, val level: Level, val route: String?, val now: Boolean)

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
data class LearnStep(val title: String, val hint: String, val done: Boolean)

data class Learning(val steps: List<LearnStep>) {
    val doneCount: Int get() = steps.count { it.done }
    val complete: Boolean get() = steps.all { it.done }
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

    fun items(i: AttentionInputs): List<AttentionItem> {
        val out = mutableListOf<AttentionItem>()
        if (i.milOn || i.storedCodes > 0) {
            out += AttentionItem(
                if (i.storedCodes > 0) "${i.storedCodes} fault code${if (i.storedCodes == 1) "" else "s"} stored" else "Engine warning light is on",
                if (i.milOn) "Engine warning light is on" else "See what it means and what it costs",
                Level.ATTENTION, "issues", now = true
            )
        }
        if (i.pendingCodes > 0) {
            out += AttentionItem("${i.pendingCodes} pending fault code${if (i.pendingCodes == 1) "" else "s"}", "Not on the dashboard yet — caught early", Level.WATCH, "issues", now = true)
        }
        i.battery?.takeIf { it.level == Level.ATTENTION }?.let { out += AttentionItem("Battery: ${it.headline.lowercase()}", it.lines.firstOrNull(), Level.ATTENTION, "battery", now = true) }
        i.vitals.filter { it.warning != null && it.level != Level.GOOD }.forEach { t ->
            out += AttentionItem(
                if (t.level == Level.ATTENTION) "${t.vital.label} outside normal" else "${t.vital.label} slowly ${if (t.points.last() < t.points.first()) "dropping" else "rising"}",
                t.summary, t.level, null, now = true
            )
        }
        i.mileageDrop?.let { d ->
            out += AttentionItem(
                "Mileage down ${d.percent}% for 2 weeks",
                (d.reasons.take(2) + listOfNotNull(d.monthlyCostInr?.let { "≈${OwnerInsights.inr(it)} a month" })).joinToString(" · ").ifBlank { null },
                Level.WATCH, "fuel", now = true
            )
        }
        i.parkedDrop?.let { p ->
            out += AttentionItem("Fuel dropped %.0f%% while parked".format(p.droppedPercent), "Worth a look for a leak", Level.WATCH, "fuel", now = true)
        }
        i.renewals.filter { it.status == ValidityStatus.EXPIRED }.forEach {
            out += AttentionItem("${it.kind.label} has expired", it.display, Level.ATTENTION, "renewals", now = true)
        }
        i.nextService?.let { s ->
            when {
                s.overdue -> out += AttentionItem("Service overdue", "Due ${s.dueDate.format(SHORT)}", Level.WATCH, "service", now = true)
                s.daysLeft <= SERVICE_SOON_DAYS || (s.kmLeft != null && s.kmLeft <= SERVICE_SOON_KM) -> out += AttentionItem(
                    if (s.kmLeft != null && s.kmLeft <= SERVICE_SOON_KM) "Service due in ~${"%,d".format(s.kmLeft)} km" else "Service due by ${s.dueDate.format(SHORT)}",
                    null, Level.GOOD, "service", now = false
                )
            }
        }
        i.battery?.takeIf { it.level == Level.WATCH }?.let { out += AttentionItem("Battery is getting weaker", "Plan a check before it lets you down", Level.WATCH, "battery", now = false) }
        i.renewals.filter { it.status != ValidityStatus.EXPIRED && (it.daysLeft ?: Long.MAX_VALUE) in 0..RENEWAL_SOON_DAYS }.sortedBy { it.daysLeft }.forEach {
            out += AttentionItem("${it.kind.label} renews ${it.date!!.format(SHORT)}", null, Level.GOOD, "renewals", now = false)
        }
        return out.sortedWith(compareByDescending<AttentionItem> { it.now }.thenBy { rank(it.level) })
    }

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
                LearnStep("First health check", "Run your first scan", scans >= 1),
                LearnStep("Baseline saved", "Every next scan is compared with the first", scans >= 1),
                LearnStep("Real mileage", "After 3 drives with the scanner plugged in", tripsWithFuel >= 3),
                LearnStep("What changed since last scan", "At your next scan", scans >= 2),
                LearnStep("Early warnings and trends", "After 3 scans or 2 weeks of drives", scans >= 3 || driveDays >= 14),
                LearnStep("Fuel quality per tank", "After 3 fill-ups", tanksCompared >= 1)
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
