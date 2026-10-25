package com.enginesense.app.vehicle.pricing

import com.enginesense.app.obd.Severity
import com.enginesense.app.vehicle.DiagnosticFinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefurbishmentEstimatorTest {

    private fun finding(title: String) = DiagnosticFinding(
        id = title, title = title, plainLanguageSummary = "test", severity = Severity.MODERATE,
        recommendedAction = "test"
    )

    @Test
    fun `no findings means a zero estimate, not a guessed one`() {
        val estimate = RefurbishmentEstimator.estimate(emptyList())
        assertTrue(estimate.lineItems.isEmpty())
        assertEquals(0, estimate.totalMinInr)
        assertEquals(0, estimate.totalMaxInr)
    }

    @Test
    fun `a finding matching a known repair category produces one indicative line item`() {
        val estimate = RefurbishmentEstimator.estimate(listOf(finding("Battery replacement")))
        assertEquals(1, estimate.lineItems.size)
        assertEquals("Battery replacement", estimate.lineItems.first().category)
        assertEquals(CostProvenance.INDICATIVE, estimate.lineItems.first().provenance)
        assertTrue(estimate.totalMinInr > 0)
        assertTrue(estimate.totalMaxInr >= estimate.totalMinInr)
    }

    @Test
    fun `a finding with no matching repair category is skipped, not guessed`() {
        val estimate = RefurbishmentEstimator.estimate(listOf(finding("Mystery rattling noise from unidentified source")))
        assertTrue(estimate.lineItems.isEmpty())
    }

    @Test
    fun `duplicate categories across findings are not double-counted`() {
        val estimate = RefurbishmentEstimator.estimate(
            listOf(finding("Battery replacement"), finding("battery issue"))
        )
        assertEquals(1, estimate.lineItems.size)
    }

    @Test
    fun `totals are the sum of all distinct line items`() {
        val estimate = RefurbishmentEstimator.estimate(
            listOf(finding("Battery replacement"), finding("Clutch replacement"))
        )
        assertEquals(2, estimate.lineItems.size)
        assertEquals(estimate.lineItems.sumOf { it.minInr }, estimate.totalMinInr)
        assertEquals(estimate.lineItems.sumOf { it.maxInr }, estimate.totalMaxInr)
    }

    @Test
    fun `a confirmed quote overrides the indicative range for the same category`() {
        val estimate = RefurbishmentEstimator.estimate(
            findings = listOf(finding("Battery replacement")),
            confirmedQuotes = mapOf("Battery replacement" to 6_500)
        )
        assertEquals(1, estimate.lineItems.size)
        val item = estimate.lineItems.first()
        assertEquals(CostProvenance.CONFIRMED, item.provenance)
        assertEquals(6_500, item.minInr)
        assertEquals(6_500, item.maxInr)
    }

    @Test
    fun `a confirmed quote for a category with no matching finding is still included`() {
        val estimate = RefurbishmentEstimator.estimate(
            findings = emptyList(),
            confirmedQuotes = mapOf("Clutch replacement" to 9_000)
        )
        assertEquals(1, estimate.lineItems.size)
        assertEquals(CostProvenance.CONFIRMED, estimate.lineItems.first().provenance)
    }

    @Test
    fun `confirmed and indicative line items for different categories coexist, each with correct provenance`() {
        val estimate = RefurbishmentEstimator.estimate(
            findings = listOf(finding("Battery replacement")),
            confirmedQuotes = mapOf("Clutch replacement" to 9_000)
        )
        assertEquals(2, estimate.lineItems.size)
        val byCategory = estimate.lineItems.associateBy { it.category }
        assertEquals(CostProvenance.INDICATIVE, byCategory.getValue("Battery replacement").provenance)
        assertEquals(CostProvenance.CONFIRMED, byCategory.getValue("Clutch replacement").provenance)
    }

    @Test
    fun `totals include confirmed quotes exactly, not a range`() {
        val estimate = RefurbishmentEstimator.estimate(
            findings = emptyList(),
            confirmedQuotes = mapOf("Clutch replacement" to 9_000)
        )
        assertEquals(9_000, estimate.totalMinInr)
        assertEquals(9_000, estimate.totalMaxInr)
    }
}
