-- Backs the vehicle-lookup Edge Function (supabase/functions/vehicle-lookup/index.ts): RC
-- (registration certificate) lookups via Surepass RC V2, used by both the regular customer
-- onboarding prefill and Dealer Sathi. Written but never run against a real Supabase project —
-- review before applying, same status every other migration here had before its first deploy.
--
-- Requires 20261025000000_vehicle_reports.sql (the dealers table) to have been applied first.

-- Dealer mode returns the registered owner's name and the chassis/engine numbers, so it can't be
-- gated on "has a dealers row" — publish_report creates that row for any signed-in user who types
-- a dealer name. This flag is set by hand in the Dashboard (Table Editor > dealers) for dealers
-- you've actually verified; nothing in any Edge Function ever sets it to true.
alter table dealers add column if not exists rc_full_access boolean not null default false;

-- One cached Surepass response per registration number, so the same car isn't paid for twice
-- (a customer reopening onboarding, a dealer re-publishing). Holds the FULL upstream payload,
-- third-party personal data included (owner name, addresses) — that's why RLS is deny-all and
-- why rows expire: the function treats anything older than its cache TTL as a miss, and the
-- cleanup below deletes it. Field filtering per mode happens in the function, never here.
create table if not exists vehicle_rc_cache (
    rc_number text primary key,
    provider text not null default 'surepass_rc_v2',
    response jsonb not null,
    fetched_at timestamptz not null default now()
);

alter table vehicle_rc_cache enable row level security;

-- One row per lookup attempt that passed the rate limit (cache hits included — a cache hit still
-- reveals data, so it still counts against the caller's limit; otherwise the cache would be a
-- free way to scrape every number anyone else ever looked up). Doubles as an audit trail of who
-- looked up which vehicle, which matters for a feature that handles third-party personal data.
create table if not exists vehicle_lookup_requests (
    id bigint generated always as identity primary key,
    user_id text not null,
    rc_number text not null,
    mode text not null check (mode in ('customer', 'dealer')),
    created_at timestamptz not null default now()
);

create index if not exists vehicle_lookup_requests_user_window_idx
    on vehicle_lookup_requests (user_id, created_at desc);

alter table vehicle_lookup_requests enable row level security;

-- Periodic cleanup — run manually or via pg_cron once enabled; not wired up automatically here.
-- Keep the cache window in sync with CACHE_TTL_DAYS in the Edge Function.
-- delete from vehicle_rc_cache where fetched_at < now() - interval '30 days';
-- delete from vehicle_lookup_requests where created_at < now() - interval '180 days';

-- Atomic check-and-record, same shape and same race-condition reasoning as
-- check_and_record_ai_mechanic_rate_limit (see 20261017000000_ai_mechanic_requests.sql).
create or replace function check_and_record_vehicle_lookup(
    p_user_id text,
    p_rc_number text,
    p_mode text,
    p_minute_limit int,
    p_day_limit int
) returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_minute_count int;
    v_day_count int;
begin
    perform pg_advisory_xact_lock(hashtext('vehicle_lookup_rate_limit:' || p_user_id));

    select count(*) into v_minute_count
    from vehicle_lookup_requests
    where user_id = p_user_id and created_at >= now() - interval '1 minute';

    if v_minute_count >= p_minute_limit then
        return false;
    end if;

    select count(*) into v_day_count
    from vehicle_lookup_requests
    where user_id = p_user_id and created_at >= date_trunc('day', now());

    if v_day_count >= p_day_limit then
        return false;
    end if;

    insert into vehicle_lookup_requests (user_id, rc_number, mode, created_at)
    values (p_user_id, p_rc_number, p_mode, now());
    return true;
end;
$$;

revoke all on function check_and_record_vehicle_lookup(text, text, text, int, int) from public, anon, authenticated;
grant execute on function check_and_record_vehicle_lookup(text, text, text, int, int) to service_role;
