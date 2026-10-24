package com.enginesense.app.vehicle.ai

import com.enginesense.app.obd.Severity

data class AiReportSection(val heading: String, val body: String)

data class AiCarHealthReport(
    val headline: String,
    val summary: String,
    val sections: List<AiReportSection>,
    val disclaimer: String
)

/**
 * Turns a validated [AiVehicleContext] into a readable "Car Health Report". The
 * contract: implementations may only *phrase* what's already in [AiVehicleContext] —
 * they must not add findings, invent sensor values, or state anything not traceable to
 * a signal or finding that's already there.
 */
interface AiCarHealthReportService {
    suspend fun generateReport(context: AiVehicleContext): AiCarHealthReport
}

/**
 * Default, production-safe implementation: a deterministic template over the already-
 * validated findings, with no LLM call at all. This is intentional, not a placeholder
 * to "fill in later with a real model" — calling a real LLM from the Android client would
 * mean embedding an API key in the APK, which is a real security issue (anyone can
 * decompile the app and extract it), so that path is deliberately not implemented here.
 * A real AI-phrased report needs a backend proxy endpoint that holds the key server-side;
 * see [RemoteAiCarHealthReportService] for that integration point.
 */
class TemplateAiCarHealthReportService : AiCarHealthReportService {

    override suspend fun generateReport(context: AiVehicleContext): AiCarHealthReport {
        val critical = context.findings.filter { it.severity == Severity.CRITICAL.name }
        val moderate = context.findings.filter { it.severity == Severity.MODERATE.name }
        val minor = context.findings.filter { it.severity == Severity.MINOR.name }

        val headline = when {
            critical.isNotEmpty() -> "Your car needs attention soon"
            moderate.isNotEmpty() -> "A few things worth getting checked"
            context.findings.isEmpty() -> "No issues detected"
            else -> "Mostly healthy, with minor notes"
        }

        val summary = buildString {
            if (context.findings.isEmpty()) {
                append("No stored trouble codes and all readings are within normal range.")
            } else {
                append("${context.findings.size} finding(s): ")
                append("${critical.size} critical, ${moderate.size} to get checked, ${minor.size} minor.")
            }
        }

        val sections = context.findings.map { finding ->
            AiReportSection(
                heading = finding.title,
                body = "${finding.summary}\n\nRecommended: ${finding.recommendedAction}"
            )
        }

        return AiCarHealthReport(
            headline = headline,
            summary = summary,
            sections = sections,
            disclaimer = "Generated from on-device diagnostic rules, not a licensed mechanic. " +
                "For anything above \"minor\", confirm with a real inspection before relying on this."
        )
    }
}

/**
 * Placeholder for a real AI-phrased report via a backend proxy (e.g. a Supabase Edge
 * Function or a small server endpoint that holds the LLM API key and forwards only
 * [AiVehicleContext.toJson]). Intentionally unimplemented — wiring this up is a backend
 * decision (which model, cost per report, rate limiting), not something to improvise
 * silently from the Android client.
 */
class RemoteAiCarHealthReportService(private val endpointUrl: String) : AiCarHealthReportService {
    override suspend fun generateReport(context: AiVehicleContext): AiCarHealthReport {
        throw NotImplementedError(
            "RemoteAiCarHealthReportService needs a backend endpoint at $endpointUrl that " +
                "accepts AiVehicleContext.toJson() and holds the LLM API key server-side. " +
                "Not implemented client-side on purpose — see the class doc."
        )
    }
}
