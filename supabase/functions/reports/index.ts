// Backs the Dealer Sathi shareable web report feature (getautosure.com/v/<slug>) — Phase A: the
// publish pipeline (Android writes) + the public read (the website, Phase B, reads). Written but
// never deployed/tested against a real Supabase project yet — same status every other Edge
// Function in this project had before its first real deploy.
//
// One function, action-dispatched, same reasoning as ai-mechanic/consultations: this project's
// Supabase CLI has never worked on the dev machine it was built on, so every deploy is a manual
// paste into the Dashboard's Code editor — fewer functions means fewer manual redeploys.
//
// Required secrets (Edge Functions > Secrets): GOOGLE_WEB_CLIENT_ID — same value already used by
// ai-mechanic and consultations. GEMINI_API_KEY — same value already used by ai-mechanic, reused
// here for the "remove_background" action (see that action's own doc).
// Required Supabase resources: see ../../migrations/20261025000000_vehicle_reports.sql, and a
// PUBLIC Storage bucket named "vehicle-report-photos" (create via Dashboard > Storage; this
// function uploads into it with the service role key, so the bucket itself only needs public
// *read* access, no public write policy).

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";
import { createRemoteJWKSet, jwtVerify } from "https://esm.sh/jose@5.9.6";

const GOOGLE_WEB_CLIENT_ID = Deno.env.get("GOOGLE_WEB_CLIENT_ID");
const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY");
const GEMINI_IMAGE_MODEL = "gemini-3.1-flash-image";
const GEMINI_IMAGE_ENDPOINT = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_IMAGE_MODEL}:generateContent`;

const GOOGLE_JWKS = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

const PHOTOS_BUCKET = "vehicle-report-photos";
// Root-level, matching the website's existing report-route convention (getautosure.com/<slug>) —
// NOT /v/<slug>. Any already-generated /v/<slug> link still works via a redirect configured on
// the website side (next.config.ts), so changing this doesn't break links already shared/printed.
const REPORT_BASE_URL = "https://getautosure.com";

// Server-side photo guardrails — Android already downscales/compresses before sending, but the
// server never trusts that; a buggy or malicious client could send anything.
const MAX_PHOTO_BYTES = 8 * 1024 * 1024; // 8 MB per photo, post-decode
const ALLOWED_PHOTO_CONTENT_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);
// A report's PDF (with its photos) or Status picture.
const MAX_OUTPUT_BYTES = 15 * 1024 * 1024;

// Until Razorpay is live, a dealer with no credits still gets their report (recorded as
// "free_period"); set the DEALER_PAYMENTS_LIVE secret to "true" once packs can be bought.
const PAYMENTS_LIVE = Deno.env.get("DEALER_PAYMENTS_LIVE") === "true";

export function jsonError(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ error: { code, message } }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Same pattern as ai-mechanic/consultations' verifyGoogleIdToken — see either for why each check exists. */
export async function verifyGoogleIdToken(authHeader: string | null): Promise<string> {
  if (!authHeader?.startsWith("Bearer ")) {
    throw jsonError(401, "unauthorized", "Missing bearer token");
  }
  const token = authHeader.slice("Bearer ".length);
  try {
    const { payload } = await jwtVerify(token, GOOGLE_JWKS, {
      issuer: ["https://accounts.google.com", "accounts.google.com"],
      audience: GOOGLE_WEB_CLIENT_ID,
    });
    if (typeof payload.sub !== "string") throw new Error("token has no subject");
    return payload.sub;
  } catch (e) {
    console.error("ID token verification failed", e);
    throw jsonError(401, "unauthorized", "Invalid or expired sign-in token");
  }
}

type SupabaseClient = ReturnType<typeof createClient>;
type PhotoInput = { filename: string; contentType: string; base64: string };

function decodeBase64(base64: string): Uint8Array {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

/** Returns the uploaded photo URLs plus a human-readable reason for each photo that was skipped — skipped, never silently dropped, so the dealer can see why their count doesn't match what they picked. */
async function uploadPhotos(
  supabase: SupabaseClient,
  dealerSub: string,
  slug: string,
  photos: PhotoInput[]
): Promise<{ urls: string[]; warnings: string[] }> {
  const urls: string[] = [];
  const warnings: string[] = [];

  for (let i = 0; i < photos.length; i++) {
    const photo = photos[i];
    const contentType = photo.contentType || "image/jpeg";
    if (!ALLOWED_PHOTO_CONTENT_TYPES.has(contentType)) {
      warnings.push(`Photo ${i + 1} skipped — unsupported file type (${contentType}).`);
      continue;
    }

    let bytes: Uint8Array;
    try {
      bytes = decodeBase64(photo.base64);
    } catch (e) {
      warnings.push(`Photo ${i + 1} skipped — could not be read.`);
      continue;
    }
    if (bytes.byteLength === 0) {
      warnings.push(`Photo ${i + 1} skipped — empty file.`);
      continue;
    }
    if (bytes.byteLength > MAX_PHOTO_BYTES) {
      warnings.push(`Photo ${i + 1} skipped — too large (over ${Math.round(MAX_PHOTO_BYTES / (1024 * 1024))} MB).`);
      continue;
    }

    const extension = photo.filename.includes(".") ? photo.filename.split(".").pop() : "jpg";
    const path = `${dealerSub}/${slug}/${i}.${extension}`;
    const { error } = await supabase.storage.from(PHOTOS_BUCKET).upload(path, bytes, {
      contentType,
      upsert: true,
    });
    if (error) {
      console.error("photo upload failed", path, error);
      warnings.push(`Photo ${i + 1} skipped — upload failed.`);
      continue; // One bad photo shouldn't fail the whole publish — the report is still useful without it.
    }
    const { data } = supabase.storage.from(PHOTOS_BUCKET).getPublicUrl(path);
    urls.push(data.publicUrl);
  }

  return { urls, warnings };
}

/**
 * health_summary.rto (the app's RTO record block) repeats the registration number — once on its
 * own and once as the "Registration" row. Same public-read masking as registration_no, so the
 * public report never shows the full plate. Everything else in the block is already safe to
 * publish: the app sends chassis/engine numbers masked and never sends the owner's name.
 */
export function maskRtoInHealthSummary(healthSummary: unknown): unknown {
  if (typeof healthSummary !== "object" || healthSummary === null) return healthSummary;
  const summary = healthSummary as Record<string, unknown>;
  const rto = summary.rto;
  if (typeof rto !== "object" || rto === null) return healthSummary;
  const r = rto as Record<string, unknown>;
  const vehicle = Array.isArray(r.vehicle)
    ? r.vehicle.map((row) => {
      const cell = row as Record<string, unknown>;
      return cell?.label === "Registration" ? { ...cell, value: maskRegistrationNumber(cell.value as string | null) } : row;
    })
    : r.vehicle;
  return {
    ...summary,
    rto: { ...r, registrationNumber: maskRegistrationNumber(r.registrationNumber as string | null), vehicle },
  };
}

/** Indian-plate-shaped masking (e.g. "KA01AB1234" -> "KA 01 * * * 1234") matching the reference design — state + RTO code visible, the rest hidden behind a fixed-length placeholder so the real length is never leaked either. Falls back to a conservative full mask for anything shorter/unusually shaped rather than guessing at a format. */
function maskRegistrationNumber(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const compact = raw.replace(/[\s-]/g, "").toUpperCase();
  if (compact.length >= 8) {
    const prefix = compact.slice(0, 4);
    const suffix = compact.slice(-4);
    return `${prefix.slice(0, 2)} ${prefix.slice(2, 4)} * * * ${suffix}`;
  }
  if (compact.length > 4) {
    return `${compact.slice(0, 2)} * * * ${compact.slice(-2)}`;
  }
  return "* * * *";
}

async function upsertDealer(supabase: SupabaseClient, dealerSub: string, body: Record<string, unknown>): Promise<{ id: string } | null> {
  const name = typeof body.dealerName === "string" ? body.dealerName.trim() : "";
  if (!name) return null; // No dealer identity supplied — caller must have a saved profile already.

  const update: Record<string, unknown> = {
    dealer_sub: dealerSub,
    name,
    tagline: typeof body.dealerTagline === "string" ? body.dealerTagline.trim() : "",
    phone: typeof body.dealerPhone === "string" ? body.dealerPhone.trim() : "",
    location: typeof body.dealerLocation === "string" ? body.dealerLocation.trim() : "",
    updated_at: new Date().toISOString(),
  };

  // Logo is optional per publish — only touched when a fresh one is actually sent, so a report
  // published without re-sending the logo doesn't blank out an already-uploaded one.
  const logoPhoto = body.dealerLogo as PhotoInput | undefined;
  if (logoPhoto?.base64) {
    const extension = logoPhoto.filename.includes(".") ? logoPhoto.filename.split(".").pop() : "png";
    const path = `${dealerSub}/logo.${extension}`;
    const { error } = await supabase.storage.from(PHOTOS_BUCKET).upload(path, decodeBase64(logoPhoto.base64), {
      contentType: logoPhoto.contentType || "image/png",
      upsert: true,
    });
    if (!error) {
      update.logo_url = supabase.storage.from(PHOTOS_BUCKET).getPublicUrl(path).data.publicUrl;
    }
  }

  const { data, error } = await supabase
    .from("dealers")
    .upsert(update, { onConflict: "dealer_sub" })
    .select("id")
    .single();
  if (error) {
    console.error("dealer upsert failed", error);
    return null;
  }
  return data as { id: string };
}

async function publishReport(supabase: SupabaseClient, dealerSub: string, body: Record<string, unknown>): Promise<Response> {
  const dealer = await upsertDealer(supabase, dealerSub, body);
  if (!dealer) return jsonError(400, "invalid_request", "dealerName is required to publish a report");

  const { data: slugData, error: slugError } = await supabase.rpc("generate_report_slug");
  if (slugError || !slugData) {
    console.error("slug generation failed", slugError);
    return jsonError(500, "publish_failed", "Could not generate a report link");
  }
  const slug = slugData as string;

  // One credit per report, charged before anything is saved (see charge_report_credit in
  // ../../migrations/20261108000000_dealer_reports_and_credits.sql). Remaking the same car within
  // 30 days is free, and so is everything while payments aren't open.
  const registration = typeof body.registrationNo === "string" ? body.registrationNo : null;
  const { data: charge, error: chargeError } = await supabase.rpc("charge_report_credit", {
    p_dealer: dealer.id, p_slug: slug, p_registration: registration, p_payments_live: PAYMENTS_LIVE,
  });
  if (chargeError) {
    console.error("credit charge failed", chargeError);
    return jsonError(500, "publish_failed", "Could not check your report credits");
  }
  if (charge === "no_credits") {
    return jsonError(402, "no_credits", "You've used all your report credits. Buy a pack to make this report.");
  }

  const photos = Array.isArray(body.photos) ? (body.photos as PhotoInput[]) : [];
  const { urls: photoUrls, warnings: photoWarnings } =
    photos.length > 0 ? await uploadPhotos(supabase, dealerSub, slug, photos) : { urls: [], warnings: [] };

  const row = {
    slug,
    dealer_id: dealer.id,
    status: "published",
    make: body.make ?? null,
    model: body.model ?? null,
    variant: body.variant ?? null,
    year: body.year ?? null,
    fuel_type: body.fuelType ?? null,
    transmission: body.transmission ?? null,
    odometer_km: body.odometerKm ?? null,
    ownership: body.ownership ?? null,
    registration_no: body.registrationNo ?? null,
    health_summary: body.healthSummary ?? {},
    dealer_condition: body.dealerCondition ?? {},
    photo_urls: photoUrls,
    scanned_at: body.scannedAt ?? null,
  };

  const { error: insertError } = await supabase.from("vehicle_reports").insert(row);
  if (insertError) {
    console.error("vehicle_reports insert failed", insertError);
    // The credit was spent on a report that doesn't exist — give it back.
    if (charge === "charged") {
      await supabase.from("dealer_credit_ledger").insert({ dealer_id: dealer.id, delta: 1, reason: "refund", report_slug: slug });
    }
    return jsonError(500, "publish_failed", "Could not save the report");
  }

  const { data: balance } = await supabase.rpc("dealer_credit_balance", { p_dealer: dealer.id });
  return new Response(
    JSON.stringify({ slug, url: `${REPORT_BASE_URL}/${slug}`, photoWarnings, charge, credits: balance ?? 0 }),
    { headers: { "Content-Type": "application/json" } }
  );
}

/** The signed-in dealer's own row id, or null when they've never published (no profile yet). */
async function dealerIdFor(supabase: SupabaseClient, dealerSub: string): Promise<string | null> {
  const { data } = await supabase.from("dealers").select("id").eq("dealer_sub", dealerSub).maybeSingle();
  return (data as { id: string } | null)?.id ?? null;
}

/** A report owned by this dealer, or an error response to return as-is. */
async function ownedReport(supabase: SupabaseClient, dealerSub: string, slug: unknown): Promise<{ id: string; slug: string } | Response> {
  if (typeof slug !== "string" || !slug) return jsonError(400, "invalid_request", "slug is required");
  const dealerId = await dealerIdFor(supabase, dealerSub);
  if (!dealerId) return jsonError(403, "forbidden", "No dealer profile found for this account");
  const { data, error } = await supabase.from("vehicle_reports").select("id, dealer_id").eq("slug", slug).maybeSingle();
  if (error || !data) return jsonError(404, "not_found", "Report not found");
  if ((data as { dealer_id: string }).dealer_id !== dealerId) return jsonError(403, "forbidden", "This report isn't yours");
  return { id: (data as { id: string }).id, slug };
}

/**
 * "My reports": every report this dealer has made, newest first, with the counts and credits for
 * the top of the screen. The dealer's own view, so the number plate is full, not masked, and the
 * saved PDF and Status picture come with each report (see save_outputs).
 */
async function listReports(supabase: SupabaseClient, dealerSub: string): Promise<Response> {
  const dealerId = await dealerIdFor(supabase, dealerSub);
  const empty = { reports: [], made: 0, thisMonth: 0, credits: 0, paymentsLive: PAYMENTS_LIVE };
  if (!dealerId) return new Response(JSON.stringify(empty), { headers: { "Content-Type": "application/json" } });

  const { data, error } = await supabase
    .from("vehicle_reports")
    .select("id, slug, status, make, model, variant, year, registration_no, dealer_condition, photo_urls, created_at, sold_at, vehicle_report_outputs(pdf_path, status_path)")
    .eq("dealer_id", dealerId)
    .order("created_at", { ascending: false })
    .limit(500);
  if (error) {
    console.error("list_reports query failed", error);
    return jsonError(500, "query_failed", "Could not load your reports");
  }
  const publicUrl = (path: string | null | undefined) => path ? supabase.storage.from(PHOTOS_BUCKET).getPublicUrl(path).data.publicUrl : null;
  // deno-lint-ignore no-explicit-any
  const rows = (data ?? []) as any[];
  const reports = rows.map((r) => {
    const outputs = Array.isArray(r.vehicle_report_outputs) ? r.vehicle_report_outputs[0] : r.vehicle_report_outputs;
    return {
      slug: r.slug,
      url: `${REPORT_BASE_URL}/${r.slug}`,
      live: r.status === "published",
      make: r.make, model: r.model, variant: r.variant, year: r.year,
      registrationNo: r.registration_no,
      priceInr: r.dealer_condition?.priceInr ?? null,
      photoUrl: Array.isArray(r.photo_urls) ? r.photo_urls[0] ?? null : null,
      createdAt: r.created_at,
      soldAt: r.sold_at,
      pdfUrl: publicUrl(outputs?.pdf_path),
      statusUrl: publicUrl(outputs?.status_path),
    };
  });
  const monthStart = new Date();
  monthStart.setUTCDate(1);
  monthStart.setUTCHours(0, 0, 0, 0);
  const { data: balance } = await supabase.rpc("dealer_credit_balance", { p_dealer: dealerId });
  return new Response(JSON.stringify({
    reports,
    made: reports.length,
    thisMonth: rows.filter((r) => new Date(r.created_at) >= monthStart).length,
    credits: balance ?? 0,
    paymentsLive: PAYMENTS_LIVE,
  }), { headers: { "Content-Type": "application/json" } });
}

/**
 * Saves a report's PDF and Status picture, so "My reports" can send them again from any phone.
 * Each file gets a random name: the bucket is public-read and the PDF shows the full number plate,
 * so its address mustn't be guessable from the report's photo addresses.
 */
async function saveOutputs(supabase: SupabaseClient, dealerSub: string, body: Record<string, unknown>): Promise<Response> {
  const report = await ownedReport(supabase, dealerSub, body.slug);
  if (report instanceof Response) return report;
  const update: Record<string, unknown> = { report_id: report.id, updated_at: new Date().toISOString() };
  const files: [string, string, string, string][] = [["pdf", "pdf_path", "application/pdf", "pdf"], ["status", "status_path", "image/png", "png"]];
  for (const [key, column, contentType, extension] of files) {
    const file = body[key] as PhotoInput | undefined;
    if (!file?.base64) continue;
    const bytes = decodeBase64(file.base64);
    if (bytes.byteLength === 0 || bytes.byteLength > MAX_OUTPUT_BYTES) return jsonError(400, "invalid_request", `${key} is empty or too large`);
    const path = `${dealerSub}/${report.slug}/${key}-${crypto.randomUUID()}.${extension}`;
    const { error } = await supabase.storage.from(PHOTOS_BUCKET).upload(path, bytes, { contentType, upsert: false });
    if (error) {
      console.error("output upload failed", path, error);
      return jsonError(500, "upload_failed", `Could not save the ${key}`);
    }
    update[column] = path;
  }
  if (Object.keys(update).length === 2) return jsonError(400, "invalid_request", "Nothing to save");
  const { error } = await supabase.from("vehicle_report_outputs").upsert(update, { onConflict: "report_id" });
  if (error) {
    console.error("vehicle_report_outputs upsert failed", error);
    return jsonError(500, "update_failed", "Could not save the report files");
  }
  return new Response(JSON.stringify({ slug: report.slug, saved: true }), { headers: { "Content-Type": "application/json" } });
}

/** Marks a car sold (or not). The report stays live — a buyer may still be holding the link. */
async function setSold(supabase: SupabaseClient, dealerSub: string, body: Record<string, unknown>): Promise<Response> {
  const report = await ownedReport(supabase, dealerSub, body.slug);
  if (report instanceof Response) return report;
  const soldAt = body.sold === true ? new Date().toISOString() : null;
  const { error } = await supabase.from("vehicle_reports").update({ sold_at: soldAt, updated_at: new Date().toISOString() }).eq("id", report.id);
  if (error) {
    console.error("set_sold failed", error);
    return jsonError(500, "update_failed", "Could not update this report");
  }
  return new Response(JSON.stringify({ slug: report.slug, soldAt }), { headers: { "Content-Type": "application/json" } });
}

async function getReport(supabase: SupabaseClient, slug: unknown): Promise<Response> {
  if (typeof slug !== "string" || !slug) return jsonError(400, "invalid_request", "slug is required");
  const { data, error } = await supabase
    .from("vehicle_reports")
    .select("*, dealers(name, tagline, phone, location, logo_url)")
    .eq("slug", slug)
    .eq("status", "published")
    .maybeSingle();
  if (error) {
    console.error("get_report query failed", error);
    return jsonError(500, "query_failed", "Could not load this report");
  }
  if (!data) return jsonError(404, "not_found", "Report not found");
  // Masked only in the PUBLIC read path — the stored value stays full-fidelity for the dealer's
  // own future edit view; this is a presentation-layer redaction, not a data-loss one.
  const row = data as Record<string, unknown>;
  const report = {
    ...row,
    registration_no: maskRegistrationNumber(row.registration_no as string | null),
    health_summary: maskRtoInHealthSummary(row.health_summary),
  };
  return new Response(JSON.stringify({ report }), { headers: { "Content-Type": "application/json" } });
}

/** Dealer-only: toggle publish status or update condition notes on a report they own. Ownership is checked against the verified token's sub, never a client-supplied dealer id. */
async function updateReport(supabase: SupabaseClient, dealerSub: string, body: Record<string, unknown>): Promise<Response> {
  const slug = body.slug;
  if (typeof slug !== "string" || !slug) return jsonError(400, "invalid_request", "slug is required");

  const { data: dealer, error: dealerError } = await supabase.from("dealers").select("id").eq("dealer_sub", dealerSub).maybeSingle();
  if (dealerError || !dealer) return jsonError(403, "forbidden", "No dealer profile found for this account");

  const { data: existing, error: fetchError } = await supabase.from("vehicle_reports").select("id, dealer_id").eq("slug", slug).maybeSingle();
  if (fetchError || !existing) return jsonError(404, "not_found", "Report not found");
  if ((existing as { dealer_id: string }).dealer_id !== (dealer as { id: string }).id) {
    return jsonError(403, "forbidden", "This report isn't yours");
  }

  const update: Record<string, unknown> = { updated_at: new Date().toISOString() };
  if (typeof body.status === "string" && ["draft", "published", "unpublished"].includes(body.status)) {
    update.status = body.status;
  }
  if (body.dealerCondition && typeof body.dealerCondition === "object") {
    update.dealer_condition = body.dealerCondition;
  }
  if (Object.keys(update).length === 1) return jsonError(400, "invalid_request", "Nothing to update");

  const { error: updateError } = await supabase.from("vehicle_reports").update(update).eq("slug", slug);
  if (updateError) {
    console.error("vehicle_reports update failed", updateError);
    return jsonError(500, "update_failed", "Could not update this report");
  }
  return new Response(JSON.stringify({ slug, updated: true }), { headers: { "Content-Type": "application/json" } });
}

/**
 * Auth-gated (see the dispatcher below) — a dealer previews this per exterior photo before
 * publishing, so they can review the result and keep the original if they don't like it (Android
 * never auto-substitutes it). Sends one photo to Gemini's current image-editing model
 * (gemini-3.1-flash-image) with an instruction to replace the background only, never touching the
 * vehicle itself, and returns the edited image's base64 directly — not stored, not attached to
 * any report; the dealer's chosen final photo (original or this result) only reaches storage via
 * the normal publish_report upload path, same validation as any other photo.
 */
async function removeBackground(body: Record<string, unknown>): Promise<Response> {
  if (!GEMINI_API_KEY) {
    console.error("Missing GEMINI_API_KEY secret");
    return jsonError(500, "misconfigured", "Server is not configured correctly");
  }
  const photo = body.photo as PhotoInput | undefined;
  if (!photo?.base64) return jsonError(400, "invalid_request", "photo is required");

  // "white" (default, and what older app versions get): plain white backdrop.
  // "studio": the used-car-portal look — light grey wall, round grey turntable floor, soft shadow.
  const style = body.style === "studio" ? "studio" : "white";
  const keepCar =
    "Keep the vehicle exactly as it is — same color, shape, badges, number plate, reflections, " +
    "scratches and dents, every detail unchanged. Keep the same camera angle and framing. Do not " +
    "alter, retouch, or regenerate the vehicle itself in any way.";
  // hidePlate: the one allowed change to the car — the plate's characters are blanked, nothing else.
  const plate = body.hidePlate === true
    ? " One exception: cover the number plate by replacing its letters and numbers with a plain blank " +
      "plate of the same shape and colour, so the registration number cannot be read. Change nothing else."
    : "";
  const instruction = (style === "studio"
    ? "Replace the background of this car photo with a professional car-dealership photo studio: " +
      "a seamless light grey wall, a large round matte grey turntable floor under the car, soft even " +
      "studio lighting, and a natural soft shadow beneath the tyres so the car sits on the floor. " +
      "Nothing else in the scene — no people, signs, buildings or other cars. " + keepCar
    : "Remove the background from this car photo and replace only the background with a plain, " +
      "pure white studio backdrop (seamless white, like a product-photography cyclorama). " + keepCar) + plate;

  const geminiBody = {
    contents: [
      {
        parts: [
          { text: instruction },
          { inlineData: { mimeType: photo.contentType || "image/jpeg", data: photo.base64 } },
        ],
      },
    ],
    generationConfig: { responseModalities: ["TEXT", "IMAGE"] },
  };

  try {
    const res = await fetch(`${GEMINI_IMAGE_ENDPOINT}?key=${GEMINI_API_KEY}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(geminiBody),
    });
    if (!res.ok) {
      const errText = await res.text();
      console.error("Gemini image call failed", res.status, errText.slice(0, 500));
      return jsonError(502, "gemini_failed", `Gemini returned HTTP ${res.status}`);
    }
    const json = await res.json();
    // deno-lint-ignore no-explicit-any
    const parts = (json?.candidates?.[0]?.content?.parts ?? []) as any[];
    const imagePart = parts.find((p) => p.inlineData?.data);
    const textPart = parts.find((p) => typeof p.text === "string");
    if (!imagePart) {
      console.error("Gemini response had no image", JSON.stringify(json).slice(0, 800));
      return jsonError(502, "gemini_failed", textPart?.text || "Gemini returned no image");
    }
    return new Response(
      JSON.stringify({ base64: imagePart.inlineData.data, mimeType: imagePart.inlineData.mimeType || "image/png" }),
      { headers: { "Content-Type": "application/json" } }
    );
  } catch (e) {
    console.error("Gemini call errored", e);
    return jsonError(502, "gemini_failed", "Background removal failed");
  }
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return jsonError(405, "method_not_allowed", "Only POST is supported");

  let rawBody: unknown;
  try {
    rawBody = await req.json();
  } catch {
    return jsonError(400, "invalid_request", "Malformed JSON body");
  }
  const body = (typeof rawBody === "object" && rawBody !== null ? rawBody : {}) as Record<string, unknown>;
  const action = typeof body.action === "string" ? body.action : null;
  if (!action) return jsonError(400, "invalid_request", "action is required");

  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    console.error("Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY secret");
    return jsonError(500, "misconfigured", "Server is not configured correctly");
  }
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  // Public: a buyer opening a report link has no Google sign-in at all.
  if (action === "get_report") return await getReport(supabase, body.slug);

  // Everything else is the dealer publishing/editing their own catalogue — including
  // remove_background, which calls a billed Gemini model and must never be reachable by an
  // unauthenticated caller.
  let dealerSub: string;
  try {
    dealerSub = await verifyGoogleIdToken(req.headers.get("Authorization"));
  } catch (e) {
    if (e instanceof Response) return e;
    return jsonError(401, "unauthorized", "Authentication failed");
  }

  switch (action) {
    case "publish_report":
      return await publishReport(supabase, dealerSub, body);
    case "update_report":
      return await updateReport(supabase, dealerSub, body);
    case "remove_background":
      return await removeBackground(body);
    case "list_reports":
      return await listReports(supabase, dealerSub);
    case "save_outputs":
      return await saveOutputs(supabase, dealerSub, body);
    case "set_sold":
      return await setSold(supabase, dealerSub, body);
    default:
      return jsonError(400, "invalid_request", `Unknown action: ${action}`);
  }
});
