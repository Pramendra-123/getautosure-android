package com.enginesense.app.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One car in My Garage. [vin] is learned from the first scan of it, and used to tell cars apart after that. */
data class GarageCar(
    val id: String,
    val profile: VehicleProfile,
    val vin: String? = null,
    val addedMillis: Long = System.currentTimeMillis()
) {
    val title: String
        get() = profile.nickname.ifBlank { listOfNotNull(profile.brand, profile.model.ifBlank { null }).joinToString(" ").ifBlank { "My car" } }
}

/** Which car was picked from when — trips recorded by the scanner belong to the car picked at the time. */
data class ActiveSince(val sinceMillis: Long, val carId: String)

data class GarageState(
    val cars: List<GarageCar>,
    val activeId: String?,
    /** Scan timestamp → car id. Scans missing here are from before multiple cars, so they belong to the first car. */
    val scanCars: Map<Long, String>,
    val activeLog: List<ActiveSince>
) {
    val active: GarageCar? get() = cars.firstOrNull { it.id == activeId } ?: cars.firstOrNull()

    fun carOfScan(timestampMillis: Long): String? = scanCars[timestampMillis] ?: cars.firstOrNull()?.id

    /** The car picked at [millis]; before any switch was logged, the first car. */
    fun carAt(millis: Long): String? =
        activeLog.filter { it.sinceMillis <= millis }.maxByOrNull { it.sinceMillis }?.carId ?: cars.firstOrNull()?.id

    companion object {
        val EMPTY = GarageState(emptyList(), null, emptyMap(), emptyList())
    }
}

/**
 * Every car the user owns, in one JSON file (same plain-file approach as [ScanHistoryStore]).
 * Replaces the single-car vehicle_profile.json: on first load that file becomes the first car,
 * so nobody loses the car they already added.
 */
class GarageStore(private val context: Context) {

    private val file = File(context.filesDir, "garage.json")

    @Synchronized
    fun load(): GarageState {
        if (!file.exists()) return migrateSingleCar()
        return try {
            fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            GarageState.EMPTY
        }
    }

    @Synchronized
    fun save(state: GarageState) {
        file.writeText(toJson(state).toString())
    }

    private fun migrateSingleCar(): GarageState {
        val old = VehicleProfileStore(context).load() ?: return GarageState.EMPTY
        val car = GarageCar(id = newCarId(), profile = old)
        return GarageState(listOf(car), car.id, emptyMap(), emptyList()).also { save(it) }
    }

    private fun toJson(state: GarageState): JSONObject = JSONObject().apply {
        put("activeId", state.activeId ?: JSONObject.NULL)
        put("cars", JSONArray().apply {
            state.cars.forEach { car ->
                put(JSONObject().apply {
                    put("id", car.id)
                    put("vin", car.vin ?: JSONObject.NULL)
                    put("addedMillis", car.addedMillis)
                    put("profile", vehicleProfileToJson(car.profile))
                })
            }
        })
        put("scanCars", JSONObject().apply { state.scanCars.forEach { (ts, id) -> put(ts.toString(), id) } })
        put("activeLog", JSONArray().apply {
            state.activeLog.forEach { put(JSONObject().put("since", it.sinceMillis).put("id", it.carId)) }
        })
    }

    private fun fromJson(json: JSONObject): GarageState {
        val carsJson = json.optJSONArray("cars") ?: JSONArray()
        val cars = (0 until carsJson.length()).mapNotNull { i ->
            runCatching {
                val c = carsJson.getJSONObject(i)
                GarageCar(
                    id = c.getString("id"),
                    profile = vehicleProfileFromJson(c.getJSONObject("profile")),
                    vin = if (c.isNull("vin")) null else c.optString("vin").takeIf { it.isNotBlank() },
                    addedMillis = c.optLong("addedMillis", 0L)
                )
            }.getOrNull()
        }
        val scanCarsJson = json.optJSONObject("scanCars") ?: JSONObject()
        val scanCars = scanCarsJson.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { it to scanCarsJson.getString(k) } }.toMap()
        val logJson = json.optJSONArray("activeLog") ?: JSONArray()
        val log = (0 until logJson.length()).mapNotNull { i ->
            runCatching { logJson.getJSONObject(i).let { ActiveSince(it.getLong("since"), it.getString("id")) } }.getOrNull()
        }
        return GarageState(cars, if (json.isNull("activeId")) null else json.optString("activeId"), scanCars, log)
    }

    companion object {
        fun newCarId(): String = "car-" + System.currentTimeMillis().toString(36)
    }
}
