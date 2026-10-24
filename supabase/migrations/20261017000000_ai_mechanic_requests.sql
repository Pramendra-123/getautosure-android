-- Backs the Edge Function's rate limiting (see supabase/functions/ai-mechanic/index.ts).
-- One row per request; the function counts rows in the last minute/day per user_id.
-- Written but never run against a real Supabase project — review before applying.

create table if not exists ai_mechanic_requests (
    id bigint generated always as identity primary key,
    user_id text not null,
    created_at timestamptz not null default now()
);

create index if not exists ai_mechanic_requests_user_window_idx
    on ai_mechanic_requests (user_id, created_at desc);

-- The Edge Function writes/reads this via the service-role key, bypassing RLS — clients never
-- query this table directly, so RLS is enabled with no policies (deny-by-default) rather than
-- left open.
alter table ai_mechanic_requests enable row level security;

-- Periodic cleanup so this table doesn't grow forever — run manually or via pg_cron once that
-- extension is enabled on the project; not wired up automatically here.
-- delete from ai_mechanic_requests where created_at < now() - interval '2 days';

-- Atomic check-and-record: the Edge Function originally did a separate SELECT count then
-- INSERT, which has a race — two concurrent requests from the same user could both pass the
-- count check before either INSERT lands, letting the limit be exceeded. A single SQL function
-- call is one statement from Postgres's perspective, so the count and the insert (when allowed)
-- happen under one atomic unit of work instead of two round-trips from the Edge Function.
create or replace function check_and_record_ai_mechanic_rate_limit(
    p_user_id text,
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
    -- Serializes concurrent calls for the SAME user_id only (advisory lock keyed by a hash of
    -- the user id) — other users' requests are never blocked by this.
    perform pg_advisory_xact_lock(hashtext('ai_mechanic_rate_limit:' || p_user_id));

    select count(*) into v_minute_count
    from ai_mechanic_requests
    where user_id = p_user_id and created_at >= now() - interval '1 minute';

    if v_minute_count >= p_minute_limit then
        return false;
    end if;

    select count(*) into v_day_count
    from ai_mechanic_requests
    where user_id = p_user_id and created_at >= date_trunc('day', now());

    if v_day_count >= p_day_limit then
        return false;
    end if;

    insert into ai_mechanic_requests (user_id, created_at) values (p_user_id, now());
    return true;
end;
$$;

-- Only the service role (used exclusively by the Edge Function) may call this — clients never
-- call it directly, same reasoning as the table's own RLS above.
revoke all on function check_and_record_ai_mechanic_rate_limit(text, int, int) from public, anon, authenticated;
grant execute on function check_and_record_ai_mechanic_rate_limit(text, int, int) to service_role;
