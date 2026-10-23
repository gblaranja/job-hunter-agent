# Navegação e responsividade do painel: especificação

## Problem Statement

No celular, a navegação do painel é uma faixa horizontal com 9 itens. A faixa não mostra o item ativo e a maioria das telas fica escondida fora da área visível. A auditoria em Chrome headless (2026-10-23) encontrou rolagem horizontal da página inteira em `/board` em todas as larguras (scrollWidth 2137 px em 375 px e 2393 px em 1024 px), e em `/inbox` e `/insights` em 320 e 375 px. Quem usa o teclado também não tem como pular a navegação até o conteúdo.

## Goals

- [ ] Em 320, 375, 768 e 1024 px, nenhuma tela do painel no escopo tem rolagem horizontal de página (`documentElement.scrollWidth <= clientWidth`).
- [ ] Abaixo de 768 px, qualquer tela do painel fica a no máximo 2 toques (abrir menu → item).
- [ ] O primeiro Tab em qualquer tela do painel chega a um link "Pular para o conteúdo".

## Out of Scope

| Feature | Reason |
| ------- | ------ |
| Kanban: atualização otimista e menu "Mover para…" | Fora das áreas escolhidas para esta rodada. O comportamento já foi decidido e está registrado em Deferred Ideas |
| Error boundary por rota, 401 em mutations, página 404, título da aba por tela | Área "Estados e erros globais" não selecionada |
| Landing page, SEO, Open Graph, divisão do chunk principal (586 kB) | Área "Landing, SEO e performance" não selecionada |
| `/settings` (estouro de 14 px em 320 e 375 px) | O arquivo tem mudanças locais não commitadas do usuário (tema). Decisão: não editar arquivos com WIP |
| Página inicial (`/`) e `/login` | Fora do painel autenticado. Não estouram em 375 px |
| Qualquer alteração no backend ou nos contratos OpenAPI | A tarefa é exclusivamente de frontend. O contrato gerado está em sincronia com `/v3/api-docs` |
| Mudança na ordem, nos rótulos ou nas rotas do menu | Navegação reorganizada, não redesenhada |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --------------------- | -------------- | --------- | ---------- |
| Ponto de corte entre layout móvel e desktop | `md` do Tailwind (768 px), o mesmo que o `AppLayout` já usa | Mantém a convenção atual. Nenhuma tela muda de comportamento no desktop | y |
| Forma da navegação móvel | Barra superior (logo, sino, botão de menu) + gaveta lateral (`Sheet` do shadcn, já no projeto) | Escolha do usuário | y |
| Como verificar visibilidade por largura e ausência de estouro | Script de auditoria via Chrome DevTools Protocol contra o dev server local (já logado), medindo `scrollWidth` vs `clientWidth` em 320/375/768/1024 px. O resultado entra no `validation.md` | O jsdom não aplica CSS (`css: false` no Vitest), então teste unitário não mede layout. O script não vira dependência do projeto | n |
| Comportamento da gaveta | Lado esquerdo, fecha ao escolher um item, com Esc, no botão fechar ou tocando fora | Padrão do `Sheet` (Radix Dialog), que já cuida de foco preso e da devolução do foco | n |
| Foco após navegar entre telas do painel | O foco vai para a região principal (`<main>`), exceto no primeiro carregamento | Leitores de tela anunciam a nova tela, e quem usa teclado não volta para o topo do menu | n |
| Kanban em telas estreitas | As colunas continuam lado a lado com rolagem horizontal dentro do quadro, não da página | Mantém o arrastar entre colunas. Empilhar colunas é redesenho do kanban (fora de escopo) | n |
| Sino de notificações no celular | Fica na barra superior, fora da gaveta | Mantém o contador de não lidas sempre visível | n |
| Usuário e "Sair" no celular | Saem da barra superior e vão para o rodapé da gaveta | Libera espaço na barra e espelha o rodapé da sidebar no desktop | n |

**Open questions:** none - todas resolvidas ou registradas acima.

---

## User Stories

### P1: Navegação móvel em gaveta ⭐ MVP

**User Story**: Como dono da instalação usando o celular, quero abrir um menu com todas as telas do painel para chegar a qualquer uma em dois toques.

