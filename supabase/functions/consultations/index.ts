// Backs the "Ask a Real Expert" paid consultation feature — Phase 3 (data access + secure
// slot reservation; see PROJECT_NOTES.md for the full phase breakdown). Written but never
// deployed/tested against a real Supabase project yet — same status ai-mechanic/index.ts had
// before its first real deploy.
//
// One function, action-dispatched (`{"action": "...", ...}` in the POST body) rather than one
// Edge Function per operation — this project's Supabase CLI has never worked on the dev
// machine it was built on (see README.md / PROJECT_NOTES.md), so every deploy is a manual
// paste into the Dashboard's Code editor. Fewer functions means fewer manual deploys as this
// feature grows through Phase 4 (payment) and Phase 5 (video), not a stylistic preference.
//
// Required secrets (Edge Functions > Secrets):
//   GOOGLE_WEB_CLIENT_ID  — same value as ai-mechanic's, and as Android's GoogleSignIn.kt.
// Required Supabase resources: see ../../migrations/20261024000000_consultations.sql.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";
import { createRemoteJWKSet, jwtVerify } from "https://esm.sh/jose@5.9.6";

const GOOGLE_WEB_CLIENT_ID = Deno.env.get("GOOGLE_WEB_CLIENT_ID");
const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

const GOOGLE_JWKS = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

// Mirrors ConsultationPricing on the Android side (app/src/main/java/com/enginesense/app/
// consultation/ConsultationModels.kt) — kept here too, not fetched from the client, per the
// explicit "price configurable from the backend, not scattered in the UI" requirement. If this
// ever changes, Android's copy must be updated to match (Phase 4 could instead have the client
// fetch it from a dedicated action, but two hardcoded-and-matching values is enough for now).
export const PRICE_INR = 499;
export const RESCHEDULE_CUTOFF_MINUTES = 120;
const RESERVATION_MINUTES = 10;

export function jsonError(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ error: { code, message } }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Same pattern as ai-mechanic/index.ts's verifyGoogleIdToken — see that file for why each check exists. */
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

/** Display-only — not a security token (the real guarantee is the slot's unique constraint + the RPC's advisory lock), just a human-readable reference for the customer and this project's own support process. */
export function generateBookingReference(): string {
  const n = Math.floor(100000 + Math.random() * 900000);
  return `GAS-${n}`;
}

/** Pure so it's testable without a live clock dependency injected everywhere — [nowMs] defaults to the real clock, tests can pass a fixed value. */
export function isPastCancelCutoff(slotStartIso: string, cutoffMinutes: number, nowMs: number = Date.now()): boolean {
  const cutoffMs = new Date(slotStartIso).getTime() - cutoffMinutes * 60_000;
  return nowMs > cutoffMs;
}

/** A 'reserved' slot whose hold has lapsed is treated as open — lazy expiry, no cron job needed; same logic the reserve_consultation_slot DB function uses internally. */
function isEffectivelyOpen(status: string, reservationExpiresAt: string | null): boolean {
  if (status === "open") return true;
  if (status === "reserved" && reservationExpiresAt) return new Date(reservationExpiresAt).getTime() < Date.now();
  return false;
}

type SupabaseClient = ReturnType<typeof createClient>;

async function listExperts(supabase: SupabaseClient): Promise<Response> {
  const { data, error } = await supabase
    .from("experts")
    .select("id, name, title, bio, years_experience, specializations, photo_url")
    .eq("active", true)
    .order("created_at");
  if (error) {
    console.error("list_experts query failed", error);
    return jsonError(500, "query_failed", "Could not load experts");
  }
  const experts = (data ?? []).map((e: any) => ({
    id: e.id,
    name: e.name,
    title: e.title,
    bio: e.bio,
    yearsExperience: e.years_experience,
    specializations: e.specializations ?? [],
    photoUrl: e.photo_url,
  }));
  return new Response(JSON.stringify({ experts }), { headers: { "Content-Type": "application/json" } });
}

async function listSlots(supabase: SupabaseClient, expertId: unknown): Promise<Response> {
  if (typeof expertId !== "string" || !expertId) return jsonError(400, "invalid_request", "expertId is required");
  const { data, error } = await supabase
    .from("expert_availability_slots")
    .select("id, expert_id, start_time, end_time, status, reservation_expires_at")
    .eq("expert_id", expertId)
    .gt("start_time", new Date().toISOString())
    .order("start_time");
  if (error) {
    console.error("list_slots query failed", error);
    return jsonError(500, "query_failed", "Could not load availability");
  }
  const slots = (data ?? [])
    .filter((s: any) => isEffectivelyOpen(s.status, s.reservation_expires_at))
    .map((s: any) => ({
      id: s.id,
      expertId: s.expert_id,
      startEpochMillis: new Date(s.start_time).getTime(),
      endEpochMillis: new Date(s.end_time).getTime(),
      status: "OPEN",
    }));
  return new Response(JSON.stringify({ slots }), { headers: { "Content-Type": "application/json" } });
}

function toBookingJson(row: any) {
  return {
    id: row.id,
    bookingReference: row.booking_reference,
    expertId: row.expert_id,
    expertName: row.experts?.name ?? "",
    slotStartEpochMillis: row.expert_availability_slots ? new Date(row.expert_availability_slots.start_time).getTime() : null,
    slotEndEpochMillis: row.expert_availability_slots ? new Date(row.expert_availability_slots.end_time).getTime() : null,
    status: row.status.toUpperCase(),
    amountInr: row.amount_inr,
  };
}

