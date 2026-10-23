-- Event Publication Registry do Spring Modulith 2.x (entidades JpaEventPublication).
-- Guarda os eventos de domínio até que cada listener termine, para reprocessar o que falhou.

create table event_publication (
    id                     uuid                     not null primary key,
    listener_id            text                     not null,
    event_type             text                     not null,
    serialized_event       text                     not null,
    publication_date       timestamp with time zone not null,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    integer                  not null default 0,
    last_resubmission_date timestamp with time zone
);

create index event_publication_serialized_event_hash_idx on event_publication using hash (serialized_event);
create index event_publication_by_completion_date_idx on event_publication (completion_date);

create table event_publication_archive (
    id                     uuid                     not null primary key,
    listener_id            text                     not null,
    event_type             text                     not null,
    serialized_event       text                     not null,
    publication_date       timestamp with time zone not null,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    integer                  not null default 0,
    last_resubmission_date timestamp with time zone
);

create index event_publication_archive_by_completion_date_idx on event_publication_archive (completion_date);
