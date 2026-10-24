-- Backs the "Ask a Real Expert" paid consultation feature (Phase 3 — see PROJECT_NOTES.md for
-- the full phase breakdown). Written but never run against a real Supabase project yet — review
-- before applying, same status the ai_mechanic_requests migration had before its first deploy.
--
-- Administration (adding/editing/deactivating experts, creating availability slots) happens
-- entirely through the Supabase Dashboard's Table Editor, which runs as postgres and bypasses
-- RLS — not an in-app admin UI. That's the "protected administrative workflow" the product
-- owner asked for: their existing, already-authenticated Supabase login, nothing new to build
-- or secure separately.

create table if not exists experts (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    title text not null,
    bio text not null default '',
    years_experience int not null default 0,
    specializations text[] not null default '{}',
    photo_url text,
    active boolean not null default true,
    created_at timestamptz not null default now()
);

alter table experts enable row level security;

-- Public, pre-booking browsing — no auth required to see who's available. Only SELECT, and only
-- active rows; no policy at all exists for INSERT/UPDATE/DELETE, so no client (authenticated or
-- not) can ever write here — only the Dashboard (postgres, bypasses RLS) can.
create policy experts_public_read on experts for select
    using (active = true);

create table if not exists expert_availability_slots (
    id uuid primary key default gen_random_uuid(),
    expert_id uuid not null references experts(id) on delete cascade,
    start_time timestamptz not null,
    end_time timestamptz not null,
    status text not null default 'open' check (status in ('open', 'reserved', 'booked')),
    -- Set when status = 'reserved'; a reservation past this time is treated as if it were still
    -- 'open' (see reserve_consultation_slot below) rather than needing a cron job to clean up.
    reservation_expires_at timestamptz,
    created_at timestamptz not null default now(),
    constraint expert_availability_slots_time_order check (end_time > start_time)
);

create index if not exists expert_availability_slots_expert_idx
    on expert_availability_slots (expert_id, start_time);

-- Same reasoning as ai_mechanic_requests: the Edge Function (service role) is the only writer,
-- and the only reader for anything beyond "open" slots. Deny-by-default RLS here is
-- defense-in-depth, not the real access control — that's the Edge Function's own logic, same
-- division of responsibility as the rest of this backend.
alter table expert_availability_slots enable row level security;

create table if not exists consultations (
    id uuid primary key default gen_random_uuid(),
    booking_reference text not null unique,
    -- The verified Google ID token's `sub` claim — same identity source as ai_mechanic_requests'
    -- user_id, never a value the client can set directly.
    customer_sub text not null,
    expert_id uuid not null references experts(id),
    slot_id uuid not null references expert_availability_slots(id),
    status text not null default 'pending_payment'
        check (status in ('pending_payment', 'confirmed', 'cancelled', 'completed')),
    amount_inr int not null,
    -- AiVehicleContext.toJson() output — attached only with the customer's consent at booking
    -- time, per the explicit "do not require an OBD scan just to book an expert" requirement;
    -- null when no scan exists or the customer didn't choose to attach one.
    vehicle_context jsonb,
    created_at timestamptz not null default now(),
    -- The actual "two customers can't reserve the same slot" guarantee — enforced by the
    -- database itself, not just application logic. Combined with reserve_consultation_slot's
    -- advisory lock below, this is what makes the reservation atomic end to end.
    constraint consultations_slot_unique unique (slot_id)
);

create index if not exists consultations_customer_idx on consultations (customer_sub, created_at desc);

alter table consultations enable row level security;

create table if not exists consultation_payments (
    id uuid primary key default gen_random_uuid(),
    consultation_id uuid not null references consultations(id) on delete cascade,
    razorpay_order_id text,
    razorpay_payment_id text,
    amount_inr int not null,
    status text not null default 'created'
        check (status in ('created', 'paid', 'failed', 'refunded')),
    verified_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists consultation_payments_consultation_idx on consultation_payments (consultation_id);

-- Phase 4 (real payment verification) wires this table up; created now so the whole schema is
-- designed together rather than bolted on piecemeal. No RLS policies — same deny-by-default.
alter table consultation_payments enable row level security;

-- Atomic slot reservation + booking creation: without this, two concurrent requests for the
-- same slot could both pass an "is this open?" check before either UPDATE lands — the exact
-- class of race check_and_record_ai_mechanic_rate_limit already had to fix for this project
-- once. One function call is one atomic unit of work from Postgres's side.
--
-- Returns the new consultation's id, or null if the slot wasn't actually reservable (already
-- reserved/booked by someone else with a live reservation, or doesn't exist).
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
    -- Locks on this specific slot id only — concurrent reservations for DIFFERENT slots never
    -- block each other, only two attempts at the SAME slot ever serialize.
    perform pg_advisory_xact_lock(hashtext('consultation_slot:' || p_slot_id::text));

    select expert_id into v_expert_id
    from expert_availability_slots
    where id = p_slot_id
      and (
        status = 'open'
        or (status = 'reserved' and reservation_expires_at < now())
      )
    for update;

    if v_expert_id is null then
        return null;
    end if;

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

-- Cancels a booking owned by p_customer_sub and frees its slot back to 'open' in the same
-- transaction — a plain UPDATE from the Edge Function could cancel the booking but leave the
-- slot stuck 'booked' forever if the second statement never ran. Returns false (changes
-- nothing) if the booking doesn't exist, isn't this customer's, or is already
-- cancelled/completed — the Edge Function turns that into the right HTTP error, this function
-- just reports success/failure.
create or replace function cancel_consultation_booking(
    p_consultation_id uuid,
    p_customer_sub text
) returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_slot_id uuid;
begin
    select slot_id into v_slot_id
    from consultations
    where id = p_consultation_id
      and customer_sub = p_customer_sub
      and status in ('pending_payment', 'confirmed')
    for update;

    if v_slot_id is null then
        return false;
    end if;

    update consultations set status = 'cancelled' where id = p_consultation_id;
    update expert_availability_slots set status = 'open', reservation_expires_at = null where id = v_slot_id;

    return true;
end;
$$;

revoke all on function cancel_consultation_booking(uuid, text) from public, anon, authenticated;
grant execute on function cancel_consultation_booking(uuid, text) to service_role;
