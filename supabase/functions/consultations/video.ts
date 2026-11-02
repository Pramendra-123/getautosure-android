// 100ms video for consultations: tokens, room creation and room codes. Kept apart from index.ts so
// the pure parts (tokens, join windows) can be tested without starting the server.
//
// Required secrets (Edge Functions > Secrets), from the 100ms dashboard's Developer page:
//   HMS_ACCESS_KEY   — the app access key
//   HMS_APP_SECRET   — the app secret; signs every token, never leaves this server
//   HMS_TEMPLATE_ID  — the Video Conferencing template (roles: host = mechanic, guest = customer)
//   HMS_SUBDOMAIN    — optional, the template's prebuilt subdomain for the mechanic's browser link

import { SignJWT } from "https://esm.sh/jose@5.9.6";

const HMS_API = "https://api.100ms.live/v2";
const DEFAULT_SUBDOMAIN = "pramendra-123-videoconf-1817";

export const CUSTOMER_ROLE = "guest";
export const MECHANIC_ROLE = "host";

/** The customer's Join button opens this long before the slot, matching the Android app's own window. */
export const CUSTOMER_EARLY_MINUTES = 5;
/** The mechanic can get in a little earlier to be waiting when the customer arrives. */
export const MECHANIC_EARLY_MINUTES = 10;
/** A call that runs a few minutes over isn't cut off mid-sentence. */
export const OVERRUN_MINUTES = 10;

export interface HmsConfig {
  accessKey: string;
  appSecret: string;
  templateId: string;
  subdomain: string;
}

export function hmsConfigFromEnv(): HmsConfig | null {
  const accessKey = Deno.env.get("HMS_ACCESS_KEY");
  const appSecret = Deno.env.get("HMS_APP_SECRET");
  const templateId = Deno.env.get("HMS_TEMPLATE_ID");
  if (!accessKey || !appSecret || !templateId) return null;
  return { accessKey, appSecret, templateId, subdomain: Deno.env.get("HMS_SUBDOMAIN") || DEFAULT_SUBDOMAIN };
}

export type JoinWindow = "open" | "not_yet" | "ended";

/** Where [nowMs] falls relative to a slot: joinable from [earlyMinutes] before start until [OVERRUN_MINUTES] after end. */
export function joinWindow(startIso: string, endIso: string, earlyMinutes: number, nowMs: number = Date.now()): JoinWindow {
  const opens = new Date(startIso).getTime() - earlyMinutes * 60_000;
  const closes = new Date(endIso).getTime() + OVERRUN_MINUTES * 60_000;
  if (nowMs < opens) return "not_yet";
  if (nowMs > closes) return "ended";
  return "open";
}

/** The room's hard limit: the slot plus the overrun, never below 100ms's 120-second minimum. */
export function roomMaxDurationSeconds(startIso: string, endIso: string): number {
  const slotSeconds = Math.round((new Date(endIso).getTime() - new Date(startIso).getTime()) / 1000);
  return Math.max(120, slotSeconds + OVERRUN_MINUTES * 60);
}

/** One room per booking, named after its reference — creating a room with an existing name returns that room, so retries are safe. */
export function roomNameFor(bookingReference: string): string {
  return `consult-${bookingReference}`.toLowerCase().replace(/[^a-z0-9.\-:_]/g, "-");
}

function secretKey(config: HmsConfig): Uint8Array {
  return new TextEncoder().encode(config.appSecret);
}

/** Server-to-100ms token for the REST API. Short-lived; a new one per request is cheap. */
export async function managementToken(config: HmsConfig, nowSeconds: number = Math.floor(Date.now() / 1000)): Promise<string> {
  return await new SignJWT({ access_key: config.accessKey, type: "management", version: 2 })
    .setProtectedHeader({ alg: "HS256", typ: "JWT" })
    .setIssuedAt(nowSeconds)
    .setNotBefore(nowSeconds)
    .setExpirationTime(nowSeconds + 10 * 60)
    .setJti(crypto.randomUUID())
    .sign(secretKey(config));
}

/** The token the customer's app joins with. It expires when the join window closes, so it can't be reused for a later call. */
export async function appToken(
  config: HmsConfig,
  roomId: string,
  userId: string,
  role: string,
  expiresAtSeconds: number,
  nowSeconds: number = Math.floor(Date.now() / 1000),
): Promise<string> {
  return await new SignJWT({ access_key: config.accessKey, room_id: roomId, user_id: userId, role, type: "app", version: 2 })
    .setProtectedHeader({ alg: "HS256", typ: "JWT" })
    .setIssuedAt(nowSeconds)
    .setNotBefore(nowSeconds)
    .setExpirationTime(expiresAtSeconds)
    .setJti(crypto.randomUUID())
    .sign(secretKey(config));
}

async function hmsPost(config: HmsConfig, path: string, body: unknown): Promise<any> {
  const response = await fetch(`${HMS_API}${path}`, {
    method: "POST",
    headers: { "Authorization": `Bearer ${await managementToken(config)}`, "Content-Type": "application/json" },
    body: JSON.stringify(body ?? {}),
  });
  const text = await response.text();
  if (!response.ok) {
    console.error(`100ms ${path} failed`, response.status, text.slice(0, 500));
    throw new Error(`100ms ${path} returned ${response.status}`);
  }
  return text ? JSON.parse(text) : {};
}

/** Creates (or, for an existing name, returns) the booking's room and gives back its id. */
export async function createRoom(
  config: HmsConfig,
  bookingReference: string,
  startIso: string,
  endIso: string,
): Promise<string> {
  const room = await hmsPost(config, "/rooms", {
    name: roomNameFor(bookingReference),
    description: `GetAutoSure consultation ${bookingReference}`,
    template_id: config.templateId,
    region: "in",
    max_duration_seconds: roomMaxDurationSeconds(startIso, endIso),
  });
  if (typeof room.id !== "string") throw new Error("100ms room response had no id");
  return room.id;
}

/** The mechanic's room code; they join through 100ms's ready-made web page, so no token handling in the browser. */
export async function createMechanicRoomCode(config: HmsConfig, roomId: string): Promise<string> {
  const result = await hmsPost(config, `/room-codes/room/${roomId}/role/${MECHANIC_ROLE}`, {});
  if (typeof result.code !== "string") throw new Error("100ms room-code response had no code");
  return result.code;
}

export function mechanicMeetingUrl(config: HmsConfig, roomCode: string): string {
  return `https://${config.subdomain}.app.100ms.live/meeting/${roomCode}`;
}
