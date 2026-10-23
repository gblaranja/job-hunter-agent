# Job Hunter Agent

Agente pessoal que coleta vagas todos os dias, mede a aderência de cada uma ao meu perfil, acompanha as candidaturas num kanban e transforma os requisitos do mercado em sugestões concretas para o currículo.

Procurar emprego gera muito trabalho repetitivo e pouca informação: alertas espalhados em vários e-mails, vagas que saem do ar antes da entrevista, nenhuma ideia de qual versão do currículo funciona melhor. O Job Hunter Agent centraliza isso:

- **Coleta** vagas dos alertas por e-mail (LinkedIn, Gupy) e de APIs públicas, sem scraping e sem automação na conta do LinkedIn.
- **Analisa** cada vaga com a Claude API: requisitos, resumo e um score de aderência híbrido (determinístico, com ajuste do LLM).
- **Acompanha** o funil num kanban, guarda uma cópia congelada de cada vaga aplicada e registra qual currículo foi enviado a cada empresa.
- **Lê os retornos** no Gmail, classifica com IA e move o card sozinho quando a confiança é alta.
- **Monitora empresas** (volume, frequência e tipo de vagas), verifica o selo GPTW e organiza tudo por nichos com fit ao perfil.

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

O backend é um monólito modular: cada módulo (`jobs`, `tracking`, `resumes` e, nas próximas fases, `sourcing`, `analysis`, `inbox`, `companies`…) só se comunica com os outros pela API pública ou por eventos de domínio, e o Spring Modulith verifica essas fronteiras nos testes. Telas que cruzam módulos usam consultas SQL de leitura, para que nenhum módulo dependa de outro em Java só para montar uma lista.

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

O login de desenvolvimento é `admin` / `jobhunter`.

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

## Roadmap

| Fase | Entrega | Status |
| --- | --- | --- |
| 1 | Fundação, tracker de candidaturas e currículos | Concluída |
| 2 | Coleta via Gmail, feedback e cadastro de empresas | — |
| 3 | Análise por IA e score de aderência | — |
| 4 | Classificação dos retornos e verificação de links | — |
| 5 | Insights semanais e monitoramento de empresas | — |
| 6 | Nichos (financeiro, consultorias) e selo GPTW | — |
