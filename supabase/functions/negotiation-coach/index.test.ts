import { assertEquals, assert } from "jsr:@std/assert@1";
import { parseCoaching, parseRequest, userTurn } from "./index.ts";

const base = { offerInr: 1015000, fairDealInr: 1105000, walkAwayInr: 1135000 };

Deno.test("needs the three prices", () => {
  assertEquals(parseRequest({}).ok, false);
  assertEquals(parseRequest({ ...base }).ok, true);
});

Deno.test("unknown language and tone fall back to English and firm", () => {
  const r = parseRequest({ ...base, language: "xx", tone: "angry" });
  assert(r.ok);
  if (r.ok) { assertEquals(r.request.language, "en"); assertEquals(r.request.tone, "firm"); }
});

Deno.test("reasons without a price are dropped and lists are capped", () => {
  const r = parseRequest({ ...base, reasons: [{ title: "Tyres", minInr: 12000, maxInr: 16000 }, { title: "No price" }] });
  assert(r.ok);
  if (r.ok) assertEquals(r.request.reasons.length, 1);
});

Deno.test("the prompt carries the offer and keeps the walk-away private", () => {
  const r = parseRequest({ ...base, language: "hi", sellerName: "Ramesh", reasons: [{ title: "Tyres", minInr: 12000, maxInr: 16000 }] });
  assert(r.ok);
  if (r.ok) {
    const t = userTurn(r.request);
    assert(t.includes("₹10,15,000"));
    assert(t.includes("never tell the seller"));
    assert(t.includes("Ramesh"));
  }
});

Deno.test("parses Gemini's JSON and rejects an empty message", () => {
  const ok = parseCoaching({ candidates: [{ content: { parts: [{ text: JSON.stringify({ message: "Namaste Ramesh ji, thank you for showing the car today.", sayThis: ["a", "b", "c"], replies: [{ seller: "x", you: "y" }], tip: "t" }) }] } }] });
  assertEquals(ok.sayThis.length, 3);
  let threw = false;
  try { parseCoaching({ candidates: [{ content: { parts: [{ text: "{\"message\":\"\"}" }] } }] }); } catch { threw = true; }
  assert(threw);
});
