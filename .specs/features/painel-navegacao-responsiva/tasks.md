# Navegação e responsividade do painel: tarefas

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the full flow (per-task cycle, sub-agent delegation, adequacy review, Verifier, discrimination sensor).

**If the skill cannot be activated, STOP and tell the user - do not proceed without it.**

---

**Design**: inline (sem `design.md`). A gaveta usa o `Sheet` existente (`src/components/ui/sheet.tsx`), e a lista de navegação sai do `AppLayout` para um módulo próprio, para servir à sidebar e à gaveta sem import circular.
**Status**: Done (Verifier PASS na iteração 3)

**Restrição de working tree:** não editar `SettingsPage.tsx`, `SettingsPage.test.tsx`, `main.tsx`, `src/lib/theme*`, `src/lib/useTheme.ts`, `.gitignore` nem o backend (WIP do usuário). Commits com `git add` seletivo.

---

## Test Coverage Matrix

> Generated from codebase, project guidelines, and spec - confirm before Execute. Guidelines found: `README.md` (seção Testes: `npm run lint && npx vitest run && npm run build`), `frontend/vite.config.ts` (Vitest + jsdom, `css: false`, sem limiar de cobertura). Amostras: `src/features/board/BoardPage.test.tsx`, `src/features/auth/LoginPage.test.tsx`, `src/components/NotificationBell.test.tsx`.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| ---------- | ------------------ | -------------------- | ---------------- | ----------- |
| Componente / layout React (comportamento) | unit (Vitest + Testing Library + MSW) | Cada AC comportamental e cada edge case listado com asserção de papel/nome acessível ou URL | `src/**/*.test.tsx`, ao lado do componente | `npx vitest run <arquivo>` |
| Ajuste só de CSS/layout (largura, rolagem) | e2e (auditoria por largura no Chrome headless) | `overflow: false` nas rotas do RESP-01 em 320/375/768/1024 px | `.specs/features/painel-navegacao-responsiva/responsive-audit.mjs` | `node .specs/features/painel-navegacao-responsiva/responsive-audit.mjs` |
| Dados estáticos / script de verificação | none | build gate only | - | build gate only |

## Gate Check Commands

> Generated from codebase - confirm before Execute. Comandos rodam a partir de `frontend/`, exceto a auditoria (raiz do repo). A auditoria exige o backend em `:8080` e o dev server em `:5173` rodando.

| Gate Level | When to Use | Command |
| ---------- | ----------- | ------- |
| Quick | After tasks with unit tests only | `npx vitest run <arquivos de teste da tarefa>` |
| Full | After tasks with e2e/integration tests | `npx vitest run && node ../.specs/features/painel-navegacao-responsiva/responsive-audit.mjs` |
| Build | After phase completion or config/entity-only tasks | `npm run lint && npx vitest run && npm run build` |

---

## Execution Plan

### Phase 1: Navegação

```
T1 → T2 → T3 → T4
```

### Phase 2: Responsividade

```
T5 → T6 → T7 → T8
```

### Phase 3: Correções do Verifier

```
T9 → T10
```

---

## Task Breakdown

### T1: Extrair a lista de navegação

**What**: Mover o array `navigation` do `AppLayout` para `src/layouts/navigation.ts`, sem mudar ordem, rótulos, rotas nem ícones.
**Where**: `frontend/src/layouts/navigation.ts`
**Depends on**: None
**Reuses**: array atual de `frontend/src/layouts/AppLayout.tsx`
**Requirement**: NAV-02

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `AppLayout` importa a lista do novo módulo
- [x] Build gate passa: `npm run lint && npx vitest run && npm run build`

**Tests**: none
**Gate**: build

**Commit**: `refactor(frontend): extract panel navigation items`

---

### T2: Gaveta de navegação móvel

**What**: Componente `MobileNav` com botão "Abrir menu" e um `Sheet` lateral esquerdo com nome acessível "Menu": os 9 links, o usuário e o botão "Sair" no rodapé. A gaveta fecha ao navegar.
**Where**: `frontend/src/layouts/MobileNav.tsx`
**Depends on**: T1
**Reuses**: `frontend/src/components/ui/sheet.tsx`, `NavLink` como no `AppLayout`
**Requirement**: NAV-02, NAV-03, NAV-04, NAV-05, NAV-06, NAV-12, NAV-14

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Testes em `frontend/src/layouts/MobileNav.test.tsx` cobrem NAV-02 a NAV-06, NAV-12 e NAV-14
- [x] Quick gate passa: `npx vitest run src/layouts/MobileNav.test.tsx`
- [x] Test count: 8 testes passam (NAV-04 tem um teste para Esc e outro para o botão Fechar)

**Tests**: unit
**Gate**: quick

