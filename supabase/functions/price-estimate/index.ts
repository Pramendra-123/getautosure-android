// Supabase Edge Function: a used-car market price from Gemini, grounded in a live Google search of
// current Indian listings, with the working shown. The app's own formula (ExShowroomPrices +
// FairPriceEstimator) stays the fallback: the app uses it whenever this fails, times out, or
// answers something far from the formula.
//
// Same answer for the same car: results are cached per normalised car (make, model, variant,
// year, fuel, km band, city, owners) for CACHE_TTL_DAYS, so everyone asking about that car gets
// the same price and Gemini is paid once. Temperature is 0 and the answer is fixed JSON.
//
// The price is for the car in good working order, BEFORE repairs — the app takes its own repair
// estimate off afterwards, so the cached price stays reusable across cars with different faults.
//
// Secrets: GEMINI_API_KEY, GOOGLE_WEB_CLIENT_ID (same as ai-mechanic), SUPABASE_URL,
// SUPABASE_SERVICE_ROLE_KEY. Tables: price_estimate_cache, price_estimate_requests
// (migrations/20261124000000_price_estimates.sql).

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";
import { createRemoteJWKSet, jwtVerify } from "https://esm.sh/jose@5.9.6";

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY");
const GOOGLE_WEB_CLIENT_ID = Deno.env.get("GOOGLE_WEB_CLIENT_ID");
const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

export const GEMINI_MODEL = "gemini-3.6-flash";
const GEMINI_ENDPOINT = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;
// Search plus reasoning takes longer than a chat answer. Must stay under the app's 50 s call timeout.
const GEMINI_TIMEOUT_MS = 42_000;
export const CACHE_TTL_DAYS = 7;
/** Fresh (uncached) estimates per user per day. Cache hits are free and not counted. */
const DAILY_LIMIT = 30;
export const KM_BAND = 10_000;
export const CONFIDENCE = ["high", "medium", "low"];
export const VARIANTS = ["Base", "Mid", "Top"];

const GOOGLE_JWKS = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

const SYSTEM_PROMPT = `You price used cars for GetAutoSure, an Indian app a buyer uses while looking at a car for sale.

Search the web for CURRENT used-car listings and price guides in India for this exact make and model (CarDekho, CarWale, Cars24, Spinny, OLX, Droom, CarTrade and similar), the same or nearest variant and year, ideally in or near the given city. Use what you find, not memory, wherever you can.

Give a fair market price range in Indian rupees for THIS car in good working order, BEFORE any repairs (the app subtracts repair costs itself — never subtract repairs). Adjust for:
- km driven against the usual ~10,000–15,000 km a year,
- the number of owners (2nd owner a little less, 3rd+ more),
- fuel type (older diesels in Delhi NCR lose value near the 10-year limit),
- dealer listings asking more than private sales: aim for what a private buyer would fairly pay.

Rules:
- lowInr and highInr are whole rupees, highInr no more than about 20% above lowInr.
- newPriceInr: today's ex-showroom price of that variant if you can find it, else 0.
- steps: 3 to 5 short plain-English lines a buyer can follow, each one fact, e.g. "Similar 2019 Creta diesels with 50–70k km list at ₹9.0–9.8 lakh in Pune", "2nd owner: about 3% less". No markdown.
- comparables: up to 4 real listings or price-guide figures you found, each with a short description, the price and the site name. Never invent one; leave the list empty if you found none.
- confidence: "high" with several close listings, "medium" with a few or only nearby years/variants, "low" when you mostly had to estimate.`;

export interface PriceRequest {
  make: string;
  model: string;
  /** The model exactly as the RTO record has it, e.g. "SLAVIA STYLE 1.0 TSI AT" — names the variant and gearbox. */
  rcModel: string;
  variant: string;
  year: number;
  fuel: string;
  km: number | null;
  city: string;
  owners: number | null;
}

export function jsonError(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ error: { code, message } }), { status, headers: { "Content-Type": "application/json" } });
}

const clean = (v: unknown, max: number) => typeof v === "string" ? v.replace(/[\r\n]+/g, " ").trim().slice(0, max) : "";

/** Validates and normalises the body. Make, model and a sensible year are required; the rest is optional. */
export function parseRequest(raw: unknown, currentYear = new Date().getFullYear()): { ok: true; request: PriceRequest } | { ok: false; message: string } {
  if (typeof raw !== "object" || raw === null) return { ok: false, message: "Request body must be a JSON object" };
  const b = raw as Record<string, unknown>;
  const make = clean(b.make, 40);
  const model = clean(b.model, 60);
  if (!make || !model) return { ok: false, message: "make and model are required" };
  const year = typeof b.year === "number" ? Math.round(b.year) : NaN;
  if (!(year >= 1990 && year <= currentYear)) return { ok: false, message: "year is missing or out of range" };
  const variant = VARIANTS.find((v) => v === b.variant) ?? "Mid";
  const km = typeof b.km === "number" && b.km >= 0 && b.km < 2_000_000 ? Math.round(b.km) : null;
  const owners = typeof b.owners === "number" && b.owners >= 1 && b.owners <= 15 ? Math.round(b.owners) : null;
  return {
    ok: true,
    request: { make, model, rcModel: clean(b.rcModel, 80), variant, year, fuel: clean(b.fuel, 20), km, city: clean(b.city, 60), owners },
  };
}

