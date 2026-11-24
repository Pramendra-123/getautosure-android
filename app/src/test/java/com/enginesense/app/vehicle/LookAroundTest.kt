package com.enginesense.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookAroundTest {

    @Test fun `panel marks and needs-work answers become priced counts`() {
        val counts = LookAround.counts(
            marks = mapOf(
                "bonnet" to setOf(PanelIssue.SCRATCH),
                "front_left_door" to setOf(PanelIssue.DENT, PanelIssue.REPAINTED),
                "rear_bumper" to setOf(PanelIssue.CRACKED_BUMPER),
                "windshield" to setOf(PanelIssue.CRACKED_GLASS)
            ),
            answers = mapOf("tyres" to QuickAnswer.NEEDS_WORK, "ac" to QuickAnswer.OK)
        )
        assertEquals(mapOf("scratch" to 1, "dent" to 1, "repaint" to 1, "bumper" to 1, "glass" to 1, "tyre" to 2), counts)
    }

    @Test fun `cost range matches the fixed table`() {
        // Scratch 1,500–4,000 + dent 2,000–6,000 + rust 2,000–8,000, as in the mockup.
        val counts = LookAround.counts(
            mapOf("bonnet" to setOf(PanelIssue.SCRATCH), "front_left_door" to setOf(PanelIssue.DENT), "rear_right_quarter" to setOf(PanelIssue.RUST)),
            emptyMap()
        )
        assertEquals(5_500 to 18_000, LookAround.costRange(counts))
    }

    @Test fun `every panel has a place and at least one issue`() {
        assertEquals(LookAround.panels.size, LookAround.panels.map { it.id }.toSet().size)
        assertTrue(LookAround.panels.all { it.kind.issues.isNotEmpty() })
        LookAround.quickChecks.forEach { q -> assertTrue(ExteriorDamage.items.any { it.id == q.itemId }) }
    }

    @Test fun `only yes answers become accident signs and ratings follow the answers`() {
        assertEquals(listOf(LookAround.accidentSigns[1]), LookAround.accidentFindings(mapOf(0 to false, 1 to true)))
        assertEquals(mapOf("Tyres" to 1, "AC" to 5), LookAround.ratings(mapOf("tyres" to QuickAnswer.NEEDS_WORK, "ac" to QuickAnswer.GOOD)))
    }
}
