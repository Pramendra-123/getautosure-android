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
        RepairCostEntry("General service (oil, filters)", 2_000, 6_000, "Routine interval service", listOf("oil change", "general service", "car service")),
        RepairCostEntry("Ignition coil", 1_500, 5_000, "Per coil; often found by swapping coils between cylinders", listOf("ignition coil", "coil")),
        RepairCostEntry("Fuel injector", 2_500, 9_000, "Cleaning vs. replacing one injector", listOf("injector")),
        RepairCostEntry("Air-flow (MAF) sensor", 3_000, 10_000, "Cleaning can fix it; replacement costs more", listOf("maf", "mass airflow", "air flow")),
        RepairCostEntry("Throttle body", 800, 12_000, "Cleaning vs. replacing the unit", listOf("throttle")),
        RepairCostEntry("EGR valve", 3_000, 12_000, "Cleaning vs. replacement; mostly diesels", listOf("egr")),
        RepairCostEntry("EVAP purge valve / fuel cap", 300, 4_000, "A loose or worn fuel cap is the cheap fix", listOf("evap", "purge", "fuel cap", "gas cap", "vapor")),
        RepairCostEntry("Coolant temperature sensor", 800, 3_000, "Small part, quick job", listOf("coolant temp", "temperature sensor")),
        RepairCostEntry("Vacuum leak repair", 500, 4_000, "Usually a cracked hose or gasket", listOf("vacuum leak", "intake leak"))
    )

    /**
     * A fault with no specific entry still gets a tentative range, by which system its code
     * belongs to, so the app never answers "how much?" with "I can't say". Wider than a specific
     * entry on purpose: it covers the usual parts for that system plus a diagnosis fee.
     */
    fun fallbackFor(code: String?): RepairCostEntry {
        val c = code?.uppercase().orEmpty()
        val subsystem = c.getOrNull(2)
        return when {
            c.startsWith("P") && (subsystem == '1' || subsystem == '2') ->
                RepairCostEntry("Fuel or air sensor / part", 1_500, 8_000, "Most are a sensor, hose or connector", emptyList())
            c.startsWith("P") && subsystem == '3' ->
                RepairCostEntry("Ignition part (plug, coil or wiring)", 1_000, 6_000, "Plugs and coils are the usual fix", emptyList())
            c.startsWith("P") && subsystem == '4' ->
                RepairCostEntry("Emission-control part", 1_500, 12_000, "From a sensor up to a catalytic converter", emptyList())
            c.startsWith("P") && (subsystem == '5' || subsystem == '0') ->
                RepairCostEntry("Engine sensor or idle-control part", 1_000, 6_000, "Usually a sensor or cleaning job", emptyList())
            c.startsWith("P") && subsystem == '6' ->
                RepairCostEntry("Wiring or engine computer", 1_000, 15_000, "Wiring fixes are cheap; a new computer isn't", emptyList())
            c.startsWith("P") && subsystem in listOf('7', '8', '9') ->
                RepairCostEntry("Gearbox sensor or service", 3_000, 25_000, "From a sensor or fluid change up to gearbox repair", emptyList())
            c.startsWith("B") || c.startsWith("C") || c.startsWith("U") ->
                RepairCostEntry("Electrical or body-module repair", 1_000, 10_000, "Often wiring or a connector", emptyList())
            else ->
                RepairCostEntry("Diagnosis and repair", 1_000, 10_000, "Includes a workshop diagnosis fee of about ₹500–1,500", emptyList())
        }
    }

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
