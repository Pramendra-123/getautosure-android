# GetAutoSure — Free/Pro Readiness Table & Subscription Architecture Proposal

**Status: proposal only. No billing code written. Per instruction, this is presented for review before any subscription implementation begins.**

Verified this session via code reading + build/test, not assumed. "Status" reflects what the code actually does today.

## Free tier — readiness table

| Feature | Status | Backend/account needed? | Data storage | Test coverage | Remaining work |
|---|---|---|---|---|---|
| OBD scan (connect, read PIDs, DTCs) | ✅ Working end-to-end | No | Local only | `PidReaderSimulatedTest`, `ObdResponseParserTest`, `DtcDecoderTest`, `Mode09ReaderTest` | None for this scope |
| Health summary (score + categories) | ✅ Working — **null-score defect fixed this session** | No | Local | `HealthScoreCalculatorTest` (16 tests) | None for this scope |
| Plain-language DTC explanations (codes actually found) | ✅ Working | No | Local (vendored `dtc-database-android`) | **Gap: no direct unit test file for `DtcRepository` itself found** | Add `DtcRepositoryTest` |
| AI Mechanic (current-scan + general-code fallback) | ✅ Working, rule-based — **P0301-class bug fixed this session** | No | Local | `MockAiServicesTest`, new `CommonDtcGlossaryTest` | Glossary covers ~18 common codes; expand as real customer questions surface |
| Sensor readings / Live Data | ✅ Working | No | Local | `PidReaderSimulatedTest` | None |
| Emissions readiness & MIL status | ✅ Working | No | Local | `ReadinessDecoderTest` (8 tests) | None |
| Scan history (recent) | ✅ Working, unlimited per earlier decision | No | Local JSON file | `ScanHistoryStoreTest` | Lost on uninstall/app-data-clear — acceptable for free tier, worth disclosing to users |
| Basic maintenance reminders | ❌ **Not implemented** — no reminder/scheduling feature exists in the codebase | N/A | N/A | N/A | Full build needed if this stays in the free-tier promise |

## Pro tier — readiness table

| Feature | Status | Backend/account needed? | Data storage/privacy | Test coverage | Remaining work |
|---|---|---|---|---|---|
| **A. Advanced AI Mechanic** | ⚠️ Partial — UI exists, explicitly labeled "answers the same way as Free" today | **Yes** — needs a server holding the LLM key (`RemoteAiMechanicChatService` already stubbed, throws `NotImplementedError` on purpose) | Would need scan history leaving the device (privacy disclosure required) | None (nothing to test yet) | Real backend + multi-scan reasoning — largest remaining Pro item |
| **B. Ask a Real Expert** | ⚠️ Partial — real WhatsApp deep-link works today | No backend for MVP (manual WhatsApp); booking/limits need one for real enforcement | N/A for MVP | None (UI only) | **Blocking: placeholder phone number must not ship as "live"**; define consultation limits |
| **C. Car Health History & Early Warnings** — Issue Forecast | ✅ Working, confirmed wired to real analyzer this session (`IssueForecastAnalyzer`, 6 tests) | No for current scope; cloud sync needed for cross-device/reinstall history | Local only — lost on uninstall | `IssueForecastAnalyzerTest` | Cloud sync if "history survives reinstall" is promised |
| — Detailed Repair Diagnosis | ✅ Working, confirmed wired this session (`EngineDiagnosisAnalyzer`, `CoolingDiagnosisAnalyzer`) | No | Local | `FuelCombustionAnalyzerTest` | None for current scope |
| — Emissions Trend History | ✅ Working, confirmed wired this session (reads real readiness-monitor history) | No | Local | **Gap: no dedicated analyzer test file found** | Add test coverage |
| — Advanced Live Data (logging/graphs) | ❌ Not implemented — routes to generic Pro teaser | Yes, for export | Local | None | Full build |
| — Repair Cost Guidance | ✅ Working, deliberately general/non-per-model by design (not a gap — a scoping decision) | No | N/A | None found | None planned — scope is intentional |
| Multi-Car Garage | ❌ Not implemented — generic Pro teaser | Yes (per-vehicle data model) | Local, needs schema change | None | Full build |
| Advanced Reports (PDF export) | ❌ Not implemented — generic Pro teaser | No (can be local PDF render) | Local | None | Full build |

