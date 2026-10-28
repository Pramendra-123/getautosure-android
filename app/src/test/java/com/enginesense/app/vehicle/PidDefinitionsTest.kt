package com.enginesense.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class PidDefinitionsTest {

    @Test
    fun `fuel trim decodes to zero percent at the midpoint byte`() {
        assertEquals(0.0, PidId.SHORT_TERM_FUEL_TRIM_1.decode(listOf(128)), 0.001)
        assertEquals(0.0, PidId.LONG_TERM_FUEL_TRIM_1.decode(listOf(128)), 0.001)
    }

    @Test
    fun `timing advance decodes to zero degrees at the midpoint byte`() {
        assertEquals(0.0, PidId.TIMING_ADVANCE.decode(listOf(128)), 0.001)
    }

    @Test
    fun `actual engine torque centers on byte 125, not 128`() {
        // PID 0x62 is one of the few that centers on 125 rather than the usual 128 — a
        // real spec quirk worth a test, since it's easy to copy-paste the wrong offset.
        assertEquals(0.0, PidId.ACTUAL_ENGINE_TORQUE_PERCENT.decode(listOf(125)), 0.001)
    }

    @Test
    fun `fuel rail pressure matches the real reading seen on a real car (14830 kPa)`() {
        // From live Skoda Slavia testing via a reference OBD2 app: 14830 kPa.
        // 14830 / 10 = 1483 = 0x05CB -> bytes 0x05, 0xCB.
        val value = PidId.FUEL_RAIL_PRESSURE_ABS.decode(listOf(0x05, 0xCB))
        assertEquals(14830.0, value, 0.1)
    }

    @Test
    fun `ambient air temperature uses the standard minus-40 offset`() {
        // 38°C seen on the same real car -> byte should be 78 (0x4E).
        assertEquals(38.0, PidId.AMBIENT_AIR_TEMP_C.decode(listOf(78)), 0.001)
    }

    @Test
    fun `ECU odometer decodes four bytes at 0_1 km per bit`() {
        // 842105 tenths = 0x000CD979 -> 84,210.5 km.
        assertEquals(84210.5, PidId.ODOMETER.decode(listOf(0x00, 0x0C, 0xD9, 0x79)), 0.01)
        assertEquals("01A6", PidId.ODOMETER.obdCommand)
    }

    @Test
    fun `cleared-code counters decode as plain counts`() {
        assertEquals(3.0, PidId.WARM_UPS_SINCE_CODES_CLEARED.decode(listOf(3)), 0.001)
        assertEquals(300.0, PidId.TIME_SINCE_CODES_CLEARED.decode(listOf(0x01, 0x2C)), 0.001)
    }

    @Test
    fun `every PID has a unique command and a non-empty display name`() {
        val commands = PidId.entries.map { it.obdCommand }
        assertEquals(commands.size, commands.toSet().size)
        PidId.entries.forEach { assert(it.displayName.isNotBlank()) }
    }
}
