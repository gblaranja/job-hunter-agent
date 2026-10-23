-- Vaga nacional ou internacional. Nula só nas vagas anteriores a esta coluna, até o backfill da aplicação
-- classificá-las pelo idioma da descrição; toda vaga nova já entra classificada.

alter table job add column scope varchar(20) check (scope in ('NACIONAL', 'INTERNACIONAL'));

create index job_scope_idx on job (scope);
