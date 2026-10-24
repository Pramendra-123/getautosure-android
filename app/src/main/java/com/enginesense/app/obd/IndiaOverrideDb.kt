package com.enginesense.app.obd

import android.content.Context
import org.json.JSONArray

data class OverrideDtc(val manufacturer: String, val code: String, val description: String)

/**
 * Hand-curated India-market manufacturer-specific codes, sourced to fill the gap in the
 * vendored dtc-database library (which has zero Hyundai/Tata/Mahindra entries and only
 * 17 Suzuki ones). Loaded once from assets/india_override_codes.json and checked by
 * DtcRepository before falling back to the vendored generic/manufacturer database.
 *
 * Sources (see README for detail): obdcode.org (Tata), avtotachki.com (Mahindra, Suzuki
 * additions, translated from Russian). Compiled manually — not a live or licensed feed.
 */
class IndiaOverrideDb private constructor(entries: List<OverrideDtc>) {

    private val byKey: Map<String, OverrideDtc> =
        entries.associateBy { key(it.manufacturer, it.code) }

    fun lookup(manufacturer: String, code: String): OverrideDtc? =
        byKey[key(manufacturer, code)]

    private fun key(manufacturer: String, code: String) =
        "${manufacturer.uppercase()}:${code.uppercase()}"

    companion object {
        @Volatile private var instance: IndiaOverrideDb? = null

        fun getInstance(context: Context): IndiaOverrideDb =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): IndiaOverrideDb {
            val text = context.assets.open("india_override_codes.json")
                .bufferedReader()
                .use { it.readText() }

            val array = JSONArray(text)
            val entries = (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                OverrideDtc(
                    manufacturer = obj.getString("manufacturer"),
                    code = obj.getString("code"),
                    description = obj.getString("description")
                )
            }
            return IndiaOverrideDb(entries)
        }
    }
}
