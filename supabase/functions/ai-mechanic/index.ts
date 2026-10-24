// Supabase Edge Function implementing the backend contract described in
// app/src/main/java/com/enginesense/app/vehicle/ai/RemoteAiModels.kt (RemoteAiProtocol).
//
// STATUS: written, not deployed, not tested against a real Supabase project or the real
// Gemini API. No Supabase/Gemini credentials or CLI access exist in the environment this was
// written in. Reviewed a second time against Google's live docs (fetched during that review,
// see README.md) and fixed a real rate-limit race condition found in that pass — still
// unexecuted, not "working code" until someone deploys and tests it for real.
//
// Required secrets (set via `supabase secrets set`):
//   GEMINI_API_KEY        — Google AI Studio API key. Never exposed to the client.
//   GOOGLE_WEB_CLIENT_ID  — same value as GOOGLE_WEB_CLIENT_ID in the Android app's
//                           GoogleSignIn.kt. Used to verify the audience of incoming ID tokens.
//                           NOTE: that value is still a placeholder in the Android app as of
//                           this writing — this function's auth check will correctly reject
//                           everything until real Google Sign-In is finished on the client side.
// Required Supabase resources:
//   ai_mechanic_requests table + check_and_record_ai_mechanic_rate_limit() function —
//   see ../../migrations/ in this repo.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";
import { createRemoteJWKSet, jwtVerify } from "https://esm.sh/jose@5.9.6";

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY");
const GOOGLE_WEB_CLIENT_ID = Deno.env.get("GOOGLE_WEB_CLIENT_ID");
const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

// Mirrors RemoteAiProtocol's limits on the Android side — enforced again here since the
// client can't be trusted to actually respect its own caps.
export const MAX_QUESTION_LENGTH = 2000;
export const MAX_HISTORY_TURNS = 10;
export const MAX_KNOWLEDGE_HINTS = 5;

const RATE_LIMIT_PER_MINUTE = 8;
const RATE_LIMIT_PER_DAY = 80;
// Must stay comfortably under RemoteAiMechanicChatService.DEFAULT_TIMEOUT_MILLIS (32_000ms on
// the Android side). Raised from 15_000 after real-device testing showed repeated
// "Gemini API call timed out after 15000ms" failures — Gemini's latency for this model/project
// is genuinely variable (seen as low as ~5s and as high as 15s+ across different sessions,
// independent of any rate limit), and longer structured answers (maxOutputTokens was raised
// 1024->2048 to fix truncation) take more time to generate. If this were longer than the
// Android timeout, a slow Gemini response could still be in flight here — and still get billed
// — after the Android client has already given up and shown the local fallback answer, paying
// for a result nobody will ever see.
const GEMINI_TIMEOUT_MS = 25_000;

// Was "gemini-3.8-flash" (the newest stable Flash model) — switched after live smoke testing
// against the real deployed function hit real, repeated 503 "This model is currently
// experiencing high demand" errors from Google's API (confirmed via Edge Function logs, not
// assumed). "gemini-3.6-flash" is the previous-generation but still fully stable/GA Flash
// model — verified available at https://ai.google.dev/gemini-api/docs/models — and as an
// established (non-brand-new) model is much less likely to be capacity-constrained. Re-check
// if 503s recur; Google's own guidance for UNAVAILABLE is to retry, which this doesn't yet do.
export const GEMINI_MODEL = "gemini-3.6-flash";
const GEMINI_ENDPOINT =
  `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;

const GOOGLE_JWKS = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

const SYSTEM_PROMPT = `You are the GetAutoSure AI Mechanic, helping Indian car owners understand car problems, OBD2 scan results, and what to do next.

KNOWLEDGE: Use your own real automotive knowledge freely. Give complete, genuinely useful answers — don't limit yourself to only the optional reference hints provided below; they're background, not a cap on what you're allowed to know.