/** The km figure is banded so nearby readings share one cached price. */
export function kmBand(km: number | null): number | null {
  return km == null ? null : Math.round(km / KM_BAND) * KM_BAND;
}

/** One key per car as priced: same normalised inputs, same key, same answer. */
export function cacheKey(r: PriceRequest): string {
  // With the RTO's exact model text, the guessed Base/Mid/Top variant doesn't matter, so it's left out of the key.
  const variant = r.rcModel ? `rc:${r.rcModel}` : r.variant;
  return [r.make, r.model, variant, r.year, r.fuel, kmBand(r.km) ?? "", r.city, r.owners ?? ""]
    .map((p) => String(p).toLowerCase().replace(/\s+/g, " ").trim())
    .join("|");
}

function userTurn(r: PriceRequest): string {
  const band = kmBand(r.km);
  return [
    r.rcModel
      ? `Car: ${r.year} ${r.make} ${r.model}. The RTO record names it "${r.rcModel}" — use that to find the exact variant, engine and gearbox (AT/AMT/DCT/CVT/DSG mean automatic, MT or none means manual), and compare listings of that variant and gearbox.`
      : `Car: ${r.year} ${r.make} ${r.model}, ${r.variant.toLowerCase()} variant (exact variant not known)`,
    r.fuel ? `Fuel: ${r.fuel}` : "",
    band != null ? `Km driven: about ${band.toLocaleString("en-IN")} km` : "Km driven: not known",
    r.owners != null ? `Owners so far: ${r.owners}` : "",
    `City: ${r.city || "India (city not known)"}`,
  ].filter(Boolean).join("\n");
}

const RESPONSE_SCHEMA = {
  type: "OBJECT",
  properties: {
    lowInr: { type: "INTEGER" },
    highInr: { type: "INTEGER" },
    newPriceInr: { type: "INTEGER" },
    confidence: { type: "STRING", enum: CONFIDENCE },
    steps: { type: "ARRAY", items: { type: "STRING" } },
    comparables: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: { description: { type: "STRING" }, priceInr: { type: "INTEGER" }, site: { type: "STRING" } },
        required: ["description", "priceInr", "site"],
      },
    },
  },
  required: ["lowInr", "highInr", "newPriceInr", "confidence", "steps", "comparables"],
};

export interface PriceEstimate {
  lowInr: number;
  highInr: number;
  newPriceInr: number | null;
  confidence: string;
  steps: string[];
  comparables: Array<{ description: string; priceInr: number; site: string }>;
  sources: Array<{ title: string; url: string }>;
  model: string;
}

/** Pulls the JSON object out of the reply, whether it came back as bare JSON or wrapped in a ``` block. */
function extractJson(text: string): any {
  try {
    return JSON.parse(text);
  } catch {
    const start = text.indexOf("{");
    const end = text.lastIndexOf("}");
    if (start < 0 || end <= start) throw new Error("No JSON object in Gemini reply");
    return JSON.parse(text.slice(start, end + 1));
  }
}

/**
 * Reads Gemini's reply into a checked estimate, or throws. A range that's back to front, implausibly
 * wide, or outside ₹30,000–₹5 crore is rejected rather than shown.
 */
export function parseEstimate(data: any): PriceEstimate {
  const parts = data?.candidates?.[0]?.content?.parts;
  const text = Array.isArray(parts) ? parts.map((p: any) => typeof p?.text === "string" ? p.text : "").join("") : "";
  if (!text.trim()) throw new Error(`Gemini reply had no text (finishReason=${data?.candidates?.[0]?.finishReason ?? "none"})`);
  const p = extractJson(text);

  let low = Math.round(Number(p?.lowInr));
  let high = Math.round(Number(p?.highInr));
  if (!Number.isFinite(low) || !Number.isFinite(high)) throw new Error("Price missing");
  if (low > high) [low, high] = [high, low];
  if (low < 30_000 || high > 50_000_000) throw new Error(`Price out of range: ${low}–${high}`);
  if (high > low * 1.6) throw new Error(`Range too wide: ${low}–${high}`);

  const steps = (Array.isArray(p?.steps) ? p.steps : [])
    .filter((s: unknown): s is string => typeof s === "string" && s.trim().length > 0)
    .map((s: string) => s.trim().slice(0, 200))
    .slice(0, 6);
  if (steps.length === 0) throw new Error("No explanation given");

  const comparables = (Array.isArray(p?.comparables) ? p.comparables : [])
    .filter((c: any) => typeof c?.description === "string" && Number(c?.priceInr) > 0)
    .slice(0, 4)
    .map((c: any) => ({ description: c.description.slice(0, 140), priceInr: Math.round(Number(c.priceInr)), site: clean(c.site, 40) }));

  const chunks = data?.candidates?.[0]?.groundingMetadata?.groundingChunks;
  const seen = new Set<string>();
  const sources = (Array.isArray(chunks) ? chunks : [])
    .map((c: any) => ({ title: clean(c?.web?.title, 80), url: typeof c?.web?.uri === "string" ? c.web.uri : "" }))
    .filter((s: { title: string; url: string }) => s.url && !seen.has(s.title) && (seen.add(s.title), true))
    .slice(0, 5);

  const newPrice = Math.round(Number(p?.newPriceInr));
  return {
    lowInr: low,
    highInr: high,
    newPriceInr: newPrice > 0 ? newPrice : null,
    confidence: CONFIDENCE.includes(p?.confidence) ? p.confidence : "low",
    steps,
    comparables,
    sources,
    model: GEMINI_MODEL,
  };
}

