-- Training sessions are stored as an append-only event log (answers and hints). The domain engine
-- is deterministic for a given seed, so replaying the events restores the exact session state,
-- including hint letters on the current word and idempotent answers. This replaces the
-- per-word / per-round snapshot tables, which were never used.

drop table attempt;
drop table round_item;
drop table session_word;

create table training_event (
    id           bigserial    primary key,
    session_id   bigint       not null references training_session(id) on delete cascade,
    seq          int          not null,                 -- 1, 2, 3 ... within the session
    kind         varchar(10)  not null,                 -- ANSWER/HINT
    week_word_id bigint       not null references week_word(id) on delete cascade,
    round_no     int          not null,
    answer_text  varchar(100),                          -- ANSWER only
    created_at   timestamptz  not null default now(),
    unique (session_id, seq),                           -- concurrent writers cannot both append
    check ((kind = 'ANSWER') = (answer_text is not null))
);
