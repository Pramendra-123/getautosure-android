package com.enginesense.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdResponseParserTest {

    @Test
    fun `strips mode echo and parses hex bytes`() {
        // Only the mode-echo byte is this layer's job; the PID-echo byte that follows
        // it in a Mode 01 response is stripped one layer up, by PidReader (see
        // PidReaderSimulatedTest) — this parser doesn't know what a "PID" is.
        val result = ObdResponseParser.parse("41 0C 1A F8", expectedModeEcho = "41")
        assertTrue(result is ObdRawResult.Ok)
        assertEquals(listOf(0x0C, 0x1A, 0xF8), (result as ObdRawResult.Ok).bytes)
    }

    @Test
    fun `keeps bytes when mode echo does not match`() {
        val result = ObdResponseParser.parse("1A F8", expectedModeEcho = "41")
        assertTrue(result is ObdRawResult.Ok)
        assertEquals(listOf(0x1A, 0xF8), (result as ObdRawResult.Ok).bytes)
    }

    @Test
    fun `recognizes NO DATA`() {
        assertEquals(ObdRawResult.NoData, ObdResponseParser.parse("NO DATA"))
    }

    @Test
    fun `recognizes unsupported marker`() {
        assertEquals(ObdRawResult.Unsupported, ObdResponseParser.parse("?"))
    }

    @Test
    fun `treats bare searching with no data as adapter error`() {
        val result = ObdResponseParser.parse("SEARCHING...")
        assertTrue(result is ObdRawResult.AdapterError)
    }

    @Test
    fun `strips a SEARCHING prefix and still parses the real data that follows`() {
        // Real capture from a live ELM327 on a Skoda Slavia: the adapter prepends
        // "SEARCHING..." to the very first response after ATSP0 selects a protocol, with
        // the real answer appended right after on the same read. Before this was handled,
        // PidReader's base-tier "which PIDs are supported" query (0100) always hit exactly
        // this path, silently losing the bitmask and making RPM/coolant/etc. look
        // unsupported even though the adapter answered them correctly.
        val result = ObdResponseParser.parse("SEARCHING... 41 00 BE 3E A8 13", expectedModeEcho = "41")
        assertTrue(result is ObdRawResult.Ok)
        assertEquals(listOf(0x00, 0xBE, 0x3E, 0xA8, 0x13), (result as ObdRawResult.Ok).bytes)
    }

    @Test
    fun `strips a BUSY prefix the same way`() {
        val result = ObdResponseParser.parse("BUSY... 41 0C 1A F8", expectedModeEcho = "41")
        assertTrue(result is ObdRawResult.Ok)
        assertEquals(listOf(0x0C, 0x1A, 0xF8), (result as ObdRawResult.Ok).bytes)
    }

    @Test
    fun `treats malformed text as adapter error`() {
        val result = ObdResponseParser.parse("garbage not hex")
        assertTrue(result is ObdRawResult.AdapterError)
    }
}
