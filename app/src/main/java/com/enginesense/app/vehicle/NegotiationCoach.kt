package com.enginesense.app.vehicle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class CoachTone(val id: String, val label: String) { GENTLE("gentle", "Gentle"), FIRM("firm", "Firm"), FINAL("final", "Final offer") }

/** Languages the message can be written in — the content languages from the multilingual plan. */
enum class CoachLanguage(val id: String, val label: String) {
    EN("en", "English"), HI("hi", "हिन्दी"), KN("kn", "ಕನ್ನಡ"), TA("ta", "தமிழ்"), TE("te", "తెలుగు"), MR("mr", "मराठी"), BN("bn", "বাংলা"), ML("ml", "മലയാളം")
}

data class SellerReply(val seller: String, val you: String)

/** The buyer's words: the WhatsApp message, three lines for the car, answers to the seller's usual replies. */
data class Coaching(val message: String, val sayThis: List<String>, val replies: List<SellerReply>, val tip: String, val fromAi: Boolean)

/**
 * Calls the negotiation-coach function. Our plan's numbers go in; Gemini writes only the words.
 * [offline] is the plain-English fallback when the function can't be reached, so the buyer always
 * has something to send.
 */
class NegotiationCoach(
    private val endpointUrl: String = ENDPOINT_URL,
    private val httpClient: OkHttpClient = OkHttpClient.Builder().callTimeout(40, TimeUnit.SECONDS).build()
) {
    suspend fun coach(
        plan: NegotiationPlan, car: String?, sellerName: String, language: CoachLanguage, tone: CoachTone,
        notes: List<String>, idToken: String
    ): Coaching = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("language", language.id).put("tone", tone.id).put("sellerName", sellerName).put("car", car ?: "")
            .put("askingInr", plan.askingInr ?: JSONObject.NULL)
            .put("offerInr", offerFor(plan, tone)).put("fairDealInr", plan.fairDeal).put("walkAwayInr", plan.walkAwayOver)
            .put("reasons", JSONArray(plan.reasons.map { JSONObject().put("title", it.title).put("minInr", it.minInr).put("maxInr", it.maxInr) }))
            .put("notes", JSONArray(notes.take(6)))
        val request = Request.Builder().url(endpointUrl)
            .header("Authorization", "Bearer $idToken")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("negotiation-coach returned ${response.code}")
            parse(text) ?: throw IllegalStateException("negotiation-coach answer unreadable")
        }
    }

    companion object {
        const val ENDPOINT_URL = "https://xofkkvhkkbtcayibjaul.supabase.co/functions/v1/negotiation-coach"

        /** Gentle and final offers start at the fair deal; firm opens lower, leaving room to meet in the middle. */
        fun offerFor(plan: NegotiationPlan, tone: CoachTone): Double = if (tone == CoachTone.FIRM) plan.openWith else plan.fairDeal

        fun parse(body: String): Coaching? = runCatching {
            val o = JSONObject(body)
            val message = o.optString("message").trim().takeIf { it.length >= 20 } ?: return null
            val say = o.optJSONArray("sayThis")?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotBlank() } }.orEmpty()
            val replies = o.optJSONArray("replies")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { SellerReply(it.optString("seller").trim(), it.optString("you").trim()) } }
                    .filter { it.seller.isNotBlank() && it.you.isNotBlank() }
            }.orEmpty()
            Coaching(message, say, replies, o.optString("tip").trim(), fromAi = true)
        }.getOrNull()

        private fun inr(v: Double) = "₹" + indian(v.toLong())
        private fun k(v: Double) = "%,d".format((v / 1000).toLong())

        /** 1015000 → 10,15,000 — Indian grouping. */
        fun indian(n: Long): String {
            val s = n.toString()
            if (s.length <= 3) return s
            val last3 = s.takeLast(3)
            val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
            return "$rest,$last3"
        }

        /** Plain English, from the same numbers — used when the AI can't be reached. */
        fun offline(plan: NegotiationPlan, car: String?, sellerName: String, tone: CoachTone): Coaching {
            val offer = offerFor(plan, tone)
            val items = plan.reasons.take(4).joinToString("\n") { r ->
                "• ${r.title} (₹${k(r.minInr)}${if (r.maxInr > r.minInr) "–${k(r.maxInr)}" else ""} thousand)"
            }
            val greet = if (sellerName.isNotBlank()) "Hello $sellerName ji" else "Hello"
            val close = when (tone) {
                CoachTone.GENTLE -> "Would ${inr(offer)} work for you? Happy to talk."
                CoachTone.FIRM -> "Keeping these in mind, I can offer ${inr(offer)}."
                CoachTone.FINAL -> "${inr(offer)} is my best and final offer."
            }
            val message = "$greet, thank you for showing me the ${car ?: "car"} today. I liked it, but the check found a few things:\n\n" +
                (items.ifBlank { "• Nothing major, but the price is above the market" }) +
                "\n\n$close I've attached the full report."
            val first = plan.reasons.firstOrNull()
            return Coaching(
                message = message,
                sayThis = listOfNotNull(
                    first?.let { "\"I like the car. ${it.title} will cost about ₹${k(it.minInr)}–${k(it.maxInr)} thousand to fix.\"" },
                    plan.reasons.getOrNull(1)?.let { "\"There's also this: ${it.title.replaceFirstChar { c -> c.lowercase() }}.\"" },
                    "\"Keeping all of this in mind, ${inr(offer)} is what I can do.\""
                ),
                replies = listOf(
                    SellerReply("\"Other buyers are coming.\"", "That's fine. My offer stands for today. Don't go above ${inr(plan.walkAwayOver)}."),
                    SellerReply("\"It's nothing, it'll run fine.\"", "Show the report. Ask them to fix it before the sale, or take the cost off.")
                ),
                tip = "Start with what the seller can see for themselves, and keep the price for last.",
                fromAi = false
            )
        }
    }
}
