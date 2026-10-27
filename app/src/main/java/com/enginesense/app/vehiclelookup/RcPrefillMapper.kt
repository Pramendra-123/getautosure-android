package com.enginesense.app.vehiclelookup

import com.enginesense.app.ui.screens.MODELS_BY_MAKE

/**
 * Turns the RTO's own wording (via the `vehicle-lookup` Edge Function) into what the onboarding
 * screens already use: a make from VEHICLE_MAKES, a model from MODELS_BY_MAKE, a fuel from the
 * details screen's list, a year. Pure, so it's unit-tested without a network or a device.
 *
 * When something can't be matched it's passed through as the RTO wrote it (title-cased), or left
 * null for the user to pick — never swapped for a "closest" guess. The customer always sees and
 * confirms the result before anything is saved.
 */
object RcPrefillMapper {

    /** Same normalization as the Edge Function's normalizeRegistrationNumber — keep the two in sync. */
    fun normalizePlate(raw: String): String = raw.replace(Regex("[\\s.\\-]"), "").uppercase()

    private val STANDARD_PLATE = Regex("^[A-Z]{2}\\d{1,2}[A-Z]{0,3}\\d{1,4}$")
    private val BH_PLATE = Regex("^\\d{2}BH\\d{4}[A-Z]{1,2}$")

    /** Same rules as the Edge Function's isValidIndianRegistration — checked here first so a typo never costs a lookup. */
    fun isValidPlate(normalized: String): Boolean =
        STANDARD_PLATE.matches(normalized) || BH_PLATE.matches(normalized)

    // Manufacturer names as the RTO records them ("MARUTI SUZUKI INDIA LTD",
    // "HYUNDAI MOTOR INDIA LTD", "MAHINDRA & MAHINDRA LIMITED", ...) contain one of these. Some
    // manufacturers build several brands (Skoda Auto Volkswagen India, Jaguar Land Rover, Nissan
    // with Datsun), which is why matchMake falls back to the model name when this is ambiguous.
    private val MAKE_KEYWORDS: List<Pair<String, List<String>>> = listOf(
        "Maruti Suzuki" to listOf("MARUTI"),
        "Hyundai" to listOf("HYUNDAI"),
        "Tata" to listOf("TATA"),
        "Mahindra" to listOf("MAHINDRA"),
        "Kia" to listOf("KIA"),
        "Honda" to listOf("HONDA"),
        "Toyota" to listOf("TOYOTA"),
        "Volkswagen" to listOf("VOLKSWAGEN"),
        "Skoda" to listOf("SKODA"),
        "Renault" to listOf("RENAULT"),
        "Nissan" to listOf("NISSAN"),
        "Datsun" to listOf("DATSUN", "NISSAN"),
        "Ford" to listOf("FORD"),
        "MG Motor" to listOf("MG MOTOR", "MORRIS GARAGES", "SAIC"),
        "Jeep" to listOf("JEEP", "FCA", "STELLANTIS"),
        "Citroen" to listOf("CITROEN", "STELLANTIS"),
        "BYD" to listOf("BYD"),
        "Mercedes-Benz" to listOf("MERCEDES", "DAIMLER"),
        "BMW" to listOf("BMW"),
        "Audi" to listOf("AUDI"),
        "Volvo" to listOf("VOLVO"),
        "Land Rover" to listOf("LAND ROVER"),
        "Jaguar" to listOf("JAGUAR"),
        "Lexus" to listOf("LEXUS"),
        "Isuzu" to listOf("ISUZU"),
        "Fiat" to listOf("FIAT", "FCA"),
        "Mitsubishi" to listOf("MITSUBISHI"),
    )

    /** Upper-case letters and digits only — "Grand i10 Nios" and "GRAND I10 NIOS SPORTZ" compare cleanly. */
    private fun squash(s: String): String = s.uppercase().filter { it.isLetterOrDigit() }

    /** The app's known model this RTO model string names, longest name first ("Scorpio-N" before "Scorpio"), or null. */
    fun matchModel(make: String, rtoModel: String?): String? {
        if (rtoModel.isNullOrBlank()) return null
        val target = squash(rtoModel)
        return MODELS_BY_MAKE[make].orEmpty()
            .filter { squash(it).isNotEmpty() && target.contains(squash(it)) }
            .maxByOrNull { squash(it).length }
    }

    /** One of VEHICLE_MAKES, or null when the RTO's manufacturer isn't one the app lists. */
    fun matchMake(rtoMaker: String?, rtoModel: String?): String? {
        if (rtoMaker.isNullOrBlank()) return null
        val maker = rtoMaker.uppercase()
        val candidates = MAKE_KEYWORDS.filter { (_, keys) -> keys.any { maker.contains(it) } }.map { it.first }
        if (candidates.size <= 1) return candidates.firstOrNull()
        // Manufacturer builds several brands: let the model decide, e.g. "SKODA AUTO VOLKSWAGEN
        // INDIA" + "VIRTUS GT" is a Volkswagen, "JAGUAR LAND ROVER" + "RANGE ROVER EVOQUE" a Land
        // Rover. An unknown manufacturer is never matched by model alone — "CITY" in some other
        // maker's model name doesn't make it a Honda.
        return candidates.firstOrNull { matchModel(it, rtoModel) != null } ?: candidates.first()
    }

    /** Maps to the details screen's fuel list; null for anything it doesn't cover (e.g. LPG), so the user picks. */
    fun matchFuel(rtoFuel: String?): String? {
        val f = rtoFuel?.uppercase() ?: return null
        return when {
            "HYBRID" in f -> "Hybrid"
            "ELECTRIC" in f || "BOV" in f || f == "EV" -> "Electric"
            "CNG" in f -> "CNG" // "PETROL/CNG" — a CNG-fitted car is a CNG car for this app's purposes
            "DIESEL" in f -> "Diesel"
            "PETROL" in f -> "Petrol"
            else -> null
        }
    }

    /** Manufacturing month ("2019-03") first, registration date ("2019-04-12") as the fallback. */
    fun matchYear(manufacturingMonth: String?, registrationDate: String?): Int? =
        listOfNotNull(manufacturingMonth, registrationDate)
            .firstNotNullOfOrNull { Regex("^(\\d{4})").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?.takeIf { it in 1950..2100 }

    /** OBD2 is cars-only; RTO records cover two- and three-wheelers too ("M-Cycle/Scooter", "e-Rickshaw"...). */
    fun isLikelyNotACar(category: String?): Boolean {
        val c = category?.uppercase() ?: return false
        return listOf("CYCLE", "SCOOTER", "MOPED", "RICKSHAW", "3W", "2W", "THREE WHEEL", "TWO WHEEL").any { it in c }
    }

    /** "BAJAJ AUTO LTD" -> "Bajaj Auto Ltd" — for showing the RTO's own name when the app has no matching make/model. */
    fun titleCase(s: String): String =
        s.trim().lowercase().split(Regex("\\s+")).joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}
