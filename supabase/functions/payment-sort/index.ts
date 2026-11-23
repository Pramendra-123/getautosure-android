// Supabase Edge Function: sorts a car-owner's payment alert that the app's own rules couldn't.
//
// The app sends only what it needs — merchant name, amount and the alert text with account
// numbers, card digits and balances already removed — and only for alerts its rules couldn't
// sort. Nothing is stored here. Same Google sign-in check and secrets as ai-mechanic:
//   GEMINI_API_KEY, GOOGLE_WEB_CLIENT_ID.

import { createRemoteJWKSet, jwtVerify } from "https://esm.sh/jose@5.9.6";

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY");
const GOOGLE_WEB_CLIENT_ID = Deno.env.get("GOOGLE_WEB_CLIENT_ID");

export const GEMINI_MODEL = "gemini-3.6-flash";
const GEMINI_ENDPOINT = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;
const GEMINI_TIMEOUT_MS = 15_000;
export const MAX_TEXT_LENGTH = 300;
export const MAX_ITEMS = 10;

/** Wallet categories the app knows, plus NOT_CAR. Mirrors ExpenseCategory in the Android app. */
export const CATEGORIES = ["SERVICE", "REPAIR", "PARTS", "INSURANCE", "TOLL", "PARKING", "WASH", "PUC", "FUEL", "OTHER", "NOT_CAR"];
export const CONFIDENCE = ["high", "medium", "low"];

const GOOGLE_JWKS = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

const SYSTEM_PROMPT = `You sort payments for GetAutoSure, an app where Indian car owners track what their car costs.
For each payment (merchant name, amount in ₹, and a bank or UPI alert with private numbers removed), decide whether it was most likely spent on the owner's car, and on what:
SERVICE (scheduled service at a workshop or dealer), REPAIR (fixing a fault), PARTS (spare parts, tyres, battery), INSURANCE (motor insurance), TOLL, PARKING, WASH (car wash, detailing), PUC (pollution check), FUEL, OTHER (clearly car-related but none of these, e.g. accessories), or NOT_CAR (groceries, food, rent, shopping, transfers to people, anything not for a car).
Use Indian context: names with "Motors", "Automobiles", "Auto Works", "Car Care", "Garage" are usually workshops; dealer names (e.g. "Maruti Arena", "Hyundai Service") are SERVICE; a person's name is NOT_CAR.
confidence: high when the merchant name makes it clear, medium when likely, low when guessing. When unsure whether it's for the car at all, prefer low confidence over NOT_CAR.`;

export interface PaymentItem {
  id: string;
  merchant: string;
  amountInr: number;
  text: string;
}

export function jsonError(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ error: { code, message } }), { status, headers: { "Content-Type": "application/json" } });
}

/** Validates the body: up to MAX_ITEMS payments, each with an id, merchant, positive amount and short text. */
export function parseItems(raw: unknown): { ok: true; items: PaymentItem[] } | { ok: false; message: string } {
  const body = raw as { payments?: unknown };
  if (!body || !Array.isArray(body.payments)) return { ok: false, message: "payments array is required" };
  const items = body.payments
    .filter((p): p is PaymentItem =>
      typeof p?.id === "string" && typeof p?.merchant === "string" && typeof p?.amountInr === "number" && p.amountInr > 0
    )
    .slice(0, MAX_ITEMS)
    .map((p) => ({
      id: p.id.slice(0, 40),
      merchant: p.merchant.slice(0, 60),
      amountInr: Math.round(p.amountInr),
      text: typeof p.text === "string" ? p.text.slice(0, MAX_TEXT_LENGTH) : "",
    }));
  if (items.length === 0) return { ok: false, message: "no valid payments" };
  return { ok: true, items };
}

/** Keeps only well-formed answers for ids that were asked about. */
export function parseSorted(data: any, ids: Set<string>): Array<{ id: string; category: string; confidence: string }> {
  const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (typeof text !== "string") throw new Error("Gemini response had no text");
  const parsed = JSON.parse(text);
  const list = Array.isArray(parsed?.results) ? parsed.results : [];
  return list.filter((r: any) =>
    ids.has(r?.id) && CATEGORIES.includes(r?.category) && CONFIDENCE.includes(r?.confidence)
  ).map((r: any) => ({ id: r.id, category: r.category, confidence: r.confidence }));
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

async function sort(items: PaymentItem[]) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), GEMINI_TIMEOUT_MS);
  try {
    const response = await fetch(`${GEMINI_ENDPOINT}?key=${GEMINI_API_KEY}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      signal: controller.signal,
      body: JSON.stringify({
        systemInstruction: { role: "system", parts: [{ text: SYSTEM_PROMPT }] },
        contents: [{ role: "user", parts: [{ text: JSON.stringify(items) }] }],
        generationConfig: {
          temperature: 0,
          maxOutputTokens: 1024,
          thinkingConfig: { thinkingLevel: "minimal" },
          responseMimeType: "application/json",
          responseSchema: {
            type: "OBJECT",
            properties: {
              results: {
                type: "ARRAY",
                items: {
                  type: "OBJECT",
                  properties: {
                    id: { type: "STRING" },
                    category: { type: "STRING", enum: CATEGORIES },
                    confidence: { type: "STRING", enum: CONFIDENCE },
                  },
                  required: ["id", "category", "confidence"],
                },
              },
            },
            required: ["results"],
          },
        },
      }),
    });
    if (!response.ok) {
      console.error("Gemini API error", response.status, await response.text().catch(() => ""));
      throw new Error(`Gemini API returned ${response.status}`);
    }
    return parseSorted(await response.json(), new Set(items.map((i) => i.id)));
  } finally {
    clearTimeout(timeout);
  }
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return jsonError(405, "method_not_allowed", "Only POST is supported");
  try {
    await verifyGoogleIdToken(req.headers.get("Authorization"));
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
  const parsed = parseItems(raw);
  if (parsed.ok === false) return jsonError(400, "invalid_request", parsed.message);
  if (!GEMINI_API_KEY) return jsonError(500, "misconfigured", "Server is not configured correctly");
  try {
    const results = await sort(parsed.items);
    return new Response(JSON.stringify({ results }), { headers: { "Content-Type": "application/json" } });
  } catch (e) {
    console.error("Sorting failed", e);
    return jsonError(502, "upstream_error", "Couldn't sort payments just now.");
  }
});
