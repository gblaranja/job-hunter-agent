-- A classificação nacional/internacional passa a olhar também a localidade e o título, porque os alertas do
-- LinkedIn não trazem descrição. scope_locked marca a escolha feita por você (cadastro manual ou correção na tela),
-- que nenhuma reclassificação automática sobrescreve.

alter table job add column scope_locked boolean not null default false;

-- Ainda não havia como registrar correções; o backfill da subida reclassifica todas com a regra nova.
update job set scope = null;
