package com.enginesense.app.vehicle.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Calls the deployed `ai-mechanic` Supabase Edge Function for a real conversational answer —
 * verified end-to-end against the real deployment (200 OK, real Gemini reply, ~9.8s) after
 * fixing the project's Gemini API billing tier. See [AiBackendConfig] and
 * [com.enginesense.app.GetAutoSureViewModel], which wire this in when configured.
 *
 * Timeout is enforced via [httpClient]'s `callTimeout`, deliberately NOT via coroutines'
 * `withTimeout` — `withTimeout` throws `TimeoutCancellationException`, which IS a
 * `CancellationException`, and [FallbackAiMechanicChatService] must always rethrow genuine
 * cancellation rather than fall back to local on it. An OkHttp call-timeout instead surfaces
 * as a plain `IOException` ([RemoteAiNetworkException]), which falling back on is correct.
 *
 * @param endpointUrl The backend's URL. An empty/placeholder value is only meaningful via
 * [AiBackendConfig.isConfigured] gating construction in the first place — this class itself
 * still refuses to call a blank URL, as a second line of defense.
 * @param idTokenProvider Fetches a fresh Google ID token per call (see
 * [com.enginesense.app.auth.getFreshGoogleIdToken]) rather than this class holding one captured
 * at construction time — ID tokens expire (~1 hour observed) and this service is typically
 * constructed once and reused for the lifetime of a ViewModel. Throwing
 * [RemoteAiNotAuthenticatedException] from this provider (as the real caller does) is how "not
 * signed in" / "session expired" reaches [FallbackAiMechanicChatService] as a distinct case from
 * a network failure.
 * @param httpClient Injectable so tests can point at a local mock server instead of the network.
 * @param timeoutMillis Configurable call timeout (connect + write + read combined).
 * @param knowledgeRetriever Optional grounding hints folded into the request — see
 * [AutomotiveKnowledgeRetriever]'s doc for why this doesn't change [AiMechanicChatService]'s contract.
 */
class RemoteAiMechanicChatService(
    private val endpointUrl: String,
    private val idTokenProvider: suspend () -> String,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    // Declared after timeoutMillis so its default can reference it — a caller who supplies a
    // custom timeout but no custom client still gets a client actually built with that timeout.
    private val httpClient: OkHttpClient = defaultHttpClient(timeoutMillis),
    private val knowledgeRetriever: AutomotiveKnowledgeRetriever? = null
) : AiMechanicChatService {

    init {
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
    }

    override suspend fun ask(context: AiVehicleContext, history: List<ChatMessage>, question: String): ChatMessage {
        if (endpointUrl.isBlank()) throw RemoteAiNotConfiguredException()

        // Fetched before building the request so an auth failure never costs a wasted network
        // call, and so it surfaces as RemoteAiNotAuthenticatedException (or whatever the
        // provider itself throws) rather than a generic HTTP 401 from the backend.
        val idToken = idTokenProvider()

        val knowledgeHints = knowledgeRetriever?.relevantEntries(question) ?: emptyList()
        val requestBody = RemoteAiProtocol.buildRequestBody(context, history, question, knowledgeHints)

        val request = Request.Builder()
            .url(endpointUrl)
            .header("Authorization", "Bearer $idToken")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val responseBody = executeAwait(request)
        val reply = RemoteAiProtocol.parseResponseBody(responseBody)
        return ChatMessage(fromUser = false, text = reply)
    }

    /**
     * Bridges OkHttp's async call to a suspend function in a cancellation-safe way: if the
     * calling coroutine is cancelled while this is suspended, `invokeOnCancellation` cancels
     * the underlying HTTP call too, instead of leaking a request nobody's waiting on anymore.
     * `suspendCancellableCoroutine` itself is what makes cancellation here throw a genuine
     * `CancellationException` rather than something this function could accidentally swallow.
     */
    private suspend fun executeAwait(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = httpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(RemoteAiNetworkException(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        continuation.resumeWithException(RemoteAiHttpException(it.code))
                        return
                    }
                    val body = try {
                        it.body?.string().orEmpty()
                    } catch (e: IOException) {
                        continuation.resumeWithException(RemoteAiNetworkException(e))
                        return
                    }
                    continuation.resume(body)
                }
            }
        })
    }

    companion object {
        // Real Gemini calls through the deployed Edge Function have shown genuinely variable
        // latency across sessions — as fast as ~8.7s, as slow as 15s+ (real-device testing hit
        // repeated "timed out after 15000ms" failures at the previous value here). This must
        // stay comfortably above the Edge Function's own GEMINI_TIMEOUT_MS (25_000ms) so the
        // client doesn't give up before a real in-flight response can arrive.
        const val DEFAULT_TIMEOUT_MILLIS = 32_000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(timeoutMillis: Long): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .build()
    }
}
