-- Retornos: classificação dos e-mails, fila de revisão e retornos registrados à mão.

alter table email_message
    add column company_guess varchar(200),
    add column job_guess     varchar(300),
    add column ai_rationale  text,
    add column classified_at timestamp with time zone,
    -- precisa da sua revisão: confiança baixa ou nenhuma candidatura correspondente
    add column needs_review  boolean not null default false,
    add column reviewed_at   timestamp with time zone;

alter table email_message
    add constraint email_message_ai_classification_check check (ai_classification in
        ('ALERTA_VAGA', 'CONVITE_ENTREVISTA', 'TESTE_TECNICO', 'PEDIDO_INFORMACAO', 'OFERTA', 'RECUSA', 'OUTRO',
         'IRRELEVANTE')),
    add constraint email_message_final_classification_check check (final_classification in
        ('ALERTA_VAGA', 'CONVITE_ENTREVISTA', 'TESTE_TECNICO', 'PEDIDO_INFORMACAO', 'OFERTA', 'RECUSA', 'OUTRO',
         'IRRELEVANTE'));

create index email_message_review_idx on email_message (received_at desc) where needs_review;
create index email_message_unclassified_idx on email_message (received_at) where kind = 'OUTRO' and classified_at is null;

-- Por onde chegou o retorno (e-mail, WhatsApp, mensagem no LinkedIn, ligação).
alter table application_event
    add column response_channel varchar(10)
        check (response_channel in ('EMAIL', 'WHATSAPP', 'LINKEDIN', 'TELEFONE', 'OUTRO'));

create index application_event_email_idx on application_event (email_message_id) where email_message_id is not null;
