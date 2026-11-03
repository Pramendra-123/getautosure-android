package com.enginesense.app.storage

import com.enginesense.app.vehiclelookup.RcLookupResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for org.json.*, same reason as the rest of this suite. */
@RunWith(RobolectricTestRunner::class)
class VehicleProfileStoreTest {

    private val rc = RcLookupResult(
        registrationNumber = "DL3CAB1234", make = "HYUNDAI MOTOR INDIA LTD", model = "VENUE", fuelType = "PETROL",
        color = "WHITE", category = "LMV", manufacturingMonth = "2021-06", registrationDate = "2021-07-02",
        registeredAt = "DELHI", cubicCapacity = "1197.00", insuranceCompany = "ACKO", insuranceUpto = "2027-07-01",
        puccUpto = "2026-12-31", fitnessUpto = "2036-07-01", taxUpto = null, partial = false, source = "RTO records",
        chassisMatch = true, ownerCount = 1, financed = false, cylinders = 4
    )

    @Test
    fun `a full car with its RTO record round-trips through JSON unchanged`() {
        val profile = VehicleProfile(
            brand = "Hyundai", nickname = "Family car", model = "Venue", year = 2021, fuelType = "Petrol",
            odometerKm = 42_000, plate = "DL3CAB1234", engineCc = 1197, claimedMileageKmPerLitre = 17.5, rc = rc
        )

        assertEquals(profile, vehicleProfileFromJson(vehicleProfileToJson(profile)))
    }

    @Test
    fun `a car entered by hand with nothing optional round-trips with nulls, not the string null`() {
        val profile = VehicleProfile(
            brand = null, nickname = "", model = "", year = null, fuelType = null, odometerKm = null,
            plate = null, engineCc = null, claimedMileageKmPerLitre = null, rc = null
        )

        assertEquals(profile, vehicleProfileFromJson(vehicleProfileToJson(profile)))
    }

    @Test
    fun `an unreadable RTO record is dropped without losing the car`() {
        val json = vehicleProfileToJson(
            VehicleProfile("Tata", "", "Nexon", 2022, "Diesel", null, "MH12AB1234", null, null, null)
        ).put("rc", JSONObject().put("unexpected", true))

        val loaded = vehicleProfileFromJson(json)

        assertEquals("Nexon", loaded.model)
        assertNull(loaded.rc)
    }
}
