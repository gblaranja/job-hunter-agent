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
| Banco e storage | PostgreSQL 17, MinIO (local) / S3 compatível |
| IA | Claude API (SDK Java oficial da Anthropic), structured outputs e prompt caching |
| Frontend | React, Vite, TypeScript, TanStack Query, shadcn/ui, dnd-kit, Recharts |
| Testes | JUnit, Testcontainers, WireMock, Vitest, Testing Library |
| Infra | Docker Compose, GitHub Actions |

O backend é um monólito modular: cada módulo (`jobs`, `tracking`, `resumes`, `sourcing`, `analysis`, `inbox`, `companies`…) só se comunica com os outros pela API pública ou por eventos de domínio, e o Spring Modulith verifica essas fronteiras nos testes.

## Como rodar

Pré-requisitos: Java 25, Docker com o plugin Compose e Node 24.

```bash
# Backend (sobe o Postgres do docker-compose automaticamente)
cd backend
./mvnw spring-boot:run

# Testes do backend (usam Testcontainers)
./mvnw verify
```

## Roadmap

| Fase | Entrega | Status |
| --- | --- | --- |
| 1 | Fundação, tracker de candidaturas e currículos | Em andamento |
| 2 | Coleta via Gmail, feedback e cadastro de empresas | — |
| 3 | Análise por IA e score de aderência | — |
| 4 | Classificação dos retornos e verificação de links | — |
| 5 | Insights semanais e monitoramento de empresas | — |
| 6 | Nichos (financeiro, consultorias) e selo GPTW | — |