**Why P1**: Hoje 6 dos 9 itens ficam fora da tela em 375 px e o item ativo pode estar escondido. É a principal queixa de navegação.

**Acceptance Criteria**:

1. WHILE a largura da janela for menor que 768 px the painel SHALL exibir uma barra superior com o logo (link para `/`), o sino de notificações e um botão com nome acessível "Abrir menu", sem a faixa horizontal de navegação.  <!-- NAV-01 -->
2. WHEN o usuário aciona "Abrir menu" THEN o painel SHALL abrir um diálogo com nome acessível "Menu" contendo os 9 links de navegação, na ordem e com os rótulos atuais.  <!-- NAV-02 -->
3. WHEN o usuário aciona um link dentro da gaveta THEN o painel SHALL navegar para a rota do link e fechar a gaveta.  <!-- NAV-03 -->
4. WHEN o usuário pressiona Esc ou aciona o botão "Fechar" da gaveta THEN o painel SHALL fechar a gaveta e devolver o foco ao botão "Abrir menu".  <!-- NAV-04 -->
5. The gaveta SHALL exibir no rodapé o nome do usuário logado e um botão "Sair" que encerra a sessão.  <!-- NAV-05 -->
6. The link da tela atual SHALL ter `aria-current="page"`, tanto na gaveta quanto na sidebar.  <!-- NAV-06 -->
7. WHILE a largura da janela for de 768 px ou mais the painel SHALL exibir a sidebar lateral atual, sem o botão "Abrir menu".  <!-- NAV-07 -->

**Independent Test**: em 375 px, abrir `/board`, tocar em "Abrir menu", escolher "Empresas": a URL vira `/companies` e a gaveta fecha. Os testes de componente cobrem NAV-02 a NAV-06, e a auditoria por largura cobre NAV-01 e NAV-07.

---

### P1: Nenhuma rolagem horizontal de página ⭐ MVP

**User Story**: Como dono da instalação, quero que as telas do painel caibam na largura da tela, para não arrastar a página de lado para ler um card.

**Why P1**: É o defeito objetivo medido na auditoria. Em `/board` ele aparece até no desktop.

**Acceptance Criteria**:

1. The telas `/inbox`, `/board`, `/applications`, `/responses`, `/jobs/new`, `/resumes`, `/companies` e `/insights` SHALL ter `documentElement.scrollWidth` menor ou igual a `clientWidth` nas larguras 320, 375, 768 e 1024 px.  <!-- RESP-01 -->
2. WHILE o quadro do kanban tiver mais colunas do que cabem na largura the painel SHALL rolar as colunas na horizontal dentro do próprio quadro, mantendo a navegação e o cabeçalho da página fixos na largura da janela.  <!-- RESP-02 -->
3. IF um texto de card na caixa de vagas (empresa, local, chips de skills) for maior que a largura disponível THEN o painel SHALL quebrar o texto em linhas em vez de estender o card além da janela.  <!-- RESP-03 -->
4. IF uma tabela de `/insights` for mais larga que o card que a contém THEN o painel SHALL rolar a tabela dentro do card, sem alargar o card nem a página.  <!-- RESP-04 -->

**Independent Test**: o script de auditoria por largura informa `overflow: false` para as 8 rotas nas 4 larguras. Rodar contra o código atual dá `overflow: true` em `/board`, `/inbox` e `/insights`.

---

### P2: Atalho e foco para quem usa teclado

**User Story**: Como usuário de teclado ou de leitor de tela, quero pular o menu e cair no conteúdo ao trocar de tela, para não percorrer 9 links a cada navegação.

**Why P2**: É melhoria de acessibilidade com custo baixo, mas o painel já funciona sem ela.

**Acceptance Criteria**:

1. The primeiro elemento focável de qualquer tela do painel SHALL ser um link "Pular para o conteúdo" que aponta para a região principal (`<main>`), visualmente oculto até receber foco.  <!-- NAV-08 -->
2. WHEN o usuário aciona "Pular para o conteúdo" THEN o painel SHALL mover o foco para a região principal.  <!-- NAV-09 -->
3. WHEN a rota muda de uma tela do painel para outra THEN o painel SHALL mover o foco para a região principal.  <!-- NAV-10 -->
4. WHEN o painel carrega pela primeira vez THEN o painel SHALL manter o foco padrão do documento, sem movê-lo para a região principal.  <!-- NAV-11 -->

