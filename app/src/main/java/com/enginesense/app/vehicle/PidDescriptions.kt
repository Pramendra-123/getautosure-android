package com.enginesense.app.vehicle

/**
 * One plain-language sentence per [PidId] — what a non-technical owner should take away
 * from it, not what it technically measures. Kept separate from [PidId] itself: that enum
 * is the protocol definition (how to ask for it, how to decode it), this is presentation
 * content for the "All sensors" screen. Every entry here should be answerable in one
 * sentence; if a sensor needs more than that to explain, it belongs in the AI report's
 * findings instead, not a tooltip.
 */
fun PidId.plainDescription(): String = when (this) {
    PidId.ENGINE_LOAD -> "How hard the engine is working right now, as a percentage of its max."
    PidId.COOLANT_TEMP_C -> "How hot the engine is running — the most important one to watch for overheating."
    PidId.SHORT_TERM_FUEL_TRIM_1 -> "How much the computer is adjusting the fuel mix moment-to-moment. Far from 0% often means a vacuum leak or dirty injector."
    PidId.LONG_TERM_FUEL_TRIM_1 -> "The longer-term version of fuel trim — same meaning, averaged over time."
    PidId.FUEL_PRESSURE -> "Pressure of fuel being delivered to the engine. Low pressure can cause rough running or hard starts."
    PidId.INTAKE_MANIFOLD_PRESSURE -> "Vacuum/pressure inside the engine's intake. Used to spot vacuum leaks."
    PidId.MAF_AIR_FLOW_RATE -> "How much air the engine is pulling in. A key input the computer uses to decide how much fuel to add."
    PidId.O2_SENSOR_B1S1_VOLTAGE -> "How rich or lean the exhaust is reading just after the engine. Swinging steadily is healthy; stuck high or low suggests a sensor or mixture problem."
    PidId.O2_WIDEBAND_B1S1_LAMBDA -> "How rich or lean the exhaust is, from a wide-band sensor. 1.00 is the ideal mixture; below is rich, above is lean. Briefly leaving 1.00 on acceleration or coasting is normal."
    PidId.INTAKE_AIR_TEMP_C -> "How hot the air entering the engine is. Mostly background info."
    PidId.RPM -> "How fast the engine is spinning right now."
    PidId.SPEED_KMH -> "How fast the car is going."
    PidId.TIMING_ADVANCE -> "How early the spark plug fires. Technical — worth flagging unusual values to a mechanic."
    PidId.THROTTLE_POSITION -> "How far the engine's air valve is actually open."
    PidId.RUN_TIME_SINCE_START -> "How long since you started the car. Gives context to other readings."
    PidId.DISTANCE_WITH_MIL_ON -> "How far you've driven since the check-engine light came on. Longer distance means more urgency."
    PidId.COMMANDED_EVAP_PURGE -> "How much fuel vapor is being recycled from the gas tank — part of the EVAP system."
    PidId.FUEL_LEVEL -> "How much fuel is left in the tank."
    PidId.DISTANCE_SINCE_CODES_CLEARED -> "How far you've driven since fault codes were last reset."
    PidId.BAROMETRIC_PRESSURE -> "Outside air pressure, related to altitude. Just context, not a fault signal."
    PidId.CATALYST_TEMP_B1S1 -> "How hot the catalytic converter is running. Too cold means it's not cleaning exhaust properly."
    PidId.CONTROL_MODULE_VOLTAGE -> "How much charge your electrical system has while running. Low means a weak battery or failing alternator."
    PidId.ABSOLUTE_LOAD_VALUE -> "A more precise version of Engine Load — mostly a diagnostic number."
    PidId.FUEL_AIR_COMMANDED_RATIO -> "The ideal fuel-to-air mix the computer is aiming for."
    PidId.RELATIVE_THROTTLE_POSITION -> "Another throttle reading, used to cross-check against the main one."
    PidId.AMBIENT_AIR_TEMP_C -> "The outside temperature, as measured by the car."
    PidId.ABSOLUTE_THROTTLE_POSITION_B -> "A second throttle sensor reading — cars use more than one for safety."
    PidId.ACCELERATOR_PEDAL_POSITION_D -> "What the physical gas pedal sensor is reading."
    PidId.ACCELERATOR_PEDAL_POSITION_E -> "A second gas pedal sensor reading, for safety redundancy."
    PidId.COMMANDED_THROTTLE_ACTUATOR -> "How far the computer is telling the throttle to open."
    PidId.FUEL_RAIL_PRESSURE_ABS -> "Pressure of fuel right before injection. Low pressure causes poor starting or power loss."
    PidId.RELATIVE_ACCELERATOR_PEDAL_POSITION -> "Another pedal-sensor reading, same redundancy purpose as the others."
    PidId.ENGINE_OIL_TEMP_C -> "How hot the engine oil is. Oil running too hot protects the engine less."
    PidId.ACTUAL_ENGINE_TORQUE_PERCENT -> "How much pulling power the engine is making right now, versus its maximum."
    PidId.ENGINE_REFERENCE_TORQUE -> "The engine's maximum rated pulling power, for comparison against the actual figure."
}
