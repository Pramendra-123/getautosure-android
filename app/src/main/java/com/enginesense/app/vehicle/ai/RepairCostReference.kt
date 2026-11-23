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

/** Rough size class, which moves parts and labour prices more than anything else the app knows. */
enum class CarSize {
    SMALL, MID, LARGE;

    companion object {
        /** From the new-car price: under ₹8 lakh is a small car, up to ₹15 lakh mid-size, above that large. */
        fun fromExShowroom(priceInr: Double?): CarSize? = when {
            priceInr == null || priceInr <= 0 -> null
            priceInr < 800_000 -> SMALL
            priceInr <= 1_500_000 -> MID
            else -> LARGE
        }
    }
}

/**
 * Narrows a range to the car's size: each range runs from a hatchback's price at the low end
 * to an SUV's at the high end, so a known size picks the matching part of it. Still tentative.
 */
fun RepairCostEntry.forSize(size: CarSize?): RepairCostEntry {
    if (size == null) return this
    val span = maxInr - minInr
    fun round(v: Double) = ((v / 100).toInt() * 100).coerceAtLeast(100)
    val (lo, hi) = when (size) {
        CarSize.SMALL -> 0.0 to 0.5
        CarSize.MID -> 0.25 to 0.75
        CarSize.LARGE -> 0.5 to 1.0
    }
    return copy(minInr = round(minInr + span * lo), maxInr = round(minInr + span * hi))
}

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
        RepairCostEntry("Vacuum leak repair", 500, 4_000, "Usually a cracked hose or gasket", listOf("vacuum leak", "intake leak")),
        // Added with the fault-code cards: parts their causes name that the table above didn't cover.
        // Same footing — tentative, labour included, hatchback at the low end and SUV at the high end.
        RepairCostEntry("Diesel fuel filter", 800, 3_000, "Draining water from it is part of the job", listOf("diesel filter", "diesel fuel filter")),
        RepairCostEntry("Fuel filter", 500, 2_500, "Petrol cars; often skipped at service", listOf("fuel filter")),
        RepairCostEntry("Air filter", 300, 1_500, "Clogs fast on dusty roads", listOf("air filter")),
        RepairCostEntry("Fuel pump", 4_000, 12_000, "In-tank pump, petrol and diesel", listOf("fuel pump")),
        RepairCostEntry("Diesel high-pressure pump", 15_000, 80_000, "Repair/recondition vs. a new pump", listOf("high-pressure pump", "high pressure pump", "hp pump")),
        RepairCostEntry("Fuel rail pressure sensor", 3_000, 10_000, "Diesel and direct-injection petrol", listOf("rail pressure")),
        RepairCostEntry("Diesel injector", 4_000, 15_000, "Per injector; testing and cleaning first is cheaper", listOf("diesel injector", "common rail injector")),
        RepairCostEntry("Glow plugs (set)", 2_000, 8_000, "Diesel cold starting", listOf("glow plug")),
        RepairCostEntry("Turbocharger", 15_000, 70_000, "Repair vs. replacement; hoses and actuator cost less", listOf("turbo")),
        RepairCostEntry("DPF cleaning / replacement", 4_000, 60_000, "Cleaning vs. a new filter", listOf("dpf", "particulate filter")),
        RepairCostEntry("AdBlue / SCR system", 3_000, 40_000, "Refill and sensor vs. pump or catalyst", listOf("adblue", "scr")),
        RepairCostEntry("EGR cooler", 6_000, 20_000, "Diesel; often found when the EGR valve is cleaned", listOf("egr cooler")),
        RepairCostEntry("Crankshaft position sensor", 1_500, 5_000, "Small part; the car may not start without it", listOf("crankshaft position", "crankshaft sensor", "crank sensor")),
        RepairCostEntry("Camshaft position sensor", 1_500, 5_000, "Small part, quick job", listOf("camshaft position", "camshaft sensor", "cam sensor")),
        RepairCostEntry("VVT oil control valve", 2_000, 6_000, "Check the engine oil level first", listOf("oil control valve", "vvt", "variable valve")),
        RepairCostEntry("Knock sensor", 1_500, 5_000, "Small part; fuel quality can trigger it too", listOf("knock sensor")),
        RepairCostEntry("MAP / boost pressure sensor", 1_500, 5_000, "Cleaning sometimes fixes it", listOf("map sensor", "manifold absolute", "manifold pressure", "boost pressure sensor")),
        RepairCostEntry("Intake air temperature sensor", 800, 3_000, "Small part, quick job", listOf("intake air temp")),
        RepairCostEntry("Accelerator pedal sensor", 2_000, 8_000, "Usually sold with the pedal", listOf("pedal position", "accelerator pedal")),
        RepairCostEntry("Idle air control valve", 1_500, 5_000, "Cleaning vs. replacement", listOf("idle air control", "idle control valve")),
        RepairCostEntry("Engine wiring / connector repair", 500, 5_000, "Rat-bitten or corroded wires are common", listOf("wiring", "harness", "connector")),
        RepairCostEntry("Engine computer (ECU)", 15_000, 60_000, "Rarely the real fault — rule out wiring first", listOf("engine computer", "ecu replacement", "control module replacement")),
        RepairCostEntry("Wheel speed (ABS) sensor", 1_500, 5_000, "Per wheel; often just needs cleaning", listOf("wheel speed sensor", "abs sensor", "wheel sensor")),
        RepairCostEntry("Vehicle speed sensor", 1_000, 4_000, "Gearbox-mounted on older cars", listOf("vehicle speed sensor", "speed sensor")),
        RepairCostEntry("ABS module", 15_000, 60_000, "Repair vs. a new unit", listOf("abs module", "abs pump")),
        RepairCostEntry("Radiator fan / fan relay", 1_500, 8_000, "Relay or fuse vs. the fan motor", listOf("radiator fan", "cooling fan", "fan relay")),
        RepairCostEntry("Engine oil leak / gasket", 1_500, 8_000, "Valve-cover gasket vs. a crankshaft seal", listOf("oil leak", "valve cover gasket", "oil seal")),
        RepairCostEntry("Oil pressure switch / sensor", 800, 3_000, "Check the oil level before replacing it", listOf("oil pressure")),
        RepairCostEntry("Engine mount", 2_000, 7_000, "Per mount", listOf("engine mount")),
        RepairCostEntry("Clutch master / slave cylinder", 2_500, 8_000, "Hydraulic clutch leak or soft pedal", listOf("clutch master", "slave cylinder", "clutch cylinder")),
        RepairCostEntry("AMT gear actuator", 15_000, 45_000, "Repair kit vs. a new actuator", listOf("amt actuator", "gear actuator", "amt")),
        RepairCostEntry("Automatic / CVT gearbox repair", 30_000, 150_000, "Fluid and solenoids vs. a gearbox overhaul", listOf("cvt", "torque converter", "automatic transmission repair")),
        RepairCostEntry("Gear position / range sensor", 2_000, 8_000, "Automatic and AMT cars", listOf("range sensor", "gear position sensor")),
        RepairCostEntry("Gearbox shift solenoid", 3_000, 12_000, "Automatic cars; with a fluid change", listOf("shift solenoid")),
        RepairCostEntry("Wheel bearing", 2_000, 6_000, "Per wheel", listOf("wheel bearing")),
        RepairCostEntry("CV joint / drive shaft", 3_000, 12_000, "A torn boot alone is cheaper", listOf("cv joint", "drive shaft", "driveshaft", "axle boot")),
        RepairCostEntry("Power steering repair", 4_000, 30_000, "Sensor or wiring vs. the steering motor or rack", listOf("power steering", "eps")),
        RepairCostEntry("AC condenser", 4_000, 12_000, "Includes a gas refill", listOf("condenser")),
        RepairCostEntry("AC blower motor", 2_500, 7_000, "Resistor is the cheaper fix", listOf("blower")),
        RepairCostEntry("Airbag sensor / clock spring", 3_000, 12_000, "Safety system — don't leave the light on", listOf("airbag", "clock spring", "srs")),
        RepairCostEntry("Exhaust leak / silencer repair", 1_000, 6_000, "Welding vs. a new section", listOf("exhaust leak", "silencer", "muffler")),
        RepairCostEntry("CNG kit service / tuning", 1_000, 4_000, "At an authorised kit fitter", listOf("cng kit", "lpg kit", "cng tuning")),
        RepairCostEntry("Power window motor", 2_000, 6_000, "Per door", listOf("power window", "window motor")),
        RepairCostEntry("Wiper motor", 1_500, 5_000, "Front wiper", listOf("wiper"))
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
