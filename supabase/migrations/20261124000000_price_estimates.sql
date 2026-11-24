-- Backs the price-estimate Edge Function (supabase/functions/price-estimate/index.ts): AI used-car
-- prices grounded in a live search of current listings.

-- One answer per normalised car (make|model|variant|year|fuel|km band|city|owners), so the same car
-- always gets the same price for CACHE_TTL_DAYS and Gemini is paid once. No personal data: the key
-- and request hold only the car's description. RLS deny-all; only the function (service role) reads it.
create table if not exists price_estimate_cache (
    cache_key text primary key,
    request jsonb not null,
    response jsonb not null,
    created_at timestamptz not null default now()
);

alter table price_estimate_cache enable row level security;

-- One row per fresh (uncached) estimate, for the per-user daily limit.
create table if not exists price_estimate_requests (
    id bigint generated always as identity primary key,
    user_id text not null,
    cache_key text not null,
    created_at timestamptz not null default now()
);

create index if not exists price_estimate_requests_user_window_idx
    on price_estimate_requests (user_id, created_at desc);

alter table price_estimate_requests enable row level security;

-- Periodic cleanup, run by hand or via pg_cron. Keep in step with CACHE_TTL_DAYS in the function.
-- delete from price_estimate_cache where created_at < now() - interval '30 days';
-- delete from price_estimate_requests where created_at < now() - interval '30 days';
