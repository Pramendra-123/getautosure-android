package com.enginesense.app.obd

/**
 * Canned vehicle scenarios for [SimulatedObdTransport]. Each one exercises a different
 * path through the pipeline (healthy / DTC present / live-threshold fault / a PID the
 * ECU genuinely doesn't support) so the whole app — PidReader, DtcDecoder,
 * DiagnosticRulesEngine, the AI layer, the UI — can be tested end to end without a
 * physical OBD dongle, exactly as the product spec requires.
 */
enum class SimulatedProfile {
    HEALTHY,
    MISFIRE_DTC,
    OVERHEATING,
    LOW_BATTERY_VOLTAGE,
    VOLTAGE_PID_UNSUPPORTED,
    /**
     * A used car whose seller cleared the codes just before showing it: normal fault list empty,
     * but a permanent code survives, a fault is pending again, the clear-code counters are low,
     * and the engine was warmed up before this start. Also reports an ECU odometer and a
     * standard-format VIN, so every Used Car Check card has something to show.
     */
    SELLER_CLEARED_CODES
}

/**
 * A fake ELM327 that speaks the same AT/OBD text protocol as a real dongle, so it's a
 * drop-in [ObdTransport] — nothing above this layer (PidReader, the rules engine, the UI)
 * can tell the difference. This is also the template for wiring in the new OBD hardware
 * once it arrives: that becomes a third [ObdTransport] implementation, same interface.
 */
class SimulatedObdTransport(private val profile: SimulatedProfile = SimulatedProfile.HEALTHY) : ObdTransport {

    private var connected = false
    private var dtcsCleared = false
    private var connectedAtMs = 0L

    override fun connect() {
        connected = true
        connectedAtMs = System.currentTimeMillis()
    }

    override fun close() {
        connected = false
    }

    override fun sendCommand(command: String): String {
        check(connected) { "SimulatedObdTransport: sendCommand called before connect()" }
        val normalized = command.trim().uppercase()

        if (profile == SimulatedProfile.SELLER_CLEARED_CODES) sellerClearedResponse(normalized)?.let { return it }

        return when {
            normalized == "ATZ" -> "ELM327 v1.5"
            normalized.startsWith("AT") -> "OK"
            normalized == "0100" -> supportedPidsRange0()
            normalized == "0120" -> supportedPidsRange20()
            normalized == "0140" -> supportedPidsRange40()
            normalized == "010C" -> rpmResponse()
            normalized == "0105" -> coolantResponse()
            normalized == "010D" -> speedResponse()
            normalized == "0111" -> throttleResponse()
            normalized == "010F" -> intakeTempResponse()
            normalized == "012F" -> fuelLevelResponse()
            normalized == "0104" -> engineLoadResponse()
            normalized == "0142" -> voltageResponse()
            normalized == "03" -> dtcResponse()
            normalized == "04" -> { dtcsCleared = true; "OK" }
            normalized == "0101" -> readinessResponse()
            normalized == "0902" -> mode09Response(pid = 0x02, countByte = 0x01, ascii = DEMO_VIN)
            normalized == "090A" -> mode09Response(pid = 0x0A, countByte = null, ascii = DEMO_ECU_NAME)
            normalized == "0904" -> mode09Response(pid = 0x04, countByte = null, ascii = DEMO_CALIBRATION_ID)
            else -> "NO DATA"
        }
    }

    /** [SimulatedProfile.SELLER_CLEARED_CODES] overrides; null falls through to the shared responses. */
    private fun sellerClearedResponse(command: String): String? = when (command) {
        "0100" -> "41 00 18 1A 80 02" // shared set + 0x1F (run time since start)
        "0120" -> "41 20 00 03 80 00" // 0x2F fuel level, 0x30 warm-ups, 0x31 distance since cleared
        "0140" -> "41 40 40 04 00 00" // 0x42 voltage, 0x4E time since cleared
        "01A0" -> "41 A0 04 00 00 00" // 0xA6 odometer
        "011F" -> "41 1F 00 5A" // engine started 90 s ago...
        "0105" -> "41 05 7D" // ...yet coolant is already 85°C
        "0130" -> "41 30 03" // 3 warm-ups since codes cleared
        "0131" -> "41 31 00 12" // 18 km since codes cleared
        "014E" -> "41 4E 00 28" // 40 minutes since codes cleared
        "01A6" -> "41 A6 00 0C D9 79" // 84,210.5 km
        "03" -> "43 00 00 00 00 00 00" // normal fault list cleared
        "07" -> if (dtcsCleared) "47 00 00 00 00 00 00" else "47 01 71 00 00 00 00" // pending P0171
        "0A" -> "4A 04 20 00 00 00 00" // permanent P0420 — Mode 04 can't erase it
        "0101" -> "41 01 00 07 21 01" // MIL off, 0 DTCs, catalyst monitor not complete
        "0902" -> mode09Response(pid = 0x02, countByte = 0x01, ascii = SELLER_CLEARED_VIN)
        else -> null
    }

