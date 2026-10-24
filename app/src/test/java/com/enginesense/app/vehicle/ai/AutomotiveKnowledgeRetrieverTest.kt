package com.enginesense.app.vehicle.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomotiveKnowledgeRetrieverTest {

    private val retriever = LocalAutomotiveKnowledgeRetriever()

    @Test
    fun `a known DTC code returns a grounding hint from CommonDtcGlossary`() {
        val hints = retriever.relevantEntries("what does P0420 mean?")
        assertTrue(hints.isNotEmpty())
        assertTrue(hints.any { it.contains("P0420") })
    }

    @Test
    fun `a general symptom returns a hint from GeneralAutomotiveKnowledge`() {
        val hints = retriever.relevantEntries("my engine is overheating")
        assertTrue(hints.any { it.contains("overheat", ignoreCase = true) })
    }

    @Test
    fun `a question matching nothing returns no fabricated hints`() {
        val hints = retriever.relevantEntries("is my windshield wiper fluid level fine?")
        assertTrue(hints.isEmpty())
    }

    @Test
    fun `results are capped at the requested limit`() {
        // Even though at most 2 sources exist today (glossary + general knowledge), the limit
        // parameter itself must be honored, not just incidentally satisfied.
        val hints = retriever.relevantEntries("what does P0420 mean?", limit = 1)
        assertEquals(1, hints.size)
    }

    @Test
    fun `works with no scan context — this is a pure text lookup, not scan-dependent`() {
        // AutomotiveKnowledgeRetriever doesn't take an AiVehicleContext at all, by design —
        // confirms it's usable even when hasScan is false, same as GeneralAutomotiveKnowledge.
        val hints = retriever.relevantEntries("why is my car overheating")
        assertTrue(hints.isNotEmpty())
    }
}
