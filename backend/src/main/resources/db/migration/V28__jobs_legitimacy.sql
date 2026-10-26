-- Legitimidade da vaga (vaga fantasma ou golpe) e deduplicação entre fontes.

alter table job
    -- ALTA, MEDIA, BAIXA ou SUSPEITA (golpe); nula até a primeira avaliação
    add column legitimacy          varchar(10)
        check (legitimacy in ('ALTA', 'MEDIA', 'BAIXA', 'SUSPEITA')),
    -- motivos em frases curtas, para mostrar na caixa e no detalhe
    add column legitimacy_reasons  jsonb not null default '[]',
    add column legitimacy_checked_at timestamp with time zone,
    -- a mesma vaga publicada em outra fonte: esta é a cópia, a referenciada é a canônica
    add column duplicate_of_job_id bigint references job (id) on delete set null;

create index job_duplicate_of_idx on job (duplicate_of_job_id) where duplicate_of_job_id is not null;
