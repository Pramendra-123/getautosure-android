// Supabase Edge Function: writes the buyer's words for a used-car negotiation — a WhatsApp message to
// the seller and what to say at the car — from the Used Car Check's own findings and numbers.
//
// The numbers (offer, fair deal, walk-away, rupees per finding) are worked out in the app from the
// market price and the fixed repair-cost table. Gemini only writes the words around them and is told
// never to add a finding or change a figure, so the message can't promise more than the check showed.
//
// Secrets: GEMINI_API_KEY, GOOGLE_WEB_CLIENT_ID (same as ai-mechanic and price-estimate).

import { createRemoteJWKSet, jwtVerify } from "https://esm.sh/jose@5.9.6";

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY");
const GOOGLE_WEB_CLIENT_ID = Deno.env.get("GOOGLE_WEB_CLIENT_ID");
export const GEMINI_MODEL = "gemini-3.6-flash";
const GEMINI_ENDPOINT = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;
const GEMINI_TIMEOUT_MS = 30_000;
const GOOGLE_JWKS = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

export const LANGUAGES: Record<string, string> = {
  en: "English", hi: "Hindi (Devanagari script)", kn: "Kannada (Kannada script)", ta: "Tamil (Tamil script)",
  te: "Telugu (Telugu script)", mr: "Marathi (Devanagari script)", bn: "Bengali (Bengali script)", ml: "Malayalam (Malayalam script)",
};
export const TONES = ["gentle", "firm", "final"];

const SYSTEM_PROMPT = `You help a used-car buyer in India negotiate. You write their words; you are not the buyer.

Hard rules:
- Use ONLY the findings and rupee figures given. Never add a fault, a cost, a market claim or a figure that isn't in the input.
- Quote the offer exactly as given. Never offer more than the offer, and never mention the walk-away price in the message to the seller.
- Polite and respectful always — the seller is a person, often the owner. No threats, no insults, no pressure tricks, no lies (never invent other cars or other buyers).
- Write rupees the Indian way: ₹10,15,000 or ₹10.15 lakh; repair ranges like ₹12–16 thousand.
- The WhatsApp message: short (60–110 words), greets the seller by name if given, thanks them, lists at most 4 findings with their rupee ranges, states the offer, and says the full report is attached.
- Tone: gentle = warm, open to talk; firm = clear and confident; final = this is my best and final offer, polite.
- sayThis: 3 short lines to say at the car, in order: open with the easiest finding to see, then the next, then the offer.
- replies: 3 likely seller replies and a calm answer to each, grounded in the findings. Answers may mention the walk-away price as a private note to the buyer ("don't go above ₹X").
- tip: one sentence of advice to the buyer.
- Write message, sayThis and replies in the requested language and script; keep codes like P0171 and numbers as digits.`;

export interface CoachReason { title: string; minInr: number; maxInr: number }
export interface CoachRequest {
  language: string;
  tone: string;
  sellerName: string;
  car: string;
  askingInr: number | null;
  offerInr: number;
  fairDealInr: number;
  walkAwayInr: number;
  reasons: CoachReason[];
  notes: string[];
}

export function jsonError(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ error: { code, message } }), { status, headers: { "Content-Type": "application/json" } });
}

const clean = (v: unknown, max: number) => typeof v === "string" ? v.replace(/[\r\n]+/g, " ").trim().slice(0, max) : "";
const money = (v: unknown) => typeof v === "number" && Number.isFinite(v) && v >= 0 && v < 100_000_000 ? Math.round(v) : null;

export function parseRequest(raw: any): { ok: true; request: CoachRequest } | { ok: false; message: string } {
  if (!raw || typeof raw !== "object") return { ok: false, message: "Body must be a JSON object" };
  const language = typeof raw.language === "string" && raw.language in LANGUAGES ? raw.language : "en";
  const tone = TONES.includes(raw.tone) ? raw.tone : "firm";
  const offer = money(raw.offerInr), fair = money(raw.fairDealInr), walk = money(raw.walkAwayInr);
  if (offer == null || fair == null || walk == null) return { ok: false, message: "offerInr, fairDealInr and walkAwayInr are required" };
  const reasons = (Array.isArray(raw.reasons) ? raw.reasons : []).slice(0, 8)
    .map((r: any) => ({ title: clean(r?.title, 120), minInr: money(r?.minInr), maxInr: money(r?.maxInr) }))
    .filter((r: any) => r.title && r.minInr != null && r.maxInr != null) as CoachReason[];
  const notes = (Array.isArray(raw.notes) ? raw.notes : []).slice(0, 6).map((n: unknown) => clean(n, 200)).filter(Boolean);
  return {
    ok: true,
    request: {
      language, tone, sellerName: clean(raw.sellerName, 40), car: clean(raw.car, 80) || "the car",
      askingInr: money(raw.askingInr), offerInr: offer, fairDealInr: fair, walkAwayInr: walk, reasons, notes,
    },
  };
}

