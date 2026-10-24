package com.enginesense.app.vehicle.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockAiServicesTest {

    private fun contextWithOneCriticalFinding() = AiVehicleContext(
        generatedAtMillis = 0L,
        vehicleBrand = "Tata",
        signals = listOf(AiSignal("Engine RPM", "rpm", "VERIFIED", 1500.0)),
        findings = listOf(
            AiFinding(
                id = "DTC_P0301",
                title = "Code P0301",
                summary = "Cylinder 1 is misfiring.",
                severity = "CRITICAL",
                recommendedAction = "Stop driving and get this checked.",
                relatedDtcCodes = listOf("P0301")
            )
        )
    )

    @Test
    fun `template report never claims a finding that is not in the context`() = runBlocking {
        val report = TemplateAiCarHealthReportService().generateReport(contextWithOneCriticalFinding())

        assertEquals("Your car needs attention soon", report.headline)
        assertEquals(1, report.sections.size)
        assertEquals("Code P0301", report.sections[0].heading)
        assertTrue(report.sections[0].body.contains("Cylinder 1 is misfiring."))
    }

    @Test
    fun `template report for a clean context says so and adds no sections`() = runBlocking {
        val clean = AiVehicleContext(0L, null, emptyList(), emptyList())
        val report = TemplateAiCarHealthReportService().generateReport(clean)

        assertEquals("No issues detected", report.headline)
        assertTrue(report.sections.isEmpty())
    }

    @Test
    fun `rule-based chat answers from a matching finding, not a guess`() = runBlocking {
        val reply = RuleBasedAiMechanicChatService().ask(
            contextWithOneCriticalFinding(),
            emptyList(),
            "what does P0301 mean?"
        )
        assertTrue(reply.text.contains("misfiring"))
    }

    @Test
    fun `rule-based chat admits it has no data rather than inventing an answer`() = runBlocking {
        // Deliberately not a finding, signal, DTC, or GeneralAutomotiveKnowledge topic —
        // "transmission" now gets a real general-knowledge answer (see tests below), so this
        // uses a question that genuinely matches nothing to keep testing the honest fallback.
        val reply = RuleBasedAiMechanicChatService().ask(
            contextWithOneCriticalFinding(),
            emptyList(),
            "is my windshield wiper fluid level fine?"
        )
        assertTrue(reply.text.contains("don't have data"))
    }

    @Test
    fun `safe-to-drive question reflects the worst finding's severity`() = runBlocking {
        val reply = RuleBasedAiMechanicChatService().ask(
            contextWithOneCriticalFinding(),
            emptyList(),
            "Is my car safe to drive?"
        )
        assertTrue(reply.text.startsWith("No"))
    }

    @Test
    fun `safe-to-drive question says yes when nothing was flagged`() = runBlocking {
        val clean = AiVehicleContext(0L, null, emptyList(), emptyList())
        val reply = RuleBasedAiMechanicChatService().ask(clean, emptyList(), "safe to drive?")
        assertTrue(reply.text.startsWith("Yes"))
    }

    @Test
    fun `cost question never invents a number for an issue not in the tentative cost reference`() = runBlocking {
        val reply = RuleBasedAiMechanicChatService().ask(
            contextWithOneCriticalFinding(),
            emptyList(),
            "how much could this cost to fix?"
        )
        assertTrue(reply.text.contains("don't have real repair-cost data"))
        assertTrue(Regex("[₹$]\\s?\\d").containsMatchIn(reply.text).not())
    }

    @Test
    fun `cost question for a finding that IS in the tentative cost reference gives a clearly-labeled range`() = runBlocking {
        val context = AiVehicleContext(
            generatedAtMillis = 0L, vehicleBrand = null,
            signals = emptyList(),
            findings = listOf(
                AiFinding(
                    id = "OVERHEAT",
                    title = "Engine overheating",
                    summary = "Coolant temperature exceeded safe range.",
                    severity = "CRITICAL",
                    recommendedAction = "Stop driving and let it cool.",
                    relatedDtcCodes = emptyList()
                )
            )
        )
        val reply = RuleBasedAiMechanicChatService().ask(context, emptyList(), "how much will this cost to fix?")
        assertTrue(reply.text.contains("Tentative range"))
        assertTrue(Regex("₹[\\d,]+").containsMatchIn(reply.text))
        assertTrue(reply.text.contains("not a real quote"))
    }

    @Test
    fun `a direct cost question naming a repair category works even with no scan at all`() = runBlocking {
        val clean = AiVehicleContext(0L, null, emptyList(), emptyList())
        val reply = RuleBasedAiMechanicChatService().ask(clean, emptyList(), "how much would a clutch replacement cost")
        assertTrue(reply.text.contains("Tentative range"))
        assertTrue(reply.text.contains("clutch replacement", ignoreCase = true))
    }

    @Test
    fun `urgency question echoes the recommended action, not a new claim`() = runBlocking {
        val reply = RuleBasedAiMechanicChatService().ask(
            contextWithOneCriticalFinding(),
            emptyList(),
            "how urgent is this?"
        )
        assertTrue(reply.text.contains("Stop driving and get this checked."))
    }

    // --- CommonDtcGlossary fallback: a known code NOT in the current scan's findings ---

    @Test
    fun `known code not in current findings is explained from general knowledge, clearly labeled`() = runBlocking {
        // Current scan only has P0301 — ask about a different, unrelated known code.
        val reply = RuleBasedAiMechanicChatService().ask(
            contextWithOneCriticalFinding(),
            emptyList(),
            "what does P0171 mean?"
        )
        assertTrue(reply.text.contains("didn't flag P0171"))
        assertTrue(reply.text.contains("lean", ignoreCase = true))
    }

    @Test
    fun `known code glossary fallback works even with an empty scan`() = runBlocking {
        val clean = AiVehicleContext(0L, null, emptyList(), emptyList())
        val reply = RuleBasedAiMechanicChatService().ask(clean, emptyList(), "what is P0420?")
        assertTrue(reply.text.contains("didn't flag P0420"))
        assertTrue(reply.text.contains("catalyst", ignoreCase = true))
    }

    @Test
    fun `a current-scan finding is answered from the finding, not the general glossary, when both could match`() = runBlocking {
        // P0301 is BOTH the current finding's code AND in the glossary — the actual
        // finding (this car's real result) must win over general knowledge.
        val reply = RuleBasedAiMechanicChatService().ask(
            contextWithOneCriticalFinding(),
            emptyList(),
            "what does P0301 mean?"
        )
        assertTrue(reply.text.contains("Cylinder 1 is misfiring."))
        assertTrue(reply.text.contains("didn't flag").not())
    }

    @Test
    fun `an unrecognized code-shaped question says so honestly instead of a generic non-answer`() = runBlocking {
        val reply = RuleBasedAiMechanicChatService().ask(
            contextWithOneCriticalFinding(),
            emptyList(),
            "what does P9999 mean?"
        )
        assertTrue(reply.text.contains("don't recognize P9999"))
    }

    // --- History-aware follow-ups, no-scan general knowledge, and beyondObdScope disclosure ---

    @Test
    fun `a pronoun follow-up after a glossary lookup resolves to that code, not a generic no-data answer`() = runBlocking {
        val clean = AiVehicleContext(0L, null, emptyList(), emptyList())
        val history = listOf(ChatMessage(fromUser = true, text = "what does P0420 mean?"))
        val reply = RuleBasedAiMechanicChatService().ask(clean, history, "is it urgent?")
        assertTrue(reply.text.lowercase().contains("p0420"))
    }

    @Test
    fun `a pronoun follow-up after a current-scan finding still resolves to that finding`() = runBlocking {
        val history = listOf(ChatMessage(fromUser = true, text = "what does P0301 mean?"))
        val reply = RuleBasedAiMechanicChatService().ask(contextWithOneCriticalFinding(), history, "is it urgent?")
        assertTrue(reply.text.contains("Code P0301") || reply.text.contains("Stop driving"))
    }

    @Test
    fun `overheating asks a clarifying question first, with safety guidance immediately`() = runBlocking {
        val reply = RuleBasedAiMechanicChatService().ask(AiVehicleContext.NO_SCAN, emptyList(), "why is my car overheating?")
        assertTrue(reply.text.contains("Pull over", ignoreCase = true))
        assertTrue(reply.text.contains("idling", ignoreCase = true) && reply.text.contains("driving", ignoreCase = true))
    }

    @Test
    fun `answering overheating's clarifying question gets the full structured answer, folding the answer in as evidence`() = runBlocking {
        val service = RuleBasedAiMechanicChatService()
        val firstReply = service.ask(AiVehicleContext.NO_SCAN, emptyList(), "my engine is overheating")
        val history = listOf(
            ChatMessage(fromUser = true, text = "my engine is overheating"),
            ChatMessage(fromUser = false, text = firstReply.text)
        )
        val reply = service.ask(AiVehicleContext.NO_SCAN, history, "only in traffic")

        assertTrue(reply.text.contains("coolant", ignoreCase = true) || reply.text.contains("thermostat", ignoreCase = true))
        assertTrue(reply.text.contains("general automotive knowledge", ignoreCase = true))
        assertTrue(reply.text.contains("only in traffic"))
        assertTrue(reply.text.contains("Urgency:"))
        assertTrue(reply.text.contains("What a mechanic should check:"))
    }

    @Test
    fun `a MODERATE-urgency topic without a clarifying question answers in full structure immediately`() = runBlocking {
        // Transmission/gear issues are MODERATE urgency — warrants the full 7-point structure.
        val reply = RuleBasedAiMechanicChatService().ask(AiVehicleContext.NO_SCAN, emptyList(), "my gear shifting is hard")
        assertTrue(reply.text.contains("general automotive knowledge", ignoreCase = true))
        assertTrue(reply.text.contains("Possible causes:"))
        assertTrue(reply.text.contains("What a mechanic should check:"))
    }

    @Test
    fun `a MINOR-urgency topic gets a concise natural answer, not the full 7-point structure`() = runBlocking {
        // Poor mileage is a simple, low-stakes question — spelling out all 7 points would be
        // bureaucratic overkill for it; see RuleBasedAiMechanicChatService.conciseSymptomAnswer.
        val reply = RuleBasedAiMechanicChatService().ask(AiVehicleContext.NO_SCAN, emptyList(), "why is my fuel mileage so poor")
        assertTrue(reply.text.contains("general automotive knowledge", ignoreCase = true))
        assertTrue(reply.text.contains("air filter", ignoreCase = true))
        assertTrue(reply.text.contains("Possible causes:").not())
    }

    @Test
    fun `a beyond-OBD-scope topic includes an explicit scan-cannot-check disclaimer`() = runBlocking {
        val reply = RuleBasedAiMechanicChatService().ask(AiVehicleContext.NO_SCAN, emptyList(), "my tyre has a bulge, is that ok?")
        assertTrue(reply.text.contains("can't check", ignoreCase = true))
    }

    @Test
    fun `safe-to-drive with no scan at all asks to run one rather than falsely saying yes`() = runBlocking {
        val reply = RuleBasedAiMechanicChatService().ask(AiVehicleContext.NO_SCAN, emptyList(), "is my car safe to drive?")
        assertTrue(reply.text.contains("don't have a scan"))
    }

    @Test
    fun `unmatched question with no scan explains it can still help generally rather than claiming nothing was flagged`() = runBlocking {
        val reply = RuleBasedAiMechanicChatService().ask(AiVehicleContext.NO_SCAN, emptyList(), "what's the weather like for driving today")
        assertTrue(reply.text.contains("haven't scanned"))
    }

    @Test
    fun `a very short ambiguous question asks for clarification instead of a dead end`() = runBlocking {
        val clean = AiVehicleContext(0L, null, emptyList(), emptyList())
        val reply = RuleBasedAiMechanicChatService().ask(clean, emptyList(), "hmm issue")
        assertTrue(reply.text.contains("Can you tell me"))
    }

    // --- Safety-critical symptoms: immediate guidance, no scan needed ---

    @Test
    fun `a misfire code not in the current scan still gets clear urgency and stop-driving guidance, labeled as general knowledge`() = runBlocking {
        val clean = AiVehicleContext(0L, null, emptyList(), emptyList())
        val reply = RuleBasedAiMechanicChatService().ask(clean, emptyList(), "what does P0300 mean?")
        assertTrue(reply.text.contains("didn't flag P0300"))
        assertTrue(reply.text.contains("Urgency:"))
        assertTrue(reply.text.contains("High", ignoreCase = true))
        assertTrue(reply.text.contains("misfire", ignoreCase = true))
    }

    @Test
    fun `urgency is never phrased as a confirmed diagnosis for a code not in the current scan`() = runBlocking {
        val clean = AiVehicleContext(0L, null, emptyList(), emptyList())
        val reply = RuleBasedAiMechanicChatService().ask(clean, emptyList(), "what does P0171 mean?")
        assertTrue(reply.text.contains("Possible causes:"))
        assertTrue(reply.text.contains("not confirmed on your car", ignoreCase = true) || reply.text.contains("didn't flag", ignoreCase = true))
    }

    @Test
    fun `oil pressure warning light gets a stop-driving answer immediately, not a clarifying question`() = runBlocking {
        val reply = RuleBasedAiMechanicChatService().ask(AiVehicleContext.NO_SCAN, emptyList(), "my oil pressure light came on")
        assertTrue(reply.text.contains("Stop", ignoreCase = true))
        assertTrue(reply.text.contains("Urgency:"))
    }

    @Test
    fun `brake failure gets a stop-driving answer immediately, not a clarifying question`() = runBlocking {
        val reply = RuleBasedAiMechanicChatService().ask(AiVehicleContext.NO_SCAN, emptyList(), "my brake pedal is going to the floor")
        assertTrue(reply.text.contains("stop-driving", ignoreCase = true) || reply.text.contains("do not continue driving", ignoreCase = true))
    }

    @Test
    fun `brake noise and brake failure are distinct topics, not shadowed by generic brake keywords`() = runBlocking {
        val squeal = RuleBasedAiMechanicChatService().ask(AiVehicleContext.NO_SCAN, emptyList(), "my brakes are squealing")
        val failure = RuleBasedAiMechanicChatService().ask(AiVehicleContext.NO_SCAN, emptyList(), "no brakes, pedal goes to the floor")
        assertTrue(squeal.text.contains("pad", ignoreCase = true))
        assertTrue(failure.text.contains("fluid", ignoreCase = true) || failure.text.contains("master cylinder", ignoreCase = true))
    }

    // --- Missing/incomplete sensor data ---

    @Test
    fun `an unsupported sensor is reported as unsupported, not a guessed value`() = runBlocking {
        val context = AiVehicleContext(
            generatedAtMillis = 0L, vehicleBrand = null,
            signals = listOf(AiSignal("Engine Oil Temperature", "°C", "UNSUPPORTED", null)),
            findings = emptyList()
        )
        val reply = RuleBasedAiMechanicChatService().ask(context, emptyList(), "what's my engine oil temperature?")
        assertTrue(reply.text.contains("isn't supported"))
    }

    @Test
    fun `a supported sensor with no data this scan says so rather than inventing a value`() = runBlocking {
        val context = AiVehicleContext(
            generatedAtMillis = 0L, vehicleBrand = null,
            signals = listOf(AiSignal("Fuel Level", "%", "SUPPORTED_NO_DATA", null)),
            findings = emptyList()
        )
        val reply = RuleBasedAiMechanicChatService().ask(context, emptyList(), "what's my fuel level?")
        assertTrue(reply.text.contains("didn't return data"))
    }

    // --- Empty/malformed input ---

    @Test
    fun `an empty question does not crash and gets a clarifying response`() = runBlocking {
        val clean = AiVehicleContext(0L, null, emptyList(), emptyList())
        val reply = RuleBasedAiMechanicChatService().ask(clean, emptyList(), "")
        assertTrue(reply.text.isNotBlank())
    }

    // --- GenericDtcExplainer: codes beyond CommonDtcGlossary's curated subset ---

    private class FakeGenericDtcExplainer(private val known: Map<String, GenericDtcExplanation>) : GenericDtcExplainer {
        override fun explain(code: String, vehicleBrand: String?): GenericDtcExplanation? = known[code.uppercase()]
    }

    @Test
    fun `a code not in CommonDtcGlossary but known to the generic explainer is still explained`() = runBlocking {
        val explainer = FakeGenericDtcExplainer(
            mapOf("P0135" to GenericDtcExplanation("P0135", "The oxygen sensor heater circuit isn't working as expected.  (P0135)", "MODERATE"))
        )
        val reply = RuleBasedAiMechanicChatService(genericDtcExplainer = explainer).ask(
            AiVehicleContext.NO_SCAN, emptyList(), "what does P0135 mean?"
        )
        assertTrue(reply.text.contains("didn't flag P0135"))
        assertTrue(reply.text.contains("oxygen sensor heater", ignoreCase = true))
    }

    @Test
    fun `a code the generic explainer doesn't recognize either still falls back to the honest unknown-code answer`() = runBlocking {
        val explainer = FakeGenericDtcExplainer(emptyMap())
        val reply = RuleBasedAiMechanicChatService(genericDtcExplainer = explainer).ask(
            AiVehicleContext.NO_SCAN, emptyList(), "what does P9999 mean?"
        )
        assertTrue(reply.text.contains("don't recognize P9999"))
    }

    @Test
    fun `without a generic explainer wired in, behavior is unchanged from before`() = runBlocking {
        val reply = RuleBasedAiMechanicChatService().ask(AiVehicleContext.NO_SCAN, emptyList(), "what does P0135 mean?")
        assertTrue(reply.text.contains("don't recognize P0135"))
    }

    // --- Keyword-matching false positives (regression — found via real-device testing) ---

    @Test
    fun `an unrelated question is not misattributed to a short, coincidentally-substring-matching topic keyword`() = runBlocking {
        // Real-device bug: "what is the best place to sell the car" matched the AC-not-cooling
        // topic because its "ac" keyword is a substring of "pl-ac-e" — a bare `"ac" in q` check
        // doesn't require a word boundary. Should fall through to an honest no-match answer.
        val reply = RuleBasedAiMechanicChatService().ask(AiVehicleContext.NO_SCAN, emptyList(), "what is the best place to sell the car")
        assertTrue(reply.text.contains("refrigerant", ignoreCase = true).not())
        assertTrue(reply.text.contains("AC is on", ignoreCase = true).not())
    }

    @Test
    fun `a common word shared between a signal name and the question does not cause a false match`() = runBlocking {
        // Real-device bug: "can you help with nearby mechanic store?" matched a signal named
        // "Distance Driven With Warning Light On" purely because "with" (length 4) is a literal
        // word in both — a bare length>3 check doesn't exclude common function words.
        val context = AiVehicleContext(
            generatedAtMillis = 0L, vehicleBrand = null,
            signals = listOf(AiSignal("Distance Driven With Warning Light On", "km", "UNSUPPORTED", null)),
            findings = emptyList()
        )
        val reply = RuleBasedAiMechanicChatService().ask(context, emptyList(), "can you help with nearby mechanic store?")
        assertTrue(reply.text.contains("Distance Driven").not())
        assertTrue(reply.text.contains("isn't supported").not())
    }

    // --- Free/Premium capability boundary: architecture present, nothing premium active yet ---

    @Test
    fun `premium-only capabilities are gated, and repair-cost reference is unavailable to both tiers`() {
        assertTrue(AiMechanicCapabilities.canUseScanHistoryReasoning(AiMechanicTier.FREE).not())
        assertTrue(AiMechanicCapabilities.canUseScanHistoryReasoning(AiMechanicTier.PREMIUM))
        assertTrue(AiMechanicCapabilities.canUseRepairCostReference(AiMechanicTier.FREE).not())
        assertTrue(AiMechanicCapabilities.canUseRepairCostReference(AiMechanicTier.PREMIUM).not())
    }
}
