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
}
