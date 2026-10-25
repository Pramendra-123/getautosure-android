package com.enginesense.app.vehicle.pricing

/**
 * Static dropdown data for the simplified "Max Buy Price" screen, which replaced the
 * Phase 1/1.1/2 comparable-matching flow entirely (that engine and its models were removed —
 * the dealer now supplies the selling price directly rather than it being derived from
 * comparable sales).
 *
 * Make/model lists and the state-to-city mapping are placeholder, representative data pending
 * the real lists the product owner said they'd provide ("I will share the pricing logic later")
 * — never presented as a complete or authoritative list, just enough for the dropdowns to work
 * today. Swap the contents of this file, not its shape, when the real data arrives.
 */
object DealerVehicleOptions {
    val makes = listOf("Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Honda", "Toyota", "Kia", "Volkswagen", "Skoda", "Renault")

    // Flat list, not yet filtered per make — there's no real make-to-model mapping supplied yet.
    val models = listOf(
        "Swift", "Baleno", "Wagon R", "Alto", "i10", "i20", "Creta", "Venue",
        "Nexon", "Tiago", "Harrier", "XUV300", "Scorpio", "City", "Amaze",
        "Innova", "Fortuner", "Seltos", "Sonet", "Polo", "Virtus", "Rapid", "Kwid"
    )

    /** Last 15 years, newest first. */
    val years: List<Int> = (2026 downTo 2012).toList()

    val states = listOf("Delhi", "Haryana", "Uttar Pradesh", "Karnataka", "Maharashtra", "Telangana", "Tamil Nadu", "Gujarat", "Rajasthan", "West Bengal")

    val citiesByState: Map<String, List<String>> = mapOf(
        "Delhi" to listOf("New Delhi"),
        "Haryana" to listOf("Gurgaon", "Faridabad", "Panipat"),
        "Uttar Pradesh" to listOf("Noida", "Ghaziabad", "Lucknow"),
        "Karnataka" to listOf("Bengaluru", "Mysuru"),
        "Maharashtra" to listOf("Mumbai", "Pune", "Nagpur"),
        "Telangana" to listOf("Hyderabad"),
        "Tamil Nadu" to listOf("Chennai", "Coimbatore"),
        "Gujarat" to listOf("Ahmedabad", "Surat"),
        "Rajasthan" to listOf("Jaipur"),
        "West Bengal" to listOf("Kolkata")
    )

    fun citiesFor(state: String): List<String> = citiesByState[state] ?: emptyList()

    /** How Indian used-car listings conventionally describe ownership — a dropdown, not a free-typed number. */
    val ownershipOptions = listOf("1st Owner", "2nd Owner", "3rd Owner", "4th Owner+")

    /** 1 (Poor) to 5 (Excellent) — used identically for engine, exterior and interior condition ratings. Descriptive/context only for now, same as the other fields in this file; not yet wired into the buy-price calculation pending the real pricing logic. */
    val conditionRatingOptions = listOf(1, 2, 3, 4, 5)

    fun conditionRatingLabel(rating: Int): String = when (rating) {
        1 -> "1 - Poor"
        2 -> "2 - Fair"
        3 -> "3 - Good"
        4 -> "4 - Very good"
        5 -> "5 - Excellent"
        else -> rating.toString()
    }
}

enum class OdometerBucket(val label: String) {
    UNDER_20K("0 - 20,000 km"),
    FROM_20K_TO_40K("20,000 - 40,000 km"),
    FROM_40K_TO_60K("40,000 - 60,000 km"),
    FROM_60K_TO_1L("60,000 - 1,00,000 km"),
    OVER_1L("1,00,000+ km")
}
