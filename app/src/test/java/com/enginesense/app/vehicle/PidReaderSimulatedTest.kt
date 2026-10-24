package com.enginesense.app.vehicle

import com.enginesense.app.obd.DtcDecoder
import com.enginesense.app.obd.ObdRawResult
import com.enginesense.app.obd.ObdResponseParser
import com.enginesense.app.obd.SimulatedObdTransport
import com.enginesense.app.obd.SimulatedProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PidReaderSimulatedTest {

    @Test
    fun `healthy profile reports verified readings for all supported PIDs`() {
        val transport = SimulatedObdTransport(SimulatedProfile.HEALTHY)
        transport.connect()
        val reader = PidReader(transport)

        val coolant = reader.read(PidId.COOLANT_TEMP_C)
        assertEquals(PidStatus.VERIFIED, coolant.status)
        assertEquals(90.0, coolant.value)

        val rpm = reader.read(PidId.RPM)
        assertEquals(PidStatus.VERIFIED, rpm.status)
        assertEquals(1726.0, rpm.value)
    }

    @Test
    fun `overheating profile is verified but out of normal range`() {
        val transport = SimulatedObdTransport(SimulatedProfile.OVERHEATING)
        transport.connect()
        val reading = PidReader(transport).read(PidId.COOLANT_TEMP_C)

        assertEquals(PidStatus.VERIFIED, reading.status) // 115C is still within the sensor's plausible range
        assertEquals(115.0, reading.value)
    }

    @Test
    fun `unsupported PID is reported as UNSUPPORTED, not a null verified reading`() {
        val transport = SimulatedObdTransport(SimulatedProfile.VOLTAGE_PID_UNSUPPORTED)
        transport.connect()
        val reading = PidReader(transport).read(PidId.CONTROL_MODULE_VOLTAGE)

        assertEquals(PidStatus.UNSUPPORTED, reading.status)
        assertNull(reading.value)
    }

    @Test
    fun `supported voltage PID reads low battery value`() {
        val transport = SimulatedObdTransport(SimulatedProfile.LOW_BATTERY_VOLTAGE)
        transport.connect()
        val reading = PidReader(transport).read(PidId.CONTROL_MODULE_VOLTAGE)

        assertEquals(PidStatus.VERIFIED, reading.status)
        assertEquals(10.8, reading.value!!, 0.001)
    }

    @Test
    fun `stored DTC round-trips through the simulator and the shared decoder`() {
        val transport = SimulatedObdTransport(SimulatedProfile.MISFIRE_DTC)
        transport.connect()
        val raw = transport.sendCommand("03")

        val parsed = ObdResponseParser.parse(raw, expectedModeEcho = "43")
        assertTrue(parsed is ObdRawResult.Ok)
        val codes = DtcDecoder.decode((parsed as ObdRawResult.Ok).bytes)

        assertEquals(listOf("P0301"), codes)
    }
}
