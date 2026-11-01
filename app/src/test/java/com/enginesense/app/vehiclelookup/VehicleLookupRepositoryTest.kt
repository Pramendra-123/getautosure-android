package com.enginesense.app.vehiclelookup

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * VehicleLookupRepository against a real local HTTP server, same approach as
 * RemoteAiMechanicChatServiceTest — Robolectric because parsing goes through org.json.
 */
@RunWith(RobolectricTestRunner::class)
class VehicleLookupRepositoryTest {

    private lateinit var server: MockWebServer
    private val fixedToken: suspend () -> String = { "test-id-token" }

    // Shape copied from the deployed function's customer-mode response; values are made up.
    private val customerResponse = """
        {"mode":"customer","source":"RTO (VAHAN) records via Surepass RC V2","fetchedAt":"2026-10-27T15:00:00Z",
         "cached":false,"partial":true,
         "vehicle":{"registrationNumber":"MH12AB1234",
           "vehicle":{"make":"HYUNDAI MOTOR INDIA LTD","model":"CRETA 1.6 SX","fuelType":"DIESEL","color":"WHITE",
             "bodyType":null,"category":"Motor Car(LMV)","manufacturingMonth":"2019-03","registrationDate":"2019-04-12",
             "registeredAt":"PUNE, Maharashtra"},
           "validity":{"insuranceCompany":"Test Insurance Co","insuranceUpto":"2027-01-15","puccUpto":null,
             "fitnessUpto":"2034-04-11","taxUpto":"2034-04-11"}}}
    """.trimIndent()

