package com.enginesense.app.vehicle.ai

/**
 * Abstraction over [com.enginesense.app.obd.DtcRepository]'s explain() so the chat service can
 * look up ANY standard code (not just the ~20 curated in [CommonDtcGlossary]) without this
 * module depending on Android Context — DtcRepository wraps an on-device SQLite database and
 * needs one. Production wires the real repository in (see GetAutoSureViewModel); plain-Kotlin
 * unit tests can supply a trivial fake instead of needing Robolectric just for this.
 */
interface GenericDtcExplainer {
    /** Null if the code isn't recognized by the underlying database at all. */
    fun explain(code: String, vehicleBrand: String?): GenericDtcExplanation?
}

data class GenericDtcExplanation(
    val code: String,
    val plainLanguage: String,
    /** CRITICAL/MODERATE/MINOR/INFO — matches [com.enginesense.app.obd.Severity]'s names. */
    val severity: String
)
