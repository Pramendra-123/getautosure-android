// Tests for the pure parts of video.ts: join windows, room limits and the tokens 100ms checks.
// Run with `deno test` from this directory.

import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { decodeJwt, decodeProtectedHeader, jwtVerify } from "https://esm.sh/jose@5.9.6";
import { appToken, type HmsConfig, joinWindow, managementToken, roomMaxDurationSeconds, roomNameFor } from "./video.ts";

const START = "2026-11-10T10:00:00Z";
const END = "2026-11-10T10:15:00Z";
const at = (iso: string) => new Date(iso).getTime();

Deno.test("join window opens early, stays open through a short overrun, then closes", () => {
  assertEquals(joinWindow(START, END, 5, at("2026-11-10T09:54:59Z")), "not_yet");
  assertEquals(joinWindow(START, END, 5, at("2026-11-10T09:55:00Z")), "open");
  assertEquals(joinWindow(START, END, 10, at("2026-11-10T09:50:00Z")), "open");
  assertEquals(joinWindow(START, END, 5, at("2026-11-10T10:25:00Z")), "open");
  assertEquals(joinWindow(START, END, 5, at("2026-11-10T10:25:01Z")), "ended");
});

Deno.test("room limit is the slot plus overrun, with 100ms's 120 s floor", () => {
  assertEquals(roomMaxDurationSeconds(START, END), 25 * 60);
  assertEquals(roomMaxDurationSeconds(START, START), 10 * 60);
});

Deno.test("room names keep only the characters 100ms allows", () => {
  assertEquals(roomNameFor("GAS-123456"), "consult-gas-123456");
  assertEquals(roomNameFor("GAS 12/34"), "consult-gas-12-34");
});

const config: HmsConfig = { accessKey: "test-access-key", appSecret: "test-app-secret", templateId: "t", subdomain: "s" };
const key = new TextEncoder().encode(config.appSecret);

Deno.test("management token carries the claims 100ms requires and is signed with the app secret", async () => {
  const token = await managementToken(config, 1_800_000_000);
  assertEquals(decodeProtectedHeader(token).alg, "HS256");
  const { payload } = await jwtVerify(token, key, { currentDate: new Date(1_800_000_000 * 1000) });
  assertEquals(payload.access_key, "test-access-key");
  assertEquals(payload.type, "management");
  assertEquals(payload.version, 2);
  assertEquals(payload.exp, 1_800_000_000 + 600);
  assertEquals(typeof payload.jti, "string");
});

Deno.test("app token names the room, user and role, and expires when the window closes", async () => {
  const token = await appToken(config, "room-1", "customer-GAS-1", "guest", 1_800_001_500, 1_800_000_000);
  const payload = decodeJwt(token);
  assertEquals(payload.room_id, "room-1");
  assertEquals(payload.user_id, "customer-GAS-1");
  assertEquals(payload.role, "guest");
  assertEquals(payload.type, "app");
  assertEquals(payload.version, 2);
  assertEquals(payload.exp, 1_800_001_500);
  await jwtVerify(token, key, { currentDate: new Date(1_800_000_000 * 1000) });
});
