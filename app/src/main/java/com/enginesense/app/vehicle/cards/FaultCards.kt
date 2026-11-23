package com.enginesense.app.vehicle.cards

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** How soon to act, as the cards put it. */
enum class CardUrgency { STOP, NOW, SOON, WAIT }

/** One card's words in one language. Lists line up with the English ones item for item. */
data class FaultCardText(
    val title: String,
    val meaning: String,
    val notice: List<String>,
    val causes: List<String>,
    val drive: String,
    val firstCheck: String,
    val costParts: List<String>,
    val indiaNote: String
)

/**
 * A plain-language explanation of one fault code, written for owners (tools/fault-cards). [brand]
 * is set for maker-defined codes (TATA, MAHINDRA, HONDA, SUZUKI); null for standard codes.
 * Costs are tentative ranges, like everywhere else in the app.
 */
data class FaultCard(
    val code: String,
    val brand: String?,
    val urgency: CardUrgency,
    val fuel: List<String>,
    val costs: List<IntRange>,
    val text: Map<String, FaultCardText>
) {
    /** The text in [language], or English when that language isn't translated yet. */
    fun textIn(language: String): FaultCardText = text[language] ?: text.getValue("en")
    fun has(language: String) = language in text

    /** The card's facts in one English paragraph, for AI Mechanic to stay consistent with (it replies in the user's language itself). */
    fun toAiHint(): String {
        val t = textIn("en")
        val costs = t.costParts.zip(costs).joinToString("; ") { (part, r) -> "$part ₹${r.first}–${r.last}" }
        return "$code${brand?.let { " ($it-specific)" } ?: ""}: ${t.title}. ${t.meaning} Urgency: ${urgency.name}. " +
            "Likely causes, most common first: ${t.causes.joinToString("; ")}. Can I keep driving: ${t.drive} " +
            "Cheapest first check: ${t.firstCheck} Tentative costs: $costs."
    }
}

/** Loads the bundled cards once and finds the one for a code, preferring the car maker's own card. */
object FaultCardRepository {
    private const val DIR = "fault_cards"
    private val TRANSLATED = listOf("ta", "te", "kn", "ml", "mr")

    @Volatile private var cards: Map<String, FaultCard>? = null

    /** Reads the asset files; call off the main thread (they're a few MB). */
    fun load(context: Context): Map<String, FaultCard> = cards ?: synchronized(this) {
        cards ?: run {
            val assets = context.assets
            val extra = TRANSLATED.mapNotNull { lang ->
                runCatching { assets.open("$DIR/cards.$lang.json").bufferedReader().use { it.readText() } }.getOrNull()?.let { lang to it }
            }.toMap()
            val base = assets.open("$DIR/cards.json").bufferedReader().use { it.readText() }
            parse(base, extra).also { cards = it }
        }
    }

    /** The maker's card when there is one for this car, else the standard card, else null. */
    fun find(cards: Map<String, FaultCard>, code: String, vehicleBrand: String?): FaultCard? {
        val c = code.trim().uppercase()
        return makerKey(vehicleBrand)?.let { cards["${it}_$c"] } ?: cards["GENERIC_$c"]
    }

    /** "Maruti Suzuki" → SUZUKI, "Tata Motors" → TATA: the makers the cards have their own codes for. */
    fun makerKey(vehicleBrand: String?): String? {
        val b = vehicleBrand?.uppercase() ?: return null
        return listOf("TATA", "MAHINDRA", "HONDA", "SUZUKI").firstOrNull { it in b }
    }

    /** [base] is cards.json; [translations] maps a language code to its cards.<lang>.json. */
    fun parse(base: String, translations: Map<String, String> = emptyMap()): Map<String, FaultCard> {
        val extra = translations.mapValues { (_, json) ->
            JSONArray(json).objects().associate { o -> key(o) to text(o) }
        }
        return JSONArray(base).objects().associate { o ->
            val k = key(o)
            val texts = o.getJSONObject("text").let { t -> t.keys().asSequence().associateWith { text(t.getJSONObject(it)) } }
            val costs = o.getJSONArray("costs").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { r -> r.getInt(0)..r.getInt(1) } } }
            k to FaultCard(
                code = o.getString("code"),
                brand = o.optString("brand").takeIf { !o.isNull("brand") && it.isNotBlank() },
                urgency = CardUrgency.valueOf(o.getString("urgency")),
                fuel = o.getJSONArray("fuel").strings(),
                costs = costs,
                text = texts + extra.mapNotNull { (lang, m) -> m[k]?.let { lang to it } }
            )
        }
    }

    private fun key(o: JSONObject) = "${if (o.isNull("brand")) "GENERIC" else o.getString("brand")}_${o.getString("code")}"

    private fun text(o: JSONObject) = FaultCardText(
        title = o.getString("title"),
        meaning = o.getString("meaning"),
        notice = o.getJSONArray("notice").strings(),
        causes = o.getJSONArray("causes").strings(),
        drive = o.getString("drive"),
        firstCheck = o.getString("first_check"),
        costParts = o.getJSONArray("cost_parts").strings(),
        indiaNote = o.optString("india_note")
    )

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}

