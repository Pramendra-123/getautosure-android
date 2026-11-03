package com.enginesense.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiyGuidesTest {

    @Test
    fun `a misfire code brings up the coil swap and spark plug guides`() {
        val ids = DiyGuides.forFinding("P0301", "Cylinder 1 misfire detected").map { it.id }
        assertTrue("coil_swap" in ids)
        assertTrue("spark_plugs" in ids)
    }

    @Test
    fun `a low battery finding with no code is matched by its words`() {
        val ids = DiyGuides.forFinding(null, "Low battery/charging voltage").map { it.id }
        assertTrue("jump_start" in ids)
        assertTrue("battery_replace" in ids)
    }

    @Test
    fun `easy jobs come before medium and workshop-only ones`() {
        val ratings = DiyGuides.forFinding("P0171", "System too lean (Bank 1)").map { it.rating }
        assertEquals(ratings.sortedBy { it.ordinal }, ratings)
        assertEquals(DiyRating.EASY, ratings.first())
    }

    @Test
    fun `a catalytic converter code is workshop only and says why`() {
        val guides = DiyGuides.forFinding("P0420", "Catalyst system efficiency below threshold")
        assertTrue(guides.all { it.rating == DiyRating.WORKSHOP })
        assertTrue(guides.all { !it.why.isNullOrBlank() })
    }

    @Test
    fun `every workshop-only guide explains why, and every other one lists time and tools`() {
        DiyGuides.all.forEach { g ->
            if (g.rating == DiyRating.WORKSHOP) assertTrue(g.id, !g.why.isNullOrBlank())
            else assertTrue(g.id, g.time != null && g.tools != null)
        }
    }
}
