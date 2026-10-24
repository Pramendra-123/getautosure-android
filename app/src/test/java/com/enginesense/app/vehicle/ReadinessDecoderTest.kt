package com.enginesense.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessDecoderTest {

    @Test
    fun `returns null on a short response instead of guessing`() {
        assertNull(ReadinessDecoder.decode(listOf(0x00, 0x07)))
    }

    @Test
    fun `reads MIL status and DTC count from byte A`() {
        // A7=1 (MIL on), A6-A0=3 (3 DTCs): 0x80 or 0x03 = 0x83.
        val status = ReadinessDecoder.decode(listOf(0x83, 0x00, 0x00, 0x00))!!
        assertTrue(status.milOn)
        assertEquals(3, status.dtcCount)
    }

    @Test
    fun `byte B bit 3 selects compression ignition and the matching monitor set`() {
        val spark = ReadinessDecoder.decode(listOf(0x00, 0x00, 0x00, 0x00))!!
        assertEquals(IgnitionType.SPARK, spark.ignitionType)
        assertTrue(spark.monitors.any { it.name == "Catalyst" })

        val compression = ReadinessDecoder.decode(listOf(0x00, 0x08, 0x00, 0x00))!!
        assertEquals(IgnitionType.COMPRESSION, compression.ignitionType)
        assertTrue(compression.monitors.any { it.name == "NMHC Catalyst" })
        assertFalse(compression.monitors.any { it.name == "Catalyst" })
    }

    @Test
    fun `common monitor availability and completeness read from the correct bits`() {
        // B0=1 (misfire supported), B4=1 (misfire NOT complete yet); fuel system and
        // components bits left 0 (unsupported, so their completeness bit doesn't matter).
        val status = ReadinessDecoder.decode(listOf(0x00, 0x11, 0x00, 0x00))!!
        val misfire = status.monitors.first { it.name == "Misfire" }
        assertTrue(misfire.supported)
        assertFalse(misfire.ready)
        val fuelSystem = status.monitors.first { it.name == "Fuel System" }
        assertFalse(fuelSystem.supported)
    }

    @Test
    fun `spark monitor availability bit 1 means supported, completeness bit 0 means ready`() {
        // C0=1: Catalyst supported. D0=0: Catalyst test complete/ready.
        val status = ReadinessDecoder.decode(listOf(0x00, 0x00, 0x01, 0x00))!!
        val catalyst = status.monitors.first { it.name == "Catalyst" }
        assertTrue(catalyst.supported)
        assertTrue(catalyst.ready)
    }

    @Test
    fun `spark monitor completeness bit 1 means NOT ready`() {
        // C0=1 (supported), D0=1 (not yet complete).
        val status = ReadinessDecoder.decode(listOf(0x00, 0x00, 0x01, 0x01))!!
        val catalyst = status.monitors.first { it.name == "Catalyst" }
        assertTrue(catalyst.supported)
        assertFalse(catalyst.ready)
    }

    @Test
    fun `readyForEmissionsTest ignores monitors the car does not have`() {
        // Nothing supported at all (all availability bits 0) -> vacuously ready.
        val status = ReadinessDecoder.decode(listOf(0x00, 0x00, 0x00, 0x00))!!
        assertTrue(status.readyForEmissionsTest)
    }

    @Test
    fun `readyForEmissionsTest is false when a supported monitor is not ready`() {
        // C0=1 supported, D0=1 not complete.
        val status = ReadinessDecoder.decode(listOf(0x00, 0x00, 0x01, 0x01))!!
        assertFalse(status.readyForEmissionsTest)
    }
}
