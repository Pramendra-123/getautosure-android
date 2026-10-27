-- Backs the Dealer Sathi "shareable web report" feature (getautosure.com/v/<slug>). A dealer
-- publishes a scan + photos + condition notes from the Android app; the public website reads it
-- back by slug with no auth. Written but never run against a real Supabase project yet — review
-- before applying, same status every other migration in this project had before its first deploy.
--
-- Deliberately in the SAME Supabase project as ai_mechanic_requests/consultations (not the
-- website's separate, unrelated project) — see the Android app's own PROJECT_NOTES.md for why:
-- one project, one deploy workflow, no duplicated secrets.

create table if not exists dealers (
    id uuid primary key default gen_random_uuid(),
    -- The verified Google ID token's `sub` claim — same identity source as every other
    -- per-user table in this project (ai_mechanic_requests.user_id, consultations.customer_sub).
    dealer_sub text not null unique,
    name text not null,
    tagline text not null default '',
    phone text not null default '',
    location text not null default '',
    logo_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Deny-by-default — the Edge Function (service role) is the only reader/writer. A dealer's own
-- contact details aren't public on their own; they're only ever exposed as part of a published
-- report (see vehicle_reports' own public-read policy below), which the Edge Function controls.
alter table dealers enable row level security;

create table if not exists vehicle_reports (
    id uuid primary key default gen_random_uuid(),
    -- Short, public, URL-safe id — e.g. "SWM-20261025-001" — never the row's own uuid, so a
    -- leaked/enumerated slug can't be used to probe other tables.
    slug text not null unique,
    dealer_id uuid not null references dealers(id) on delete cascade,
    status text not null default 'published' check (status in ('draft', 'published', 'unpublished')),

    -- Mirrors VehicleReportInfo (app/src/main/java/.../report/VehicleReportPdfGenerator.kt) —
    -- same "whatever the user actually entered, nothing guessed" fields, not re-derived here.
    make text,
    model text,
    variant text,
    year int,
    fuel_type text,
    transmission text,
    odometer_km int,
    ownership text,
    registration_no text,

    -- The scan's health score + findings, as JSON — same shape ScanResult/HealthScoreResult
    -- already produce client-side; this table stores the rendered summary, not raw PID data.
    health_summary jsonb not null default '{}',
    -- {"exterior": {"rating": "Good", "note": "..."}, "interior": {...}, "tyres": {...}} —
    -- self-reported by the dealer, always labeled as such on the report (never implied to be an
    -- independent inspection).
    dealer_condition jsonb not null default '{}',
    -- Public Storage URLs, after background removal where applicable — never raw device paths.
    photo_urls jsonb not null default '[]',

    scanned_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists vehicle_reports_dealer_idx on vehicle_reports (dealer_id, created_at desc);

alter table vehicle_reports enable row level security;

-- The one deliberate public hole in this schema — a buyer must be able to open a report link
-- with no login and no app, exactly like experts_public_read does for browsing experts. Only
-- published rows, only SELECT; INSERT/UPDATE/DELETE still require the service role.
create policy vehicle_reports_public_read on vehicle_reports for select
    using (status = 'published');

-- Generates a short, collision-checked slug — e.g. "GAS-A1B2C3" — called by the Edge Function at
-- publish time via the service role, never by a client directly.
create or replace function generate_report_slug()
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_slug text;
    v_exists boolean;
begin
    loop
        v_slug := 'GAS-' || upper(substr(md5(random()::text || clock_timestamp()::text), 1, 6));
        select exists(select 1 from vehicle_reports where slug = v_slug) into v_exists;
        exit when not v_exists;
    end loop;
    return v_slug;
end;
$$;

revoke all on function generate_report_slug() from public, anon, authenticated;
grant execute on function generate_report_slug() to service_role;
