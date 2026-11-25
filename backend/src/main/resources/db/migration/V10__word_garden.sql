-- Word garden: every learned word is a plant that grows with reviews.

alter table child_word
    add column plant_kind            smallint,               -- 0..7, picked once at random
    add column growth_stage          smallint,               -- 0 SEED .. 4 FRUIT
    add column review_interval_weeks smallint,               -- wait after the next successful review
    add column next_review_on        date,
    add column planted_week_id       bigint references study_week(id) on delete set null;

alter table child_word add constraint ck_child_word_plant check (
    (plant_kind is null and growth_stage is null and review_interval_weeks is null and next_review_on is null)
    or (plant_kind between 0 and 7 and growth_stage between 0 and 4
        and review_interval_weeks between 0 and 16 and next_review_on is not null));

create index ix_child_word_due on child_word(child_id, next_review_on) where plant_kind is not null;

-- Words learned before the garden existed are planted as seeds of the week they were learned in.
update child_word cw
set plant_kind            = floor(random() * 8)::smallint,
    growth_stage          = 0,
    review_interval_weeks = 1,
    next_review_on        = w.end_date + 7,
    planted_week_id       = w.id
from study_week w
where w.id = cw.last_week_id
  and cw.status in ('LEARNED', 'HARD');

-- Watering the garden is a training session of the child, not of a week.
alter table training_session alter column week_id drop not null;
alter table training_session add column child_id bigint references child(id) on delete cascade;
alter table training_session add constraint ck_session_owner check (
    (kind = 'REVIEW' and week_id is null and child_id is not null)
    or (kind <> 'REVIEW' and week_id is not null and child_id is null));
create unique index ux_review_open on training_session(child_id) where kind = 'REVIEW' and status = 'IN_PROGRESS';

-- The words of a review session: each one is trained with its latest frozen week word.
create table review_word (
    session_id   bigint not null references training_session(id) on delete cascade,
    week_word_id bigint not null references week_word(id) on delete cascade,
    concept_id   bigint not null references concept(id),
    primary key (session_id, week_word_id)
);