## Known placeholders — classified (Phase 5)

Scale: **1** Working end-to-end · **2** Partially implemented · **3** UI-only placeholder · **4** Blocked by config/external dependency · **5** Not implemented

| Feature | Class | Evidence |
|---|---|---|
| Fair Value Estimate | **1 (rebuilt this session)** | Was class 3 (static placeholder; an earlier formula failed validation) → rebuilt on IRDAI's published standard motor depreciation schedule (age + a small bounded odometer adjustment), user supplies ex-showroom price since there's no price catalog. Shown as a range, explicitly labeled a rough estimate, with outbound links to real valuation tools for a second opinion |
| Warning Light Guide | **1 (fixed this session)** | Was class 4 (website not deployed — verified live) → rebuilt natively, no longer depends on the website |
| Learning Hub | 4 | `getautosure.com/learn` verified live this session: falls through to the dynamic `[slug]` report route and hangs on "Loading report...", not even a clean 404 |
| Ask a Real Expert | 2 | Real WhatsApp intent works; phone number is explicitly flagged in code as placeholder |
| Advanced AI Mechanic | 2 | Real screen, banner honestly discloses it behaves like free today |
| Pro / Dealer subscription screens | 3 | Explicit "not a real subscription yet" copy; no billing |
| Multi-Car Garage | 5 | Routes to generic teaser, no implementation |
| Advanced Live Data | 5 | Routes to generic teaser, no implementation |
| Advanced Reports | 5 | Routes to generic teaser, no implementation |
| Issue Forecast | **1** | Verified this session: real analyzer, real history data, real tests |
| Detailed Diagnosis | **1** | Verified this session: real analyzers, real tests |
| Emissions Trend | **1** | Verified this session: reads real readiness-monitor history; missing dedicated test file (gap, not a placeholder) |
| Vehicle photographs | 3 | Generic icon only; no image API wired in (you mentioned providing one later) |
| Google Sign-In | 4 | Needs a real Firebase/Google Cloud Web Client ID; has an explicit "Skip for now" bypass |

## Subscription architecture proposal (not implemented)

**Principle carried through every option below: offline basic scanning must never depend on an account, a subscription, or network access.** All Pro-gating happens as an *additional* check layered on top of, never inside, the existing local OBD pipeline.

1. **Accounts & cloud scan history** — Supabase Auth (phone OTP, matching the report-app's existing pattern) + a `scans` table keyed by user ID, synced opportunistically when online. Local `ScanHistoryStore` stays the source of truth offline; cloud is a mirror, not a dependency.
2. **Secure AI backend** — a thin Edge Function (Supabase or Cloudflare Worker) that holds the real LLM API key server-side and proxies Advanced AI Mechanic requests with the same `AiVehicleContext` JSON contract already built (`AiContracts.kt` already serializes to JSON — this is most of the client-side work already done). Never embed the key in the APK.
3. **Usage metering & limits** — a `usage_events` table (user ID, feature, timestamp); entitlement check reads a count against the plan's limit before allowing an Advanced AI Mechanic call or expert consultation booking.
4. **Google Play Billing** — standard `BillingClient` integration; purchase tokens validated server-side (never trust a client-reported "purchase succeeded").
5. **Server-side purchase validation & entitlement** — a `subscriptions` table (user ID, product ID, expiry, Play purchase token) verified against Google's Play Developer API on the server, not just acknowledged client-side.
6. **Restore purchases** — standard Play Billing query-purchases flow on app start, reconciled against the server's `subscriptions` table.
7. **Expert consultation booking** — a `consultations` table (user ID, timestamp, status, linked scan ID) with a usage-limit check before generating the WhatsApp deep-link with real context attached.

**Explicitly not decided / flagged for your call:**
- ₹999/year and ₹149/month are hypotheses to validate, not approved pricing — nothing should hard-code these.
- Exact Advanced AI Mechanic usage limits (N queries/month?) — undecided.
- Expert consultation limits (N/month, call vs. WhatsApp only?) — undecided.
- Whether Dealer Pro reuses this same subscription system or stays a separate B2B flow (current code keeps it separate; `DealerScreen` untouched this session).

**Do not start building any of this until you've reviewed and confirmed the above** — per your explicit instruction, nothing beyond this proposal has been implemented.
