package com.enginesense.app.owner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** AI's answer for one queued payment. */
data class SortedPayment(val id: String, val category: String, val confidence: String)

/**
 * Asks the payment-sort function what queued payments were for. Sends only merchant, amount and
 * the already-masked alert text. Errors throw; the caller keeps the payments queued and retries later.
 */
class RemotePaymentSorter(
    private val endpointUrl: String = ENDPOINT_URL,
    private val httpClient: OkHttpClient = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
) {
    suspend fun sort(payments: List<PendingPayment>, idToken: String): List<SortedPayment> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("payments", JSONArray(payments.map { p ->
            JSONObject().put("id", p.id).put("merchant", p.merchant).put("amountInr", p.amountInr).put("text", p.text)
        }))
        val request = Request.Builder().url(endpointUrl)
            .header("Authorization", "Bearer $idToken")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("payment-sort returned ${response.code}")
            parse(text)
        }
    }

    companion object {
        const val ENDPOINT_URL = "https://xofkkvhkkbtcayibjaul.supabase.co/functions/v1/payment-sort"
        /** Per call; the function itself caps at the same number. */
        const val BATCH = 10

        fun parse(body: String): List<SortedPayment> {
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            return (0 until results.length()).mapNotNull { i ->
                results.optJSONObject(i)?.let { o -> SortedPayment(o.optString("id"), o.optString("category"), o.optString("confidence")) }
                    ?.takeIf { it.id.isNotEmpty() && it.category.isNotEmpty() }
            }
        }
    }
}
