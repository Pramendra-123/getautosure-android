package com.enginesense.app.vehicle.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val NO_SCAN = AiVehicleContext.NO_SCAN

/** A fake that answers instantly, for asserting which service actually produced the reply. */
private class FixedAiMechanicChatService(private val replyText: String) : AiMechanicChatService {
    override suspend fun ask(context: AiVehicleContext, history: List<ChatMessage>, question: String) =
        ChatMessage(fromUser = false, text = replyText)
}

/** A fake that always fails — stands in for any of RemoteAiMechanicChatService's real failure modes. Tracks call count so tests can assert whether a retry happened. */
private class ThrowingAiMechanicChatService(private val exception: Exception) : AiMechanicChatService {
    var callCount = 0
        private set

    override suspend fun ask(context: AiVehicleContext, history: List<ChatMessage>, question: String): ChatMessage {
        callCount++
        throw exception
    }
}

/** Fails with [exception] on its first [failTimes] calls, then succeeds — for proving the retry actually recovers. */
private class FlakyAiMechanicChatService(
    private val failTimes: Int,
    private val exception: Exception,
    private val replyText: String
) : AiMechanicChatService {
    var callCount = 0
        private set

    override suspend fun ask(context: AiVehicleContext, history: List<ChatMessage>, question: String): ChatMessage {
        callCount++
        if (callCount <= failTimes) throw exception
        return ChatMessage(fromUser = false, text = replyText)
    }
}