**Commit**: `feat(frontend): add mobile navigation drawer`

---

### T3: Barra superior móvel no AppLayout

**What**: Abaixo de `md`, o `AppLayout` mostra a barra superior (logo, sino, `MobileNav`) e esconde a faixa de navegação. A partir de `md`, mantém a sidebar atual.
**Where**: `frontend/src/layouts/AppLayout.tsx`
**Depends on**: T2
**Reuses**: classes `md:` já usadas no `AppLayout`
**Requirement**: NAV-01, NAV-06, NAV-07, NAV-13

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Testes em `frontend/src/layouts/AppLayout.test.tsx`: NAV-06 na sidebar, NAV-13 (Sair pela gaveta leva ao login sem diálogo aberto)
- [x] Auditoria por largura: em 375 px existe o botão "Abrir menu" visível e a faixa `nav[aria-label=Principal]` não está visível. Em 1024 px, o inverso (NAV-01, NAV-07)
- [x] Testes completos passam (49). A auditoria completa de estouro fica para T6–T8; NAV-01/NAV-07 medidos no Chrome headless
- [x] Test count: 2 testes novos passam

**Tests**: unit
**Gate**: full

**Commit**: `feat(frontend): show mobile top bar in the panel layout`

---

### T4: Atalho "Pular para o conteúdo" e foco ao navegar

**What**: Link de atalho como primeiro elemento focável, `<main id="conteudo" tabIndex={-1}>`, e foco na região principal a cada troca de rota do painel, exceto na primeira renderização.
**Where**: `frontend/src/layouts/AppLayout.tsx`
**Depends on**: T3
**Also touches**: `frontend/src/layouts/MobileNav.tsx` (o foco não volta ao botão do menu quando a gaveta fecha por navegação, senão o NAV-10 quebra)
**Reuses**: classe `sr-only`/`focus:not-sr-only` do Tailwind
**Requirement**: NAV-08, NAV-09, NAV-10, NAV-11

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Testes em `frontend/src/layouts/AppLayout.test.tsx` cobrem NAV-08 a NAV-11
- [x] Build gate passa (último da fase)
- [x] Test count: 5 testes novos passam (NAV-10 pela sidebar e pela gaveta)

**Tests**: unit
**Gate**: build

**Commit**: `feat(frontend): add skip link and focus main region on navigation`

---

### T5: Auditoria por largura como script de verificação

**What**: Script Node sem dependências que abre o Chrome headless pelo DevTools Protocol, entra com o login de desenvolvimento e mede `scrollWidth` vs `clientWidth` nas rotas do RESP-01 em 320/375/768/1024 px. Sai com código 1 se alguma rota estourar.
**Where**: `.specs/features/painel-navegacao-responsiva/responsive-audit.mjs`
**Depends on**: None
**Reuses**: protótipo da auditoria inicial (scratchpad)
**Requirement**: RESP-01

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Rodar contra o código atual sai com código 1 e lista `/board`, `/inbox` e `/insights`
- [x] Build gate passa (o script fica fora do frontend; lint/testes/build do frontend inalterados desde T4)

**Tests**: none
**Gate**: build

**Commit**: `test(frontend): add responsive width audit script`

---

### T6: Kanban rola dentro do quadro

**What**: O contêiner com rolagem do quadro passa a ser `relative`, para que os `sr-only` absolutos dos cards não alarguem o documento.
**Where**: `frontend/src/features/board/BoardPage.tsx`
**Depends on**: T5
**Reuses**: -
**Requirement**: RESP-02

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Auditoria: `/board` sem estouro nas 4 larguras e o quadro com `scrollWidth > clientWidth` (rola por dentro)
- [x] Full gate passa nas rotas já corrigidas

**Tests**: e2e
**Gate**: full

**Commit**: `fix(frontend): keep kanban horizontal scroll inside the board`

---

### T7: Cards da caixa de vagas quebram linha

**What**: Badges de origem, local e modelo e a linha de botões do card da caixa de vagas quebram linha em vez de estender o card.
**Where**: `frontend/src/features/inbox/InboxPage.tsx`
**Depends on**: T6
**Reuses**: `Badge` com `whitespace-normal` via `className`
**Requirement**: RESP-03

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Auditoria: `/inbox` sem estouro em 320 e 375 px
- [x] Full gate passa

**Status**: concluída depois de ficar bloqueada. O commit leva só as trocas de classe, aplicadas sobre a versão commitada; o WIP da outra sessão (botão "Vaga encerrada") ficou fora do commit.
**Also touches**: `frontend/src/components/FeedbackButtons.tsx` (o grupo Gostei/Neutro/Não gostei passa a quebrar linha)

**Tests**: e2e
**Gate**: full

