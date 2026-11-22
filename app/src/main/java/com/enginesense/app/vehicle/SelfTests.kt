package com.enginesense.app.vehicle

import com.enginesense.app.obd.ObdFrames
import com.enginesense.app.obd.ObdTransport

/**
 * One on-board self-test result (Mode 06): the ECU's own measurement against the limits it
 * uses itself. [passed] compares the raw numbers, so it holds even for a unit we can't name.
 */
data class SelfTest(
    val mid: Int,
    val tid: Int,
    /** SAE unit-and-scaling id: how to turn the raw numbers into [unit]. */
    val unitId: Int,
    val rawValue: Int,
    val rawMin: Int,
    val rawMax: Int
) {
    val passed: Boolean get() = rawValue in rawMin..rawMax
    private val scaling: Scaling? get() = SelfTests.scaling(unitId)
    val value: Double? get() = scaling?.let { rawValue * it.factor + it.offset }
    val min: Double? get() = scaling?.let { rawMin * it.factor + it.offset }
    val max: Double? get() = scaling?.let { rawMax * it.factor + it.offset }
    val unit: String? get() = scaling?.unit
    val monitor: String get() = SelfTests.monitorName(mid)
}

data class Scaling(val unit: String, val factor: Double, val offset: Double = 0.0)

/** Misfires the ECU counted on one cylinder: this/last drive, and its running average over ~10 drives. */
data class CylinderMisfires(val cylinder: Int, val recent: Int?, val average: Int?)

data class SelfTestResult(val tests: List<SelfTest>) {
    val failed: List<SelfTest> get() = tests.filter { !it.passed }
    /** Per cylinder, from the misfire monitors (MID A2 = cylinder 1 … AD = cylinder 12). */
    val misfires: List<CylinderMisfires> get() = tests.filter { it.mid in 0xA2..0xAD }.groupBy { it.mid }.map { (mid, t) ->
        CylinderMisfires(mid - 0xA1, t.firstOrNull { it.tid == 0x0C }?.rawValue, t.firstOrNull { it.tid == 0x0B }?.rawValue)
    }.sortedBy { it.cylinder }
    /** Null when the car has no catalyst monitor result. */
    val catalystPassed: Boolean? get() = tests.filter { it.mid == 0x21 || it.mid == 0x22 }.takeIf { it.isNotEmpty() }?.all { it.passed }
}

/**
 * Mode 06 on-board monitoring test results — misfires per cylinder, catalyst efficiency, oxygen
 * sensor response, EGR and EVAP — read only on CAN cars (2008+ in most of the world, and every
 * BS4/BS6 car sold in India), whose answer is a run of 9-byte records:
 * MID, TID, unit id, value (2), min (2), max (2). Older protocols answer in a different shape
 * and come back as null rather than misread.
 */
object SelfTests {

    /** Monitors worth the extra seconds on a scan: oxygen sensors, catalyst, EGR, EVAP, misfire. */
    private val WANTED: Set<Int> = ((0x01..0x10) + (0x21..0x22) + (0x31..0x33) + (0x39..0x3D) + (0xA1..0xAD)).toSet()

    fun read(transport: ObdTransport): SelfTestResult? {
        val supported = supportedMids(transport) ?: return null
        val tests = supported.filter { it in WANTED }.sorted().flatMap { mid ->
            runCatching { parseRecords(transport.sendCommand("06%02X".format(mid)), mid) }.getOrDefault(emptyList())
        }
        return SelfTestResult(tests).takeIf { tests.isNotEmpty() }
    }

    /** MIDs 00, 20, 40… each list the next 32; follow the chain while the last bit says there's more. */
    private fun supportedMids(transport: ObdTransport): Set<Int>? {
        val all = mutableSetOf<Int>()
        var range = 0x00
        while (range <= 0xE0) {
            val raw = runCatching { transport.sendCommand("06%02X".format(range)) }.getOrNull() ?: break
            val mask = ObdFrames.after(raw, 0x46, range) ?: break
            val ids = ObdFrames.supportedFrom(mask.take(4), range)
            all += ids.filter { it % 0x20 != 0 }
            if ((range + 0x20) !in ids) break
            range += 0x20
        }
        return all.takeIf { it.isNotEmpty() }
    }

