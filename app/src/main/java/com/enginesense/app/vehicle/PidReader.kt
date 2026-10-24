package com.enginesense.app.vehicle

import com.enginesense.app.obd.ObdRawResult
import com.enginesense.app.obd.ObdResponseParser
import com.enginesense.app.obd.ObdTransport
import java.io.IOException

/**
 * Reads [PidId]s off any [ObdTransport] (real dongle, future hardware, or the
 * simulator — this class never knows which) and turns the raw response into a
 * validated [PidReading]. Handles the SAE J1979 "supported PIDs" discovery protocol
 * so a PID the ECU genuinely doesn't have comes back as [PidStatus.UNSUPPORTED]
 * rather than being confused with a transient "no data right now".
 */
class PidReader(private val transport: ObdTransport) {

    /** Cache of which PIDs are supported, keyed by range start (0x00, 0x20, 0x40, ...). Avoids re-querying per PID. */
    private val supportedPidCache = mutableMapOf<Int, Set<Int>>()

    fun read(pidId: PidId): PidReading {
        return try {
            if (!isSupported(pidId.pid)) {
                return PidReading(pidId, PidStatus.UNSUPPORTED, null, null)
            }

            val raw = transport.sendCommand(pidId.obdCommand)
            val expectedEcho = "41"
            when (val parsed = ObdResponseParser.parse(raw, expectedModeEcho = expectedEcho)) {
                // Response format is "<mode echo> <pid echo> <data...>" — the mode echo is
                // already stripped above; drop the pid echo too (e.g. "0C" in "41 0C 1A F8")
                // before handing the actual data bytes to the decoder.
                is ObdRawResult.Ok -> {
                    val dataBytes = if (parsed.bytes.firstOrNull() == pidId.pid) parsed.bytes.drop(1) else parsed.bytes
                    fromBytes(pidId, dataBytes)
                }
                is ObdRawResult.NoData -> PidReading(pidId, PidStatus.SUPPORTED_NO_DATA, null, null)
                is ObdRawResult.Unsupported -> PidReading(pidId, PidStatus.UNSUPPORTED, null, null)
                is ObdRawResult.AdapterError -> PidReading(pidId, PidStatus.TRANSPORT_ERROR, null, null)
            }
        } catch (e: IOException) {
            PidReading(pidId, PidStatus.TRANSPORT_ERROR, null, null)
        }
    }

    fun readAll(pids: List<PidId> = PidId.entries): Map<PidId, PidReading> =
        pids.associateWith { read(it) }

    private fun fromBytes(pidId: PidId, bytes: List<Int>): PidReading {
        if (bytes.size < pidId.minBytes) {
            return PidReading(pidId, PidStatus.TRANSPORT_ERROR, null, bytes)
        }
        val value = try {
            pidId.decode(bytes)
        } catch (e: Exception) {
            return PidReading(pidId, PidStatus.TRANSPORT_ERROR, null, bytes)
        }
        val status = if (value in pidId.plausibleRange) PidStatus.VERIFIED else PidStatus.IMPLAUSIBLE
        return PidReading(pidId, status, value, bytes)
    }

    /**
     * SAE J1979 PID-support discovery: PID 0x00 returns a 4-byte bitmask covering PIDs
     * 0x01-0x20, PID 0x20 covers 0x21-0x40, PID 0x40 covers 0x41-0x60, and so on. Each
     * range only needs to be queried once per connection.
     */
    private fun isSupported(pid: Int): Boolean {
        val rangeStart = (pid - 1) / 0x20 * 0x20
        val supported = supportedPidCache.getOrPut(rangeStart) { querySupportedRange(rangeStart) }
        return pid in supported
    }

    private fun querySupportedRange(rangeStart: Int): Set<Int> {
        val command = "01%02X".format(rangeStart)
        val raw = try {
            transport.sendCommand(command)
        } catch (e: IOException) {
            return emptySet()
        }

        val parsed = ObdResponseParser.parse(raw, expectedModeEcho = "41")
        val bytes = (parsed as? ObdRawResult.Ok)?.bytes ?: return emptySet()
        // First byte of the response data is the PID echo (e.g. 0x00); the bitmask is the next 4 bytes.
        val maskBytes = if (bytes.isNotEmpty()) bytes.drop(1) else bytes
        if (maskBytes.size < 4) return emptySet()

        val mask = (maskBytes[0] shl 24) or (maskBytes[1] shl 16) or (maskBytes[2] shl 8) or maskBytes[3]
        val supported = mutableSetOf<Int>()
        for (offset in 1..0x20) {
            val bitIndex = 32 - offset
            if ((mask shr bitIndex) and 1 == 1) {
                supported.add(rangeStart + offset)
            }
        }
        return supported
    }
}
