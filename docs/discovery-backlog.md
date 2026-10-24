# Discovery e backlog de melhorias

23/10/2026 · pesquisa de projetos abertos, sites de vagas, classificadores, ATS e ferramentas de recrutadores

> Complementa a [arquitetura](arquitetura.md), que descreve o sistema e as fases 1 a 6. Aqui está o que a pesquisa sugere fazer depois, por quê e em que ordem. Os números de fornecedores aparecem com a fonte e servem como direção; quem decide é o funil da sua própria instalação (seção 4).

## Resumo

- **O agente já cobre o ciclo de um tracker com IA**: coleta, score, kanban, retornos, insights e currículo por vaga. O que mais falta não é outra etapa, e sim os canais que mais convertem e a confiança nas vagas da caixa.
- **O canal pesa mais que o currículo.** No Ashby (38 milhões de candidaturas, 2021 a 2024), chegam à entrevista 40% dos indicados, 42% dos candidatos de agências, 25% de quem foi abordado por recrutador e 3% das candidaturas espontâneas. Hoje o agente só trabalha a candidatura espontânea.
- **Foco vence volume.** Nos dados da Huntr (1º trimestre de 2026), quem enviou de 11 a 20 candidaturas teve 9,25% de entrevistas por candidatura; quem enviou 100 ou mais, 2,58%. Aplicar 8 vezes ou mais na mesma empresa derruba a taxa para 1,91%, e o currículo adaptado dobra a taxa (4,23% contra 2,07%).
- **Parte da caixa não é vaga de verdade.** Uma análise do Greenhouse estimou que 18% a 22% das vagas publicadas não estavam sendo preenchidas. O projeto já guarda os sinais (republicação, link fora do ar, tempo no ar), mas não os mostra na caixa.
- **O que elimina é o eliminatório.** Os ATS cortam mais por perguntas e filtros duros (anos de experiência, local, idioma, autorização de trabalho) do que por IA, e a IA da Gupy ordena os candidatos lendo os campos Habilidades e Experiências do perfil. O score atual não separa eliminatórios, e o agente não cuida dos perfis nas plataformas.
- **Uma decisão não pode esperar (D-01):** a busca de descrições no endpoint `jobs-guest` do LinkedIn, em implementação no checkout principal e ligada por padrão, contraria o não-objetivo "sem scraping do LinkedIn" e a cláusula 8.2 dos termos do LinkedIn.
- **Ordem sugerida:** medir e decidir (onda 1), canais que convertem (onda 2) e vagas melhores na caixa (onda 3). O resto fica em Próximo e Depois, na seção 5.

## 1. Ponto de partida

| Área | O que já existe | Lacuna principal |
| --- | --- | --- |
| Coleta | Alertas do LinkedIn e da Gupy no Gmail; Greenhouse, Lever e Ashby por empresa; Remotive, GitHub (backend-br/vagas) e Adzuna, desligadas por padrão; vaga manual | Alertas chegam sem descrição; não há importação por link; SmartRecruiters, Workable e as páginas da Gupy estão fora |
| Identidade da vaga | Fingerprint exato (empresa, título e local normalizados); escopo nacional ou internacional; empresa normalizada com aliases | A mesma vaga em duas fontes, com título um pouco diferente, vira duas vagas |
| Análise | Dicionário de 77 skills; score 70/15/10/5 mais ajuste de ±10 da Claude; exemplos do seu feedback no prompt | Sem eliminatórios, sem skills equivalentes, sem evidência por requisito, sem salário |
| Funil | Kanban com 10 etapas; snapshot congelado; currículo por candidatura; retornos do Gmail com fila de revisão; canal da candidatura (`INDICACAO`, `EASY_APPLY`…) | Sem contatos, sem follow-up, sem caminho de indicação |
| Currículo | Upload com texto extraído; currículo adaptado por vaga (Opus) com DOCX e cobertura de palavras-chave; taxa de entrevista por versão | Não testa o que um ATS lê; não cuida dos perfis da Gupy e do LinkedIn |
| Empresas e insights | Métricas semanais, republicações, alertas; relatório semanal com diagnóstico CURRICULO/FILTRO | Não mede fonte, canal nem tempo até aplicar |

**Trabalho em andamento (23/10/2026):**

- A Fase 6 (nichos e selo GPTW) está no worktree `../job-hunter-agent-fase6`, branch `feat/fase-6-nichos`, com as migrações V19, V20 e V21 reservadas. As migrações deste backlog começam em **V22**. Os itens que tocam o módulo `companies` (B-06, B-13, B-19) ficam mais simples depois do merge da Fase 6, que mexe nele.
- O enriquecimento da descrição dos alertas do LinkedIn (`LinkedInDescriptions`, V17 e V18) está sem commit no checkout principal. Ver D-01.

## 2. O que a pesquisa encontrou

### 2.1 Projetos abertos

