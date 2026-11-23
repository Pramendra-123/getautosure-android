package com.enginesense.app.vehiclelookup

/**
 * The `vehicle-lookup` response, exactly as the RTO record has it (strings are the RTO's own
 * wording; null means the RTO didn't have it). The fields after [chassisMatch] only come back in
 * dealer mode — customer mode never carries owner, loan or chassis details, and that's enforced
 * server-side, not by leaving fields out here. The owner's name is never read at all, so no
 * report can print it.
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
    /** RTO wording, e.g. "999.00" — see [RcPrefillMapper.matchEngineCc]. */
    val cubicCapacity: String?,
    val insuranceCompany: String?,
    val insuranceUpto: String?,
    val puccUpto: String?,
    val fitnessUpto: String?,
    val taxUpto: String?,
    /** The RTO returned a reduced record — say "some details aren't available", don't imply the car has none. */
    val partial: Boolean,
    val source: String,
    /** Whether the VIN sent with the lookup matches the RC's chassis number; null when it couldn't be compared (or no VIN was sent). */
    val chassisMatch: Boolean? = null,
    val bodyType: String? = null,
    /** When our server fetched the record from Surepass (ISO-8601) — a cached record can be up to 30 days old. */
    val fetchedAt: String? = null,
    /** "dealer" when the server returned the full record, else "customer". */
    val mode: String = "customer",
    val ownerCount: Int? = null,
    /** True when the RC shows a loan (hypothecation) on the car. */
    val financed: Boolean? = null,
    val financer: String? = null,
    val rcStatus: String? = null,
    val blacklistStatus: String? = null,
    val nocDetails: String? = null,
    val nonUseStatus: String? = null,
    /** Full numbers — print them through [maskIdentifier], never as-is. */
    val chassisNumber: String? = null,
    val engineNumber: String? = null,
    val emissionNorms: String? = null,
    val cylinders: Int? = null,
    val seatCapacity: Int? = null,
    val unladenWeightKg: Int? = null,
    val rtoCode: String? = null,
    /** When the RTO record itself was last updated on Surepass's side. */
    val recordUpdatedOn: String? = null,
) {
    val hasDealerDetails: Boolean get() = mode == "dealer"
}

/**
 * Chassis and engine numbers on a shared report show only their last five characters — enough
 * for a buyer to match against the number stamped on the car, not enough to clone its papers.
 */
fun maskIdentifier(value: String?): String? {
    val clean = value?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    val visible = clean.takeLast(5)
    return "•".repeat((clean.length - visible.length).coerceIn(3, 12)) + visible
}

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
    /** Engine size in cc, when the RTO record has it — lets Live Data estimate fuel use on cars without a MAF sensor. */
    val engineCc: Int? = null,
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
                engineCc = RcPrefillMapper.engineCcFor(result),
            )
        }
    }
}
