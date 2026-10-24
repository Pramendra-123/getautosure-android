# GetAutoSure — Project Notes (session handoff, 2026-10-19)

Written so a new Claude Code session (or a new person) can pick this up without the prior
conversation's history. The code itself is the real source of truth — read it before trusting
anything below, since it can drift out of date. This file is a map, not a spec.

## What this is

Two codebases:
- **`obd2-app`** (this repo) — Android app (Kotlin + Jetpack Compose), connects to an OBD2
  adapter, runs a rules engine over real ECU data, AI Mechanic chat, PDF reports, Dealer Sathi.
- **`getautosure-report/report-app`** — the marketing website (Next.js), separate repo.

## Backend (Supabase + Gemini)

- Supabase project ref: `xofkkvhkkbtcayibjaul`. Edge Function `ai-mechanic` at
  `supabase/functions/ai-mechanic/index.ts` — this is the ONLY feature that calls a real LLM
  (Gemini). Everything else (PDF report, health-report card) is deterministic/templated,
  deliberately — see `AiCarHealthReportService.kt`'s doc for why (API key can't live in the APK).
- Model: `gemini-3.6-flash` (not 3.8 — 3.8 hit repeated 503 "high demand" errors during real
  testing; 3.6 is the established, stable fallback). `thinkingLevel: "minimal"`,
  `maxOutputTokens: 2048`, `GEMINI_TIMEOUT_MS = 25_000` server-side,
  `RemoteAiMechanicChatService.DEFAULT_TIMEOUT_MILLIS = 32_000` client-side (server must always
  stay under client, with margin — see that constant's doc comment for the real observed
  latency numbers that drove these values).
- **Deploying the Edge Function**: the Supabase CLI never worked from this machine (a real,
  never-root-caused "Transport error" specific to the Go-compiled CLI binary on this Windows
  machine — WARP, GODEBUG, firewall, AppLocker, MOTW, proxy were all ruled out). Workaround:
  paste the full `index.ts` content into the Supabase Dashboard's Code editor for `ai-mechanic`
  and click Deploy. Manual line-edits there have drifted out of sync with the local file before
  — always paste the WHOLE file, never a partial edit, to avoid drift.
- Google Cloud project: `carbide-ward-511910-g8` — holds the Gemini API key and billing. Had to
  be moved onto the paid tier (not just "billing enabled") before Gemini calls worked at all —
  a ₹0 Cloud Prepay balance was the real root cause of a long 503/timeout debugging loop early on.
- `GOOGLE_WEB_CLIENT_ID` (used both in `GoogleSignIn.kt` and as a Supabase secret) must match
  exactly between the Android app and the Edge Function, or auth fails.
- System prompt (in `index.ts`) uses a trimmed structure for complex answers — What it means /
  Evidence / Possible causes / Urgency / Safe next steps / Questions to ask the mechanic /
  Limitations — capped at ~200 words, with an explicit "stay short for simple questions" escape
  hatch. This was a deliberate token/latency optimization, not just a formatting choice.

## Android-side AI Mechanic auth

- `RemoteAiMechanicChatService` needs `Authorization: Bearer <Google ID token>` on every call.
- The token is cached in-memory by `GetAutoSureViewModel` (decoding its own `exp` claim via
  `decodeJwtExpiry` in `GoogleSignIn.kt`) and only re-fetched via Credential Manager when it's
  actually near expiry — NOT fetched fresh per message. A real-device bug before this fix:
  fetching fresh every message triggered Credential Manager's account picker on every single
  chat message, because the fetch used an Application context when Credential Manager needs an
  Activity-rooted one. See `authUiContext`'s doc on `GetAutoSureViewModel` for why that field
  exists and why it's deliberately short-lived (never cached longer than one request).
- `FallbackAiMechanicChatService` retries once on a network blip or 5xx before falling back to
  the local rule-based answer, and every fallback now carries a visible, cause-specific notice
  (auth/rate-limited/generic) — a real-device bug before this: silent fallback answered
  completely unrelated questions with wrong-topic local guesses, which looked like a broken AI
  rather than an honest degradation.

