package com.enginesense.app.dealerreport

data class PhotoUpload(val filename: String, val contentType: String, val base64: String)

/** [options] are picked from a fixed per-category list ([DealerConditionOptions]), not typed — the dealer selects, never writes. */
data class DealerConditionItem(val rating: String, val options: List<String>)

data class DealerConditionInput(
    val exterior: DealerConditionItem,
    val interior: DealerConditionItem,
    val tyres: DealerConditionItem
)

data class PublishedReport(val slug: String, val url: String)

/** One car in "My reports" — the dealer's own view, so [registrationNo] is the full plate. */
data class MyReport(
    val slug: String,
    val url: String,
    val live: Boolean,
    val make: String?,
    val model: String?,
    val variant: String?,
    val year: Int?,
    val registrationNo: String?,
    val priceInr: Double?,
    val photoUrl: String?,
    val createdAtIso: String,
    val soldAtIso: String?,
    /** The saved PDF and Status picture; null for reports made before they were saved. */
    val pdfUrl: String?,
    val statusUrl: String?
) {
    val sold: Boolean get() = soldAtIso != null
    val title: String get() = listOfNotNull(year?.toString(), make, model, variant).joinToString(" ").ifBlank { "Car report" }
}

/**
 * Everything on "My reports": the dealer's reports, newest first, and the counts at the top.
 * [credits] comes from the server's ledger; while [paymentsLive] is false a dealer with none still
 * gets reports free.
 */
data class MyReports(
    val reports: List<MyReport>,
    val made: Int,
    val thisMonth: Int,
    val credits: Int,
    val paymentsLive: Boolean
)

/** Curated option lists per condition category — a dealer picks any number of these (multi-select), never free text, so every report describes condition in the same vocabulary instead of inconsistent typed notes. */
object DealerConditionOptions {
    val exterior = listOf(
        "No visible damage",
        "Minor scratches",
        "Minor dents",
        "Repainted panel(s)",
        "Rust spots",
        "Cracked/chipped windshield",
        "Faded paint",
        "Panel gap/misalignment",
        "Previous accident repair"
    )

    val interior = listOf(
        "Clean, no visible wear",
        "Minor wear on seats",
        "Stains/tears on upholstery",
        "Dashboard in good condition",
        "AC working well",
        "Music/infotainment working",
        "Odour present",
        "Missing interior trim"
    )

    val tyres = listOf(
        "New/like-new tread",
        "Good tread (~70%+)",
        "Moderate wear (~40-70%)",
        "Worn, replacement needed soon",
        "Mismatched tyre brands",
        "Spare tyre available",
        "Spare tyre missing"
    )
}

/** Everything publish_report needs for a Dealer Saathi report (health summary v2 and dealer-declared condition v2, see [saleReportJson]). */
data class SaleReportPublishRequest(
    val dealerName: String,
    val dealerTagline: String,
    val dealerPhone: String,
    val dealerLocation: String,
    val dealerLogo: PhotoUpload?,
    val make: String?,
    val model: String?,
    val variant: String?,
    val year: Int?,
    val fuelType: String?,
    val transmission: String?,
    val odometerKm: Int?,
    val ownership: String?,
    val registrationNo: String?,
    val healthSummary: org.json.JSONObject,
    val dealerCondition: org.json.JSONObject,
    val photos: List<PhotoUpload>,
    val scannedAtIso: String?
)
