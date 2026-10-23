-- Nova etapa terminal: a vaga deixou de aceitar candidaturas (antes ou depois de você aplicar).

alter table application drop constraint application_status_check;
alter table application add constraint application_status_check
    check (status in ('NOVA', 'INTERESSE', 'APLICADA', 'TRIAGEM', 'ENTREVISTA', 'TESTE_TECNICO', 'OFERTA', 'RECUSADA',
                      'DESISTI', 'ENCERRADA'));

alter table application_event drop constraint application_event_from_status_check;
alter table application_event add constraint application_event_from_status_check
    check (from_status in ('NOVA', 'INTERESSE', 'APLICADA', 'TRIAGEM', 'ENTREVISTA', 'TESTE_TECNICO', 'OFERTA',
                           'RECUSADA', 'DESISTI', 'ENCERRADA'));

alter table application_event drop constraint application_event_to_status_check;
alter table application_event add constraint application_event_to_status_check
    check (to_status in ('NOVA', 'INTERESSE', 'APLICADA', 'TRIAGEM', 'ENTREVISTA', 'TESTE_TECNICO', 'OFERTA',
                         'RECUSADA', 'DESISTI', 'ENCERRADA'));
