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
    fun `fromJson on an object missing keys entirely defaults to blank strings, not a crash`() {
        val roundTripped = dealerProfileFromJson(org.json.JSONObject("{}"))

        assertEquals(DealerProfile("", "", "", ""), roundTripped)
    }
}