    /** Byte A: MIL off, 1 DTC stored for MISFIRE_DTC else 0. Byte B: spark ignition, misfire+fuel+components all supported and complete except misfire on MISFIRE_DTC. Bytes C/D: catalyst+O2 sensor supported and complete, everything else not supported (0). */
    private fun readinessResponse(): String {
        val dtcCount = if (profile == SimulatedProfile.MISFIRE_DTC && !dtcsCleared) 1 else 0
        val byteA = dtcCount and 0x7F // MIL off
        val misfireIncomplete = profile == SimulatedProfile.MISFIRE_DTC && !dtcsCleared
        val byteB = 0x07 or (if (misfireIncomplete) 0x10 else 0x00) // B0-2 all supported; B4 set only while misfire test hasn't rerun
        val byteC = 0x21 // Catalyst (C0) + Oxygen Sensor (C5) supported
        val byteD = 0x00 // both complete (0 = complete)
        return "41 01 %02X %02X %02X %02X".format(byteA, byteB, byteC, byteD)
    }

    private fun mode09Response(pid: Int, countByte: Int?, ascii: String): String {
        val bytes = mutableListOf(0x49, pid)
        if (countByte != null) bytes.add(countByte)
        bytes.addAll(ascii.map { it.code })
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    private fun supportedPidsRange0() = "41 00 18 1A 80 00" // marks 0x04,0x05,0x0C,0x0D,0x0F,0x11 supported
    private fun supportedPidsRange20() = "41 20 00 02 00 00" // marks 0x2F (fuel level) supported

    private fun supportedPidsRange40(): String = when (profile) {
        SimulatedProfile.VOLTAGE_PID_UNSUPPORTED -> "41 40 00 00 00 00" // 0x42 deliberately NOT marked supported
        else -> "41 40 40 00 00 00" // marks 0x42 (module voltage) supported
    }

    private fun rpmResponse(): String = when (profile) {
        SimulatedProfile.HEALTHY, SimulatedProfile.MISFIRE_DTC -> "41 0C 1A F8" // ~1726 rpm
        else -> "41 0C 0A F0" // ~700 rpm idle
    }

    private fun coolantResponse(): String = when (profile) {
        SimulatedProfile.OVERHEATING -> "41 05 9B" // 115C
        else -> "41 05 82" // 90C, normal
    }

    /**
     * A realistic accelerate-hold-brake trace instead of a static "0 km/h" — makes test
     * mode actually exercise Live Data and the Performance test end to end without needing
     * real hardware, consistent with the "simulated test mode for every scenario" goal.
     * A plain one-shot scan still effectively reads ~0 km/h most of the time (elapsed since
     * connect is usually small), matching the old "parked" behavior for that case.
     */
    private fun speedResponse(): String {
        val elapsedSeconds = (System.currentTimeMillis() - connectedAtMs) / 1000.0
        val speed = when {
            elapsedSeconds < 6.0 -> (elapsedSeconds / 6.0 * 80).toInt()
            elapsedSeconds < 9.0 -> 80
            elapsedSeconds < 12.0 -> (80 * (1 - (elapsedSeconds - 9.0) / 3.0)).toInt()
            else -> 0
        }.coerceIn(0, 255)
        return "41 0D %02X".format(speed)
    }

    private fun throttleResponse() = "41 11 24" // ~14%

    private fun intakeTempResponse() = "41 0F 4B" // 35C

    private fun fuelLevelResponse() = "41 2F 99" // ~60%

    private fun engineLoadResponse() = "41 04 33" // ~20%

    private fun voltageResponse(): String = when (profile) {
        SimulatedProfile.LOW_BATTERY_VOLTAGE -> "41 42 2A 30" // 10.8V
        else -> "41 42 37 78" // 14.2V, normal
    }

    private fun dtcResponse(): String {
        if (dtcsCleared) return "43 00 00 00 00 00 00"
        return when (profile) {
            SimulatedProfile.MISFIRE_DTC -> "43 03 01 00 00 00 00" // encodes P0301
            else -> "43 00 00 00 00 00 00" // no stored codes
        }
    }

    companion object {
        private const val DEMO_VIN = "DEMO1234567890VIN" // 17 chars, clearly fake — test mode only
        private const val DEMO_ECU_NAME = "ENGINE ECU"
        private const val DEMO_CALIBRATION_ID = "SW1.0.4"
        // Standard 17-character format, sent with the RTO lookup for the chassis match; "XX9" isn't
        // a real manufacturer code, so it can never match a real RC.
        private const val SELLER_CLEARED_VIN = "XX9SAMPLEK0123456"
    }
}
