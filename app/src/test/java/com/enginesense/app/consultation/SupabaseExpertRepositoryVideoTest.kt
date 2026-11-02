package com.enginesense.app.consultation

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The join_video call: what the app sends, and how the backend's answers come back. Robolectric because requests and parsing go through org.json. */
@RunWith(RobolectricTestRunner::class)
class SupabaseExpertRepositoryVideoTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: SupabaseExpertRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = SupabaseExpertRepository(endpointUrl = server.url("/consultations").toString(), idTokenProvider = { "google-id-token" })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sends join_video with the booking id and sign-in token, returns the auth token`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"authToken":"hms-token-123"}"""))

        val result = repository.videoJoinToken("booking-1")

        assertEquals("hms-token-123", result.getOrThrow())
        val request = server.takeRequest()
        assertEquals("Bearer google-id-token", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("join_video", body.getString("action"))
        assertEquals("booking-1", body.getString("consultationId"))
    }

    @Test
    fun `a refusal keeps the backend's message so the screen can show it`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(422)
                .setBody("""{"error":{"code":"join_not_yet","message":"You can join from 5 minutes before the consultation starts."}}""")
        )

        val error = repository.videoJoinToken("booking-1").exceptionOrNull()

        assertTrue(error is ConsultationHttpException)
        error as ConsultationHttpException
        assertEquals(422, error.statusCode)
        assertEquals("join_not_yet", error.code)
        assertEquals("You can join from 5 minutes before the consultation starts.", error.serverMessage)
    }

    @Test
    fun `preview repository never pretends to have a room`() = runBlocking {
        assertTrue(MockExpertRepository().videoJoinToken("preview-booking-1").isFailure)
    }
}
