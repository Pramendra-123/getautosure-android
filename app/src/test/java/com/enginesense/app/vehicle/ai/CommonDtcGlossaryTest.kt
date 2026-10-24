package com.enginesense.app.vehicle.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonDtcGlossaryTest {

    @Test
    fun `P0301 is a known code with real content, not a placeholder`() {
        val info = CommonDtcGlossary.lookup("P0301")
        assertNotNull(info)
        assertTrue(info!!.meaning.contains("Cylinder 1", ignoreCase = true))
        assertTrue(info.meaning.contains("misfire", ignoreCase = true))
        assertEquals("CRITICAL", info.urgency)
        assertTrue(info.symptoms.isNotBlank())
        assertTrue(info.possibleCauses.isNotBlank())
        assertTrue(info.diagnosticSteps.isNotBlank())
        assertTrue(info.drivingGuidance.isNotBlank())
    }

    @Test
    fun `lookup is case-insensitive`() {
        assertEquals(CommonDtcGlossary.lookup("p0301"), CommonDtcGlossary.lookup("P0301"))
    }

    @Test
    fun `unknown code returns null rather than a guessed answer`() {
        assertNull(CommonDtcGlossary.lookup("P9999"))
    }

    @Test
    fun `findMentioned locates a code embedded in a free-text question`() {
        val info = CommonDtcGlossary.findMentioned("what does p0301 mean for my car?")
        assertNotNull(info)
        assertEquals("P0301", info!!.code)
    }

    @Test
    fun `findMentioned returns null when no known code is present`() {
        assertNull(CommonDtcGlossary.findMentioned("is my transmission okay?"))
    }
}
