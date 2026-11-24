-- Boy or girl, so that texts for the child can use the right grammatical gender. Optional.
alter table child add column gender varchar(5) check (gender in ('BOY', 'GIRL'));
