package com.enginesense.app.vehicle

import com.enginesense.app.obd.DtcDecoder
import com.enginesense.app.obd.ObdFrames
import com.enginesense.app.obd.ObdTransport

/**
 * The car's own snapshot from the moment it stored a fault code (Mode 02, frame 0): the code
 * and what the engine was doing — "set at 2,800 rpm, engine hot, under load". Only the
 * readings the ECU actually saved are here.
 */
data class FreezeFrame(val dtc: String, val readings: Map<PidId, Double>)

object FreezeFrameReader {

    /** What's worth asking for: enough to describe the moment, without slowing the scan. */
    val PIDS = listOf(
        PidId.RPM, PidId.SPEED_KMH, PidId.COOLANT_TEMP_C, PidId.ENGINE_LOAD,
        PidId.SHORT_TERM_FUEL_TRIM_1, PidId.LONG_TERM_FUEL_TRIM_1, PidId.INTAKE_MANIFOLD_PRESSURE,
        PidId.INTAKE_AIR_TEMP_C, PidId.THROTTLE_POSITION, PidId.FUEL_SYSTEM_STATUS, PidId.RUN_TIME_SINCE_START
    )

    /** Null when no code froze a frame (the usual answer on a healthy car) or the car can't answer Mode 02. */
    fun read(transport: ObdTransport): FreezeFrame? {
        val dtc = runCatching { parseDtc(transport.sendCommand("020200")) }.getOrNull() ?: return null
        val readings = PIDS.mapNotNull { pid ->
            runCatching { parseReading(pid, transport.sendCommand("02%02X00".format(pid.pid))) }.getOrNull()?.let { pid to it }
        }.toMap()
        return FreezeFrame(dtc, readings)
    }

    /** "42 02 00 01 33" → "P0133". 00 00 means no frame was stored. */
    fun parseDtc(raw: String): String? {
        val data = ObdFrames.after(raw, 0x42, 0x02)?.drop(1) ?: return null // drop the frame number
        if (data.size < 2 || (data[0] == 0 && data[1] == 0)) return null
        return DtcDecoder.decode(data.take(2)).firstOrNull()
    }

    /** "42 0C 00 1A F8" → 1726.0 rpm, through the same decoder and plausibility check as live data. */
    fun parseReading(pid: PidId, raw: String): Double? {
        val data = ObdFrames.after(raw, 0x42, pid.pid)?.drop(1) ?: return null
        if (data.size < pid.minBytes) return null
        val value = runCatching { pid.decode(data) }.getOrNull() ?: return null
        return value.takeIf { it in pid.plausibleRange }
    }
}

/** "2,800 rpm · 64 km/h · engine hot 92°C · under load 71% · fuel mix +4% · 23 min after starting" */
fun FreezeFrame.describe(): String {
    val r = readings
    val parts = mutableListOf<String>()
    r[PidId.RPM]?.let { parts += "%,.0f rpm".format(it) }
    r[PidId.SPEED_KMH]?.let { parts += if (it < 1) "standing still" else "%.0f km/h".format(it) }
    r[PidId.COOLANT_TEMP_C]?.let { parts += (if (it >= 80) "engine hot" else if (it < 60) "engine cold" else "engine warming up") + " %.0f°C".format(it) }
    r[PidId.ENGINE_LOAD]?.let { parts += (if (it >= 60) "under load" else "load") + " %.0f%%".format(it) }
    val trim = listOfNotNull(r[PidId.SHORT_TERM_FUEL_TRIM_1], r[PidId.LONG_TERM_FUEL_TRIM_1]).takeIf { it.isNotEmpty() }?.sum()
    trim?.let { parts += "fuel mix %+.0f%%".format(it) }
    r[PidId.RUN_TIME_SINCE_START]?.let { parts += if (it < 120) "%.0f s after starting".format(it) else "%.0f min after starting".format(it / 60) }
    return parts.joinToString(" · ")
}
