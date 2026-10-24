package com.enginesense.app.auth

import android.content.Context
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.json.JSONObject
import java.util.UUID

/**
 * Real Google Sign-In via Android's Credential Manager (the current Google-recommended API —
 * the older GoogleSignInClient is deprecated).
 *
 * [GOOGLE_WEB_CLIENT_ID] is the Google Cloud Console "Web application" OAuth client's ID
 * (project carbide-ward-511910-g8, created 17 Oct 2026) — deliberately NOT the separate
 * Android-type client ID also created for this app (package com.enginesense.app, debug SHA-1
 * AB:89:AD:1C:AE:4B:06:98:39:9D:E2:95:38:85:F1:F3:7E:A2:60:2D); the Android client only lets
 * Google recognize this app build as allowed to request a credential at all, while the Web
 * client ID is what actually ends up as the ID token's audience — it's the value this
 * function, and the backend verifying that token server-side, both need.
 *
 * Still unverified: this has not been exercised against a real device or Google account picker
 * (nothing here can be — unit tests can't drive that), and the Android-type client's SHA-1 only
 * covers debug builds; a release signing key will need its own Android-type client added later.
 * The "Skip for now" option on the login screens still exists so the rest of the UI can be used
 * either way; remove it once this has actually been verified signing in on a real device.
 */
const val GOOGLE_WEB_CLIENT_ID = "195829834262-1nfdpgold76mlna7om5pn7cist0e6ptu.apps.googleusercontent.com"

/**
 * [idToken] is the actual signed JWT (what the backend verifies) — distinct from [email],
 * which is [GoogleIdTokenCredential.id] (the account identifier, not a credential).
 */
data class GoogleSignInResult(val displayName: String?, val email: String?, val idToken: String)

suspend fun signInWithGoogle(context: Context): Result<GoogleSignInResult> = try {
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(GOOGLE_WEB_CLIENT_ID)
        .setFilterByAuthorizedAccounts(false)
        .setNonce(UUID.randomUUID().toString())
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val response = CredentialManager.create(context).getCredential(context, request)
    val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
    Result.success(GoogleSignInResult(credential.displayName, credential.id, credential.idToken))
} catch (e: Exception) {
    // Every real-world failure here — no configured client ID, no Google account on the
    // device, the user dismissing the account picker — should land the UI in the same
    // place: show what happened, offer Skip. Not worth distinguishing exception subtypes
    // for a screen whose only two outcomes are "signed in" or "didn't."
    Result.failure(e)
}

/**
 * Silently fetches a fresh Google ID token for a user who has already completed [signInWithGoogle]
 * before, without showing an account picker ([GetGoogleIdOption.setFilterByAuthorizedAccounts]
 * `true`, vs [signInWithGoogle]'s `false`). ID tokens are short-lived (observed ~1 hour expiry),
 * so [RemoteAiMechanicChatService][com.enginesense.app.vehicle.ai.RemoteAiMechanicChatService]
 * calls this per-request rather than reusing the token captured at sign-in time.
 *
 * Two things this needs to actually behave silently — both verified against
 * developer.android.com during review, after a real-device test where every call failed:
 * - `setAutoSelectEnabled(true)`: without it, Credential Manager can still want to show a
 *   confirmation UI even with a single authorized account, which an Application context (see
 *   below) can't host. Auto-select only engages with exactly one matching account, the user not
 *   signed out, and auto sign-in not disabled in their Google Account settings — short of that,
 *   this still correctly fails and the caller falls back to asking the user to sign in again.
 * - [context] must be Activity-rooted, not an Application context — the docs: "use an activity
 *   context to make sure the UI will be launched within the same task stack." The caller is
 *   responsible for passing one (see [com.enginesense.app.GetAutoSureViewModel]'s doc on why it
 *   can't just hold one itself).
 *
 * Returns failure — never throws past this boundary — when there's no authorized account to
 * silently retrieve a credential for. That covers both "never signed in" and "session expired
 * or revoked": Credential Manager doesn't distinguish the two for a silent request, and callers
 * shouldn't need to either — both mean "ask the user to sign in again."
 */
suspend fun getFreshGoogleIdToken(context: Context): Result<String> = try {
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(GOOGLE_WEB_CLIENT_ID)
        .setFilterByAuthorizedAccounts(true)
        .setAutoSelectEnabled(true)
        .setNonce(UUID.randomUUID().toString())
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val response = CredentialManager.create(context).getCredential(context, request)
    val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
    Result.success(credential.idToken)
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * Reads the unverified `exp` claim (seconds since epoch) out of a JWT's payload segment, purely
 * so [com.enginesense.app.GetAutoSureViewModel] can cache a token in memory and know when it's
 * worth re-fetching — this is never used to authorize anything; the backend independently
 * verifies the token's signature on every request regardless of what this returns. A real-device
 * test showed [getFreshGoogleIdToken] triggering Credential Manager's account picker on every
 * single chat message without this cache, which is what this function exists to fix.
 *
 * Returns null for anything malformed rather than throwing — callers should treat that the same
 * as "unknown expiry" and fall back to a conservative assumed lifetime.
 */
fun decodeJwtExpiry(jwt: String): Long? {
    val parts = jwt.split(".")
    if (parts.size < 2) return null
    return try {
        val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val exp = JSONObject(String(payloadBytes, Charsets.UTF_8)).optLong("exp", -1)
        exp.takeIf { it > 0 }
    } catch (e: Exception) {
        null
    }
}
