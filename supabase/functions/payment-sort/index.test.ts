// Tests for the pure parts of payment-sort: request validation and reading Gemini's JSON answer.
import { assertEquals, assertThrows } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { MAX_ITEMS, MAX_TEXT_LENGTH, parseItems, parseSorted } from "./index.ts";

Deno.test("valid payments pass, trimmed to the limits", () => {
  const r = parseItems({ payments: [{ id: "a", merchant: "SRI SAI MOTORS", amountInr: 4200.4, text: "x".repeat(500) }] });
  assertEquals(r.ok, true);
  if (r.ok) {
    assertEquals(r.items[0].amountInr, 4200);
    assertEquals(r.items[0].text.length, MAX_TEXT_LENGTH);
  }
});

Deno.test("malformed payments are dropped; none left is an error", () => {
  assertEquals(parseItems({ payments: [{ id: "a", merchant: "X", amountInr: -5 }] }).ok, false);
  assertEquals(parseItems({}).ok, false);
});

Deno.test("at most MAX_ITEMS payments are sorted per request", () => {
  const many = Array.from({ length: 25 }, (_, i) => ({ id: `p${i}`, merchant: "M", amountInr: 100, text: "" }));
  const r = parseItems({ payments: many });
  assertEquals(r.ok && r.items.length, MAX_ITEMS);
});

Deno.test("only known categories for asked-about ids come back", () => {
  const data = { candidates: [{ content: { parts: [{ text: JSON.stringify({ results: [
    { id: "a", category: "REPAIR", confidence: "high" },
    { id: "b", category: "GROCERIES", confidence: "high" },
    { id: "zzz", category: "WASH", confidence: "low" },
  ] }) }] } }] };
  assertEquals(parseSorted(data, new Set(["a", "b"])), [{ id: "a", category: "REPAIR", confidence: "high" }]);
});

Deno.test("no text in the answer throws", () => {
  assertThrows(() => parseSorted({ candidates: [] }, new Set(["a"])));
});
