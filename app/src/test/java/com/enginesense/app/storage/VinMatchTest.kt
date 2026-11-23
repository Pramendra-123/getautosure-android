package com.enginesense.app.storage

import com.enginesense.app.vehicle.ai.ChatMessage
import com.enginesense.app.vehiclelookup.RcLookupResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VinMatchTest {

    private val slaviaVin = "MEXBPAPB6RG010139"

    private fun car(brand: String?, chassis: String? = null) = GarageCar(
        id = "c", vin = null,
        profile = VehicleProfile(brand, "", "", null, null, null, null, null, null,
            chassis?.let { RcLookupResult(
                registrationNumber = "KA01", make = null, model = null, fuelType = null, color = null, category = null,
                manufacturingMonth = null, registrationDate = null, registeredAt = null, cubicCapacity = null,
                insuranceCompany = null, insuranceUpto = null, puccUpto = null, fitnessUpto = null, taxUpto = null,
                partial = false, source = "test", chassisNumber = it
            ) })
    )

    @Test
    fun `a Kia can't take a Skoda's VIN`() {
        assertEquals(false, VinMatch.fits(car("Kia"), slaviaVin))
    }

    @Test
    fun `the right maker fits, Skoda and Volkswagen share a code`() {
        assertEquals(true, VinMatch.fits(car("Skoda"), slaviaVin))
        assertEquals(true, VinMatch.fits(car("Volkswagen"), slaviaVin))
    }

    @Test
    fun `the RTO chassis number decides when it's there`() {
        assertEquals(true, VinMatch.fits(car("Skoda", "MEXBPAPB6RG0*****"), slaviaVin))
        assertEquals(false, VinMatch.fits(car("Skoda", "MEXBPAPB6RG9*****"), slaviaVin))
    }

    @Test
    fun `an unknown maker code or no brand never blocks`() {
        assertNull(VinMatch.fits(car("Kia"), "ZZZ12345678901234"))
        assertNull(VinMatch.fits(car(null), slaviaVin))
    }

    @Test
    fun `a chat survives saving and loading`() {
        val chat = listOf(ChatMessage(true, "Is it safe?"), ChatMessage(false, "Yes — ₹1,000–4,000 if it's the plugs."))
        assertEquals(chat, ChatStore.fromJson(ChatStore.toJson(chat)))
    }
}
