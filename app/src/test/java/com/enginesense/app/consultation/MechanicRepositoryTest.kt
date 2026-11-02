package com.enginesense.app.consultation

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Mechanic mode's calls against a local server. Robolectric because requests and parsing go through org.json. */
@RunWith(RobolectricTestRunner::class)
class MechanicRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: MechanicRepository

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

    private val scheduleJson = """
        {"hours":[{"weekday":1,"start":"10:00","end":"13:00"},{"weekday":1,"start":"17:00","end":"20:00"}],
         "leave":[{"id":"leave-1","startDate":"2026-11-12","endDate":"2026-11-13"}]}
    """.trimIndent()

    @Test
    fun `saving hours sends every block and reads back the saved schedule`() = runBlocking {
        server.enqueue(MockResponse().setBody(scheduleJson))

        val saved = repository.setWeeklyHours(listOf(WeeklyHours(1, "10:00", "13:00"), WeeklyHours(1, "17:00", "20:00")))

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("expert_set_weekly_hours", body.getString("action"))
        val hours = body.getJSONArray("hours")
        assertEquals(2, hours.length())
        assertEquals("17:00", hours.getJSONObject(1).getString("start"))
        assertEquals(listOf(WeeklyHours(1, "10:00", "13:00"), WeeklyHours(1, "17:00", "20:00")), saved.hours)
        assertEquals(listOf(Leave("leave-1", "2026-11-12", "2026-11-13")), saved.leave)
    }

    @Test
    fun `adding leave sends the two dates`() = runBlocking {
        server.enqueue(MockResponse().setBody(scheduleJson))

        repository.addLeave("2026-11-12", "2026-11-13")

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("expert_add_leave", body.getString("action"))
        assertEquals("2026-11-12", body.getString("startDate"))
        assertEquals("2026-11-13", body.getString("endDate"))
    }

    @Test
    fun `bookings carry the car summary, or none when no scan was attached`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {"bookings":[
                  {"id":"c1","bookingReference":"GAS-1","slotStartEpochMillis":1000,"slotEndEpochMillis":2000,
                   "car":{"brand":"Maruti","checkEngineOn":true,"issues":["Engine misfire (P0301)"]}},
                  {"id":"c2","bookingReference":"GAS-2","slotStartEpochMillis":3000,"slotEndEpochMillis":4000,"car":null}
                ]}
                """.trimIndent()
            )
        )

        val bookings = repository.bookings()

        assertEquals(CarSummary("Maruti", true, listOf("Engine misfire (P0301)")), bookings[0].car)
        assertNull(bookings[1].car)
    }

    @Test
    fun `an unlinked account is recognised so the screen can explain it`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"error":{"code":"not_a_mechanic","message":"someone@gmail.com isn't linked to a GetAutoSure mechanic profile yet."}}""")
        )

        val error = runCatching { repository.me() }.exceptionOrNull()

        assertTrue(error!!.isNotAMechanic())
        assertFalse(ConsultationNetworkException().isNotAMechanic())
    }

    @Test
    fun `the mechanic's join uses its own action`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"authToken":"host-token"}"""))

        assertEquals("host-token", repository.mechanicJoinToken("c1"))
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("expert_join_video", body.getString("action"))
        assertEquals("c1", body.getString("consultationId"))
    }
}
