// Generates fault-code cards (English + Hindi) with Claude Sonnet through the Batch API.
//
//   node generate.mjs smoke    – 2 cards right away (normal API), to check the prompt and output
//   node generate.mjs submit   – sends every code in out/codes.json as one overnight batch (50% off)
//   node generate.mjs collect  – fetches the batch results, validates each card, writes out/cards.json
//
// Needs ANTHROPIC_API_KEY in the environment. The card format matches docs/fault-card-samples.html.
import Anthropic from "@anthropic-ai/sdk";
import { readFileSync, writeFileSync, existsSync } from "node:fs";

const MODEL = "claude-sonnet-5-5";
const STATE = "out/batch-state.json";
const client = new Anthropic();

// --- Output schema ---------------------------------------------------------------------------

const pair = { type: "object", additionalProperties: false, required: ["en", "hi"],
  properties: { en: { type: "string" }, hi: { type: "string" } } };
const SCHEMA = {
  type: "object", additionalProperties: false,
  required: ["title", "meaning", "fuel", "urgency", "notice", "causes", "drive", "first_check", "costs", "india_note", "confidence"],
  properties: {
    title: pair,
    meaning: pair,
    fuel: { type: "array", items: { type: "string", enum: ["Petrol", "Diesel", "CNG"] } },
    urgency: { type: "string", enum: ["STOP", "NOW", "SOON", "WAIT"] },
    notice: { type: "array", items: pair },
    causes: { type: "array", items: pair },
    drive: pair,
    first_check: pair,
    costs: { type: "array", items: { type: "object", additionalProperties: false,
      required: ["part", "min_inr", "max_inr"],
      properties: { part: pair, min_inr: { type: "integer" }, max_inr: { type: "integer" } } } },
    india_note: pair,
    confidence: { type: "string", enum: ["high", "medium", "low"] },
  },
};

// --- Prompt ----------------------------------------------------------------------------------

