package com.enginesense.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantTest {

    @Test
    fun `the button shows on everyday screens`() {
        listOf("home", "connect", "reports", "scanResults", "aiReport", "issueDetail/{issueId}", "repairGuidance/{findingId}", "prePurchaseResult")
            .forEach { assertTrue(it, showsAssistant(it)) }
    }

    @Test
    fun `the button stays out of sign-up, scans in progress, full-screen tools and the chat itself`() {
        listOf(
            "splash", "login", "enterRegistration", "selectModel/{brand}", "addVehicleDetails/{brand}/{model}",
            "scanning", "liveData", "performance", "tripMileage", "pdfViewer/{path}", "aiMechanic", "aiMechanicAdvanced", null
        ).forEach { assertFalse(it.toString(), showsAssistant(it)) }
    }

    @Test
    fun `a problem's screen names that problem and suggests questions about it`() {
        val ctx = aiContextFor("repairGuidance/{findingId}", "Cylinder 1 misfire detected")
        assertEquals("Cylinder 1 misfire detected", ctx.screen)
        assertTrue(ctx.suggestions.any { "Cylinder 1 misfire detected" in it })
        assertFalse(ctx.aboutUsedCar)
    }

    @Test
    fun `the used car check answers about the seller's car, not the user's own`() {
        val ctx = aiContextFor("prePurchaseResult", null)
        assertTrue(ctx.aboutUsedCar)
        assertTrue(ctx.suggestions.contains("Should I buy this car?"))
    }

    @Test
    fun `every screen offers at least one suggested question`() {
        listOf("home", "connect", "reports", "scanResults", "milStatus", "allSensors", "understandRepair", "somethingNew")
            .forEach { assertTrue(it, aiContextFor(it, null).suggestions.isNotEmpty()) }
    }
}
