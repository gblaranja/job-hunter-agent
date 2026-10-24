<p align="center">
  <img src="frontend/public/favicon.svg" width="72" alt="" />
</p>

<h1 align="center">Job Hunter Agent</h1>

<p align="center"><strong>Seu agente pessoal de busca de emprego.</strong><br/>
Coleta vagas todos os dias, mede a aderência de cada uma ao seu currículo com IA, acompanha as candidaturas num kanban, lê os retornos no Gmail e transforma os requisitos do mercado em sugestões concretas para o currículo.</p>

<p align="center">
  Feito por <a href="https://www.linkedin.com/in/gabriel-laranja">Gabriel Laranja</a> ·
  <a href="#autor-feedback-e-contribuições">feedbacks e contribuições são bem-vindos</a>
</p>

Procurar emprego gera muito trabalho repetitivo e pouca informação: alertas espalhados em vários e-mails, vagas que saem do ar antes da entrevista, nenhuma ideia de qual versão do currículo funciona melhor. O Job Hunter Agent centraliza isso:

- **Coleta** vagas dos alertas por e-mail (LinkedIn, Gupy) e de APIs públicas, sem scraping e sem automação na conta do LinkedIn.
- **Analisa** cada vaga com a Claude API: requisitos, resumo e um score de aderência híbrido (determinístico, com ajuste do LLM).
- **Adapta o currículo para cada vaga**: reescreve o currículo com os termos da vaga para passar no ATS, sem inventar experiência, mostra o score e as palavras-chave cobertas antes e depois e gera um DOCX que vira uma versão do currículo para a candidatura.
- **Acompanha** o funil num kanban, guarda uma cópia congelada de cada vaga aplicada e registra qual currículo foi enviado a cada empresa.
- **Lê os retornos** no Gmail, classifica com IA e move o card sozinho quando a confiança é alta.
- **Monitora empresas**: volume e frequência de vagas por semana, tempo no ar, reposts e alertas das empresas-alvo.
- **Gera insights semanais**: skills mais pedidas, lacunas, onde o funil trava e bullets do currículo reescritos.
- **Organiza nichos**: fintechs, bancos e consultorias de tecnologia (dados abertos do Banco Central e da Receita, mais as empresas das vagas) ordenados pelo fit com o seu perfil, com o selo GPTW verificado na web.

Cada pessoa roda a própria instalação (auto-hospedada), com o próprio banco, a própria conta Google e a própria chave da Claude API: os dados de busca de emprego não saem da sua infraestrutura.

A arquitetura completa está em [docs/arquitetura.md](docs/arquitetura.md).

## Stack

| Camada | Tecnologias |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1, Spring Modulith 2.1, Spring Data JPA, Flyway, Spring Security |
| Banco e storage | PostgreSQL 17, storage S3 compatível (RustFS no ambiente local; S3 ou R2 em produção) |
| IA | Claude API (SDK Java oficial da Anthropic), structured outputs e prompt caching |
| Frontend | React 19, Vite, TypeScript, TanStack Query e Table, shadcn/ui, dnd-kit |
| Contrato da API | springdoc-openapi, tipos TypeScript gerados com openapi-typescript |
| Testes | JUnit, Testcontainers, Spring Modulith, Vitest, Testing Library, MSW |
| Infra | Docker Compose, GitHub Actions |

O backend é um monólito modular: cada módulo (`jobs`, `tracking`, `resumes`, `sourcing`, `analysis`, `inbox`, `companies`, `insights`, `notifications`…) só se comunica com os outros pela API pública ou por eventos de domínio, e o Spring Modulith verifica essas fronteiras nos testes. Telas que cruzam módulos usam consultas SQL de leitura, para que nenhum módulo dependa de outro em Java só para montar uma lista.

Algumas decisões que valem destacar:

