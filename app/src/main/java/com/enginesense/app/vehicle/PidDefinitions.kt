package com.enginesense.app.vehicle

/**
 * One entry per supported Mode 01 PID: how to ask for it, how to decode the response
 * bytes into a physical value, and the plausible range used to flag [PidStatus.IMPLAUSIBLE]
 * readings (a stuck/noisy sensor or a mis-decoded response). Adding a new PID means adding
 * one entry here — nothing else in the pipeline changes.
 */
enum class PidId(
    val pid: Int,
    val displayName: String,
    val unit: String,
    val minBytes: Int,
    val decode: (List<Int>) -> Double,
    val plausibleRange: ClosedFloatingPointRange<Double>
) {
    ENGINE_LOAD(0x04, "Engine Load", "%", 1, { b -> b[0] * 100.0 / 255.0 }, 0.0..100.0),
    COOLANT_TEMP_C(0x05, "Coolant Temperature", "°C", 1, { b -> (b[0] - 40).toDouble() }, -40.0..215.0),
    SHORT_TERM_FUEL_TRIM_1(0x06, "Short-Term Fuel Trim", "%", 1, { b -> (b[0] - 128) * 100.0 / 128.0 }, -100.0..99.2),
    LONG_TERM_FUEL_TRIM_1(0x07, "Long-Term Fuel Trim", "%", 1, { b -> (b[0] - 128) * 100.0 / 128.0 }, -100.0..99.2),
    FUEL_PRESSURE(0x0A, "Fuel Pressure", "kPa", 1, { b -> b[0] * 3.0 }, 0.0..765.0),
    INTAKE_MANIFOLD_PRESSURE(0x0B, "Intake Manifold Pressure", "kPa", 1, { b -> b[0].toDouble() }, 0.0..255.0),
    MAF_AIR_FLOW_RATE(0x10, "Air Flow Rate", "g/s", 2, { b -> ((b[0] * 256.0) + b[1]) / 100.0 }, 0.0..655.35),
    O2_SENSOR_B1S1_VOLTAGE(0x14, "Oxygen Sensor Voltage (Bank 1 Sensor 1)", "V", 2, { b -> b[0] / 200.0 }, 0.0..1.275),
    INTAKE_AIR_TEMP_C(0x0F, "Intake Air Temperature", "°C", 1, { b -> (b[0] - 40).toDouble() }, -40.0..215.0),
    RPM(0x0C, "Engine RPM", "rpm", 2, { b -> ((b[0] * 256.0) + b[1]) / 4.0 }, 0.0..8000.0),
    SPEED_KMH(0x0D, "Vehicle Speed", "km/h", 1, { b -> b[0].toDouble() }, 0.0..255.0),
    TIMING_ADVANCE(0x0E, "Ignition Timing Advance", "°", 1, { b -> (b[0] - 128) / 2.0 }, -64.0..63.5),
    THROTTLE_POSITION(0x11, "Throttle Position", "%", 1, { b -> b[0] * 100.0 / 255.0 }, 0.0..100.0),
    RUN_TIME_SINCE_START(0x1F, "Run Time Since Engine Start", "s", 2, { b -> (b[0] * 256.0) + b[1] }, 0.0..65535.0),
    DISTANCE_WITH_MIL_ON(0x21, "Distance Driven With Warning Light On", "km", 2, { b -> (b[0] * 256.0) + b[1] }, 0.0..65535.0),
    COMMANDED_EVAP_PURGE(0x2E, "Commanded Evaporative Purge", "%", 1, { b -> b[0] * 100.0 / 255.0 }, 0.0..100.0),
    FUEL_LEVEL(0x2F, "Fuel Level", "%", 1, { b -> b[0] * 100.0 / 255.0 }, 0.0..100.0),
    DISTANCE_SINCE_CODES_CLEARED(0x31, "Distance Since Codes Cleared", "km", 2, { b -> (b[0] * 256.0) + b[1] }, 0.0..65535.0),
    BAROMETRIC_PRESSURE(0x33, "Barometric Pressure", "kPa", 1, { b -> b[0].toDouble() }, 0.0..255.0),
    CATALYST_TEMP_B1S1(0x3C, "Catalyst Temperature", "°C", 2, { b -> ((b[0] * 256.0) + b[1]) / 10.0 - 40.0 }, -40.0..900.0),
    CONTROL_MODULE_VOLTAGE(0x42, "Battery/Module Voltage", "V", 2, { b -> ((b[0] * 256.0) + b[1]) / 1000.0 }, 6.0..18.0),
    ABSOLUTE_LOAD_VALUE(0x43, "Absolute Engine Load", "%", 2, { b -> ((b[0] * 256.0) + b[1]) * 100.0 / 255.0 }, 0.0..300.0),
    FUEL_AIR_COMMANDED_RATIO(0x44, "Commanded Fuel/Air Ratio", "", 2, { b -> ((b[0] * 256.0) + b[1]) / 32768.0 }, 0.0..2.0),
    RELATIVE_THROTTLE_POSITION(0x45, "Relative Throttle Position", "%", 1, { b -> b[0] * 100.0 / 255.0 }, 0.0..100.0),
    AMBIENT_AIR_TEMP_C(0x46, "Ambient Air Temperature", "°C", 1, { b -> (b[0] - 40).toDouble() }, -40.0..100.0),
    ABSOLUTE_THROTTLE_POSITION_B(0x47, "Absolute Throttle Position B", "%", 1, { b -> b[0] * 100.0 / 255.0 }, 0.0..100.0),
    ACCELERATOR_PEDAL_POSITION_D(0x49, "Accelerator Pedal Position D", "%", 1, { b -> b[0] * 100.0 / 255.0 }, 0.0..100.0),
    ACCELERATOR_PEDAL_POSITION_E(0x4A, "Accelerator Pedal Position E", "%", 1, { b -> b[0] * 100.0 / 255.0 }, 0.0..100.0),
    COMMANDED_THROTTLE_ACTUATOR(0x4C, "Commanded Throttle Actuator", "%", 1, { b -> b[0] * 100.0 / 255.0 }, 0.0..100.0),
    FUEL_RAIL_PRESSURE_ABS(0x59, "Fuel Rail Pressure", "kPa", 2, { b -> ((b[0] * 256.0) + b[1]) * 10.0 }, 0.0..655350.0),
    RELATIVE_ACCELERATOR_PEDAL_POSITION(0x5A, "Relative Accelerator Pedal Position", "%", 1, { b -> b[0] * 100.0 / 255.0 }, 0.0..100.0),
    ENGINE_OIL_TEMP_C(0x5C, "Engine Oil Temperature", "°C", 1, { b -> (b[0] - 40).toDouble() }, -40.0..210.0),
    ACTUAL_ENGINE_TORQUE_PERCENT(0x62, "Actual Engine Torque", "%", 1, { b -> (b[0] - 125).toDouble() }, -125.0..130.0),
    ENGINE_REFERENCE_TORQUE(0x63, "Engine Reference Torque", "N·m", 2, { b -> (b[0] * 256.0) + b[1] }, 0.0..65535.0);

    /** The Mode 01 command string for this PID, e.g. "010C" for RPM. */
    val obdCommand: String get() = "01%02X".format(pid)
}
