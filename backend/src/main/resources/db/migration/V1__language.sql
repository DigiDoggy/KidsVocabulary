-- Language reference table. Adding a language = a new row here (via a new migration).
-- Native (endonym) names for the UI come from frontend i18n resources, not from this table.
create table language (
    code varchar(5)  primary key,   -- ISO 639-1: 'ru', 'et', 'en'
    name varchar(50) not null       -- English name
);

insert into language (code, name) values
    ('ru', 'Russian'),
    ('et', 'Estonian'),
    ('en', 'English');
