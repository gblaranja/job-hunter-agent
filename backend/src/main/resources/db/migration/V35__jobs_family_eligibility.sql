-- Família de cargo pelo título (B-25) e, para as vagas do exterior, de onde a pessoa pode ser contratada, o fuso
-- exigido e se a contratação é como contractor (B-24). As regras preenchem as colunas na entrada da vaga; as vagas
-- anteriores (classification_version nula ou de uma versão antiga das regras) são classificadas pelo backfill da
-- subida. A Claude também extrai família e elegibilidade (job_analysis), e a leitura prefere a dela quando existe.

alter table job
    add column job_family                varchar(20)
        check (job_family in ('BACKEND', 'FRONTEND', 'FULLSTACK', 'MOBILE', 'DADOS', 'DEVOPS_SRE', 'QA', 'SEGURANCA',
                              'GESTAO', 'FORA_DE_TI', 'OUTRA')),
    add column international_eligibility varchar(20)
        check (international_eligibility in ('BRASIL', 'LATAM', 'GLOBAL', 'PAIS_ESPECIFICO', 'NAO_INFORMADO')),
    add column required_timezone         varchar(60),
    add column contractor                boolean not null default false,
    add column classification_version    integer;

create index job_family_idx on job (job_family);

-- Descarte automático (família de cargo fora dos perfis de busca): o detalhe explica o motivo na tela.
alter table job_dismissal add column detail varchar(200);