## Local rule-based fallback — two real bugs fixed, worth knowing about

`RuleBasedAiMechanicChatService` / `GeneralAutomotiveKnowledge` keyword-match free text against
findings/signals/topics. Two real false-match bugs were found via real-device testing and fixed:
- A bare `"ac" in text` substring check matched inside "pl**ac**e" — fixed with a word-boundary
  regex, but ONLY for short (<=3 char) keywords; longer keywords deliberately keep substring
  matching (so "squeal" still matches "squealing" — that's a feature, not the bug).
- A bare `word.length > 3` check let common words like "with" (present in both a signal's name
  and almost any sentence) cause false matches — fixed with a stopword exclusion list
  (`COMMON_STOPWORDS` in `AiMechanicChatService.kt`).

If a new "this question got a bizarre, wrong-topic local answer" report comes in, check for a
similar short/common-word keyword collision before assuming something else is wrong.

## Repair cost reference (tentative, explicitly user-authorized to estimate)

`RepairCostReference.kt` — ~20 category ranges in INR, explicitly approximate/invented at the
user's request (different from the rest of the app's "never invent data about a specific
vehicle" rule — these are general category ranges, not claims about any one car). Wired into
the Repair Guidance screen (6-item preview + full list) and the local chat's cost-question
handling. Always keep the "tentative, not a real quote" framing visible wherever this shows.

## Dealer Sathi — in progress

- **Built and working**: dealer branding on the PDF report. `DealerProfileStore.kt` (local
  JSON, single-dealer, no backend — same pattern as `ScanHistoryStore`/`TripMileageStore`),
  `DealerProfileScreen.kt` (name/tagline/phone/location/logo), wired into
  `VehicleReportPdfGenerator.kt` — null `dealerProfile` means the exact same unbranded report
  every free user gets; only diverges once a dealer profile is actually saved.
- **Planned, not built**: RC (registration certificate) lookup via Surepass API — needs the
  user to set up a real paid Surepass account first, then a backend proxy (same pattern as
  `GEMINI_API_KEY` — never in the APK). Mockup already reviewed and approved: a
  "Registration & Ownership Details" page, official-RTO-data framing, with **owner name kept
  dealer-only** (left off the buyer-facing PDF) and **financier/loan status shown to the buyer**
  (a loan against the car is something a buyer should know before purchasing) — that split was
  an explicit decision, don't reverse it without asking.
- **Blocked on the user**: price benchmarking ("what to pay" / profit estimate) — the user is
  sourcing real pricing data from multiple dealers themselves, to be supplied later, updated a
  few times a year (same static-file pattern as `RepairCostReference`, not a live feed). Do not
  invent vehicle market values the way repair costs were tentatively estimated — different risk
  class, explicitly ruled out earlier in this project.
- **Not started**: "Ask a real expert" as a live/real feature (currently just a WhatsApp handoff
  link) — this was named as the other PRO feature to build, discussion hadn't started yet as of
  this handoff.

## Recent small fixes worth knowing the reasoning behind

- "Used Car Check" removed from the Home screen's grid (still reachable from Reports tab's
  Discover More, not deleted — just de-surfaced, by request).
- "Prepare PDF report" used to auto-launch `ACTION_VIEW` for the generated PDF, which (with no
  default PDF viewer set) shows Android's "Open with" app picker — visually near-identical to a
  share sheet, which read as "it shares immediately." Fixed: Prepare now only generates the
  file and reveals View/Save/Share buttons; no external app launches until one of those is
  tapped deliberately.

## Open/unresolved

- `obd2-app`'s git remote was pointing at the WEBSITE's repo
  (`github.com/Pramendra-123/getautosure-report.git`) — a real misconfiguration, not
  intentional. Mid-fix as of this handoff: user created a new repo for the Android app but
  hadn't yet supplied its URL. Check `git remote -v` in this repo before pushing anything.
- Supabase CLI still doesn't work from this machine (see above) — dashboard paste-and-deploy is
  the working method until/unless that's ever root-caused.
