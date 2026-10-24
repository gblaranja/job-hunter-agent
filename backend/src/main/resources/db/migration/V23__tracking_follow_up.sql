-- Follow-up agendado que para sozinho: o tipo da próxima ação, quantos follow-ups você já mandou e se a data foi
-- agendada pelo sistema (um retorno da empresa cancela só as automáticas).

alter table application
    add column next_action_kind varchar(20)
        check (next_action_kind in ('FOLLOW_UP', 'RESPONDER', 'ENTREVISTA', 'OUTRO')),
    add column follow_ups_sent  integer not null default 0,
    add column next_action_auto boolean not null default false;

-- Próximas ações anotadas antes desta versão não tinham tipo.
update application set next_action_kind = 'OUTRO' where next_action_at is not null;

-- Candidaturas recentes em Aplicada, sem próxima ação e sem retorno, ganham o primeiro follow-up (7 dias depois).
update application a
set next_action_at = a.applied_at + interval '7 days', next_action_kind = 'FOLLOW_UP', next_action_auto = true
where a.status = 'APLICADA' and a.next_action_at is null and a.applied_at >= now() - interval '30 days'
  and not exists (select 1 from application_event e where e.application_id = a.id and e.response_type is not null);

create index application_next_action_idx on application (next_action_at) where next_action_at is not null;
