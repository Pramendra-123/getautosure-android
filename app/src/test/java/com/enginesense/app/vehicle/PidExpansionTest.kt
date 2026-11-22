package com.enginesense.app.vehicle

import com.enginesense.app.obd.Mode09Reader
import com.enginesense.app.obd.ObdFrames
import com.enginesense.app.obd.ObdTransport
import com.enginesense.app.obd.VehicleIdInfo
import com.enginesense.app.storage.PersistedScan
import com.enginesense.app.storage.scanFromMap
import com.enginesense.app.storage.scanToMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PidExpansionTest {

    /** Answers from a fixed script; anything else is "NO DATA", like a real car. */
    private class Scripted(private val answers: Map<String, String>) : ObdTransport {
        val asked = mutableListOf<String>()
        override fun connect() {}
        override fun sendCommand(command: String): String { asked += command; return answers[command] ?: "NO DATA" }
        override fun close() {}
    }

    @Test
    fun newPidsDecodeToSaeFormulas() {
        assertEquals(12.5, PidId.ENGINE_FUEL_RATE.decode(listOf(0x00, 0xFA)), 0.001) // 250 / 20
        assertEquals(20.0, PidId.ETHANOL_PERCENT.decode(listOf(51)), 0.01)
        assertEquals(0.45, PidId.O2_SENSOR_B1S2_VOLTAGE.decode(listOf(90, 0xFF)), 0.001)
        assertEquals(150.0, PidId.COMMANDED_BOOST_PRESSURE.decode(listOf(0x01, 0x12, 0xC0)), 0.001) // 4800 / 32
        assertEquals(2.5, PidId.DPF_DIFFERENTIAL_PRESSURE.decode(listOf(0x01, 0x00, 0xFA)), 0.001)
        assertEquals(560.0, PidId.DPF_INLET_TEMP.decode(listOf(0x17, 0x70)), 0.001) // 6000 / 10 - 40
        assertEquals(2.0, PidId.FUEL_SYSTEM_STATUS.decode(listOf(2)), 0.0)
        assertEquals("0152", PidId.ETHANOL_PERCENT.obdCommand)
    }

    @Test
    fun framesAreReadWhateverTheAdapterLayout() {
        val multi = "014\n0: 49 02 01 31 47 31\n1: 4A 43 35 34 34 34 52\n>"
        assertEquals(listOf(0x49, 0x02, 0x01, 0x31, 0x47, 0x31, 0x4A, 0x43, 0x35, 0x34, 0x34, 0x34, 0x52), ObdFrames.bytes(multi))
        assertEquals(setOf(0x01, 0x20), ObdFrames.supportedFrom(listOf(0x80, 0x00, 0x00, 0x01), 0x00))
    }

    @Test
    fun freezeFrameNamesTheCodeAndTheMoment() {
        val car = Scripted(mapOf("020200" to "42 02 00 03 01", "020C00" to "42 0C 00 2B C0", "020500" to "42 05 00 7B"))
        val frame = FreezeFrameReader.read(car)!!
        assertEquals("P0301", frame.dtc)
        assertEquals(2800.0, frame.readings[PidId.RPM]!!, 0.01)
        assertEquals(83.0, frame.readings[PidId.COOLANT_TEMP_C]!!, 0.01)
        assertFalse("asked but not saved", PidId.SPEED_KMH in frame.readings)
    }

    @Test
    fun noStoredCodeMeansNoFreezeFrameAndNoMoreQuestions() {
        val car = Scripted(mapOf("020200" to "42 02 00 00 00"))
        assertNull(FreezeFrameReader.read(car))
        assertEquals(listOf("020200"), car.asked)
    }

    @Test
    fun selfTestRecordsParseIncludingSignedAndASecondEcu() {
        val raw = "46 A2 0B 24 00 05 00 00 00 FF A2 0C 24 00 02 00 00 00 FF 46 A2 0C 24 00 00 00 00 00 FF"
        val tests = SelfTests.parseRecords(raw, 0xA2)
        assertEquals(3, tests.size)
        assertEquals(5, tests[0].rawValue)
        assertTrue(tests.all { it.passed })
        val signed = SelfTests.parseRecords("46 01 01 8A FF F6 FF 00 00 64", 0x01).single()
        assertEquals(-10, signed.rawValue)
        assertEquals(-1.22, signed.value!!, 0.001)
    }

    @Test
    fun selfTestsReadOnlySupportedMonitorsAndSummarise() {
        val car = Scripted(
            mapOf(
                "0600" to "46 00 00 00 00 01", // nothing here but "more"
                "0620" to "46 20 80 00 00 01", // MID 21 (catalyst) and "more"
                "0640" to "46 40 00 00 00 01",
                "0660" to "46 60 00 00 00 01",
                "0680" to "46 80 00 00 00 01",
                "06A0" to "46 A0 60 00 00 00", // A2 and A3: cylinders 1 and 2
                "0621" to "46 21 80 20 00 40 00 50 FF FF", // catalyst ratio below its minimum: failed
                "06A2" to "46 A2 0B 24 00 00 00 00 00 FF A2 0C 24 00 00 00 00 00 FF",
                "06A3" to "46 A3 0B 24 00 0C 00 00 00 FF A3 0C 24 00 1E 00 00 00 FF"
            )
        )
        val result = SelfTests.read(car)!!
        assertEquals(false, result.catalystPassed)
        assertEquals(1, result.failed.size)
        assertEquals(listOf(CylinderMisfires(1, 0, 0), CylinderMisfires(2, 30, 12)), result.misfires)
        assertFalse("never asks for monitors the car doesn't have", "0601" in car.asked)
    }

    @Test
    fun aCarWithoutMode06ReturnsNull() {
        assertNull(SelfTests.read(Scripted(emptyMap())))
    }

    @Test
    fun cvnsFromOneOrMoreEcus() {
        assertEquals(listOf("1A2B3C4D"), Mode09Reader.parseCvns("49 06 01 1A 2B 3C 4D"))
        assertEquals(listOf("1A2B3C4D", "DEADBEEF"), Mode09Reader.parseCvns("49 06 01 1A 2B 3C 4D\n49 06 01 DE AD BE EF"))
        assertTrue(Mode09Reader.parseCvns("NO DATA").isEmpty())
    }

    @Test
    fun freezeFrameSelfTestsAndCvnSurviveSaving() {
        val scan = PersistedScan(
            1L, "REAL", false, "Kia", emptyMap(), emptyList(),
            VehicleIdInfo("VIN", null, "CAL1", listOf("1A2B3C4D")), null,
            FreezeFrame("P0301", mapOf(PidId.RPM to 2800.0)),
            SelfTestResult(listOf(SelfTest(0xA2, 0x0C, 0x24, 3, 0, 255)))
        )
        assertEquals(scan, scanFromMap(scanToMap(scan)))
    }

    @Test
    fun oldSavesWithoutTheNewFieldsStillLoad() {
        val old = scanToMap(PersistedScan(1L, "REAL", false, null, emptyMap(), emptyList(), VehicleIdInfo(null, null, null), null))
            .filterKeys { it != "freezeFrame" && it != "selfTests" }
        val loaded = scanFromMap(old)
        assertNull(loaded.freezeFrame)
        assertNull(loaded.selfTests)
    }
}
