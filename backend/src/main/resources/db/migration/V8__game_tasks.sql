-- Game modes (unscored): tasks the parent gives the child, and word-search grids.

-- A game task: one game mode with its own word list. Prepared as a draft, then given to the child.
create table game_task (
    id           bigserial   primary key,
    child_id     bigint      not null references child(id) on delete cascade,
    mode         varchar(12) not null,                    -- CHOICE/SCRAMBLE/WORD_SEARCH
    status       varchar(10) not null,                    -- DRAFT/GIVEN
    created_at   timestamptz not null default now(),
    given_at     timestamptz,
    times_played int         not null default 0,
    grid         jsonb,                                   -- WORD_SEARCH: built when the task is given
    check ((status = 'GIVEN') = (given_at is not null))
);
create index ix_game_task_child on game_task(child_id);

-- Texts are copied from the dictionary, like week words, so a given task never changes.
create table game_task_word (
    id          bigserial   primary key,
    task_id     bigint      not null references game_task(id) on delete cascade,
    concept_id  bigint      not null references concept(id),
    prompt_text varchar(60) not null,
    answer_text varchar(60) not null,
    unique (task_id, concept_id)
);

-- The word-search grid of a week's words, built the first time the child opens it.
create table week_word_search (
    week_id bigint primary key references study_week(id) on delete cascade,
    grid    jsonb  not null
);
