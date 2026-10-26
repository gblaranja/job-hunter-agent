-- Duas classificações novas de e-mail: a confirmação de que a candidatura foi recebida (vira candidatura em
-- Aplicada sem digitar) e a abordagem de um recrutador ou agência (vira contato e oportunidade).

alter table email_message drop constraint email_message_ai_classification_check;
alter table email_message add constraint email_message_ai_classification_check check (ai_classification in
    ('ALERTA_VAGA', 'CONVITE_ENTREVISTA', 'TESTE_TECNICO', 'PEDIDO_INFORMACAO', 'OFERTA', 'RECUSA',
     'CONFIRMACAO_CANDIDATURA', 'CONTATO_RECRUTADOR', 'OUTRO', 'IRRELEVANTE'));

alter table email_message drop constraint email_message_final_classification_check;
alter table email_message add constraint email_message_final_classification_check check (final_classification in
    ('ALERTA_VAGA', 'CONVITE_ENTREVISTA', 'TESTE_TECNICO', 'PEDIDO_INFORMACAO', 'OFERTA', 'RECUSA',
     'CONFIRMACAO_CANDIDATURA', 'CONTATO_RECRUTADOR', 'OUTRO', 'IRRELEVANTE'));
