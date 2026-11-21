-- A second lookup of the same registration number by the same user on the same (UTC) day is free:
-- it's served from vehicle_rc_cache, so it costs no Surepass call and shouldn't use up the daily
-- limit. A dealer re-checking the car they're making a report for was hitting the limit.
-- Only the first lookup of a number each day is recorded and counted.

create or replace function public.check_and_record_vehicle_lookup(
    p_user_id text, p_rc_number text, p_mode text, p_minute_limit integer, p_day_limit integer
) returns boolean
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
    v_minute_count int;
    v_day_count int;
begin
    perform pg_advisory_xact_lock(hashtext('vehicle_lookup_rate_limit:' || p_user_id));

    if exists (
        select 1 from vehicle_lookup_requests
        where user_id = p_user_id and rc_number = p_rc_number and created_at >= date_trunc('day', now())
    ) and exists (
        -- Only when the record is cached; a number whose lookup failed would otherwise be retried
        -- against Surepass (paid) with no limit at all. 30 days = CACHE_TTL_DAYS in the function.
        select 1 from vehicle_rc_cache
        where rc_number = p_rc_number and fetched_at >= now() - interval '30 days'
    ) then
        return true;
    end if;

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
$function$;
