package com.enginesense.app.vehicle

import com.enginesense.app.obd.Severity
import com.enginesense.app.vehicle.ai.CommonDtcGlossary
import com.enginesense.app.vehicle.ai.RepairCostEntry
import com.enginesense.app.vehicle.ai.RepairCostReference

/**
 * Repair guidance for one scan finding, in the order a driver needs it: what was detected, how
 * urgent it is, what might cause it, what to check, how to get it fixed, and how to confirm the
 * fix worked. Built only from data the app already has — the scan itself, [CommonDtcGlossary]
 * (SAE-based detail for common generic codes) and [RepairCostReference] (tentative costs).
 * Where that data doesn't cover a section, the section is null and the screen says so and
 * offers the AI Mechanic instead; nothing is filled in by guesswork here.
 */
data class RepairGuidance(
    val findingId: String,
    val code: String?,
    val title: String,
    /** 1. What the car detected */
    val meaning: String,
    /** The code's own technical definition, when it differs from [meaning]. */
    val technicalDefinition: String?,
    /** 2. Urgency */
    val severity: Severity,
    val drivingAdvice: String,
    val stopSigns: List<String>,
    val symptoms: String?,
    /** 3. Likely causes — null when this code isn't in the reference library. */
    val possibleCauses: List<String>?,
    /** 4. What to check next — null when not covered. */
    val checks: String?,
    /** 5. Repair paths */
    val costEstimates: List<RepairCostEntry>,
    val mechanicNote: String,
    /** 6. After repair */
    val afterRepair: List<String>,
    /** True when sections 3–4 come from the reference library rather than being unavailable. */
    val hasDetailedReference: Boolean,
)

object RepairGuidanceBuilder {

    /** General safety advice — not specific to any one code, so it's safe to show for every serious finding. */
    private val STOP_SIGNS = listOf(
        "The check-engine light starts flashing",
        "The temperature gauge goes into the red, or you see steam",
        "You smell burning or fuel",
        "The car suddenly loses power or shakes badly",
    )

    private fun defaultDrivingAdvice(severity: Severity): String = when (severity) {
        Severity.CRITICAL -> "Avoid driving except to get it checked, and get it looked at as soon as possible."
        Severity.MODERATE -> "Usually OK to drive gently for now, but get it checked soon."
        Severity.MINOR -> "Safe to keep driving. Get it checked at your next service."
        Severity.INFO -> "No action needed — this is informational."
    }

    fun build(finding: DiagnosticFinding, scanDateLabel: String? = null): RepairGuidance {
        val code = finding.relatedDtcCodes.firstOrNull()
        val ref = code?.let { CommonDtcGlossary.lookup(it) }

        val causes = ref?.possibleCauses?.let(::splitCauses)
        val costText = listOfNotNull(finding.title, finding.plainLanguageSummary, ref?.meaning, ref?.possibleCauses).joinToString(" ")

        return RepairGuidance(
            findingId = finding.id,
            code = code,
            title = ref?.meaning ?: finding.title,
            meaning = finding.plainLanguageSummary,
            technicalDefinition = ref?.meaning?.takeIf { it != finding.plainLanguageSummary },
            severity = finding.severity,
            drivingAdvice = ref?.drivingGuidance ?: defaultDrivingAdvice(finding.severity),
            stopSigns = if (finding.severity == Severity.CRITICAL || finding.severity == Severity.MODERATE) STOP_SIGNS else emptyList(),
            symptoms = ref?.symptoms,
            possibleCauses = causes,
            checks = ref?.diagnosticSteps ?: finding.recommendedAction.takeIf { code == null },
            costEstimates = RepairCostReference.findAllByText(costText),
            mechanicNote = mechanicNote(finding, code, ref?.meaning, ref?.symptoms, scanDateLabel),
            afterRepair = afterRepairSteps(code),
            hasDetailedReference = ref != null,
        )
    }

    /** "Vacuum leak, a dirty MAF sensor, or a clogged injector." -> ["Vacuum leak", "A dirty MAF sensor", "A clogged injector"] */
    fun splitCauses(text: String): List<String> =
        text.trim().trimEnd('.')
            .split(Regex(",\\s*(?:or\\s+)?|\\s+or\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replaceFirstChar(Char::uppercase) }

    private fun mechanicNote(finding: DiagnosticFinding, code: String?, definition: String?, symptoms: String?, scanDateLabel: String?): String =
        buildString {
            append("Hi, my car's OBD scan")
            scanDateLabel?.let { append(" ($it)") }
            if (code != null) {
                append(" shows fault code $code")
                definition?.let { append(" — $it") }
                append(".")
            } else {
                append(" flagged: ${finding.title}. ${finding.plainLanguageSummary}")
            }
            if (symptoms != null) append(" Common symptoms for this are: ${symptoms.trimEnd('.').replaceFirstChar(Char::lowercase)}.")
            append(" Could you diagnose the actual cause before replacing any parts, and share an itemised quote?")
        }

    private fun afterRepairSteps(code: String?): List<String> = buildList {
        add("Ask the mechanic to clear the code only after the repair — clearing it without fixing the cause just hides it.")
        add("Scan again with GetAutoSure right after the repair to confirm no codes are stored.")
        add("Drive normally for 2–3 days (around 50 km of mixed city and highway driving) so the car re-runs its self-tests, then scan once more.")
        if (code != null) {
            add("If $code comes back, the root cause wasn't fixed — show the mechanic both scans rather than paying for the same part twice.")
        } else {
            add("If the same warning comes back, the root cause wasn't fixed — show the mechanic both scans.")
        }
    }
}
