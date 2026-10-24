package com.enginesense.app.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class DtcDecoderTest {

    @Test
    fun `decodes single DTC with padding`() {
        // 0x03 = 0000_0011 -> P, digit1=0, digit2=3; 0x01 -> digit3=0, digit4=1 => P0301
        val codes = DtcDecoder.decode(listOf(0x03, 0x01, 0x00, 0x00, 0x00, 0x00))
        assertEquals(listOf("P0301"), codes)
    }

    @Test
    fun `decodes the canonical textbook example`() {
        // 0x01 -> P, digit1=0, digit2=1; 0x33 -> digit3=3, digit4=3 => P0133
        val codes = DtcDecoder.decode(listOf(0x01, 0x33))
        assertEquals(listOf("P0133"), codes)
    }

    @Test
    fun `returns empty list for all-zero padding`() {
        val codes = DtcDecoder.decode(listOf(0x00, 0x00, 0x00, 0x00))
        assertEquals(emptyList<String>(), codes)
    }

    @Test
    fun `decodes chassis and body letter prefixes`() {
        // top 2 bits 01 -> C, 10 -> B, 11 -> U
        val c = DtcDecoder.decode(listOf(0x41, 0x00)) // 0100_0001 -> C, digit1=0, digit2=1
        val b = DtcDecoder.decode(listOf(0x81, 0x00)) // 1000_0001 -> B
        val u = DtcDecoder.decode(listOf(0xC1, 0x00)) // 1100_0001 -> U
        assertEquals(listOf("C0100"), c)
        assertEquals(listOf("B0100"), b)
        assertEquals(listOf("U0100"), u)
    }

    @Test
    fun `decodes multiple DTCs in one response`() {
        val codes = DtcDecoder.decode(listOf(0x03, 0x01, 0x01, 0x33, 0x00, 0x00))
        assertEquals(listOf("P0301", "P0133"), codes)
    }
}