- **Histórico append-only.** `application_event` recusa `UPDATE` e `DELETE` por trigger; correções entram como eventos novos, e as métricas de conversão saem daí.
- **Cópia congelada da vaga.** Ao aplicar, o link e a descrição viram um `job_snapshot`: as vagas saem do ar antes da entrevista, e o que foi pedido continua salvo.
- **Upload validado pelo conteúdo.** O Apache Tika identifica PDF e DOCX pelos bytes (não pela extensão), o sha256 evita duplicatas e o download é por URL pré-assinada de 5 minutos.
- **Contrato tipado de ponta a ponta.** Os records Java anotados com `@Nullable` (JSpecify) viram um OpenAPI fiel, e o frontend gera os tipos a partir dele.

## Como rodar

Pré-requisitos: Java 25, Docker com o plugin Compose e Node 24.

### Desenvolvimento

```bash
# Backend: sobe Postgres e o storage S3 do docker-compose automaticamente
cd backend
./mvnw spring-boot:run

# Frontend (em outro terminal): http://localhost:5173, com proxy de /api para o backend
cd frontend
npm install
npm run dev
```

O login de desenvolvimento é `admin` / `jobhunter`. A página inicial, com a apresentação do produto, fica em `/`; o painel, em `/board`.

### Stack completa, como em produção

```bash
cp .env.example .env   # defina usuário, hash da senha e segredos
docker compose --profile app up --build
# backoffice em http://localhost:8081
```

### Testes

```bash
cd backend && ./mvnw verify                                       # unitários, módulos e integração (Testcontainers)
cd frontend && npm run lint && npx vitest run && npm run build
```

Depois de mudar a API, com o backend rodando: `cd frontend && npm run gen:api`.

### Ligar a análise por IA

Sem chave, a análise usa só o dicionário de skills e as regras, e o score funciona do mesmo jeito. Com `JOBHUNTER_AI_API_KEY` (Claude API), o Claude Haiku refina a extração dos requisitos e ajusta o score em até ±10 pontos, com justificativa. Configure um limite de gasto mensal no Console da Anthropic; o uso de tokens de cada chamada fica na tabela `llm_usage`.

Com a chave, a vaga ganha também o card **Currículo para esta vaga**: o Claude Opus reescreve o currículo padrão (ou outro que você escolher) a partir da análise da vaga. As regras do prompt proíbem inventar empresas, números ou tecnologias; o que a vaga pede e o currículo não comprova fica listado à parte, e as skills que aparecerem no texto adaptado sem estar no original são sinalizadas para você conferir. Dá para editar o texto, baixar o DOCX (uma coluna, sem tabelas nem imagens, como os ATS leem melhor) e salvá-lo em Currículos, onde ele entra na taxa de entrevistas por versão. Ao marcar "Tenho interesse" numa vaga, a primeira versão é gerada sozinha; desligue com `JOBHUNTER_TAILORING_AUTO=false`. O modelo pode ser trocado em `jobhunter.ai.models.tailoring`.

### Conectar o Gmail

1. No Google Cloud, crie um projeto, ative a Gmail API e configure a tela de consentimento (External, escopo `gmail.readonly`). Publique o app como **In production**: no modo Testing o refresh token expira em 7 dias.
2. Crie uma credencial OAuth do tipo **Aplicativo da Web** com o redirect URI `http://localhost:8080/api/admin/gmail/callback` (desenvolvimento) ou o da sua instalação.
3. Defina `JOBHUNTER_GMAIL_CLIENT_ID` e `JOBHUNTER_GMAIL_CLIENT_SECRET` e reinicie o backend.
4. No Gmail, crie a label `jobs` e filtros que a apliquem aos alertas do LinkedIn e da Gupy e aos e-mails de recrutadores.
5. Em **Configurações → Conectar Gmail**, autorize o acesso. A coleta roda todo dia às 7h ou em **Coletar agora**.

### Entrar com Google

