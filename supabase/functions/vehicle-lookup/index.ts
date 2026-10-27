// RC (registration certificate) lookup via Surepass RC V2 — one function for both callers:
//   mode "customer": regular-customer onboarding prefill (make/model/year/fuel) + validity dates
//                    for reminders. Never returns owner identity or financier details — anyone can
//                    type any plate, so this mode must be safe to show about a stranger's car.
//   mode "dealer":   Dealer Sathi. Adds owner name/count, loan status, RC status/blacklist and
//                    chassis/engine numbers. Only for dealers whose dealers.rc_full_access flag was
//                    set by hand in the Dashboard (see the migration for why).
// Father's name, addresses and mobile number are never returned in either mode — nothing in the
// app needs them, so they stay in the server-side cache only.
//
// STATUS: written and unit-tested (index.test.ts, run with `deno test`), NOT yet deployed or run
// against a real Supabase project. The Surepass field names below come from a real sandbox
// RC V2 response (2026-10-27), not from docs — Surepass doesn't publish its response schema.
//
// Required secrets (Edge Functions > Secrets):
//   GOOGLE_WEB_CLIENT_ID — same value ai-mechanic/consultations/reports already use.
//   SUREPASS_API_TOKEN   — Surepass bearer token. Sandbox and production tokens differ.
//   SUREPASS_BASE_URL    — e.g. https://sandbox.surepass.app for the sandbox. No default on
//                          purpose: production must be set explicitly, never fallen back into.
// Required Supabase resources: ../../migrations/20261027000000_vehicle_lookups.sql (which needs
// 20261025000000_vehicle_reports.sql applied first, for the dealers table).

import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";
import { createRemoteJWKSet, jwtVerify } from "https://esm.sh/jose@5.9.6";

const GOOGLE_WEB_CLIENT_ID = Deno.env.get("GOOGLE_WEB_CLIENT_ID");
const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const SUREPASS_API_TOKEN = Deno.env.get("SUREPASS_API_TOKEN");
const SUREPASS_BASE_URL = Deno.env.get("SUREPASS_BASE_URL");

export const SUREPASS_RC_ENDPOINT = "/api/v1/rc/rc-v2";
export const SOURCE_LABEL = "RTO (VAHAN) records via Surepass RC V2";

// Paid per call — the cache is what keeps a customer reopening onboarding from costing twice.
// Keep in sync with the cleanup comment in the migration.
export const CACHE_TTL_DAYS = 30;
const SUREPASS_TIMEOUT_MS = 20_000;

// Cache hits count too (see the migration). A customer adds one or two cars, ever; a dealer
// checks a handful per day. Raise deliberately, not by default.
export const RATE_LIMITS = {
  customer: { perMinute: 3, perDay: 10 },
  dealer: { perMinute: 10, perDay: 100 },
} as const;

const GOOGLE_JWKS = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

export type LookupMode = "customer" | "dealer";

export function jsonError(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ error: { code, message } }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Same pattern as ai-mechanic/consultations/reports' verifyGoogleIdToken — see ai-mechanic for why each check exists. */
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
    if (typeof payload.sub !== "string") throw new Error("token has no subject");
    return payload.sub;
  } catch (e) {
    console.error("ID token verification failed", e);
    throw jsonError(401, "unauthorized", "Invalid or expired sign-in token");
  }
}

/** "mh-12 ab 1234" -> "MH12AB1234". Spaces, hyphens and dots are how people actually type plates. */
export function normalizeRegistrationNumber(raw: string): string {
  return raw.replace(/[\s.\-]/g, "").toUpperCase();
}

// Standard series: state (2 letters) + RTO (1–2 digits) + series (0–3 letters) + number (1–4
// digits), e.g. MH12AB1234, DL3CAB1234, KA011234. BH series: 22BH1234AA. Rejecting anything else
// before calling Surepass means a typo costs nothing instead of a paid failed lookup.
const STANDARD_PLATE = /^[A-Z]{2}\d{1,2}[A-Z]{0,3}\d{1,4}$/;
const BH_PLATE = /^\d{2}BH\d{4}[A-Z]{1,2}$/;

