package com.enginesense.app.vehiclelookup

/**
 * The customer-mode `vehicle-lookup` response, exactly as the RTO record has it (strings are the
 * RTO's own wording; null means the RTO didn't have it). Customer mode never carries owner,
 * loan or chassis details — that's enforced server-side, not by leaving fields out here.
 */
data class RcLookupResult(
    val registrationNumber: String,
    val make: String?,
    val model: String?,
    val fuelType: String?,
    val color: String?,
    val category: String?,
    val manufacturingMonth: String?,
    val registrationDate: String?,
    val registeredAt: String?,
    val insuranceCompany: String?,
    val insuranceUpto: String?,
    val puccUpto: String?,
    val fitnessUpto: String?,
    val taxUpto: String?,
    /** The RTO returned a reduced record — say "some details aren't available", don't imply the car has none. */
    val partial: Boolean,
    val source: String,
)

/** What the onboarding screens prefill from an [RcLookupResult] — see [RcPrefillMapper]. */
data class RcPrefill(
    /** One of VEHICLE_MAKES when matched, else the RTO's own manufacturer name, title-cased. */
    val brand: String,
    /** One of MODELS_BY_MAKE when matched, else the RTO's own model name, title-cased. */
    val model: String,
    val year: Int?,
    /** One of the details screen's fuel types, or null for the user to pick. */
    val fuelType: String?,
    val registrationNumber: String,
    val looksLikeNonCar: Boolean,
) {
    companion object {
        fun from(result: RcLookupResult): RcPrefill? {
            val make = RcPrefillMapper.matchMake(result.make, result.model)
            val brand = make ?: result.make?.let(RcPrefillMapper::titleCase) ?: return null
            val model = make?.let { RcPrefillMapper.matchModel(it, result.model) }
                ?: result.model?.let(RcPrefillMapper::titleCase)
                ?: return null
            return RcPrefill(
                brand = brand,
                model = model,
                year = RcPrefillMapper.matchYear(result.manufacturingMonth, result.registrationDate),
                fuelType = RcPrefillMapper.matchFuel(result.fuelType),
                registrationNumber = result.registrationNumber,
                looksLikeNonCar = RcPrefillMapper.isLikelyNotACar(result.category),
            )
        }
    }
}
