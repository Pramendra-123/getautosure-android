package com.enginesense.app.obd

import android.content.Context
import com.dtcdatabase.DTCDatabase

enum class Severity(val label: String) {
    CRITICAL("Stop driving soon"),
    MODERATE("Get it checked"),
    MINOR("Keep an eye on it"),
    INFO("Informational")
}

data class ExplainedDtc(
    val code: String,
    val rawDescription: String,
    val plainLanguage: String,
    val severity: Severity,
    val isGenericCode: Boolean,
    val isIndiaOverride: Boolean = false
)

/**
 * Wraps the vendored dtc-database library and adds a plain-language layer on top of
 * its raw SAE/manufacturer descriptions, since the free dataset ships technical text only.
 */
class DtcRepository(context: Context) {

    private val db = DTCDatabase.getInstance(context)
    private val overrideDb = IndiaOverrideDb.getInstance(context)
    private val unresolvedLogger = UnresolvedCodeLogger.getInstance(context)

    /**
     * India-market brand -> the manufacturer key to try first. TATA and MAHINDRA resolve
     * against the hand-curated override table (see IndiaOverrideDb) since the vendored
     * free dataset has zero entries for either. MARUTI/SUZUKI resolve against both the
     * override table's Suzuki additions and the vendored dataset's 17 Suzuki codes, since
     * Maruti Suzuki cars run on shared Suzuki global platforms.
     */
    private val indiaBrandProxy = mapOf(
        "MARUTI" to "SUZUKI",
        "MARUTI SUZUKI" to "SUZUKI",
        "SUZUKI" to "SUZUKI",
        "TATA" to "TATA",
        "TATA MOTORS" to "TATA",
        "MAHINDRA" to "MAHINDRA",
        "MAHINDRA & MAHINDRA" to "MAHINDRA",
        "KIA" to "KIA"
    )

    fun explain(code: String, vehicleBrand: String? = null): ExplainedDtc {
        val proxyManufacturer = vehicleBrand?.uppercase()?.let { indiaBrandProxy[it] }

        val override = proxyManufacturer?.let { overrideDb.lookup(it, code) }
        if (override != null) {
            return ExplainedDtc(
                code = code.uppercase(),
                rawDescription = override.description,
                plainLanguage = toPlainLanguage(code, override.description),
                severity = classifySeverity(override.description),
                isGenericCode = false,
                isIndiaOverride = true
            )
        }

        val dtc = db.getDTC(code, proxyManufacturer)
        logIfUnresolved(code, vehicleBrand, dtc)

        val description = dtc?.description ?: "Unrecognized code $code — not in the offline database."
        return ExplainedDtc(
            code = code.uppercase(),
            rawDescription = description,
            plainLanguage = toPlainLanguage(code, description),
            severity = classifySeverity(description),
            isGenericCode = dtc?.isGeneric() ?: true
        )
    }

    /**
     * Records codes worth sourcing next: either not in any database, or a code that looks
     * manufacturer-specific (second character '1') for a known brand but only resolved to
     * a generic fallback — i.e. neither the override table nor the vendored database has
     * this brand's actual meaning for it.
     */
    private fun logIfUnresolved(code: String, vehicleBrand: String?, dtc: DTCDatabase.DTC?) {
        when {
            dtc == null ->
                unresolvedLogger.log(code, vehicleBrand, UnresolvedReason.UNKNOWN_CODE)

            vehicleBrand != null && dtc.isGeneric() && UnresolvedCodeLogger.looksManufacturerSpecific(code) ->
                unresolvedLogger.log(code, vehicleBrand, UnresolvedReason.MANUFACTURER_GAP)
        }
    }

    fun explainAll(codes: List<String>, vehicleBrand: String? = null): List<ExplainedDtc> =
        codes.map { explain(it, vehicleBrand) }

    /**
     * Rewrites the dataset's technical wording into a short, plain-English sentence.
     * This is keyword-based, not a certified translation — treat it as a starting point,
     * not a diagnosis.
     */
    private fun toPlainLanguage(code: String, description: String): String {
        val d = description.lowercase()
        return when {
            "misfire" in d -> "One or more engine cylinders aren't firing correctly — this can cause rough running and, over time, damage the catalytic converter."
            "catalyst" in d || "catalytic" in d -> "The catalytic converter isn't cleaning exhaust gases as efficiently as it should."
            "oxygen sensor" in d || "o2 sensor" in d -> "An oxygen sensor reading looks off, which can hurt fuel economy and emissions."
            "evap" in d || "purge" in d || "vapor" in d -> "There's a small leak or fault in the fuel vapor (EVAP) system — usually not urgent, often just a loose gas cap."
            "knock" in d -> "The engine is detecting abnormal combustion (knocking), which can cause damage if ignored."
            "coolant" in d || "thermostat" in d -> "The engine cooling system isn't behaving as expected — watch the temperature gauge."
            "throttle" in d -> "The throttle position reading is outside its expected range."
            "fuel trim" in d || "lean" in d || "rich" in d -> "The engine's air-fuel mixture is off balance."
            "circuit" in d -> "A sensor or its wiring circuit is reporting a fault — could be the sensor, a connector, or a wiring issue."
            else -> description
        } + "  ($code)"
    }

    /** Heuristic severity bucket based on keywords — not an authoritative safety rating. */
    private fun classifySeverity(description: String): Severity {
        val d = description.lowercase()
        return when {
            "misfire" in d || "knock" in d || "overheat" in d -> Severity.CRITICAL
            "catalyst" in d || "catalytic" in d || "coolant" in d || "fuel trim" in d -> Severity.MODERATE
            "evap" in d || "purge" in d || "vapor" in d -> Severity.MINOR
            else -> Severity.MODERATE
        }
    }
}

/**
 * Adapts [DtcRepository] to [com.enginesense.app.vehicle.ai.GenericDtcExplainer] so the AI
 * Mechanic chat service can explain any standard code this repository knows, not just the
 * curated subset in CommonDtcGlossary — without the `vehicle.ai` package needing an Android
 * Context. Returns null for a code the vendored database genuinely doesn't recognize, rather
 * than passing along its generic "not in the offline database" filler text as if it were a
 * real explanation.
 *
 * Also returns null (rather than throwing) on any underlying database access failure — e.g. a
 * corrupted or unreadable on-device SQLite file — so a lookup problem degrades the chat to its
 * honest "I don't recognize that code" fallback instead of crashing the conversation.
 */
class DtcRepositoryGenericExplainer(private val repository: DtcRepository) :
    com.enginesense.app.vehicle.ai.GenericDtcExplainer {

    override fun explain(code: String, vehicleBrand: String?): com.enginesense.app.vehicle.ai.GenericDtcExplanation? {
        val explained = try {
            repository.explain(code, vehicleBrand)
        } catch (e: Exception) {
            return null
        }
        if (explained.rawDescription.startsWith("Unrecognized code")) return null
        return com.enginesense.app.vehicle.ai.GenericDtcExplanation(
            code = explained.code,
            plainLanguage = explained.plainLanguage,
            severity = explained.severity.name
        )
    }
}
