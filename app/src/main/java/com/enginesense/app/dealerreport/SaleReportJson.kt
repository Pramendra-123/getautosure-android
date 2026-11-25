package com.enginesense.app.dealerreport

import com.enginesense.app.ScanResult
import com.enginesense.app.report.keyReadingRows
import com.enginesense.app.vehicle.FaultKind
import com.enginesense.app.vehicle.PrePurchaseReport
import com.enginesense.app.vehiclelookup.RcLookupResult
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/*
 * What a Dealer Saathi report publishes for its web page (getautosure.com/<slug>): the same facts
 * as the PDF, so the page and the PDF never disagree. Stored in vehicle_reports.health_summary and
 * dealer_condition; "version": 2 tells the website to render the full report rather than the
 * older, shorter one.
 */

/** health_summary v2: the older fields (score, categories, rto…) plus everything on the PDF's scan pages. */
fun saleHealthSummaryJson(result: ScanResult, report: PrePurchaseReport, summary: DealerSaleSummary, rc: RcLookupResult?, today: LocalDate): JSONObject =
    healthSummaryJson(result, rc, today).apply {
        put("version", 2)
        // The same short status line the PDF and Status picture use ("Good health", "Needs attention").
        put("statusLabel", summary.healthLabel)
        put("keyFindings", JSONArray(DealerFindings.keyFindings(result, report, today).map {
            JSONObject().put("text", it.text).put("good", it.good ?: JSONObject.NULL)
        }))
        put("faultCount", summary.faultCount)
        put("redFlagCount", summary.redFlagCount)
        put("faults", JSONArray(report.faults.filter { it.kind != FaultKind.EXTERIOR }.map { line ->
            JSONObject().apply {
                put("code", line.code ?: JSONObject.NULL)
                put("title", line.title)
                put("type", when (line.kind) {
                    FaultKind.STORED -> if (line.severity == com.enginesense.app.obd.Severity.CRITICAL) "Serious" else "Stored"
                    FaultKind.HIDDEN_PERMANENT -> "Hidden"
                    FaultKind.PENDING -> "Developing"
                    FaultKind.EXTERIOR -> "Body / cabin"
                    FaultKind.DRIVE -> "Test drive"
                })
                put("costMin", line.cost?.minInr ?: JSONObject.NULL)
                put("costMax", line.cost?.maxInr ?: JSONObject.NULL)
            }
        }))
        put("repairMin", report.repairMinInr)
        put("repairMax", report.repairMaxInr)
        put("redFlags", JSONArray(report.redFlags.map { JSONObject().put("title", it.title).put("evidence", it.evidence) }))
        put("odometer", JSONObject().apply {
            val odo = report.odometer
            put("dashboardKm", odo.dashboardKm ?: JSONObject.NULL)
            put("ecuKm", odo.ecuKm ?: JSONObject.NULL)
            put("ecuSupported", odo.ecuSupported ?: JSONObject.NULL)
            put("match", odo.match?.name ?: JSONObject.NULL)
            put("kmSinceCodesCleared", odo.kmSinceCodesCleared ?: JSONObject.NULL)
        })
        put("readings", JSONArray(keyReadingRows(result.snapshot).map {
            JSONObject().put("name", it.name).put("value", it.value).put("range", it.range).put("status", it.chip)
        }))
        put("readingAlerts", JSONArray(DealerFindings.readingAlerts(result.snapshot).map {
            JSONObject().put("name", it.name).put("value", it.valueText).put("serious", it.serious).put("action", it.action)
        }))
        put("selfTests", JSONArray(result.readiness?.monitors?.filter { it.supported }.orEmpty().map {
            JSONObject().put("name", it.name).put("ready", it.ready)
        }))
        put("papersOk", summary.papersOk ?: JSONObject.NULL)
    }

/** dealer_condition v2: the dealer's own star ratings, ticks and note — always shown on the page as dealer-declared. */
fun saleDealerConditionJson(inspection: DealerInspection, priceInr: Double? = null): JSONObject = JSONObject().apply {
    put("version", 2)
    put("ratings", JSONArray(INSPECTION_ITEMS.filter { it in inspection.ratings }.map {
        JSONObject().put("item", it).put("stars", inspection.ratings.getValue(it))
    }))
    put("extras", JSONArray(inspection.extras))
    put("note", inspection.note.trim())
    // The dealer's asking price, only when they added one — shown on the web page as set by the dealer.
    put("priceInr", priceInr?.takeIf { it > 0 }?.toLong() ?: JSONObject.NULL)
}
