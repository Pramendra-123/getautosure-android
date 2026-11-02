-- Weekly working hours for each mechanic, turned into bookable slots automatically, like Practo.
-- The owner sets each mechanic's hours once (Table Editor or SQL editor, same admin path as the
-- experts table); generate_expert_slots fills in 15-minute slots for the next week and is called
-- every time a customer opens that mechanic's availability, so there's no cron job to keep alive.
-- All schedule times are India time (Asia/Kolkata).

create table if not exists expert_weekly_hours (
    id uuid primary key default gen_random_uuid(),
    expert_id uuid not null references experts(id) on delete cascade,
    -- ISO weekday: 1 = Monday ... 7 = Sunday.
    weekday smallint not null check (weekday between 1 and 7),
    start_time time not null,
    end_time time not null,
    created_at timestamptz not null default now(),
    constraint expert_weekly_hours_time_order check (end_time > start_time)
);

create index if not exists expert_weekly_hours_expert_idx on expert_weekly_hours (expert_id, weekday);

-- Leave, holidays or a blocked afternoon. Open slots inside it disappear; booked ones stay.
create table if not exists expert_time_off (
    id uuid primary key default gen_random_uuid(),
    expert_id uuid not null references experts(id) on delete cascade,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    reason text,
    created_at timestamptz not null default now(),
    constraint expert_time_off_time_order check (ends_at > starts_at)
);

create index if not exists expert_time_off_expert_idx on expert_time_off (expert_id, starts_at);

-- Same deny-by-default as the other consultation tables: only the Dashboard and the service role
-- touch these.
alter table expert_weekly_hours enable row level security;
alter table expert_time_off enable row level security;

-- One slot per mechanic per start time, so generating twice never creates duplicates.
create unique index if not exists expert_availability_slots_expert_start_idx
    on expert_availability_slots (expert_id, start_time);

-- Creates this mechanic's slots for the next [p_days] days from their weekly hours, skipping time
-- off and anything already in the past, then removes open, never-booked future slots that no
-- longer fit (hours changed, or time off added). Hand-made slots are left alone for mechanics who
-- have no weekly hours yet. Returns how many slots were added.
create or replace function generate_expert_slots(
    p_expert_id uuid,
    p_days int default 7,
    p_slot_minutes int default 15,
    p_gap_minutes int default 5
) returns int
language plpgsql
security definer
set search_path = public
as $$
declare
    v_tz constant text := 'Asia/Kolkata';
    v_today date := (now() at time zone v_tz)::date;
    v_day date;
    v_hours record;
    v_start timestamptz;
    v_window_end timestamptz;
    v_added int := 0;
begin
    for v_day in select generate_series(v_today, v_today + p_days - 1, interval '1 day')::date loop
        for v_hours in
            select start_time, end_time from expert_weekly_hours
            where expert_id = p_expert_id and weekday = extract(isodow from v_day)
        loop
            v_start := (v_day + v_hours.start_time) at time zone v_tz;
            v_window_end := (v_day + v_hours.end_time) at time zone v_tz;
            while v_start + make_interval(mins => p_slot_minutes) <= v_window_end loop
                if v_start > now() and not exists (
                    select 1 from expert_time_off o
                    where o.expert_id = p_expert_id
                      and o.starts_at < v_start + make_interval(mins => p_slot_minutes)
                      and o.ends_at > v_start
                ) then
                    insert into expert_availability_slots (expert_id, start_time, end_time)
                    values (p_expert_id, v_start, v_start + make_interval(mins => p_slot_minutes))
                    on conflict (expert_id, start_time) do nothing;
                    if found then
                        v_added := v_added + 1;
                    end if;
                end if;
                v_start := v_start + make_interval(mins => p_slot_minutes + p_gap_minutes);
            end loop;
        end loop;
    end loop;

    if exists (select 1 from expert_weekly_hours where expert_id = p_expert_id) then
        delete from expert_availability_slots s
        where s.expert_id = p_expert_id
          and s.status = 'open'
          and s.start_time > now()
          and not exists (select 1 from consultations c where c.slot_id = s.id)
          and (
            exists (
                select 1 from expert_time_off o
                where o.expert_id = s.expert_id and o.starts_at < s.end_time and o.ends_at > s.start_time
            )
            or not exists (
                select 1 from expert_weekly_hours w
                where w.expert_id = s.expert_id
                  and w.weekday = extract(isodow from (s.start_time at time zone v_tz))
                  and (s.start_time at time zone v_tz)::time >= w.start_time
                  and (s.end_time at time zone v_tz)::time <= w.end_time
            )
          );
    end if;

    return v_added;
end;
$$;

revoke all on function generate_expert_slots(uuid, int, int, int) from public, anon, authenticated;
grant execute on function generate_expert_slots(uuid, int, int, int) to service_role;

-- Re-booking fix. The original unique (slot_id) allowed one consultation per slot ever, so a slot
-- freed by a cancellation or an expired payment hold looked open but failed when booked. Only one
-- *live* consultation per slot is the real rule.
alter table consultations drop constraint if exists consultations_slot_unique;
create unique index if not exists consultations_live_slot_idx on consultations (slot_id) where status <> 'cancelled';

-- Same as before, plus two changes: a lapsed hold's leftover pending_payment booking is closed
-- before the new one is made, and slots starting within the next 2 hours can't be booked (the
-- mechanic needs notice, and it matches the cancellation cutoff).
create or replace function reserve_consultation_slot(
    p_slot_id uuid,
    p_customer_sub text,
    p_booking_reference text,
    p_amount_inr int,
    p_vehicle_context jsonb,
    p_reservation_minutes int default 10
) returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_expert_id uuid;
    v_consultation_id uuid;
begin
    perform pg_advisory_xact_lock(hashtext('consultation_slot:' || p_slot_id::text));

    select expert_id into v_expert_id
    from expert_availability_slots
    where id = p_slot_id
      and start_time > now() + interval '2 hours'
      and (
        status = 'open'
        or (status = 'reserved' and reservation_expires_at < now())
      )
    for update;

    if v_expert_id is null then
        return null;
    end if;

    update consultations set status = 'cancelled'
    where slot_id = p_slot_id and status = 'pending_payment';

    update expert_availability_slots
    set status = 'reserved',
        reservation_expires_at = now() + make_interval(mins => p_reservation_minutes)
    where id = p_slot_id;

    insert into consultations (booking_reference, customer_sub, expert_id, slot_id, status, amount_inr, vehicle_context)
    values (p_booking_reference, p_customer_sub, v_expert_id, p_slot_id, 'pending_payment', p_amount_inr, p_vehicle_context)
    returning id into v_consultation_id;

    return v_consultation_id;
end;
$$;

revoke all on function reserve_consultation_slot(uuid, text, text, int, jsonb, int) from public, anon, authenticated;
grant execute on function reserve_consultation_slot(uuid, text, text, int, jsonb, int) to service_role;
