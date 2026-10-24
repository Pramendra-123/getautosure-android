# Premium backend — scoping

One backend, built once, serving two needs: real-AI entitlement for GetAutoSure
Premium, and Dealer Pro accounts/reports. Built on the Supabase project the
website (`getautosure-report/report-app`) already uses — not a new stack.

## Why one backend, not two

Both needs boil down to the same question — "is this user allowed to do X
right now" — backed by the same three things: identity, a subscription
record, and billing verification. Building it twice means two places that can
drift out of sync. And Dealer Pro reports can live in the *same* Supabase
tables that already power dealer-branded WhatsApp inspection reports — an OBD
scan becomes a second way to create a report in that system, not a parallel
product.

## Components

### 1. Identity — Supabase Auth
The Android app has zero accounts today (fully local, by design, until now).
Recommend **phone-number OTP via Supabase Auth** — consistent with
GetAutoSure's WhatsApp-native identity (dealers already identify by phone),
and no Google account dependency.

### 2. Entitlement — `subscriptions` table
`user_id, tier (free | premium | dealer), status (active | expired |
cancelled), renews_at, source (google_play | manual)`. Kept in sync by Google
Play's real-time developer notifications → a Supabase Edge Function webhook →
updates this table. The app reads *this* table for "is the user Premium right
now" — never trusts a client-side flag alone, since that's spoofable.

### 3. Billing — Google Play Billing Library
Android side integrates Play Billing for a subscription product (₹99/mo,
₹799/yr, already on the Pro card mockup). Server side verifies the purchase
receipt via the Google Play Developer API before the webhook marks a user
Premium.

### 4. AI proxy — Supabase Edge Function
The Android app never talks to the AI provider directly — an embedded API key
in the APK can be extracted in minutes. Flow: app calls a Supabase Edge
Function → function checks the caller's entitlement → if Premium, calls the
AI provider with the key stored as a Supabase secret → returns the response.
Log every call (`user_id`, timestamp, token count) and enforce a monthly cap
even for Premium users, so one chatty user's API bill doesn't eat the
subscription revenue from everyone else. The free tier's AI Mechanic stays
exactly as it is today — local, rule-based, zero cost, no network call.

### 5. Dealer Pro — data model
Reuses `report-app`'s existing Supabase project. New tables: `dealer_vehicles`
(dealer_id, vehicle info, linked OBD scan payload), extending the existing
report schema so an OBD-sourced scan renders through the same `[slug]`
report page dealers already share on WhatsApp. Dealer auth is the same
Supabase Auth, with `tier = "dealer"` in the subscriptions table.

## Not decided yet (separate calls, not blocking this scope)
- Dealer Pro's price point (likely different from consumer Premium's ₹99/mo)
- Exact AI provider/model and the real per-message cost, which determines
  what the monthly usage cap should actually be — needs a cost pass before
  picking a number, not a guess
- Whether free users get phone auth too (e.g. to sync scan history across
  devices) or stay fully local — recommend staying local for free, and only
  prompting auth at the point someone upgrades

## Suggested build order
1. Supabase Auth (phone OTP) in the Android app — everything else depends on this
2. `subscriptions` table + Play Billing integration + webhook sync
3. AI Edge Function proxy + usage metering
4. Dealer Pro tables + report generation reusing `report-app`

Each step is a real chunk of work on its own — this is a multi-week
initiative, not a quick add on top of the local-only app built so far.
