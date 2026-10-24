package com.enginesense.app.storage

import android.content.Context
import com.enginesense.app.obd.VehicleIdInfo
import com.enginesense.app.vehicle.DtcReading
import com.enginesense.app.vehicle.DtcSource
import com.enginesense.app.vehicle.IgnitionType
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.PidReading
import com.enginesense.app.vehicle.PidStatus
import com.enginesense.app.vehicle.ReadinessMonitor
import com.enginesense.app.vehicle.ReadinessStatus
import com.enginesense.app.vehicle.VehicleSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One real scan's raw inputs — everything [com.enginesense.app.GetAutoSureViewModel] needs
 * to replay through [com.enginesense.app.GetAutoSureViewModel.computeScanResult] and get back
 * the exact same findings/health/AI report a live scan produces. Deliberately NOT the derived
 * [com.enginesense.app.ScanResult] itself — persisting only the raw snapshot means a scan from
 * last month reflects today's scoring logic when you look at it, not whatever logic existed
 * the day it was taken, and it's a lot less to serialize.
 */
data class PersistedScan(
    val timestampMillis: Long,
    val profileLabel: String,
    val isDemo: Boolean,
    val vehicleBrand: String?,
    val readings: Map<PidId, PidReading>,
    val dtcs: List<DtcReading>,
    val vehicleId: VehicleIdInfo,
    val readiness: ReadinessStatus?
)

/**
 * Plain-file local persistence for scan history — no Room/SQLite. At this data scale (a
 * bounded list of scan records for one device, no joins or indexed queries needed) a JSON
 * file via the `org.json` classes already bundled in Android is simpler and has zero new
 * build dependencies, versus pulling in Room + an annotation processor for what would just
 * be a single blob column anyway. Revisit if this ever needs real querying.
 *
 * [scanToMap]/[scanFromMap] (the actual PersistedScan <-> data shape) are plain Kotlin and
 * unit-tested directly; this class only bridges that plain `Map<String, Any?>` shape to
 * `org.json`'s types for the file itself. That split exists because Android's bundled
 * `org.json` classes are a "not mocked" stub under local JVM unit tests — they only work on
 * a real device/emulator — so the actual serialization logic has to live somewhere that
 * doesn't touch them.
 */
class ScanHistoryStore(context: Context) {

    private val file = File(context.filesDir, "scan_history.json")

    /** Newest first, matching [com.enginesense.app.GetAutoSureViewModel.history]'s existing order. Never throws — a corrupt or missing file just means no history yet. */
    @Synchronized
    fun load(): List<PersistedScan> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                runCatching { scanFromMap(jsonObjectToMap(array.getJSONObject(i))) }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(scansNewestFirst: List<PersistedScan>) {
        val array = JSONArray()
        scansNewestFirst.forEach { array.put(mapToJsonObject(scanToMap(it))) }
        file.writeText(array.toString())
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject =
        JSONObject().apply { map.forEach { (k, v) -> put(k, toJsonValue(v)) } }

    private fun toJsonValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> mapToJsonObject(@Suppress("UNCHECKED_CAST") (value as Map<String, Any?>))
        is List<*> -> JSONArray().apply { value.forEach { put(toJsonValue(it)) } }
        else -> value
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key -> map[key] = fromJsonValue(json.get(key)) }
        return map
    }

    private fun fromJsonValue(value: Any?): Any? = when {
        value == null || value == JSONObject.NULL -> null
        value is JSONObject -> jsonObjectToMap(value)
        value is JSONArray -> (0 until value.length()).map { fromJsonValue(value.get(it)) }
        else -> value
    }
}

/**
 * [PersistedScan] as a plain Kotlin tree (String/Number/Boolean/List/Map/null) — no
 * `org.json` dependency, so this round-trip is directly unit-testable on a local JVM.
 */