export function userTurn(r: CoachRequest): string {
  const inr = (v: number) => "₹" + v.toLocaleString("en-IN");
  return [
    `Language: ${LANGUAGES[r.language]}. Tone: ${r.tone}.`,
    `Car: ${r.car}.` + (r.sellerName ? ` Seller's name: ${r.sellerName}.` : ""),
    r.askingInr ? `Seller is asking ${inr(r.askingInr)}.` : "Asking price not given.",
    `Offer to make: ${inr(r.offerInr)}. Fair deal: ${inr(r.fairDealInr)}. Walk away above: ${inr(r.walkAwayInr)} (private, never tell the seller).`,
    "Findings, with repair ranges:",
    ...(r.reasons.length ? r.reasons.map((x) => `- ${x.title}: ${inr(x.minInr)}–${inr(x.maxInr)}`) : ["- none priced"]),
    ...(r.notes.length ? ["Other things the check found (no price):", ...r.notes.map((n) => `- ${n}`)] : []),
  ].join("\n");
}

const RESPONSE_SCHEMA = {
  type: "OBJECT",
  properties: {
    message: { type: "STRING" },
    sayThis: { type: "ARRAY", items: { type: "STRING" } },
    replies: { type: "ARRAY", items: { type: "OBJECT", properties: { seller: { type: "STRING" }, you: { type: "STRING" } }, required: ["seller", "you"] } },
    tip: { type: "STRING" },
  },
  required: ["message", "sayThis", "replies", "tip"],
};

export interface Coaching { message: string; sayThis: string[]; replies: { seller: string; you: string }[]; tip: string; model: string }

export function parseCoaching(data: any): Coaching {
  const text = (data?.candidates?.[0]?.content?.parts ?? []).map((p: any) => p?.text ?? "").join("");
  const start = text.indexOf("{"), end = text.lastIndexOf("}");
  if (start < 0 || end <= start) throw new Error("No JSON in Gemini reply");
  const j = JSON.parse(text.slice(start, end + 1));
  const message = typeof j.message === "string" ? j.message.trim() : "";
  if (message.length < 20) throw new Error("Message too short");
  return {
    message: message.slice(0, 1500),
    sayThis: (Array.isArray(j.sayThis) ? j.sayThis : []).filter((s: unknown) => typeof s === "string" && s.trim()).slice(0, 4).map((s: string) => s.trim().slice(0, 300)),
    replies: (Array.isArray(j.replies) ? j.replies : [])
      .filter((x: any) => typeof x?.seller === "string" && typeof x?.you === "string").slice(0, 4)
      .map((x: any) => ({ seller: x.seller.trim().slice(0, 200), you: x.you.trim().slice(0, 300) })),
    tip: typeof j.tip === "string" ? j.tip.trim().slice(0, 300) : "",
    model: GEMINI_MODEL,
  };
}

async function coach(r: CoachRequest): Promise<Coaching> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), GEMINI_TIMEOUT_MS);
  try {
    const response = await fetch(`${GEMINI_ENDPOINT}?key=${GEMINI_API_KEY}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      signal: controller.signal,
      body: JSON.stringify({
        systemInstruction: { role: "system", parts: [{ text: SYSTEM_PROMPT }] },
        contents: [{ role: "user", parts: [{ text: userTurn(r) }] }],
        generationConfig: {
          temperature: 0.4,
          maxOutputTokens: 2048,
          thinkingConfig: { thinkingLevel: "low" },
          responseMimeType: "application/json",
          responseSchema: RESPONSE_SCHEMA,
        },
      }),
    });
    if (!response.ok) {
      console.error("Gemini API error", response.status, await response.text().catch(() => ""));
      throw new Error(`Gemini API returned ${response.status}`);
    }
    return parseCoaching(await response.json());
  } finally {
    clearTimeout(timeout);
  }
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
  const parsed = parseRequest(raw);
  if (parsed.ok === false) return jsonError(400, "invalid_request", parsed.message);
  if (!GEMINI_API_KEY) return jsonError(500, "misconfigured", "Server is not configured correctly");
  try {
    return new Response(JSON.stringify(await coach(parsed.request)), { headers: { "Content-Type": "application/json" } });
  } catch (e) {
    console.error("Coaching failed", e);
    return jsonError(502, "upstream_error", "Couldn't write the message just now.");
  }
});
