-- Dealer Saathi "My reports" and report credits.
--
-- 1. vehicle_reports.sold_at: the dealer marks a car sold; the report stays live and listed.
-- 2. vehicle_report_outputs: where each report's PDF and WhatsApp Status picture are saved, so the
--    dealer can send them again from any phone. Its own table, not columns on vehicle_reports: that
--    table is publicly readable (vehicle_reports_public_read) and the PDF shows the full number
--    plate, which the public web page masks. Deny-by-default; only the Edge Function reads it.
-- 3. dealer_credit_ledger: every credit in or out — a pack bought (Razorpay, later), a report made.
--    The balance is the sum of delta, so it can be audited row by row and never drifts.
-- 4. charge_report_credit(): called by the Edge Function before a report is saved.

alter table vehicle_reports add column if not exists sold_at timestamptz;

create table if not exists vehicle_report_outputs (
    report_id uuid primary key references vehicle_reports(id) on delete cascade,
    pdf_path text,
    status_path text,
    updated_at timestamptz not null default now()
);
alter table vehicle_report_outputs enable row level security;

create table if not exists dealer_credit_ledger (
    id bigserial primary key,
    dealer_id uuid not null references dealers(id) on delete cascade,
    -- +N for a pack bought or a refund, -1 for a report, 0 for a report that cost nothing
    -- (a repeat of the same car, or made while payments weren't open yet) — kept for the record.
    delta int not null,
    reason text not null check (reason in ('purchase', 'grant', 'refund', 'report', 'repeat', 'free_period')),
    report_slug text,
    -- The payment provider's id for a purchase; unique so a retried webhook can't add a pack twice.
    payment_ref text unique,
    created_at timestamptz not null default now()
);
create index if not exists dealer_credit_ledger_dealer_idx on dealer_credit_ledger (dealer_id, created_at desc);
alter table dealer_credit_ledger enable row level security;

create or replace function dealer_credit_balance(p_dealer uuid)
returns int
language sql
stable
security definer
set search_path = public
as $$
    select coalesce(sum(delta), 0)::int from dealer_credit_ledger where dealer_id = p_dealer;
$$;

-- Returns what happened: 'charged' (one credit used), 'repeat' (a report for the same number plate
-- in the last 30 days — remaking it is free), 'free' (no credits, but payments aren't open yet), or
-- 'no_credits' (nothing recorded; the report must not be made). Locked per dealer so two reports
-- made at once can't both spend the last credit.
create or replace function charge_report_credit(p_dealer uuid, p_slug text, p_registration text, p_payments_live boolean)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_plate text := nullif(upper(regexp_replace(coalesce(p_registration, ''), '[\s-]', '', 'g')), '');
begin
    perform pg_advisory_xact_lock(hashtext('dealer_credit:' || p_dealer::text));

    if v_plate is not null and exists (
        select 1 from vehicle_reports
        where dealer_id = p_dealer
          and upper(regexp_replace(coalesce(registration_no, ''), '[\s-]', '', 'g')) = v_plate
          and created_at >= now() - interval '30 days'
    ) then
        insert into dealer_credit_ledger (dealer_id, delta, reason, report_slug) values (p_dealer, 0, 'repeat', p_slug);
        return 'repeat';
    end if;

    if dealer_credit_balance(p_dealer) > 0 then
        insert into dealer_credit_ledger (dealer_id, delta, reason, report_slug) values (p_dealer, -1, 'report', p_slug);
        return 'charged';
    end if;

    if not p_payments_live then
        insert into dealer_credit_ledger (dealer_id, delta, reason, report_slug) values (p_dealer, 0, 'free_period', p_slug);
        return 'free';
    end if;

    return 'no_credits';
end;
$$;

revoke all on function dealer_credit_balance(uuid) from public, anon, authenticated;
revoke all on function charge_report_credit(uuid, text, text, boolean) from public, anon, authenticated;
