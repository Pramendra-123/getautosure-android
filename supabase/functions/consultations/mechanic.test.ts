// Tests for mechanic.ts: schedule checks and India-time leave dates. Run with `deno test`.

import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { checkLeave, checkWeeklyHours, istDate, leaveDates, summariseCar } from "./mechanic.ts";

Deno.test("the car summary keeps brand, check-engine state and the first findings with their codes", () => {
  const ctx = {
    vehicleBrand: "Maruti",
    milOn: true,
    findings: [
      { title: "Engine misfire", relatedDtcCodes: ["P0301"] },
      { title: "Battery voltage low", relatedDtcCodes: [] },
      { summary: "no title, skipped" },
    ],
  };
  assertEquals(summariseCar(ctx), { brand: "Maruti", checkEngineOn: true, issues: ["Engine misfire (P0301)", "Battery voltage low"] });
  assertEquals(summariseCar(null), null);
});

Deno.test("a normal split schedule is accepted, and an empty one means no calls", () => {
  const hours = [
    { weekday: 1, start: "10:00", end: "13:00" },
    { weekday: 1, start: "17:00", end: "20:00" },
    { weekday: 6, start: "09:30", end: "12:00" },
  ];
  assertEquals(checkWeeklyHours(hours), { ok: true, value: hours });
  assertEquals(checkWeeklyHours([]), { ok: true, value: [] });
});

Deno.test("bad days, bad times, backwards and overlapping ranges are refused", () => {
  assertEquals(checkWeeklyHours([{ weekday: 0, start: "10:00", end: "11:00" }]).ok, false);
  assertEquals(checkWeeklyHours([{ weekday: 8, start: "10:00", end: "11:00" }]).ok, false);
  assertEquals(checkWeeklyHours([{ weekday: 1, start: "9:00", end: "11:00" }]).ok, false);
  assertEquals(checkWeeklyHours([{ weekday: 1, start: "24:00", end: "11:00" }]).ok, false);
  assertEquals(checkWeeklyHours([{ weekday: 1, start: "13:00", end: "10:00" }]).ok, false);
  assertEquals(
    checkWeeklyHours([{ weekday: 2, start: "10:00", end: "13:00" }, { weekday: 2, start: "12:30", end: "14:00" }]).ok,
    false,
  );
  assertEquals(checkWeeklyHours("10-13").ok, false);
});

Deno.test("back-to-back ranges on the same day are fine", () => {
  assertEquals(
    checkWeeklyHours([{ weekday: 3, start: "10:00", end: "13:00" }, { weekday: 3, start: "13:00", end: "14:00" }]).ok,
    true,
  );
});

const NOW = new Date("2026-11-10T05:00:00Z").getTime(); // 10:30 India time, 10 Nov

Deno.test("leave covers whole India days, through midnight after the last day", () => {
  assertEquals(checkLeave("2026-11-12", "2026-11-13", NOW), {
    ok: true,
    value: { startsAt: "2026-11-11T18:30:00.000Z", endsAt: "2026-11-13T18:30:00.000Z" },
  });
  // Today is allowed, so a mechanic can block the rest of a day they're unwell.
  assertEquals(checkLeave("2026-11-10", "2026-11-10", NOW).ok, true);
});

Deno.test("leave in the past, backwards, too long or malformed is refused", () => {
  assertEquals(checkLeave("2026-11-09", "2026-11-10", NOW).ok, false);
  assertEquals(checkLeave("2026-11-13", "2026-11-12", NOW).ok, false);
  assertEquals(checkLeave("2026-11-12", "2027-03-12", NOW).ok, false);
  assertEquals(checkLeave("12-11-2026", "13-11-2026", NOW).ok, false);
});

Deno.test("stored leave converts back to the dates the mechanic picked", () => {
  assertEquals(leaveDates("2026-11-11T18:30:00.000Z", "2026-11-13T18:30:00.000Z"), { startDate: "2026-11-12", endDate: "2026-11-13" });
  assertEquals(istDate("2026-11-10T19:00:00Z"), "2026-11-11");
});
