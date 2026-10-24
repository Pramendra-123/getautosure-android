package com.enginesense.app.auth

import android.content.Context

private const val TOKEN_EXPIRY_SAFETY_MARGIN_SECONDS = 60L
private const val ASSUMED_TOKEN_LIFETIME_SECONDS = 15L * 60L

/**
 * Caches a Google ID token in memory, re-fetching via Credential Manager only when the cached
 * token is actually near expiry (decoded from its own `exp` claim), not on every call — the
 * same real-device-tested approach [com.enginesense.app.GetAutoSureViewModel] uses inline for
 * AI Mechanic (see its own doc comment on `authUiContext` for the account-picker-on-every-
 * -message bug this fixes). Pulled out as a reusable class here since
 * [com.enginesense.app.consultation.ConsultationViewModel] is now a second caller;
 * GetAutoSureViewModel's own inline copy is deliberately left as-is rather than migrated, to
 * avoid touching already-working, already-tested code for a refactor nobody asked for.
 */
class GoogleIdTokenCache {
    private data class CachedToken(val token: String, val expiresAtEpochSeconds: Long)
    private var cached: CachedToken? = null

    /**
     * @param uiContext Must be Activity-rooted, not an Application context — see
     * [getFreshGoogleIdToken]'s own doc for why; a real-device test showed Application context
     * failing Credential Manager's silent fetch every single time.
     * @throws Exception whatever [getFreshGoogleIdToken] failed with, when no cached token is
     * usable and a fresh fetch also fails (not signed in, session revoked, etc.) — callers map
     * this to their own "not authenticated" type, same as elsewhere in this app.
     */
    suspend fun get(uiContext: Context): String {
        val now = System.currentTimeMillis() / 1000
        val current = cached
        if (current != null && current.expiresAtEpochSeconds - TOKEN_EXPIRY_SAFETY_MARGIN_SECONDS > now) {
            return current.token
        }
        val fresh = getFreshGoogleIdToken(uiContext).getOrElse { throw it }
        val expiry = decodeJwtExpiry(fresh) ?: (now + ASSUMED_TOKEN_LIFETIME_SECONDS)
        cached = CachedToken(fresh, expiry)
        return fresh
    }
}
