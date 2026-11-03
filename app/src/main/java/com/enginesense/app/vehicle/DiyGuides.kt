package com.enginesense.app.vehicle

/** How hard a fix is for an owner: [WORKSHOP] means don't try it — special tools, lifting the car, or a safety system. */
enum class DiyRating(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    WORKSHOP("Workshop only")
}

/**
 * One do-it-yourself job, shown in Repair Guidance's "Do it yourself" section. [videoUrl] is the
 * slot for the short how-to video: null until it's recorded and uploaded to the GetAutoSure
 * YouTube channel, then just paste the link here. A [DiyRating.WORKSHOP] entry says [why] instead
 * of listing tools — its video explains why it isn't a DIY job.
 */
data class DiyGuide(
    val id: String,
    val title: String,
    val rating: DiyRating,
    val time: String?,
    val tools: String?,
    val partCost: String?,
    val why: String? = null,
    /** A fault code starting with any of these brings this guide up (e.g. "P030" for every misfire code). */
    val codePrefixes: List<String> = emptyList(),
    /** Words in the fault's title, meaning or likely causes that bring this guide up. */
    val keywords: List<String> = emptyList(),
    val videoUrl: String? = null
)

object DiyGuides {

    val all = listOf(
        DiyGuide(
            "fuel_cap", "Check and tighten the fuel cap", DiyRating.EASY, "2 min", "None", "₹0 (a new cap is ₹300–800)",
            codePrefixes = listOf("P0440", "P0442", "P0455", "P0456", "P0457"),
            keywords = listOf("fuel cap", "gas cap", "evap", "vapor")
        ),
        DiyGuide(
            "jump_start", "Jump-start a car safely", DiyRating.EASY, "10 min", "Jumper cables", "Cables ₹600–1,500",
            keywords = listOf("battery", "charging voltage")
        ),
        DiyGuide(
            "battery_terminals", "Clean the battery terminals", DiyRating.EASY, "15 min", "Wire brush, baking soda, gloves", "Under ₹100",
            keywords = listOf("battery", "charging voltage")
        ),
        DiyGuide(
            "battery_replace", "Replace the car battery", DiyRating.MEDIUM, "30 min", "10 mm spanner", "Battery ₹4,000–9,000",
            keywords = listOf("battery", "charging voltage")
        ),
        DiyGuide(
            "coolant_top_up", "Check and top up coolant (engine cold only)", DiyRating.EASY, "5 min", "None", "Coolant ₹300–600",
            codePrefixes = listOf("P0115", "P0116", "P0117", "P0118", "P0128", "P0217"),
            keywords = listOf("coolant", "overheat", "running hot", "thermostat")
        ),
        DiyGuide(
            "coil_swap", "Find a misfire by swapping ignition coils", DiyRating.MEDIUM, "20 min", "Socket set", "₹0 to find it (a coil is ₹1,500–5,000)",
            codePrefixes = listOf("P030"),
            keywords = listOf("misfire", "ignition coil")
        ),
        DiyGuide(
            "spark_plugs", "Replace the spark plugs", DiyRating.MEDIUM, "30–45 min", "Spark plug socket, ratchet", "Plugs ₹1,000–4,000",
            codePrefixes = listOf("P030"),
            keywords = listOf("misfire", "spark plug")
        ),
        DiyGuide(
            "air_filter", "Replace the engine air filter", DiyRating.EASY, "10 min", "Usually none (clips)", "Filter ₹300–800",
            codePrefixes = listOf("P0100", "P0101", "P0102", "P0103", "P0171", "P0174"),
            keywords = listOf("air filter", "too lean")
        ),
        DiyGuide(
            "maf_clean", "Clean the air-flow (MAF) sensor", DiyRating.MEDIUM, "15 min", "MAF cleaner spray, screwdriver", "Spray ₹400–600",
            codePrefixes = listOf("P0100", "P0101", "P0102", "P0103", "P0171", "P0174"),
            keywords = listOf("maf", "mass airflow")
        ),
        DiyGuide(
            "vacuum_leak", "Find a vacuum leak (cracked hose)", DiyRating.MEDIUM, "30 min", "Torch, spray bottle of soapy water", "Hose ₹200–800",
            codePrefixes = listOf("P0171", "P0174"),
            keywords = listOf("vacuum leak")
        ),
        DiyGuide(
            "throttle_clean", "Clean the throttle body", DiyRating.MEDIUM, "30 min", "Throttle cleaner, screwdriver", "Cleaner ₹300–500",
            codePrefixes = listOf("P0121", "P0122", "P0123", "P0505", "P0506", "P0507"),
            keywords = listOf("throttle", "rough idle")
        ),
        DiyGuide(
            "o2_sensor", "Replace an oxygen sensor", DiyRating.MEDIUM, "45 min", "Oxygen sensor socket", "Sensor ₹3,000–9,000",
            codePrefixes = listOf("P013", "P014", "P015", "P016"),
            keywords = listOf("oxygen sensor", "o2 sensor")
        ),
        DiyGuide(
            "coolant_sensor", "Replace the coolant temperature sensor", DiyRating.MEDIUM, "30 min", "Spanner (engine cold)", "Sensor ₹800–3,000",
            codePrefixes = listOf("P0115", "P0116", "P0117", "P0118"),
            keywords = listOf("temperature sensor")
        ),
        DiyGuide(
            "catalyst", "Catalytic converter", DiyRating.WORKSHOP, null, null, "₹8,000–35,000",
            why = "It's welded into the exhaust and the real cause (misfires, a bad sensor) has to be fixed first, or a new one fails too.",
            codePrefixes = listOf("P0420", "P0430"),
            keywords = listOf("catalytic converter", "catalyst")
        ),
        DiyGuide(
            "injectors", "Fuel injectors", DiyRating.WORKSHOP, null, null, "₹2,500–9,000 per injector",
            why = "The fuel system is under high pressure, and injectors need testing equipment to find the faulty one.",
            codePrefixes = listOf("P020"),
            keywords = listOf("injector")
        ),
        DiyGuide(
            "egr", "EGR valve", DiyRating.WORKSHOP, null, null, "₹3,000–12,000",
            why = "It's buried in the engine on most cars and often needs a scan tool to relearn after cleaning.",
            codePrefixes = listOf("P040"),
            keywords = listOf("egr")
        ),
        DiyGuide(
            "gearbox", "Gearbox", DiyRating.WORKSHOP, null, null, "₹3,000–25,000",
            why = "Gearbox faults need the maker's tools to read, and the work means lifting the car.",
            codePrefixes = listOf("P07", "P08", "P09"),
            keywords = listOf("transmission", "gearbox")
        ),
        DiyGuide(
            "overheating", "Overheating that keeps coming back", DiyRating.WORKSHOP, null, null, "₹2,500–30,000",
            why = "Once topping up coolant doesn't fix it, it's the pump, thermostat, radiator or head gasket, and driving hot can wreck the engine.",
            keywords = listOf("overheat", "running hot")
        )
    )

    /** Easiest first, so a customer sees what they can try themselves before the workshop-only jobs. */
    fun forFinding(code: String?, text: String, limit: Int = 4): List<DiyGuide> {
        val c = code?.uppercase()
        val t = text.lowercase()
        return all
            .filter { g -> (c != null && g.codePrefixes.any { c.startsWith(it) }) || g.keywords.any { it in t } }
            .sortedBy { it.rating.ordinal }
            .take(limit)
    }
}
