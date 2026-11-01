// Tests for the public-read masking in index.ts — the part that decides what a stranger opening a
// report link can see. Run with `deno test` from this directory.

import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { maskRtoInHealthSummary } from "./index.ts";

Deno.test("the RTO block's registration number is masked in both places, everything else untouched", () => {
  const summary = {
    score: 90,
    rto: {
      registrationNumber: "MH12AB1234",
      vehicle: [
        { label: "Registration", value: "MH12AB1234", concern: false },
        { label: "Fuel", value: "PETROL", concern: false },
      ],
      ownership: [{ label: "Owners so far", value: "2nd owner", concern: false }],
    },
  };
  const masked = maskRtoInHealthSummary(summary) as typeof summary;
  assertEquals(masked.rto.registrationNumber, "MH 12 * * * 1234");
  assertEquals(masked.rto.vehicle[0].value, "MH 12 * * * 1234");
  assertEquals(masked.rto.vehicle[1].value, "PETROL");
  assertEquals(masked.rto.ownership, summary.rto.ownership);
  assertEquals(masked.score, 90);
});

Deno.test("a summary without an RTO block passes through unchanged", () => {
  const summary = { score: 80 };
  assertEquals(maskRtoInHealthSummary(summary), summary);
  assertEquals(maskRtoInHealthSummary(null), null);
});
