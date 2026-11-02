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
//   HMS_*                 — 100ms keys for the video call; see video.ts.
// Required Supabase resources: see ../../migrations/20261024000000_consultations.sql and
// 20261102000000_consultation_video.sql.
//
// Besides the POST actions there is one GET route: ?expert=<expert_join_token> is the mechanic's
// join link. During the join window it redirects their browser into the booking's 100ms room.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";
import { createRemoteJWKSet, jwtVerify } from "https://esm.sh/jose@5.9.6";
import {
  appToken,
  createMechanicRoomCode,
  createRoom,
  CUSTOMER_EARLY_MINUTES,
  CUSTOMER_ROLE,
  type HmsConfig,
  hmsConfigFromEnv,
  joinWindow,
  MECHANIC_EARLY_MINUTES,
  MECHANIC_ROLE,
  mechanicMeetingUrl,
  OVERRUN_MINUTES,
} from "./video.ts";
import { checkLeave, checkWeeklyHours, leaveDates, summariseCar } from "./mechanic.ts";

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
/** Slots starting sooner than this aren't offered; must match reserve_consultation_slot's own check. */
const MIN_BOOKING_NOTICE_MINUTES = 120;

export function jsonError(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ error: { code, message } }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Same pattern as ai-mechanic/index.ts's verifyGoogleIdToken — see that file for why each check exists. */
export async function verifyGoogleIdToken(authHeader: string | null): Promise<string> {
  return (await verifyGoogleIdentity(authHeader)).sub;
}