async function callGemini(r: PriceRequest, structured: boolean): Promise<Response> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), GEMINI_TIMEOUT_MS);
  try {
    return await fetch(`${GEMINI_ENDPOINT}?key=${GEMINI_API_KEY}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      signal: controller.signal,
      body: JSON.stringify({
        systemInstruction: { role: "system", parts: [{ text: SYSTEM_PROMPT + (structured ? "" : "\n\nReply with only the JSON object, no other text.") }] },
        contents: [{ role: "user", parts: [{ text: userTurn(r) }] }],
        tools: [{ google_search: {} }],
        generationConfig: {
          temperature: 0,
          maxOutputTokens: 2048,
          thinkingConfig: { thinkingLevel: "low" },
          ...(structured ? { responseMimeType: "application/json", responseSchema: RESPONSE_SCHEMA } : {}),
        },
      }),
    });
  } finally {
    clearTimeout(timeout);
  }
}

async function estimate(r: PriceRequest): Promise<PriceEstimate> {
  let response = await callGemini(r, true);
  // Older model versions reject a response schema alongside the search tool; ask for plain JSON then.
  if (response.status === 400) {
    console.warn("Structured output with search rejected, retrying as plain JSON", await response.text().catch(() => ""));
    response = await callGemini(r, false);
  }
  if (!response.ok) {
    console.error("Gemini API error", response.status, await response.text().catch(() => ""));
    throw new Error(`Gemini API returned ${response.status}`);
  }
  return parseEstimate(await response.json());
}

async function verifyGoogleIdToken(authHeader: string | null): Promise<string> {
  if (!authHeader?.startsWith("Bearer ")) throw jsonError(401, "unauthorized", "Missing bearer token");
  try {
    const { payload } = await jwtVerify(authHeader.slice("Bearer ".length), GOOGLE_JWKS, {
      issuer: ["https://accounts.google.com", "accounts.google.com"],
      audience: GOOGLE_WEB_CLIENT_ID,
    });
    if (typeof payload.sub !== "string") throw new Error("token has no subject");
    return payload.sub;
  } catch (e) {
    console.error("ID token verification failed", e);
    throw jsonError(401, "unauthorized", "Invalid or expired sign-in token");
  }
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
  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    return jsonError(400, "invalid_request", "Malformed JSON body");
  }
  const parsed = parseRequest(raw);
  if (parsed.ok === false) return jsonError(400, "invalid_request", parsed.message);
  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY || !GEMINI_API_KEY) {
    return jsonError(500, "misconfigured", "Server is not configured correctly");
  }
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
  const key = cacheKey(parsed.request);

  const since = new Date(Date.now() - CACHE_TTL_DAYS * 86_400_000).toISOString();
  const { data: hit } = await supabase.from("price_estimate_cache").select("response, created_at")
    .eq("cache_key", key).gte("created_at", since).maybeSingle();
  if (hit?.response) {
    return new Response(JSON.stringify({ ...hit.response, cached: true, pricedAt: hit.created_at }), { headers: { "Content-Type": "application/json" } });
  }

  // Only fresh estimates cost money, so only they count towards the daily limit.
  const dayAgo = new Date(Date.now() - 86_400_000).toISOString();
  const { count, error: countError } = await supabase.from("price_estimate_requests")
    .select("id", { count: "exact", head: true }).eq("user_id", userId).gte("created_at", dayAgo);
  if (countError) {
    console.error("Limit check failed", countError);
    return jsonError(503, "unavailable", "Couldn't check limits just now.");
  }
  if ((count ?? 0) >= DAILY_LIMIT) return jsonError(429, "rate_limited", "Too many price checks today.");
  await supabase.from("price_estimate_requests").insert({ user_id: userId, cache_key: key });

  try {
    const result = await estimate(parsed.request);
    const pricedAt = new Date().toISOString();
    const { error: saveError } = await supabase.from("price_estimate_cache")
      .upsert({ cache_key: key, request: parsed.request, response: result, created_at: pricedAt });
    if (saveError) console.error("Cache save failed", saveError);
    return new Response(JSON.stringify({ ...result, cached: false, pricedAt }), { headers: { "Content-Type": "application/json" } });
  } catch (e) {
    console.error("Price estimate failed", e);
    return jsonError(502, "upstream_error", "Couldn't get a market price just now.");
  }
});