ABOUT THIS USER'S OWN VEHICLE, SPECIFICALLY — this part is not optional:
- "scanContext" below is the ONLY source of truth for this specific car's actual data. If it's null, no scan has been run on this car at all — say so plainly rather than guessing what a scan might show.
- Never state a fault code, sensor reading, VIN, MIL status, or any other fact about THIS car that is not explicitly present in scanContext. General knowledge about what a symptom or code usually means is fine and encouraged; claiming it's true of this specific car when scanContext doesn't say so is not.
- Always make it clear in your answer which parts are confirmed from this car's actual scan (from scanContext) versus general automotive knowledge or advice.

SAFETY:
- For overheating, oil pressure warnings, brake failure/soft pedal, or other dangerous symptoms, give clear safety guidance (e.g. stop driving, don't open a hot cooling system) before anything else.
- Never claim an OBD scan can verify something it physically cannot measure (compression, brake pad thickness, tyre tread, body/rust condition, etc.) — say that needs a physical inspection.
- Never state a specific repair cost as a real quote. Costs vary by shop and region and you don't have real pricing data — suggest getting a quote from a mechanic instead.
- Present causes as possibilities, not confirmed diagnoses, unless scanContext directly confirms them.

RESPONSE FORMAT:
- A simple, direct question (a yes/no, a quick fact, a short follow-up) gets a short answer — 1-3 sentences, no headers. Don't pad a simple question with the structure below.
- A genuinely complex or safety-critical question (diagnosing a symptom, explaining a code, "what's wrong with my car") uses this structure, in Markdown, every section kept tight — one or two sentences or a few one-line bullets, not paragraphs:
  **What it means** — one or two sentences.
  **Evidence** — one line: what's confirmed from scanContext vs. general knowledge.
  **Possible causes** — up to 4 bullets, one line each.
  **Urgency** — one sentence.
  **Safe next steps** — up to 3 bullets or 2 sentences.
  **Limitations** — one closing sentence noting what's general guidance vs. confirmed, and that this isn't a substitute for a mechanic's physical inspection.
- Keep a full structured answer under roughly 200 words total. Being concise is more useful than being exhaustive — a mechanic can fill in detail a chat answer can't.`;

export interface RemoteAskRequest {
  question: string;
  history: Array<{ fromUser: boolean; text: string }>;
  context: Record<string, unknown> | null;
  knowledgeHints: string[];
}

export function jsonError(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ error: { code, message } }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/**
 * Pure validation/normalization of the raw parsed JSON body — pulled out of the request
 * handler so it's unit-testable without a running server (see index.test.ts). Returns an error
 * code/message pair instead of throwing, since "malformed input" here is an expected, routine
 * case, not an exceptional one.
 */
export function parseAndValidateRequest(
  rawBody: unknown,
): { ok: true; request: RemoteAskRequest } | { ok: false; code: string; message: string } {
  if (typeof rawBody !== "object" || rawBody === null) {
    return { ok: false, code: "invalid_request", message: "Request body must be a JSON object" };
  }
  const body = rawBody as Partial<RemoteAskRequest>;

  const question = typeof body.question === "string" ? body.question.slice(0, MAX_QUESTION_LENGTH).trim() : "";
  if (!question) return { ok: false, code: "invalid_request", message: "question is required" };

  const history = Array.isArray(body.history)
    ? body.history
      .filter((m): m is { fromUser: boolean; text: string } =>
        typeof m?.fromUser === "boolean" && typeof m?.text === "string"
      )
      .slice(-MAX_HISTORY_TURNS)
    : [];

  const context = body.context && typeof body.context === "object" ? body.context as Record<string, unknown> : null;
  const knowledgeHints = Array.isArray(body.knowledgeHints)
    ? body.knowledgeHints.filter((h): h is string => typeof h === "string").slice(0, MAX_KNOWLEDGE_HINTS)
    : [];

  return { ok: true, request: { question, history, context, knowledgeHints } };
}

export async function verifyGoogleIdToken(authHeader: string | null): Promise<string> {
  if (!authHeader?.startsWith("Bearer ")) {
    throw jsonError(401, "unauthorized", "Missing bearer token");
  }
  const token = authHeader.slice("Bearer ".length);
  try {
    const { payload } = await jwtVerify(token, GOOGLE_JWKS, {
      issuer: ["https://accounts.google.com", "accounts.google.com"],
      audience: GOOGLE_WEB_CLIENT_ID,
    });
    // jwtVerify already rejects an expired/invalid-signature/wrong-issuer/wrong-audience token
    // by throwing before this line is reached — `sub` is the one additional shape check it
    // doesn't make for us.
    if (typeof payload.sub !== "string") throw new Error("token has no subject");
    return payload.sub;
  } catch (e) {
    console.error("ID token verification failed", e);
    throw jsonError(401, "unauthorized", "Invalid or expired sign-in token");
  }
}

/**
 * Atomic via a single Postgres function call (see the migration) — the previous version of
 * this did a separate SELECT count then INSERT from here, which raced: two concurrent requests
 * from the same user could both pass the count check before either INSERT landed. One RPC call
 * is one statement from Postgres's side, so the whole check-and-record happens under a single
 * advisory-lock-guarded transaction instead of two separate round-trips this function could be
 * interleaved between.
 */
async function checkAndRecordRateLimit(
  supabase: ReturnType<typeof createClient>,
  userId: string,
): Promise<{ allowed: boolean; reason?: string }> {
  const { data, error } = await supabase.rpc("check_and_record_ai_mechanic_rate_limit", {
    p_user_id: userId,
    p_minute_limit: RATE_LIMIT_PER_MINUTE,
    p_day_limit: RATE_LIMIT_PER_DAY,
  });
  if (error) {
    console.error("Rate limit check failed", error);
    // Fail closed: if we can't confirm the user is under their limit, don't call the paid
    // model on their behalf.
    return { allowed: false, reason: "Could not verify request limits — please try again shortly." };
  }
  return data === true
    ? { allowed: true }
    : { allowed: false, reason: "Too many requests — please wait and try again." };
}

function buildGeminiBody(req: RemoteAskRequest) {
  const historyText = req.history
    .slice(-MAX_HISTORY_TURNS)
    .map((m) => `${m.fromUser ? "User" : "AI Mechanic"}: ${m.text}`)
    .join("\n");

  const hintsText = req.knowledgeHints.slice(0, MAX_KNOWLEDGE_HINTS).join("\n");

  const userTurn = [
    `scanContext: ${req.context ? JSON.stringify(req.context) : "null"}`,
    hintsText ? `Reference hints (optional background, not a limit on your knowledge):\n${hintsText}` : "",
    historyText ? `Conversation so far:\n${historyText}` : "",
    `Current question: ${req.question}`,
  ].filter(Boolean).join("\n\n");

  return {
    systemInstruction: { role: "system", parts: [{ text: SYSTEM_PROMPT }] },
    contents: [{ role: "user", parts: [{ text: userTurn }] }],
    // gemini-3.6-flash supports "minimal" thinking (unlike 3.8-flash, which floors at "low") —
    // verified at https://ai.google.dev/gemini-api/docs/thinking. Keeping thinking as low as
    // possible both cuts latency and avoids maxOutputTokens being eaten by thinking tokens.
    //
    // maxOutputTokens was 1024 — real-device testing showed longer structured answers (the
    // system prompt's own "what to tell your mechanic" / numbered-questions format) getting cut
    // off mid-sentence once thinking tokens and a long reply together crossed that cap. Raised
    // with headroom; cost impact is small since thinkingLevel is already "minimal".
    generationConfig: {
      maxOutputTokens: 2048,
      temperature: 0.4,
      thinkingConfig: { thinkingLevel: "minimal" },
    },
  };
}

/** Thrown specifically for a safety/policy block, as distinct from a generic parse failure — see callers for why that distinction matters. */
export class GeminiBlockedError extends Error {
  constructor(public reason: string) {
    super(`Gemini blocked this request: ${reason}`);
  }
}

/**
 * Pure parsing of Gemini's generateContent response — pulled out of callGemini so it's
 * unit-testable with recorded/fabricated response bodies, without a real API call.
 * Schema verified against https://ai.google.dev/api/generate-content during review:
 * - promptFeedback.blockReason set, no candidates → the prompt itself was blocked.
 * - candidates[0].finishReason one of the safety/policy values → that candidate was blocked.
 * - otherwise, candidates[0].content.parts[0].text is the reply.
 */
export function parseGeminiResponse(data: any): string {
  const blockReason = data?.promptFeedback?.blockReason;
  if (blockReason && blockReason !== "BLOCK_REASON_UNSPECIFIED") {
    throw new GeminiBlockedError(`prompt blocked: ${blockReason}`);
  }

  const candidate = data?.candidates?.[0];
  const SAFETY_FINISH_REASONS = new Set([
    "SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY",
    "IMAGE_PROHIBITED_CONTENT", "RECITATION", "IMAGE_RECITATION",
  ]);
  if (candidate?.finishReason && SAFETY_FINISH_REASONS.has(candidate.finishReason)) {
    throw new GeminiBlockedError(`candidate blocked: ${candidate.finishReason}`);
  }

  const text = candidate?.content?.parts?.[0]?.text;
  if (typeof text !== "string" || !text.trim()) {
    throw new Error(
      `Gemini response had no usable text (finishReason=${candidate?.finishReason ?? "none"}, ` +
        `candidates=${data?.candidates?.length ?? 0})`,
    );
  }
  return text.trim();
}

async function callGemini(req: RemoteAskRequest): Promise<string> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), GEMINI_TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(`${GEMINI_ENDPOINT}?key=${GEMINI_API_KEY}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(buildGeminiBody(req)),
      signal: controller.signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") {
      throw new Error(`Gemini API call timed out after ${GEMINI_TIMEOUT_MS}ms`);
    }
    throw e;
  } finally {
    clearTimeout(timeout);
  }

  if (!response.ok) {
    const bodyText = await response.text().catch(() => "");
    console.error("Gemini API error", response.status, bodyText);
    throw new Error(`Gemini API returned ${response.status}`);
  }

  const data = await response.json();
  return parseGeminiResponse(data);
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return jsonError(405, "method_not_allowed", "Only POST is supported");

  let userId: string;
  try {
    userId = await verifyGoogleIdToken(req.headers.get("Authorization"));
  } catch (e) {
    if (e instanceof Response) return e;
    return jsonError(401, "unauthorized", "Authentication failed");
  }

  let rawBody: unknown;
  try {
    rawBody = await req.json();
  } catch {
    return jsonError(400, "invalid_request", "Malformed JSON body");
  }

  const validated = parseAndValidateRequest(rawBody);
  // `validated.ok === false`, not `!validated.ok` — verified during review that the latter
  // does not narrow this discriminated union correctly under the TypeScript compiler available
  // in this environment (confirmed via an isolated repro); this form does.
  if (validated.ok === false) return jsonError(400, validated.code, validated.message);

  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    console.error("Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY secret");
    return jsonError(500, "misconfigured", "Server is not configured correctly");
  }
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  const rateLimit = await checkAndRecordRateLimit(supabase, userId);
  if (!rateLimit.allowed) {
    return jsonError(429, "rate_limited", rateLimit.reason ?? "Too many requests");
  }

  if (!GEMINI_API_KEY) {
    console.error("Missing GEMINI_API_KEY secret");
    return jsonError(500, "misconfigured", "Server is not configured correctly");
  }

  try {
    const reply = await callGemini(validated.request);
    return new Response(JSON.stringify({ reply }), { headers: { "Content-Type": "application/json" } });
  } catch (e) {
    if (e instanceof GeminiBlockedError) {
      console.error("Gemini blocked the request", e.reason);
      return jsonError(
        422,
        "blocked",
        "I can't give a specific answer to that one — try rephrasing, or for anything safety-critical, talk to a mechanic directly.",
      );
    }
    console.error("Upstream model call failed", e);
    return jsonError(502, "upstream_error", "The AI model didn't respond. Please try again.");
  }
});
