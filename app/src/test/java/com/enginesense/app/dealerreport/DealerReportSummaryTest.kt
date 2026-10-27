package com.enginesense.app.dealerreport

import com.enginesense.app.ScanResult
import com.enginesense.app.obd.Severity
import com.enginesense.app.obd.VehicleIdInfo
import com.enginesense.app.vehicle.CategoryHealth
import com.enginesense.app.vehicle.CategoryState
import com.enginesense.app.vehicle.DiagnosticFinding
import com.enginesense.app.vehicle.FuelCombustionInsight
import com.enginesense.app.vehicle.HealthCategory
import com.enginesense.app.vehicle.HealthScoreResult
import com.enginesense.app.vehicle.VehicleSnapshot
import com.enginesense.app.vehicle.ai.AiCarHealthReport
import com.enginesense.app.vehicle.ai.AiVehicleContext
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric, not plain JUnit — org.json.JSONObject.put throws "not mocked" under the default Android stub jar, same reason GoogleSignInTest uses it. */
@RunWith(RobolectricTestRunner::class)
class DealerReportSummaryTest {

    private fun scanResult(
        score: Int? = 82,
        findings: List<DiagnosticFinding> = emptyList(),
        categories: List<CategoryHealth> = listOf(
            CategoryHealth(HealthCategory.ENGINE, CategoryState.GOOD, "Looks fine", score = 82),
            CategoryHealth(HealthCategory.EMISSIONS, CategoryState.UNAVAILABLE, "No data", score = null)
        ),
        dtcs: List<com.enginesense.app.vehicle.DtcReading> = emptyList(),
        readiness: com.enginesense.app.vehicle.ReadinessStatus? = null
    ) = ScanResult(
        timestampMillis = 0L,
        profileLabel = "Test",
        isDemo = false,
        snapshot = VehicleSnapshot(0L, null, emptyMap(), dtcs),
        findings = findings,
        health = HealthScoreResult(
            score = score,
            label = if (score != null) "Good" else "No data",
            categories = categories,
            fuelCombustionInsight = FuelCombustionInsight(82, CategoryState.GOOD, null, emptyList(), "test", null)
        ),
        aiReport = AiCarHealthReport("headline", "summary", emptyList(), "disclaimer"),
        aiContext = AiVehicleContext(0L, null, emptyList(), emptyList()),
        vehicleId = VehicleIdInfo(null, null, null),
        readiness = readiness,
        fuelCombustionInsight = FuelCombustionInsight(82, CategoryState.GOOD, null, emptyList(), "test", null)
    )

    @Test
    fun `carries the overall score and label through`() {
        val json = healthSummaryJson(scanResult(score = 82))
        assertEquals(82, json.getInt("score"))
        assertEquals("Good", json.getString("label"))
    }

    @Test
    fun `a null score is represented as JSON null, not a guessed number`() {
        val json = healthSummaryJson(scanResult(score = null))
        assertTrue(json.isNull("score"))
    }

    @Test
    fun `counts findings and critical findings separately`() {
        val findings = listOf(
            DiagnosticFinding("1", "A", "", Severity.CRITICAL, recommendedAction = ""),
            DiagnosticFinding("2", "B", "", Severity.MINOR, recommendedAction = "")
        )
        val json = healthSummaryJson(scanResult(findings = findings))
        assertEquals(2, json.getInt("findingsCount"))
        assertEquals(1, json.getInt("criticalCount"))
    }

    @Test
    fun `each category carries its own state, note and nullable score`() {
        val json = healthSummaryJson(scanResult())
        val categories = json.getJSONArray("categories")
        assertEquals(2, categories.length())
        val engine = categories.getJSONObject(0)
        assertEquals("ENGINE", engine.getString("category"))
        assertEquals("GOOD", engine.getString("state"))
        assertEquals(82, engine.getInt("score"))
        val emissions = categories.getJSONObject(1)
        assertEquals("UNAVAILABLE", emissions.getString("state"))
        assertTrue(emissions.isNull("score"))
    }

    @Test
    fun `no readiness data means MIL, DTC count and emissions readiness are all JSON null, not a guessed clean bill of health`() {
        val json = healthSummaryJson(scanResult(readiness = null))
        assertTrue(json.isNull("milOn"))
        assertTrue(json.isNull("dtcCount"))
        assertTrue(json.isNull("emissionsReady"))
    }

    @Test
    fun `readiness data carries MIL status, DTC count and emissions readiness through`() {
        val readiness = com.enginesense.app.vehicle.ReadinessStatus(
            milOn = true,
            dtcCount = 2,
            ignitionType = com.enginesense.app.vehicle.IgnitionType.SPARK,
            monitors = listOf(com.enginesense.app.vehicle.ReadinessMonitor("Catalyst", supported = true, ready = false))
        )
        val json = healthSummaryJson(scanResult(readiness = readiness))
        assertEquals(true, json.getBoolean("milOn"))
        assertEquals(2, json.getInt("dtcCount"))
        assertEquals(false, json.getBoolean("emissionsReady")) // one supported monitor isn't ready
    }

    @Test
    fun `fault codes list the DTCs from the scan snapshot`() {
        val dtcs = listOf(
            com.enginesense.app.vehicle.DtcReading("P0171", com.enginesense.app.vehicle.DtcSource.STORED),
            com.enginesense.app.vehicle.DtcReading("P0300", com.enginesense.app.vehicle.DtcSource.PENDING)
        )
        val json = healthSummaryJson(scanResult(dtcs = dtcs))
        val codes = json.getJSONArray("faultCodes")
        assertEquals(2, codes.length())
        assertEquals("P0171", codes.getString(0))
        assertEquals("P0300", codes.getString(1))
    }

    @Test
    fun `no DTCs means an empty fault codes array, not omitted or null`() {
        val json = healthSummaryJson(scanResult(dtcs = emptyList()))
        assertEquals(0, json.getJSONArray("faultCodes").length())
    }

    @Test
    fun `findings are summarized as title and severity only`() {
        val findings = listOf(DiagnosticFinding("1", "Battery weak", "", Severity.MODERATE, recommendedAction = ""))
        val json = healthSummaryJson(scanResult(findings = findings))
        val findingsJson: JSONArray = json.getJSONArray("findings")
        assertEquals(1, findingsJson.length())
        assertEquals("Battery weak", findingsJson.getJSONObject(0).getString("title"))
        assertEquals("MODERATE", findingsJson.getJSONObject(0).getString("severity"))
    }
}
