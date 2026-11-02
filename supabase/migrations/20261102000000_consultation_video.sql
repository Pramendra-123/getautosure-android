-- Video calls for "Ask a Real Expert" (Phase 5): each confirmed consultation gets its own 100ms
-- room, created lazily the first time either side tries to join (see the consultations Edge
-- Function's ensureRoom). Lazy rather than at confirmation time so this works the same whether a
-- booking was confirmed by real payment verification (Phase 4) or by hand in the Dashboard while
-- testing.

alter table consultations
    -- The 100ms room for this booking; null until someone first joins.
    add column if not exists hms_room_id text,
    -- The host-role room code the mechanic's browser link redirects to. Created alongside the
    -- room on the mechanic's first join, then reused.
    add column if not exists hms_host_room_code text,
    -- Secret for the mechanic's join link (…/functions/v1/consultations?expert=<token>). Readable
    -- only through the Dashboard (RLS on this table has no client policies), which is where the
    -- owner copies it from to send to the mechanic until a mechanic panel exists. Two UUIDs give
    -- 244 random bits without needing pgcrypto.
    add column if not exists expert_join_token text not null
        default replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');

create unique index if not exists consultations_expert_join_token_idx on consultations (expert_join_token);
