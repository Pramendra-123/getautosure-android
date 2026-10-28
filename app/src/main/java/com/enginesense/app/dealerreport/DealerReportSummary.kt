package com.enginesense.app.dealerreport

import com.enginesense.app.ScanResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Condenses a [ScanResult] into the JSON blob stored in `vehicle_reports.health_summary` — a
 * snapshot for the web report to render, not raw PID data (the full scan already lives in the
 * PDF/in-app detail screens for that). Deliberately a loose, JSONB-backed shape: later phases
 * (DTC codes, emissions-readiness detail) can add fields here without a new migration, since
 * nothing on the read side assumes a fixed schema beyond what it explicitly reads.
 */
fun healthSummaryJson(result: ScanResult): JSONObject = JSONObject().apply {
    put("score", result.health.score ?: JSONObject.NULL)
    put("label", result.health.label)
    put("findingsCount", result.findings.size)
    put("criticalCount", result.findings.count { it.severity == com.enginesense.app.obd.Severity.CRITICAL })
    // MIL/DTC-count/emissions-readiness come from readiness (Mode 01 PID 01) — null throughout
    // when the adapter never answered it, never defaulted to a false "all clear".
    val readiness = result.readiness
    if (readiness != null) {
        put("milOn", readiness.milOn)
        put("dtcCount", readiness.dtcCount)
        put("emissionsReady", readiness.readyForEmissionsTest)
    } else {
        put("milOn", JSONObject.NULL)
        put("dtcCount", JSONObject.NULL)
        put("emissionsReady", JSONObject.NULL)
    }
    // Permanent codes (Mode 0A) usually repeat a stored one, so they'd double-list here.
    put("faultCodes", JSONArray(result.snapshot.dtcs.filter { it.source != com.enginesense.app.vehicle.DtcSource.PERMANENT }.map { it.code }))
    put(
        "categories",
        JSONArray(
            result.health.categories.map { category ->
                JSONObject().apply {
                    put("category", category.category.name)
                    put("state", category.state.name)
                    put("note", category.note)
                    put("score", category.score ?: JSONObject.NULL)
                }
            }
        )
    )
    put(
        "findings",
        JSONArray(
            result.findings.map { finding ->
                JSONObject().apply {
                    put("title", finding.title)
                    put("severity", finding.severity.name)
                }
            }
        )
    )
}