**Independent Test**: no navegador, Tab na tela `/board` mostra o atalho, e Enter leva o foco ao conteúdo. Os testes de componente cobrem NAV-08 a NAV-11.

---

## Edge Cases

- WHEN a gaveta está aberta e o usuário toca no sino de notificações THEN o painel SHALL manter o sino inacessível atrás do overlay até a gaveta fechar (comportamento modal do diálogo).  <!-- NAV-12 -->
- IF a sessão expira enquanto a gaveta está aberta THEN o painel SHALL redirecionar para `/login` como hoje, sem deixar overlay da gaveta na tela de login.  <!-- NAV-13 -->
- WHEN o usuário escolhe na gaveta o link da tela em que já está THEN o painel SHALL fechar a gaveta sem erro.  <!-- NAV-14 -->

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| -------------- | ----- | ----- | ------ |
| NAV-01 | P1: Navegação móvel em gaveta | Execute | Verified |
| NAV-02 | P1: Navegação móvel em gaveta | Execute | Verified |
| NAV-03 | P1: Navegação móvel em gaveta | Execute | Verified |
| NAV-04 | P1: Navegação móvel em gaveta | Execute | Verified |
| NAV-05 | P1: Navegação móvel em gaveta | Execute | Verified |
| NAV-06 | P1: Navegação móvel em gaveta | Execute | Verified |
| NAV-07 | P1: Navegação móvel em gaveta | Execute | Verified |
| RESP-01 | P1: Nenhuma rolagem horizontal | Execute | Verified |
| RESP-02 | P1: Nenhuma rolagem horizontal | Execute | Verified |
| RESP-03 | P1: Nenhuma rolagem horizontal | Execute | Verified |
| RESP-04 | P1: Nenhuma rolagem horizontal | Execute | Verified |
| NAV-08 | P2: Atalho e foco | Execute | Verified |
| NAV-09 | P2: Atalho e foco | Execute | Verified |
| NAV-10 | P2: Atalho e foco | Execute | Verified |
| NAV-11 | P2: Atalho e foco | Execute | Verified |
| NAV-12 | Edge case | Execute | Verified |
| NAV-13 | Edge case | Execute | Verified |
| NAV-14 | Edge case | Execute | Verified |

**Coverage:** 18 total, 18 mapped to tasks (T1–T8), 0 unmapped

---

## Implicit-requirement dimensions

| Dimension | Resolution |
| --------- | ---------- |
| Input validation & bounds | Larguras de referência fixadas em 320, 375, 768 e 1024 px (RESP-01) |
| Failure / partial-failure states | NAV-13 (sessão expira com a gaveta aberta). Os estados de erro das telas não mudam |
| Idempotency / retry / duplicate handling | NAV-14 (escolher a rota atual) |
| Auth boundaries & rate limits | N/A because o fluxo de sessão do `AppLayout` (redirect para `/login` sem sessão) é preservado sem alteração |
| Concurrency / ordering | N/A because não há escrita nova. Arrastar no kanban e mudar status continuam iguais |
| Data lifecycle / expiry | N/A because nenhum dado novo é armazenado (a gaveta não persiste estado) |
| Observability | N/A because é uma mudança visual no cliente, sem eventos de negócio |
| External-dependency failure | N/A because nenhuma chamada de API nova é feita |
| State-transition integrity | N/A because os status de candidatura e o `planMove` não mudam |

---

## Deferred Ideas

- Kanban: atualização otimista com rollback quando a API recusar e menu "Mover para…" em cada card (decidido pelo usuário em 2026-10-23, fica para uma rodada própria).
- `/settings` responsivo: o botão de ação do card estoura 14 px em 320 e 375 px. Fazer depois que o WIP do tema for commitado.
- Áreas não selecionadas: estados e erros globais, landing/SEO/performance.

---

## Success Criteria

- [ ] A auditoria por largura informa zero rotas com estouro horizontal nas 4 larguras.
- [ ] Em 375 px, qualquer tela do painel fica a 2 toques a partir de qualquer outra.
- [ ] `npm run lint`, `npx vitest run` e `npm run build` passam (o timeout preexistente em `ResponseDialog.test.tsx` é investigado e registrado).
