// Copies the cards into the app: app/src/main/assets/fault_cards/
//   cards.json        – every card: code, maker, urgency, fuel, costs, plus English and Hindi text
//   cards.<lang>.json – text only, for each translated language that has cards
// Text per language is { title, meaning, notice[], causes[], drive, first_check, cost_parts[], india_note }.
import { readFileSync, writeFileSync, existsSync, mkdirSync, statSync } from "node:fs";

const OUT = "../../app/src/main/assets/fault_cards";
mkdirSync(OUT, { recursive: true });

const text = (c, l) => ({
  title: c.title[l], meaning: c.meaning[l], notice: c.notice.map(x => x[l]), causes: c.causes.map(x => x[l]),
  drive: c.drive[l], first_check: c.first_check[l], cost_parts: c.costs.map(x => x.part[l]), india_note: c.india_note[l],
});

const cards = JSON.parse(readFileSync("out/cards.json", "utf8"));
const base = cards.map(c => ({
  code: c.code, brand: c.brand, urgency: c.urgency, fuel: c.fuel,
  costs: c.costs.map(x => [x.min_inr, x.max_inr]),
  text: { en: text(c, "en"), hi: text(c, "hi") },
}));
writeFileSync(`${OUT}/cards.json`, JSON.stringify(base));
console.log(`cards.json: ${base.length} cards, ${(statSync(`${OUT}/cards.json`).size / 1e6).toFixed(1)} MB`);

for (const lang of ["ta", "te", "kn", "ml", "mr"]) {
  const f = `out/cards.${lang}.json`;
  const list = existsSync(f) ? JSON.parse(readFileSync(f, "utf8")) : [];
  if (!list.length) { console.log(`${lang}: not translated yet, skipped`); continue; }
  const slim = list.map(({ code, brand, ...t }) => ({ code, brand, ...t }));
  writeFileSync(`${OUT}/cards.${lang}.json`, JSON.stringify(slim));
  console.log(`cards.${lang}.json: ${slim.length} cards`);
}
