# ai-mechanic Edge Function

Backend for `RemoteAiMechanicChatService` (see `app/src/main/java/com/enginesense/app/vehicle/ai/`).

**Status: written, reviewed a second time, still not deployed or tested.** No Supabase project,
Gemini API key, or deploy tooling exists in the environment this was written in. The second
review pass (against Google's live docs, fetched during that review) found and fixed one real
bug — a rate-limit race condition — and corrected the Gemini model ID, which was stale. Treat
this as a carefully-reviewed draft, not working code, until someone deploys and tests it for real.

## What this does

Accepts the request shape in `RemoteAiProtocol` (Android side) — verified field-for-field to
match, see the review notes below — verifies the caller's Google ID token, rate-limits per user
(atomically, via a Postgres function), calls Gemini with a system prompt that allows general
automotive knowledge but forbids fabricating anything about the specific user's vehicle beyond
what's in `scanContext`, and returns `{"reply": "..."}` or `{"error": {"code", "message"}}`.

## Before this can work at all

1. **Finish Google Sign-In on the Android side first.** `GOOGLE_WEB_CLIENT_ID` in
   `app/src/main/java/com/enginesense/app/auth/GoogleSignIn.kt` is still a placeholder. This
   function's auth check will correctly reject every request until that's real — that's
   intentional, not a bug to work around here.
2. **Create a Supabase project** (if one doesn't already exist for this app).
3. **Run the migration** in `supabase/migrations/20261017000000_ai_mechanic_requests.sql` —
   this creates both the rate-limit table AND the
   `check_and_record_ai_mechanic_rate_limit()` Postgres function the function now calls
   (added in the second review pass to close a race condition — see below).
4. **Get a Gemini API key** from Google AI Studio. Confirm `gemini-3.8-flash` (the model ID
   currently set in `index.ts`) is still available there — model availability changes; this
   was confirmed against `ai.google.dev/gemini-api/docs/models` during review, not guaranteed
   current at whatever point you actually deploy.
5. **Set secrets**:
   ```
   supabase secrets set GEMINI_API_KEY=<your key>
   supabase secrets set GOOGLE_WEB_CLIENT_ID=<same value as the Android app's GOOGLE_WEB_CLIENT_ID>
   ```
   (`SUPABASE_URL` / `SUPABASE_SERVICE_ROLE_KEY` are provided automatically to Edge Functions —
   don't set these yourself.)
6. **Deploy**: `supabase functions deploy ai-mechanic`.
7. **Smoke-test with curl directly** (see below) before touching the Android app at all.
8. **Point the app at it**: set `AiBackendConfig.ENDPOINT_URL` to the deployed function's URL
   and `AiBackendConfig.REMOTE_ENABLED = true` — both are `false`/empty by design until this
   point. Don't flip these without actually completing steps 1-7 first.

## What the second review pass found and fixed

- **Rate-limit race condition (real bug, fixed).** The original version did a separate SELECT
  count then INSERT from the Edge Function — two round-trips, not one transaction. Two
  concurrent requests from the same user could both pass the count check before either INSERT
  landed, letting the limit be exceeded. Fixed by moving the whole check-and-record into one
  Postgres function (`check_and_record_ai_mechanic_rate_limit`, in the migration), called via
  a single `supabase.rpc(...)` call — one statement, atomic by construction, with an advisory
  lock keyed per-user so concurrent requests from different users are never blocked by each other.
- **Stale model ID (fixed).** `gemini-2.0-flash` (the original guess) was listed as legacy when
  checked against `ai.google.dev/gemini-api/docs/models` directly. Updated to `gemini-3.8-flash`,
  the current flagship Flash model per that fetch. `gemini-3.5-flash-lite` is Google's own
  cheaper alternative if cost matters more than this does — swap the `GEMINI_MODEL` constant if so.
- **No timeout on the Gemini call (fixed).** Added an `AbortController`-based timeout
  (`GEMINI_TIMEOUT_MS`, 15s) — previously an unresponsive upstream could hang the function
  indefinitely (or until the platform's own execution-time ceiling, which isn't a deliberate
  design choice).
- **Blocked/safety responses weren't distinguished from generic failures (fixed).** Per Gemini's
  documented response schema (`promptFeedback.blockReason`, candidate-level `finishReason`
  values like `SAFETY`/`RECITATION`), a blocked response now throws a specific
  `GeminiBlockedError` and returns a tailored message, instead of being lumped in with "the
  model didn't respond."
- **Google ID token verification reviewed against Google's own backend-auth docs — confirmed
  correct as originally written**: JWKS URL, both accepted issuer strings, audience-as-client-ID
  check, and expiry (checked automatically by `jose`'s `jwtVerify`) all match Google's documented
  requirements. No `hd` (hosted-domain) check — correctly omitted, this is a consumer app, not
  restricted to a Google Workspace organization.
- **A real TypeScript compiler bug surfaced during review, unrelated to any of the above**:
  `!validated.ok` did not narrow the discriminated union return type of
  `parseAndValidateRequest` correctly under this environment's TypeScript compiler (v7.0.2),
  confirmed with an isolated repro. Fixed by using `validated.ok === false` instead, which
  narrows correctly. Worth knowing about if anyone sees similar narrowing failures elsewhere —
  this is a compiler-version quirk, not a mistake in how discriminated unions are normally used.
- **Contract match**: re-checked `RemoteAiProtocol.buildRequestBody`/`parseResponseBody`
  (Android/Kotlin) against this function's request parsing and response shape field-by-field —
  matches exactly (`question`, `history[{fromUser,text}]`, `context`, `knowledgeHints` →
  `{reply}` / `{error:{code,message}}`). One asymmetry, not a bug: this function always signals
  errors via HTTP status code (400/401/429/500/502), never a 200 with an `{"error":...}` body,
  so `RemoteAiProtocol`'s support for parsing an error object out of a 200 response body is
  never actually exercised by this backend. Not fixed — `RemoteAiMechanicChatService` only reads
  the body on success already, so this doesn't change behavior, just means that parsing path is
  currently dead code reachable only by some other future backend implementation.

## Tests

Added `index.test.ts` (Deno's built-in test runner) covering the two pieces of pure logic:
request validation/normalization and Gemini response parsing (success, prompt-level block,
candidate-level block for each safety-related `finishReason`, `MAX_TOKENS` correctly NOT treated
as a block, missing/empty text, malformed input). **Never run** — no `deno` binary in this
environment, only syntax-checked with `tsc --noEmit`. Run `deno test` for real before trusting it.

Deliberately NOT covered by tests, because testing them honestly needs real infrastructure this
environment doesn't have: auth (`verifyGoogleIdToken` needs a real signed JWT and a live JWKS
fetch), rate limiting (the atomicity fix is only meaningfully verified by a concurrent
integration test against a real Postgres database), and the actual Gemini HTTP call (needs a
fetch mock or a real network call). A test that fakes all of these would prove nothing beyond
"my mock does what I told it to."

## What I still could not verify from here

- **The exact Gemini `generateContent` request/response shape** — this review fetched
  `ai.google.dev/api/generate-content` directly and the field names used in `index.ts`
  (`candidates[].content.parts[].text`, `promptFeedback.blockReason`, `finishReason` values)
  match what was fetched, but the full `GenerationConfig` schema wasn't completely covered. Spot
  check the current docs before deploying.
- **The `jose` and `@supabase/supabase-js` esm.sh imports actually resolving in the real Supabase
  Edge Runtime** — syntactically fine, never executed.
- **Cost**: Gemini usage and Edge Function invocation costs both depend on real request volume —
  no way to estimate without your accounts.

## Testing this for real

Once deployed, call the function directly with `curl` and a real Google ID token before touching
the Android app:
```
curl -X POST https://<project-ref>.supabase.co/functions/v1/ai-mechanic \
  -H "Authorization: Bearer <real Google ID token>" \
  -H "Content-Type: application/json" \
  -d '{"question": "why is my mileage poor?", "history": [], "context": null, "knowledgeHints": []}'
```
Only flip `AiBackendConfig.REMOTE_ENABLED`/`ENDPOINT_URL` once that works.
