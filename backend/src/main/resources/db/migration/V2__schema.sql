-- Core schema. Counters use int (maps to Java int without conversions).

create table parent_account (
    id                  bigserial    primary key,
    email               varchar(255) not null unique,
    password_hash       varchar(100) not null,
    pin_hash            varchar(100) not null,          -- PIN that guards parent mode
    timezone            varchar(50)  not null default 'Europe/Tallinn',
    failed_pin_attempts int          not null default 0,
    pin_locked_until    timestamptz,
    created_at          timestamptz  not null default now()
);

create table child (
    id                bigserial   primary key,
    parent_id         bigint      not null references parent_account(id) on delete cascade,
    name              varchar(50) not null,
    birth_date        date,
    level             varchar(2)  not null default 'A0',  -- A0/A1/A2
    native_lang       varchar(5)  not null references language(code),
    target_lang       varchar(5)  not null references language(code),
    default_week_size int         not null default 10 check (default_week_size between 1 and 50),
    archived          boolean     not null default false,
    created_at        timestamptz not null default now(),
    check (native_lang <> target_lang)
);
create index ix_child_parent on child(parent_id);

-- Refresh tokens are opaque random strings; only their SHA-256 hash is stored.
-- The token remembers the mode it was issued for, so refreshing never escalates a child session.
create table refresh_token (
    id         bigserial    primary key,
    parent_id  bigint       not null references parent_account(id) on delete cascade,
    token_hash varchar(64)  not null unique,
    mode       varchar(10)  not null,                     -- PARENT/CHILD
    child_id   bigint       references child(id) on delete cascade,
    expires_at timestamptz  not null,
    revoked    boolean      not null default false,
    created_at timestamptz  not null default now(),
    check ((mode = 'CHILD') = (child_id is not null))
);
create index ix_refresh_token_parent on refresh_token(parent_id);

create table concept (
    id              bigserial   primary key,
    topic           varchar(40),
    difficulty      int         not null default 1,       -- 1..3
    origin          varchar(10) not null,                 -- SEED/AI/MANUAL
    owner_parent_id bigint      references parent_account(id) on delete cascade, -- null = shared dictionary
    created_at      timestamptz not null default now()
);

create table word_form (
    id         bigserial   primary key,
    concept_id bigint      not null references concept(id) on delete cascade,
    lang       varchar(5)  not null references language(code),
    text       varchar(60) not null,
    normalized varchar(60) not null,
    is_primary boolean     not null default false,
    unique (concept_id, lang, normalized)
);
create index ix_word_form_lookup on word_form(lang, normalized);
create unique index ux_word_form_primary on word_form(concept_id, lang) where is_primary;

create table study_month (
    id           bigserial    primary key,
    child_id     bigint       not null references child(id) on delete cascade,
    seq_no       int          not null,
    status       varchar(10)  not null,                   -- ACTIVE/COMPLETED
    reward_text  varchar(255),
    total_points numeric(6,1),
    max_points   numeric(6,1),
    percent      numeric(5,2),
    result       varchar(10),                             -- REWARD/GOOD/REPEAT
    report       jsonb,                                   -- report snapshot
    completed_at timestamptz,
    unique (child_id, seq_no)
);
create unique index ux_month_active on study_month(child_id) where status = 'ACTIVE';

create table study_week (
    id                      bigserial   primary key,
    month_id                bigint      not null references study_month(id) on delete cascade,
    child_id                bigint      not null references child(id) on delete cascade,
    week_index              int         check (week_index between 1 and 4), -- assigned on launch
    start_date              date        not null,         -- Monday
    end_date                date        not null,         -- Sunday
    status                  varchar(10) not null,         -- DRAFT/SCHEDULED/ACTIVE/COMPLETED/EXPIRED
    word_count              int         not null,
    launched_at             timestamptz,
    first_pass_completed_at timestamptz,
    points                  numeric(5,1),
    max_points              numeric(5,1)
);
create unique index ux_week_child_date on study_week(child_id, start_date) where status <> 'DRAFT';

create table week_word (
    id               bigserial   primary key,
    week_id          bigint      not null references study_week(id) on delete cascade,
    concept_id       bigint      not null references concept(id),
    source           varchar(12) not null,                -- MANUAL/AI/DICTIONARY/CARRY_OVER
    prompt_text      varchar(60) not null,                -- frozen on launch
    primary_answer   varchar(60) not null,
    accepted_answers text[]      not null,                -- normalised
    -- first-pass result:
    attempt_no       int,                                 -- round in which it was solved
    hint_used        boolean,
    problematic      boolean,
    points           numeric(2,1),
    outcome          varchar(10),                         -- LEARNED/HARD/UNLEARNED
    unique (week_id, concept_id)
);

create table training_session (
    id            bigserial   primary key,
    week_id       bigint      not null references study_week(id) on delete cascade,
    kind          varchar(12) not null,                   -- FIRST_PASS/PRACTICE
    status        varchar(12) not null,                   -- IN_PROGRESS/COMPLETED/CLOSED
    current_round int         not null default 1,
    seed          bigint      not null,                   -- reproducible shuffling
    started_at    timestamptz not null default now(),
    completed_at  timestamptz,
    version       int         not null default 0          -- optimistic locking
);
create unique index ux_first_pass on training_session(week_id) where kind = 'FIRST_PASS';

create table session_word (
    session_id   bigint  not null references training_session(id) on delete cascade,
    week_word_id bigint  not null references week_word(id) on delete cascade,
    wrong_count  int     not null default 0,
    hint_used    boolean not null default false,
    problematic  boolean not null default false,
    solved_round int,
    primary key (session_id, week_word_id)
);

create table round_item (
    session_id   bigint  not null references training_session(id) on delete cascade,
    round_no     int     not null,
    position     int     not null,
    week_word_id bigint  not null references week_word(id) on delete cascade,
    hint_letters int     not null default 0,
    answered     boolean not null default false,
    primary key (session_id, round_no, position)
);

create table attempt (
    id           bigserial    primary key,
    session_id   bigint       not null references training_session(id) on delete cascade,
    week_word_id bigint       not null references week_word(id) on delete cascade,
    round_no     int          not null,
    answer_text  varchar(100) not null,
    correct      boolean      not null,
    hint_letters int          not null,
    created_at   timestamptz  not null default now(),
    unique (session_id, week_word_id, round_no)           -- protects against double submit
);

create table child_word (
    child_id      bigint      not null references child(id) on delete cascade,
    concept_id    bigint      not null references concept(id),
    status        varchar(10) not null,                   -- LEARNED/HARD/UNLEARNED
    times_studied int         not null default 1,
    last_week_id  bigint      references study_week(id),
    updated_at    timestamptz not null default now(),
    primary key (child_id, concept_id)
);

create table carry_over (
    id              bigserial   primary key,
    child_id        bigint      not null references child(id) on delete cascade,
    concept_id      bigint      not null references concept(id),
    from_month_id   bigint      not null references study_month(id),
    target_month_id bigint      references study_month(id), -- null = not yet placed
    reason          varchar(10) not null,                   -- HARD/UNLEARNED
    unique (child_id, concept_id, from_month_id)
);

create table ai_request_log (
    id          bigserial   primary key,
    provider    varchar(30) not null,
    status      varchar(20) not null,                     -- OK/INVALID/ERROR/RATE_LIMIT
    words_in    int,
    words_valid int,
    latency_ms  int,
    error       text,
    created_at  timestamptz not null default now()
);
