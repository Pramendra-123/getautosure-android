package com.enginesense.app.vehicle

/** What can be wrong with one part of the car, and which [ExteriorDamage] item prices it. */
enum class PanelIssue(val label: String, val itemId: String) {
    SCRATCH("Scratch", "scratch"),
    DENT("Dent", "dent"),
    REPAINTED("Repainted", "repaint"),
    RUST("Rust", "rust"),
    CRACKED_BUMPER("Cracked", "bumper"),
    CRACKED_GLASS("Cracked", "glass"),
    DAMAGED_MIRROR("Damaged", "mirror")
}

enum class PanelKind(val issues: List<PanelIssue>) {
    BODY(listOf(PanelIssue.SCRATCH, PanelIssue.DENT, PanelIssue.REPAINTED, PanelIssue.RUST)),
    BUMPER(listOf(PanelIssue.SCRATCH, PanelIssue.DENT, PanelIssue.CRACKED_BUMPER, PanelIssue.REPAINTED)),
    GLASS(listOf(PanelIssue.CRACKED_GLASS)),
    MIRROR(listOf(PanelIssue.DAMAGED_MIRROR))
}

/** One tappable part on the top-down car picture. Left and right are the car's own, front at the top. */
data class CarPanel(val id: String, val label: String, val kind: PanelKind)

/** Good / OK / Needs work for the things easier to judge than to count. */
enum class QuickAnswer(val label: String, val stars: Int) { GOOD("Good", 5), OK("OK", 3), NEEDS_WORK("Needs work", 1) }

/** A quick check and what "Needs work" costs, as an [ExteriorDamage] item and how many of it. */
data class QuickCheck(val id: String, val label: String, val hint: String?, val itemId: String, val count: Int)

/**
 * The look-around step: parts of the body marked on a picture of the car, four quick checks, and
 * three signs of accident repair. Everything turns into the same [ExteriorDamage] counts the report
 * already prices, so costs stay the fixed, explainable table they always were.
 */
object LookAround {

    val panels = listOf(
        CarPanel("front_bumper", "Front bumper", PanelKind.BUMPER),
        CarPanel("bonnet", "Bonnet", PanelKind.BODY),
        CarPanel("front_left_fender", "Front-left fender", PanelKind.BODY),
        CarPanel("front_right_fender", "Front-right fender", PanelKind.BODY),
        CarPanel("windshield", "Windshield", PanelKind.GLASS),
        CarPanel("left_mirror", "Left mirror", PanelKind.MIRROR),
        CarPanel("right_mirror", "Right mirror", PanelKind.MIRROR),
        CarPanel("roof", "Roof", PanelKind.BODY),
        CarPanel("front_left_door", "Front-left door", PanelKind.BODY),
        CarPanel("front_right_door", "Front-right door", PanelKind.BODY),
        CarPanel("rear_left_door", "Rear-left door", PanelKind.BODY),
        CarPanel("rear_right_door", "Rear-right door", PanelKind.BODY),
        CarPanel("rear_glass", "Rear glass", PanelKind.GLASS),
        CarPanel("rear_left_quarter", "Rear-left quarter panel", PanelKind.BODY),
        CarPanel("rear_right_quarter", "Rear-right quarter panel", PanelKind.BODY),
        CarPanel("boot", "Boot", PanelKind.BODY),
        CarPanel("rear_bumper", "Rear bumper", PanelKind.BUMPER)
    )

    val quickChecks = listOf(
        QuickCheck("tyres", "Tyres", "Look for worn tread and cracks", "tyre", 2),
        QuickCheck("glass_lights", "Glass and lights", null, "light", 1),
        QuickCheck("ac", "AC", "Run it on full for a minute", "ac", 1),
        QuickCheck("cabin", "Seats and cabin", null, "interior", 1)
    )

    /** Signs a panel was repaired after an accident; any "yes" is a red flag. */
    val accidentSigns = listOf(
        "One panel a slightly different shade from the next",
        "Gaps between panels uneven, wider on one side",
        "Paint on rubber seals, bolts or under the bonnet"
    )

    /** Panel marks and quick answers as counts per [ExteriorDamage] item, for the report and its costs. */
    fun counts(marks: Map<String, Set<PanelIssue>>, answers: Map<String, QuickAnswer>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        marks.values.flatten().forEach { counts[it.itemId] = (counts[it.itemId] ?: 0) + 1 }
        quickChecks.forEach { q -> if (answers[q.id] == QuickAnswer.NEEDS_WORK) counts[q.itemId] = (counts[q.itemId] ?: 0) + q.count }
        return counts
    }

    /** Quick answers as the 1–5 "Your ratings" the PDF already prints. */
    fun ratings(answers: Map<String, QuickAnswer>): Map<String, Int> =
        quickChecks.mapNotNull { q -> answers[q.id]?.let { q.label to it.stars } }.toMap()

    /** The accident signs the buyer answered yes to. */
    fun accidentFindings(answers: Map<Int, Boolean>): List<String> =
        accidentSigns.filterIndexed { i, _ -> answers[i] == true }

    /** Total tentative cost of what's marked so far, min to max. */
    fun costRange(counts: Map<String, Int>): Pair<Int, Int> {
        val lines = ExteriorDamage.lines(counts)
        return lines.sumOf { it.cost!!.minInr } to lines.sumOf { it.cost!!.maxInr }
    }
}