/** The signed-in Google account: its stable subject, and its email only when Google has verified it (Mechanic mode matches on that). */
export async function verifyGoogleIdentity(authHeader: string | null): Promise<{ sub: string; email: string | null }> {
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
    const email = payload.email_verified === true && typeof payload.email === "string" ? payload.email.toLowerCase() : null;
    return { sub: payload.sub, email };
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

// What createClient(url, key) returns with no generated schema types.
type SupabaseClient = ReturnType<typeof createClient<any, "public", any>>;

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
  // Tops up the mechanic's slots from their weekly hours every time someone looks, so the next
  // week is always there without a scheduled job. Safe to repeat; a failure just shows what exists.
  const { error: generateError } = await supabase.rpc("generate_expert_slots", { p_expert_id: expertId });
  if (generateError) console.error("generate_expert_slots failed", generateError);

  const { data, error } = await supabase
    .from("expert_availability_slots")
    .select("id, expert_id, start_time, end_time, status, reservation_expires_at")
    .eq("expert_id", expertId)
    // Same 2-hour notice reserve_consultation_slot enforces.
    .gt("start_time", new Date(Date.now() + MIN_BOOKING_NOTICE_MINUTES * 60_000).toISOString())
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

/** The booking row plus its slot times — everything joining needs. */
const JOIN_SELECT =
  "id, booking_reference, customer_sub, status, hms_room_id, hms_host_room_code, expert_availability_slots(start_time, end_time)";

/** Returns the booking's 100ms room id, creating the room on first use. */
async function ensureRoom(supabase: SupabaseClient, hms: HmsConfig, row: any): Promise<string> {
  if (row.hms_room_id) return row.hms_room_id;
  const slot = row.expert_availability_slots;
  // Same name gives the same room, so two first joins racing each other still end up together.
  const roomId = await createRoom(hms, row.booking_reference, slot.start_time, slot.end_time);
  const { error } = await supabase.from("consultations").update({ hms_room_id: roomId }).eq("id", row.id).is("hms_room_id", null);
  if (error) console.error("saving hms_room_id failed", error);
  return roomId;
}

const JOIN_WINDOW_MESSAGES = {
  not_yet: (early: number) => `You can join from ${early} minutes before the consultation starts.`,
  ended: () => "This consultation's time has passed.",
};

async function joinVideo(supabase: SupabaseClient, customerSub: string, consultationId: unknown): Promise<Response> {
  if (typeof consultationId !== "string" || !consultationId) return jsonError(400, "invalid_request", "consultationId is required");
  const hms = hmsConfigFromEnv();
  if (!hms) {
    console.error("Missing HMS_ACCESS_KEY / HMS_APP_SECRET / HMS_TEMPLATE_ID secret");
    return jsonError(503, "video_not_configured", "Video calls aren't available yet");
  }

  const { data: row, error } = await supabase.from("consultations").select(JOIN_SELECT).eq("id", consultationId).maybeSingle();
  if (error || !row) return jsonError(404, "not_found", "Booking not found");
  if (row.customer_sub !== customerSub) return jsonError(403, "forbidden", "This booking isn't yours");
  if (row.status !== "confirmed") return jsonError(409, "not_confirmed", "Only confirmed consultations can be joined");

  const slot = (row as any).expert_availability_slots;
  const window = joinWindow(slot.start_time, slot.end_time, CUSTOMER_EARLY_MINUTES);
  if (window !== "open") return jsonError(422, `join_${window}`, JOIN_WINDOW_MESSAGES[window](CUSTOMER_EARLY_MINUTES));

  try {
    const roomId = await ensureRoom(supabase, hms, row);
    const expiresAt = Math.floor(new Date(slot.end_time).getTime() / 1000) + OVERRUN_MINUTES * 60;
    // Not the Google subject: 100ms only needs a stable id per booking, not who the customer is.
    const authToken = await appToken(hms, roomId, `customer-${row.booking_reference}`, CUSTOMER_ROLE, expiresAt);
    return new Response(JSON.stringify({ authToken }), { headers: { "Content-Type": "application/json" } });
  } catch (e) {
    console.error("join_video failed", e);
    return jsonError(502, "video_unavailable", "Couldn't start the video call. Please try again.");
  }
}

/**
 * What the mechanic's browser shows when it can't go straight into the call. Plain text, not
 * HTML: Supabase serves function responses on its own domain as text/plain, so markup would
 * show up as raw tags.
 */
function mechanicPage(status: number, title: string, message: string): Response {
  return new Response(`${title}\n\n${message}\n`, { status, headers: { "Content-Type": "text/plain; charset=utf-8" } });
}

async function mechanicJoin(supabase: SupabaseClient, token: string): Promise<Response> {
  const hms = hmsConfigFromEnv();
  if (!hms) return mechanicPage(503, "Video not set up", "Video calls aren't configured yet. Please contact GetAutoSure.");

  const { data: row, error } = await supabase.from("consultations").select(JOIN_SELECT).eq("expert_join_token", token).maybeSingle();
  if (error || !row) return mechanicPage(404, "Link not valid", "This consultation link isn't valid. Please check it with GetAutoSure.");
  if (row.status !== "confirmed") {
    return mechanicPage(409, "Consultation not active", `Consultation ${row.booking_reference} isn't confirmed (it may have been cancelled).`);
  }

  const slot = (row as any).expert_availability_slots;
  const window = joinWindow(slot.start_time, slot.end_time, MECHANIC_EARLY_MINUTES);
  if (window !== "open") {
    const start = new Date(slot.start_time).toLocaleString("en-IN", { timeZone: "Asia/Kolkata", dateStyle: "medium", timeStyle: "short" });
    return mechanicPage(
      422,
      window === "not_yet" ? "Too early to join" : "Consultation over",
      window === "not_yet"
        ? `Consultation ${row.booking_reference} starts ${start}. This link opens ${MECHANIC_EARLY_MINUTES} minutes before.`
        : `Consultation ${row.booking_reference} (${start}) has ended.`,
    );
  }

  try {
    const roomId = await ensureRoom(supabase, hms, row);
    let code = row.hms_host_room_code as string | null;
    if (!code) {
      code = await createMechanicRoomCode(hms, roomId);
      await supabase.from("consultations").update({ hms_host_room_code: code }).eq("id", row.id as string);
    }
    return new Response(null, { status: 302, headers: { Location: mechanicMeetingUrl(hms, code) } });
  } catch (e) {
    console.error("mechanic join failed", e);
    return mechanicPage(502, "Couldn't start the call", "Something went wrong starting the video call. Please refresh in a moment.");
  }
}

// ---- Mechanic mode ----------------------------------------------------------------------------
// Every expert_* action works on the mechanic whose experts.google_email matches the verified
// email of the Google account signed in to the app. Nothing here takes an expert id from the
// client, so a mechanic can only ever see or change their own schedule and calls.

const json = (data: unknown) => new Response(JSON.stringify(data), { headers: { "Content-Type": "application/json" } });

async function handleMechanicAction(
  supabase: SupabaseClient,
  action: string,
  email: string | null,
  body: Record<string, unknown>,
): Promise<Response> {
  if (!email) return jsonError(403, "not_a_mechanic", "Your Google account's email isn't verified, so it can't be matched to a mechanic profile.");
  const { data: expert, error } = await supabase
    .from("experts")
    .select("id, name, title, bio, years_experience, specializations, photo_url, active")
    .eq("google_email", email)
    .maybeSingle();
  if (error) {
    console.error("expert lookup failed", error);
    return jsonError(500, "query_failed", "Couldn't load your mechanic profile");
  }
  if (!expert) {
    return jsonError(403, "not_a_mechanic", `${email} isn't linked to a GetAutoSure mechanic profile yet. Ask GetAutoSure to add it.`);
  }
  const expertId = expert.id as string;

  switch (action) {
    case "expert_me":
      return json({
        expert: {
          id: expert.id,
          name: expert.name,
          title: expert.title,
          bio: expert.bio,
          yearsExperience: expert.years_experience,
          specializations: expert.specializations ?? [],
          photoUrl: expert.photo_url,
        },
        email,
      });
    case "expert_get_schedule":
      return await mechanicSchedule(supabase, expertId);
    case "expert_set_weekly_hours": {
      const checked = checkWeeklyHours(body.hours);
      if (!checked.ok) return jsonError(400, "invalid_hours", checked.message);
      const { error: saveError } = await supabase.rpc("set_expert_weekly_hours", { p_expert_id: expertId, p_hours: checked.value });
      if (saveError) {
        console.error("set_expert_weekly_hours failed", saveError);
        return jsonError(500, "save_failed", "Couldn't save your hours");
      }
      await regenerateSlots(supabase, expertId);
      return await mechanicSchedule(supabase, expertId);
    }
    case "expert_add_leave": {
      const checked = checkLeave(body.startDate, body.endDate);
      if (!checked.ok) return jsonError(400, "invalid_leave", checked.message);
      const { error: leaveError } = await supabase
        .from("expert_time_off")
        .insert({ expert_id: expertId, starts_at: checked.value.startsAt, ends_at: checked.value.endsAt, reason: "Leave (set in app)" });
      if (leaveError) {
        console.error("adding leave failed", leaveError);
        return jsonError(500, "save_failed", "Couldn't save your leave");
      }
      await regenerateSlots(supabase, expertId);
      return await mechanicSchedule(supabase, expertId);
    }
    case "expert_remove_leave": {
      if (typeof body.leaveId !== "string" || !body.leaveId) return jsonError(400, "invalid_request", "leaveId is required");
      const { error: removeError } = await supabase.from("expert_time_off").delete().eq("id", body.leaveId).eq("expert_id", expertId);
      if (removeError) {
        console.error("removing leave failed", removeError);
        return jsonError(500, "save_failed", "Couldn't remove that leave");
      }
      await regenerateSlots(supabase, expertId);
      return await mechanicSchedule(supabase, expertId);
    }
    case "expert_bookings":
      return await mechanicBookings(supabase, expertId);
    case "expert_join_video":
      return await mechanicJoinVideo(supabase, expertId, body.consultationId);
    default:
      return jsonError(400, "invalid_request", `Unknown action: ${action}`);
  }
}

async function regenerateSlots(supabase: SupabaseClient, expertId: string): Promise<void> {
  const { error } = await supabase.rpc("generate_expert_slots", { p_expert_id: expertId });
  if (error) console.error("generate_expert_slots failed", error);
}

async function mechanicSchedule(supabase: SupabaseClient, expertId: string): Promise<Response> {
  const [hoursResult, leaveResult] = await Promise.all([
    supabase.from("expert_weekly_hours").select("weekday, start_time, end_time").eq("expert_id", expertId).order("weekday").order("start_time"),
    supabase.from("expert_time_off").select("id, starts_at, ends_at").eq("expert_id", expertId).gt("ends_at", new Date().toISOString()).order("starts_at"),
  ]);
  if (hoursResult.error || leaveResult.error) {
    console.error("loading schedule failed", hoursResult.error ?? leaveResult.error);
    return jsonError(500, "query_failed", "Couldn't load your schedule");
  }
  return json({
    // Postgres returns "10:00:00"; the app works in "10:00".
    hours: (hoursResult.data ?? []).map((h: any) => ({ weekday: h.weekday, start: h.start_time.slice(0, 5), end: h.end_time.slice(0, 5) })),
    leave: (leaveResult.data ?? []).map((l: any) => ({ id: l.id, ...leaveDates(l.starts_at, l.ends_at) })),
  });
}

async function mechanicBookings(supabase: SupabaseClient, expertId: string): Promise<Response> {
  // Confirmed calls from earlier today onward, so a call in progress or just finished still shows.
  const since = new Date(Date.now() - 12 * 60 * 60_000).toISOString();
  const { data, error } = await supabase
    .from("consultations")
    .select("id, booking_reference, status, vehicle_context, expert_availability_slots!inner(start_time, end_time)")
    .eq("expert_id", expertId)
    .eq("status", "confirmed")
    .gt("expert_availability_slots.end_time", since);
  if (error) {
    console.error("expert_bookings query failed", error);
    return jsonError(500, "query_failed", "Couldn't load your calls");
  }
  const bookings = (data ?? [])
    .map((row: any) => ({
      id: row.id,
      bookingReference: row.booking_reference,
      slotStartEpochMillis: new Date(row.expert_availability_slots.start_time).getTime(),
      slotEndEpochMillis: new Date(row.expert_availability_slots.end_time).getTime(),
      car: summariseCar(row.vehicle_context),
    }))
    .sort((a, b) => a.slotStartEpochMillis - b.slotStartEpochMillis);
  return json({ bookings });
}

async function mechanicJoinVideo(supabase: SupabaseClient, expertId: string, consultationId: unknown): Promise<Response> {
  if (typeof consultationId !== "string" || !consultationId) return jsonError(400, "invalid_request", "consultationId is required");
  const hms = hmsConfigFromEnv();
  if (!hms) return jsonError(503, "video_not_configured", "Video calls aren't available yet");

  const { data: row, error } = await supabase
    .from("consultations")
    .select(`${JOIN_SELECT}, expert_id`)
    .eq("id", consultationId)
    .maybeSingle();
  if (error || !row) return jsonError(404, "not_found", "Booking not found");
  if (row.expert_id !== expertId) return jsonError(403, "forbidden", "This call isn't yours");
  if (row.status !== "confirmed") return jsonError(409, "not_confirmed", "This call isn't confirmed (it may have been cancelled)");

  const slot = (row as any).expert_availability_slots;
  const window = joinWindow(slot.start_time, slot.end_time, MECHANIC_EARLY_MINUTES);
  if (window !== "open") return jsonError(422, `join_${window}`, JOIN_WINDOW_MESSAGES[window](MECHANIC_EARLY_MINUTES));

  try {
    const roomId = await ensureRoom(supabase, hms, row);
    const expiresAt = Math.floor(new Date(slot.end_time).getTime() / 1000) + OVERRUN_MINUTES * 60;
    const authToken = await appToken(hms, roomId, `mechanic-${expertId}`, MECHANIC_ROLE, expiresAt);
    return json({ authToken });
  } catch (e) {
    console.error("expert_join_video failed", e);
    return jsonError(502, "video_unavailable", "Couldn't start the video call. Please try again.");
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "GET") {
    const token = new URL(req.url).searchParams.get("expert");
    if (!token) return mechanicPage(400, "Link not valid", "This link is missing its consultation code.");
    if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) return mechanicPage(500, "Server not configured", "Please contact GetAutoSure.");
    return await mechanicJoin(createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY), token);
  }
  if (req.method !== "POST") return jsonError(405, "method_not_allowed", "Only GET and POST are supported");

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

  // Everything else needs to know who's signed in.
  let identity: { sub: string; email: string | null };
  try {
    identity = await verifyGoogleIdentity(req.headers.get("Authorization"));
  } catch (e) {
    if (e instanceof Response) return e;
    return jsonError(401, "unauthorized", "Authentication failed");
  }
  const customerSub = identity.sub;

  if (action.startsWith("expert_")) return await handleMechanicAction(supabase, action, identity.email, body);

  switch (action) {
    case "reserve_slot":
      return await reserveSlot(supabase, customerSub, body.slotId, body.vehicleContext ?? null);
    case "my_bookings":
      return await myBookings(supabase, customerSub);
    case "cancel_booking":
      return await cancelBooking(supabase, customerSub, body.consultationId);
    case "join_video":
      return await joinVideo(supabase, customerSub, body.consultationId);
    default:
      return jsonError(400, "invalid_request", `Unknown action: ${action}`);
  }
});
