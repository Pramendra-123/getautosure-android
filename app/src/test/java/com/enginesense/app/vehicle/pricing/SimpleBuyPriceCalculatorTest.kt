package com.enginesense.app.vehicle.pricing

import com.enginesense.app.vehicle.ai.RepairCostReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleBuyPriceCalculatorTest {

    private fun repair(category: String) = RepairCostReference.entries.first { it.category == category }

    @Test
    fun `total refurbishment sums the midpoint of each selected repair plus the manual addition`() {
        // Battery replacement 4,000-9,000 (mid 6,500); Clutch replacement 6,000-18,000 (mid 12,000).
        val total = SimpleBuyPriceCalculator.totalRefurbishmentInr(
            listOf(repair("Battery replacement"), repair("Clutch replacement")),
            manualAdditionalCostInr = 2_000.0
        )
        assertEquals(20_500.0, total, 0.01)
    }

    @Test
    fun `no selected repairs and no manual cost means zero refurbishment, not a guess`() {
        assertEquals(0.0, SimpleBuyPriceCalculator.totalRefurbishmentInr(emptyList(), 0.0), 0.01)
    }

    @Test
    fun `buy price subtracts refurbishment and the margin amount from the selling price`() {
        val result = SimpleBuyPriceCalculator.calculate(sellingPriceInr = 600_000.0, refurbishmentInr = 20_000.0, marginPercent = 10)
        // margin = 60,000; raw = 600,000 - 20,000 - 60,000 = 520,000
        assertEquals(520_000.0, result.rawBuyPriceInr, 0.01)
        assertEquals(520_000.0, result.buyPriceInr, 0.01)
        assertEquals(60_000.0, result.marginAmountInr, 0.01)
        assertTrue(result.isViable)
    }

    @Test
    fun `margin is a percentage of selling price, not a flat amount`() {
        val higherSp = SimpleBuyPriceCalculator.calculate(sellingPriceInr = 1_000_000.0, refurbishmentInr = 0.0, marginPercent = 10)
        assertEquals(100_000.0, higherSp.marginAmountInr, 0.01)
    }

    @Test
    fun `increasing margin percent reduces the buy price`() {
        val lowMargin = SimpleBuyPriceCalculator.calculate(600_000.0, 0.0, marginPercent = 5)
        val highMargin = SimpleBuyPriceCalculator.calculate(600_000.0, 0.0, marginPercent = 20)
        assertTrue(highMargin.rawBuyPriceInr < lowMargin.rawBuyPriceInr)
    }

    @Test
    fun `zero margin and zero refurbishment means buy price equals selling price`() {
        val result = SimpleBuyPriceCalculator.calculate(600_000.0, 0.0, marginPercent = 0)
        assertEquals(600_000.0, result.rawBuyPriceInr, 0.01)
    }

    @Test
    fun `negative raw result is NOT VIABLE and exposes the true negative value`() {
        val result = SimpleBuyPriceCalculator.calculate(sellingPriceInr = 100_000.0, refurbishmentInr = 80_000.0, marginPercent = 30)
        // margin = 30,000; raw = 100,000 - 80,000 - 30,000 = -10,000
        assertEquals(-10_000.0, result.rawBuyPriceInr, 0.01)
        assertFalse(result.isViable)
        assertEquals(0.0, result.buyPriceInr, 0.01) // display-safe field never goes negative
    }

    @Test
    fun `exactly zero raw result is not viable - breaking even is not a real offer`() {
        val result = SimpleBuyPriceCalculator.calculate(sellingPriceInr = 100_000.0, refurbishmentInr = 0.0, marginPercent = 100)
        assertEquals(0.0, result.rawBuyPriceInr, 0.01)
        assertFalse(result.isViable)
    }

    @Test
    fun `zero selling price is not viable and does not crash`() {
        val result = SimpleBuyPriceCalculator.calculate(sellingPriceInr = 0.0, refurbishmentInr = 0.0, marginPercent = 10)
        assertEquals(0.0, result.rawBuyPriceInr, 0.01)
        assertFalse(result.isViable)
    }
}
