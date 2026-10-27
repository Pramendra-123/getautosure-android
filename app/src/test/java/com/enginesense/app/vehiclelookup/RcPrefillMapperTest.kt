package com.enginesense.app.vehiclelookup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure mapping logic — no org.json, so plain JUnit, no Robolectric. */
class RcPrefillMapperTest {

    @Test
    fun `plates are normalized and validated like the Edge Function does`() {
        assertEquals("MH12AB1234", RcPrefillMapper.normalizePlate("mh-12 ab.1234"))
        listOf("MH12AB1234", "DL3CAB1234", "KA011234", "22BH1234AA").forEach {
            assertTrue(it, RcPrefillMapper.isValidPlate(it))
        }
        listOf("TEST0000", "1234", "MH12ABCD1234", "22BH12AA").forEach {
            assertFalse(it, RcPrefillMapper.isValidPlate(it))
        }
    }

    @Test
    fun `RTO manufacturer names map to the app's makes`() {
        assertEquals("Maruti Suzuki", RcPrefillMapper.matchMake("MARUTI SUZUKI INDIA LTD", "SWIFT VXI"))
        assertEquals("Hyundai", RcPrefillMapper.matchMake("HYUNDAI MOTOR INDIA LTD", "CRETA 1.6 SX"))
        assertEquals("Mahindra", RcPrefillMapper.matchMake("MAHINDRA & MAHINDRA LIMITED", "XUV700 AX7"))
        assertEquals("MG Motor", RcPrefillMapper.matchMake("MG MOTOR INDIA PVT LTD", "HECTOR SHARP"))
    }

    @Test
    fun `a manufacturer that builds several brands is resolved by the model`() {
        assertEquals("Volkswagen", RcPrefillMapper.matchMake("SKODA AUTO VOLKSWAGEN INDIA PVT LTD", "VIRTUS GT"))
        assertEquals("Skoda", RcPrefillMapper.matchMake("SKODA AUTO VOLKSWAGEN INDIA PVT LTD", "SLAVIA STYLE"))
        assertEquals("Land Rover", RcPrefillMapper.matchMake("JAGUAR LAND ROVER INDIA LTD", "RANGE ROVER EVOQUE"))
        assertEquals("Jaguar", RcPrefillMapper.matchMake("JAGUAR LAND ROVER INDIA LTD", "F-PACE R"))
        assertEquals("Datsun", RcPrefillMapper.matchMake("NISSAN MOTOR INDIA PVT LTD", "GO PLUS T"))
        assertEquals("Nissan", RcPrefillMapper.matchMake("NISSAN MOTOR INDIA PVT LTD", "MAGNITE XV"))
    }

    @Test
    fun `an unknown manufacturer is never matched by model name alone`() {
        // Real sandbox record: a Bajaj two-wheeler. "BAJAJ" isn't a make the app lists.
        assertNull(RcPrefillMapper.matchMake("BAJAJ AUTO LTD", "CALIBER"))
        assertNull(RcPrefillMapper.matchMake("SOME OTHER MOTORS", "CITY"))
        assertNull(RcPrefillMapper.matchMake(null, "CITY"))
    }

    @Test
    fun `models match the app's list, longest name first`() {
        assertEquals("Creta", RcPrefillMapper.matchModel("Hyundai", "CRETA 1.6 VTVT SX"))
        assertEquals("Grand i10 Nios", RcPrefillMapper.matchModel("Hyundai", "GRAND I10 NIOS SPORTZ"))
        assertEquals("Scorpio-N", RcPrefillMapper.matchModel("Mahindra", "SCORPIO-N Z8"))
        assertEquals("Urban Cruiser Hyryder", RcPrefillMapper.matchModel("Toyota", "URBAN CRUISER HYRYDER V"))
        assertNull(RcPrefillMapper.matchModel("Hyundai", "SANTRO XING"))
        assertNull(RcPrefillMapper.matchModel("Hyundai", null))
    }

