-- Vagas descartadas da caixa sem passar pelo kanban: sem interesse ou fora do seu perfil técnico.

create table job_dismissal (
    job_id     bigint                   primary key references job (id),
    reason     varchar(20)              not null check (reason in ('SEM_INTERESSE', 'SEM_PERFIL_TECNICO')),
    created_at timestamp with time zone not null default now()
);
