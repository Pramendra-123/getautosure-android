package com.enginesense.app.obd

/**
 * Normalizes a raw ELM327 text response into one of a small set of outcomes, so every
 * layer above (PID decoding, DTC decoding, the rules engine) works off a structured
 * result instead of re-parsing adapter strings. This is transport-agnostic: it only
 * assumes the response text follows the standard ELM327 AT/OBD text conventions, which
 * any [ObdTransport] implementation (real dongle, future hardware, simulator) produces.
 */
sealed interface ObdRawResult {
    /** Successfully got space-separated hex byte pairs back, in order. */
    data class Ok(val bytes: List<Int>) : ObdRawResult

    /** Adapter reported "NO DATA": the PID/mode is valid for this ECU but has nothing to report right now. */
    data object NoData : ObdRawResult

    /** Adapter reported "?", "UNABLE TO CONNECT", or similar: this PID/mode isn't supported at all. */
    data object Unsupported : ObdRawResult

    /** Anything else unrecognized (adapter busy, "SEARCHING...", malformed text, etc). */
    data class AdapterError(val raw: String) : ObdRawResult
}

object ObdResponseParser {

    private val HEX_BYTE = Regex("^[0-9A-Fa-f]{2}$")
    private val NO_DATA_MARKERS = setOf("NODATA")
    private val UNSUPPORTED_MARKERS = setOf("?", "UNABLETOCONNECT", "NODATA")

    private val SEARCHING_NOISE = Regex("(?i)SEARCHING\\.*")
    private val BUSY_NOISE = Regex("(?i)\\bBUSY\\.*")

    /**
     * Parses a raw adapter response for an OBD request (not a plain AT command).
     * [expectedModeEcho] is the one-byte mode+0x40 echo expected as the first token
     * (e.g. "41" for a Mode 01 request, "43" for Mode 03) — when present it's stripped
     * before returning the data bytes, since callers only care about the payload.
     */
    fun parse(raw: String, expectedModeEcho: String? = null): ObdRawResult {
        // The adapter can prepend "SEARCHING..." (sometimes "BUSY...") to the very first
        // response after ATSP0 selects a protocol, with the real answer appended right after
        // on the same read — e.g. "SEARCHING... 41 00 BE 3E A8 13". Strip that noise instead
        // of treating the whole response as an error: on real hardware this made the base-
        // tier "which PIDs are supported" query look like it failed, so PidReader never even
        // asked for RPM/coolant/etc — not because the adapter didn't have them, but because
        // the one query that would have confirmed it was discarded here.
        val cleaned = raw.replace(SEARCHING_NOISE, " ").replace(BUSY_NOISE, " ").trim()

        val compact = cleaned.replace(" ", "").uppercase()
        if (compact.isEmpty()) return ObdRawResult.AdapterError(raw)
        if (compact in NO_DATA_MARKERS) return ObdRawResult.NoData
        if (compact in UNSUPPORTED_MARKERS) return ObdRawResult.Unsupported

        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty() || !tokens.all { it.matches(HEX_BYTE) }) {
            return ObdRawResult.AdapterError(raw)
        }

        val dataTokens = if (expectedModeEcho != null && tokens.firstOrNull()?.equals(expectedModeEcho, ignoreCase = true) == true) {
            tokens.drop(1)
        } else {
            tokens
        }

        return ObdRawResult.Ok(dataTokens.map { it.toInt(16) })
    }
}