| Projeto | O que faz bem | O que trazer |
| --- | --- | --- |
| [career-ops](https://github.com/santifer/career-ops) (roda no Claude Code, MIT) | Avaliação A–F com pesquisa de remuneração, checagem de legitimidade e de autorização de trabalho; banco de histórias STAR; roteiros de negociação; cadência de follow-up e leitura das respostas; padrões de recusa por canal; detecção de repost; confere se a vaga está no ar antes de aplicar; varre mais de 100 empresas em Ashby, Greenhouse e Lever | Legitimidade, banco STAR, follow-up, métricas por canal, verificação antes de aplicar |
| [Fieldwork](https://www.producthunt.com/p/fieldwork-3/fieldwork-2) (auto-hospedado, MIT) | Fila de ações do dia, scorecards, rascunhos de mensagem que terminam num botão Copiar; a IA não pode inventar histórico | Fila do dia; rascunho em vez de envio |
| [JobTrackr](https://ca.unraid.net/apps/jobtrackr-1wudf9l0r45fc5), [Trackr](https://www.producthunt.com/products/trackr-7), [JobSync](https://github.com/Gsync/jobsync) | Kanban, contatos e lembretes de follow-up; extensão que recorta a vaga aberta no navegador | Contatos e lembretes; extensão |
| [Resume Matcher](https://github.com/srbhr/Resume-Matcher) (Apache 2.0) | Compara currículo e vaga por palavras-chave e similaridade vetorial; gera currículo adaptado com modelo local (Ollama) | Relatório de termos faltantes; modelo local como opção barata |
| [JobScout](https://pypi.org/project/jobscout/) (MIT) | Score por embeddings, com modo local sem chave de API | Pré-ranking local, se o volume crescer |
| [JobSpy](https://github.com/speedyapply/JobSpy) (MIT) | Raspa LinkedIn, Indeed, Glassdoor e Google ao mesmo tempo, com proxies | Só o modelo de dados: usar viola os termos dessas plataformas |
| [AIHawk](https://github.com/feder-cr/Jobs_Applier_AI_Agent_AIHawk) (AGPL) | Candidatura automática | Nada: é um não-objetivo, e volume derruba a conversão |

### 2.2 Sites de recomendação de vagas

| Site | Como recomenda | O que trazer |
| --- | --- | --- |
| LinkedIn (Job Match) | Cruza o perfil com cada requisito obrigatório e desejável da vaga; no Premium, mostra a faixa de aderência e o selo Top Applicant (os 10% com mais chance de resposta). O LinkedIn relata 7% menos candidaturas a vagas pouco aderentes e 22% mais às aderentes | Evidência requisito a requisito; chance de retorno |
| [Jobright.ai](https://jobright.ai/ai-job-match) | Score por skills, setor, senioridade e anos de experiência; copiloto que explica as lacunas; filtro de vagas suspeitas ou antigas; conexões na empresa | Dimensões de anos e setor; legitimidade; conexões |
| [HiringCafe](https://jobright.ai/blog/hiringcafe-review-2026-features-pros-cons-and-alternatives/) | Rastreia as páginas de carreira das empresas várias vezes ao dia; a IA extrai salário, anos de experiência e setor para os filtros; remove vagas fantasma | Coleta direto no ATS; extração estruturada para filtros |
| Welcome to the Jungle (ex-Otta) | Recomendação que aprende com as interações; faixa salarial por setor e experiência; selo de tempo de resposta da empresa | Aprender com o feedback; tempo de resposta por empresa |
| [Gupy](https://gupy.io/inteligencia-artificial) | Alerta por e-mail do cargo buscado; os agentes de IA leem a vaga e ordenam os candidatos pelos campos Habilidades e Experiências do perfil, ignorando dados sensíveis; a decisão final é humana. A Gupy diz que 60% das contratações saem das 10 primeiras recomendações da IA | Textos para o perfil Gupy (B-09) |

Na [Huntr](https://huntr.co/research/job-search-trends-q1-2026) (12 meses até o 1º trimestre de 2026, dados autodeclarados), a taxa de entrevista por fonte foi de 7,12% no Google, 5,02% no Wellfound, 3,14% no Indeed e 2,94% no LinkedIn. Vagas só remotas tiveram 3,63%, contra cerca de 5,7% das híbridas e presenciais. Vale medir o mesmo na sua instalação (B-01).

### 2.3 Classificadores e taxonomias

| Recurso | O que é | Uso aqui |
| --- | --- | --- |
| [ESCO](https://esco.ec.europa.eu/en/use-esco/use-esco-services-api/esco-web-service-api) | Taxonomia europeia de ocupações e skills, com API REST gratuita, URIs estáveis e rótulos nas línguas da UE, português incluído | Identificador e sinônimos pt/en para o dicionário (B-22) |
| [O*NET Web Services](https://services.onetcenter.org/about) | Busca por título que devolve o código O*NET-SOC; exige cadastro; foco nos EUA | Só se as vagas internacionais crescerem |
| [Lightcast Open Skills](https://docs.lightcast.io/lightcast-api/docs/free-api-access) | Mais de 33 mil skills; gratuito só para iniciativas sem fins lucrativos, com extração limitada | Não depender |
| [JobBERT-v2](https://huggingface.co/onnx-community/JobBERT-v2-ONNX) (TechWolf) | Encoder que normaliza títulos de vaga a partir das skills associadas; tem versão ONNX | Família de cargo sem LLM (B-25) |
| [CBO 2002](https://concla.ibge.gov.br/classificacoes/por-tema/ocupacao/classificacao-brasileira-de-ocupacoes) (MTE) | Código oficial das ocupações, usado nos registros de admissão (eSocial, Novo CAGED) | Salário de admissão por ocupação e UF (B-23, layout dos microdados a confirmar) |

**Deduplicação.** Nenhuma técnica sozinha resolve. Num estudo de 2024 ([arXiv 2406.06257](https://arxiv.org/pdf/2406.06257)), o melhor resultado veio de comparar strings, embeddings e listas de skills juntos, e a [Textkernel](https://www.textkernel.com/newsroom/online-job-postings-have-many-duplicates-but-how-can-you-detect-them-if-they-are-not-exact-copies-of-each-other/), que usa MinHash em produção, observa que duplicatas reais podem ter só 37% de similaridade de texto.

**Vagas fantasma e golpes.** Os sinais mais citados são vaga com mais de 30 dias, republicação em ciclo, vaga que não aparece na página de carreiras e ausência de recrutador nomeado. Salário ausente é sinal fraco: na Huntr, vagas sem salário tiveram mais entrevistas (6,42%) que as com salário (4,47%), e no Brasil a maioria não informa. Em golpes, o sinal forte é o pedido de pagamento; depois vêm contato só por WhatsApp ou Telegram, e-mail gratuito em nome de empresa grande, salário muito acima do mercado e urgência.

### 2.4 ATS

**APIs públicas de vagas.** Greenhouse, Lever e Ashby já estão integradas. A [SmartRecruiters](https://developers.smartrecruiters.com/docs/endpoints) documenta `GET /v1/companies/{id}/postings`, e a descrição vem no detalhe de cada vaga. Workable (`/api/accounts/{conta}`) e Teamtailor (`/jobs.json`) têm feeds públicos conhecidos pela comunidade, sem documentação oficial. O Workday tem um endpoint interno, `POST /wday/cxs/{tenant}/{site}/jobs`, não documentado e que algumas empresas bloqueiam para IPs de datacenter. Dois cuidados ([dev.to](https://dev.to/scrapemint/every-major-ats-has-a-public-json-api-for-job-openings-nobody-uses-them-f9j)): na Lever, empresa inexistente e empresa sem vagas devolvem a mesma lista vazia; e as empresas trocam de ATS (a Plaid saiu da Lever para a Ashby).

**Gupy e ATS brasileiros.** A API oficial da Gupy é autenticada e serve às empresas. O portal público é usado por scrapers, mas o endpoint não é documentado (o caminho testado devolveu 404). Não encontrei API pública da InHire, da Solides nem de outros ATS nacionais.

**JSON-LD `JobPosting`.** É o formato que o [Google for Jobs](https://developers.google.com/search/docs/appearance/structured-data/job-posting) lê nas páginas de carreira: título, empresa, `baseSalary`, `validThrough`, `jobLocationType: TELECOMMUTE` e `applicantLocationRequirements`. Um leitor genérico desse formato importa vagas de qualquer página que o publique (B-15).

**Como os ATS filtram.** A maior parte dos cortes vem de regras configuradas pelo recrutador: perguntas eliminatórias, pontuação mínima e filtros duros de localização ou certificação ([metaintro](https://www.metaintro.com/blog/how-ai-and-ats-read-your-resume-2026)). O que sobra é ordenado, e a busca do recrutador dentro do ATS é por termo literal. O [Jobscan](https://jobscan.co/jobscan-tutorial), que imita esse comportamento, pesa primeiro as hard skills, depois a formação (quando a vaga pede pós-graduação), o título do cargo e as soft skills, e só conta o termo exato.

### 2.5 Headhunters e ferramentas de recrutador

| Fonte | O que mostra | O que significa aqui |
| --- | --- | --- |
| [Ashby, referrals](https://www.ashbyhq.com/talent-trends-report/reports/referrals) (38 milhões de candidaturas em 93 mil vagas) | Chegam à entrevista: indicação 40%, agência 42%, abordado por recrutador 25%, espontânea 3%. Da entrevista à oferta: indicação 16%, agência 8%, abordado e espontânea 6% | Indicação, agências e ser encontrado valem mais que qualquer ajuste de currículo |
| [Gem](https://www.gem.com/blog/silver-medalists-candidate-rediscovery) | CRM de talentos; redescoberta de finalistas não contratados ("silver medalists"); sequências automáticas com gatilhos por marco | Contatos, follow-up agendado e volta às empresas onde você foi finalista |
| [Juicebox/PeopleGPT](https://info.juicebox.ai/what-is-juicebox), [LinkedIn Recruiter](https://training.talent.linkedin.com/path/use-boolean-in-linkedin-recruiter) | Busca em linguagem natural ou booleana em centenas de milhões de perfis, inclusive GitHub e Stack Overflow | O perfil precisa ter os termos literais que o recrutador digita |
| [Eightfold](https://eightfold.ai/engineering-blog/ai-powered-talent-matching-the-tech-behind-smarter-and-fairer-hiring/) | Inferência de skills adjacentes com embeddings e modelos explicáveis | Skills equivalentes com crédito parcial (B-12) |
| [noon.ai](https://www.noon.ai/blog/articles/243-candidate-response-time-benchmarks) (dados de fornecedor) | Cerca de 90% das respostas a abordagens chegam em 64 a 76 horas, e 97% em 30 dias | Um follow-up depois de alguns dias úteis, no máximo dois |

### 2.6 Ferramentas para quem procura emprego

- **[Teal](https://www.unite.ai/tealhq-review), [Huntr](https://huntr.co/product/contact-tracker) e [Simplify](https://simplify.jobs/copilot):** extensão que salva a vaga de dezenas de sites, match score com palavras-chave faltantes, contatos e lembretes por vaga. O Simplify registra sozinho a candidatura enviada.
- **Jobscan:** match rate de 0 a 100%, com meta de 75% ou mais.
- **Preparação de entrevista ([VirtualSpeech](https://virtualspeech.com/blog/ai-prepare-job-interview)):** perguntas geradas a partir da vaga e do currículo, feedback no formato STAR e um banco de 6 a 10 histórias, que a IA só lapida.
- **Mercado:** as candidaturas por contratação triplicaram desde 2021 e passam de 300 em média ([Ashby, 2026](https://www.hrdive.com/news/recruiters-see-job-applications-triple-to-more-than-300-per-role/820096/)); a primeira semana de uma vaga recebe cerca do dobro das candidaturas das semanas seguintes ([Ashby, 2023](https://www.ashbyhq.com/talent-trends-report/reports/2023-trends-report-applications-per-job)); 92% dos líderes de recrutamento dizem que currículos escritos por IA viraram comuns ([Deel](https://www.deel.com/deel-works/ai-resume-crisis)).

## 3. Insights

1. **O canal pesa mais que o currículo.** Indicação e agência levam mais de dez vezes mais candidaturas à entrevista que a candidatura espontânea. → Contatos, conexões e rascunhos de indicação entram na onda 2 (B-05 a B-08).
2. **Foco e velocidade.** Poucas candidaturas aderentes, enviadas cedo, rendem mais que volume. → Fila do dia com meta semanal, frescor no ranking e alerta imediato (B-16, B-17).
3. **O que elimina é o eliminatório.** Anos de experiência, idioma, local e autorização de trabalho derrubam antes de qualquer score. → Eliminatórios separados das skills (B-11).
4. **A caixa precisa ser confiável.** Vagas fantasma, golpes e duplicatas gastam a melhor hora do dia. → Legitimidade e deduplicação entre fontes (B-13, B-14).
5. **Score explicado vira ação.** O LinkedIn e o Jobright mostram o porquê requisito a requisito, e isso também diz o que adaptar no currículo. → Skills equivalentes e evidência por requisito (B-12, B-21).
6. **Dá para ter cobertura sem violar termos.** ATS direto, JSON-LD e captura por clique trazem a descrição completa sem scraping do LinkedIn. → D-01, B-14, B-15 e B-29.
7. **Os dados da sua instalação valem mais que benchmark de fornecedor.** Os números da seção 2 vêm de outros mercados e de bases de clientes. → Medir fonte, canal, tempo até aplicar e adaptação antes de mexer em pesos (B-01, B-02).

## 4. Hipóteses e como validar

Todas usam dados que o agente já guarda ou passa a guardar na onda 1. Com uma pessoa só, as amostras são pequenas: trate os resultados como sinal, revise a cada 20 candidaturas e não espere significância estatística.

| # | Hipótese | Como medir | Segue se |
| --- | --- | --- | --- |
| H1 | Pedir indicação nas vagas com score ≥ 75 aumenta a taxa de entrevista | Entrevistas por candidatura com canal `INDICACAO` contra `SITE_EMPRESA` e `EASY_APPLY` | A taxa com indicação for pelo menos o dobro |
| H2 | Aplicar até 72 horas depois de a vaga aparecer aumenta o retorno | `application.applied_at − job.first_seen_at` contra a primeira resposta | A diferença for de 1,5 vez ou mais; aí o frescor ganha peso no ranking |
| H3 | O currículo adaptado converte mais que o padrão | Candidaturas cujo currículo veio de `tailored_resume.resume_version_id` contra as demais | O ganho se confirmar; senão, rever o prompt de adaptação |
| H4 | As 10 primeiras vagas da caixa concentram as que você escolhe | Precision@10 com interesse, feedback GOSTEI e descartes | Abaixo de 50%, recalibrar os pesos (B-35) |
| H5 | Mostrar os eliminatórios evita recusas automáticas | Recusas em até 3 dias, antes e depois do B-11 | A queda for de 30% ou mais |
| H6 | A extração da Claude acerta as obrigatórias | Precisão e recall no conjunto anotado (B-02) | 90% ou mais; abaixo disso, revisar o prompt antes do v2 |
| H7 | Vagas com legitimidade baixa quase não dão retorno | Taxa de retorno por faixa de legitimidade (B-13) | Se confirmar, esconder a faixa baixa por padrão |

## 5. Backlog

**Escalas.** Impacto: Alto, Médio ou Baixo. Esforço: P (até meio dia), M (1 a 2 dias) ou G (3 dias ou mais). Horizonte: Agora (ondas 1 a 3), Próximo ou Depois.

**Como executar.** Cada onda vira uma ou mais features em `.specs/features/` (o fluxo spec-driven já usado no projeto) e um branch `feat/*`. Migrações a partir de V22: liste as migrações de novo antes de criar uma, porque outras sessões trabalham em paralelo.

### 5.1 Decisões pendentes

| # | Decisão | Recomendação |
| --- | --- | --- |
| D-01 | A busca de descrições no endpoint `jobs-guest` do LinkedIn (em implementação, ligada por padrão) contraria o não-objetivo do projeto e a cláusula 8.2 dos [termos do LinkedIn](https://www.linkedin.com/legal/user-agreement), que proíbe usar software, scripts ou robôs para copiar dados do serviço. No caso hiQ, o LinkedIn [ganhou por quebra de contrato](https://www.privacyworld.blog/2022/11/federal-court-rules-in-favor-of-linkedins-breach-of-contract-claim-after-six-years-of-cfaa-data-scraping-litigation/) | Desligar por padrão (`JOBHUNTER_LINKEDIN_DESCRIPTIONS_ENABLED=false`), explicar o risco no README e trazer a descrição pelo B-14 (a mesma vaga no ATS da empresa), pelo B-15 e pelo B-29. O custo: vagas do LinkedIn sem equivalente num ATS ficam sem descrição até você colar o texto. Combinar com a sessão que está implementando |
| D-02 | Canal do alerta e do resumo no celular | ntfy (push auto-hospedado, um POST HTTP) ou bot do Telegram. E-mail pelo Gmail exigiria o escopo `gmail.send`, além do `readonly` de hoje |
| D-03 | Extensão do navegador no LinkedIn | A mesma cláusula 8.2 cita plugins de navegador que copiam dados, e Teal, Huntr e Simplify fazem isso por clique. Capturar só por clique, só a vaga aberta, nunca em lote, e preferir o JSON-LD ou o ATS quando a vaga existir lá |
| D-04 | Embeddings e pgvector | Adiar até o volume justificar (centenas de vagas novas por dia, ou custo do LLM acima do limite mensal). Medir antes com o B-01 e o B-02 |
| D-05 | Dados de terceiros (contatos e conexões) | Guardar só nome, empresa, cargo, link do perfil e histórico de contato; e-mail e telefone só se você anotar; permitir apagar (LGPD) |

### 5.2 Visão geral

| # | Item | Tema | Impacto | Esforço | Horizonte |
| --- | --- | --- | --- | --- | --- |
| B-01 | Métricas de fonte, canal, tempo e adaptação | Foco | Alto | M | Agora · onda 1 |
| B-02 | Conjunto anotado para avaliar extração e ranking | Score | Médio | M | Agora · onda 1 |
| B-03 | Saúde das fontes e alertas não reconhecidos | Plataforma | Médio | P | Agora · onda 1 |
| B-04 | Registro de fontes e termos de uso | Plataforma | Médio | P | Agora · onda 1 |
| B-05 | Contatos (CRM leve) | Canais | Alto | M | Agora · onda 2 |
| B-06 | Conexões do LinkedIn por empresa (export oficial) | Canais | Alto | M | Agora · onda 2 |
| B-07 | Rascunhos de mensagem | Canais | Alto | P | Agora · onda 2 |
| B-08 | Follow-up agendado que para sozinho | Canais | Alto | P | Agora · onda 2 |
| B-09 | Textos dos perfis Gupy e LinkedIn | Currículo | Alto | P | Agora · onda 2 |
| B-10 | Teste "o que o ATS lê" | Currículo | Médio | P | Agora · onda 2 |
| B-11 | Eliminatórios separados das skills | Score | Alto | M | Agora · onda 3 |
| B-12 | Skills equivalentes e hierarquia no score | Score | Médio | P | Agora · onda 3 |
| B-13 | Legitimidade da vaga | Qualidade | Alto | M | Agora · onda 3 |
| B-14 | Deduplicação entre fontes | Qualidade | Alto | M | Agora · onda 3 |
| B-15 | Importar vaga por link (JSON-LD) | Fontes | Alto | M | Agora · onda 3 |
| B-16 | Frescor no ranking e alerta imediato | Foco | Médio | P | Agora · onda 3 |
| B-17 | Fila do dia | Foco | Alto | M | Agora · onda 3 |
| B-18 | Abordagens de recrutadores e agências | Canais | Alto | M | Próximo |
| B-19 | Volta às empresas onde você foi finalista | Canais | Médio | P | Próximo |
| B-20 | Resumo diário no celular | Foco | Médio | P | Próximo |
| B-21 | Evidência por requisito | Score | Médio | M | Próximo |
| B-22 | Dicionário de skills que cresce com revisão | Score | Médio | M | Próximo |
| B-23 | Salário normalizado e CLT × PJ | Score | Médio | M | Próximo |
| B-24 | Elegibilidade das vagas internacionais | Score | Médio | P | Próximo |
| B-25 | Família de cargo | Score | Médio | M | Próximo |
| B-26 | Alerta de golpe | Qualidade | Médio | P | Próximo |
| B-27 | Conferir o link antes de aplicar | Qualidade | Baixo | P | Próximo |
| B-28 | SmartRecruiters, Workable, Teamtailor e Recruitee | Fontes | Médio | M | Próximo |
| B-29 | Extensão "Salvar vaga" | Fontes | Alto | G | Próximo |
| B-30 | Cobertura de palavras-chave no currículo adaptado | Currículo | Médio | P | Próximo |
| B-31 | Respostas de triagem e carta de apresentação | Currículo | Médio | P | Próximo |
| B-32 | Preparação para entrevista | Entrevistas | Alto | M | Próximo |
| B-33 | Banco de histórias STAR | Entrevistas | Médio | M | Próximo |
| B-34 | Orçamento mensal do LLM por tarefa | Plataforma | Baixo | P | Próximo |
| B-35 | Pesos calibrados pelo seu histórico | Score | Médio | G | Depois |
| B-36 | Pré-ranking semântico com pgvector | Score | Baixo | G | Depois |
| B-37 | Páginas de carreira da Gupy por empresa | Fontes | Médio | M | Depois |
| B-38 | Workday das empresas-alvo | Fontes | Médio | M | Depois |
| B-39 | Registro pós-entrevista | Entrevistas | Médio | P | Depois |
| B-40 | Comparação de ofertas e negociação | Entrevistas | Médio | M | Depois |
| B-41 | Exportar e apagar dados | Plataforma | Baixo | P | Depois |

### 5.3 Onda 1 — medir e decidir

*Pronta quando* a tela Insights mostra o funil por fonte, canal, tempo até aplicar e adaptação, existe uma régua para comparar versões de prompt e o D-01 está decidido.

**B-01 · Métricas de fonte, canal, tempo e adaptação** (`insights`, `tracking`)

- *Por quê:* as hipóteses H1 a H3 dependem desses cortes, e os benchmarks da seção 2 são de outros mercados.
- *Como:* no `InsightAggregator`, a taxa Aplicada → Triagem ou além por `job.source`, por `application.channel`, por tempo até aplicar (até 1 dia, 1 a 3, 4 a 7, mais de 7) e por currículo adaptado contra padrão; o tempo até a primeira resposta por empresa, que vira o "tempo de resposta" no detalhe da empresa. Campos novos em `insight_report` (jsonb, numa migração nova) e cards na tela Insights.
- *Pronto quando:* cada corte mostra o número de candidaturas por grupo, e grupos com menos de 5 aparecem como "poucos dados".

**B-02 · Conjunto anotado para avaliar extração e ranking** (`analysis`)

- *Por quê:* B-11, B-12 e B-13 mudam prompt e pesos; sem uma régua, não dá para saber se melhorou (H4, H6).
- *Como:* 40 vagas reais anonimizadas em `src/test/resources/fixtures/golden/`, com obrigatórias, desejáveis, senioridade, modelo de trabalho e eliminatórios anotados à mão; um teste com tag própria, fora do `verify` e do CI, que roda com `JOBHUNTER_AI_API_KEY` e imprime precisão e recall por campo e por versão de prompt; uma consulta de precision@10 da caixa contra interesse, GOSTEI e descartes.
- *Pronto quando:* o relatório compara o `extract-requirements.v1` com a próxima versão nas mesmas 40 vagas.

**B-03 · Saúde das fontes e alertas não reconhecidos** (`sourcing`)

- *Por quê:* os parsers de alerta quebram quando o remetente muda o HTML (o commit `6b15569` corrigiu os cards do LinkedIn), e a coleta falha em silêncio.
- *Como:* a partir de `collection_run.stats`, um quadro em Configurações com a última vaga nova, os erros e os bloqueios (HTTP 429) de cada fonte; notificação quando uma fonte ligada passa 3 dias sem vaga nova; o e-mail da label `jobs` que parece alerta mas não gerou vaga fica registrado (id do Gmail e remetente) para virar fixture.
- *Pronto quando:* um alerta com HTML novo aparece como "não reconhecido" no mesmo dia.

**B-04 · Registro de fontes e termos de uso** (README, `sourcing`)

- *Por quê:* cada fonte tem regras próprias (a Remotive pede crédito e poucas consultas por dia, a Adzuna exige chave, o LinkedIn proíbe scraping), e hoje isso está espalhado em comentários.
- *Como:* uma tabela no README com a fonte, como ela coleta, o que os termos dizem, se vem ligada por padrão e o risco. Fontes com risco de termos vêm desligadas. Fecha o D-01.
- *Pronto quando:* todo valor de `JobOrigin` aparece na tabela, e nenhuma fonte de risco vem ligada por padrão.

### 5.4 Onda 2 — canais que convertem

*Pronta quando* cada vaga mostra quem você conhece na empresa, você copia um pedido de indicação em um clique e nenhuma candidatura fica sem follow-up.

**B-05 · Contatos (CRM leve)** (módulo novo `network`)

- *Por quê:* indicação e agência levam 40% e 42% das candidaturas à entrevista, contra 3% da espontânea (Ashby); Huntr, Teal e JobTrackr guardam contatos por vaga.
- *Como:* tabelas `contact` (nome, papel — RECRUTADOR, HEADHUNTER, GESTOR, INDICACAO, COLEGA —, empresa, link do perfil, notas) e `contact_interaction` (data, canal, resumo, candidatura opcional); contatos na candidatura, na empresa e no detalhe da vaga; o retorno manual (`ResponseDialog`) pode apontar o contato. Dados mínimos, conforme o D-05.
- *Pronto quando:* dá para registrar que uma recrutadora da empresa X falou com você pelo LinkedIn sobre a vaga Y, e isso aparece na linha do tempo da candidatura.

**B-06 · Conexões do LinkedIn por empresa** (`network`, `companies`)

- *Por quê:* o caminho da indicação começa por saber quem você conhece na empresa. O LinkedIn permite [exportar as conexões](https://linkedapi.io/guides/export-linkedin-connections) (Configurações → Privacidade dos dados → Obter uma cópia dos seus dados), num `Connections.csv` com nome, link do perfil, empresa, cargo e data da conexão; o e-mail só vem quando a pessoa permite.
- *Como:* upload do CSV; casamento da empresa pelo `CompanyNameNormalizer` e pelos aliases; "3 conexões" na caixa, no detalhe da vaga e na empresa, com o filtro "tenho conexão"; reimportar atualiza sem duplicar. Nenhuma automação na sua conta.
- *Pronto quando:* depois do upload, as vagas das empresas onde você tem conexões mostram quem são e o cargo de cada uma.

**B-07 · Rascunhos de mensagem** (`network`, `shared.ai`)

- *Por quê:* pedir indicação e fazer follow-up são as ações de maior retorno e as que mais se adiam; o career-ops e o Fieldwork geram rascunhos e nunca enviam.
- *Como:* botões "pedir indicação", "follow-up", "agradecer a entrevista" e "responder recrutador" geram o texto com a vaga, a análise e o contato (Haiku); o convite do LinkedIn tem no máximo 300 caracteres; botão Copiar, sem envio. Valem as regras de veracidade do currículo adaptado.
- *Pronto quando:* numa vaga com conexão, você copia um pedido de indicação pronto em um clique.

**B-08 · Follow-up agendado que para sozinho** (`tracking`, `notifications`)

- *Por quê:* ferramentas de recrutador como o Gem automatizam sequências de contato. Para quem procura emprego, o equivalente é um follow-up agendado, e a maioria das respostas chega em poucos dias (noon.ai).
- *Como:* ao mover para APLICADA, o `next_action_at` vai para D+7 (configurável) com a ação "follow-up", e um segundo para D+14; um retorno classificado no Gmail (`EmailResponseHandler`), um retorno manual ou a mudança para RECUSADA, DESISTI ou ENCERRADA cancela os pendentes; os vencidos aparecem no kanban e na fila do dia (B-17).
- *Pronto quando:* uma candidatura sem retorno pede follow-up no 7º dia, e uma que recebeu resposta não pede.

**B-09 · Textos dos perfis Gupy e LinkedIn** (`insights`)

- *Por quê:* a IA da Gupy ordena os candidatos pelos campos Habilidades e Experiências, e 60% das contratações saem das 10 primeiras recomendações; recrutadores acham pessoas por busca literal ou booleana, e quem é abordado chega à entrevista em 25% dos casos (Ashby).
- *Como:* o `ResumeAdvisor`, que já sugere `linkedinKeywords`, passa a gerar o texto dos campos Habilidades e Experiências da Gupy e o título, o Sobre e as Competências do LinkedIn, com os termos mais pedidos nas vagas que o currículo comprova, e mostra o que mudou desde o relatório anterior (prompt `resume-advice.v2`).
- *Pronto quando:* o relatório semanal traz os textos para colar nos dois perfis, sem nenhuma skill que o currículo não mostre.

**B-10 · Teste "o que o ATS lê"** (`resumes`)

- *Por quê:* o ATS ordena pelo texto que consegue extrair; colunas, tabelas e cabeçalhos em imagem quebram a extração, e o problema só aparece como recusa.
- *Como:* o texto que o Tika já extrai no upload passa por uma checagem: contato encontrado (e-mail, telefone, LinkedIn), seções com nomes padrão, uma data por experiência em ordem decrescente, caracteres corrompidos e texto curto demais para o número de páginas. Os avisos aparecem em Currículos, ao lado de cada versão, inclusive as geradas pelo currículo adaptado.
- *Pronto quando:* um PDF de duas colunas mostra os avisos correspondentes, e o DOCX do currículo adaptado passa sem avisos.

### 5.5 Onda 3 — vagas melhores na caixa

*Pronta quando* a caixa abre com vagas legítimas, sem duplicatas, com os eliminatórios à vista, e uma vaga aderente de empresa-alvo chega ao celular minutos depois da coleta.

**B-11 · Eliminatórios separados das skills** (`analysis`, `profile`)

- *Por quê:* o corte dos ATS vem de perguntas eliminatórias e filtros duros; o score de hoje trata "inglês fluente" ou "8 anos de experiência" como mais uma skill.
- *Como:* o `extract-requirements.v2` extrai à parte os anos mínimos de experiência, o idioma e o nível, a residência ou localidade exigida, os dias presenciais, a autorização de trabalho e o fuso (vagas internacionais), a formação ou certificação obrigatória e o regime (CLT ou PJ), numa coluna `knockouts jsonb` em `job_analysis`. O perfil de busca ganha seus anos de experiência, nível de inglês, cidade, disponibilidade presencial e regimes aceitos. Cada eliminatório aparece como atende, não atende ou sem informação; um "não atende" limita o score (por exemplo, a 40) e vira motivo na caixa. Os requisitos salvos com o v1 continuam valendo até a reanálise.
- *Pronto quando:* uma vaga que pede inglês fluente e 8 anos mostra dois eliminatórios em vermelho para um perfil com inglês intermediário e 5 anos, e o B-02 mostra 90% de acerto nos eliminatórios.

**B-12 · Skills equivalentes e hierarquia no score** (`analysis`)

- *Por quê:* hoje só o ajuste da Claude enxerga equivalências (RabbitMQ no lugar de Kafka, Quarkus no lugar de Spring Boot); sem chave, ou quando a diferença passa de 10 pontos, o score determinístico subestima a aderência. A Eightfold e o Jobright trabalham com skills adjacentes.
- *Como:* o `skills.txt` ganha duas relações: `~` para equivalência parcial, que vale metade (`Kafka ~ RabbitMQ, SQS`), e `>` para implicação (`Spring Boot > Spring, Java`). O `DeterministicScorer` conta a equivalência com peso 0,5, e o breakdown mostra "atendida por equivalência".
- *Pronto quando:* uma vaga que pede Kafka, para um currículo com RabbitMQ, ganha meio ponto na obrigatória, com a explicação e um teste em `AnalysisRulesTest`.

**B-13 · Legitimidade da vaga** (`jobs`, `companies`)

- *Por quê:* 18% a 22% das vagas no Greenhouse eram fantasma; o Jobright e a HiringCafe filtram vagas suspeitas ou antigas; o career-ops checa a legitimidade antes de recomendar.
- *Como:* uma faixa Alta, Média ou Baixa com os motivos, a partir dos sinais que o projeto já guarda (`repost_of_job_id`, `link_failures`, `closed_at`, `first_seen_at` e as republicações da empresa) e de outros três: vaga com mais de 30 dias, empresa com ATS integrado onde a vaga não aparece, e agência sem empresa contratante nomeada. Salário ausente não pesa (ver 2.3). Selo e filtro na caixa; a faixa entra no ranking.
- *Pronto quando:* uma vaga republicada três vezes em 90 dias aparece com legitimidade baixa e o motivo, e sai da caixa no filtro padrão.

**B-14 · Deduplicação entre fontes** (`jobs`, `sourcing`)

- *Por quê:* a mesma vaga chega pelo alerta do LinkedIn, sem descrição, e pelo Greenhouse ou outro ATS, completa e com título um pouco diferente; o fingerprint exato não junta as duas. É também o caminho permitido para completar as vagas do LinkedIn (D-01).
- *Como:* ao ingerir, buscar candidatas da mesma empresa nos últimos 60 dias com título parecido (o índice `job_title_trgm`, de `pg_trgm`, já existe) e confirmar por local, modelo de trabalho e sobreposição de skills. A fusão mantém a descrição mais completa, o link da origem (ATS antes de alerta) e as duas origens, e fica registrada (`duplicate_of_job_id`) para poder ser desfeita na tela da vaga.
- *Pronto quando:* a vaga do alerta do LinkedIn e a mesma vaga no Greenhouse da empresa aparecem como uma só, com a descrição completa.

**B-15 · Importar vaga por link** (`jobs`, `sourcing`)

- *Por quê:* muitas páginas de carreira publicam a vaga em JSON-LD `JobPosting` para o Google for Jobs; ler esse formato importa vagas de qualquer ATS sem um adapter por plataforma e evita digitar a vaga manual.
- *Como:* `POST /api/jobs/import` com a URL. O backend baixa a página uma vez, lê os `script[type=application/ld+json]` com o jsoup (já é dependência) e mapeia título, empresa, local, `jobLocationType`, `baseSalary`, `validThrough` e descrição; sem JSON-LD, tira o texto principal e pede à Claude título, empresa e local. A tela Nova vaga ganha o campo "colar link". Links do LinkedIn ficam de fora (D-01): para eles, cole o texto.
- *Pronto quando:* colar o link de uma página que publique o JSON-LD cria a vaga com descrição completa e, quando houver, salário.

**B-16 · Frescor no ranking e alerta imediato** (`jobs`, `notifications`)

- *Por quê:* a primeira semana de uma vaga recebe cerca do dobro das candidaturas das semanas seguintes (Ashby), e a caixa hoje ordena só por score.
- *Como:* ordem padrão por score com um bônus decrescente nas primeiras 72 horas (configurável; desligue se a H2 não se confirmar) e a idade da vaga visível ("há 5 h"). As notificações de score ≥ 75 (`NotificationTriggers.HIGH_SCORE`) e de empresa ALVO passam a ir também para o canal do D-02, assim que a análise termina.
- *Pronto quando:* uma vaga aderente de uma empresa-alvo chega ao celular minutos depois da coleta.

**B-17 · Fila do dia** (frontend, `jobs`, `tracking`)

- *Por quê:* você usa o painel de manhã, depois da coleta das 7h ([PRODUCT.md](../PRODUCT.md)); o career-ops e o Fieldwork resolvem isso com uma fila de ações. A meta semanal combate o efeito do volume (Huntr: 9,25% de entrevistas por candidatura entre 11 e 20 candidaturas, 2,58% com 100 ou mais).
- *Como:* uma tela `/today`, ou o topo da Caixa de vagas, com as vagas novas aderentes e legítimas para aplicar hoje, os follow-ups vencidos (B-08), os retornos para revisar, as entrevistas e testes da semana e os currículos adaptados prontos; uma meta semanal de candidaturas aderentes (padrão de 10 a 15) e um aviso ao aplicar numa vaga de score baixo ou pela quarta vez na mesma empresa em 6 meses.
- *Pronto quando:* de manhã, a fila mostra tudo o que pede ação no dia, e cada item leva à tela onde a ação acontece.

### 5.6 Próximo

**B-18 · Abordagens de recrutadores e agências** (`inbox`, `jobs`, `network`). Quem é abordado chega à entrevista em 25% dos casos, e quem vem por agência em 42%; hoje um InMail ou um e-mail de headhunter cai como OUTRO. Uma classificação nova, `CONTATO_RECRUTADOR`, no `classify-email.v2` (notificação de mensagem do LinkedIn, e-mail de agência) cria a oportunidade, com a origem nova `RECRUTADOR` em `JobOrigin`, e o contato (B-05); as agências já existem como `company.kind = RECRUTAMENTO`. *Pronto quando* um e-mail de headhunter vira uma vaga na caixa com o contato ligado.

**B-19 · Volta às empresas onde você foi finalista** (`companies`, `notifications`). O Gem chama de "silver medalists" os finalistas não contratados e os trata como o melhor ponto de partida para a próxima vaga. Empresas onde uma candidatura sua chegou a ENTREVISTA, TESTE_TECNICO ou OFERTA e terminou em RECUSADA geram um alerta quando abrem uma vaga parecida depois de um intervalo (padrão de 90 dias), com o rascunho de mensagem para quem te entrevistou (B-07). *Pronto quando* o alerta aparece com a candidatura anterior e o contato.

**B-20 · Resumo diário no celular** (`notifications`). Já estava no backlog da arquitetura. Reaproveita o canal do B-16: às 7h30, as 5 melhores vagas do dia, os follow-ups vencidos e os retornos para revisar, cada um com o link para o painel.

**B-21 · Evidência por requisito** (`analysis`, frontend). O Job Match do LinkedIn mostra, requisito a requisito, como o perfil atende. A Claude passa a devolver, para cada obrigatória e desejável, o trecho do currículo que comprova, ou "não comprovado"; o `AnalysisCard` mostra a lista, e o currículo adaptado usa as mesmas evidências. *Pronto quando* cada requisito da vaga aparece com a frase do currículo que o sustenta.

**B-22 · Dicionário de skills que cresce com revisão** (`analysis`, Configurações). As skills que a Claude extrai e que não estão no dicionário viram sugestões, com o número de vagas que as pedem; aceitar uma grava a grafia canônica e os apelidos numa tabela que complementa o `skills.txt`. Opcionalmente, a sugestão traz a URI e os rótulos em português e inglês da ESCO. *Pronto quando* uma skill nova pedida em 5 vagas aparece como sugestão e, aceita, passa a contar no score determinístico.

**B-23 · Salário normalizado e CLT × PJ** (`analysis`, `profile`). Extrair faixa, moeda, período e regime, inclusive do `baseSalary` do JSON-LD; converter para um mensal equivalente (CLT com 13º, férias mais 1/3 e FGTS; PJ com impostos estimados; dólar pelo câmbio do dia); um piso salarial no perfil de busca, como filtro ou penalidade; o motivo SALARIO do feedback sugere o piso, como a arquitetura já prevê. Referência opcional: o salário de admissão do Novo CAGED por CBO e UF, depois de confirmar o layout dos microdados. *Pronto quando* a caixa filtra por salário equivalente e mostra a conversão de uma vaga PJ ou em dólar.

**B-24 · Elegibilidade das vagas internacionais** (`jobs`, `analysis`). Distinguir remoto global, remoto para América Latina ou Brasil e remoto só para EUA ou Europa, o fuso exigido e a contratação como contractor, usando o `applicantLocationRequirements` do JSON-LD quando houver. Estende o `JobScopeDetector` depois do commit do trabalho em andamento. *Pronto quando* a aba Internacionais mostra por padrão só as vagas elegíveis a partir do Brasil.

**B-25 · Família de cargo** (`analysis`). Classificar o título em backend, frontend, full stack, dados, mobile, SRE/DevOps, QA, gestão ou fora de TI, primeiro por regras e pela Claude; se precisar sem LLM, com o JobBERT-v2 em ONNX. Vira filtro e motivo de descarte automático, reaproveitando o `SEM_PERFIL_TECNICO`. *Pronto quando* as vagas fora das famílias escolhidas no perfil saem da caixa com o motivo.

**B-26 · Alerta de golpe** (`jobs`, `inbox`). Já estava no backlog da arquitetura, com os sinais da seção 2.3; vale também para os e-mails de recrutador do B-18. *Pronto quando* uma vaga que pede pagamento ou só aceita contato por WhatsApp aparece com o selo e o motivo.

**B-27 · Conferir o link antes de aplicar** (`tracking`). Ao abrir "Aplicar" ou mover para APLICADA, o `LinkVerifier` confere se a vaga continua no ar, como o career-ops faz antes de recomendar; se fechou, o painel avisa e sugere ENCERRADA.

**B-28 · SmartRecruiters, Workable, Teamtailor e Recruitee** (`sourcing`, `companies`). Um bean por ATS em `AtsSources`, com o valor correspondente em `JobOrigin` e em `fullListing()`. O ATS e o slug são detectados pela URL da página de carreira cadastrada na empresa, e o slug é validado no cadastro, porque a Lever devolve lista vazia tanto para empresa sem vagas quanto para slug errado. *Pronto quando* uma empresa-alvo em qualquer um dos quatro ATS entra na coleta diária só com a URL da página de carreira.

**B-29 · Extensão "Salvar vaga"** (extensão MV3, `jobs`). Já estava no backlog da arquitetura. Um botão na página aberta envia a URL e o JSON-LD, ou o texto selecionado, para o B-15. Pede um token pessoal de API, porque hoje a sessão é por cookie com CSRF. Segue o D-03.

**B-30 · Cobertura de palavras-chave no currículo adaptado** (`tailoring`). No estilo do Jobscan: o título da vaga no headline, o termo exato da vaga além da skill normalizada, hard skills primeiro, e avisos de repetição excessiva e de clichês; meta de cobertura configurável (o Jobscan sugere 75% ou mais).

**B-31 · Respostas de triagem e carta de apresentação** (`tailoring`). Rascunhos para as perguntas comuns dos formulários (pretensão salarial com base no B-23, disponibilidade, "por que esta empresa") e uma carta curta quando o formulário pedir, com as mesmas regras de veracidade do currículo adaptado.

**B-32 · Preparação para entrevista** (módulo novo `interviews`). Já estava no backlog da arquitetura. Ao entrar em ENTREVISTA ou TESTE_TECNICO: perguntas prováveis a partir dos requisitos e das lacunas, o que a empresa contrata (métricas de `companies`), perguntas para fazer, a descrição congelada e as histórias STAR sugeridas (B-33). *Pronto quando* o card em ENTREVISTA abre um plano de preparação em uma página.

**B-33 · Banco de histórias STAR** (`interviews`). De 6 a 10 histórias escritas por você (situação, tarefa, ação, resultado e reflexão), marcadas por skill e competência; a preparação sugere qual usar em cada pergunta, e a IA só reescreve o que você escreveu.

**B-34 · Orçamento mensal do LLM por tarefa** (`shared.ai`). Um limite mensal por tarefa em `jobhunter.ai.*`, calculado sobre `llm_usage`; ao atingir, a análise cai para o determinístico e o painel avisa.

### 5.7 Depois

| # | Item | Quando faz sentido |
| --- | --- | --- |
| B-35 | Pesos calibrados pelo seu histórico: regressão logística sobre o breakdown, a legitimidade e o frescor, mantendo a explicação, e "chance de retorno" pela sua taxa histórica por faixa de score | Com cerca de 100 vagas avaliadas (interesse, GOSTEI e descartes) e se a H4 falhar |
| B-36 | Pré-ranking semântico com pgvector, já no backlog da arquitetura | Quando o D-04 disser que o volume justifica |
| B-37 | Páginas de carreira da Gupy por empresa (`{empresa}.gupy.io`) | Depois de um spike que confirme um JSON-LD ou JSON público e os termos de uso da Gupy |
| B-38 | Workday das empresas-alvo (bancos e grandes empresas usam muito) | Depois de um spike com 2 ou 3 empresas; o endpoint não é documentado e pode quebrar ou bloquear |
| B-39 | Registro pós-entrevista: o que perguntaram e o que faltou, no relatório semanal e no banco STAR | Junto com o B-32 |
| B-40 | Comparação de ofertas e negociação (CLT, PJ e internacional com o B-23, benefícios, modelo de trabalho) | Quando a primeira oferta chegar |
| B-41 | Exportar vagas, candidaturas, eventos e contatos em JSON ou CSV, e apagar um contato (LGPD) | Junto com o B-05 ou antes de abrir o projeto para outras instalações |

## 6. O que não fazer

- **Candidatura automática** (AIHawk e similares): é um não-objetivo, e os dados da Huntr mostram a taxa de entrevista caindo conforme o volume sobe.
- **Scraping do LinkedIn, Indeed ou Glassdoor**, logado ou por endpoints internos como o `jobs-guest` (JobSpy faz isso): a cláusula 8.2 dos termos do LinkedIn proíbe, e o LinkedIn ganhou do hiQ por quebra de contrato.
- **Enviar mensagens ou e-mails sozinho:** o agente prepara o rascunho e você envia, como no career-ops e no Fieldwork.
- **Inventar experiência, números ou tecnologias** em currículo, perfil ou mensagem: a regra que já está no prompt de adaptação vale para todos os textos novos.
- **Raspar Glassdoor ou Levels.fyi para salários:** o Levels.fyi só libera dados por contrato, e o Glassdoor não tem API pública.
- **Depender do plano gratuito do Lightcast:** ele só vale para iniciativas sem fins lucrativos.
- **Tratar número de fornecedor como verdade:** use como direção e confirme no seu funil (seção 4).

## 7. Fontes

**Projetos abertos:** [career-ops](https://github.com/santifer/career-ops) · [JobSpy](https://github.com/speedyapply/JobSpy) · [Resume Matcher](https://github.com/srbhr/Resume-Matcher) · [AIHawk](https://github.com/feder-cr/Jobs_Applier_AI_Agent_AIHawk) · [JobSync](https://github.com/Gsync/jobsync) · [Fieldwork](https://www.producthunt.com/p/fieldwork-3/fieldwork-2) · [Trackr](https://www.producthunt.com/products/trackr-7) · [JobTrackr](https://ca.unraid.net/apps/jobtrackr-1wudf9l0r45fc5) · [JobScout](https://pypi.org/project/jobscout/)

**Sites de vagas:** [LinkedIn Job Match](https://jai-un-pote-dans-la.com/job-match-lia-de-linkedin-pour-mieux-candidater/) · [Jobright AI Job Match](https://jobright.ai/ai-job-match) · [HiringCafe (review)](https://jobright.ai/blog/hiringcafe-review-2026-features-pros-cons-and-alternatives/) · [Welcome to the Jungle UK (avaliações)](https://es.trustpilot.com/review/otta.com?page=10) · [Gupy: inteligência artificial](https://gupy.io/inteligencia-artificial) · [Gupy: IA na seleção (Baguete)](https://www.baguete.com.br/noticias/gupy-ia-comanda-selecao) · [Gupy: recomendações de vagas](https://www.gupy.io/blog-do-emprego/recomendacoes-de-vagas)

**Classificadores e dados:** [ESCO API](https://esco.ec.europa.eu/en/use-esco/use-esco-services-api/esco-web-service-api) · [O*NET Web Services](https://services.onetcenter.org/about) · [Lightcast, acesso gratuito](https://docs.lightcast.io/lightcast-api/docs/free-api-access) · [JobBERT (arXiv 2109.09605)](https://arxiv.org/abs/2109.09605) · [JobBERT-v2 ONNX](https://huggingface.co/onnx-community/JobBERT-v2-ONNX) · [CBO no IBGE/Concla](https://concla.ibge.gov.br/classificacoes/por-tema/ocupacao/classificacao-brasileira-de-ocupacoes) · [Novo CAGED com o pacote cagedr](https://cran.r-universe.dev/cagedr/doc/manual.html) · [Deduplicação de vagas (arXiv 2406.06257)](https://arxiv.org/pdf/2406.06257) · [Textkernel sobre duplicatas](https://www.textkernel.com/newsroom/online-job-postings-have-many-duplicates-but-how-can-you-detect-them-if-they-are-not-exact-copies-of-each-other/) · [Vagas fantasma no Greenhouse (Fox)](https://www.fox5ny.com/news/ghost-jobs-greenhouse-analysis) · [Sinais de vaga fantasma (Careerflow)](https://www.careerflow.ai/blog/ghost-jobs) · [Golpes por WhatsApp (Practicus)](https://www.practicus.com/blog/how-to-spot-a-fake-recruiter-on-whatsapp) · [Synapse, ranking em duas fases (arXiv 2604.02539)](https://arxiv.org/abs/2604.02539)

**ATS:** [SmartRecruiters Posting API](https://developers.smartrecruiters.com/docs/endpoints) · [Workable, endpoints públicos](https://help.workable.com/hc/en-us/articles/115012771647) · [APIs públicas de ATS (dev.to)](https://dev.to/scrapemint/every-major-ats-has-a-public-json-api-for-job-openings-nobody-uses-them-f9j) · [Workday JSON (dev.to)](https://dev.to/udaninn/workday-job-boards-have-a-json-api-too-its-just-better-hidden-23fl) · [Teamtailor jobs.json (Apify)](https://apify.com/careersignal/teamtailor-jobs-api) · [Gupy Public API (Digibee)](https://docs.digibee.com/documentation/connectors-and-triggers/connectors/industry-solutions/gupy) · [Google JobPosting](https://developers.google.com/search/docs/appearance/structured-data/job-posting) · [Como os ATS leem currículos (metaintro)](https://www.metaintro.com/blog/how-ai-and-ats-read-your-resume-2026) · [Jobscan tutorial](https://jobscan.co/jobscan-tutorial)

**Recrutadores e headhunters:** [Ashby, referrals](https://www.ashbyhq.com/talent-trends-report/reports/referrals) · [Ashby, candidaturas por vaga (2023)](https://www.ashbyhq.com/talent-trends-report/reports/2023-trends-report-applications-per-job) · [Ashby, 300+ candidaturas por contratação (HR Dive)](https://www.hrdive.com/news/recruiters-see-job-applications-triple-to-more-than-300-per-role/820096/) · [Gem, silver medalists](https://www.gem.com/blog/silver-medalists-candidate-rediscovery) · [Gem CRM](https://www.gem.com/product/crm) · [Juicebox](https://info.juicebox.ai/what-is-juicebox) · [Eightfold, matching](https://eightfold.ai/engineering-blog/ai-powered-talent-matching-the-tech-behind-smarter-and-fairer-hiring/) · [LinkedIn Recruiter, busca booleana](https://training.talent.linkedin.com/path/use-boolean-in-linkedin-recruiter) · [Tempo de resposta a abordagens (noon.ai)](https://www.noon.ai/blog/articles/243-candidate-response-time-benchmarks)

**Ferramentas para candidatos:** [Huntr, tendências do 1º tri de 2026](https://huntr.co/research/job-search-trends-q1-2026) · [Huntr, contatos](https://huntr.co/product/contact-tracker) · [Teal (review)](https://www.unite.ai/tealhq-review) · [Simplify Copilot](https://simplify.jobs/copilot) · [Preparação com IA (VirtualSpeech)](https://virtualspeech.com/blog/ai-prepare-job-interview) · [Currículos escritos por IA (Deel)](https://www.deel.com/deel-works/ai-resume-crisis) · [Levels.fyi, acesso à API](https://levels.fyi/api-access)

**Termos de uso:** [LinkedIn User Agreement](https://www.linkedin.com/legal/user-agreement) · [hiQ × LinkedIn, quebra de contrato](https://www.privacyworld.blog/2022/11/federal-court-rules-in-favor-of-linkedins-breach-of-contract-claim-after-six-years-of-cfaa-data-scraping-litigation/) · [Exportar conexões do LinkedIn](https://linkedapi.io/guides/export-linkedin-connections)
