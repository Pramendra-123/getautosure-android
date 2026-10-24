package com.enginesense.app.vehicle.integrations

/**
 * Result of looking up a vehicle by registration number against RC/challan/insurance
 * data (the "Vehicle information/challan module" from the product spec — see the
 * earlier conversation about Surepass/Signzy/Sandbox APIs as candidate providers).
 * All fields nullable/empty by default so a placeholder result is visibly incomplete
 * rather than looking like real data.
 */
data class VehicleInfoResult(
    val registrationNumber: String,
    val isDataAvailable: Boolean,
    val ownerName: String? = null,
    val make: String? = null,
    val model: String? = null,
    val registrationDate: String? = null,
    val insuranceValid: Boolean? = null,
    val pendingChallanCount: Int? = null,
    val financierName: String? = null,
    val sourceNote: String
)

/**
 * Interface for the RC/challan/insurance verification module. No real provider is wired
 * up yet (needs a paid API key from Surepass/Signzy/Sandbox APIs or similar — a business
 * decision, not something to pick unilaterally). This is the integration point the UI
 * can call once that's chosen.
 */
interface VehicleInfoService {
    suspend fun lookup(registrationNumber: String): VehicleInfoResult
}

/**
 * Placeholder implementation: always returns a clearly-marked "not integrated" result.
 * Deliberately does not fabricate plausible-looking owner/insurance data — that would be
 * actively misleading for a vehicle-trust product.
 */
class PlaceholderVehicleInfoService : VehicleInfoService {
    override suspend fun lookup(registrationNumber: String): VehicleInfoResult {
        return VehicleInfoResult(
            registrationNumber = registrationNumber,
            isDataAvailable = false,
            sourceNote = "RC/challan/insurance lookup isn't connected yet — this is a placeholder. " +
                "Needs a provider (Surepass/Signzy/Sandbox APIs) and an API key."
        )
    }
}