internal fun scanToMap(scan: PersistedScan): Map<String, Any?> = mapOf(
    "timestampMillis" to scan.timestampMillis,
    "profileLabel" to scan.profileLabel,
    "isDemo" to scan.isDemo,
    "vehicleBrand" to scan.vehicleBrand,
    "readings" to scan.readings.values.map { reading ->
        mapOf(
            "pid" to reading.pid.name,
            "status" to reading.status.name,
            "value" to reading.value,
            "rawBytes" to reading.rawBytes
        )
    },
    "dtcs" to scan.dtcs.map { mapOf("code" to it.code, "source" to it.source.name) },
    "vehicleId" to mapOf(
        "vin" to scan.vehicleId.vin,
        "ecuName" to scan.vehicleId.ecuName,
        "calibrationId" to scan.vehicleId.calibrationId
    ),
    "readiness" to scan.readiness?.let { r ->
        mapOf(
            "milOn" to r.milOn,
            "dtcCount" to r.dtcCount,
            "ignitionType" to r.ignitionType.name,
            "monitors" to r.monitors.map { mapOf("name" to it.name, "supported" to it.supported, "ready" to it.ready) }
        )
    }
)

internal fun scanFromMap(map: Map<String, Any?>): PersistedScan {
    @Suppress("UNCHECKED_CAST")
    val readingsList = map["readings"] as List<Map<String, Any?>>
    val readings = readingsList.associate { r ->
        val pid = PidId.valueOf(r["pid"] as String)
        pid to PidReading(
            pid = pid,
            status = PidStatus.valueOf(r["status"] as String),
            value = (r["value"] as? Number)?.toDouble(),
            rawBytes = (r["rawBytes"] as? List<*>)?.map { (it as Number).toInt() }
        )
    }

    @Suppress("UNCHECKED_CAST")
    val dtcsList = map["dtcs"] as List<Map<String, Any?>>
    val dtcs = dtcsList.map { d -> DtcReading(d["code"] as String, DtcSource.valueOf(d["source"] as String)) }

    @Suppress("UNCHECKED_CAST")
    val vehicleIdMap = map["vehicleId"] as Map<String, Any?>
    val vehicleId = VehicleIdInfo(
        vin = vehicleIdMap["vin"] as String?,
        ecuName = vehicleIdMap["ecuName"] as String?,
        calibrationId = vehicleIdMap["calibrationId"] as String?
    )

    @Suppress("UNCHECKED_CAST")
    val readiness = (map["readiness"] as Map<String, Any?>?)?.let { r ->
        @Suppress("UNCHECKED_CAST")
        val monitorsList = r["monitors"] as List<Map<String, Any?>>
        val monitors = monitorsList.map { m -> ReadinessMonitor(m["name"] as String, m["supported"] as Boolean, m["ready"] as Boolean) }
        ReadinessStatus(
            milOn = r["milOn"] as Boolean,
            dtcCount = (r["dtcCount"] as Number).toInt(),
            ignitionType = IgnitionType.valueOf(r["ignitionType"] as String),
            monitors = monitors
        )
    }

    return PersistedScan(
        timestampMillis = (map["timestampMillis"] as Number).toLong(),
        profileLabel = map["profileLabel"] as String,
        isDemo = map["isDemo"] as Boolean,
        vehicleBrand = map["vehicleBrand"] as String?,
        readings = readings,
        dtcs = dtcs,
        vehicleId = vehicleId,
        readiness = readiness
    )
}

fun PersistedScan.toSnapshot(): VehicleSnapshot = VehicleSnapshot(timestampMillis, vehicleBrand, readings, dtcs)

fun com.enginesense.app.ScanResult.toPersistedScan(): PersistedScan = PersistedScan(
    timestampMillis = timestampMillis,
    profileLabel = profileLabel,
    isDemo = isDemo,
    vehicleBrand = snapshot.vehicleBrand,
    readings = snapshot.readings,
    dtcs = snapshot.dtcs,
    vehicleId = vehicleId,
    readiness = readiness
)