    // Shape copied from the function's dealer-mode shapeRcData; values are made up.
    private val dealerResponse = """
        {"mode":"dealer","source":"RTO (VAHAN) records via Surepass RC V2","fetchedAt":"2026-10-27T15:00:00Z",
         "cached":true,"partial":false,"chassisMatch":true,
         "vehicle":{"registrationNumber":"MH12AB1234",
           "vehicle":{"make":"MARUTI SUZUKI","model":"SWIFT VXI","fuelType":"PETROL","bodyType":"HATCHBACK"},
           "validity":{},
           "ownership":{"ownerName":"SOMEONE","ownerCount":2,"financed":true,"financer":"HDFC BANK"},
           "compliance":{"rcStatus":"ACTIVE","blacklistStatus":null,"nocDetails":null,"nonUseStatus":null},
           "identifiers":{"chassisNumber":"MA3EWDE1S00123456","engineNumber":"K12MN1234567"},
           "technical":{"emissionNorms":"BHARAT STAGE IV","cylinders":4,"seatCapacity":5},
           "registration":{"rtoCode":"MH12","recordUpdatedOn":"2026-09-01"}}}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repo() = VehicleLookupRepository(endpointUrl = server.url("/vehicle-lookup").toString(), idTokenProvider = fixedToken)

    @Test
    fun `a dealer-mode response carries ownership, loan and identifiers but never the owner's name`() = runTest {
        server.enqueue(MockResponse().setBody(dealerResponse))

        val result = repo().lookup("MH12AB1234", mode = "dealer").getOrThrow()

        assertTrue(result.hasDealerDetails)
        assertEquals(2, result.ownerCount)
        assertEquals(true, result.financed)
        assertEquals("HDFC BANK", result.financer)
        assertEquals("ACTIVE", result.rcStatus)
        assertNull(result.blacklistStatus)
        assertEquals("MA3EWDE1S00123456", result.chassisNumber)
        assertEquals("BHARAT STAGE IV", result.emissionNorms)
        assertEquals(4, result.cylinders)
        assertEquals("HATCHBACK", result.bodyType)
        assertEquals("MH12", result.rtoCode)
        assertTrue(JSONObject(server.takeRequest().body.readUtf8()).getString("mode") == "dealer")
        assertTrue(result.toString().contains("SOMEONE").not())
    }

    @Test
    fun `lookupBest falls back to the basic record when dealer mode is refused`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"code":"forbidden","message":"no"}}"""))
        server.enqueue(MockResponse().setBody(customerResponse))

        val result = repo().lookupBest("MH12AB1234", vin = null, preferDealer = true).getOrThrow()

        assertEquals("customer", result.mode)
        assertEquals("dealer", JSONObject(server.takeRequest().body.readUtf8()).getString("mode"))
        assertEquals("customer", JSONObject(server.takeRequest().body.readUtf8()).getString("mode"))
    }

    @Test
    fun `a customer-mode response is parsed, nulls staying null`() = runTest {
        server.enqueue(MockResponse().setBody(customerResponse))

        val result = repo().lookup("MH12AB1234").getOrThrow()

        assertEquals("HYUNDAI MOTOR INDIA LTD", result.make)
        assertEquals("CRETA 1.6 SX", result.model)
        assertEquals("2019-03", result.manufacturingMonth)
        assertEquals("2027-01-15", result.insuranceUpto)
        assertNull(result.puccUpto) // JSON null, not the string "null"
        assertTrue(result.partial)
    }

    @Test
    fun `the request asks for customer mode with the sign-in token`() = runTest {
        server.enqueue(MockResponse().setBody(customerResponse))

        repo().lookup("MH12AB1234")

        val recorded = server.takeRequest()
        assertEquals("Bearer test-id-token", recorded.getHeader("Authorization"))
        val sent = JSONObject(recorded.body.readUtf8())
        assertEquals("MH12AB1234", sent.getString("registrationNumber"))
        assertEquals("customer", sent.getString("mode"))
    }

    @Test
    fun `a VIN is sent only when given, and the chassis match comes back three-way`() = runTest {
        server.enqueue(MockResponse().setBody(customerResponse.replaceFirst("\"partial\":true", "\"partial\":true,\"chassisMatch\":false")))
        val mismatch = repo().lookup("MH12AB1234", vin = "XX9SAMPLEK0123456").getOrThrow()
        assertEquals("XX9SAMPLEK0123456", JSONObject(server.takeRequest().body.readUtf8()).getString("vin"))
        assertEquals(false, mismatch.chassisMatch)

        server.enqueue(MockResponse().setBody(customerResponse.replaceFirst("\"partial\":true", "\"partial\":true,\"chassisMatch\":null")))
        val unknown = repo().lookup("MH12AB1234").getOrThrow()
        assertTrue(!JSONObject(server.takeRequest().body.readUtf8()).has("vin"))
        assertNull(unknown.chassisMatch)

        // An older deployment without the field at all reads as "couldn't compare".
        server.enqueue(MockResponse().setBody(customerResponse))
        assertNull(repo().lookup("MH12AB1234").getOrThrow().chassisMatch)
    }

    @Test
    fun `RTO manufacturing month reads as month and year`() {
        assertEquals("Mar 2019", PurchaseRcCheckViewModel.formatMonth("2019-03"))
        assertEquals("11/1998", PurchaseRcCheckViewModel.formatMonth("11/1998")) // unknown format shown as-is
        assertNull(PurchaseRcCheckViewModel.formatMonth(null))
    }

    @Test
    fun `server errors keep their code and people-facing message`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(422)
                .setBody("""{"error":{"code":"lookup_failed","message":"No RTO record found for that registration number."}}""")
        )

        val error = repo().lookup("MH12AB1234").exceptionOrNull() as VehicleLookupHttpException

        assertEquals(422, error.statusCode)
        assertEquals("lookup_failed", error.code)
        assertEquals("No RTO record found for that registration number.", VehicleLookupViewModel.messageFor(error))
    }

    @Test
    fun `no sign-in surfaces as not-authenticated, without calling the server`() = runTest {
        val repo = VehicleLookupRepository(
            endpointUrl = server.url("/vehicle-lookup").toString(),
            idTokenProvider = { throw IllegalStateException("not signed in") }
        )

        val error = repo.lookup("MH12AB1234").exceptionOrNull()

        assertTrue(error is VehicleLookupNotAuthenticatedException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an unexpected server failure is not shown to the customer as raw HTTP`() {
        val message = VehicleLookupViewModel.messageFor(VehicleLookupHttpException(502, "upstream_auth", "Vehicle lookup is temporarily unavailable."))
        assertTrue(message.contains("enter the details yourself"))
    }

    @Test
    fun `a body that isn't the expected shape is a parse error, not a crash`() = runTest {
        server.enqueue(MockResponse().setBody("""{"unexpected":true}"""))

        assertTrue(repo().lookup("MH12AB1234").exceptionOrNull() is VehicleLookupParseException)
    }
}
