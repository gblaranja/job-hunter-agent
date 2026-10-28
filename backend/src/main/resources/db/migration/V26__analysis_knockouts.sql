-- Eliminatórios, skills cobertas em parte, evidências do currículo e salário extraídos das vagas.

alter table job_analysis
    -- [{type, text, value, status (ATENDE | NAO_ATENDE | SEM_INFORMACAO), reason}], avaliados contra candidate_profile
    add column knockouts                 jsonb not null default '[]',
    -- [{skill, via}]: requisito coberto por uma skill equivalente do currículo (vale metade)
    add column partial_skills            jsonb not null default '[]',
    -- [{requirement, quote}]: trecho literal do currículo que comprova o requisito
    add column evidence                  jsonb not null default '[]',
    -- {min, max, currency, period (MENSAL | ANUAL | HORA), regime (CLT | PJ | CONTRACTOR | null), text}
    add column salary                    jsonb,
    -- BACKEND, FRONTEND, FULLSTACK, MOBILE, DADOS, DEVOPS_SRE, QA, SEGURANCA, GESTAO, FORA_DE_TI ou OUTRA
    add column job_family                varchar(20),
    -- BRASIL, LATAM, GLOBAL, PAIS_ESPECIFICO ou NAO_INFORMADO: de onde a pessoa pode ser contratada
    add column international_eligibility varchar(20);
