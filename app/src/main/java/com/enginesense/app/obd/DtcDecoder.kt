package com.enginesense.app.obd

/**
 * Standalone SAE J1979 DTC byte-pair decoder, usable by any transport's Mode 03/07
 * response. Pulled out of [Elm327Client] so the new normalization pipeline
 * ([ObdResponseParser]) and the simulator can share the exact same decode logic
 * instead of two copies drifting apart.
 */
object DtcDecoder {

    /**
     * Decodes already-normalized data bytes (mode-echo byte already stripped) into DTC
     * strings like "P0133". First byte's top 2 bits pick the letter, next 2 bits the
     * first digit, its low nibble the second digit, and the second byte gives digits 3-4.
     */
    fun decode(dataBytes: List<Int>): List<String> {
        val codes = mutableListOf<String>()
        var i = 0
        while (i + 1 < dataBytes.size) {
            val a = dataBytes[i]
            val b = dataBytes[i + 1]
            i += 2
            if (a == 0 && b == 0) continue // padding, no code in this slot

            val letter = when ((a shr 6) and 0b11) {
                0 -> "P"
                1 -> "C"
                2 -> "B"
                else -> "U"
            }
            val digit1 = (a shr 4) and 0b11
            val digit2 = a and 0b1111
            val digit3 = (b shr 4) and 0b1111
            val digit4 = b and 0b1111

            codes.add("%s%d%X%X%X".format(letter, digit1, digit2, digit3, digit4))
        }
        return codes
    }
}
