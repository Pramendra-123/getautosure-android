package com.enginesense.app.vehicle.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairCostReferenceTest {

    @Test
    fun `every preview category name exists in the full entries list`() {
        // Guards against a typo in previewCategories silently dropping a category (mapNotNull
        // would hide it rather than fail loudly).
        assertEquals(RepairCostReference.previewCategories.size, RepairCostReference.preview.size)
    }

    @Test
    fun `preview has exactly six categories, matching the Repair Guidance screen's design`() {
        assertEquals(6, RepairCostReference.preview.size)
    }

    @Test
    fun `every entry has a positive range with max at or above min`() {
        RepairCostReference.entries.forEach { entry ->
            assertTrue("${entry.category} min must be positive", entry.minInr > 0)
            assertTrue("${entry.category} max must be >= min", entry.maxInr >= entry.minInr)
        }
    }

    @Test
    fun `findByTitle matches a finding title mentioning a known category`() {
        val match = RepairCostReference.findByTitle("Engine overheating detected")
        assertNotNull(match)
        assertEquals("Radiator / cooling system", match!!.category)
    }

    @Test
    fun `findByTitle is case-insensitive`() {
        assertNotNull(RepairCostReference.findByTitle("CLUTCH SLIPPING"))
    }

    @Test
    fun `findByTitle returns null for a title matching no known category`() {
        assertNull(RepairCostReference.findByTitle("Code P0301 - Cylinder 1 misfiring"))
    }

    @Test
    fun `a short keyword like the AC entry's does not false-match inside an unrelated word`() {
        // Same real bug this app hit before: "ac" as a bare substring matches "place".
        assertNull(RepairCostReference.findByQuestion("what is the best place to sell my car"))
    }

    @Test
    fun `findByQuestion matches a direct cost question naming a category with no scan context`() {
        val match = RepairCostReference.findByQuestion("how much would a clutch replacement cost")
        assertNotNull(match)
        assertEquals("Clutch replacement", match!!.category)
    }
}
