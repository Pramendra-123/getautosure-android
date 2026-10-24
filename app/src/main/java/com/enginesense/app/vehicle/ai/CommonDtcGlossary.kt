package com.enginesense.app.vehicle.ai

/**
 * General knowledge about common, generic (SAE J2012, "P0xxx") OBD-II codes — independent of
 * whether that code is present in the current scan. This is what [RuleBasedAiMechanicChatService]
 * falls back to when a customer asks about a code that isn't one of this car's actual findings
 * (e.g. "what does P0301 mean?" asked out of curiosity, or about a code seen on someone else's
 * car). It is deliberately separate from [com.enginesense.app.obd.DtcRepository]: that class
 * needs an Android Context (it wraps a vendored on-device database) and explains codes this
 * car actually reported; this is a small, pure-Kotlin, unit-testable table of well-known codes
 * for general explanation only. Source: SAE J2012 generic code definitions, cross-checked
 * against the same reference table used in the app's own OBD Scanner learning content — not
 * invented. Covers the codes most likely to come up for a customer asking "what does my car's
 * fault code mean" before or independent of a scan.
 */
data class CommonDtcInfo(
    val code: String,
    val meaning: String,
    val symptoms: String,
    val possibleCauses: String,
    val diagnosticSteps: String,
    /** CRITICAL/MODERATE/MINOR/INFO — matches DiagnosticFinding's Severity scale, as a string to stay framework-agnostic here. */
    val urgency: String,
    val drivingGuidance: String
)

object CommonDtcGlossary {

