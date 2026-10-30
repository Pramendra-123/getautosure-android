package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrePurchaseAnalyzerTest {

    private fun finding(severity: Severity, title: String = "Issue") = DiagnosticFinding(
        id = title, title = title, plainLanguageSummary = "summary", severity = severity, recommendedAction = "Get it checked."
    )

    @Test
    fun `no findings and a high health score is a good buy`() {
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 95)
        assertEquals(PurchaseVerdict.GOOD_BUY, insight.verdict)
        assertTrue(insight.negotiationPoints.isEmpty())
    }

    @Test
    fun `a critical finding is always avoid, regardless of health score`() {
        val insight = PrePurchaseAnalyzer.analyze(listOf(finding(Severity.CRITICAL)), healthScore = 90)
        assertEquals(PurchaseVerdict.AVOID, insight.verdict)
    }

    @Test
    fun `a moderate finding is buy with caution`() {
        val insight = PrePurchaseAnalyzer.analyze(listOf(finding(Severity.MODERATE)), healthScore = 85)
        assertEquals(PurchaseVerdict.BUY_WITH_CAUTION, insight.verdict)
    }

    @Test
    fun `a low health score alone (no findings) is still buy with caution`() {
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 55)
        assertEquals(PurchaseVerdict.BUY_WITH_CAUTION, insight.verdict)
    }

    @Test
    fun `info findings alone do not downgrade the verdict`() {
        val insight = PrePurchaseAnalyzer.analyze(listOf(finding(Severity.INFO)), healthScore = 90)
        assertEquals(PurchaseVerdict.GOOD_BUY, insight.verdict)
        assertTrue(insight.negotiationPoints.isEmpty())
    }

    @Test
    fun `a null health score (not enough data) is buy with caution, never good buy`() {
        // A used-car check that can't actually verify condition must not default to "good
        // buy" just because nothing was flagged — "nothing flagged" and "nothing could be
        // checked" are different situations with different advice.
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = null)
        assertEquals(PurchaseVerdict.BUY_WITH_CAUTION, insight.verdict)
        assertTrue(insight.headline.contains("didn't report enough data"))
    }

    @Test
    fun `a critical finding is still avoid even with a null health score`() {
        val insight = PrePurchaseAnalyzer.analyze(listOf(finding(Severity.CRITICAL)), healthScore = null)
        assertEquals(PurchaseVerdict.AVOID, insight.verdict)
    }

    @Test
    fun `negotiation points are sorted worst-first and exclude info findings`() {
        val findings = listOf(
            finding(Severity.MINOR, "Minor thing"),
            finding(Severity.CRITICAL, "Critical thing"),
            finding(Severity.INFO, "Just info"),
            finding(Severity.MODERATE, "Moderate thing")
        )
        val insight = PrePurchaseAnalyzer.analyze(findings, healthScore = 50)

        assertEquals(listOf("Critical thing", "Moderate thing", "Minor thing"), insight.negotiationPoints.map { it.title })
    }

    @Test
    fun `recently cleared codes flag when MIL off, zero DTCs, and monitors not ready`() {
        val readiness = ReadinessStatus(
            milOn = false,
            dtcCount = 0,
            ignitionType = IgnitionType.SPARK,
            monitors = listOf(
                ReadinessMonitor("Catalyst", supported = true, ready = false),
                ReadinessMonitor("Misfire", supported = true, ready = true)
            )
        )
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90, readiness = readiness)
        assertEquals(1, insight.fraudRiskFlags.size)
        assertEquals("Codes may have been cleared recently", insight.fraudRiskFlags.first().title)
    }

    @Test
    fun `no fraud flag when MIL is on`() {
        val readiness = ReadinessStatus(
            milOn = true,
            dtcCount = 0,
            ignitionType = IgnitionType.SPARK,
            monitors = listOf(ReadinessMonitor("Catalyst", supported = true, ready = false))
        )
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90, readiness = readiness)
        assertTrue(insight.fraudRiskFlags.isEmpty())
    }

    @Test
    fun `no fraud flag when active DTCs are stored`() {
        val readiness = ReadinessStatus(
            milOn = false,
            dtcCount = 2,
            ignitionType = IgnitionType.SPARK,
            monitors = listOf(ReadinessMonitor("Catalyst", supported = true, ready = false))
        )
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90, readiness = readiness)
        assertTrue(insight.fraudRiskFlags.isEmpty())
    }

    @Test
    fun `no fraud flag when active faults are present`() {
        val readiness = ReadinessStatus(
            milOn = false,
            dtcCount = 0,
            ignitionType = IgnitionType.SPARK,
            monitors = listOf(ReadinessMonitor("Catalyst", supported = true, ready = false))
        )
        val insight = PrePurchaseAnalyzer.analyze(listOf(finding(Severity.MODERATE)), healthScore = 90, readiness = readiness)
        assertTrue(insight.fraudRiskFlags.isEmpty())
    }

    private fun snapshot(
        vararg values: Pair<PidId, Double>,
        unsupported: List<PidId> = emptyList(),
        dtcs: List<DtcReading> = emptyList()
    ) = VehicleSnapshot(
        timestampMillis = 0L,
        vehicleBrand = null,
        readings = values.associate { (pid, v) -> pid to PidReading(pid, PidStatus.VERIFIED, v, null) } +
            unsupported.associateWith { PidReading(it, PidStatus.UNSUPPORTED, null, null) },
        dtcs = dtcs
    )

    @Test
    fun `a permanent code missing from the stored list is a tampering flag and blocks good buy`() {
        val snap = snapshot(dtcs = listOf(DtcReading("P0420", DtcSource.PERMANENT)))
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 95, readiness = null, snapshot = snap)
        assertEquals(PurchaseVerdict.BUY_WITH_CAUTION, insight.verdict)
        assertEquals(1, insight.fraudRiskFlags.size)
        assertEquals("Permanent fault code still stored", insight.fraudRiskFlags.first().title)
    }

    @Test
    fun `a permanent code that is also stored is not flagged again`() {
        val snap = snapshot(dtcs = listOf(DtcReading("P0420", DtcSource.STORED), DtcReading("P0420", DtcSource.PERMANENT)))
        val insight = PrePurchaseAnalyzer.analyze(listOf(finding(Severity.MODERATE)), healthScore = 80, readiness = null, snapshot = snap)
        assertTrue(insight.fraudRiskFlags.isEmpty())
    }

    @Test
    fun `pending codes are a caution note, not a tampering flag`() {
        val snap = snapshot(dtcs = listOf(DtcReading("P0171", DtcSource.PENDING), DtcReading("P0174", DtcSource.PENDING)))
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 95, readiness = null, snapshot = snap)
        assertEquals(PurchaseVerdict.BUY_WITH_CAUTION, insight.verdict)
        assertTrue(insight.fraudRiskFlags.isEmpty())
        assertEquals(1, insight.cautionNotes.size)
        assertEquals("Developing faults not yet confirmed", insight.cautionNotes.first().title)
    }

    @Test
    fun `driving far with the warning light on is noted, a short hop is not`() {
        val far = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90, readiness = null, snapshot = snapshot(PidId.DISTANCE_WITH_MIL_ON to 1200.0))
        assertEquals(1, far.cautionNotes.size)
        assertTrue(far.cautionNotes.first().evidence.contains("1,200 km with the check-engine light on"))

        val short = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90, readiness = null, snapshot = snapshot(PidId.DISTANCE_WITH_MIL_ON to 10.0))
        assertTrue(short.cautionNotes.isEmpty())
    }

    @Test
    fun `a hot engine moments after starting is noted as pre-warmed`() {
        val snap = snapshot(PidId.COOLANT_TEMP_C to 85.0, PidId.RUN_TIME_SINCE_START to 90.0)
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90, readiness = null, snapshot = snap)
        assertEquals(1, insight.cautionNotes.size)
        assertTrue(insight.cautionNotes.first().evidence.contains("Coolant was 85°C just 1 minute after starting"))
        // A note only — it doesn't change the verdict on its own.
        assertEquals(PurchaseVerdict.GOOD_BUY, insight.verdict)
    }

    @Test
    fun `no pre-warmed note for a cold start, a long run, or missing run time`() {
        val cold = snapshot(PidId.COOLANT_TEMP_C to 40.0, PidId.RUN_TIME_SINCE_START to 90.0)
        val longRun = snapshot(PidId.COOLANT_TEMP_C to 90.0, PidId.RUN_TIME_SINCE_START to 900.0)
        val noRunTime = snapshot(PidId.COOLANT_TEMP_C to 90.0)
        listOf(cold, longRun, noRunTime).forEach { snap ->
            assertTrue(PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90, readiness = null, snapshot = snap).cautionNotes.isEmpty())
        }
    }

    private val notReadyCatalyst = ReadinessStatus(
        milOn = false, dtcCount = 0, ignitionType = IgnitionType.SPARK,
        monitors = listOf(ReadinessMonitor("Catalyst", supported = true, ready = false))
    )

    @Test
    fun `cleared-code counters name how recently codes were cleared`() {
        val snap = snapshot(
            PidId.DISTANCE_SINCE_CODES_CLEARED to 18.0,
            PidId.WARM_UPS_SINCE_CODES_CLEARED to 3.0,
            PidId.TIME_SINCE_CODES_CLEARED to 40.0
        )
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90, readiness = null, snapshot = snap)
        assertEquals(1, insight.fraudRiskFlags.size)
        val flag = insight.fraudRiskFlags.first().evidence
        assertTrue(flag.contains("18 km"))
        assertTrue(flag.contains("3 engine warm-ups"))
        assertTrue(flag.contains("40 min of running"))
    }

    @Test
    fun `a recent reset is flagged even when the warning light has come back on`() {
        // The fault returning after a reset doesn't make the reset any less worth asking about.
        val readiness = notReadyCatalyst.copy(milOn = true, dtcCount = 1)
        val insight = PrePurchaseAnalyzer.analyze(
            listOf(finding(Severity.MODERATE)), healthScore = 70, readiness = readiness,
            snapshot = snapshot(PidId.DISTANCE_SINCE_CODES_CLEARED to 25.0)
        )
        assertEquals(1, insight.fraudRiskFlags.size)
        assertTrue(insight.fraudRiskFlags.first().evidence.contains("25 km"))
    }

    @Test
    fun `counters that disagree about recency do not raise the counter flag`() {
        val snap = snapshot(PidId.DISTANCE_SINCE_CODES_CLEARED to 50.0, PidId.WARM_UPS_SINCE_CODES_CLEARED to 200.0)
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90, readiness = null, snapshot = snap)
        assertTrue(insight.fraudRiskFlags.isEmpty())
    }

    @Test
    fun `codes cleared long ago suppresses the readiness-only flag`() {
        val insight = PrePurchaseAnalyzer.analyze(
            emptyList(), healthScore = 90, readiness = notReadyCatalyst,
            snapshot = snapshot(PidId.DISTANCE_SINCE_CODES_CLEARED to 12000.0)
        )
        assertTrue(insight.fraudRiskFlags.isEmpty())
    }

    @Test
    fun `without counters the readiness fingerprint still flags`() {
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90, readiness = notReadyCatalyst, snapshot = snapshot())
        assertEquals(1, insight.fraudRiskFlags.size)
        assertEquals("Codes may have been cleared recently", insight.fraudRiskFlags.first().title)
        assertTrue(insight.fraudRiskFlags.first().evidence.endsWith("haven't finished: Catalyst."))
    }

    @Test
    fun `checks list what was clear, flagged, and not reported`() {
        val snap = snapshot(
            PidId.DISTANCE_SINCE_CODES_CLEARED to 18.0,
            PidId.DISTANCE_WITH_MIL_ON to 0.0,
            dtcs = listOf(DtcReading("P0171", DtcSource.PENDING))
        )
        val checks = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90, readiness = null, snapshot = snap)
            .checks.associate { it.label to it.outcome }

        assertEquals(CheckOutcome.CLEAR, checks["Stored fault codes"])
        assertEquals(CheckOutcome.CLEAR, checks["Hidden permanent codes"])
        assertEquals(CheckOutcome.WORTH_ASKING, checks["Developing (pending) faults"])
        assertEquals(CheckOutcome.FLAGGED, checks["Codes cleared before the sale"])
        assertEquals(CheckOutcome.CLEAR, checks["Driven with warning light on"])
        // No coolant/run-time reading: never shown as a pass.
        assertEquals(CheckOutcome.NOT_AVAILABLE, checks["Engine pre-warmed"])
    }

    @Test
    fun `no checks without a scan snapshot`() {
        assertTrue(PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90).checks.isEmpty())
    }

    @Test
    fun `ECU odometer is passed through when the car reports it`() {
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90, readiness = null, snapshot = snapshot(PidId.ODOMETER to 84210.5))
        assertEquals(84210.5, insight.ecuOdometerKm!!, 0.01)
        assertEquals(true, insight.odometerSupported)
    }

    @Test
    fun `odometer support is false when unsupported and unknown without scan data`() {
        val unsupported = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90, readiness = null, snapshot = snapshot(unsupported = listOf(PidId.ODOMETER)))
        assertEquals(false, unsupported.odometerSupported)
        assertEquals(null, unsupported.ecuOdometerKm)
        assertEquals(null, PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90).odometerSupported)
    }

    @Test
    fun `odometer comparison tolerates small drift but flags a real gap`() {
        assertEquals(OdometerMatch.MATCHES, PrePurchaseAnalyzer.compareOdometer(84210.0, 84000.0))
        // 2% of 100,000 km = 2,000 km tolerance on high-km cars.
        assertEquals(OdometerMatch.MATCHES, PrePurchaseAnalyzer.compareOdometer(100000.0, 98500.0))
        assertEquals(OdometerMatch.MISMATCH, PrePurchaseAnalyzer.compareOdometer(84210.0, 42000.0))
        assertEquals(OdometerMatch.MISMATCH, PrePurchaseAnalyzer.compareOdometer(3000.0, 2000.0))
    }

    @Test
    fun `no fraud flag when all supported monitors are ready`() {
        val readiness = ReadinessStatus(
            milOn = false,
            dtcCount = 0,
            ignitionType = IgnitionType.SPARK,
            monitors = listOf(ReadinessMonitor("Catalyst", supported = true, ready = true))
        )
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 90, readiness = readiness)
        assertTrue(insight.fraudRiskFlags.isEmpty())
    }
}
