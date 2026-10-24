// Tests for the pure, extracted logic in index.ts — request validation and Gemini response
// parsing. Written against Deno's built-in test runner (`Deno.test`, `std/assert`).
//
// STATUS: NEVER RUN. No `deno` binary is available in the environment this was written in —
// only syntax-checked with `tsc --noEmit` (the same way index.ts was), which catches malformed
// TypeScript but cannot confirm these assertions actually pass. Run `deno test` for real before
// trusting this file.
//
// Deliberately not covering: auth (verifyGoogleIdToken needs a real/forged signed JWT and a
// live JWKS fetch), rate limiting (needs a real Postgres connection — the atomicity fix itself
// is only meaningfully verified by a concurrent integration test against a real database, which
// isn't practical to fake convincingly here), and the Gemini HTTP call itself (needs a fetch
// mock). Those need real infrastructure to test honestly rather than a test that exercises
// nothing but its own mock.

import { assertEquals, assertThrows } from "https://deno.land/std@0.224.0/assert/mod.ts";
import {
  GEMINI_MODEL,
  GeminiBlockedError,
  MAX_HISTORY_TURNS,
  MAX_KNOWLEDGE_HINTS,
  MAX_QUESTION_LENGTH,
  parseAndValidateRequest,
  parseGeminiResponse,
} from "./index.ts";

// --- parseAndValidateRequest ---

Deno.test("a well-formed request is accepted as-is", () => {
  const result = parseAndValidateRequest({
    question: "why is my mileage poor?",
    history: [{ fromUser: true, text: "earlier" }],
    context: { hasScan: true },
    knowledgeHints: ["hint"],
  });
  assertEquals(result.ok, true);
  if (result.ok) {
    assertEquals(result.request.question, "why is my mileage poor?");
    assertEquals(result.request.history.length, 1);
    assertEquals(result.request.knowledgeHints, ["hint"]);
  }
});

Deno.test("a non-object body is rejected", () => {
  const result = parseAndValidateRequest("just a string");
  assertEquals(result.ok, false);
  if (result.ok === false) assertEquals(result.code, "invalid_request");
});

Deno.test("null body is rejected", () => {
  const result = parseAndValidateRequest(null);
  assertEquals(result.ok, false);
});

Deno.test("an empty or missing question is rejected", () => {
  assertEquals(parseAndValidateRequest({ question: "" }).ok, false);
  assertEquals(parseAndValidateRequest({ question: "   " }).ok, false);
  assertEquals(parseAndValidateRequest({}).ok, false);
});

Deno.test("a question longer than the cap is truncated, not rejected", () => {
  const longQuestion = "a".repeat(MAX_QUESTION_LENGTH + 500);
  const result = parseAndValidateRequest({ question: longQuestion });
  assertEquals(result.ok, true);
  if (result.ok) assertEquals(result.request.question.length, MAX_QUESTION_LENGTH);
});

Deno.test("history longer than the cap is truncated to the most recent turns", () => {
  const history = Array.from({ length: 30 }, (_, i) => ({ fromUser: i % 2 === 0, text: `turn ${i}` }));
  const result = parseAndValidateRequest({ question: "q", history });
  assertEquals(result.ok, true);
  if (result.ok) {
    assertEquals(result.request.history.length, MAX_HISTORY_TURNS);
    assertEquals(result.request.history[result.request.history.length - 1].text, "turn 29");
  }
});

Deno.test("malformed history entries are dropped, not passed through or crashing", () => {
  const result = parseAndValidateRequest({
    question: "q",
    history: [{ fromUser: true, text: "ok" }, { fromUser: "not a boolean", text: "bad" }, "also bad", null],
  });
  assertEquals(result.ok, true);
  if (result.ok) assertEquals(result.request.history.length, 1);
});

Deno.test("a missing context is treated as null, not fabricated", () => {
  const result = parseAndValidateRequest({ question: "q" });
  assertEquals(result.ok, true);
  if (result.ok) assertEquals(result.request.context, null);
});

Deno.test("knowledgeHints is capped and non-string entries are dropped", () => {
  const hints = Array.from({ length: 10 }, (_, i) => `hint ${i}`);
  const result = parseAndValidateRequest({ question: "q", knowledgeHints: [...hints, 123, null] });
  assertEquals(result.ok, true);
  if (result.ok) assertEquals(result.request.knowledgeHints.length, MAX_KNOWLEDGE_HINTS);
});

// --- parseGeminiResponse ---

Deno.test("a normal successful response returns the text", () => {
  const data = { candidates: [{ content: { parts: [{ text: "Check your air filter." }] }, finishReason: "STOP" }] };
  assertEquals(parseGeminiResponse(data), "Check your air filter.");
});

Deno.test("a prompt-level block throws GeminiBlockedError, not a generic parse error", () => {
  const data = { promptFeedback: { blockReason: "SAFETY" }, candidates: [] };
  assertThrows(() => parseGeminiResponse(data), GeminiBlockedError);
});

Deno.test("a candidate-level safety block throws GeminiBlockedError", () => {
  const data = { candidates: [{ finishReason: "SAFETY", content: null }] };
  assertThrows(() => parseGeminiResponse(data), GeminiBlockedError);
});

Deno.test("a candidate-level RECITATION block throws GeminiBlockedError too", () => {
  const data = { candidates: [{ finishReason: "RECITATION" }] };
  assertThrows(() => parseGeminiResponse(data), GeminiBlockedError);
});

Deno.test("MAX_TOKENS is not treated as a block — partial text is still returned", () => {
  const data = { candidates: [{ finishReason: "MAX_TOKENS", content: { parts: [{ text: "partial answer" }] } }] };
  assertEquals(parseGeminiResponse(data), "partial answer");
});

Deno.test("no candidates and no block reason throws a generic (non-blocked) error", () => {
  const data = { candidates: [] };
  assertThrows(
    () => parseGeminiResponse(data),
    Error,
    "no usable text",
  );
});

Deno.test("a candidate with empty text throws", () => {
  const data = { candidates: [{ content: { parts: [{ text: "   " }] }, finishReason: "STOP" }] };
  assertThrows(() => parseGeminiResponse(data));
});

Deno.test("completely malformed/empty response object throws rather than returning undefined as text", () => {
  assertThrows(() => parseGeminiResponse({}));
  assertThrows(() => parseGeminiResponse(null));
});

// --- Sanity checks on constants reviewers might change without updating code that depends on them ---

Deno.test("GEMINI_MODEL is set to something, not left blank", () => {
  assertEquals(typeof GEMINI_MODEL, "string");
  assertEquals(GEMINI_MODEL.length > 0, true);
});
