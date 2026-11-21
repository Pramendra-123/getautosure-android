package com.enginesense.app.dealerreport

import com.enginesense.app.ui.screens.matches
import com.enginesense.app.ui.screens.plateShort
import kotlinx.coroutines.test.runTest
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

/** "My reports": the server calls and the list's helpers. Robolectric because requests and parsing go through org.json. */
@RunWith(RobolectricTestRunner::class)
class MyReportsTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: DealerReportRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = DealerReportRepository(endpointUrl = server.url("/reports").toString(), idTokenProvider = { "google-id-token" })
    }

    @After
    fun tearDown() = server.shutdown()

    private val listBody = """
        {"reports":[
          {"slug":"GAS-D34052","url":"https://getautosure.com/GAS-D34052","live":true,"make":"Kia","model":"Seltos","variant":"GTX PLUS","year":2020,
           "registrationNo":"KA03NG8216","priceInr":1250000,"photoUrl":"https://x/0.jpg","createdAt":"2026-11-07T18:04:56.746102+00:00","soldAt":null,
           "pdfUrl":"https://x/pdf-1.pdf","statusUrl":"https://x/status-1.png"},
          {"slug":"GAS-16A577","url":"https://getautosure.com/GAS-16A577","live":true,"make":"Toyota","model":"Corolla","variant":null,"year":2018,
           "registrationNo":null,"priceInr":null,"photoUrl":null,"createdAt":"2026-11-06T03:16:55+00:00","soldAt":"2026-11-07T10:00:00+00:00",
           "pdfUrl":null,"statusUrl":null}
        ],"made":2,"thisMonth":2,"credits":0,"paymentsLive":false}
    """.trimIndent()

    @Test
    fun `list_reports reads every report and the counts`() = runTest {
        server.enqueue(MockResponse().setBody(listBody))
        val data = repository.listReports().getOrThrow()

        val request = server.takeRequest()
        assertEquals("Bearer google-id-token", request.getHeader("Authorization"))
        assertEquals("list_reports", JSONObject(request.body.readUtf8()).getString("action"))

        assertEquals(2, data.made)
        assertEquals(0, data.credits)
        assertFalse(data.paymentsLive)
        val seltos = data.reports[0]
        assertEquals("2020 Kia Seltos GTX PLUS", seltos.title)
        assertEquals(1_250_000.0, seltos.priceInr!!, 0.0)
        assertEquals("https://x/pdf-1.pdf", seltos.pdfUrl)
        assertFalse(seltos.sold)
        // Older report: nothing saved, no price — nulls, not "null" strings.
        val corolla = data.reports[1]
        assertNull(corolla.variant)
        assertNull(corolla.priceInr)
        assertNull(corolla.pdfUrl)
        assertTrue(corolla.sold)
        assertEquals("2018 Toyota Corolla", corolla.title)
    }

    @Test
    fun `save_outputs sends both files`() = runTest {
        server.enqueue(MockResponse().setBody("""{"slug":"GAS-D34052","saved":true}"""))
        repository.saveOutputs("GAS-D34052", byteArrayOf(1, 2, 3), byteArrayOf(4, 5)).getOrThrow()

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("save_outputs", body.getString("action"))
        assertEquals("GAS-D34052", body.getString("slug"))
        assertEquals("AQID", body.getJSONObject("pdf").getString("base64"))
        assertEquals("image/png", body.getJSONObject("status").getString("contentType"))
    }

    @Test
    fun `out of credits comes back as its own error code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":{"code":"no_credits","message":"You've used all your report credits."}}"""))
        val error = repository.setSold("GAS-D34052", true).exceptionOrNull()
        assertEquals("no_credits", (error as DealerReportHttpException).code)
        assertEquals(402, error.statusCode)
    }

    @Test
    fun `search matches the car or the number plate, ignoring spaces and case`() {
        val report = parseMyReports(JSONObject(listBody)).reports[0]
        assertTrue(matches(report, ""))
        assertTrue(matches(report, "seltos"))
        assertTrue(matches(report, "ka03 ng"))
        assertTrue(matches(report, "8216"))
        assertFalse(matches(report, "creta"))
    }

    @Test
    fun `number plate is shortened for the list`() {
        assertEquals("KA03 •• 8216", plateShort("KA03NG8216"))
        assertEquals("KA03 •• 8216", plateShort("ka 03 ng 8216"))
        assertEquals("DL1C", plateShort("DL1C"))
    }
}
