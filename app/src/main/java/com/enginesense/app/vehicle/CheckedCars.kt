package com.enginesense.app.vehicle

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One car from a Used Car Check, as the buyer's home screen lists it. Kept after the app closes. */
data class CheckedCar(
    /** The scan's time — one check, updated as the buyer goes through the steps. */
    val scanMillis: Long,
    val name: String?,
    val registration: String?,
    val verdict: PurchaseVerdict,
    val redFlags: Int,
    val score: Int?,
    /** "As it stands" price after repairs, when there was one. */
    val priceLow: Double?,
    val priceHigh: Double?
) {
    fun toJson(): JSONObject = JSONObject()
        .put("scanMillis", scanMillis)
        .put("name", name ?: JSONObject.NULL)
        .put("registration", registration ?: JSONObject.NULL)
        .put("verdict", verdict.name)
        .put("redFlags", redFlags)
        .put("score", score ?: JSONObject.NULL)
        .put("priceLow", priceLow ?: JSONObject.NULL)
        .put("priceHigh", priceHigh ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject): CheckedCar? = runCatching {
            CheckedCar(
                scanMillis = o.getLong("scanMillis"),
                name = o.optString("name").takeIf { !o.isNull("name") && it.isNotBlank() },
                registration = o.optString("registration").takeIf { !o.isNull("registration") && it.isNotBlank() },
                verdict = PurchaseVerdict.valueOf(o.getString("verdict")),
                redFlags = o.optInt("redFlags"),
                score = if (o.isNull("score")) null else o.optInt("score"),
                priceLow = if (o.isNull("priceLow")) null else o.optDouble("priceLow"),
                priceHigh = if (o.isNull("priceHigh")) null else o.optDouble("priceHigh")
            )
        }.getOrNull()

        fun from(report: PrePurchaseReport, name: String?, scanMillis: Long, score: Int?): CheckedCar = CheckedCar(
            scanMillis = scanMillis,
            name = name,
            registration = report.registration,
            verdict = report.verdict,
            redFlags = report.redFlags.size,
            score = score,
            priceLow = report.value?.afterRepairsLow,
            priceHigh = report.value?.afterRepairsHigh
        )
    }
}

/** The last few checked cars, newest first, in the app's own preferences. Holds no personal data from the RTO record. */
class CheckedCarStore(context: Context) {
    private val prefs = context.getSharedPreferences("checked_cars", Context.MODE_PRIVATE)

    fun load(): List<CheckedCar> = runCatching {
        val a = JSONArray(prefs.getString(KEY, "[]"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(CheckedCar::fromJson) }
    }.getOrDefault(emptyList())

    /** Adds or updates [car] (same scan replaces the old entry) and keeps the newest [MAX]. */
    fun save(car: CheckedCar): List<CheckedCar> {
        val list = (listOf(car) + load().filter { it.scanMillis != car.scanMillis }).take(MAX)
        prefs.edit().putString(KEY, JSONArray(list.map { it.toJson() }).toString()).apply()
        return list
    }

    companion object {
        private const val KEY = "list"
        const val MAX = 5
    }
}
