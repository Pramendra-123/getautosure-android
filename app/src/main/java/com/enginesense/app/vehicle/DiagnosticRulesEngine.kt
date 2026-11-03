package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity

data class DiagnosticFinding(
    val id: String,
    val title: String,
    val plainLanguageSummary: String,
    val severity: Severity,
    val relatedDtcCodes: List<String> = emptyList(),
    val relatedPids: List<PidId> = emptyList(),
    val recommendedAction: String,
    /** Which of the car's code lists this came from, for code-based findings; null for live-reading findings. */
    val dtcSource: DtcSource? = null
) {
    /** A pending or permanent code: the car remembers a fault, but it isn't a confirmed, active one right now. */
    val isUnconfirmedCode: Boolean get() = dtcSource == DtcSource.PENDING || dtcSource == DtcSource.PERMANENT
}

/**
 * Pure, deterministic evaluation of a [VehicleSnapshot] into [DiagnosticFinding]s. This
 * is the "source of truth" layer the product spec requires: no AI involved, no network
 * calls, same input always produces the same output. The AI layer downstream is only
 * ever allowed to *explain* these findings, never invent new ones.
 *
 * DTC-derived findings reuse whatever plain-language explanation the caller already
 * computed (via DtcRepository, which needs an Android Context this class deliberately
 * avoids depending on, so the engine itself stays a plain-JVM-testable component).
 * Live-PID-threshold findings are new: they catch problems a dealer's checklist or a
 * stored DTC might miss because the fault hasn't tripped the MIL yet.
 */
object DiagnosticRulesEngine {

    fun evaluate(
        snapshot: VehicleSnapshot,
        dtcExplanations: Map<String, DtcExplanationInput>
    ): List<DiagnosticFinding> {
        val findings = mutableListOf<DiagnosticFinding>()

        val storedCodes = snapshot.dtcs.filter { it.source == DtcSource.STORED }.map { it.code.uppercase() }.distinct()
        for (code in storedCodes) {
            val explanation = dtcExplanations[code]
            findings.add(
                DiagnosticFinding(
                    id = "DTC_$code",
                    title = explanation?.title ?: "Code $code",
                    plainLanguageSummary = explanation?.plainLanguage
                        ?: "Code $code was reported but isn't in the offline database yet.",
                    severity = explanation?.severity ?: Severity.MODERATE,
                    relatedDtcCodes = listOf(code),
                    recommendedAction = severityToAction(explanation?.severity ?: Severity.MODERATE),
                    dtcSource = DtcSource.STORED
                )
            )
        }

        // Pending and permanent codes aren't confirmed active faults, so they count for less than a
        // stored one — but a car that still remembers a fault can't read as a clean 100.
        fun addUnconfirmed(source: DtcSource, severity: Severity, summary: (String) -> String) {
            snapshot.dtcs.filter { it.source == source }.map { it.code.uppercase() }.distinct()
                .filter { it !in storedCodes }
                .forEach { code ->
                    findings.add(
                        DiagnosticFinding(
                            id = "${source.name}_$code",
                            title = dtcExplanations[code]?.title ?: "Code $code",
                            plainLanguageSummary = summary(code),
                            severity = severity,
                            relatedDtcCodes = listOf(code),
                            recommendedAction = severityToAction(severity),
                            dtcSource = source
                        )
                    )
                }
        }
        addUnconfirmed(DtcSource.PERMANENT, Severity.MODERATE) { code ->
            "The car still holds $code as a permanent code. It was cleared from the normal list, but the car hasn't yet confirmed the fault is fixed."
        }
        addUnconfirmed(DtcSource.PENDING, Severity.MINOR) { code ->
            "The car noticed $code once but hasn't confirmed it yet. It may turn into a stored fault."
        }

        findings.addAll(liveThresholdFindings(snapshot))

        return findings.sortedBy { severityRank(it.severity) }
    }

    private fun liveThresholdFindings(snapshot: VehicleSnapshot): List<DiagnosticFinding> {
        val findings = mutableListOf<DiagnosticFinding>()

        snapshot.verifiedValue(PidId.COOLANT_TEMP_C)?.let { tempC ->
            if (tempC >= 110.0) {
                findings.add(
                    DiagnosticFinding(
                        id = "LIVE_OVERHEATING",
                        title = "Engine running hot",
                        plainLanguageSummary = "Coolant temperature is ${tempC.toInt()}°C — that's above the normal operating range and can damage the engine if it continues.",
                        severity = Severity.CRITICAL,
                        relatedPids = listOf(PidId.COOLANT_TEMP_C),
                        recommendedAction = severityToAction(Severity.CRITICAL)
                    )
                )
            }
        }

        snapshot.verifiedValue(PidId.CONTROL_MODULE_VOLTAGE)?.let { volts ->
            if (volts < 11.5) {
                findings.add(
                    DiagnosticFinding(
                        id = "LIVE_LOW_VOLTAGE",
                        title = "Low battery/charging voltage",
                        plainLanguageSummary = "System voltage is ${"%.1f".format(volts)}V while the engine is running — normal is roughly 13.5-14.5V. The battery or alternator may need attention.",
                        severity = Severity.MODERATE,
                        relatedPids = listOf(PidId.CONTROL_MODULE_VOLTAGE),
                        recommendedAction = severityToAction(Severity.MODERATE)
                    )
                )
            } else if (volts > 15.5) {
                findings.add(
                    DiagnosticFinding(
                        id = "LIVE_HIGH_VOLTAGE",
                        title = "High charging voltage",
                        plainLanguageSummary = "System voltage is ${"%.1f".format(volts)}V — that's higher than normal and can point to a faulty voltage regulator, which can damage the battery over time.",
                        severity = Severity.MODERATE,
                        relatedPids = listOf(PidId.CONTROL_MODULE_VOLTAGE),
                        recommendedAction = severityToAction(Severity.MODERATE)
                    )
                )
            }
            Unit
        }

        return findings
    }

    private fun severityRank(severity: Severity): Int = when (severity) {
        Severity.CRITICAL -> 0
        Severity.MODERATE -> 1
        Severity.MINOR -> 2
        Severity.INFO -> 3
    }

    private fun severityToAction(severity: Severity): String = when (severity) {
        Severity.CRITICAL -> "Stop driving and get this checked as soon as possible."
        Severity.MODERATE -> "Get this looked at by a mechanic soon — safe to drive carefully in the meantime."
        Severity.MINOR -> "Worth mentioning at your next service; not urgent."
        Severity.INFO -> "No action needed."
    }
}

/**
 * The minimal subset of [com.enginesense.app.obd.DtcRepository]'s DTC explanation that
 * the rules engine needs, so this file doesn't have to depend on Android's Context (which
 * DtcRepository requires to load the bundled databases) just to stay pure/testable.
 */
data class DtcExplanationInput(
    val plainLanguage: String,
    val severity: Severity,
    /** A short plain name for the code, like "Cylinder 1 misfire". Null when the code isn't recognized. */
    val title: String? = null
)
