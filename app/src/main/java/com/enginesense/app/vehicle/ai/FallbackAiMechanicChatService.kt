package com.enginesense.app.vehicle.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Tries [remote] first — with one retry for a failure that's plausibly transient — falls back
 * to [local] if that still doesn't work. Exists so neither [RemoteAiMechanicChatService] nor
 * [RuleBasedAiMechanicChatService] has to know about the other; this is the only class that does.
 *
 * CRITICAL correctness requirement: [CancellationException] (thrown when the coroutine calling
 * this is itself cancelled — e.g. the screen that started this chat navigated away) is caught
 * first and always rethrown, never treated as "remote failed, try local." Swallowing it here
 * would mean a cancelled request still silently runs the local fallback and updates state
 * nobody's listening for anymore, and would break structured concurrency for whatever cancelled
 * the parent coroutine in the first place expecting that cancellation to actually propagate.
 * [RemoteAiMechanicChatService]'s own doc explains why its timeout is deliberately NOT built on
 * `withTimeout` — a timeout there must be catchable here, genuine cancellation must not be. The
 * retry path below follows the same rule: cancellation during the retry's [delay] or the retry
 * call itself is never caught here, so it propagates the same as on the first attempt.
 *
 * Every fallback to [local] now comes with a notice prepended to the answer — a real-device
 * test showed a silent fallback answering completely unrelated questions (wrong-topic local
 * guesses) with no indication the real AI hadn't actually answered, which looked like a broken
 * or "random" AI rather than an honest degradation. The notice differs by cause
 * ([AUTH_FALLBACK_NOTICE], [RATE_LIMITED_FALLBACK_NOTICE], [GENERIC_FALLBACK_NOTICE]) so it's
 * actually useful — "sign in again" is wrong advice for a network outage.
 */
class FallbackAiMechanicChatService(
    private val remote: AiMechanicChatService,
    private val local: AiMechanicChatService
) : AiMechanicChatService {

    override suspend fun ask(context: AiVehicleContext, history: List<ChatMessage>, question: String): ChatMessage {
        return try {
            askRemoteWithOneRetry(context, history, question)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RemoteAiNotAuthenticatedException) {
            withNotice(AUTH_FALLBACK_NOTICE, local.ask(context, history, question))
        } catch (e: RemoteAiHttpException) {
            when (e.statusCode) {
                401, 403 -> withNotice(AUTH_FALLBACK_NOTICE, local.ask(context, history, question))
                429 -> withNotice(RATE_LIMITED_FALLBACK_NOTICE, local.ask(context, history, question))
                else -> withNotice(GENERIC_FALLBACK_NOTICE, local.ask(context, history, question))
            }
        } catch (e: Exception) {
            withNotice(GENERIC_FALLBACK_NOTICE, local.ask(context, history, question))
        }
    }

    /**
     * One retry, only for failures plausibly fixed by trying again a moment later: a network
     * blip ([RemoteAiNetworkException]) or a momentary server error (HTTP 5xx — the same shape
     * of error seen repeatedly from a momentarily-overloaded Gemini during real backend
     * debugging). NOT retried: auth failures (a stale token is still stale), 429 (retrying
     * immediately just hits the same limit again), and malformed responses (retrying won't fix
     * a parsing bug) — for those, retrying would only delay the fallback the user is waiting on.
     */
    private suspend fun askRemoteWithOneRetry(context: AiVehicleContext, history: List<ChatMessage>, question: String): ChatMessage {
        return try {
            remote.ask(context, history, question)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RemoteAiNetworkException) {
            delay(RETRY_DELAY_MILLIS)
            remote.ask(context, history, question)
        } catch (e: RemoteAiHttpException) {
            if (e.statusCode in 500..599) {
                delay(RETRY_DELAY_MILLIS)
                remote.ask(context, history, question)
            } else {
                throw e
            }
        }
    }

    private fun withNotice(notice: String, reply: ChatMessage): ChatMessage =
        reply.copy(text = "$notice\n\n${reply.text}")

    companion object {
        private const val RETRY_DELAY_MILLIS = 400L

        const val AUTH_FALLBACK_NOTICE =
            "You're signed out (or your session expired), so this is a general answer, not a personalized one from the AI Mechanic. Sign in again for the full experience."
        const val RATE_LIMITED_FALLBACK_NOTICE =
            "You've sent a lot of questions quickly, so here's a general answer for now — try again in a moment for a personalized one."
        const val GENERIC_FALLBACK_NOTICE =
            "Couldn't reach the AI Mechanic just now, so this is a general answer instead. Please try again shortly."
    }
}
