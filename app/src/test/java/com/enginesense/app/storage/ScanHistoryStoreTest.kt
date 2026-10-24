package com.enginesense.app.storage

import com.enginesense.app.obd.VehicleIdInfo
import com.enginesense.app.vehicle.DtcReading
import com.enginesense.app.vehicle.DtcSource
import com.enginesense.app.vehicle.IgnitionType
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.PidReading
import com.enginesense.app.vehicle.PidStatus
import com.enginesense.app.vehicle.ReadinessMonitor
import com.enginesense.app.vehicle.ReadinessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanHistoryStoreTest {

    @Test
    fun `a full scan round-trips through JSON unchanged`() {
        val scan = PersistedScan(
            timestampMillis = 1_700_000_000_000L,
            profileLabel = "HEALTHY",
            isDemo = false,
            vehicleBrand = "Skoda",
            readings = mapOf(
                PidId.RPM to PidReading(PidId.RPM, PidStatus.VERIFIED, 1726.0, listOf(0x1A, 0xF8)),
                PidId.COOLANT_TEMP_C to PidReading(PidId.COOLANT_TEMP_C, PidStatus.UNSUPPORTED, null, null)
            ),
            dtcs = listOf(DtcReading("P0301", DtcSource.STORED)),
            vehicleId = VehicleIdInfo(vin = "DEMO1234567890VIN", ecuName = "ENGINE ECU", calibrationId = "SW1.0.4"),
            readiness = ReadinessStatus(
                milOn = true, dtcCount = 1, ignitionType = IgnitionType.SPARK,
                monitors = listOf(ReadinessMonitor("Catalyst", supported = true, ready = false))
            )
        )

        val roundTripped = scanFromMap(scanToMap(scan))

        assertEquals(scan, roundTripped)
    }

    @Test
    fun `null vehicle info and readiness round-trip as null, not placeholder values`() {
        val scan = PersistedScan(
            timestampMillis = 1L, profileLabel = "Real dongle", isDemo = false, vehicleBrand = null,
            readings = emptyMap(), dtcs = emptyList(),
            vehicleId = VehicleIdInfo(vin = null, ecuName = null, calibrationId = null),
            readiness = null
        )

        val roundTripped = scanFromMap(scanToMap(scan))

        assertNull(roundTripped.vehicleBrand)
        assertNull(roundTripped.vehicleId.vin)
        assertNull(roundTripped.readiness)
    }

    @Test
    fun `a reading with no raw bytes round-trips with a null rawBytes list, not an empty one`() {
        val scan = PersistedScan(
            timestampMillis = 1L, profileLabel = "demo", isDemo = true, vehicleBrand = null,
            readings = mapOf(PidId.RPM to PidReading(PidId.RPM, PidStatus.SUPPORTED_NO_DATA, null, null)),
            dtcs = emptyList(), vehicleId = VehicleIdInfo(null, null, null), readiness = null
        )

        val roundTripped = scanFromMap(scanToMap(scan))

        assertNull(roundTripped.readings.getValue(PidId.RPM).rawBytes)
    }
}
