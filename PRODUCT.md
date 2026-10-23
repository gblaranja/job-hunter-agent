# Produto: Job Hunter Agent

Verdades duráveis sobre o produto, para quem (pessoa ou agente de IA) for desenhar, escrever ou revisar uma tela. O visual está em [DESIGN.md](DESIGN.md).

## Plataforma

Web. Backend Spring Boot servindo uma SPA React; auto-hospedado com Docker Compose.

## Quem usa

- **Dono da instalação:** uma pessoa procurando emprego, em geral dev, que instala o agente para si. Usa o painel todo dia de manhã, depois da coleta das 7h, no notebook e às vezes no celular.
- **Visitante da página pública:** recrutador, dev curioso ou possível contribuidor que chegou pelo LinkedIn ou pelo GitHub. Não tem conta e não vai ter: cada instalação tem um dono só.

## Propósito

Tirar o trabalho braçal da busca de emprego (coletar, filtrar, acompanhar, ler retornos) sem tirar a decisão da pessoa. Sucesso é o dono gastar o tempo dele escolhendo vagas e se preparando, não copiando links para planilhas.

## Superfícies e modos

| Superfície | Modo | O que é sucesso |
|---|---|---|
| Home pública (`/`) | Persuadir | O visitante entende o produto e vai ao código ou fala com o autor |
| Login (`/login`) | Operar | O dono entra em segundos; um visitante entende por que não pode entrar |
| Painel (`/board`, `/inbox`, ...) | Operar | Decidir e mover candidaturas rápido, com dados confiáveis |

## Capacidades e limites

- Coleta: alertas do LinkedIn e da Gupy numa label do Gmail; job boards Greenhouse, Lever e Ashby.
- Score de 0 a 100: regras determinísticas (obrigatórias 70, diferenciais 15, senioridade 10, modelo de trabalho 5) mais um ajuste da Claude entre −10 e +10.
- Retornos do Gmail movem o card sozinhos com confiança de pelo menos 70%; abaixo disso vão para a fila de revisão.
- Tudo funciona sem chave da Claude API, com regras e dicionário.
- Uma instalação, um dono. Não existe cadastro, plano, preço ou time.

## Voz

Direta, concreta e honesta. Fala do que o produto faz, com números reais quando existem. Português do Brasil, sem jargão de marketing ("revolucione", "potencialize", "boost").

## Evitar

- Clichês de landing de SaaS: métrica gigante no hero, grade de cards iguais com ícone em quadradinho, eyebrow acima de todo título.
- Estética genérica de "ferramenta de IA": gradiente roxo, neon, glassmorphism, brilhos.
- Afirmações sem evidência: depoimentos, número de usuários, taxas de sucesso, empresas clientes. Valores de exemplo aparecem rotulados como exemplo.

## Evidências

- `docs/arquitetura.md` descreve módulos, fluxos e decisões.
- Os pesos do score estão em `backend/.../analysis/internal/DeterministicScorer.java`; o limiar de 70% em `tracking/internal/EmailResponseHandler.java`. A home cita esses valores; se mudarem lá, mudam na home.
