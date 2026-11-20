-- Week planning rules.

-- A week joins a month (and gets its index) only when it becomes ACTIVE:
-- a set prepared in advance may end up in the next month.
alter table study_week alter column month_id drop not null;
alter table study_week add constraint ck_week_month_when_running
    check (status in ('DRAFT', 'SCHEDULED') or (month_id is not null and week_index is not null));

-- At most one prepared (draft or scheduled) week per child.
create unique index ux_week_one_pending on study_week(child_id) where status in ('DRAFT', 'SCHEDULED');

-- Carried-over words are tracked by the week they were placed into; null = still waiting.
alter table carry_over drop column target_month_id;
alter table carry_over add column placed_week_id bigint references study_week(id) on delete set null;
create index ix_carry_over_queue on carry_over(child_id) where placed_week_id is null;

-- Monday of the last week for which the system prepared a draft automatically.
alter table child add column last_auto_draft date;
