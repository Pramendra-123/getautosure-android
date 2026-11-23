// Picks which fault codes get a card and writes out/codes.json.
//
// Generic codes: the database has 9,415, most of them rare (hybrid/EV, body modules, exotic
// sensors). We keep the ranges that actually show up on Indian petrol/diesel/CNG cars.
// Brand codes: entries in india_override_codes.json that are maker-defined (P1xxx, P3xxx,
// U1xxx…) or missing from the generic table. SAE-defined codes (second character 0 or 2) mean
// the same on every car, so the generic card already covers them however the maker words it.
import initSqlJs from "sql.js";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";

const ROOT = "../..";
const SQL = await initSqlJs();
const db = new SQL.Database(readFileSync(`${ROOT}/dtc-database-android/src/main/assets/dtc_codes.db`));
const generic = new Map(
  db.exec("select code, description from dtc_definitions where manufacturer='GENERIC' and locale='en'")[0]
    .values.map(([c, d]) => [c, d])
);

// Inclusive ranges of decimal-looking codes, e.g. ["P0100", "P0199"].
const RANGES = [
  ["P0010", "P0024"], // variable valve timing
  ["P0030", "P0068"], // oxygen sensor heaters, MAP/MAF correlation
  ["P0087", "P0093"], // fuel rail pressure (diesel, GDI)
  ["P0100", "P0199"], // air/fuel metering sensors
  ["P0200", "P0238"], // injectors, throttle, turbo boost
  ["P0261", "P0272"], // injector circuits, cylinders 1-4
  ["P0299", "P0299"], // turbo underboost
  ["P0300", "P0308"], // misfires
  ["P0320", "P0346"], // crank/cam sensors, knock sensors
  ["P0351", "P0354"], // ignition coils 1-4
  ["P0380", "P0384"], // glow plugs (diesel)
  ["P0400", "P0499"], // emissions: EGR, catalyst, EVAP, fans
  ["P0500", "P0599"], // speed, idle, cruise, charging voltage
  ["P0600", "P0610"], // engine computer
  ["P0627", "P0629"], // fuel pump control
  ["P0641", "P0652"], // sensor reference voltage
  ["P0700", "P0799"], // transmission (AT/AMT/CVT)
  ["P2002", "P2003"], // DPF efficiency
  ["P2096", "P2099"], // post-catalyst fuel trim
  ["P2100", "P2119"], // electronic throttle
  ["P2135", "P2138"], // throttle/pedal correlation
  ["P2187", "P2196"], // lean/rich at idle, O2 signal stuck
  ["P2263", "P2263"], // turbo boost performance
  ["P2279", "P2279"], // intake air leak
  ["P2457", "P2463"], // EGR cooler, DPF soot
  ["U0001", "U0001"], // CAN bus
  ["U0073", "U0073"],
  ["U0100", "U0164"], // lost communication with modules
  ["U0401", "U0401"],
  ["C0035", "C0050"], // ABS wheel speed sensors
];
const EXTRA = ["P242F", "P20EE", "P0A80"]; // DPF ash, SCR efficiency, hybrid battery

const num = c => parseInt(c.slice(1), 10);
const picked = [];
for (const [from, to] of RANGES) {
  for (const [code, desc] of generic) {
    if (code[0] === from[0] && /^\w\d{4}$/.test(code) && num(code) >= num(from) && num(code) <= num(to)) {
      picked.push({ code, brand: null, description: desc });
    }
  }
}
for (const code of EXTRA) if (generic.has(code)) picked.push({ code, brand: null, description: generic.get(code) });

const overrides = JSON.parse(readFileSync(`${ROOT}/app/src/main/assets/india_override_codes.json`, "utf8"));
const genericCodes = new Set(picked.map(c => c.code));
let sameAsGeneric = 0;
for (const o of overrides) {
  const saeDefined = o.code[1] === "0" || o.code[1] === "2";
  if (saeDefined && generic.has(o.code)) {
    sameAsGeneric++;
    // A standard code we didn't pick by range still deserves its generic card if an Indian maker lists it.
    if (!genericCodes.has(o.code)) { picked.push({ code: o.code, brand: null, description: generic.get(o.code) }); genericCodes.add(o.code); }
    continue;
  }
  picked.push({ code: o.code, brand: o.manufacturer, description: o.description });
}

const seen = new Set();
const codes = picked
  .filter(c => { const k = `${c.brand ?? "GENERIC"}:${c.code}`; return !seen.has(k) && seen.add(k); })
  .sort((a, b) => (a.brand ?? "").localeCompare(b.brand ?? "") || a.code.localeCompare(b.code));

mkdirSync("out", { recursive: true });
writeFileSync("out/codes.json", JSON.stringify(codes, null, 1));
const brands = codes.filter(c => c.brand).length;
console.log(`generic: ${codes.length - brands}, brand-specific: ${brands} (${sameAsGeneric} standard codes folded into generic), total: ${codes.length}`);
