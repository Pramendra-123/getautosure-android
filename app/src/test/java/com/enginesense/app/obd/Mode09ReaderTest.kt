package com.enginesense.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeTransport(private val responses: Map<String, String>) : ObdTransport {
    override fun connect() {}
    override fun sendCommand(command: String): String = responses[command] ?: "NO DATA"
    override fun close() {}
}

class Mode09ReaderTest {

    @Test
    fun `decodes a flat single-line response with a VIN count byte`() {
        // "49 02 01" then ASCII "ABC123" = 41 42 43 31 32 33
        val t = FakeTransport(mapOf("0902" to "49 02 01 41 42 43 31 32 33"))
        assertEquals("ABC123", Mode09Reader.read(t).vin)
    }

    @Test
    fun `decodes a response with frame-index prefixes some adapters emit`() {
        val t = FakeTransport(mapOf("090A" to "014 0:49 0A 45 4E 47 1:49 4E 45 20 45 43 55"))
        assertEquals("ENGINE ECU", Mode09Reader.read(t).ecuName)
    }

    @Test
    fun `returns null for a field the ECU never answered`() {
        val t = FakeTransport(emptyMap())
        val info = Mode09Reader.read(t)
        assertNull(info.vin)
        assertNull(info.ecuName)
        assertNull(info.calibrationId)
    }

    @Test
    fun `calibration ID has no count byte but decodes correctly anyway`() {
        val t = FakeTransport(mapOf("0904" to "49 04 53 57 31 2E 30"))
        assertEquals("SW1.0", Mode09Reader.read(t).calibrationId)
    }

    @Test
    fun `ignores a transport that throws`() {
        val t = object : ObdTransport {
            override fun connect() {}
            override fun sendCommand(command: String): String = throw java.io.IOException("boom")
            override fun close() {}
        }
        val info = Mode09Reader.read(t)
        assertNull(info.vin)
    }
}
