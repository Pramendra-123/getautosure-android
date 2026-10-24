package com.enginesense.app.vehicle

import com.enginesense.app.obd.ObdRawResult
import com.enginesense.app.obd.ObdResponseParser
import com.enginesense.app.obd.ObdTransport
import java.io.IOException

/** One emissions-monitor test: whether this vehicle has it at all, and whether it's finished running since codes were last cleared. */
data class ReadinessMonitor(
    val name: String,
    val supported: Boolean,
    val ready: Boolean
)

enum class IgnitionType { SPARK, COMPRESSION }

data class ReadinessStatus(
    val milOn: Boolean,
    val dtcCount: Int,
    val ignitionType: IgnitionType,
    val monitors: List<ReadinessMonitor>
) {
    /** What an emissions/PUC inspection actually checks: every monitor the car HAS must be ready. One it doesn't have doesn't count against it. */
    val readyForEmissionsTest: Boolean get() = monitors.filter { it.supported }.all { it.ready }
}

/**
 * Decodes Mode 01 PID 01 ("Monitor status since DTCs cleared") per SAE J1979 / ISO 15031-5.
 * The bit layout here was verified against the canonical OBD-II PID reference rather than
 * guessed — getting the supported-vs-ready direction backwards would show a driver the exact
 * opposite of their car's real readiness state, which is worse than not showing it at all.
 */
object ReadinessDecoder {
    fun decode(bytes: List<Int>): ReadinessStatus? {
        if (bytes.size < 4) return null
        val a = bytes[0]
        val b = bytes[1]
        val c = bytes[2]
        val d = bytes[3]

        val milOn = (a and 0x80) != 0
        val dtcCount = a and 0x7F
        val ignitionType = if ((b and 0x08) != 0) IgnitionType.COMPRESSION else IgnitionType.SPARK

        // Common monitors: same bit index +4 between "available" (low nibble) and "complete" (high
        // nibble); complete bit == 0 means the test has finished, 1 means it hasn't run yet.
        val common = listOf(
            ReadinessMonitor("Misfire", supported = (b and 0x01) != 0, ready = (b and 0x10) == 0),
            ReadinessMonitor("Fuel System", supported = (b and 0x02) != 0, ready = (b and 0x20) == 0),
            ReadinessMonitor("Components", supported = (b and 0x04) != 0, ready = (b and 0x40) == 0)
        )

        val specific = if (ignitionType == IgnitionType.SPARK) {
            listOf(
                ReadinessMonitor("Catalyst", (c and 0x01) != 0, (d and 0x01) == 0),
                ReadinessMonitor("Heated Catalyst", (c and 0x02) != 0, (d and 0x02) == 0),
                ReadinessMonitor("Evaporative System", (c and 0x04) != 0, (d and 0x04) == 0),
                ReadinessMonitor("Secondary Air System", (c and 0x08) != 0, (d and 0x08) == 0),
                ReadinessMonitor("Oxygen Sensor", (c and 0x20) != 0, (d and 0x20) == 0),
                ReadinessMonitor("Oxygen Sensor Heater", (c and 0x40) != 0, (d and 0x40) == 0),
                ReadinessMonitor("EGR/VVT System", (c and 0x80) != 0, (d and 0x80) == 0)
            )
        } else {
            listOf(
                ReadinessMonitor("NMHC Catalyst", (c and 0x01) != 0, (d and 0x01) == 0),
                ReadinessMonitor("NOx/SCR Monitor", (c and 0x02) != 0, (d and 0x02) == 0),
                ReadinessMonitor("Boost Pressure", (c and 0x08) != 0, (d and 0x08) == 0),
                ReadinessMonitor("Exhaust Gas Sensor", (c and 0x20) != 0, (d and 0x20) == 0),
                ReadinessMonitor("PM Filter", (c and 0x40) != 0, (d and 0x40) == 0),
                ReadinessMonitor("EGR/VVT System", (c and 0x80) != 0, (d and 0x80) == 0)
            )
        }

        return ReadinessStatus(milOn, dtcCount, ignitionType, common + specific)
    }
}

/** Reads and decodes Mode 01 PID 01 off any [ObdTransport]. Returns null rather than a guess if the adapter can't answer. */
object ReadinessReader {
    fun read(transport: ObdTransport): ReadinessStatus? {
        val raw = try {
            transport.sendCommand("0101")
        } catch (e: IOException) {
            return null
        }
        val parsed = ObdResponseParser.parse(raw, expectedModeEcho = "41")
        val bytes = (parsed as? ObdRawResult.Ok)?.bytes ?: return null
        val dataBytes = if (bytes.firstOrNull() == 0x01) bytes.drop(1) else bytes
        return ReadinessDecoder.decode(dataBytes)
    }
}
