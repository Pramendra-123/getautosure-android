// Translates the English side of out/cards.json into regional languages with Claude Sonnet.
//
//   node translate.mjs smoke    – one card per language right away, with the cost per card
//   node translate.mjs submit   – every card × every language as one batch (50% off)
//   node translate.mjs collect  – fetches results, validates them, writes out/cards.<lang>.json
//
// Hindi is already in cards.json. Pass language codes after the command to do only some, e.g. "submit ta te". Needs ANTHROPIC_API_KEY.
import Anthropic from "@anthropic-ai/sdk";
import { readFileSync, writeFileSync, existsSync } from "node:fs";

const MODEL = "claude-sonnet-5-5";
const STATE = "out/translate-state.json";
const LANGS = {
  ta: { name: "Tamil", script: /[஀-௿]/, words: "பேட்டரி, சென்சார், இன்ஜின், வொர்க்ஷாப்" },
  te: { name: "Telugu", script: /[ఀ-౿]/, words: "బ్యాటరీ, సెన్సార్, ఇంజిన్, వర్క్‌షాప్" },
  kn: { name: "Kannada", script: /[ಀ-೿]/, words: "ಬ್ಯಾಟರಿ, ಸೆನ್ಸರ್, ಇಂಜಿನ್, ವರ್ಕ್‌ಶಾಪ್" },
  ml: { name: "Malayalam", script: /[ഀ-ൿ]/, words: "ബാറ്ററി, സെൻസർ, എഞ്ചിൻ, വർക്ക്‌ഷോപ്പ്" },
  mr: { name: "Marathi", script: /[ऀ-ॿ]/, words: "बॅटरी, सेन्सर, इंजिन, वर्कशॉप" },
};
const client = new Anthropic();
const cards = JSON.parse(readFileSync("out/cards.json", "utf8"));
const idOf = c => `${c.brand ?? "GENERIC"}_${c.code}`;

// The English text of a card, in the shape the translation must keep.
const english = c => ({
  title: c.title.en, meaning: c.meaning.en,
  notice: c.notice.map(x => x.en), causes: c.causes.map(x => x.en),
  drive: c.drive.en, first_check: c.first_check.en,
  cost_parts: c.costs.map(x => x.part.en), india_note: c.india_note.en,
});

const str = { type: "string" };
const arr = { type: "array", items: str };
const SCHEMA = {
  type: "object", additionalProperties: false,
  required: ["title", "meaning", "notice", "causes", "drive", "first_check", "cost_parts", "india_note"],
  properties: { title: str, meaning: str, notice: arr, causes: arr, drive: str, first_check: str, cost_parts: arr, india_note: str },
};

const system = lang => `You translate fault-code cards for GetAutoSure, an OBD2 car-scanner app, from English into ${LANGS[lang].name} for ordinary car owners in India.

- Write natural, everyday spoken ${LANGS[lang].name} in its own script, the way a helpful local mechanic would explain it. Not literary, not word-for-word.
- Keep car words people say in English, written in ${LANGS[lang].name} script (e.g. ${LANGS[lang].words}). Keep abbreviations like MAF, O2, EGR, DPF, ABS, CNG, PUC, OBD in Latin letters.
- Keep fault codes, numbers, ₹ amounts, km, V and other units exactly as they are.
- Translate everything; add nothing and drop nothing. Lists must have exactly the same number of items, in the same order.
- If india_note is empty, return it empty.`;

const params = (c, lang) => ({
  model: MODEL,
  max_tokens: 8000,
  output_config: { effort: "low", format: { type: "json_schema", schema: SCHEMA } },
  system: [{ type: "text", text: system(lang), cache_control: { type: "ephemeral" } }],
  messages: [{ role: "user", content: JSON.stringify(english(c)) }],
});

function check(c, lang, message) {
  if (message.stop_reason !== "end_turn") return { error: `stop_reason ${message.stop_reason}` };
  let t;
  try { t = JSON.parse(message.content.filter(b => b.type === "text").map(b => b.text).join("")); } catch { return { error: "invalid JSON" }; }
  const en = english(c);
  for (const k of ["notice", "causes", "cost_parts"]) if (t[k].length !== en[k].length) return { error: `${k} count ${t[k].length} vs ${en[k].length}` };
  const texts = [t.title, t.meaning, t.drive, t.first_check, ...t.notice, ...t.causes, ...t.cost_parts, ...(en.india_note.trim() ? [t.india_note] : [])];
  if (texts.some(s => !LANGS[lang].script.test(s))) return { error: `text not in ${LANGS[lang].name} script` };
  return { card: { code: c.code, brand: c.brand, ...t } };
}

