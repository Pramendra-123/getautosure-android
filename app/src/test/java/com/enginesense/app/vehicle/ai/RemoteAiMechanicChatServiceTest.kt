package com.enginesense.app.vehicle.ai

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * Exercises RemoteAiMechanicChatService against a real local HTTP server — not a mock of OkHttp
 * itself. Needs Robolectric for the same reason as RemoteAiProtocolTest: parsing the response
 * body goes through org.json.*, which throws under plain JVM unit tests without it.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteAiMechanicChatServiceTest {

    private lateinit var server: MockWebServer
    private val noScan = AiVehicleContext.NO_SCAN
    private val fixedToken: suspend () -> String = { "test-id-token" }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a successful response is parsed into the reply`() = runTest {
        server.enqueue(MockResponse().setBody("""{"reply": "Check your air filter first."}"""))
        val service = RemoteAiMechanicChatService(endpointUrl = server.url("/ask").toString(), idTokenProvider = fixedToken)

        val reply = service.ask(noScan, emptyList(), "why is my mileage poor?")

        assertEquals("Check your air filter first.", reply.text)
        assertEquals(false, reply.fromUser)
    }

    @Test
    fun `the request body sent actually matches the protocol`() = runTest {
        server.enqueue(MockResponse().setBody("""{"reply": "ok"}"""))
        val service = RemoteAiMechanicChatService(endpointUrl = server.url("/ask").toString(), idTokenProvider = fixedToken)

        service.ask(noScan, emptyList(), "is my car safe to drive?")

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        val sentJson = org.json.JSONObject(recorded.body.readUtf8())
        assertEquals("is my car safe to drive?", sentJson.getString("question"))
    }

    @Test
    fun `a slow response past the configured timeout surfaces as RemoteAiNetworkException, not a hang`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"reply": "too slow"}""")
                .setHeadersDelay(1500, TimeUnit.MILLISECONDS)
        )
        // Short timeout so this test runs fast while still genuinely exercising the timeout path.
        val service = RemoteAiMechanicChatService(endpointUrl = server.url("/ask").toString(), idTokenProvider = fixedToken, timeoutMillis = 200)

        try {
            service.ask(noScan, emptyList(), "question")
            fail("expected a timeout failure")
        } catch (e: RemoteAiNetworkException) {
            // expected — OkHttp's call timeout surfaces as an IOException, wrapped here.
        }
    }

    @Test
    fun `a connection failure (server unreachable) surfaces as RemoteAiNetworkException`() = runTest {
        val deadUrl = server.url("/ask").toString()
        server.shutdown() // now nothing is listening on this port
        val service = RemoteAiMechanicChatService(endpointUrl = deadUrl, idTokenProvider = fixedToken, timeoutMillis = 1000)

        try {
            service.ask(noScan, emptyList(), "question")
            fail("expected a connection failure")
        } catch (e: RemoteAiNetworkException) {
            // expected
        }
    }

    @Test
    fun `a 500 response surfaces as RemoteAiHttpException with the real status code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))
        val service = RemoteAiMechanicChatService(endpointUrl = server.url("/ask").toString(), idTokenProvider = fixedToken)

        try {
            service.ask(noScan, emptyList(), "question")
            fail("expected an HTTP error")
        } catch (e: RemoteAiHttpException) {
            assertEquals(500, e.statusCode)
        }
    }

    @Test
    fun `a 429 response surfaces as RemoteAiHttpException too`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        val service = RemoteAiMechanicChatService(endpointUrl = server.url("/ask").toString(), idTokenProvider = fixedToken)

        try {
            service.ask(noScan, emptyList(), "question")
            fail("expected an HTTP error")
        } catch (e: RemoteAiHttpException) {
            assertEquals(429, e.statusCode)
        }
    }

    @Test
    fun `malformed JSON in a 200 response surfaces as RemoteAiParseException`() = runTest {
        server.enqueue(MockResponse().setBody("this is not json"))
        val service = RemoteAiMechanicChatService(endpointUrl = server.url("/ask").toString(), idTokenProvider = fixedToken)

        try {
            service.ask(noScan, emptyList(), "question")
            fail("expected a parse error")
        } catch (e: RemoteAiParseException) {
            // expected
        }
    }

    @Test
    fun `valid JSON missing the reply field surfaces as RemoteAiParseException`() = runTest {
        server.enqueue(MockResponse().setBody("""{"somethingElse": true}"""))
        val service = RemoteAiMechanicChatService(endpointUrl = server.url("/ask").toString(), idTokenProvider = fixedToken)

        try {
            service.ask(noScan, emptyList(), "question")
            fail("expected a parse error")
        } catch (e: RemoteAiParseException) {
            // expected
        }
    }

    @Test
    fun `a structured backend error response surfaces as RemoteAiUpstreamException`() = runTest {
        server.enqueue(MockResponse().setBody("""{"error": {"code": "rate_limited", "message": "slow down"}}"""))
        val service = RemoteAiMechanicChatService(endpointUrl = server.url("/ask").toString(), idTokenProvider = fixedToken)

        try {
            service.ask(noScan, emptyList(), "question")
            fail("expected an upstream error")
        } catch (e: RemoteAiUpstreamException) {
            assertEquals("rate_limited", e.code)
        }
    }

    @Test
    fun `a blank endpoint URL fails fast without any network call`() = runTest {
        val service = RemoteAiMechanicChatService(endpointUrl = "", idTokenProvider = fixedToken)

        try {
            service.ask(noScan, emptyList(), "question")
            fail("expected RemoteAiNotConfiguredException")
        } catch (e: RemoteAiNotConfiguredException) {
            // expected
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a blank endpoint URL fails fast without even consulting the id token provider`() = runTest {
        var providerCalled = false
        val service = RemoteAiMechanicChatService(endpointUrl = "", idTokenProvider = { providerCalled = true; "token" })

        try {
            service.ask(noScan, emptyList(), "question")
            fail("expected RemoteAiNotConfiguredException")
        } catch (e: RemoteAiNotConfiguredException) {
            // expected
        }
        assertFalse(providerCalled)
    }

    @Test
    fun `the knowledge retriever's hints are included in the outgoing request`() = runTest {
        server.enqueue(MockResponse().setBody("""{"reply": "ok"}"""))
        val service = RemoteAiMechanicChatService(
            endpointUrl = server.url("/ask").toString(),
            idTokenProvider = fixedToken,
            knowledgeRetriever = LocalAutomotiveKnowledgeRetriever()
        )

        service.ask(noScan, emptyList(), "what does P0420 mean?")

        val sentJson = org.json.JSONObject(server.takeRequest().body.readUtf8())
        val hints = sentJson.getJSONArray("knowledgeHints")
        assertTrue(hints.length() > 0)
        assertTrue(hints.getString(0).contains("P0420"))
    }

    @Test
    fun `the id token is sent as a Bearer Authorization header`() = runTest {
        server.enqueue(MockResponse().setBody("""{"reply": "ok"}"""))
        val service = RemoteAiMechanicChatService(
            endpointUrl = server.url("/ask").toString(),
            idTokenProvider = { "a-real-looking-jwt" }
        )

        service.ask(noScan, emptyList(), "question")

        val recorded = server.takeRequest()
        assertEquals("Bearer a-real-looking-jwt", recorded.getHeader("Authorization"))
    }

    @Test
    fun `a fresh token is fetched on every call, not just the first`() = runTest {
        server.enqueue(MockResponse().setBody("""{"reply": "first"}"""))
        server.enqueue(MockResponse().setBody("""{"reply": "second"}"""))
        var callCount = 0
        val service = RemoteAiMechanicChatService(
            endpointUrl = server.url("/ask").toString(),
            idTokenProvider = { "token-${++callCount}" }
        )

        service.ask(noScan, emptyList(), "first question")
        service.ask(noScan, emptyList(), "second question")

        assertEquals("Bearer token-1", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer token-2", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `a missing or expired session (idTokenProvider throws) fails fast without any network call`() = runTest {
        val service = RemoteAiMechanicChatService(
            endpointUrl = server.url("/ask").toString(),
            idTokenProvider = { throw RemoteAiNotAuthenticatedException() }
        )

        try {
            service.ask(noScan, emptyList(), "question")
            fail("expected RemoteAiNotAuthenticatedException")
        } catch (e: RemoteAiNotAuthenticatedException) {
            // expected
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a 401 response from the backend surfaces as RemoteAiHttpException with code 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"unauthorized","message":"Invalid or expired sign-in token"}}"""))
        val service = RemoteAiMechanicChatService(endpointUrl = server.url("/ask").toString(), idTokenProvider = fixedToken)

        try {
            service.ask(noScan, emptyList(), "question")
            fail("expected an HTTP 401 error")
        } catch (e: RemoteAiHttpException) {
            assertEquals(401, e.statusCode)
        }
    }
}
