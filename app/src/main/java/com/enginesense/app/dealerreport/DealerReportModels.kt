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
