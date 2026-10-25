package com.enginesense.app.vehicle.pricing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DealerPricingInputsTest {

    @Test
    fun `parseNonNegativeAmount rejects negative amounts and defaults blank or invalid input to zero`() {
        assertEquals(5_000.0, parseNonNegativeAmount("5000"), 0.001)
        assertEquals(0.0, parseNonNegativeAmount("-5000"), 0.001)
        assertEquals(0.0, parseNonNegativeAmount(""), 0.001)
        assertEquals(0.0, parseNonNegativeAmount("not a number"), 0.001)
    }

    @Test
    fun `findingsForSelectedCategories produces one finding per category using the category name as title`() {
        val findings = findingsForSelectedCategories(listOf("Battery replacement", "Clutch replacement"))
        assertEquals(2, findings.size)
        assertEquals(setOf("Battery replacement", "Clutch replacement"), findings.map { it.title }.toSet())
    }

    @Test
    fun `findings built this way correctly route through RefurbishmentEstimator's existing matching`() {
        val findings = findingsForSelectedCategories(listOf("Battery replacement"))
        val estimate = RefurbishmentEstimator.estimate(findings)
        assertEquals(1, estimate.lineItems.size)
        assertEquals("Battery replacement", estimate.lineItems.first().category)
        assertEquals(CostProvenance.INDICATIVE, estimate.lineItems.first().provenance)
    }

    @Test
    fun `parseConfirmedQuotes keeps only the entries that parse to a positive integer`() {
        val quotes = parseConfirmedQuotes(mapOf("Battery replacement" to "6500", "Clutch replacement" to "", "AC compressor / gas top-up" to "not a number"))
        assertEquals(mapOf("Battery replacement" to 6500), quotes)
    }

    @Test
    fun `negative or zero confirmed quotes are rejected, not accepted as a negative refurbishment cost`() {
        assertTrue(parseConfirmedQuotes(mapOf("Battery replacement" to "-5000")).isEmpty())
        assertTrue(parseConfirmedQuotes(mapOf("Battery replacement" to "0")).isEmpty())
    }

    @Test
    fun `a confirmed quote parsed this way produces a CONFIRMED line item end to end`() {
        val findings = findingsForSelectedCategories(listOf("Battery replacement"))
        val quotes = parseConfirmedQuotes(mapOf("Battery replacement" to "6500"))
        val estimate = RefurbishmentEstimator.estimate(findings, quotes)
        assertEquals(CostProvenance.CONFIRMED, estimate.lineItems.first().provenance)
        assertEquals(6_500, estimate.lineItems.first().minInr)
    }

    @Test
    fun `a confirmed quote for a category not among the selected findings is not applied`() {
        // Simulates a screen filtering quote inputs to only currently-selected categories before calling parseConfirmedQuotes.
        val allQuoteInputs = mapOf("Battery replacement" to "6500", "Clutch replacement" to "9000")
        val onlySelected = allQuoteInputs.filterKeys { it == "Battery replacement" }
        val findings = findingsForSelectedCategories(listOf("Battery replacement"))
        val estimate = RefurbishmentEstimator.estimate(findings, parseConfirmedQuotes(onlySelected))
        assertEquals(1, estimate.lineItems.size)
        assertEquals("Battery replacement", estimate.lineItems.first().category)
    }
}
