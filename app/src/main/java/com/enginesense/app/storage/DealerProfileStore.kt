package com.enginesense.app.storage

import android.content.Context
import org.json.JSONObject
import java.io.File

/** What a dealer has entered about themselves, to stamp on a shared PDF report — single-dealer, local-only, no backend (see DealerScreen's own doc for why). */
data class DealerProfile(
    val name: String,
    val tagline: String,
    val phone: String,
    val location: String
)

fun dealerProfileToJson(profile: DealerProfile): JSONObject = JSONObject().apply {
    put("name", profile.name)
    put("tagline", profile.tagline)
    put("phone", profile.phone)
    put("location", profile.location)
}

fun dealerProfileFromJson(json: JSONObject): DealerProfile = DealerProfile(
    name = json.optString("name", ""),
    tagline = json.optString("tagline", ""),
    phone = json.optString("phone", ""),
    location = json.optString("location", "")
)

/**
 * Same plain-JSON-file approach as [ScanHistoryStore]/[TripMileageStore]. The logo is a separate
 * file ([logoFile]) rather than base64-in-JSON — it's read directly as a [android.graphics.Bitmap]
 * by [com.enginesense.app.report.VehicleReportPdfGenerator], no decoding detour needed.
 */
class DealerProfileStore(context: Context) {

    private val file = File(context.filesDir, "dealer_profile.json")
    val logoFile = File(context.filesDir, "dealer_logo.png")

    /** Null means no dealer profile has been set up — the PDF report stays unbranded (the normal, free, every-user path). */
    @Synchronized
    fun load(): DealerProfile? {
        if (!file.exists()) return null
        return try {
            dealerProfileFromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun save(profile: DealerProfile) {
        file.writeText(dealerProfileToJson(profile).toString())
    }

    fun hasLogo(): Boolean = logoFile.exists()

    @Synchronized
    fun saveLogo(bytes: ByteArray) {
        logoFile.writeBytes(bytes)
    }

    @Synchronized
    fun clearLogo() {
        if (logoFile.exists()) logoFile.delete()
    }
}
