package com.enginesense.app.dealerreport

/**
 * Turns the RTO record's maker and model into the name buyers know: "KIA INDIA PRIVATE LIMITED" +
 * "SELTOS G1.4 7DCT GTX PLUS" → "Kia Seltos". The RTO stores the maker's company name and the full
 * variant, which overflowed the Status picture's title on a real phone.
 */
object CarNameFormatter {

    /** Keyword in the company name → brand. Checked in order, so "MARUTI" wins over "SUZUKI". */
    private val BRANDS = listOf(
        "MARUTI" to "Maruti Suzuki", "HYUNDAI" to "Hyundai", "TATA" to "Tata", "MAHINDRA" to "Mahindra",
        "KIA" to "Kia", "HONDA" to "Honda", "TOYOTA" to "Toyota", "RENAULT" to "Renault", "NISSAN" to "Nissan",
        "MG MOTOR" to "MG", "SAIC" to "MG", "FORD" to "Ford", "JEEP" to "Jeep", "FCA" to "Jeep",
        "CITROEN" to "Citroen", "MERCEDES" to "Mercedes-Benz", "BMW" to "BMW", "AUDI" to "Audi",
        "VOLVO" to "Volvo", "JAGUAR" to "Jaguar", "LAND ROVER" to "Land Rover", "LEXUS" to "Lexus",
        "BYD" to "BYD", "FIAT" to "Fiat", "DATSUN" to "Datsun", "CHEVROLET" to "Chevrolet",
        "GENERAL MOTORS" to "Chevrolet", "ISUZU" to "Isuzu", "FORCE MOTORS" to "Force", "SUZUKI" to "Suzuki"
    )

    /** Skoda and Volkswagen register under one company in India; the model tells them apart. */
    private val VOLKSWAGEN_MODELS = setOf("POLO", "VENTO", "VIRTUS", "TAIGUN", "TIGUAN", "AMEO", "JETTA", "PASSAT", "T-ROC")

    /** Models whose name is two words; the second word would otherwise be dropped as the variant. */
    private val ALWAYS_TWO_WORDS = setOf("GRAND", "WAGON", "URBAN")
    private val NAME_PREFIXES = setOf("INNOVA", "SCORPIO")
    private val NUMBERED_NAMES = setOf("ALTO", "XUV")

    private val COMPANY_WORDS = setOf("INDIA", "PRIVATE", "PVT", "LIMITED", "LTD", "MOTOR", "MOTORS", "CARS", "CAR",
        "AUTO", "AUTOMOBILES", "CO", "COMPANY", "CORPORATION", "AND", "&", "(INDIA)", "PVT.", "LTD.")

    fun brand(make: String?, model: String?): String? {
        val m = make?.uppercase()?.trim()?.ifBlank { null } ?: return null
        if ("SKODA" in m || "VOLKSWAGEN" in m) {
            val first = model?.uppercase()?.trim()?.split(Regex("\\s+"))?.firstOrNull()
            return if (first in VOLKSWAGEN_MODELS) "Volkswagen" else "Skoda"
        }
        BRANDS.firstOrNull { (key, _) -> Regex("\\b${Regex.escape(key)}\\b").containsMatchIn(m) }?.let { return it.second }
        return m.split(Regex("\\s+")).filter { it !in COMPANY_WORDS }.joinToString(" ") { titleWord(it) }.ifBlank { null }
    }

    fun model(make: String?, model: String?, fuelType: String? = null): String? {
        var words = model?.uppercase()?.trim()?.split(Regex("[\\s/]+"))?.filter { it.isNotBlank() } ?: return null
        // Some records repeat the maker in the model ("MARUTI SWIFT").
        val brandWords = brand(make, null)?.uppercase()?.split(" ").orEmpty()
        while (words.size > 1 && words.first() in brandWords) words = words.drop(1)
        if (words.isEmpty()) return null
        val second = words.getOrNull(1)
        val take = when {
            second == null -> 1
            words.first() in ALWAYS_TWO_WORDS -> 2 // Grand i10, Wagon R
            words.first() in NAME_PREFIXES && second.none(Char::isDigit) -> 2 // Scorpio N, Innova Crysta — but not "Innova 2.5"
            words.first() in NUMBERED_NAMES && second.any(Char::isDigit) -> 2 // Alto K10, XUV 500
            else -> 1
        }
        var name = words.take(take).joinToString(" ") { titleWord(it) }
        if (fuelType.equals("Electric", ignoreCase = true) && words.getOrNull(take) == "EV") name += " EV"
        return name
    }

    /** The trim from the RTO model text: "SWIFT VXI" → "VXI", "SELTOS G1.4 7DCT GTX PLUS" → "GTX PLUS". At most two words. */
    fun variant(make: String?, model: String?, fuelType: String? = null): String? {
        val name = model(make, model, fuelType) ?: return null
        val words = model!!.uppercase().trim().split(Regex("[\\s/]+")).filter { it.isNotBlank() }
        val brandWords = brand(make, null)?.uppercase()?.split(" ").orEmpty()
        val rest = words.dropWhile { it in brandWords }.drop(name.split(" ").size)
        // Engine and gearbox codes (G1.4, 1.5L, 7DCT) are left out: the trim name is what buyers know.
        return rest.filter { w -> w.none(Char::isDigit) && CarNameFormatter.transmission(w) == null }.take(2).joinToString(" ").ifBlank { null }
    }

    /** Gearbox from the variant text, when it says: "7DCT", "AMT", "CVT", "AT" → Automatic; "MT" → Manual; else null. */
    fun transmission(model: String?): String? {
        val words = model?.uppercase()?.split(Regex("[\\s/.-]+")).orEmpty()
        return when {
            words.any { Regex("^\\d?(DCT|DSG|AMT|CVT|IVT|AT|TC|AUTO|AUTOMATIC)$").matches(it) } -> "Automatic"
            words.any { Regex("^\\d?(MT|MANUAL)$").matches(it) } -> "Manual"
            else -> null
        }
    }

    /** "PETROL/CNG" → "Petrol + CNG", "DIESEL" → "Diesel"; CNG, LPG and EV stay in capitals. */
    fun fuel(raw: String?): String? {
        val parts = raw?.split('/', '+', ',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (parts.isEmpty()) return null
        return parts.joinToString(" + ") { p ->
            val up = p.uppercase()
            if (up in setOf("CNG", "LPG", "EV")) up else p.lowercase().split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        }
    }

    /** "Kia Seltos", or whatever part is known; null when the record has neither. */
    fun carName(make: String?, model: String?, fuelType: String? = null): String? =
        listOfNotNull(brand(make, model), model(make, model, fuelType)).joinToString(" ").ifBlank { null }

    private fun titleWord(w: String): String = when {
        Regex("^I\\d+$").matches(w) -> "i" + w.drop(1) // Hyundai i10 / i20
        w.any(Char::isDigit) && w.length <= 6 -> w // XUV700, K10, 3XO
        w.length <= 2 -> w // MG, EV
        else -> w.lowercase().replaceFirstChar { it.uppercase() }
    }
}