    /**
     * "46 A2 0B 24 00 00 00 00 FF FF 0C 24 …": one 9-byte record per test. A second ECU answering
     * starts again with "46 A2", which is skipped.
     */
    fun parseRecords(raw: String, mid: Int): List<SelfTest> {
        val b = ObdFrames.bytes(raw)
        var i = (0 until b.size - 1).firstOrNull { b[it] == 0x46 && b[it + 1] == mid }?.plus(1) ?: return emptyList()
        val out = mutableListOf<SelfTest>()
        while (i + 9 <= b.size) {
            if (b[i] == 0x46 && b.getOrNull(i + 1) == mid) { i++; continue }
            if (b[i] != mid) break
            val unitId = b[i + 2]
            fun word(at: Int): Int {
                val v = (b[at] shl 8) or b[at + 1]
                return if (unitId >= 0x80 && v >= 0x8000) v - 0x10000 else v
            }
            out += SelfTest(mid, b[i + 1], unitId, word(i + 3), word(i + 5), word(i + 7))
            i += 9
        }
        return out
    }

    fun monitorName(mid: Int): String = when (mid) {
        in 0x01..0x10 -> {
            val n = mid - 1
            "Oxygen sensor, bank ${n / 4 + 1} sensor ${n % 4 + 1}"
        }
        0x21 -> "Catalytic converter, bank 1"
        0x22 -> "Catalytic converter, bank 2"
        in 0x31..0x33 -> "EGR system"
        0x39 -> "Fuel vapour leak (large)"
        0x3A -> "Fuel vapour leak (medium)"
        0x3B -> "Fuel vapour leak (small)"
        0x3C -> "Fuel vapour leak (very small)"
        0x3D -> "Fuel vapour purge flow"
        0xA1 -> "Misfire, all cylinders"
        in 0xA2..0xAD -> "Misfire, cylinder ${mid - 0xA1}"
        else -> "Self-test %02X".format(mid)
    }

    /** SAE J1979 unit-and-scaling ids — the common ones; anything else keeps its pass/fail but no unit. */
    fun scaling(id: Int): Scaling? = when (id) {
        0x01, 0x81 -> Scaling("", 1.0)
        0x02, 0x82 -> Scaling("", 0.1)
        0x03, 0x83 -> Scaling("", 0.01)
        0x04, 0x84 -> Scaling("", 0.001)
        0x07 -> Scaling("rpm", 0.25)
        0x0A, 0x8A -> Scaling("mV", 0.122)
        0x0B, 0x8B -> Scaling("V", 0.001)
        0x0C, 0x8C -> Scaling("V", 0.01)
        0x0D, 0x8D -> Scaling("mA", 0.00390625)
        0x0E, 0x8E -> Scaling("A", 0.001)
        0x10, 0x90 -> Scaling("ms", 1.0)
        0x11 -> Scaling("ms", 100.0)
        0x12 -> Scaling("s", 1.0)
        0x14 -> Scaling("Ω", 1.0)
        0x16, 0x96 -> Scaling("°C", 0.1, -40.0)
        0x17 -> Scaling("kPa", 0.01)
        0x1A -> Scaling("kPa", 1.0)
        0x1C, 0x9C -> Scaling("°", 0.01)
        0x1D, 0x9D -> Scaling("°", 0.5)
        0x1E -> Scaling("ratio", 0.0000305)
        0x20 -> Scaling("ratio", 0.0039062)
        0x22 -> Scaling("Hz", 1.0)
        0x24 -> Scaling("counts", 1.0)
        0x25 -> Scaling("km", 1.0)
        0x27 -> Scaling("g/s", 0.01)
        0x2F, 0xAF -> Scaling("%", 0.01)
        0x30 -> Scaling("%", 0.001526)
        0x34 -> Scaling("min", 1.0)
        else -> null
    }
}