/**
 * The card's own headings and urgency labels per language. Written by hand for the first
 * languages — to be checked by a native speaker with the card translations.
 */
object FaultCardLabels {
    data class Labels(
        val notice: String, val causes: String, val mostCommon: String, val drive: String,
        val firstCheck: String, val cost: String, val indiaNote: String, val urgency: Map<CardUrgency, String>
    )

    private fun u(stop: String, now: String, soon: String, wait: String) =
        mapOf(CardUrgency.STOP to stop, CardUrgency.NOW to now, CardUrgency.SOON to soon, CardUrgency.WAIT to wait)

    private val all = mapOf(
        "en" to Labels("You may notice", "Likely causes", "Most common", "Can I keep driving?", "Cheapest first check", "Tentative cost", "India note",
            u("Stop driving", "Fix right away", "Fix within 2 weeks", "Can wait")),
        "hi" to Labels("आपको ये दिख सकता है", "संभावित कारण", "सबसे आम", "क्या गाड़ी चला सकते हैं?", "सबसे सस्ती पहली जांच", "अनुमानित ख़र्च", "भारत के लिए ख़ास",
            u("गाड़ी रोकें", "तुरंत ठीक कराएं", "2 हफ़्ते में ठीक कराएं", "रुक सकते हैं")),
        "ta" to Labels("நீங்கள் கவனிக்கக்கூடியவை", "சாத்தியமான காரணங்கள்", "மிகவும் பொதுவானது", "வண்டியை ஓட்டலாமா?", "முதலில் செய்ய வேண்டிய மலிவான சோதனை", "மதிப்பிட்ட செலவு", "இந்தியாவுக்கான குறிப்பு",
            u("வண்டியை நிறுத்துங்கள்", "உடனே சரிசெய்யுங்கள்", "2 வாரத்திற்குள் சரிசெய்யுங்கள்", "காத்திருக்கலாம்")),
        "te" to Labels("మీరు గమనించేవి", "సంభావ్య కారణాలు", "అత్యంత సాధారణం", "కారు నడపవచ్చా?", "ముందుగా చేయాల్సిన చౌకైన తనిఖీ", "అంచనా ఖర్చు", "భారత్ కోసం సూచన",
            u("కారు ఆపండి", "వెంటనే రిపేర్ చేయించండి", "2 వారాల్లో రిపేర్ చేయించండి", "ఆగవచ్చు")),
        "kn" to Labels("ನೀವು ಗಮನಿಸಬಹುದಾದವು", "ಸಂಭವನೀಯ ಕಾರಣಗಳು", "ಅತ್ಯಂತ ಸಾಮಾನ್ಯ", "ಕಾರು ಓಡಿಸಬಹುದೇ?", "ಮೊದಲು ಮಾಡಬೇಕಾದ ಅಗ್ಗದ ಪರಿಶೀಲನೆ", "ಅಂದಾಜು ವೆಚ್ಚ", "ಭಾರತಕ್ಕಾಗಿ ಸೂಚನೆ",
            u("ಕಾರು ನಿಲ್ಲಿಸಿ", "ತಕ್ಷಣ ರಿಪೇರಿ ಮಾಡಿಸಿ", "2 ವಾರದೊಳಗೆ ರಿಪೇರಿ ಮಾಡಿಸಿ", "ಕಾಯಬಹುದು")),
        "ml" to Labels("നിങ്ങൾ ശ്രദ്ധിച്ചേക്കാവുന്നവ", "സാധ്യമായ കാരണങ്ങൾ", "ഏറ്റവും സാധാരണം", "വണ്ടി ഓടിക്കാമോ?", "ആദ്യം ചെയ്യേണ്ട ചെലവ് കുറഞ്ഞ പരിശോധന", "ഏകദേശ ചെലവ്", "ഇന്ത്യയ്ക്കുള്ള കുറിപ്പ്",
            u("വണ്ടി നിർത്തുക", "ഉടൻ ശരിയാക്കുക", "2 ആഴ്ചയ്ക്കുള്ളിൽ ശരിയാക്കുക", "കാത്തിരിക്കാം")),
        "mr" to Labels("तुम्हाला हे जाणवू शकते", "संभाव्य कारणे", "सर्वात सामान्य", "गाडी चालवू शकतो का?", "आधी करायची स्वस्त तपासणी", "अंदाजे खर्च", "भारतासाठी टीप",
            u("गाडी थांबवा", "लगेच दुरुस्त करा", "2 आठवड्यांत दुरुस्त करा", "थांबू शकता"))
    )

    fun of(language: String): Labels = all[language] ?: all.getValue("en")
}
