package com.enginesense.app.vehicle.pricing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One listing or price-guide figure the AI found while pricing the car. */
data class PriceComparable(val description: String, val priceInr: Double, val site: String)

/** A page the AI's web search used. */
data class PriceSource(val title: String, val url: String)

/**
 * The price-estimate function's answer: a market range for the car in good working order, before
 * repairs, worked out from a live search of current listings, with the steps in plain English.
 */
data class AiPriceEstimate(
    val lowInr: Double,
    val highInr: Double,
    val newPriceInr: Double?,
    /** "high", "medium" or "low". */
    val confidence: String,
    val steps: List<String>,
    val comparables: List<PriceComparable>,
    val sources: List<PriceSource>,
    /** ISO-8601, when this car was priced; a cached answer can be up to a week old. */
    val pricedAt: String?
) {
    val midInr: Double get() = (lowInr + highInr) / 2
}

/** What the car is, as sent for pricing. */
data class AiPriceQuery(
    val make: String,
    val model: String,
    val variant: Variant,
    val year: Int,
    val fuel: String?,
    val km: Int?,
    val city: String?,
    val owners: Int?,
    /** The model exactly as on the RTO record ("SLAVIA STYLE 1.0 TSI AT"): names the real variant and gearbox. */
    val rcModel: String? = null
) {
    /** Nearby km readings give the same query, so typing the odometer doesn't fire a new request per digit. */
    fun banded(): AiPriceQuery = copy(km = km?.let { (it + 5_000) / 10_000 * 10_000 })
}

/** Which price the report shows. */
enum class PriceBasis { AI_MARKET, FORMULA }

/**
 * Picks the AI market price when there is a sane one, and our formula otherwise. The formula stays
 * the backup: it's used when the AI didn't answer, and also when the AI's answer is so far from it
 * that one of the two is clearly wrong — then we show our own number and say why, rather than a
 * figure we can't vouch for.
 */
object MarketPriceChooser {
    /** How far apart (as a share of the formula's midpoint) the two may be before the AI's answer is set aside. */
    const val MAX_DISAGREEMENT = 0.45

    data class Choice(val basis: PriceBasis, val low: Double, val high: Double, val note: String?)

    fun choose(ai: AiPriceEstimate?, formulaLow: Double?, formulaHigh: Double?): Choice? {
        val formulaMid = if (formulaLow != null && formulaHigh != null) (formulaLow + formulaHigh) / 2 else null
        if (ai == null || ai.lowInr <= 0 || ai.highInr < ai.lowInr) {
            return formulaMid?.let { Choice(PriceBasis.FORMULA, formulaLow!!, formulaHigh!!, null) }
        }
        if (formulaMid == null) return Choice(PriceBasis.AI_MARKET, ai.lowInr, ai.highInr, null)
        val gap = kotlin.math.abs(ai.midInr - formulaMid) / formulaMid
        return if (gap > MAX_DISAGREEMENT) {
            Choice(
                PriceBasis.FORMULA, formulaLow!!, formulaHigh!!,
                "The online market check came back far from our own estimate, so we've shown ours. Check a few listings yourself before you bargain."
            )
        } else {
            Choice(PriceBasis.AI_MARKET, ai.lowInr, ai.highInr, null)
        }
    }
}

/** Calls the price-estimate function. Errors throw; the caller falls back to the formula. */
class RemotePriceEstimator(
    private val endpointUrl: String = ENDPOINT_URL,
    // The function allows Gemini 42 s for search and reasoning; this leaves room for the round trip.
    private val httpClient: OkHttpClient = OkHttpClient.Builder().callTimeout(50, TimeUnit.SECONDS).build()
) {
    suspend fun estimate(query: AiPriceQuery, idToken: String): AiPriceEstimate = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("make", query.make)
            .put("model", query.model)
            .put("variant", query.variant.label)
            .put("year", query.year)
            .apply {
                query.fuel?.let { put("fuel", it) }
                query.km?.let { put("km", it) }
                query.city?.let { put("city", it) }
                query.owners?.let { put("owners", it) }
                query.rcModel?.takeIf { it.isNotBlank() }?.let { put("rcModel", it) }
            }
        val request = Request.Builder().url(endpointUrl)
            .header("Authorization", "Bearer $idToken")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("price-estimate returned ${response.code}")
            parse(text) ?: throw IllegalStateException("price-estimate answer unreadable")
        }
    }

    companion object {
        const val ENDPOINT_URL = "https://xofkkvhkkbtcayibjaul.supabase.co/functions/v1/price-estimate"

        /** Null when the answer is missing a sane range or any explanation. */
        fun parse(body: String): AiPriceEstimate? = runCatching {
            val o = JSONObject(body)
            val low = o.optDouble("lowInr", Double.NaN)
            val high = o.optDouble("highInr", Double.NaN)
            if (low.isNaN() || high.isNaN() || low <= 0 || high < low) return null
            val steps = o.optJSONArray("steps")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
            if (steps.isEmpty()) return null
            val comparables = o.optJSONArray("comparables")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    a.optJSONObject(i)?.let { c -> PriceComparable(c.optString("description"), c.optDouble("priceInr", 0.0), c.optString("site")) }
                        ?.takeIf { it.description.isNotBlank() && it.priceInr > 0 }
                }
            }.orEmpty()
            val sources = o.optJSONArray("sources")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    a.optJSONObject(i)?.let { s -> PriceSource(s.optString("title"), s.optString("url")) }?.takeIf { it.url.isNotBlank() }
                }
            }.orEmpty()
            AiPriceEstimate(
                lowInr = low,
                highInr = high,
                newPriceInr = o.optDouble("newPriceInr", 0.0).takeIf { it > 0 },
                confidence = o.optString("confidence", "low"),
                steps = steps,
                comparables = comparables,
                sources = sources,
                pricedAt = o.optString("pricedAt").ifBlank { null }
            )
        }.getOrNull()
    }
}