async function reserveSlot(supabase: SupabaseClient, customerSub: string, slotId: unknown, vehicleContext: unknown): Promise<Response> {
  if (typeof slotId !== "string" || !slotId) return jsonError(400, "invalid_request", "slotId is required");
  const safeVehicleContext = vehicleContext && typeof vehicleContext === "object" ? vehicleContext : null;

  const { data: consultationId, error } = await supabase.rpc("reserve_consultation_slot", {
    p_slot_id: slotId,
    p_customer_sub: customerSub,
    p_booking_reference: generateBookingReference(),
    p_amount_inr: PRICE_INR,
    p_vehicle_context: safeVehicleContext,
    p_reservation_minutes: RESERVATION_MINUTES,
  });
  if (error) {
    console.error("reserve_consultation_slot failed", error);
    return jsonError(500, "reserve_failed", "Could not reserve this slot");
  }
  if (!consultationId) return jsonError(409, "slot_unavailable", "That slot is no longer available");

  const { data: booking, error: fetchErr } = await supabase
    .from("consultations")
    .select("id, booking_reference, expert_id, status, amount_inr, experts(name), expert_availability_slots(start_time, end_time)")
    .eq("id", consultationId)
    .single();
  if (fetchErr || !booking) {
    console.error("fetching reserved consultation failed", fetchErr);
    return jsonError(500, "reserve_failed", "Reserved, but couldn't load the booking details");
  }
  return new Response(JSON.stringify({ booking: toBookingJson(booking) }), { headers: { "Content-Type": "application/json" } });
}

async function myBookings(supabase: SupabaseClient, customerSub: string): Promise<Response> {
  const { data, error } = await supabase
    .from("consultations")
    .select("id, booking_reference, expert_id, status, amount_inr, experts(name), expert_availability_slots(start_time, end_time)")
    .eq("customer_sub", customerSub)
    .order("created_at", { ascending: false });
  if (error) {
    console.error("my_bookings query failed", error);
    return jsonError(500, "query_failed", "Could not load your consultations");
  }
  return new Response(JSON.stringify({ bookings: (data ?? []).map(toBookingJson) }), { headers: { "Content-Type": "application/json" } });
}

async function cancelBooking(supabase: SupabaseClient, customerSub: string, consultationId: unknown): Promise<Response> {
  if (typeof consultationId !== "string" || !consultationId) return jsonError(400, "invalid_request", "consultationId is required");

  const { data: existing, error: fetchErr } = await supabase
    .from("consultations")
    .select("id, customer_sub, status, expert_availability_slots(start_time)")
    .eq("id", consultationId)
    .maybeSingle();
  if (fetchErr || !existing) return jsonError(404, "not_found", "Booking not found");
  if (existing.customer_sub !== customerSub) return jsonError(403, "forbidden", "This booking isn't yours");

  const slotStart = (existing as any).expert_availability_slots?.start_time;
  // Enforced server-side, not just in the Android UI — a client can't bypass the cutoff by
  // skipping its own check.
  if (slotStart && isPastCancelCutoff(slotStart, RESCHEDULE_CUTOFF_MINUTES)) {
    return jsonError(
      422,
      "past_cutoff",
      `Cancellations must be made at least ${RESCHEDULE_CUTOFF_MINUTES / 60} hours before the consultation.`,
    );
  }

  const { data: cancelled, error } = await supabase.rpc("cancel_consultation_booking", {
    p_consultation_id: consultationId,
    p_customer_sub: customerSub,
  });
  if (error) {
    console.error("cancel_consultation_booking failed", error);
    return jsonError(500, "cancel_failed", "Could not cancel this booking");
  }
  if (!cancelled) return jsonError(409, "cannot_cancel", "This booking can no longer be cancelled");
  return new Response(JSON.stringify({ cancelled: true }), { headers: { "Content-Type": "application/json" } });
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return jsonError(405, "method_not_allowed", "Only POST is supported");

  let rawBody: unknown;
  try {
    rawBody = await req.json();
  } catch {
    return jsonError(400, "invalid_request", "Malformed JSON body");
  }
  const body = (typeof rawBody === "object" && rawBody !== null ? rawBody : {}) as Record<string, unknown>;
  const action = typeof body.action === "string" ? body.action : null;
  if (!action) return jsonError(400, "invalid_request", "action is required");

  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    console.error("Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY secret");
    return jsonError(500, "misconfigured", "Server is not configured correctly");
  }
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  // Public actions — just browsing, no identity needed yet.
  if (action === "list_experts") return await listExperts(supabase);
  if (action === "list_slots") return await listSlots(supabase, body.expertId);

  // Everything else touches a specific customer's own booking.
  let customerSub: string;
  try {
    customerSub = await verifyGoogleIdToken(req.headers.get("Authorization"));
  } catch (e) {
    if (e instanceof Response) return e;
    return jsonError(401, "unauthorized", "Authentication failed");
  }

  switch (action) {
    case "reserve_slot":
      return await reserveSlot(supabase, customerSub, body.slotId, body.vehicleContext ?? null);
    case "my_bookings":
      return await myBookings(supabase, customerSub);
    case "cancel_booking":
      return await cancelBooking(supabase, customerSub, body.consultationId);
    default:
      return jsonError(400, "invalid_request", `Unknown action: ${action}`);
  }
});
