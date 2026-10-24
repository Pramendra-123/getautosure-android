package com.enginesense.app.vehicle

/** Standard SAE J2012 DTC code-range groups — not GetAutoSure's own 6-category health system,
 *  a different, standard classification purely by which numeric range a fault code falls in.
 *  Shared by the Fault Codes screen and the PDF report so both agree on one grouping. */
enum class ScannedSystemGroup(val label: String, val codeRange: String) {
    FUEL_AIR_METERING("Fuel & air metering", "P0100–P0299"),
    IGNITION_MISFIRE("Ignition system & misfire", "P0300–P0399"),
    AUX_EMISSION("Auxiliary emission control", "P0400–P0499"),
    SPEED_IDLE_CONTROL("Vehicle speed & idle control", "P0500–P0599"),
    COMPUTER_OUTPUT("Computer output circuit", "P0600–P0699"),
    TRANSMISSION("Transmission", "P0700–P0899"),
    OTHERS("Others", "P0900+")
}

/** Only generic P0xxx codes map cleanly to these standard ranges; anything else honestly falls into Others rather than being force-fit. */
fun scannedSystemGroupFor(code: String): ScannedSystemGroup {
    val upper = code.uppercase()
    if (upper.length < 5 || upper[0] != 'P' || upper[1] != '0') return ScannedSystemGroup.OTHERS
    val num = upper.substring(2, 5).toIntOrNull() ?: return ScannedSystemGroup.OTHERS
    return when (num) {
        in 100..299 -> ScannedSystemGroup.FUEL_AIR_METERING
        in 300..399 -> ScannedSystemGroup.IGNITION_MISFIRE
        in 400..499 -> ScannedSystemGroup.AUX_EMISSION
        in 500..599 -> ScannedSystemGroup.SPEED_IDLE_CONTROL
        in 600..699 -> ScannedSystemGroup.COMPUTER_OUTPUT
        in 700..899 -> ScannedSystemGroup.TRANSMISSION
        else -> ScannedSystemGroup.OTHERS
    }
}
