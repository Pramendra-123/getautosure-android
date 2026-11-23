package com.enginesense.app.storage

/**
 * Whether a scanned VIN can belong to a car in My Garage, from what the app already knows about
 * the car: its RTO chassis number (often masked, "MEXBPAPB6RG0*****") and its maker. Used before a
 * car with no VIN yet learns one — a scan taken in another car must not quietly become this car's.
 */
object VinMatch {

    /**
     * World manufacturer codes (first three VIN characters) for cars built in India. Only codes
     * we're sure of; an unknown code never blocks a match.
     */
    private val MAKERS = mapOf(
        "MA3" to setOf("Maruti Suzuki"),
        "MAL" to setOf("Hyundai"),
        "MAT" to setOf("Tata"),
        "MA1" to setOf("Mahindra"),
        "MAK" to setOf("Honda"),
        "MBJ" to setOf("Toyota"),
        "MZB" to setOf("Kia"),
        "MEX" to setOf("Skoda", "Volkswagen"),
        "MAJ" to setOf("Ford")
    )

    /** The makers a VIN's first three characters stand for, or null when the code isn't in the list. */
    fun makersOf(vin: String): Set<String>? = MAKERS[vin.trim().uppercase().take(3)]

    /** The visible start of an RTO chassis number ("MEXBPAPB6RG0" from "MEXBPAPB6RG0*****"); null when too short to trust. */
    fun chassisPrefix(chassis: String?): String? =
        chassis?.trim()?.uppercase()?.takeWhile { it.isLetterOrDigit() }?.takeIf { it.length >= 8 }

    /**
     * true when the RTO chassis number confirms the VIN, false when the chassis number or the
     * maker rules it out, null when there's nothing to go on.
     */
    fun fits(car: GarageCar, vin: String): Boolean? {
        val v = vin.trim().uppercase()
        chassisPrefix(car.profile.rc?.chassisNumber)?.let { return v.startsWith(it) }
        val brand = car.profile.brand?.takeIf { it.isNotBlank() } ?: return null
        val makers = makersOf(v) ?: return null
        return makers.any { it.equals(brand, ignoreCase = true) }
    }
}
