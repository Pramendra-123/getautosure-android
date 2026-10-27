package com.enginesense.app.dealerreport

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Mirrors the ConsultationApiException/RemoteAiException families — see either for why distinct exception types matter here. */
sealed class DealerReportApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
class DealerReportNetworkException(cause: Throwable? = null) : DealerReportApiException("Network error calling the reports backend", cause)
class DealerReportHttpException(val statusCode: Int, val code: String?, val serverMessage: String?) :
    DealerReportApiException("reports backend returned HTTP $statusCode${code?.let { " ($it)" } ?: ""}")
class DealerReportNotAuthenticatedException(cause: Throwable? = null) : DealerReportApiException("No valid sign-in session available", cause)

/**
 * Talks to the single `reports` Edge Function's `publish_report` action — same action-dispatched,
 * cancellation-safe OkHttp pattern as [com.enginesense.app.consultation.SupabaseExpertRepository]
 * and [com.enginesense.app.vehicle.ai.RemoteAiMechanicChatService]. [idTokenProvider] is called
 * fresh per call, never cached here — same division of responsibility as those two.
 */
class DealerReportRepository(
    private val endpointUrl: String,
    private val idTokenProvider: suspend () -> String,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val httpClient: OkHttpClient = defaultHttpClient(timeoutMillis)
) {
    suspend fun publishReport(
        dealerName: String,
        dealerTagline: String,
        dealerPhone: String,
        dealerLocation: String,
        dealerLogo: PhotoUpload?,
        make: String?,
        model: String?,
        variant: String?,
        year: Int?,
        fuelType: String?,
        transmission: String?,
        odometerKm: Int?,
        ownership: String?,
        registrationNo: String?,
        healthSummary: JSONObject,
        dealerCondition: DealerConditionInput,
        photos: List<PhotoUpload>,
        scannedAtIso: String?
    ): Result<PublishedReport> = runCatchingApi {
        val body = JSONObject().apply {
            put("action", "publish_report")
            put("dealerName", dealerName)
            put("dealerTagline", dealerTagline)
            put("dealerPhone", dealerPhone)
            put("dealerLocation", dealerLocation)
            dealerLogo?.let { put("dealerLogo", it.toJson()) }
            put("make", make ?: JSONObject.NULL)
            put("model", model ?: JSONObject.NULL)
            put("variant", variant ?: JSONObject.NULL)
            put("year", year ?: JSONObject.NULL)
            put("fuelType", fuelType ?: JSONObject.NULL)
            put("transmission", transmission ?: JSONObject.NULL)
            put("odometerKm", odometerKm ?: JSONObject.NULL)
            put("ownership", ownership ?: JSONObject.NULL)
            put("registrationNo", registrationNo ?: JSONObject.NULL)
            put("healthSummary", healthSummary)
            put("dealerCondition", dealerCondition.toJson())
            put("photos", JSONArray(photos.map { it.toJson() }))
            put("scannedAt", scannedAtIso ?: JSONObject.NULL)
        }

        val token = try {
            idTokenProvider()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw DealerReportNotAuthenticatedException(e)
        }

        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $token")

        val responseBody = executeAwait(requestBuilder.build())
        val json = try {
            JSONObject(responseBody)
        } catch (e: Exception) {
            throw DealerReportApiExceptionParse(e)
        }
        PublishedReport(slug = json.getString("slug"), url = json.getString("url"))
    }

    /** Previews background removal on one exterior photo — never stored server-side, never attached to a report on its own. The caller decides whether to use the result or keep the original; publishReport only ever uploads whichever bytes it's given. */
    suspend fun removeBackground(photo: PhotoUpload): Result<PhotoUpload> = runCatchingApi {
        val body = JSONObject().apply {
            put("action", "remove_background")
            put("photo", photo.toJson())
        }
        val token = try {
            idTokenProvider()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw DealerReportNotAuthenticatedException(e)
        }
        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $token")
        val responseBody = executeAwait(requestBuilder.build())
        val json = try {
            JSONObject(responseBody)
        } catch (e: Exception) {
            throw DealerReportApiExceptionParse(e)
        }
        val mimeType = json.optString("mimeType", "image/png")
        val extension = if (mimeType == "image/jpeg") "jpg" else "png"
        PhotoUpload(
            filename = "processed.$extension",
            contentType = mimeType,
            base64 = json.getString("base64")
        )
    }

    /** Ownership is enforced server-side against the verified token's sub — this call can only ever affect a report the caller actually owns, regardless of what slug is passed. */
    suspend fun updateReportStatus(slug: String, status: String): Result<Unit> = runCatchingApi {
        val body = JSONObject().apply {
            put("action", "update_report")
            put("slug", slug)
            put("status", status)
        }
        val token = try {
            idTokenProvider()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw DealerReportNotAuthenticatedException(e)
        }
        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $token")
        executeAwait(requestBuilder.build())
        Unit
    }

    /** Same bridging pattern as RemoteAiMechanicChatService/SupabaseExpertRepository's executeAwait. */
    private suspend fun executeAwait(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = httpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(DealerReportNetworkException(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyText = try {
                        it.body?.string().orEmpty()
                    } catch (e: IOException) {
                        continuation.resumeWithException(DealerReportNetworkException(e))
                        return
                    }
                    if (!it.isSuccessful) {
                        val (code, message) = parseErrorBody(bodyText)
                        continuation.resumeWithException(DealerReportHttpException(it.code, code, message))
                        return
                    }
                    continuation.resume(bodyText)
                }
            }
        })
    }

    private fun parseErrorBody(bodyText: String): Pair<String?, String?> = try {
        val error = JSONObject(bodyText).optJSONObject("error")
        (error?.optString("code")) to (error?.optString("message"))
    } catch (e: Exception) {
        null to null
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L // Higher than the other repositories' default — this call uploads photo bytes, not just text.
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(timeoutMillis: Long): OkHttpClient =
            OkHttpClient.Builder().callTimeout(timeoutMillis, TimeUnit.MILLISECONDS).build()
    }
}

private fun PhotoUpload.toJson(): JSONObject = JSONObject().apply {
    put("filename", filename)
    put("contentType", contentType)
    put("base64", base64)
}

private fun DealerConditionInput.toJson(): JSONObject = JSONObject().apply {
    put("exterior", exterior.toJson())
    put("interior", interior.toJson())
    put("tyres", tyres.toJson())
}

private fun DealerConditionItem.toJson(): JSONObject = JSONObject().apply {
    put("rating", rating)
    put("options", JSONArray(options))
}

private class DealerReportApiExceptionParse(cause: Throwable) : DealerReportApiException("reports backend returned a response that wasn't valid JSON", cause)

private suspend fun <T> runCatchingApi(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: DealerReportApiException) {
    Result.failure(e)
}
