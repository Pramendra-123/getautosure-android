package com.enginesense.app.dealerreport

import com.enginesense.app.ScanResult
import com.enginesense.app.vehiclelookup.RcLookupResult
import com.enginesense.app.vehiclelookup.RtoRecord
import com.enginesense.app.vehiclelookup.RtoRow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal fun rtoJson(rc: RcLookupResult, today: LocalDate): JSONObject = JSONObject().apply {
    fun rows(list: List<RtoRow>) = JSONArray(list.map { JSONObject().put("label", it.label).put("value", it.value).put("concern", it.concern) })
    put("registrationNumber", rc.registrationNumber)
    put("vehicle", rows(RtoRecord.vehicleRows(rc)))
    put(
        "validity",
        JSONArray(RtoRecord.validity(rc, today).map { JSONObject().put("label", it.label).put("value", it.display).put("status", it.status.name) })
    )
    put("ownership", rows(RtoRecord.dealerRows(rc)))
    put("chassisMatch", rc.chassisMatch ?: JSONObject.NULL)
    put("partial", rc.partial)
    put("source", RtoRecord.sourceLine(rc))
}

/**
 * Condenses a [ScanResult] into the JSON blob stored in `vehicle_reports.health_summary` — a
 * snapshot for the web report to render, not raw PID data (the full scan already lives in the
 * PDF/in-app detail screens for that). Deliberately a loose, JSONB-backed shape: later phases
 * (DTC codes, emissions-readiness detail) can add fields here without a new migration, since
 * nothing on the read side assumes a fixed schema beyond what it explicitly reads.
 */
fun healthSummaryJson(result: ScanResult, rc: RcLookupResult? = null, today: LocalDate = LocalDate.now()): JSONObject = JSONObject().apply {
    // The RTO record, already turned into labelled rows (RtoRecord — the same rows the PDFs print),
    // so the web report renders it without knowing Surepass's shape. Dealer-mode rows carry masked
    // chassis/engine numbers and never the owner's name.
    if (rc != null) put("rto", rtoJson(rc, today))
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
