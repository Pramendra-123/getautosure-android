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
    return jsonError(500, "publish_failed", "Could not save the report");
  }

  return new Response(
    JSON.stringify({ slug, url: `${REPORT_BASE_URL}/${slug}`, photoWarnings }),
    { headers: { "Content-Type": "application/json" } }
  );
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
  const report = { ...(data as Record<string, unknown>), registration_no: maskRegistrationNumber((data as Record<string, unknown>).registration_no as string | null) };
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

  const geminiBody = {
    contents: [
      {
        parts: [
          {
            text:
              "Remove the background from this car photo. Keep the vehicle exactly as it is — " +
              "same color, shape, badges, reflections, every detail unchanged — and replace only " +
              "the background with a plain, pure white studio backdrop (seamless white, like a " +
              "product-photography cyclorama). Do not alter, retouch, or regenerate the vehicle " +
              "itself in any way.",
          },
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
    default:
      return jsonError(400, "invalid_request", `Unknown action: ${action}`);
  }
});
