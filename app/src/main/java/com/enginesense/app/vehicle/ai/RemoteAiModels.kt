package com.enginesense.app.vehicle.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * The backend contract [RemoteAiMechanicChatService] sends/expects — see the architecture
 * proposal this implements. This is a CONTRACT only: no backend exists yet (see that class's
 * doc), so nothing here has been exercised against a real server.
 *
 * Request shape:
 * ```
 * {
 *   "question": "string, capped at MAX_QUESTION_LENGTH",
 *   "history": [{"fromUser": true, "text": "..."}, ...],   // capped at MAX_HISTORY_TURNS, chronological
 *   "context": { ...AiVehicleContext.toJson()... } | null, // null when hasScan = false
 *   "knowledgeHints": ["...", ...]                          // optional, from AutomotiveKnowledgeRetriever
 * }
 * ```
 * Response shape (success): `{"reply": "string"}`
 * Response shape (failure): `{"error": {"code": "string", "message": "string"}}`
 */
object RemoteAiProtocol {
    const val MAX_QUESTION_LENGTH = 2000
    const val MAX_HISTORY_TURNS = 10

    fun buildRequestBody(
        context: AiVehicleContext,
        history: List<ChatMessage>,
        question: String,
        knowledgeHints: List<String>
    ): JSONObject {
        val cappedQuestion = question.take(MAX_QUESTION_LENGTH)
        // takeLast preserves chronological order — it drops the oldest entries, not the newest.
        val cappedHistory = history.takeLast(MAX_HISTORY_TURNS)

        return JSONObject().apply {
            put("question", cappedQuestion)
            put("history", JSONArray(cappedHistory.map { msg ->
                JSONObject().apply {
                    put("fromUser", msg.fromUser)
                    put("text", msg.text)
                }
            }))
            put("context", if (context.hasScan) context.toJson() else JSONObject.NULL)
            put("knowledgeHints", JSONArray(knowledgeHints))
        }
    }

    /** @throws RemoteAiParseException if the response is missing or malformed. */
    fun parseResponseBody(body: String): String {
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw RemoteAiParseException("Response body is not valid JSON", e)
        }
        json.optJSONObject("error")?.let { error ->
            throw RemoteAiUpstreamException(
                code = error.optString("code", "unknown"),
                upstreamMessage = error.optString("message", "No error message provided")
            )
        }
        val reply = json.optString("reply", "")
        if (reply.isBlank()) throw RemoteAiParseException("Response had no non-blank 'reply' field")
        return reply
    }
}

/** Base type for every way a remote call can fail — [FallbackAiMechanicChatService] catches this family, never [kotlinx.coroutines.CancellationException]. */
sealed class RemoteAiMechanicException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The endpoint isn't configured (blank/placeholder URL) — see [AiBackendConfig]. Thrown before any network call is attempted. */
class RemoteAiNotConfiguredException : RemoteAiMechanicException("Remote AI endpoint is not configured")

/**
 * No valid Google ID token could be obtained for this request — the user never signed in, or
 * their session has expired/been revoked. Thrown before any network call is attempted; see
 * [com.enginesense.app.auth.getFreshGoogleIdToken]'s doc for why those two cases aren't
 * distinguished further. [FallbackAiMechanicChatService] treats this specially: unlike a
 * network/parse failure, it must not fall back to [RuleBasedAiMechanicChatService] silently.
 */
class RemoteAiNotAuthenticatedException(cause: Throwable? = null) :
    RemoteAiMechanicException("No valid sign-in session available", cause)

/** Covers connection failures and timeouts — OkHttp reports both as [java.io.IOException] subtypes. */
class RemoteAiNetworkException(cause: Throwable? = null) : RemoteAiMechanicException("Network error calling remote AI backend", cause)

/** A non-2xx HTTP response. */
class RemoteAiHttpException(val statusCode: Int) : RemoteAiMechanicException("Remote AI backend returned HTTP $statusCode")

/** The response body wasn't valid JSON, or was missing the fields this contract requires. */
class RemoteAiParseException(message: String, cause: Throwable? = null) : RemoteAiMechanicException(message, cause)

/** The backend itself reported a structured error (rate limited, invalid request, upstream model failure, ...). */
class RemoteAiUpstreamException(val code: String, val upstreamMessage: String) :
    RemoteAiMechanicException("Remote AI backend reported an error: $code — $upstreamMessage")