export function isValidIndianRegistration(normalized: string): boolean {
  return STANDARD_PLATE.test(normalized) || BH_PLATE.test(normalized);
}

export function parseLookupRequest(
  rawBody: unknown,
): { ok: true; registrationNumber: string; mode: LookupMode } | { ok: false; code: string; message: string } {
  if (typeof rawBody !== "object" || rawBody === null) {
    return { ok: false, code: "invalid_request", message: "Request body must be a JSON object" };
  }
  const body = rawBody as Record<string, unknown>;

  const mode = body.mode === undefined ? "customer" : body.mode;
  if (mode !== "customer" && mode !== "dealer") {
    return { ok: false, code: "invalid_request", message: 'mode must be "customer" or "dealer"' };
  }

  const raw = typeof body.registrationNumber === "string" ? body.registrationNumber.slice(0, 20) : "";
  const registrationNumber = normalizeRegistrationNumber(raw);
  if (!registrationNumber) {
    return { ok: false, code: "invalid_request", message: "registrationNumber is required" };
  }
  if (!isValidIndianRegistration(registrationNumber)) {
    return {
      ok: false,
      code: "invalid_registration",
      message: "That doesn't look like an Indian registration number (e.g. MH12AB1234)",
    };
  }
  return { ok: true, registrationNumber, mode };
}

/** Surepass sends "" and "Not Available" for missing values — both become null so the app shows "not available" instead of a blank or a fake-looking string. */
function text(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (!trimmed || /^not available$/i.test(trimmed)) return null;
  return trimmed;
}

function int(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return Math.trunc(value);
  const t = text(value);
  if (t === null || !/^\d+$/.test(t)) return null;
  return Number(t);
}

function bool(value: unknown): boolean | null {
  if (typeof value === "boolean") return value;
  const t = text(value)?.toLowerCase();
  if (t === "true" || t === "yes") return true;
  if (t === "false" || t === "no") return false;
  return null;
}

/** A Surepass body is usable only if it explicitly says success and carries a data object — anything else (including HTTP 200 with success:false) is a failed lookup, not an empty car. */
// deno-lint-ignore no-explicit-any
export function isUsableSurepassResponse(json: any): boolean {
  return json?.success === true && typeof json?.data === "object" && json.data !== null;
}

/**
 * Maps Surepass RC V2 `data` to what each mode is allowed to see. Built as an allow-list: a field
 * Surepass adds tomorrow is dropped until someone decides which mode should get it.
 */
