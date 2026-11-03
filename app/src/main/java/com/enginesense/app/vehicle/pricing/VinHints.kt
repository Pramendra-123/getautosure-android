package com.enginesense.app.vehicle.pricing

/**
 * What a chassis number (VIN) says on its own, used only to pre-fill the Used Car Check's price
 * card — the buyer can always change it, and an RTO record wins over both. The model-year code is
 * the ISO 3779 one most makers in India follow; the maker codes are the common Indian ones.
 */
object VinHints {

    private val YEAR_CODES = "ABCDEFGHJKLMNPRSTVWXY"

    private val MAKERS = mapOf(
        "MA3" to "Maruti Suzuki", "MAL" to "Hyundai", "MAT" to "Tata", "MA1" to "Mahindra",
        "MAK" to "Honda", "MBJ" to "Toyota", "MAJ" to "Ford", "MEE" to "Renault", "MDH" to "Nissan"
    )

    /** Null for anything that isn't a real 17-character VIN. */
    fun modelYear(vin: String?, currentYear: Int): Int? {
        val v = vin?.trim()?.uppercase()?.takeIf { isVin(it) } ?: return null
        val code = v[9]
        val year = when {
            code in '1'..'9' -> 2000 + (code - '0')
            else -> YEAR_CODES.indexOf(code).takeIf { it >= 0 }?.let { 2010 + it } ?: return null
        }
        // Codes repeat every 30 years; a year in the future means the earlier cycle.
        return if (year > currentYear + 1) year - 30 else year
    }

    fun make(vin: String?): String? = vin?.trim()?.uppercase()?.takeIf { isVin(it) }?.let { MAKERS[it.take(3)] }

    private fun isVin(v: String) = v.length == 17 && v.all { it.isLetterOrDigit() } && v.none { it in "IOQ" }
}
