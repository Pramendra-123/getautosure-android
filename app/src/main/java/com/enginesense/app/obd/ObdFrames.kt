package com.enginesense.app.obd

/**
 * Every data byte in a raw adapter response, tolerant of the multi-frame layouts ELM327 clones
 * print: an optional 3-hex-digit total-length line, and "0:", "1:" frame-index prefixes. Used by
 * the Mode 02/06/09 readers, whose answers often span several CAN frames or several ECUs.
 */
object ObdFrames {

    private val LENGTH_LINE = Regex("^[0-9A-Fa-f]{3}$")
    private val FRAME_INDEX = Regex("^[0-9A-Fa-f]:")
    private val HEX_PAIR = Regex("\\b[0-9A-Fa-f]{2}\\b")

    fun bytes(raw: String): List<Int> = raw
        .replace("\r", "\n")
        .split("\n", ">")
        .map { it.trim() }
        .filter { it.isNotEmpty() && !LENGTH_LINE.matches(it) }
        .map { it.replace(FRAME_INDEX, " ") }
        .flatMap { line -> HEX_PAIR.findAll(line).map { it.value.toInt(16) }.toList() }

    /** The bytes after the first "[mode echo] [pid echo]" pair; null when the echo isn't there. */
    fun after(raw: String, modeEcho: Int, pidEcho: Int): List<Int>? {
        val all = bytes(raw)
        val i = (0 until all.size - 1).firstOrNull { all[it] == modeEcho && all[it + 1] == pidEcho } ?: return null
        return all.drop(i + 2)
    }

    /** A 4-byte J1979 support bitmask starting at [rangeStart] as the set of ids it marks supported. */
    fun supportedFrom(mask: List<Int>, rangeStart: Int): Set<Int> {
        if (mask.size < 4) return emptySet()
        val bits = (mask[0].toLong() shl 24) or (mask[1].toLong() shl 16) or (mask[2].toLong() shl 8) or mask[3].toLong()
        return (1..0x20).filter { offset -> (bits shr (32 - offset)) and 1L == 1L }.map { rangeStart + it }.toSet()
    }
}
