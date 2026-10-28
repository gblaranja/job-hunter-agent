-- Famílias de cargo de interesse de cada perfil de busca (B-25). Vazio aceita todas; com alguma escolhida, as vagas
-- coletadas de outra família saem da caixa como "sem perfil técnico".

alter table search_profile add column job_families text[] not null default '{}';