    private val codes: Map<String, CommonDtcInfo> = listOf(
        CommonDtcInfo(
            "P0100", "Mass Airflow (MAF) sensor circuit malfunction",
            "Rough idle, hesitation on acceleration, poor fuel economy.",
            "Dirty or faulty MAF sensor, loose intake wiring, a dirty air filter restricting airflow.",
            "Inspect the MAF sensor and air filter for dirt/damage; check the wiring connector for looseness or corrosion.",
            "MODERATE", "Usually drivable, but have it checked soon — it affects how much fuel the engine adds."
        ),
        CommonDtcInfo(
            "P0128", "Coolant thermostat below regulating temperature",
            "Engine takes longer than normal to warm up; heater may run cooler than usual.",
            "A stuck-open thermostat, or genuinely low coolant level.",
            "Check coolant level first; if normal, the thermostat itself likely needs replacing.",
            "MINOR", "Safe to keep driving — this affects efficiency and emissions, not safety."
        ),
        CommonDtcInfo(
            "P0171", "System too lean (Bank 1)",
            "Rough idle, hesitation, possible engine misfire under load.",
            "Vacuum leak, a dirty/failing MAF sensor, weak fuel pump, or a clogged fuel injector.",
            "Check for vacuum leaks around intake hoses first — the most common and cheapest cause.",
            "MODERATE", "Drivable short-term, but a sustained lean condition can damage the catalytic converter if ignored."
        ),
        CommonDtcInfo(
            "P0172", "System too rich (Bank 1)",
            "Strong fuel smell, reduced fuel economy, possible black exhaust smoke.",
            "Faulty oxygen sensor, a leaking fuel injector, or a dirty air filter restricting air relative to fuel.",
            "Check the air filter and O2 sensor readings; a leaking injector needs a mechanic to confirm.",
            "MODERATE", "Drivable, but excess unburnt fuel can damage the catalytic converter over time."
        ),
        CommonDtcInfo(
            "P0217", "Engine overheat condition",
            "Temperature gauge in the red zone, possible steam from the bonnet, reduced power.",
            "Low coolant, a failed thermostat, or a cooling fan that isn't running.",
            "Do not open a hot radiator cap. Once cool, check coolant level and look for visible leaks.",
            "CRITICAL", "Stop and let the engine cool. Continuing to drive an overheating engine can cause permanent damage."
        ),
        CommonDtcInfo(
            "P0300", "Random/multiple cylinder misfire detected",
            "Engine shakes or runs unevenly, especially at idle; possible loss of power.",
            "Worn spark plugs, a vacuum leak, or low fuel pressure affecting multiple cylinders.",
            "Spark plugs and ignition coils are the first, cheapest things to inspect.",
            "CRITICAL", "A sustained multi-cylinder misfire can damage the catalytic converter — have it looked at promptly, and stop driving if the check-engine light is flashing."
        ),
        CommonDtcInfo(
            "P0301", "Cylinder 1 misfire detected",
            "A rough, uneven-feeling engine, especially noticeable at idle or under light acceleration; possible check-engine light flashing.",
            "Most often a worn spark plug or failing ignition coil on that cylinder; less commonly a fuel injector issue or low compression.",
            "Spark plug and ignition coil for cylinder 1 are the standard first checks — often done by swapping the coil with another cylinder to see if the misfire moves.",
            "CRITICAL", "If the check-engine light is flashing (not just steady on), that's an active misfire — it's best to stop driving and get it looked at, since continuing can damage the catalytic converter."
        ),
        CommonDtcInfo(
            "P0302", "Cylinder 2 misfire detected",
            "Same as P0301, localized to cylinder 2 — rough running, possible flashing check-engine light.",
            "Spark plug, ignition coil, injector, or compression issue specific to cylinder 2.",
            "Same approach as P0301: check/swap the spark plug and coil for that cylinder first.",
            "CRITICAL", "Same guidance as P0301 — stop driving if the light is flashing."
        ),
        CommonDtcInfo(
            "P0303", "Cylinder 3 misfire detected",
            "Same as P0301, localized to cylinder 3.",
            "Spark plug, ignition coil, injector, or compression issue specific to cylinder 3.",
            "Same approach as P0301: check/swap the spark plug and coil for that cylinder first.",
            "CRITICAL", "Same guidance as P0301 — stop driving if the light is flashing."
        ),
        CommonDtcInfo(
            "P0304", "Cylinder 4 misfire detected",
            "Same as P0301, localized to cylinder 4.",
            "Spark plug, ignition coil, injector, or compression issue specific to cylinder 4.",
            "Same approach as P0301: check/swap the spark plug and coil for that cylinder first.",
            "CRITICAL", "Same guidance as P0301 — stop driving if the light is flashing."
        ),
        CommonDtcInfo(
            "P0325", "Knock sensor circuit malfunction",
            "Usually no noticeable symptom by itself, beyond the warning light.",
            "A faulty knock sensor or damaged wiring to it.",
            "Have the sensor and its wiring tested with a scan tool.",
            "MINOR", "Drivable, but the engine loses a safety check against harmful knocking — get it checked when convenient."
        ),
        CommonDtcInfo(
            "P0340", "Camshaft position sensor circuit malfunction",
            "Possible hard starting, stalling, or rough idle.",
            "Faulty camshaft position sensor or a wiring/connector fault.",
            "Inspect sensor wiring first; the sensor itself is a common, inexpensive replacement.",
            "MODERATE", "Usually drivable, but hard starting can leave you stranded — don't put it off."
        ),
        CommonDtcInfo(
            "P0401", "Exhaust gas recirculation (EGR) flow insufficient",
            "Possible rough idle, pinging/knocking sound under load, or failed emissions test.",
            "A clogged EGR valve or passages.",
            "EGR valve/passage cleaning or replacement.",
            "MINOR", "Safe to keep driving — this mainly affects emissions, not immediate safety."
        ),
        CommonDtcInfo(
            "P0420", "Catalyst system efficiency below threshold (Bank 1)",
            "Often no noticeable symptom beyond the warning light and a failed emissions test.",
            "A failing catalytic converter, or an upstream oxygen sensor giving a false reading.",
            "Check O2 sensor readings before assuming the catalytic converter itself needs replacing — it's the expensive part.",
            "MODERATE", "Drivable, but don't ignore it indefinitely — a failing catalytic converter can eventually affect performance."
        ),
        CommonDtcInfo(
            "P0442", "Evaporative emission (EVAP) system leak detected (small leak)",
            "Usually no noticeable symptom beyond the warning light.",
            "Most commonly a loose or faulty fuel cap; less often a small crack in an EVAP hose.",
            "Check that the fuel cap is tightened properly first — it resolves this code more often than any other cause.",
            "MINOR", "Safe to keep driving — not a safety issue, just an emissions-system leak."
        ),
        CommonDtcInfo(
            "P0455", "Evaporative emission (EVAP) system leak detected (large leak)",
            "Possible fuel smell; otherwise often no symptom beyond the warning light.",
            "Fuel cap missing or badly damaged, or a larger EVAP system leak.",
            "Check the fuel cap first; if present and tight, the EVAP hoses need inspection.",
            "MINOR", "Safe to keep driving, but a fuel smell is worth checking promptly."
        ),
        CommonDtcInfo(
            "P0500", "Vehicle speed sensor malfunction",
            "Speedometer reading incorrectly or not at all; possible issues with cruise control or ABS.",
            "Faulty vehicle speed sensor or its wiring.",
            "Sensor and wiring inspection — a known, generally inexpensive part.",
            "MODERATE", "Drivable, but an incorrect speedometer reading is worth addressing soon for safety and legal reasons."
        ),
        CommonDtcInfo(
            "P0562", "System voltage low",
            "Dim lights, electrical accessories behaving oddly, possible difficulty starting.",
            "Weak or failing battery, a faulty alternator, or loose battery connections.",
            "Check battery terminals and have the alternator's charging output tested.",
            "MODERATE", "Usually drivable short-term, but a weak charging system can leave you stranded — get it checked soon."
        ),
        CommonDtcInfo(
            "P0700", "Transmission control system malfunction",
            "This is a general flag that a transmission-related code exists — the real detail is in a separate, transmission-specific code.",
            "Varies — this code alone doesn't pinpoint the cause.",
            "Scan for the specific transmission code(s) stored alongside this one; that's where the real information is.",
            "MODERATE", "Depends entirely on the specific transmission code found alongside this one."
        )
    ).associateBy { it.code }

    /** Finds a known common code mentioned anywhere in free text, case-insensitively. Returns null if none match. */
    fun findMentioned(text: String): CommonDtcInfo? {
        val upper = text.uppercase()
        return codes.values.firstOrNull { it.code in upper }
    }

    fun lookup(code: String): CommonDtcInfo? = codes[code.uppercase()]
}
