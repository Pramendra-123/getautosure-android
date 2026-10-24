package com.enginesense.app.obd

/** Vehicle/ECU identification from Mode 09 — whatever the ECU actually answered, never a guess; any field it didn't answer is null. */
data class VehicleIdInfo(
    val vin: String?,
    val ecuName: String?,
    val calibrationId: String?
)

/**
 * Reads Mode 09 (vehicle information): VIN (PID 02), ECU name (PID 0A), calibration ID
 * (PID 04). Unlike the Mode 01 PIDs in [PidDefinitions][com.enginesense.app.vehicle.PidId],
 * these responses are ASCII text spread across multiple ISO-TP frames, and different ELM327
 * clones format that multi-line text differently — some emit a "0:"/"1:"/"2:" frame-index
 * prefix per line, some don't, some prepend a 3-hex-digit total-length line. Rather than
 * assume one exact format (untested against the real adapters this would ship to), this
 * scans the whole response for the "49 <PID>" mode+PID echo and decodes every hex-byte-pair
 * after it, which is tolerant of all of the above.
 */
object Mode09Reader {

    fun read(transport: ObdTransport): VehicleIdInfo = VehicleIdInfo(
        vin = readAsciiField(transport, "0902", pidEcho = 0x02),
        ecuName = readAsciiField(transport, "090A", pidEcho = 0x0A),
        calibrationId = readAsciiField(transport, "0904", pidEcho = 0x04)
    )

    private val HEX_PAIR = Regex("[0-9A-Fa-f]{2}")

    private fun readAsciiField(transport: ObdTransport, command: String, pidEcho: Int): String? {
        val raw = try {
            transport.sendCommand(command)
        } catch (e: Exception) {
            return null
        }

        val allBytes = HEX_PAIR.findAll(raw).map { it.value.toInt(16) }.toList()
        val echoIndex = (0 until allBytes.size - 1).firstOrNull { i ->
            allBytes[i] == 0x49 && allBytes[i + 1] == pidEcho
        } ?: return null

        // The byte right after the PID echo is sometimes a "number of data items" count
        // (VIN responses carry one, ECU name/calibration ID usually don't) — it's never
        // printable ASCII either way, so dropping leading non-printable bytes handles both
        // without needing to special-case which fields have it.
        val payload = allBytes.drop(echoIndex + 2)
        val text = payload
            .dropWhile { it < 0x20 || it > 0x7E }
            .filter { it in 0x20..0x7E }
            .map { it.toChar() }
            .joinToString("")
            .trim()

        return text.ifBlank { null }
    }
}
