package com.enginesense.app.vehicle.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pure request-building/parsing logic — no network involved. Needs Robolectric (already a test
 * dependency, added for the real-DTC-database tests) purely because it exercises org.json.* at
 * runtime: plain JVM unit tests link against the Android SDK's stub jar, where JSONObject/
 * JSONArray methods throw "Method ... not mocked" rather than doing anything. Robolectric
 * shadows them with a real implementation.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteAiProtocolTest {

    private val scanContext = AiVehicleContext(
        generatedAtMillis = 1000L, vehicleBrand = "Tata",
        signals = listOf(AiSignal("Engine RPM", "rpm", "VERIFIED", 1500.0)),
        findings = emptyList()
    )

    @Test
    fun `history longer than the cap is truncated to the most recent turns, in chronological order`() {
        val history = (1..20).map { ChatMessage(fromUser = it % 2 == 0, text = "turn $it") }
        val body = RemoteAiProtocol.buildRequestBody(scanContext, history, "current question", emptyList())

        val sentHistory = body.getJSONArray("history")
        assertEquals(RemoteAiProtocol.MAX_HISTORY_TURNS, sentHistory.length())
        // Oldest entries are dropped, not newest — the last entry sent should be "turn 20".
        assertEquals("turn 20", sentHistory.getJSONObject(sentHistory.length() - 1).getString("text"))
        // Chronological order preserved: each entry's number is greater than the previous one's.
        var lastNum = 0
        for (i in 0 until sentHistory.length()) {
            val num = sentHistory.getJSONObject(i).getString("text").removePrefix("turn ").toInt()
            assertTrue("history out of order at index $i", num > lastNum)
            lastNum = num
        }
    }

    @Test
    fun `the current question is never present in the outgoing history`() {
        val history = listOf(
            ChatMessage(fromUser = true, text = "earlier question"),
            ChatMessage(fromUser = false, text = "earlier reply")
        )
        val body = RemoteAiProtocol.buildRequestBody(scanContext, history, "what does P0301 mean?", emptyList())

        assertEquals("what does P0301 mean?", body.getString("question"))
        val sentHistory = body.getJSONArray("history")
        for (i in 0 until sentHistory.length()) {
            assertFalse(sentHistory.getJSONObject(i).getString("text") == "what does P0301 mean?")
        }
    }

    @Test
    fun `an overlong question is capped, not rejected or truncated silently without a bound`() {
        val longQuestion = "a".repeat(RemoteAiProtocol.MAX_QUESTION_LENGTH + 500)
        val body = RemoteAiProtocol.buildRequestBody(scanContext, emptyList(), longQuestion, emptyList())
        assertEquals(RemoteAiProtocol.MAX_QUESTION_LENGTH, body.getString("question").length)
    }

    @Test
    fun `context is included when a scan exists`() {
        val body = RemoteAiProtocol.buildRequestBody(scanContext, emptyList(), "q", emptyList())
        assertTrue(body.get("context") is org.json.JSONObject)
        assertEquals("Tata", body.getJSONObject("context").getString("vehicleBrand"))
    }

    @Test
    fun `context is null when there is no scan, never fabricated`() {
        val body = RemoteAiProtocol.buildRequestBody(AiVehicleContext.NO_SCAN, emptyList(), "q", emptyList())
        assertTrue(body.isNull("context"))
    }

    @Test
    fun `knowledge hints are passed through`() {
        val body = RemoteAiProtocol.buildRequestBody(scanContext, emptyList(), "q", listOf("hint one", "hint two"))
        val hints = body.getJSONArray("knowledgeHints")
        assertEquals(2, hints.length())
        assertEquals("hint one", hints.getString(0))
    }

    @Test
    fun `a successful response body is parsed into the reply text`() {
        val reply = RemoteAiProtocol.parseResponseBody("""{"reply": "Your mileage is likely fine."}""")
        assertEquals("Your mileage is likely fine.", reply)
    }

    @Test(expected = RemoteAiParseException::class)
    fun `invalid JSON throws a parse exception`() {
        RemoteAiProtocol.parseResponseBody("not json at all")
    }

    @Test(expected = RemoteAiParseException::class)
    fun `valid JSON missing the reply field throws a parse exception`() {
        RemoteAiProtocol.parseResponseBody("""{"somethingElse": "value"}""")
    }

    @Test(expected = RemoteAiParseException::class)
    fun `a blank reply field is treated as missing, not an empty message`() {
        RemoteAiProtocol.parseResponseBody("""{"reply": "   "}""")
    }

    @Test
    fun `a structured backend error is surfaced as RemoteAiUpstreamException, not parsed as a reply`() {
        try {
            RemoteAiProtocol.parseResponseBody("""{"error": {"code": "rate_limited", "message": "Too many requests"}}""")
            org.junit.Assert.fail("expected RemoteAiUpstreamException")
        } catch (e: RemoteAiUpstreamException) {
            assertEquals("rate_limited", e.code)
            assertEquals("Too many requests", e.upstreamMessage)
        }
    }
}
