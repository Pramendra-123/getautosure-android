package com.enginesense.app.storage

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for org.json.*, same reason as the rest of this suite. */
@RunWith(RobolectricTestRunner::class)
class DealerProfileStoreTest {

    @Test
    fun `a full profile round-trips through JSON unchanged`() {
        val profile = DealerProfile(
            name = "AutoTrust Motors",
            tagline = "Certified Pre-Owned Dealer",
            phone = "+91 98765 43210",
            location = "Andheri West, Mumbai"
        )

        val roundTripped = dealerProfileFromJson(dealerProfileToJson(profile))

        assertEquals(profile, roundTripped)
    }

    @Test
    fun `blank fields round-trip as blank, not crashing on missing keys`() {
        val profile = DealerProfile(name = "", tagline = "", phone = "", location = "")

        val roundTripped = dealerProfileFromJson(dealerProfileToJson(profile))

        assertEquals(profile, roundTripped)
    }

    @Test
    fun `the second phone and the showroom's map point round-trip too`() {
        val profile = DealerProfile(
            name = "Sharma Motors", tagline = "", phone = "98450 12345", location = "Koramangala, Bengaluru",
            phone2 = "98450 67890", latitude = 12.9352, longitude = 77.6245
        )

        assertEquals(profile, dealerProfileFromJson(dealerProfileToJson(profile)))
    }

    @Test
    fun `a profile saved before phone2 and location existed still loads`() {
        val old = org.json.JSONObject("""{"name":"AutoTrust","tagline":"","phone":"1","location":"Pune"}""")

        val loaded = dealerProfileFromJson(old)

        assertEquals("", loaded.phone2)
        assertEquals(null, loaded.latitude)
        assertEquals(listOf("1"), loaded.phones)
    }

    @Test
    fun `the map link uses the saved point, else searches the typed address, else is absent`() {
        assertEquals("https://maps.google.com/?q=12.9352,77.6245", DealerProfile("a", "", "", "x", latitude = 12.9352, longitude = 77.6245).mapsUrl)
        assertEquals("https://maps.google.com/?q=Andheri+West%2C+Mumbai", DealerProfile("a", "", "", "Andheri West, Mumbai").mapsUrl)
        assertEquals(null, DealerProfile("a", "", "", " ").mapsUrl)
    }

    @Test
    fun `fromJson on an object missing keys entirely defaults to blank strings, not a crash`() {
        val roundTripped = dealerProfileFromJson(org.json.JSONObject("{}"))

        assertEquals(DealerProfile("", "", "", ""), roundTripped)
    }
}
