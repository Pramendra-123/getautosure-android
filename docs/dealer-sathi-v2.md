# Dealer Sathi v2 — final spec

Agreed with the founder on 4 Nov 2026. Samples: `brand/reports/dealer-report-v2-sample.pdf` (report)
and `brand/reports/dealer-status-sample.png` (WhatsApp Status image). Everything in the samples is
made-up data.

## Status

Phase 1 (built 4 Nov 2026): entry screen, the 4-step flow (`DealerFlowScreen.kt`), dealer profile
with second phone and "Use my location", the PDF (`DealerSaleReportPdfGenerator.kt`) and the Status
image with share to WhatsApp (`DealerStatusImage.kt`). The charge is a placeholder
(`DEALER_REPORT_CHARGE`) and nothing is charged yet. The Status image has no link or QR yet — its
orange box says "Ask for the full health report" until the verify page exists.

Premium page (built 4 Nov 2026, `DealerSaathiScreens.kt`, sample
`brand/dealer-saathi/premium-page-sample.png`): the name shown is now "Dealer Saathi". Hero, the three
outputs with tap-to-open samples, Gemini before/after, a video slot (no video yet), packs of 1 / 5 / 30
cars at ₹29 / ₹99 / ₹499 (introductory prices; `DEALER_PACKS`), what's included and not, and a
checkout screen. Payments aren't live: "Pay" says so, charges nothing, and reports stay free. More tools
and Coming soon were removed from this page. The Status image shows the dealer's price as an orange pill
when they add one.

Phase 2: verify page + QR codes, payments, dealer profile saved to the account.

## Who it's for

A used-car dealer, **after refurbishing a car**, makes one report to send to buyers and broadcast
(WhatsApp chats, broadcast lists, Status). It is not a "should I buy this car" check — that's Used Car
Check. The report speaks to the dealer's customer, never as buyer advice.

## Entry screen

- Charges shown upfront: **₹___ per report** (amount still to be decided). Scanning and the RTO
  check are free; the charge is taken before the PDF is made.
- "Not a dealer, buying a car? Use Used Car Check instead →" so regular customers don't wander in.
- Dealer profile shown with an edit link. "More tools" (web report, buy-price calculator) as a small
  link at the bottom.
- One flow only. The separate "Dealer report" and "Dealer report with photos" buttons go.

## The flow (4 steps, then share)

1. **Scan** — the same round blinking Scan button as Home.
2. **Car number** — RTO record appears and is matched to the scan. Once the record is found the
   field loses focus and the keyboard closes (no blinking cursor left behind).
3. **Physical inspection** — the dealer taps 1–5 stars for: Exterior and paint, Interior and seats,
   Tyres, AC, Brakes and suspension, Test drive. Any can be skipped. Tick-boxes: Accident-free,
   Service records, Spare key, Single owner. One optional free-text note.
4. **Photos** — front photo **required** (it's the cover of the PDF and the Status image); side,
   rear, interior, dashboard, boot optional. Backgrounds cleaned to white by Gemini, labelled
   "Background cleaned · car not edited".

Then: optional "Add your price" (small, collapsed), pay, make PDF, share.

Not in the flow: "fix before selling", "work done by the dealer", the price estimate box
(ex-showroom, ±₹10k), fair value.

## Dealer profile (set once)

Logo, name, tagline, **two phone numbers**, address with a **"Use my location"** button (GPS fills
the address and saves the map point). Saved once and used on every report. Today it's stored only
on the phone; it should be saved to the dealer's account so it survives a reinstall or new phone.

## The PDF

Page 1
- Dealer header: logo, name, tagline, both phones; address with "Open in Google Maps".
- "Vehicle health report", car name, then one line: number · year · fuel · colour · km
  (the registration appears once, not 2–3 times).
- Front photo next to the health score.
- Summary tiles: faults found, red flags, self-tests, dealer's star rating.
- **RTO record first**: owner number, registered, manufactured, fuel/colour, insurance, PUC, loan
  (hypothecation), fitness/road tax, blacklist — whatever the RTO lookup returns.
- **Papers checklist**: ✓ insurance, ✓ PUC, ✓ fitness, ✓ tax, ✓ no loan.

Page 2
- Scan results: each system's score, fault codes (stored, pending, hidden) with tentative repair
  cost ranges when found.
- **Engine readings at the scan**, each with its normal range and a "Normal" tick.
- **Self-tests** listed by name, complete or not.
- **Odometer check**: dashboard vs engine computer km, distance since codes were cleared, shown as a
  badge ("No signs of odometer tampering"). Shown honestly even when codes were just cleared.
- Dealer's inspection — "Rated by <dealer>, not checked by GetAutoSure".
- Photos.
- Price — only if the dealer added it.
- **QR code + "Verify this report" link** to the online copy, so a PDF can't be faked.
- Footer: report ID, scan date/time, "Scan, RTO record and repair costs by GetAutoSure. Inspection,
  photos and price by the dealer."

Removed from today's report: "Good buy / Buy with caution" verdict, "Before you pay" checklist,
"Worth asking the seller", red-flag text aimed at buyers ("hidden from you"), fair value and offer
range.

## WhatsApp Status image (1080×1920)

Black background, white text. Top to bottom: dealer logo, name, area; "✓ Scanned & verified · date";
the dealer's front photo in a white card (Gemini-cleaned); car name; one line
"year · fuel · km · owner · **KA02**" (RTO code only — the first four characters of the number, the
rest never shown); four badges (health score, Odometer OK, No faults, Papers OK); orange box with the
dealer's phone, "Full report: getautosure.in/r/<id>" and a QR code; "Report by GetAutoSure".

"Share to WhatsApp Status" opens WhatsApp's share sheet with the image and copies the report link
for the caption (Android can't post to Status directly; the dealer taps "My status"). The PDF is for
chats and broadcast lists — WhatsApp Status doesn't take PDFs.

## Later

- **Live web report, slightly interactive** (the PDF stays the standard sharing format): the buyer
  can expand each diagnostic finding, see the original scan date, verify the report's QR code, and
  see whether a newer scan of the car exists. This gives dealers a reason to keep re-scanning with
  GetAutoSure instead of just downloading a PDF. Depends on the verify page below.

- 15–20 s Status video from the photos and badges.
- Dealer's promises (warranty, free service, RC transfer).
- Walkaround video link as a QR.
- "✓ Confirmed by re-scan" for fixes the scanner can see.
- Challan and service history (need paid outside data).

## Still to decide

- The per-report charge, and how it's paid.
- The online "verify this report" page (`getautosure.in/r/<id>`): where it's hosted. The QR, the
  Status link and the verify feature all depend on it.
