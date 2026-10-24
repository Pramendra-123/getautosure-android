package com.enginesense.app.vehicle

import kotlin.math.abs

/** Same shape as [FuelCombustionInsight] (which predates this and keeps its own name/tests) — reused for Engine and Cooling's detailed diagnosis. */
data class DetailedDiagnosisInsight(
    val primarySignal: SignalReading?,
    val supportingSignals: List<SignalReading>,
    /** A deterministic, templated sentence from the actual readings — never a real AI call, never invented. */
    val interpretation: String
)

/**
 * The Premium "Detailed Repair Diagnosis" treatment for Engine, extending the same
 * cross-signal-interpretation idea [FuelCombustionAnalyzer] already does for Fuel &
 * Combustion. Deliberately stays descriptive of what THIS scan shows rather than claiming
 * to know "behavior" (idle stability, warm-up curve) — a single snapshot can't support that
 * claim, only a reading over time could, and nothing here fakes a trend it doesn't have.
 */
object EngineDiagnosisAnalyzer {
    fun analyze(snapshot: VehicleSnapshot, categoryFindings: List<DiagnosticFinding>): DetailedDiagnosisInsight {
        val rpm = snapshot.verifiedValue(PidId.RPM)
        val load = snapshot.verifiedValue(PidId.ENGINE_LOAD)
        val throttle = snapshot.verifiedValue(PidId.THROTTLE_POSITION)
        val timing = snapshot.verifiedValue(PidId.TIMING_ADVANCE)
        val map = snapshot.verifiedValue(PidId.INTAKE_MANIFOLD_PRESSURE)

        val primary = rpm?.let { SignalReading("Engine RPM", "${it.toInt()} rpm") }
        val supporting = buildList {
            load?.let { add(SignalReading("Engine load", "${it.toInt()}%")) }
            throttle?.let { add(SignalReading("Throttle position", "${it.toInt()}%")) }
            map?.let { add(SignalReading("Manifold pressure", "${it.toInt()} kPa")) }
            timing?.let { add(SignalReading("Ignition timing", "${"%.1f".format(it)}°")) }
        }

        categoryFindings.firstOrNull()?.let {
            return DetailedDiagnosisInsight(primary, supporting, it.plainLanguageSummary)
        }

        if (rpm == null && load == null) {
            return DetailedDiagnosisInsight(primary, supporting, "No engine operating data this scan.")
        }
        val parts = buildList {
            rpm?.let { add("running at ${it.toInt()} rpm") }
            load?.let { add("${it.toInt()}% load") }
            throttle?.let { add("throttle at ${it.toInt()}%") }
        }
        val base = "Engine is " + parts.joinToString(", ") + " on this scan."
        val timingNote = timing?.takeIf { abs(it) > 20.0 }
            ?.let { " Ignition timing (${"%.1f".format(it)}°) is further from typical than usual — worth a mechanic's look if this keeps showing up." }
            ?: ""
        return DetailedDiagnosisInsight(primary, supporting, "$base$timingNote No faults were detected in this category this scan.")
    }
}

/** The Premium "Detailed Repair Diagnosis" treatment for Cooling — same approach as [EngineDiagnosisAnalyzer]. */
object CoolingDiagnosisAnalyzer {
    fun analyze(snapshot: VehicleSnapshot, categoryFindings: List<DiagnosticFinding>): DetailedDiagnosisInsight {
        val coolant = snapshot.verifiedValue(PidId.COOLANT_TEMP_C)
        val ambient = snapshot.verifiedValue(PidId.AMBIENT_AIR_TEMP_C)
        val intake = snapshot.verifiedValue(PidId.INTAKE_AIR_TEMP_C)
        val catalyst = snapshot.verifiedValue(PidId.CATALYST_TEMP_B1S1)

        val primary = coolant?.let { SignalReading("Coolant temperature", "${it.toInt()}°C") }
        val supporting = buildList {
            ambient?.let { add(SignalReading("Ambient temperature", "${it.toInt()}°C")) }
            intake?.let { add(SignalReading("Intake air temperature", "${it.toInt()}°C")) }
            catalyst?.let { add(SignalReading("Catalyst temperature", "${it.toInt()}°C")) }
        }

        categoryFindings.firstOrNull()?.let {
            return DetailedDiagnosisInsight(primary, supporting, it.plainLanguageSummary)
        }

        if (coolant == null) {
            return DetailedDiagnosisInsight(primary, supporting, "No coolant temperature data this scan.")
        }
        val state = when {
            coolant in 80.0..105.0 -> "within its normal operating range"
            coolant < 80.0 -> "still below normal operating temperature — the engine may not be fully warmed up"
            else -> "above the normal operating range"
        }
        val ambientNote = ambient?.let { " Ambient temperature was ${it.toInt()}°C at the time, for context." } ?: ""
        return DetailedDiagnosisInsight(primary, supporting, "Coolant is ${coolant.toInt()}°C, $state.$ambientNote No faults were detected in this category this scan.")
    }
}