// The app's existing tentative cost table, so cards quote the same ranges the app already shows.
const costTable = [...readFileSync("../../app/src/main/java/com/enginesense/app/vehicle/ai/RepairCostReference.kt", "utf8")
  .matchAll(/RepairCostEntry\("([^"]+)", ([\d_]+), ([\d_]+), "([^"]+)"/g)]
  .map(([, part, min, max, note]) => `- ${part}: ₹${min.replace(/_/g, ",")}–${max.replace(/_/g, ",")} (${note})`)
  .join("\n");

const EXAMPLE = {
  title: { en: "Engine is misfiring (several cylinders)", hi: "इंजन मिसफ़ायर कर रहा है (कई सिलेंडर)" },
  meaning: { en: "Fuel isn't burning properly in more than one cylinder, so the car shakes and loses power.",
    hi: "एक से ज़्यादा सिलेंडर में फ़्यूल ठीक से नहीं जल रहा, इसलिए गाड़ी झटके देती है और पावर कम हो जाती है।" },
  fuel: ["Petrol", "CNG"],
  urgency: "NOW",
  notice: [
    { en: "Shaking at idle or while accelerating", hi: "खड़ी गाड़ी में या एक्सीलरेट करते समय झटके" },
    { en: "Check-engine light on, or flashing", hi: "चेक-इंजन लाइट जलना या टिमटिमाना" },
    { en: "Lower mileage", hi: "माइलेज कम होना" }],
  causes: [
    { en: "Worn spark plugs", hi: "घिसे हुए स्पार्क प्लग" },
    { en: "Weak ignition coil", hi: "कमज़ोर इग्निशन कॉइल" },
    { en: "Poor-quality or adulterated fuel", hi: "ख़राब या मिलावटी फ़्यूल" },
    { en: "Badly tuned CNG/LPG kit", hi: "ठीक से ट्यून न की गई CNG/LPG किट" }],
  drive: { en: "If the check-engine light is flashing, stop driving: unburnt fuel can ruin the catalytic converter (₹8,000–35,000). If it's steady, drive gently to a workshop.",
    hi: "अगर चेक-इंजन लाइट टिमटिमा रही है तो गाड़ी चलाना बंद करें — बिना जला फ़्यूल कैटेलिटिक कन्वर्टर ख़राब कर सकता है (₹8,000–35,000)। लाइट स्थिर है तो धीरे-धीरे वर्कशॉप तक ले जाएं।" },
  first_check: { en: "Start with the spark plugs, especially if they haven't been changed in 30,000 km.",
    hi: "शुरुआत स्पार्क प्लग से करें, ख़ासकर अगर 30,000 km से नहीं बदले गए।" },
  costs: [
    { part: { en: "Spark plugs (set)", hi: "स्पार्क प्लग (सेट)" }, min_inr: 1000, max_inr: 4000 },
    { part: { en: "Ignition coil (each)", hi: "इग्निशन कॉइल (एक)" }, min_inr: 1500, max_inr: 5000 }],
  india_note: { en: "On CNG, spark plugs wear out about twice as fast. Check them every 15,000–20,000 km.",
    hi: "CNG पर स्पार्क प्लग लगभग दोगुनी तेज़ी से घिसते हैं — हर 15,000–20,000 km पर जांच कराएं।" },
  confidence: "high",
};

const SYSTEM = `You write fault-code cards for GetAutoSure, an OBD2 scanner app for car owners and used-car buyers in India. A card turns one engine fault code into something an ordinary owner understands and can act on. Owners are not mechanics; many read Hindi more comfortably than English.

Write each field in both English ("en") and Hindi ("hi").
- English: short, plain sentences an owner with no car knowledge understands. No jargon without explaining it.
- Hindi: natural everyday Hindi in Devanagari, the way a helpful mechanic would speak. Keep common car words that Indians say in English, written in Devanagari (सेंसर, बैटरी, इंजन, वर्कशॉप, फ़्यूल). Never word-for-word translation.

Fields:
- title: what is wrong, in owner words (not the SAE wording).
- meaning: 1–2 sentences.
- fuel: which fuel types this code applies to (Petrol, Diesel, CNG). CNG cars also run petrol systems.
- urgency: STOP = stop driving now (overheating, oil pressure, brakes, anything that can wreck the engine or be unsafe within minutes); NOW = fix in the next few days; SOON = within about 2 weeks; WAIT = no harm in waiting, keep an eye on it.
- notice: 2–4 things the owner may notice. Say "often nothing at all" when that is true.
- causes: 2–5 likely causes, MOST COMMON FIRST, ranked for Indian conditions (dust, fuel quality, water in diesel, heat, rats chewing wires, monsoon flooding, aftermarket accessories and CNG kits, stop-start city traffic, local workshops using the wrong parts or oil).
- drive: answer "Can I keep driving?" directly, with the condition that changes the answer if there is one.
- first_check: the cheapest sensible thing to check or try first, so the owner isn't pushed into an expensive repair.
- costs: 1–4 parts or jobs with tentative Indian workshop price ranges in rupees (2026, typical hatchback-to-sedan, labour included). Reuse the app's ranges below when the part matches; otherwise give an honest wide range.
- india_note: one useful India-specific tip for this fault. Use an empty string for both languages only if there is truly nothing India-specific to say.
- confidence: high = standard, well-documented meaning; medium = meaning is clear but causes vary a lot by car; low = maker-specific code whose exact meaning you are unsure of. For low, keep claims general and say in "meaning" that the exact meaning depends on the car maker.

Rules:
- Only state what is generally true for this code. Don't invent maker-specific details.
- Never claim the scanner measures something OBD cannot (compression, brake pad wear, tyre condition).
- Never suggest removing or bypassing the catalytic converter, DPF, EGR or any emission part, and never suggest disconnecting a warning light. If workshops commonly offer that, warn that it is illegal and will fail PUC.
- Don't tell the owner to "show this report to a mechanic"; say what to check or ask for.
- Costs are tentative estimates; the app labels them as such, so don't repeat disclaimers inside the card.

The app's existing tentative cost ranges:
${costTable}

Example of a finished card for P0300 (Random/Multiple Cylinder Misfire Detected):
${JSON.stringify(EXAMPLE)}`;

const userPrompt = c =>
  `Write the card for ${c.code}${c.brand ? ` as defined by ${c.brand}` : " (generic SAE code)"}.\nTechnical description: ${c.description}`;

const params = c => ({
  model: MODEL,
  max_tokens: 8000,
  output_config: { effort: "medium", format: { type: "json_schema", schema: SCHEMA } },
  system: [{ type: "text", text: SYSTEM, cache_control: { type: "ephemeral" } }],
  messages: [{ role: "user", content: userPrompt(c) }],
});

const idOf = c => `${c.brand ?? "GENERIC"}_${c.code}`;
const codes = JSON.parse(readFileSync("out/codes.json", "utf8"));

// --- Validation ------------------------------------------------------------------------------

const DEVANAGARI = /[ऀ-ॿ]/;
function problems(card) {
  const out = [];
  const pairs = [card.title, card.meaning, card.drive, card.first_check, ...card.notice, ...card.causes, ...card.costs.map(x => x.part)];
  for (const p of pairs) {
    if (!p.en.trim()) out.push("empty English text");
    if (!DEVANAGARI.test(p.hi)) out.push(`Hindi missing for "${p.en.slice(0, 40)}"`);
  }
  if (card.india_note.en.trim() && !DEVANAGARI.test(card.india_note.hi)) out.push("india_note Hindi missing");
  if (card.notice.length < 1 || card.causes.length < 2) out.push("too few notice/causes");
  if (card.fuel.length === 0) out.push("no fuel type");
  for (const x of card.costs) {
    if (x.min_inr <= 0 || x.min_inr > x.max_inr || x.max_inr > 500_000) out.push(`odd cost ${x.min_inr}–${x.max_inr}`);
  }
  return out;
}

function toCard(c, message) {
  if (message.stop_reason !== "end_turn") return { error: `stop_reason ${message.stop_reason}` };
  const text = message.content.filter(b => b.type === "text").map(b => b.text).join("");
  let card;
  try { card = JSON.parse(text); } catch { return { error: "invalid JSON" }; }
  const issues = problems(card);
  if (issues.length) return { error: issues.join("; ") };
  return { card: { code: c.code, brand: c.brand, tech: c.description, ...card } };
}

// Sonnet 5.5 batch prices: half of $2 / $10 per million input / output tokens.
const cost = u => ((u.input_tokens + (u.cache_creation_input_tokens ?? 0) * 1.25 + (u.cache_read_input_tokens ?? 0) * 0.1) * 1
  + u.output_tokens * 5) / 1e6;

// --- Commands --------------------------------------------------------------------------------

async function smoke() {
  const pick = [codes.find(c => c.code === "P0171" && !c.brand), codes.find(c => c.brand === "TATA")];
  for (const c of pick) {
    const msg = await client.messages.create(params(c));
    const r = toCard(c, msg);
    const usd = cost(msg.usage) * 2; // not a batch, so full price
    console.log(`\n${idOf(c)}  ${r.error ? "FAILED: " + r.error : "ok"}  tokens in/out ${msg.usage.input_tokens}/${msg.usage.output_tokens}  ~$${usd.toFixed(4)}`);
    if (r.card) console.log(JSON.stringify(r.card, null, 1));
  }
}

async function submit() {
  if (existsSync(STATE)) throw new Error(`${STATE} exists: a batch was already submitted. Run collect, or delete the file to start over.`);
  const only = process.argv[3] ? new Set(JSON.parse(readFileSync(process.argv[3], "utf8"))) : null; // optional retry list
  const todo = only ? codes.filter(c => only.has(idOf(c))) : codes;
  const batch = await client.messages.batches.create({ requests: todo.map(c => ({ custom_id: idOf(c), params: params(c) })) });
  writeFileSync(STATE, JSON.stringify({ id: batch.id, count: todo.length, submitted: new Date().toISOString() }, null, 1));
  console.log(`Submitted batch ${batch.id} with ${todo.length} cards. Most batches finish within an hour; run "npm run collect" later.`);
}

async function collect() {
  const { id } = JSON.parse(readFileSync(STATE, "utf8"));
  const batch = await client.messages.batches.retrieve(id);
  if (batch.processing_status !== "ended") {
    const n = batch.request_counts;
    console.log(`Still running: ${n.succeeded} done, ${n.processing} processing, ${n.errored} errored.`);
    return;
  }
  const byId = new Map(codes.map(c => [idOf(c), c]));
  const existing = existsSync("out/cards.json") ? JSON.parse(readFileSync("out/cards.json", "utf8")) : [];
  const cards = new Map(existing.map(k => [`${k.brand ?? "GENERIC"}_${k.code}`, k]));
  const failed = [];
  let usd = 0;
  for await (const res of await client.messages.batches.results(id)) {
    const c = byId.get(res.custom_id);
    if (res.result.type !== "succeeded") { failed.push({ id: res.custom_id, error: res.result.type }); continue; }
    usd += cost(res.result.message.usage);
    const r = toCard(c, res.result.message);
    if (r.card) cards.set(res.custom_id, r.card); else failed.push({ id: res.custom_id, error: r.error });
  }
  const sorted = [...cards.values()].sort((a, b) => (a.brand ?? "").localeCompare(b.brand ?? "") || a.code.localeCompare(b.code));
  writeFileSync("out/cards.json", JSON.stringify(sorted, null, 1));
  writeFileSync("out/failed.json", JSON.stringify(failed, null, 1));
  writeFileSync("out/retry-ids.json", JSON.stringify(failed.map(f => f.id)));
  const low = sorted.filter(k => k.confidence === "low").length;
  console.log(`Saved ${sorted.length} cards (${low} marked low confidence). Failed: ${failed.length}. Approx. cost of this batch: $${usd.toFixed(2)}.`);
  if (failed.length) console.log(`To retry the failed ones: delete ${STATE}, then "node generate.mjs submit out/retry-ids.json".`);
}

const cmd = process.argv[2];
if (cmd === "smoke") await smoke();
else if (cmd === "submit") await submit();
else if (cmd === "collect") await collect();
else console.log("Usage: node generate.mjs smoke | submit | collect");
