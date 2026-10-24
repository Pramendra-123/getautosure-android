package com.enginesense.app.vehicle.ai

import com.enginesense.app.obd.VehicleIdInfo
import com.enginesense.app.vehicle.DiagnosticFinding
import com.enginesense.app.vehicle.ReadinessStatus
import com.enginesense.app.vehicle.VehicleSnapshot
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the AI layer is allowed to know about a vehicle. This is deliberately a
 * closed, serializable contract — the AI never sees raw ECU bytes, never sees the
 * transport, and never gets asked about a reading that isn't in here. If a value isn't
 * in this contract, the AI must say so rather than guess, because [DiagnosticRulesEngine]
 * already ran and this *is* its output, not a preprocessing step the AI can second-guess.
 */
data class AiVehicleContext(
    val generatedAtMillis: Long,
    val vehicleBrand: String?,
    val signals: List<AiSignal>,
    val findings: List<AiFinding>,
    /**
     * False only when no scan has ever been run — distinct from a real scan that found
     * nothing. Defaults true so every existing caller (which always represents a real,
     * if sometimes empty, scan) is unaffected. The AI Mechanic chat needs this distinction
     * to answer honestly: "nothing flagged" and "nothing has been checked" are different
     * situations and warrant different advice (see RuleBasedAiMechanicChatService).
     */
    val hasScan: Boolean = true,
    /** VIN/ECU name from Mode 09 — null if the scan didn't include it or the ECU didn't answer. Never guessed. */
    val vin: String? = null,
    val ecuName: String? = null,
    /** Mode 01 PID 01 readiness — both null together if no readiness data was read this scan. */
    val milOn: Boolean? = null,
    val readyForEmissionsTest: Boolean? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("generatedAtMillis", generatedAtMillis)
        put("vehicleBrand", vehicleBrand ?: JSONObject.NULL)
        put("signals", JSONArray(signals.map { it.toJson() }))
        put("findings", JSONArray(findings.map { it.toJson() }))
        put("hasScan", hasScan)
        put("vin", vin ?: JSONObject.NULL)
        put("ecuName", ecuName ?: JSONObject.NULL)
        put("milOn", milOn ?: JSONObject.NULL)
        put("readyForEmissionsTest", readyForEmissionsTest ?: JSONObject.NULL)
    }

    companion object {
        /** No scan has been run at all — the general-knowledge-only state. */
        val NO_SCAN = AiVehicleContext(0L, null, emptyList(), emptyList(), hasScan = false)

        /**
         * @param vehicleId Mode 09 result, when the scan read it — omit for scans that don't.
         * @param readiness Mode 01 PID 01 result, when the scan read it — omit for scans that don't.
         * Both optional and default to null so existing callers are unaffected; only
         * [com.enginesense.app.GetAutoSureViewModel] currently has this data to pass.
         */
        fun from(
            snapshot: VehicleSnapshot,
            findings: List<DiagnosticFinding>,
            vehicleId: VehicleIdInfo? = null,
            readiness: ReadinessStatus? = null
        ): AiVehicleContext {
            val signals = snapshot.readings.values.map { reading ->
                AiSignal(
                    name = reading.pid.displayName,
                    unit = reading.pid.unit,
                    status = reading.status.name,
                    value = reading.value
                )
            }
            val aiFindings = findings.map { f ->
                AiFinding(
                    id = f.id,
                    title = f.title,
                    summary = f.plainLanguageSummary,
                    severity = f.severity.name,
                    recommendedAction = f.recommendedAction,
                    relatedDtcCodes = f.relatedDtcCodes
                )
            }
            return AiVehicleContext(
                generatedAtMillis = snapshot.timestampMillis,
                vehicleBrand = snapshot.vehicleBrand,
                signals = signals,
                findings = aiFindings,
                vin = vehicleId?.vin,
                ecuName = vehicleId?.ecuName,
                milOn = readiness?.milOn,
                readyForEmissionsTest = readiness?.readyForEmissionsTest
            )
        }
    }
}

/** One validated sensor reading, with its status so the AI knows VERIFIED from UNSUPPORTED. */
data class AiSignal(
    val name: String,
    val unit: String,
    val status: String,
    val value: Double?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("unit", unit)
        put("status", status)
        put("value", value ?: JSONObject.NULL)
    }
}

/** One deterministic finding from the rules engine — the AI explains these, it does not generate new ones. */
data class AiFinding(
    val id: String,
    val title: String,
    val summary: String,
    val severity: String,
    val recommendedAction: String,
    val relatedDtcCodes: List<String>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("summary", summary)
        put("severity", severity)
        put("recommendedAction", recommendedAction)
        put("relatedDtcCodes", JSONArray(relatedDtcCodes))
    }
}