// Sonnet 5.5 batch prices: half of $2 / $10 per million input / output tokens.
const cost = u => ((u.input_tokens + (u.cache_creation_input_tokens ?? 0) * 1.25 + (u.cache_read_input_tokens ?? 0) * 0.1) + u.output_tokens * 5) / 1e6;

async function smoke() {
  const c = cards.find(k => k.code === "P0300" && !k.brand);
  let total = 0;
  for (const lang of Object.keys(LANGS)) {
    const msg = await client.messages.create(params(c, lang));
    const r = check(c, lang, msg);
    const batchUsd = cost(msg.usage);
    total += batchUsd;
    console.log(`\n${lang}: ${r.error ? "FAILED " + r.error : "ok"}  out tokens ${msg.usage.output_tokens}  batch-price ~$${batchUsd.toFixed(4)}/card`);
    if (r.card) console.log(`  ${r.card.title}\n  ${r.card.drive}`);
  }
  console.log(`\nEstimate for all ${cards.length} cards × ${Object.keys(LANGS).length} languages: ~$${(total * cards.length).toFixed(0)} (P0300 is an average-length card)`);
}

async function submit() {
  if (existsSync(STATE)) throw new Error(`${STATE} exists: run collect, or delete it to start over.`);
  // Extra arguments: a retry-ids .json file, or language codes ("ta te") to do only those.
  const args = process.argv.slice(3);
  const retryFile = args.find(a => a.endsWith(".json"));
  const only = retryFile ? new Set(JSON.parse(readFileSync(retryFile, "utf8"))) : null;
  const langs = args.filter(a => a in LANGS);
  const requests = [];
  for (const lang of langs.length ? langs : Object.keys(LANGS)) for (const c of cards) {
    const id = `${lang}_${idOf(c)}`;
    if (!only || only.has(id)) requests.push({ custom_id: id, params: params(c, lang) });
  }
  const batch = await client.messages.batches.create({ requests });
  writeFileSync(STATE, JSON.stringify({ id: batch.id, count: requests.length, submitted: new Date().toISOString() }, null, 1));
  console.log(`Submitted batch ${batch.id} with ${requests.length} translations.`);
}

async function collect() {
  const { id } = JSON.parse(readFileSync(STATE, "utf8"));
  const batch = await client.messages.batches.retrieve(id);
  if (batch.processing_status !== "ended") {
    const n = batch.request_counts;
    console.log(`Still running: ${n.succeeded} done, ${n.processing} processing, ${n.errored} errored.`);
    return;
  }
  const byId = new Map(cards.map(c => [idOf(c), c]));
  const out = Object.fromEntries(Object.keys(LANGS).map(l => {
    const f = `out/cards.${l}.json`;
    return [l, new Map(existsSync(f) ? JSON.parse(readFileSync(f, "utf8")).map(k => [idOf(k), k]) : [])];
  }));
  const failed = [];
  let usd = 0;
  for await (const res of await client.messages.batches.results(id)) {
    const lang = res.custom_id.slice(0, 2), cid = res.custom_id.slice(3);
    if (res.result.type !== "succeeded") { failed.push({ id: res.custom_id, error: res.result.type }); continue; }
    usd += cost(res.result.message.usage);
    const r = check(byId.get(cid), lang, res.result.message);
    if (r.card) out[lang].set(cid, r.card); else failed.push({ id: res.custom_id, error: r.error });
  }
  for (const [lang, m] of Object.entries(out)) {
    const sorted = [...m.values()].sort((a, b) => (a.brand ?? "").localeCompare(b.brand ?? "") || a.code.localeCompare(b.code));
    writeFileSync(`out/cards.${lang}.json`, JSON.stringify(sorted, null, 1));
    console.log(`${LANGS[lang].name}: ${sorted.length} cards`);
  }
  writeFileSync("out/translate-failed.json", JSON.stringify(failed, null, 1));
  writeFileSync("out/translate-retry-ids.json", JSON.stringify(failed.map(f => f.id)));
  console.log(`Failed: ${failed.length}. Approx. cost of this batch: $${usd.toFixed(2)}.`);
  if (failed.length) console.log(`To retry: delete ${STATE}, then "node translate.mjs submit out/translate-retry-ids.json".`);
}

const cmd = process.argv[2];
if (cmd === "smoke") await smoke();
else if (cmd === "submit") await submit();
else if (cmd === "collect") await collect();
else console.log("Usage: node translate.mjs smoke | submit | collect");
