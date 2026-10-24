-- Os alertas do LinkedIn trazem só título, empresa e local. A descrição é buscada depois, na página pública da vaga,
-- uma por vez e devagar; esta tabela guarda as tentativas para não insistir numa vaga sem descrição ou removida.

create table linkedin_description_fetch (
    job_id          bigint                   primary key references job (id) on delete cascade,
    status          varchar(20)              not null check (status in ('OK', 'SEM_DESCRICAO', 'REMOVIDA', 'ERRO')),
    attempts        smallint                 not null default 1,
    last_attempt_at timestamp with time zone not null
);