// deno-lint-ignore no-explicit-any
export function shapeRcData(data: any, mode: LookupMode) {
  const customer = {
    registrationNumber: text(data?.rc_number),
    vehicle: {
      make: text(data?.maker_description),
      model: text(data?.maker_model),
      fuelType: text(data?.fuel_type),
      color: text(data?.color),
      bodyType: text(data?.body_type),
      category: text(data?.vehicle_category_description),
      // "1998-11" — month precision is all the RTO record has; the app derives the year from it.
      manufacturingMonth: text(data?.manufacturing_date_formatted),
      registrationDate: text(data?.registration_date),
      registeredAt: text(data?.registered_at),
    },
    validity: {
      insuranceCompany: text(data?.insurance_company),
      insuranceUpto: text(data?.insurance_upto),
      puccUpto: text(data?.pucc_upto),
      fitnessUpto: text(data?.fit_up_to),
      taxUpto: text(data?.tax_upto),
    },
  };
  if (mode === "customer") return customer;

  return {
    ...customer,
    ownership: {
      ownerName: text(data?.owner_name),
      ownerCount: int(data?.owner_number),
      financed: bool(data?.financed),
      financer: text(data?.financer),
    },
    compliance: {
      rcStatus: text(data?.rc_status),
      blacklistStatus: text(data?.blacklist_status),
      nocDetails: text(data?.noc_details),
      nonUseStatus: text(data?.non_use_status),
    },
    identifiers: {
      chassisNumber: text(data?.vehicle_chasi_number),
      engineNumber: text(data?.vehicle_engine_number),
    },
    // Everything else non-personal Surepass returns — dealers asked for whatever's available.
    technical: {
      categoryCode: text(data?.vehicle_category),
      emissionNorms: text(data?.norms_type),
      cubicCapacity: text(data?.cubic_capacity),
      cylinders: int(data?.no_cylinders),
      seatCapacity: int(data?.seat_capacity),
      sleeperCapacity: int(data?.sleeper_capacity),
      standingCapacity: int(data?.standing_capacity),
      // Surepass sends 0 for "not recorded" here (seen in sandbox), so 0 is treated as unknown.
      grossWeightKg: int(data?.vehicle_gross_weight) || null,
      unladenWeightKg: int(data?.unladen_weight) || null,
      wheelbase: text(data?.wheelbase),
    },
    registration: {
      rtoCode: text(data?.rto_code),
      taxPaidUpto: text(data?.tax_paid_upto),
      puccNumber: text(data?.pucc_number),
      nonUseFrom: text(data?.non_use_from),
      nonUseTo: text(data?.non_use_to),
      // When the RTO record itself was last refreshed on Surepass's side, not when we fetched it.
      recordUpdatedOn: text(data?.latest_by),
    },
    permit: {
      number: text(data?.permit_number),
      type: text(data?.permit_type),
      issueDate: text(data?.permit_issue_date),
      validFrom: text(data?.permit_valid_from),
      validUpto: text(data?.permit_valid_upto),
      nationalPermitNumber: text(data?.national_permit_number),
      nationalPermitUpto: text(data?.national_permit_upto),
      nationalPermitIssuedBy: text(data?.national_permit_issued_by),
    },
  };
}

async function hasDealerRcAccess(supabase: SupabaseClient, userId: string): Promise<boolean> {
  const { data, error } = await supabase
    .from("dealers")
    .select("rc_full_access")
    .eq("dealer_sub", userId)
    .maybeSingle();
  if (error) {
    console.error("dealer access check failed", error);
    return false; // Fail closed — dealer mode reveals owner identity.
  }
  return (data as { rc_full_access?: boolean } | null)?.rc_full_access === true;
}

async function checkAndRecordLookup(
  supabase: SupabaseClient,
  userId: string,
  registrationNumber: string,
  mode: LookupMode,
): Promise<{ allowed: boolean; reason?: string }> {
  const { data, error } = await supabase.rpc("check_and_record_vehicle_lookup", {
    p_user_id: userId,
    p_rc_number: registrationNumber,
    p_mode: mode,
    p_minute_limit: RATE_LIMITS[mode].perMinute,
    p_day_limit: RATE_LIMITS[mode].perDay,
  });
  if (error) {
    console.error("Rate limit check failed", error);
    // Fail closed: don't make a paid call we can't account for.
    return { allowed: false, reason: "Could not verify lookup limits — please try again shortly." };
  }
  return data === true
    ? { allowed: true }
    : { allowed: false, reason: "Too many vehicle lookups — please try again later." };
}

async function readCache(
  supabase: SupabaseClient,
  registrationNumber: string,
): Promise<{ data: unknown; fetchedAt: string } | null> {
  const cutoff = new Date(Date.now() - CACHE_TTL_DAYS * 24 * 60 * 60 * 1000).toISOString();
  const { data, error } = await supabase
    .from("vehicle_rc_cache")
    .select("response, fetched_at")
    .eq("rc_number", registrationNumber)
    .gte("fetched_at", cutoff)
    .maybeSingle();
  if (error) {
    console.error("cache read failed", error);
    return null; // A cache outage just means a paid lookup, not a failed one.
  }
  if (!data) return null;
  const row = data as { response: { data?: unknown }; fetched_at: string };
  return { data: row.response?.data, fetchedAt: row.fetched_at };
}

