# GetAutoSure — Your AI Car Health Expert

Android app (Kotlin + Jetpack Compose) that connects to an OBD2 adapter,
reads the vehicle's actual ECU state, runs it through a deterministic rules
engine, and presents the result as a plain-language Car Health Report with
an AI Mechanic you can ask follow-up questions. Originally scaffolded as
"EngineSense" (a single-screen DTC reader); this is the layered rebuild
described below.

## Premium UI layer (on top of the 13-stage architecture)

A WHOOP/Oura-style presentation layer was added on top of the architecture
below, without changing any OBD/PID/DTC/rules-engine/transport code except
where explicitly noted:

- **`vehicle/HealthScore.kt` + `HealthScoreCalculator.kt`** (new, pure Kotlin,
  unit-tested) — turns the rules engine's existing findings into one 0-100
  score and five category states (Engine/Battery/Cooling/Emissions/
  Diagnostics). Categorizes DTC-based findings by the DTC code's own SAE
  J2012 subsystem digit (not by scanning prose text — see the "misfire
  mentions catalytic converter" test case for why that's unreliable), and
  marks a category `UNAVAILABLE` (never fabricated as "good") when the
  vehicle doesn't expose the PIDs it needs.
- **`vehicle/ai/AiMechanicChatService.kt`** — extended (not rewritten) with
  direct handlers for "is it safe to drive", "how urgent", "what should I
  ask the mechanic", and "how much could this cost" — the last one
  explicitly refuses to invent a number, since no repair-cost provider is
  connected.
- **`GetAutoSureViewModel.kt`** — restructured around a persistent
  `latestResult` + `history` (for multi-screen nav) instead of the earlier
  single-stage state machine. Same underlying scan pipeline (`PidReader` →
  `DiagnosticRulesEngine` → `AiCarHealthReportService`), just composed for
  more than one screen.
- **`ui/` package** — theme (dark, large score typography), reusable
  components (health ring, category row, severity badge), and one Compose
  screen per flow step: onboarding, add vehicle, connect, scanning, home,
  issues, issue detail, AI report, AI Mechanic, reports timeline, workshop
  and vehicle-info placeholders. Navigation via `navigation-compose`, bottom
  nav limited to the four tabs the spec asked for (Home/Scan/Reports/AI
  Mechanic) — detail screens are pushed on top, not exposed as extra tabs.
- **Car Health Timeline**: five demo scans (one per simulated profile, dated
  2/6/10/15/21 days ago) are seeded once on first launch via the *real*
  simulator + rules engine — not fabricated numbers — and clearly labeled
  "Demo data" in the UI. Real scans you run get prepended above them.

All 39 JVM unit tests pass (the original 27 plus 12 new ones covering the
health score calculator and the new AI Mechanic intents), and
`gradle :app:assembleDebug` produces a real APK — see "Running this on your
phone" at the bottom of this file.

## Architecture

```
OBD/raw ECU data → normalization → Vehicle Data Model → Rules Engine → AI layer → UI
```

The rules engine is the source of truth. The AI layer only ever *phrases*
what the rules engine already decided — it cannot invent a finding, a
sensor value, or a diagnosis that isn't already in the validated context it's
handed. See `vehicle/ai/AiContracts.kt` for the exact boundary.

| Layer | File(s) | What it does |
|---|---|---|
| Transport | `obd/ObdTransport.kt`, `obd/Elm327Client.kt`, `obd/SimulatedObdTransport.kt` | Hardware-agnostic interface. `Elm327Client` (Bluetooth SPP) and `SimulatedObdTransport` (test mode) are interchangeable; a new OBD adapter becomes a third implementation with zero changes above this line. |
| Normalization | `obd/ObdResponseParser.kt` | Turns raw adapter text into `Ok(bytes)` / `NoData` / `Unsupported` / `AdapterError` — one place that understands ELM327 text quirks. |
| PID registry & decode | `vehicle/PidDefinitions.kt` | One `PidId` enum entry per supported PID: command string, decoder, plausible range. Adding a PID means adding one entry here. |
| DTC decode | `obd/DtcDecoder.kt` | Shared SAE J1979 byte-pair decoder; `Elm327Client.parseDtcResponse` delegates to it instead of duplicating the bit math. |
| Supported/verified/unsupported | `vehicle/PidReader.kt`, `vehicle/VehicleDataModel.kt` (`PidStatus`) | Implements the real SAE J1979 "supported PIDs" bitmask discovery (Mode 01 PID 00/20/40...) so an unsupported PID reads as `UNSUPPORTED`, not a mysterious null. |
| Vehicle Data Model | `vehicle/VehicleDataModel.kt` | `VehicleSnapshot` — the single validated structure everything downstream consumes. |
| Rules Engine | `vehicle/DiagnosticRulesEngine.kt` | Pure Kotlin, no Android dependency, fully unit-testable. Produces DTC-based findings (via `DtcRepository`'s explanation) plus new live-threshold findings (overheating, low/high battery voltage) that a stored DTC alone wouldn't catch. |
| AI input contract | `vehicle/ai/AiContracts.kt` | `AiVehicleContext` — the closed, serializable set of signals/findings the AI is allowed to see. |
| AI Car Health Report | `vehicle/ai/AiCarHealthReportService.kt` | `TemplateAiCarHealthReportService` (default, safe, no LLM call) + `RemoteAiCarHealthReportService` (documented stub — see "AI layer" below). |
| AI Mechanic chat | `vehicle/ai/AiMechanicChatService.kt` | `RuleBasedAiMechanicChatService` (default) answers only from the context or says "I don't have data on that" — same pattern as the report service. |
| End-to-end UI | `GetAutoSureViewModel.kt`, `MainActivity.kt` | Connect → Scan → Report → AI Mechanic → Take Action. Functional, not polished (per spec — visual design comes after the new OBD hardware is validated). |
| Mechanic/workshop integration | `vehicle/integrations/WorkshopIntegration.kt` | Interface + placeholder. Returns a visibly-labeled "not connected" result rather than fake workshop data. |
| Vehicle info/challan | `vehicle/integrations/VehicleInfoService.kt` | Interface + placeholder for the RC/challan/insurance lookup discussed earlier (Surepass/Signzy/Sandbox APIs). Same "don't fabricate data" rule. |
| Simulated ECU / test mode | `obd/SimulatedObdTransport.kt` | Five canned profiles (`HEALTHY`, `MISFIRE_DTC`, `OVERHEATING`, `LOW_BATTERY_VOLTAGE`, `VOLTAGE_PID_UNSUPPORTED`) that exercise the full pipeline without any hardware. "Run in test mode" on the Connect screen uses this. |

### Why the AI layer doesn't call a real LLM yet

Calling Anthropic/OpenAI/etc. directly from the Android client means
embedding an API key in the APK — trivially extractable by decompiling the
app. That's a real security issue, not a style preference, so it's not
implemented. `TemplateAiCarHealthReportService` and
`RuleBasedAiMechanicChatService` are the **production-safe default**, not a
placeholder to feel bad about: they're deterministic, can't hallucinate, and
work fully offline. `RemoteAiCarHealthReportService` /
`RemoteAiMechanicChatService` are documented stubs for when a backend proxy
endpoint exists (e.g. a Supabase Edge Function holding the key server-side,
mirroring the `inspectx-bot` backend from the GetAutosure web project) —
wiring one up is a product/cost decision (which model, rate limits), not
something to improvise silently.

## Hardware-agnostic by design

Everything above `ObdTransport` only ever calls `connect()` / `sendCommand()`
/ `close()`. The new OBD hardware arriving shortly becomes a new class
implementing that interface — nothing in `vehicle/` or the UI needs to
change. `Elm327Client` (the existing Bluetooth SPP transport) was touched
only to add `: ObdTransport` and three `override` keywords; its connection
logic, AT init sequence, and SDP-fallback handling are untouched.

## Testing without hardware

`app/src/test/java/com/enginesense/app/` has JVM unit tests (JUnit, no
Android instrumentation needed) covering:
- `ObdResponseParserTest` — adapter text normalization edge cases (NO DATA, `?`, SEARCHING..., malformed text).
- `DtcDecoderTest` — the SAE J1979 byte-pair math, including the textbook P0133 example and all four letter prefixes.
- `PidReaderSimulatedTest` — the simulator driving the real `PidReader` end-to-end: verified readings, an out-of-range-but-still-decodable overheating value, and the `UNSUPPORTED` path via the `VOLTAGE_PID_UNSUPPORTED` profile.
- `DiagnosticRulesEngineTest` — live-threshold findings, DTC findings reusing the caller's explanation (not inventing one), implausible readings correctly producing *no* finding, and severity sorting.
- `AiVehicleContextTest` — the AI contract carries every signal's status through untouched.
- `MockAiServicesTest` — the template report and rule-based chat never say anything not traceable to the context.

Run them with `gradle :app:testDebugUnitTest` (see Setup below for the
toolchain this was actually verified against).

## Indian manufacturer DTC coverage

(Unchanged from the earlier EngineSense work — still applies on top of the new architecture.)

The bundled free `dtc-database-android` module has **no entries for Hyundai,
Tata, or Mahindra**, and only 17 codes for Suzuki. `app/src/main/assets/india_override_codes.json`
is a hand-compiled table `DtcRepository` checks *before* falling back to the
vendored database:

| Manufacturer | Codes added | Source |
|---|---|---|
| Tata | ~150 (mostly Bosch diesel-EMS P0/P1 codes) | [obdcode.org/tata](https://obdcode.org/tata/) |
| Mahindra | ~78 P1xxx codes | [en.avtotachki.com/codes-mahindra](https://en.avtotachki.com/codes-mahindra/) (translated from Russian) |
| Suzuki (Maruti proxy) | 13 additional P1xxx codes | [en.avtotachki.com/codes-suzuki](https://en.avtotachki.com/codes-suzuki/) |

Manually compiled from public reference sites, not a licensed/live feed —
**Hyundai is still uncovered** (no structured public powertrain-code source
found). `obd/UnresolvedCodeLogger.kt` logs codes that fall through
unexplained (code + manufacturer + timestamp, no PII) so real usage data can
prioritize what to source next; the old single-screen UI exposed a "Share
log" button for this — it's not wired into the new flow yet (see Known gaps).

**Product-safety note**: a Team-BHP user reported a generic OBD2 scanner
triggering false ESC/TPMS/parking-sensor warnings on a Mahindra XUV300 just
from connecting. Worth surfacing a "read-only, generic PIDs only" warning for
specific models before this goes further.

## What's built (this session)

All 13 items from the architecture spec:
1. Vehicle Data Model (`VehicleDataModel.kt`)
2. Raw OBD response normalization (`ObdResponseParser.kt`)
3. PID definitions and decoders (`PidDefinitions.kt`)
4. DTC decoding, shared (`DtcDecoder.kt`)
5. Supported/verified/unavailable/unsupported states (`PidStatus`, `PidReader.kt`)
6. Diagnostic/Rules Engine (`DiagnosticRulesEngine.kt`)
7. AI input contract/schema (`AiContracts.kt`)
8. AI Car Health Report generation (`AiCarHealthReportService.kt`)
9. AI Mechanic conversation layer (`AiMechanicChatService.kt`)
10. Minimal end-to-end UI (`GetAutoSureViewModel.kt`, `MainActivity.kt`)
11. Mechanic/workshop integration placeholder (`WorkshopIntegration.kt`)
12. Vehicle info/challan module placeholder (`VehicleInfoService.kt`)
13. Simulated ECU/test mode (`SimulatedObdTransport.kt`)

## Known gaps / what's still dependent on the new OBD hardware

- **Not tested against a real adapter or vehicle.** Everything above is
  verified via `gradle compileDebugKotlin` + the JVM unit test suite (listed
  above) against the simulator, which is the most testing possible without
  hardware. The real ELM327 Bluetooth path (`Elm327Client`) has not changed
  behaviorally, but it also hasn't been re-tested against a physical dongle
  in this session.
- **The new OBD hardware's actual command set is unknown.** If it isn't
  ELM327-AT-command-compatible, it needs its own `ObdTransport`
  implementation (same interface, new class) — can't be designed further
  until the hardware/its datasheet is in hand.
- **`ScanViewModel.kt` and the original `EngineSenseScreen`/`DtcCard`
  composables are now fully unused** — the premium UI (`GetAutoSureViewModel`
  + the `ui/` package) replaced them as `MainActivity`'s entry point. Left in
  place rather than deleted, in case anything there is still wanted; the
  "unresolved code log / Share log" feature that lived in the old UI isn't
  exposed anywhere in the new flow yet.
- **"Take Action" → workshop lookup and the vehicle-info/challan module are
  interfaces + placeholders only**, as specified. No real provider is
  connected.
- **AI layer is template/rule-based, not a real LLM**, deliberately — see
  "Why the AI layer doesn't call a real LLM yet" above. Needs a backend
  decision before going further.
- **No app icon / further visual polish** — intentionally deferred per this
  phase's own instructions ("focus on functional flow rather than visual
  polish... we will refine the design after the new OBD is validated").
- **Not run on an emulator or physical device in this session** — this
  sandbox has no display/emulator. Verified via `compileDebugKotlin`,
  39/39 JVM unit tests, and a successful `assembleDebug` producing a real
  APK — but actual on-screen rendering, navigation transitions, and
  Bluetooth permission prompts have not been visually confirmed. Real
  on-device testing is the next step — see below.

## Setup / toolchain actually used this session

Unlike the original EngineSense scaffolding (done blind, no SDK on the
machine), this was **built and verified with a real toolchain**:
Android Studio's bundled JBR (JDK 25) as `JAVA_HOME`, Gradle 9.0.0, the
Android SDK installed alongside Android Studio.

One environment-specific note if you rebuild this elsewhere: this sandbox
sits behind a TLS-inspecting gateway whose root CA isn't in the JDK's
default `cacerts` (only in the OS trust store), which breaks Gradle's
dependency resolution with `PKIX path building failed` until that root is
imported into a JDK-readable truststore. Android Studio's own bundled Gradle
likely won't hit this on your machine — it only showed up because this
session drove Gradle directly from the command line.

To run it yourself in Android Studio:
1. Open the `obd2-app/` folder (Hedgehog/2023.1.1+). It'll fetch its own
   Gradle wrapper jar on first sync.
2. Pair an ELM327 dongle in system Bluetooth settings, **or** just tap "Run
   in test mode" on the Connect screen — no hardware needed to see the full
   flow end-to-end.
3. `gradle :app:testDebugUnitTest` runs the unit test suite above.

## Running this on your Android phone

A debug APK already exists at
`app/build/outputs/apk/debug/app-debug.apk` (built and verified this
session). Two ways to get it onto your phone:

**Option A — Android Studio (recommended, lets you see logs if anything goes wrong)**
1. On your phone: Settings → About phone → tap "Build number" 7 times to
   enable Developer Options, then Settings → Developer options → turn on
   **USB debugging**.
2. Connect the phone to this PC with a USB cable. Allow the "Allow USB
   debugging?" prompt that appears on the phone.
3. Open `obd2-app/` in Android Studio, let Gradle sync, then pick your
   phone from the device dropdown (top toolbar) and click **Run ▶**.

**Option B — install the existing APK directly via adb, no Android Studio needed**
1. Enable Developer Options + USB debugging as in step 1 above.
2. Connect the phone via USB and allow the debugging prompt.
3. From a terminal with `adb` on PATH (it ships with the Android SDK —
   on this machine that's `C:\Users\<you>\AppData\Local\Android\Sdk\platform-tools\adb.exe`):
   ```
   adb devices
   ```
   confirm your phone shows up as `device` (not `unauthorized` — if so, check the phone screen for the prompt).
4. Install it:
   ```
   adb install "C:\Users\18314\Desktop\Code\obd2-app\app\build\outputs\apk\debug\app-debug.apk"
   ```
5. Open **GetAutoSure** from the app drawer. Grant the Bluetooth permission prompt.

### Trying every test scenario

Once the app is open:
1. Tap through Onboarding → "Get started" → fill in (or skip) the vehicle
   nickname/brand → Continue.
2. On the Connect screen, tap **"Run in test mode (no hardware)"**.
3. Pick a profile and watch the full pipeline run for real — PID reads, DTC
   decode, rules engine, health score, AI report:
   - **Healthy car** → score 100, all categories green, "No issues detected".
   - **Cylinder misfire (DTC)** → Engine category red, one critical issue (P0301).
   - **Overheating engine** → Cooling category red, critical live-threshold finding (no DTC needed — this one's caught from the live coolant reading alone).
   - **Low battery voltage** → Battery category yellow, moderate finding.
   - **Older car (voltage PID unsupported)** → Battery category shows gray "No data" — confirms unsupported PIDs are communicated honestly, never shown as fake-good or fake-bad.
4. From the result, try **"View issues"**, tap into an issue for the full detail (what/why/action/urgency), then **"Open AI Car Health Report"**.
5. Tap **"Ask the AI Mechanic"** and either use the suggested-question chips or type your own — try "Is my car safe to drive?", "How urgent is this?", and "How much could this cost?" (the last one should explicitly say it doesn't have real cost data, not invent a number).
6. Use the bottom nav to check **Reports** (five demo entries pre-seeded, labeled "Demo", plus your own test-mode runs stacking on top as you do more scans) and jump back to **Home**.
7. Repeat step 2-3 with a different profile each time to see all five scenarios.
