package com.enginesense.app.storage

import android.content.Context
import com.enginesense.app.owner.EntrySource
import com.enginesense.app.owner.ExpenseCategory
import com.enginesense.app.owner.ExpenseEntry
import com.enginesense.app.owner.FuelEntry
import com.enginesense.app.owner.OwnerCarData
import com.enginesense.app.owner.RenewalKind
import com.enginesense.app.owner.ServiceEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One JSON file per car under filesDir/owner — same plain-file approach as [ScanHistoryStore]. */
class OwnerDataStore(context: Context) {

    private val dir = File(context.filesDir, "owner").apply { mkdirs() }

    private fun fileFor(carId: String) = File(dir, "$carId.json")

    /** Never throws — a missing or corrupt file is an empty record. */
    @Synchronized
    fun load(carId: String): OwnerCarData {
        val file = fileFor(carId)
        if (!file.exists()) return OwnerCarData()
        return try {
            fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            OwnerCarData()
        }
    }

    @Synchronized
    fun save(carId: String, data: OwnerCarData) {
        fileFor(carId).writeText(toJson(data).toString())
    }

    @Synchronized
    fun delete(carId: String) {
        fileFor(carId).delete()
    }

    private fun toJson(d: OwnerCarData): JSONObject = JSONObject().apply {
        put("renewalDates", JSONObject().apply { d.renewalDates.forEach { (k, v) -> put(k.name, v) } })
        put("remindersOn", d.remindersOn)
        put("lastServiceQuestionSkipped", d.lastServiceQuestionSkipped)
        put("serviceIntervalKm", d.serviceIntervalKm)
        put("serviceIntervalMonths", d.serviceIntervalMonths)
        put("services", JSONArray().apply {
            d.services.forEach { s ->
                put(JSONObject().put("id", s.id).put("date", s.date).put("odometerKm", s.odometerKm ?: JSONObject.NULL)
                    .put("work", s.work).put("costInr", s.costInr ?: JSONObject.NULL))
            }
        })
        put("fuel", JSONArray().apply {
            d.fuel.forEach { f ->
                put(JSONObject().put("id", f.id).put("date", f.date).put("litres", f.litres ?: JSONObject.NULL)
                    .put("amountInr", f.amountInr).put("odometerKm", f.odometerKm ?: JSONObject.NULL).put("fullTank", f.fullTank).put("source", f.source.name))
            }
        })
        put("expenses", JSONArray().apply {
            d.expenses.forEach { e ->
                put(JSONObject().put("id", e.id).put("date", e.date).put("category", e.category.name)
                    .put("amountInr", e.amountInr).put("note", e.note).put("source", e.source.name))
            }
        })
        put("tankLitres", d.tankLitres ?: JSONObject.NULL)
        put("handledFills", JSONArray().apply { d.handledFills.forEach { put(it) } })
        put("monthlyKm", d.monthlyKm ?: JSONObject.NULL)
        put("capturedKeys", JSONArray().apply { d.capturedKeys.forEach { put(it) } })
    }

    private fun fromJson(j: JSONObject): OwnerCarData {
        val dates = j.optJSONObject("renewalDates")?.let { o ->
            o.keys().asSequence().mapNotNull { k -> runCatching { RenewalKind.valueOf(k) to o.getString(k) }.getOrNull() }.toMap()
        } ?: emptyMap()
        return OwnerCarData(
            renewalDates = dates,
            remindersOn = j.optBoolean("remindersOn", true),
            lastServiceQuestionSkipped = j.optBoolean("lastServiceQuestionSkipped", false),
            serviceIntervalKm = j.optInt("serviceIntervalKm", 10_000),
            serviceIntervalMonths = j.optInt("serviceIntervalMonths", 12),
            services = j.optJSONArray("services").objects().mapNotNull { o ->
                runCatching { ServiceEntry(o.getLong("id"), o.getString("date"), o.intOrNull("odometerKm"), o.optString("work"), o.intOrNull("costInr")) }.getOrNull()
            },
            fuel = j.optJSONArray("fuel").objects().mapNotNull { o ->
                runCatching {
                    FuelEntry(o.getLong("id"), o.getString("date"), o.doubleOrNull("litres"), o.getInt("amountInr"), o.intOrNull("odometerKm"), o.optBoolean("fullTank", true), o.source())
                }.getOrNull()
            },
            expenses = j.optJSONArray("expenses").objects().mapNotNull { o ->
                runCatching {
                    ExpenseEntry(o.getLong("id"), o.getString("date"), ExpenseCategory.valueOf(o.getString("category")), o.getInt("amountInr"), o.optString("note"), o.source())
                }.getOrNull()
            },
            tankLitres = j.intOrNull("tankLitres"),
            handledFills = j.optJSONArray("handledFills")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optLong(i).takeIf { it > 0 } } } ?: emptyList(),
            monthlyKm = j.intOrNull("monthlyKm"),
            capturedKeys = j.optJSONArray("capturedKeys")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf { it.isNotEmpty() } } } ?: emptyList()
        )
    }

    private fun JSONObject.source(): EntrySource = runCatching { EntrySource.valueOf(optString("source")) }.getOrDefault(EntrySource.MANUAL)
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else (opt(key) as? Number)?.toInt()
    private fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key)) null else (opt(key) as? Number)?.toDouble()
}