/** Suspends forever (until cancelled) — used to prove cancellation actually propagates instead of being swallowed. */
private class HangingAiMechanicChatService : AiMechanicChatService {
    val started = CompletableDeferred<Unit>()
    override suspend fun ask(context: AiVehicleContext, history: List<ChatMessage>, question: String): ChatMessage {
        started.complete(Unit)
        CompletableDeferred<Unit>().await() // never completes on its own
        return ChatMessage(fromUser = false, text = "unreachable")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FallbackAiMechanicChatServiceTest {

    @Test
    fun `remote success is used directly, local is never called`() = runTest {
        val remote = FixedAiMechanicChatService("remote answer")
        val local = ThrowingAiMechanicChatService(IllegalStateException("local should not be called"))
        val fallback = FallbackAiMechanicChatService(remote, local)

        val reply = fallback.ask(NO_SCAN, emptyList(), "question")
        assertEquals("remote answer", reply.text)
    }

    // --- Retry: only for plausibly-transient failures (network, 5xx) ---

    @Test
    fun `a network failure that succeeds on retry returns the real remote answer, no fallback at all`() = runTest {
        val remote = FlakyAiMechanicChatService(failTimes = 1, exception = RemoteAiNetworkException(), replyText = "remote answer")
        val local = ThrowingAiMechanicChatService(IllegalStateException("local should not be called"))
        val reply = FallbackAiMechanicChatService(remote, local).ask(NO_SCAN, emptyList(), "question")
        assertEquals("remote answer", reply.text)
        assertEquals(2, remote.callCount)
    }

    @Test
    fun `a 503 that succeeds on retry returns the real remote answer too`() = runTest {
        // The exact shape of error seen repeatedly from a momentarily-overloaded Gemini during
        // real backend debugging — worth retrying rather than giving up immediately.
        val remote = FlakyAiMechanicChatService(failTimes = 1, exception = RemoteAiHttpException(503), replyText = "remote answer")
        val local = ThrowingAiMechanicChatService(IllegalStateException("local should not be called"))
        val reply = FallbackAiMechanicChatService(remote, local).ask(NO_SCAN, emptyList(), "question")
        assertEquals("remote answer", reply.text)
        assertEquals(2, remote.callCount)
    }

    @Test
    fun `a network failure that fails again on retry falls back to local, remote called exactly twice`() = runTest {
        val remote = ThrowingAiMechanicChatService(RemoteAiNetworkException())
        val local = FixedAiMechanicChatService("local answer")
        val reply = FallbackAiMechanicChatService(remote, local).ask(NO_SCAN, emptyList(), "question")
        assertTrue(reply.text.contains("local answer"))
        assertEquals(2, remote.callCount)
    }

    @Test
    fun `a 400 is not retried — remote is called exactly once before falling back`() = runTest {
        // Not auth, not rate-limited, not 5xx — a 400 means the request itself was malformed,
        // which a retry can't fix.
        val remote = ThrowingAiMechanicChatService(RemoteAiHttpException(400))
        val local = FixedAiMechanicChatService("local answer")
        val reply = FallbackAiMechanicChatService(remote, local).ask(NO_SCAN, emptyList(), "question")
        assertTrue(reply.text.contains("local answer"))
        assertEquals(1, remote.callCount)
    }

    @Test
    fun `an auth failure is not retried — remote is called exactly once`() = runTest {
        val remote = ThrowingAiMechanicChatService(RemoteAiNotAuthenticatedException())
        val local = FixedAiMechanicChatService("local answer")
        FallbackAiMechanicChatService(remote, local).ask(NO_SCAN, emptyList(), "question")
        assertEquals(1, remote.callCount)
    }

    @Test
    fun `a 429 is not retried — remote is called exactly once`() = runTest {
        val remote = ThrowingAiMechanicChatService(RemoteAiHttpException(429))
        val local = FixedAiMechanicChatService("local answer")
        FallbackAiMechanicChatService(remote, local).ask(NO_SCAN, emptyList(), "question")
        assertEquals(1, remote.callCount)
    }

    // --- Every fallback now carries a visible, cause-appropriate notice ---

    @Test
    fun `remote network failure falls back to local with the generic notice`() = runTest {
        val remote = ThrowingAiMechanicChatService(RemoteAiNetworkException())
        val local = FixedAiMechanicChatService("local answer")
        val reply = FallbackAiMechanicChatService(remote, local).ask(NO_SCAN, emptyList(), "question")
        assertTrue(reply.text.contains(FallbackAiMechanicChatService.GENERIC_FALLBACK_NOTICE))
        assertTrue(reply.text.contains("local answer"))
    }

    @Test
    fun `remote http error falls back to local with the generic notice`() = runTest {
        val remote = ThrowingAiMechanicChatService(RemoteAiHttpException(500))
        val local = FixedAiMechanicChatService("local answer")
        val reply = FallbackAiMechanicChatService(remote, local).ask(NO_SCAN, emptyList(), "question")
        assertTrue(reply.text.contains(FallbackAiMechanicChatService.GENERIC_FALLBACK_NOTICE))
    }

    @Test
    fun `remote parse failure falls back to local with the generic notice`() = runTest {
        val remote = ThrowingAiMechanicChatService(RemoteAiParseException("bad json"))
        val local = FixedAiMechanicChatService("local answer")
        val reply = FallbackAiMechanicChatService(remote, local).ask(NO_SCAN, emptyList(), "question")
        assertTrue(reply.text.contains(FallbackAiMechanicChatService.GENERIC_FALLBACK_NOTICE))
    }

    @Test
    fun `remote not configured falls back to local with the generic notice`() = runTest {
        val remote = ThrowingAiMechanicChatService(RemoteAiNotConfiguredException())
        val local = FixedAiMechanicChatService("local answer")
        val reply = FallbackAiMechanicChatService(remote, local).ask(NO_SCAN, emptyList(), "question")
        assertTrue(reply.text.contains(FallbackAiMechanicChatService.GENERIC_FALLBACK_NOTICE))
    }

    @Test
    fun `a missing or expired session falls back to local WITH the auth notice, not the generic one`() = runTest {
        val remote = ThrowingAiMechanicChatService(RemoteAiNotAuthenticatedException())
        val local = FixedAiMechanicChatService("local answer")
        val reply = FallbackAiMechanicChatService(remote, local).ask(NO_SCAN, emptyList(), "question")
        assertTrue(reply.text.contains(FallbackAiMechanicChatService.AUTH_FALLBACK_NOTICE))
        assertTrue(reply.text.contains("local answer"))
        assertFalse(reply.text.contains(FallbackAiMechanicChatService.GENERIC_FALLBACK_NOTICE))
    }

    @Test
    fun `an HTTP 401 from the backend falls back to local WITH the auth notice`() = runTest {
        val remote = ThrowingAiMechanicChatService(RemoteAiHttpException(401))
        val local = FixedAiMechanicChatService("local answer")
        val reply = FallbackAiMechanicChatService(remote, local).ask(NO_SCAN, emptyList(), "question")
        assertTrue(reply.text.contains(FallbackAiMechanicChatService.AUTH_FALLBACK_NOTICE))
    }

    @Test
    fun `an HTTP 403 from the backend falls back to local WITH the auth notice too`() = runTest {
        val remote = ThrowingAiMechanicChatService(RemoteAiHttpException(403))
        val local = FixedAiMechanicChatService("local answer")
        val reply = FallbackAiMechanicChatService(remote, local).ask(NO_SCAN, emptyList(), "question")
        assertTrue(reply.text.contains(FallbackAiMechanicChatService.AUTH_FALLBACK_NOTICE))
    }

    @Test
    fun `an HTTP 429 from the backend falls back to local WITH the rate-limited notice, not auth or generic`() = runTest {
        val remote = ThrowingAiMechanicChatService(RemoteAiHttpException(429))
        val local = FixedAiMechanicChatService("local answer")
        val reply = FallbackAiMechanicChatService(remote, local).ask(NO_SCAN, emptyList(), "question")
        assertTrue(reply.text.contains(FallbackAiMechanicChatService.RATE_LIMITED_FALLBACK_NOTICE))
        assertFalse(reply.text.contains(FallbackAiMechanicChatService.AUTH_FALLBACK_NOTICE))
        assertFalse(reply.text.contains(FallbackAiMechanicChatService.GENERIC_FALLBACK_NOTICE))
    }

    @Test
    fun `cancellation propagates and does NOT fall back to local`() = runTest {
        val remote = HangingAiMechanicChatService()
        val local = ThrowingAiMechanicChatService(IllegalStateException("local must not run on cancellation"))
        val fallback = FallbackAiMechanicChatService(remote, local)
        var caughtCancellation = false
        var localRan = false

        val job = launch {
            try {
                fallback.ask(NO_SCAN, emptyList(), "question")
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            } catch (e: IllegalStateException) {
                localRan = true
            }
        }

        launch {
            remote.started.await()
            job.cancel()
        }

        advanceUntilIdle()

        assertTrue("expected the ask() coroutine to observe cancellation, not swallow it", caughtCancellation)
        assertFalse("local fallback must not run when the call was cancelled, not failed", localRan)
        assertTrue(job.isCancelled)
    }
}
