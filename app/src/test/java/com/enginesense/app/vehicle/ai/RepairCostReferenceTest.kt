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
    fun `every kind of fault code gets a positive fallback range`() {
        listOf("P0171", "P0301", "P0420", "P0505", "P0606", "P0700", "B1234", "U0100", null).forEach { code ->
            val entry = RepairCostReference.fallbackFor(code)
            assertTrue("$code min must be positive", entry.minInr > 0)
            assertTrue("$code max must be >= min", entry.maxInr >= entry.minInr)
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

    @Test
    fun `category names are unique`() {
        val names = RepairCostReference.entries.map { it.category }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `parts named by the fault cards are priced`() {
        assertEquals("Diesel high-pressure pump", RepairCostReference.findByQuestion("cost of a high-pressure pump")?.category)
        assertEquals("Wheel speed (ABS) sensor", RepairCostReference.findByQuestion("abs wheel speed sensor price")?.category)
        assertEquals("DPF cleaning / replacement", RepairCostReference.findByQuestion("DPF clogged")?.category)
    }

    @Test
    fun `a lost-communication fault is not priced as a new engine computer`() {
        assertNull(RepairCostReference.findByTitle("Lost Communication With ECM/PCM A"))
    }

    @Test
    fun `car size picks the matching part of the range and stays inside it`() {
        val clutch = RepairCostReference.entries.first { it.category == "Clutch replacement" }
        val small = clutch.forSize(CarSize.SMALL)
        val large = clutch.forSize(CarSize.LARGE)
        assertTrue(small.minInr >= clutch.minInr && large.maxInr <= clutch.maxInr)
        assertTrue(small.maxInr <= large.minInr)
        assertEquals(clutch, clutch.forSize(null))
    }

    @Test
    fun `car size comes from the new-car price`() {
        assertEquals(CarSize.SMALL, CarSize.fromExShowroom(650_000.0))
        assertEquals(CarSize.MID, CarSize.fromExShowroom(1_200_000.0))
        assertEquals(CarSize.LARGE, CarSize.fromExShowroom(2_000_000.0))
        assertNull(CarSize.fromExShowroom(null))
    }
}