    @Test
    fun `fuel maps to the details screen's list, and anything else is left for the user`() {
        assertEquals("Petrol", RcPrefillMapper.matchFuel("PETROL"))
        assertEquals("Diesel", RcPrefillMapper.matchFuel("DIESEL"))
        assertEquals("CNG", RcPrefillMapper.matchFuel("PETROL/CNG"))
        assertEquals("Electric", RcPrefillMapper.matchFuel("ELECTRIC(BOV)"))
        assertEquals("Hybrid", RcPrefillMapper.matchFuel("PETROL/HYBRID"))
        assertNull(RcPrefillMapper.matchFuel("LPG"))
        assertNull(RcPrefillMapper.matchFuel(null))
    }

    @Test
    fun `year comes from the manufacturing month, else the registration date`() {
        assertEquals(1998, RcPrefillMapper.matchYear("1998-11", "1998-11-12"))
        assertEquals(2019, RcPrefillMapper.matchYear(null, "2019-04-12"))
        assertNull(RcPrefillMapper.matchYear(null, null))
        assertNull(RcPrefillMapper.matchYear("not a date", null))
    }

    @Test
    fun `engine size is read from the RTO's cubic capacity and implausible values are dropped`() {
        assertEquals(999, RcPrefillMapper.matchEngineCc("999.00"))
        assertEquals(1197, RcPrefillMapper.matchEngineCc("1197"))
        assertNull(RcPrefillMapper.matchEngineCc(""))
        assertNull(RcPrefillMapper.matchEngineCc("149.5")) // a scooter, not a car engine
        assertNull(RcPrefillMapper.matchEngineCc(null))
        assertEquals(999, RcPrefill.from(result(make = "SKODA AUTO VOLKSWAGEN INDIA PVT LTD", model = "SLAVIA 1.0 TSI"))!!.engineCc)
    }

    @Test
    fun `two- and three-wheelers are flagged, cars are not`() {
        assertTrue(RcPrefillMapper.isLikelyNotACar("M-Cycle/Scooter"))
        assertTrue(RcPrefillMapper.isLikelyNotACar("e-Rickshaw(P)"))
        assertFalse(RcPrefillMapper.isLikelyNotACar("Motor Car(LMV)"))
        assertFalse(RcPrefillMapper.isLikelyNotACar(null))
    }

    @Test
    fun `a car the app knows becomes a clean prefill`() {
        val prefill = RcPrefill.from(result(make = "HYUNDAI MOTOR INDIA LTD", model = "CRETA 1.6 SX", fuel = "DIESEL", month = "2019-03"))!!
        assertEquals("Hyundai", prefill.brand)
        assertEquals("Creta", prefill.model)
        assertEquals(2019, prefill.year)
        assertEquals("Diesel", prefill.fuelType)
        assertEquals("MH12AB1234", prefill.registrationNumber)
        assertFalse(prefill.looksLikeNonCar)
    }

    @Test
    fun `an unknown make or model keeps the RTO's own words rather than a guess`() {
        val prefill = RcPrefill.from(result(make = "BAJAJ AUTO LTD", model = "CALIBER", category = "M-Cycle/Scooter"))!!
        assertEquals("Bajaj Auto Ltd", prefill.brand)
        assertEquals("Caliber", prefill.model)
        assertTrue(prefill.looksLikeNonCar)
    }

    @Test
    fun `a record with no make or model can't be a prefill`() {
        assertNull(RcPrefill.from(result(make = null, model = "CRETA")))
        assertNull(RcPrefill.from(result(make = "HYUNDAI MOTOR INDIA LTD", model = null)))
    }

    private fun result(
        make: String?,
        model: String?,
        fuel: String? = "PETROL",
        month: String? = null,
        category: String? = "Motor Car(LMV)",
    ) = RcLookupResult(
        registrationNumber = "MH12AB1234", make = make, model = model, fuelType = fuel, color = null,
        category = category, manufacturingMonth = month, registrationDate = null, registeredAt = null, cubicCapacity = "999.00",
        insuranceCompany = null, insuranceUpto = null, puccUpto = null, fitnessUpto = null, taxUpto = null,
        partial = false, source = "RTO records",
    )
}