**Commit**: `fix(frontend): wrap inbox card badges and actions on narrow screens`

---

### T8: Tabelas de Insights contidas no card

**What**: Grades e cards de `/insights` deixam de crescer até a largura mínima das tabelas. As tabelas rolam dentro do card.
**Where**: `frontend/src/features/insights/InsightsPage.tsx`
**Depends on**: T7
**Reuses**: wrapper `overflow-x-auto` existente em `ui/table.tsx`
**Requirement**: RESP-04, RESP-01

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Auditoria: `/insights` sem estouro nas 4 larguras e as tabelas rolam dentro do card (RESP-04)
- [x] Auditoria completa sai com código 0 (8 rotas × 4 larguras), depois da T7
- [x] Build gate passa (lint, 54 testes, build)

**Tests**: e2e
**Gate**: full

**Commit**: `fix(frontend): contain insights tables within their cards`

---

### T9: Teste do NAV-11 com a sessão em cache (correção do Verifier, iteração 1)

**What**: Teste do NAV-11 montando o `AppLayout` com `queryKeys.me` já em cache, como acontece ao vir do login. O teste antigo passava com ou sem a proteção do primeiro carregamento (mutante M3 sobreviveu).
**Where**: `frontend/src/layouts/AppLayout.test.tsx`
**Depends on**: None (corrige o teste da T4, já concluída na Fase 1)
**Reuses**: `QueryClient` do TanStack Query com `setQueryData`
**Requirement**: NAV-11

**Done when**:

- [x] O mutante M3 (remover `if (previousPath.current === location.pathname) return`) faz o teste novo falhar
- [x] Quick gate passa: 8 testes em `AppLayout.test.tsx`

**Tests**: unit
**Gate**: quick

**Commit**: `test(frontend): cover first-load focus with a cached session`

---

### T10: Testes de interação repetida para NAV-10 e NAV-04 (correção do Verifier, iteração 2)

**What**: Dois testes: voltar à tela inicial (/board → /companies → /board) move o foco para `main`, e reabrir a gaveta depois de navegar por ela e fechar com Esc devolve o foco ao botão "Abrir menu". Os testes anteriores só cobriam a primeira interação (mutantes M7 e M9 sobreviveram).
**Where**: `frontend/src/layouts/AppLayout.test.tsx`
**Depends on**: T9
**Reuses**: `renderPanel` do próprio arquivo
**Requirement**: NAV-10, NAV-04

**Done when**:

- [x] M7 (remover `previousPath.current = location.pathname`) faz o teste de volta à tela inicial falhar
- [x] M9 (remover `navigated.current = false`) faz o teste de reabrir a gaveta falhar
- [x] Quick gate passa: 18 testes em `src/layouts`

**Tests**: unit
**Gate**: quick

**Commit**: `test(frontend): cover repeated navigation focus and drawer reopen`

---

## Phase Execution Map

```
Phase 1 → Phase 2 → Phase 3

Phase 1:  T1 ------→ T2 ------→ T3 ------→ T4
Phase 2:  T5 ------→ T6 ------→ T7 ------→ T8
Phase 3:  T9 ------→ T10
```

## Validation tables

| Task | Depends On (task body) | Diagram Shows | Status |
| ---- | ---------------------- | ------------- | ------ |
| T1 | None | start of Phase 1 | ✅ Match |
| T2 | T1 | T1 → T2 | ✅ Match |
| T3 | T2 | T2 → T3 | ✅ Match |
| T4 | T3 | T3 → T4 | ✅ Match |
| T5 | None | start of Phase 2 | ✅ Match |
| T6 | T5 | T5 → T6 | ✅ Match |
| T7 | T6 | T6 → T7 | ✅ Match |
| T8 | T7 | T7 → T8 | ✅ Match |
| T9 | None | start of Phase 3 | ✅ Match |
| T10 | T9 | T9 → T10 | ✅ Match |

| Task | Code Layer Created/Modified | Matrix Requires | Task Says | Status |
| ---- | --------------------------- | --------------- | --------- | ------ |
| T1 | Dados estáticos | none | none | ✅ OK |
| T2 | Componente React | unit | unit | ✅ OK |
| T3 | Layout React | unit (+ auditoria) | unit | ✅ OK |
| T4 | Layout React | unit | unit | ✅ OK |
| T5 | Script de verificação | none | none | ✅ OK |
| T6 | Só CSS | e2e | e2e | ✅ OK |
| T7 | Só CSS | e2e | e2e | ✅ OK |
| T8 | Só CSS | e2e | e2e | ✅ OK |
| T9 | Teste de layout React | unit | unit | ✅ OK |
| T10 | Teste de layout React | unit | unit | ✅ OK |
