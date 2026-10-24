-- Dados da pessoa candidata que o ATS usa como eliminatórios (anos, idioma, local, regime) e a meta semanal.
-- Linha única (id = 1); sem a linha, o sistema usa os padrões.

create table candidate_profile (
    id                            smallint                 not null primary key default 1 check (id = 1),
    years_of_experience           integer check (years_of_experience between 0 and 60),
    english_level                 varchar(15)
        check (english_level in ('NENHUM', 'BASICO', 'INTERMEDIARIO', 'AVANCADO', 'FLUENTE')),
    city                          varchar(120),
    state                         varchar(2),
    -- dias por semana no escritório que a pessoa aceita; nulo = não informado
    max_onsite_days               integer check (max_onsite_days between 0 and 5),
    accepted_contracts            text[]                   not null default '{CLT,PJ}',
    -- países (ISO-2) onde a pessoa pode trabalhar legalmente
    work_authorizations           text[]                   not null default '{BR}',
    education_level               varchar(25)
        check (education_level in ('MEDIO', 'TECNICO', 'SUPERIOR_INCOMPLETO', 'SUPERIOR', 'POS', 'MESTRADO',
                                   'DOUTORADO')),
    education                     text,
    certifications                text[]                   not null default '{}',
    -- pretensão mensal em reais: bruta em CLT, faturamento em PJ
    desired_salary_clt            numeric(12, 2),
    desired_salary_pj             numeric(12, 2),
    weekly_goal                   integer                  not null default 12 check (weekly_goal between 1 and 100),
    -- sugestão lida do currículo, para a pessoa confirmar
    suggested_years_of_experience integer,
    suggested_english_level       varchar(15),
    suggested_from                varchar(200),
    updated_at                    timestamp with time zone not null default now()
);

insert into candidate_profile (id) values (1);
