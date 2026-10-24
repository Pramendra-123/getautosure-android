package com.enginesense.app.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One completed trip's mileage result, kept to build a baseline for detecting a real drop later. */
data class PersistedTripMileage(
    val timestampMillis: Long,
    val distanceKm: Double,
    val fuelLitres: Double,
    val averageKmPerLitre: Double
)

/** Same plain-JSON-file approach as [ScanHistoryStore], for the same reasons — see its class doc. */
class TripMileageStore(context: Context) {

    private val file = File(context.filesDir, "trip_mileage_history.json")

    /** Newest first. Never throws — a corrupt or missing file just means no trip history yet. */
    @Synchronized
    fun load(): List<PersistedTripMileage> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                runCatching { tripFromJson(array.getJSONObject(i)) }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(tripsNewestFirst: List<PersistedTripMileage>) {
        val array = JSONArray()
        tripsNewestFirst.forEach { array.put(tripToJson(it)) }
        file.writeText(array.toString())
    }

    private fun tripToJson(trip: PersistedTripMileage): JSONObject = JSONObject().apply {
        put("timestampMillis", trip.timestampMillis)
        put("distanceKm", trip.distanceKm)
        put("fuelLitres", trip.fuelLitres)
        put("averageKmPerLitre", trip.averageKmPerLitre)
    }

    private fun tripFromJson(json: JSONObject): PersistedTripMileage = PersistedTripMileage(
        timestampMillis = json.getLong("timestampMillis"),
        distanceKm = json.getDouble("distanceKm"),
        fuelLitres = json.getDouble("fuelLitres"),
        averageKmPerLitre = json.getDouble("averageKmPerLitre")
    )
}
