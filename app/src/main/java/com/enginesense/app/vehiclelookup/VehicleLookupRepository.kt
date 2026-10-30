package com.enginesense.app.vehiclelookup

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Mirrors the DealerReportApiException family — see DealerReportRepository for why distinct exception types matter here. */
sealed class VehicleLookupException(message: String, cause: Throwable? = null) : Exception(message, cause)
class VehicleLookupNetworkException(cause: Throwable? = null) : VehicleLookupException("Network error calling vehicle-lookup", cause)
class VehicleLookupHttpException(val statusCode: Int, val code: String?, val serverMessage: String?) :
    VehicleLookupException("vehicle-lookup returned HTTP $statusCode${code?.let { " ($it)" } ?: ""}")
class VehicleLookupNotAuthenticatedException(cause: Throwable? = null) : VehicleLookupException("No valid sign-in session available", cause)
class VehicleLookupParseException(cause: Throwable? = null) : VehicleLookupException("vehicle-lookup returned a response that couldn't be read", cause)

/**
 * Customer-mode client for the `vehicle-lookup` Edge Function — same cancellation-safe OkHttp
 * pattern as [com.enginesense.app.dealerreport.DealerReportRepository]. [idTokenProvider] is
 * called fresh per call, never cached here.
 */
class VehicleLookupRepository(
    private val endpointUrl: String,
    private val idTokenProvider: suspend () -> String,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val httpClient: OkHttpClient = OkHttpClient.Builder().callTimeout(timeoutMillis, TimeUnit.MILLISECONDS).build()
) {
    /** [vin] is the car's own VIN read over OBD — when given, the result says whether it matches the RC's chassis number. */
    suspend fun lookup(registrationNumber: String, vin: String? = null): Result<RcLookupResult> = try {
        val token = try {
            idTokenProvider()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw VehicleLookupNotAuthenticatedException(e)
        }
        val body = JSONObject().apply {
            put("registrationNumber", registrationNumber)
            put("mode", "customer")
            if (vin != null) put("vin", vin)
        }
        val request = Request.Builder()
            .url(endpointUrl)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $token")
            .build()
        Result.success(parseLookupResponse(executeAwait(request)))
    } catch (e: VehicleLookupException) {
        Result.failure(e)
    }

    private suspend fun executeAwait(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = httpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(VehicleLookupNetworkException(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyText = try {
                        it.body?.string().orEmpty()
                    } catch (e: IOException) {
                        continuation.resumeWithException(VehicleLookupNetworkException(e))
                        return
                    }
                    if (!it.isSuccessful) {
                        val error = try { JSONObject(bodyText).optJSONObject("error") } catch (e: Exception) { null }
                        continuation.resumeWithException(
                            VehicleLookupHttpException(it.code, error?.optStringOrNull("code"), error?.optStringOrNull("message"))
                        )
                        return
                    }
                    continuation.resume(bodyText)
                }
            }
        })
    }

    companion object {
        // The function itself gives Surepass 20s — this leaves room for that plus auth and the network.
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Pure parsing of a 200 response — see the Edge Function's shapeRcData for the shape. */
        fun parseLookupResponse(bodyText: String): RcLookupResult = try {
            val json = JSONObject(bodyText)
            val vehicleRoot = json.getJSONObject("vehicle")
            val vehicle = vehicleRoot.optJSONObject("vehicle") ?: JSONObject()
            val validity = vehicleRoot.optJSONObject("validity") ?: JSONObject()
            RcLookupResult(
                registrationNumber = vehicleRoot.optStringOrNull("registrationNumber") ?: "",
                make = vehicle.optStringOrNull("make"),
                model = vehicle.optStringOrNull("model"),
                fuelType = vehicle.optStringOrNull("fuelType"),
                color = vehicle.optStringOrNull("color"),
                category = vehicle.optStringOrNull("category"),
                manufacturingMonth = vehicle.optStringOrNull("manufacturingMonth"),
                registrationDate = vehicle.optStringOrNull("registrationDate"),
                registeredAt = vehicle.optStringOrNull("registeredAt"),
                cubicCapacity = vehicle.optStringOrNull("cubicCapacity"),
                insuranceCompany = validity.optStringOrNull("insuranceCompany"),
                insuranceUpto = validity.optStringOrNull("insuranceUpto"),
                puccUpto = validity.optStringOrNull("puccUpto"),
                fitnessUpto = validity.optStringOrNull("fitnessUpto"),
                taxUpto = validity.optStringOrNull("taxUpto"),
                partial = json.optBoolean("partial", false),
                source = json.optStringOrNull("source") ?: "RTO records",
                chassisMatch = if (json.has("chassisMatch") && !json.isNull("chassisMatch")) json.getBoolean("chassisMatch") else null,
            )
        } catch (e: org.json.JSONException) {
            throw VehicleLookupParseException(e)
        }
    }
}

/** org.json's optString turns JSON null into the string "null" — this keeps it a real null. */
private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
