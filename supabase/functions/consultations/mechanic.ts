// Mechanic mode's pure parts: checking a weekly schedule the app sends, and converting leave
// dates (India time, whole days) to and from timestamps. The handlers that use these live in
// index.ts.

export interface WeeklyHours {
  weekday: number; // ISO: 1 = Monday ... 7 = Sunday
  start: string; // "HH:MM", India time
  end: string;
}

export type Checked<T> = { ok: true; value: T } | { ok: false; message: string };

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const MAX_RANGES_PER_DAY = 4;
const MAX_LEAVE_DAYS = 90;
const IST_OFFSET_MS = 5.5 * 60 * 60 * 1000;

/** Accepts the schedule only if every range is a real, non-overlapping time window. An empty list means "not taking calls". */
export function checkWeeklyHours(input: unknown): Checked<WeeklyHours[]> {
  if (!Array.isArray(input)) return { ok: false, message: "hours must be a list" };
  const hours: WeeklyHours[] = [];
  for (const item of input) {
    const h = item as Record<string, unknown>;
    if (typeof h?.weekday !== "number" || !Number.isInteger(h.weekday) || h.weekday < 1 || h.weekday > 7) {
      return { ok: false, message: "Each day must be Monday to Sunday" };
    }
    if (typeof h.start !== "string" || typeof h.end !== "string" || !TIME.test(h.start) || !TIME.test(h.end)) {
      return { ok: false, message: "Times must look like 10:00" };
    }
    if (h.start >= h.end) return { ok: false, message: `${h.start}–${h.end}: the end must be after the start` };
    hours.push({ weekday: h.weekday, start: h.start, end: h.end });
  }
  for (let day = 1; day <= 7; day++) {
    const ranges = hours.filter((h) => h.weekday === day).sort((a, b) => a.start.localeCompare(b.start));
    if (ranges.length > MAX_RANGES_PER_DAY) return { ok: false, message: `At most ${MAX_RANGES_PER_DAY} time ranges per day` };
    for (let i = 1; i < ranges.length; i++) {
      if (ranges[i].start < ranges[i - 1].end) {
        return { ok: false, message: `${ranges[i - 1].start}–${ranges[i - 1].end} and ${ranges[i].start}–${ranges[i].end} overlap` };
      }
    }
  }
  return { ok: true, value: hours };
}

/** Midnight India time at the start of [date] ("YYYY-MM-DD"), as an ISO timestamp. */
export function istMidnightIso(date: string): string {
  return new Date(`${date}T00:00:00+05:30`).toISOString();
}

/** The India-time calendar date a timestamp falls on. */
export function istDate(iso: string): string {
  return new Date(new Date(iso).getTime() + IST_OFFSET_MS).toISOString().slice(0, 10);
}

function addDays(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** Leave from [startDate] through [endDate], whole days, India time: the stored range runs to the midnight after the last day. */
export function checkLeave(startDate: unknown, endDate: unknown, nowMs: number = Date.now()): Checked<{ startsAt: string; endsAt: string }> {
  if (typeof startDate !== "string" || typeof endDate !== "string" || !DATE.test(startDate) || !DATE.test(endDate)) {
    return { ok: false, message: "Pick a start and end date" };
  }
  if (isNaN(new Date(`${startDate}T00:00:00Z`).getTime()) || isNaN(new Date(`${endDate}T00:00:00Z`).getTime())) {
    return { ok: false, message: "Pick a start and end date" };
  }
  if (endDate < startDate) return { ok: false, message: "The last day of leave can't be before the first" };
  if (startDate < istDate(new Date(nowMs).toISOString())) return { ok: false, message: "Leave can't start in the past" };
  const days = (new Date(`${endDate}T00:00:00Z`).getTime() - new Date(`${startDate}T00:00:00Z`).getTime()) / 86_400_000 + 1;
  if (days > MAX_LEAVE_DAYS) return { ok: false, message: `Leave can be at most ${MAX_LEAVE_DAYS} days at a time` };
  return { ok: true, value: { startsAt: istMidnightIso(startDate), endsAt: istMidnightIso(addDays(endDate, 1)) } };
}

export interface CarSummary {
  brand: string | null;
  checkEngineOn: boolean | null;
  /** Up to 5 findings from the customer's scan, e.g. "Engine misfire (P0301)". */
  issues: string[];
}

/** What the mechanic needs before the call from the scan the customer chose to attach; null when none was attached. */
export function summariseCar(vehicleContext: unknown): CarSummary | null {
  if (!vehicleContext || typeof vehicleContext !== "object") return null;
  const ctx = vehicleContext as Record<string, unknown>;
  const findings = Array.isArray(ctx.findings) ? ctx.findings : [];
  const issues = findings
    .map((f) => {
      const finding = f as Record<string, unknown>;
      if (typeof finding?.title !== "string") return null;
      const codes = Array.isArray(finding.relatedDtcCodes) ? finding.relatedDtcCodes.filter((c) => typeof c === "string") : [];
      return codes.length ? `${finding.title} (${codes.join(", ")})` : finding.title;
    })
    .filter((s): s is string => s !== null)
    .slice(0, 5);
  return {
    brand: typeof ctx.vehicleBrand === "string" ? ctx.vehicleBrand : null,
    checkEngineOn: typeof ctx.milOn === "boolean" ? ctx.milOn : null,
    issues,
  };
}

/** Back to the dates the mechanic picked: the end is stored as the midnight after their last day. */
export function leaveDates(startsAt: string, endsAt: string): { startDate: string; endDate: string } {
  return { startDate: istDate(startsAt), endDate: istDate(new Date(new Date(endsAt).getTime() - 1).toISOString()) };
}
