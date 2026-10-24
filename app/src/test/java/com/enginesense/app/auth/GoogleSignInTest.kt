package com.enginesense.app.auth

import android.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for the same reason as the rest of the suite: android.util.Base64 and org.json.* aren't available under a plain JVM unit test. */
@RunWith(RobolectricTestRunner::class)
class GoogleSignInTest {

    private fun fakeJwt(payloadJson: String): String {
        val header = Base64.encodeToString("{}".toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val payload = Base64.encodeToString(payloadJson.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        return "$header.$payload.fake-signature"
    }

    @Test
    fun `reads a real-looking exp claim`() {
        val jwt = fakeJwt(JSONObject().apply { put("exp", 1792268932L) }.toString())
        assertEquals(1792268932L, decodeJwtExpiry(jwt))
    }

    @Test
    fun `a token with no dots at all returns null`() {
        assertNull(decodeJwtExpiry("not-a-jwt"))
    }

    @Test
    fun `a token missing the payload segment returns null`() {
        assertNull(decodeJwtExpiry("onlyonesegment"))
    }

    @Test
    fun `a payload that isn't valid base64 returns null`() {
        assertNull(decodeJwtExpiry("header.!!!not-base64!!!.sig"))
    }

    @Test
    fun `a payload that decodes but isn't valid JSON returns null`() {
        val header = Base64.encodeToString("{}".toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val payload = Base64.encodeToString("not json".toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        assertNull(decodeJwtExpiry("$header.$payload.sig"))
    }

    @Test
    fun `valid JSON with no exp claim returns null`() {
        val jwt = fakeJwt(JSONObject().apply { put("sub", "12345") }.toString())
        assertNull(decodeJwtExpiry(jwt))
    }

    @Test
    fun `a zero or negative exp is treated as missing`() {
        assertNull(decodeJwtExpiry(fakeJwt(JSONObject().apply { put("exp", 0L) }.toString())))
        assertNull(decodeJwtExpiry(fakeJwt(JSONObject().apply { put("exp", -5L) }.toString())))
    }
}
