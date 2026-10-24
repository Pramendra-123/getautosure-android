package com.enginesense.app.consultation

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

/** One way a `consultations` Edge Function call can fail — mirrors the RemoteAi* exception family in vehicle/ai/RemoteAiModels.kt, same reasoning: distinct types so callers can tell "transient" from "this specific thing is wrong" apart. */
sealed class ConsultationApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ConsultationNetworkException(cause: Throwable? = null) : ConsultationApiException("Network error calling the consultations backend", cause)
class ConsultationHttpException(val statusCode: Int, val code: String?, val serverMessage: String?) :
    ConsultationApiException("consultations backend returned HTTP $statusCode${code?.let { " ($it)" } ?: ""}")
class ConsultationNotAuthenticatedException(cause: Throwable? = null) : ConsultationApiException("No valid sign-in session available", cause)

/**
 * The real, Supabase-backed [ExpertRepository] — Phase 3. Talks to the single `consultations`
 * Edge Function (action-dispatched; see its own doc for why it's one function, not several).
 * Same auth pattern as [com.enginesense.app.vehicle.ai.RemoteAiMechanicChatService]:
 * [idTokenProvider] is called fresh per authenticated request (never cached here — the caller,
 * same as [com.enginesense.app.GetAutoSureViewModel] does for AI Mechanic, is responsible for
 * its own caching/Activity-context handling), and is simply never invoked for the two public,
 * browsing-only actions (list experts, list slots).
 */
class SupabaseExpertRepository(
    private val endpointUrl: String,
    private val idTokenProvider: suspend () -> String,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val httpClient: OkHttpClient = defaultHttpClient(timeoutMillis)
) : ExpertRepository {

    override suspend fun listActiveExperts(): List<Expert> {
        val json = postAction(action = "list_experts", requiresAuth = false)
        val array = json.getJSONArray("experts")
        return (0 until array.length()).map { i -> expertFromJson(array.getJSONObject(i)) }
    }

    override suspend fun availableSlots(expertId: String): List<AvailabilitySlot> {
        val json = postAction(action = "list_slots", requiresAuth = false) { put("expertId", expertId) }
        val array = json.getJSONArray("slots")
        return (0 until array.length()).map { i -> slotFromJson(array.getJSONObject(i)) }
    }

    override suspend fun myBookings(): List<ConsultationBooking> {
        val json = postAction(action = "my_bookings", requiresAuth = true)
        val array = json.getJSONArray("bookings")
        return (0 until array.length()).map { i -> bookingFromJson(array.getJSONObject(i)) }
    }

    override suspend fun reserveSlot(slotId: String, vehicleContext: JSONObject?): Result<ConsultationBooking> = runCatchingApi {
        val json = postAction(action = "reserve_slot", requiresAuth = true) {
            put("slotId", slotId)
            put("vehicleContext", vehicleContext ?: JSONObject.NULL)
        }
        bookingFromJson(json.getJSONObject("booking"))
    }

    /** Not wired up until Phase 4 — real payment verification confirms a booking server-side, there's no `confirm_booking` action yet. */
    override suspend fun confirmBookingAfterPayment(bookingId: String): Result<ConsultationBooking> =
        Result.failure(NotImplementedError("Payment confirmation isn't implemented yet — Phase 4 adds this, see ExpertRepository's doc."))

    override suspend fun cancelBooking(bookingId: String): Result<Unit> = runCatchingApi {
        postAction(action = "cancel_booking", requiresAuth = true) { put("consultationId", bookingId) }
        Unit
    }

    private suspend fun postAction(action: String, requiresAuth: Boolean, extra: JSONObject.() -> Unit = {}): JSONObject {
        val body = JSONObject().apply {
            put("action", action)
            extra()
        }
        val requestBuilder = Request.Builder().url(endpointUrl).post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        if (requiresAuth) {
            val token = try {
                idTokenProvider()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Must always propagate — same rule as everywhere else cancellation-sensitive
                // in this app (see RemoteAiMechanicChatService / FallbackAiMechanicChatService's
                // docs). A broad `catch (e: Exception)` here would swallow it.
                throw e
            } catch (e: Exception) {
                throw ConsultationNotAuthenticatedException(e)
            }
            requestBuilder.header("Authorization", "Bearer $token")
        }
        val responseBody = executeAwait(requestBuilder.build())
        return try {
            JSONObject(responseBody)
        } catch (e: Exception) {
            throw ConsultationApiExceptionParse(e)
        }
    }

    /** Same bridging pattern as RemoteAiMechanicChatService.executeAwait — see that function's doc for why cancellation safety here matters. */
    private suspend fun executeAwait(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = httpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(ConsultationNetworkException(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyText = try {
                        it.body?.string().orEmpty()
                    } catch (e: IOException) {
                        continuation.resumeWithException(ConsultationNetworkException(e))
                        return
                    }
                    if (!it.isSuccessful) {
                        val (code, message) = parseErrorBody(bodyText)
                        continuation.resumeWithException(ConsultationHttpException(it.code, code, message))
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
        const val DEFAULT_TIMEOUT_MILLIS = 20_000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(timeoutMillis: Long): OkHttpClient =
            OkHttpClient.Builder().callTimeout(timeoutMillis, TimeUnit.MILLISECONDS).build()

        fun expertFromJson(json: JSONObject): Expert = Expert(
            id = json.getString("id"),
            name = json.getString("name"),
            title = json.getString("title"),
            bio = json.optString("bio", ""),
            yearsExperience = json.optInt("yearsExperience", 0),
            specializations = json.optJSONArray("specializations")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
            photoUrl = json.optString("photoUrl").takeIf { it.isNotBlank() }
        )

        fun slotFromJson(json: JSONObject): AvailabilitySlot = AvailabilitySlot(
            id = json.getString("id"),
            expertId = json.getString("expertId"),
            startEpochMillis = json.getLong("startEpochMillis"),
            endEpochMillis = json.getLong("endEpochMillis"),
            status = SlotStatus.valueOf(json.optString("status", "OPEN"))
        )

        fun bookingFromJson(json: JSONObject): ConsultationBooking = ConsultationBooking(
            id = json.getString("id"),
            bookingReference = json.getString("bookingReference"),
            expertId = json.getString("expertId"),
            expertName = json.optString("expertName", ""),
            slotStartEpochMillis = json.optLong("slotStartEpochMillis"),
            slotEndEpochMillis = json.optLong("slotEndEpochMillis"),
            status = BookingStatus.valueOf(json.getString("status")),
            amountInr = json.getInt("amountInr")
        )
    }
}

/** Parse-failure case for [SupabaseExpertRepository.postAction] — malformed JSON in an otherwise-2xx response. */
private class ConsultationApiExceptionParse(cause: Throwable) : ConsultationApiException("consultations backend returned a response that wasn't valid JSON", cause)

private suspend fun <T> runCatchingApi(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: ConsultationApiException) {
    Result.failure(e)
}
