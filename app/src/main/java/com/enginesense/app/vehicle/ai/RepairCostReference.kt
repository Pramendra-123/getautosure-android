package com.enginesense.app.vehicle.ai

/**
 * Tentative Indian repair-cost ranges — explicitly NOT real, sourced pricing data. Unlike the
 * rest of this app's "never invent data" rule (which is strictly about a specific user's own
 * vehicle/scan), these are deliberately approximate ballpark figures from general automotive
 * knowledge, included at the user's explicit request so "how much could this cost?" has
 * something more useful to say than a flat refusal. Every surface that shows one of these
 * ([UnderstandRepairScreen][com.enginesense.app.ui.screens.UnderstandRepairScreen],
 * [RuleBasedAiMechanicChatService]) must keep it clearly labeled tentative, never presented as
 * a real quote — real pricing varies by city, workshop, and car segment far more than a single
 * range can capture.
 */
data class RepairCostEntry(
    val category: String,
    val minInr: Int,
    val maxInr: Int,
    val note: String,
    val keywords: List<String>
)

object RepairCostReference {

    val entries = listOf(
        RepairCostEntry("Battery replacement", 4_000, 9_000, "Varies by brand and warranty period", listOf("battery")),
        RepairCostEntry("Brake pads (front pair)", 1_500, 4_500, "OEM vs. aftermarket parts", listOf("brake pad", "brake pads", "front brake")),
        RepairCostEntry("Brake pads (rear pair)", 1_200, 3_500, "Often cheaper than front", listOf("rear brake")),
        RepairCostEntry("Brake disc/rotor replacement", 2_500, 8_000, "Per axle, parts and labor", listOf("brake disc", "brake rotor", "disc brake")),
        RepairCostEntry("Clutch replacement", 6_000, 18_000, "Hatchback vs. SUV/diesel", listOf("clutch")),
        RepairCostEntry("AC compressor / gas top-up", 2_000, 15_000, "Top-up vs. full compressor swap", listOf("ac compressor", "ac gas", "air conditioning", "ac not cooling", "ac not cold")),
        RepairCostEntry("Radiator / cooling system", 2_500, 12_000, "Hose/thermostat vs. radiator replacement", listOf("radiator", "cooling system", "coolant", "overheat", "overheating", "thermostat")),
        RepairCostEntry("Timing belt / chain", 5_000, 20_000, "Belt vs. chain, labor-heavy job", listOf("timing belt", "timing chain")),
        RepairCostEntry("Spark plugs (set)", 1_000, 4_000, "More for iridium/platinum plugs", listOf("spark plug", "spark plugs")),
        RepairCostEntry("Oxygen (O2) sensor", 3_000, 9_000, "Per sensor, varies by position", listOf("oxygen sensor", "o2 sensor")),
        RepairCostEntry("Catalytic converter", 8_000, 35_000, "Aftermarket vs. OEM, varies widely", listOf("catalytic converter", "catalyst")),
        RepairCostEntry("Wheel alignment & balancing", 500, 1_500, "Routine service item", listOf("wheel alignment", "wheel balancing", "alignment")),
        RepairCostEntry("Suspension (shock absorber pair)", 4_000, 14_000, "Front or rear pair", listOf("shock absorber", "suspension")),
        RepairCostEntry("Water pump replacement", 3_000, 9_000, "Often paired with a timing belt job", listOf("water pump")),
        RepairCostEntry("Starter motor", 3_500, 10_000, "Repair vs. full replacement", listOf("starter motor", "starter")),
        RepairCostEntry("Alternator", 4_000, 12_000, "Repair vs. full replacement", listOf("alternator")),
        RepairCostEntry("Transmission/gearbox service", 2_500, 8_000, "Fluid change vs. deeper repair", listOf("transmission", "gearbox")),
        RepairCostEntry("Head gasket replacement", 10_000, 30_000, "Labor-intensive, engine-dependent", listOf("head gasket")),
        RepairCostEntry("Tyre replacement (per tyre)", 3_000, 9_000, "Depends on size and brand", listOf("tyre", "tire")),
        RepairCostEntry("General service (oil, filters)", 2_000, 6_000, "Routine interval service", listOf("oil change", "general service", "car service"))
    )

    /** The six most commonly asked-about categories — used for the Repair Guidance screen's inline preview, full list is [entries]. */
    val previewCategories = listOf(
        "Battery replacement", "Brake pads (front pair)", "Clutch replacement",
        "AC compressor / gas top-up", "Radiator / cooling system", "Timing belt / chain"
    )
    val preview: List<RepairCostEntry> get() = previewCategories.mapNotNull { name -> entries.firstOrNull { it.category == name } }

    /** Matches a finding/referent title (e.g. "Engine overheating") against a known cost category, or null if none fits. */
    fun findByTitle(title: String): RepairCostEntry? = findByText(title)

    /** Matches free text (e.g. a direct question like "how much for a clutch job?") against a known cost category. */
    fun findByQuestion(question: String): RepairCostEntry? = findByText(question)

    /** Every category the text mentions — e.g. a fault whose causes name both the O2 sensor and the catalytic converter gets both, since either could be the actual repair. */
    fun findAllByText(text: String): List<RepairCostEntry> {
        val q = text.lowercase()
        return entries.filter { entry -> entry.keywords.any { matchesKeyword(q, it) } }
    }

    private fun findByText(text: String): RepairCostEntry? {
        val q = text.lowercase()
        return entries.firstOrNull { entry -> entry.keywords.any { matchesKeyword(q, it) } }
    }

    /** Same short-keyword word-boundary fix as GeneralAutomotiveKnowledge.matchesKeyword — a bare substring check on a short single word (e.g. "ac") would false-match inside unrelated words. */
    private fun matchesKeyword(q: String, keyword: String): Boolean = when {
        " " in keyword -> keyword in q
        keyword.length <= 3 -> Regex("""\b${Regex.escape(keyword)}\b""").containsMatchIn(q)
        else -> keyword in q
    }
}