O login com Google reaproveita a credencial OAuth do Gmail. Como cada instalação tem um único dono, só entra a conta Google com o e-mail autorizado (verificado pelo Google); o login com usuário e senha continua disponível.

1. Na mesma credencial OAuth do Google Cloud, adicione um segundo redirect URI: `http://localhost:8080/api/auth/google/callback` (desenvolvimento) ou `http://localhost:8081/api/auth/google/callback` (stack completa), ou o da sua instalação.
2. Defina `JOBHUNTER_ALLOWED_EMAIL` com o e-mail da sua conta Google (e, fora do padrão, `JOBHUNTER_GOOGLE_REDIRECT_URI`).
3. Reinicie o backend: o botão **Entrar com Google** aparece na tela de login.

### Nichos e selo GPTW

A tela **Nichos** lista as empresas do Financeiro e das Consultorias pelo fit (subsegmento 35%, stack das vagas 30%, domínio 20%, sinais 15%; os pesos são editáveis), com a justificativa de cada uma. As empresas importadas entram como descobertas e sobem para monitoradas quando o fit é alto ou quando publicam vaga.

- **Banco Central:** a Relação de Instituições em Funcionamento (API OData de dados abertos) é importada todo dia 1º, ou em **Configurações → Bases de empresas**. O tipo de autorização define o subsegmento.
- **Receita Federal (consultorias):** baixe os arquivos `Empresas*.zip` e `Estabelecimentos*.zip` da base de CNPJ em arquivos.receitafederal.gov.br, aponte `JOBHUNTER_CNPJ_DIRECTORY` para a pasta e rode a importação. Entram as matrizes ativas com CNAE de TI, porte diferente de ME e capital social a partir de `JOBHUNTER_CNPJ_MIN_CAPITAL`. Os arquivos de sócios não são lidos.
- **Selo GPTW:** com `JOBHUNTER_SEARCH_API_KEY` (Tavily) e a Claude API, cada empresa nova com vaga ativa é buscada na web e verificada pelo Claude Haiku; sem menção explícita à empresa e ao ano, o resultado é "não encontrado". As respostas ficam 6 meses em cache e `JOBHUNTER_SEARCH_MONTHLY_LIMIT` limita as consultas pagas do mês (configure também um limite de gasto no Tavily). Confiança abaixo de 80% ou homônimos vão para a fila **Revisar selos** na tela de Empresas. Sem a chave, use **Importar lista GPTW** com o texto ou o link da lista anual. O selo soma +10 (ranking GPTW TI), +7 (outro ranking) ou +4 (certificação) na ordenação da caixa de vagas, sem mudar o score de aderência.

## Roadmap

| Fase | Entrega | Status |
| --- | --- | --- |
| 1 | Fundação, tracker de candidaturas e currículos | Concluída |
| 2 | Coleta via Gmail, feedback e cadastro de empresas | Concluída |
| 3 | Análise por IA e score de aderência | Concluída |
| 4 | Classificação dos retornos e verificação de links | Concluída |
| 5 | Insights semanais e monitoramento de empresas | Concluída |
| — | Página inicial, identidade visual e login com Google | Concluída |
| 6 | Nichos (financeiro, consultorias) e selo GPTW | Concluída |

## Autor, feedback e contribuições

Criei o Job Hunter Agent para a minha própria busca de emprego e como projeto de portfólio, e quero que ele sirva para mais gente. Feedbacks sobre o produto, o código ou a arquitetura são muito bem-vindos, assim como contribuições: novas fontes de vagas, parsers de alertas, melhorias nos prompts, nas telas ou nos testes.

Fale comigo no LinkedIn: **[linkedin.com/in/gabriel-laranja](https://www.linkedin.com/in/gabriel-laranja)**.

Para contribuir com código: abra uma issue descrevendo a ideia ou o bug, crie um branch a partir de `main` e envie um pull request com testes (`./mvnw verify` no backend e `npm run lint && npx vitest run` no frontend precisam passar).
