package com.enginesense.app.storage

import android.content.Context
import com.enginesense.app.vehiclelookup.RcLookupResult
import com.enginesense.app.vehiclelookup.VehicleLookupRepository
import org.json.JSONObject
import java.io.File

/** The user's own car, as entered in Add Vehicle (or confirmed from the RTO lookup). */
data class VehicleProfile(
    val brand: String?,
    val nickname: String,
    val model: String,
    val year: Int?,
    val fuelType: String?,
    val odometerKm: Int?,
    val plate: String?,
    val engineCc: Int?,
    val claimedMileageKmPerLitre: Double?,
    val rc: RcLookupResult?
)

fun vehicleProfileToJson(profile: VehicleProfile): JSONObject = JSONObject().apply {
    put("brand", profile.brand ?: JSONObject.NULL)
    put("nickname", profile.nickname)
    put("model", profile.model)
    put("year", profile.year ?: JSONObject.NULL)
    put("fuelType", profile.fuelType ?: JSONObject.NULL)
    put("odometerKm", profile.odometerKm ?: JSONObject.NULL)
    put("plate", profile.plate ?: JSONObject.NULL)
    put("engineCc", profile.engineCc ?: JSONObject.NULL)
    put("claimedMileageKmPerLitre", profile.claimedMileageKmPerLitre ?: JSONObject.NULL)
    put("rc", profile.rc?.let { VehicleLookupRepository.toResponseJson(it) } ?: JSONObject.NULL)
}

fun vehicleProfileFromJson(json: JSONObject): VehicleProfile = VehicleProfile(
    brand = json.stringOrNull("brand"),
    nickname = json.optString("nickname", ""),
    model = json.optString("model", ""),
    year = json.intOrNull("year"),
    fuelType = json.stringOrNull("fuelType"),
    odometerKm = json.intOrNull("odometerKm"),
    plate = json.stringOrNull("plate"),
    engineCc = json.intOrNull("engineCc"),
    claimedMileageKmPerLitre = if (json.isNull("claimedMileageKmPerLitre")) null else json.optDouble("claimedMileageKmPerLitre").takeUnless { it.isNaN() },
    // A record that no longer parses is dropped rather than losing the whole car with it.
    rc = json.optJSONObject("rc")?.let { runCatching { VehicleLookupRepository.parseLookupResponse(it.toString()) }.getOrNull() }
)

private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else (opt(key) as? Number)?.toInt()

/**
 * Same plain-JSON-file approach as [DealerProfileStore]. Without it the car the user added lived
 * only in memory, so every app restart showed "My Car" and reports said "Vehicle details not entered".
 */
class VehicleProfileStore(context: Context) {

    private val file = File(context.filesDir, "vehicle_profile.json")

    /** Null means no car has been added yet (or the file couldn't be read). */
    @Synchronized
    fun load(): VehicleProfile? {
        if (!file.exists()) return null
        return try {
            vehicleProfileFromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun save(profile: VehicleProfile) {
        file.writeText(vehicleProfileToJson(profile).toString())
    }
}
