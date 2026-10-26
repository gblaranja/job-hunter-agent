-- Teste "o que o ATS lê": avisos sobre o texto extraído de cada versão do currículo.
-- [{code, message}]; nulo enquanto a checagem não rodou.
alter table resume_version add column lint jsonb;
