-- Mechanic mode: a mechanic signs in to the app with Google, and the consultations function
-- matches the verified email in their sign-in token to this column. The owner fills it in once
-- per mechanic in the Table Editor; until then that mechanic sees "not linked yet".
alter table experts add column if not exists google_email text;

-- Stored lowercase so the lookup is a plain equality; one mechanic per Google account.
create unique index if not exists experts_google_email_idx on experts (lower(google_email)) where google_email is not null;

-- Replaces a mechanic's whole weekly schedule in one transaction, so a failed save never leaves
-- them with half their hours deleted. [p_hours] is a JSON array of
-- {"weekday": 1-7, "start": "HH:MM", "end": "HH:MM"}, already validated by the Edge Function.
create or replace function set_expert_weekly_hours(p_expert_id uuid, p_hours jsonb) returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    delete from expert_weekly_hours where expert_id = p_expert_id;
    insert into expert_weekly_hours (expert_id, weekday, start_time, end_time)
    select p_expert_id, h.weekday, h.start::time, h."end"::time
    from jsonb_to_recordset(p_hours) as h(weekday smallint, start text, "end" text);
end;
$$;

revoke all on function set_expert_weekly_hours(uuid, jsonb) from public, anon, authenticated;
grant execute on function set_expert_weekly_hours(uuid, jsonb) to service_role;