async function writeCache(supabase: SupabaseClient, registrationNumber: string, response: unknown, fetchedAt: string) {
  const { error } = await supabase
    .from("vehicle_rc_cache")
    .upsert({ rc_number: registrationNumber, response, fetched_at: fetchedAt }, { onConflict: "rc_number" });
  if (error) console.error("cache write failed", error); // Non-fatal — the caller still gets their result.
}

class SurepassError extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message);
  }
}

async function callSurepass(registrationNumber: string): Promise<unknown> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), SUREPASS_TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(`${SUREPASS_BASE_URL!.replace(/\/$/, "")}${SUREPASS_RC_ENDPOINT}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${SUREPASS_API_TOKEN}` },
      body: JSON.stringify({ id_number: registrationNumber }),
      signal: controller.signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") {
      throw new SurepassError(504, "upstream_timeout", "The RTO lookup took too long. Please try again.");
    }
    throw e;
  } finally {
    clearTimeout(timeout);
  }

  // Surepass error bodies were empty in sandbox testing, so the status code is all there is to go on.
  if (response.status === 401 || response.status === 403) {
    console.error("Surepass rejected our token", response.status);
    throw new SurepassError(502, "upstream_auth", "Vehicle lookup is temporarily unavailable.");
  }
  if (response.status === 422) {
    throw new SurepassError(422, "lookup_failed", "No RTO record found for that registration number.");
  }
  if (!response.ok) {
    console.error("Surepass error", response.status, (await response.text().catch(() => "")).slice(0, 500));
    throw new SurepassError(502, "upstream_error", "Vehicle lookup failed. Please try again.");
  }

  const json = await response.json();
  if (!isUsableSurepassResponse(json)) {
    console.error("Surepass returned no usable data", json?.status_code, json?.message_code);
    throw new SurepassError(422, "lookup_failed", "No RTO record found for that registration number.");
  }
  return json;
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

  const parsed = parseLookupRequest(rawBody);
  // `=== false`, not `!parsed.ok` — see the matching comment in ai-mechanic/index.ts.
  if (parsed.ok === false) return jsonError(400, parsed.code, parsed.message);
  const { registrationNumber, mode } = parsed;

  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY || !SUREPASS_API_TOKEN || !SUREPASS_BASE_URL) {
    console.error("Missing SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, SUREPASS_API_TOKEN or SUREPASS_BASE_URL");
    return jsonError(500, "misconfigured", "Server is not configured correctly");
  }
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  if (mode === "dealer" && !(await hasDealerRcAccess(supabase, userId))) {
    return jsonError(403, "forbidden", "Full RC details are only available to verified Dealer Sathi accounts");
  }

  const limit = await checkAndRecordLookup(supabase, userId, registrationNumber, mode);
  if (!limit.allowed) return jsonError(429, "rate_limited", limit.reason ?? "Too many requests");

  let rcData: unknown;
  let fetchedAt: string;
  let cached = false;
  const hit = await readCache(supabase, registrationNumber);
  if (hit?.data) {
    rcData = hit.data;
    fetchedAt = hit.fetchedAt;
    cached = true;
  } else {
    try {
      const json = await callSurepass(registrationNumber) as { data: unknown };
      fetchedAt = new Date().toISOString();
      await writeCache(supabase, registrationNumber, json, fetchedAt);
      rcData = json.data;
    } catch (e) {
      if (e instanceof SurepassError) return jsonError(e.status, e.code, e.message);
      console.error("Surepass call errored", e);
      return jsonError(502, "upstream_error", "Vehicle lookup failed. Please try again.");
    }
  }

  return new Response(
    JSON.stringify({
      mode,
      source: SOURCE_LABEL,
      fetchedAt,
      cached,
      // Surepass flags records where the RTO itself returned fewer fields — the app should say
      // "some details aren't available from the RTO" rather than imply the car has none.
      partial: (rcData as { less_info?: unknown })?.less_info === true,
      vehicle: shapeRcData(rcData, mode),
    }),
    { headers: { "Content-Type": "application/json" } },
  );
});
