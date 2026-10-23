# Navegação e responsividade do painel: validação

**Date**: 2026-10-23
**Spec**: `.specs/features/painel-navegacao-responsiva/spec.md`
**Diff range**: `aa574a5..HEAD` (`754fac6`), branch `feat/login-google-home`. Commits b09fd51, 6c67884, 149ce53, 29fc26c, 3ffbf48, 7ba56e2, b7f606b, 9d8ed88, a24ce04, 1c611a9, 347cf42, 754fac6.
**Verifier**: independent sub-agent (author ≠ verifier), iteração 3 de 3

**Verdict**: PASS ✅

Motivo: os 18 requisitos têm evidência. M3, M7 e M9 agora morrem. Quatro mutantes novos também morreram. Gate e auditoria passam.

---

## Histórico

### Iteração 1: FAIL

- Gap: o mutante M3 (remover a guarda `if (previousPath.current === location.pathname) return`, `AppLayout.tsx:23`) sobrevivia.
- Causa: o teste do NAV-11 rodava com a sessão carregando. O `<main>` ainda não existia no primeiro efeito.
- Correção: commit `1c611a9` (teste do NAV-11 com `queryKeys.me` em cache) e `347cf42` (T9 em tasks.md).

### Iteração 2: FAIL

- M3 morreu. Dois mutantes novos sobreviveram:
  - M7 (remover `previousPath.current = location.pathname`, `AppLayout.tsx:24`): voltar para a tela inicial não movia o foco (NAV-10).
  - M9 (remover `navigated.current = false`, `MobileNav.tsx:28`): depois de navegar pela gaveta, Esc não devolvia o foco ao botão (NAV-04).
- Causa: os testes só exercitavam a primeira interação.
- Correção: commit `754fac6` (T10), com dois testes de interação repetida em `AppLayout.test.tsx`.

### Iteração 3: PASS (este relatório)

- Código de produção sem mudança desde a iteração 2: `git diff 347cf42..HEAD --stat -- frontend/src ':!*.test.tsx'` vazio.
- M3, M7 e M9 reexecutados: os três morrem.

---

## Task Completion

| Task | Status | Notes |
| ---- | ------ | ----- |
| T1 | ✅ Done | `frontend/src/layouts/navigation.ts:14-24`. Ordem, rótulos e rotas iguais aos de `aa574a5` |
| T2 | ✅ Done | `MobileNav.tsx` + 8 testes em `MobileNav.test.tsx` |
| T3 | ✅ Done | Barra superior móvel. NAV-01/NAV-07 conferidos no Chrome headless |
| T4 | ✅ Done | Atalho e foco ao navegar |
| T5 | ✅ Done | `responsive-audit.mjs` |
| T6 | ✅ Done | `BoardPage.tsx:71` com `relative` |
| T7 | ✅ Done | `InboxPage.tsx` e `FeedbackButtons.tsx` |
| T8 | ✅ Done | `InsightsPage.tsx` com `grid-cols-1` |
| T9 | ✅ Done | `AppLayout.test.tsx:70-90`. Mata M3 e M18 |
| T10 | ✅ Done | `AppLayout.test.tsx:102-129`. Mata M7 e M9 |

---

## Spec-Anchored Acceptance Criteria

Testes unitários: `frontend/src/layouts/MobileNav.test.tsx` (MN) e `frontend/src/layouts/AppLayout.test.tsx` (AL).
Evidência de navegador: auditoria `responsive-audit.mjs` (exit 0 nesta iteração) e uma sonda CDP própria do Verifier, só leitura, contra o dev server em :5173. O script e o log ficam no scratchpad, fora do repo.

| Req | Critério | Resultado esperado pela spec | Evidência (`file:line` + asserção) | Resultado |
| --- | -------- | ---------------------------- | ---------------------------------- | --------- |
| NAV-01 | WHILE largura < 768 px | Barra com logo (link para `/`), sino e "Abrir menu"; sem faixa de navegação | Código: `AppLayout.tsx:52` (logo), `:55` (sino), `:56-60` (`MobileNav` com `md:hidden`), `:62` (`nav` com `hidden md:flex`). Sonda em `/board`, 320/375/767 px: `menuBtn true`, `logo true`, `logoHref "/"`, `bell true`, `sidebarNav false` | ✅ PASS |
| NAV-02 | WHEN aciona "Abrir menu" | Diálogo "Menu" com os 9 links, na ordem e rótulos atuais | `MobileNav.test.tsx:26` - `findByRole('dialog', { name: 'Menu' })`; `:35-45` - `expect(links.map(textContent)).toEqual(['Caixa de vagas', …, 'Configurações'])` | ✅ PASS |
| NAV-03 | WHEN aciona link da gaveta | Navega para a rota e fecha a gaveta | `MobileNav.test.tsx:54` - `toHaveTextContent('/companies')`; `:55` - `queryByRole('dialog', { name: 'Menu' })).not.toBeInTheDocument()` | ✅ PASS |
| NAV-04 | WHEN Esc ou "Fechar" | Fecha e devolve o foco a "Abrir menu" | Esc: `MobileNav.test.tsx:64-65` - dialog ausente + `getByRole('button', { name: 'Abrir menu' })).toHaveFocus()`. Fechar: `:74-75`, mesmas asserções. Reabrir depois de navegar e fechar com Esc: `AppLayout.test.tsx:127-128` - dialog ausente + botão com foco. Mata M9 | ✅ PASS |
| NAV-05 | Rodapé da gaveta | Nome do usuário e "Sair" que encerra a sessão | `MobileNav.test.tsx:83` - `within(menu).getByText('gabriel')`; `:86` - `expect(onLogout).toHaveBeenCalledTimes(1)`. Sessão encerrada de fato: `AppLayout.test.tsx:151` - `findByText('Tela de login')` (mata M14). Posição no rodapé (jsdom não mede layout): sonda em 375 px, diálogo 0–812, `nav` 64–456, "Sair" 764–796, texto do rodapé "admin" | ✅ PASS |
| NAV-06 | Tela atual | `aria-current="page"` na gaveta e na sidebar | Gaveta: `MobileNav.test.tsx:94` - `toHaveAttribute('aria-current', 'page')`, `:95` negativo. Sidebar: `AppLayout.test.tsx:38-39`, mesmas asserções | ✅ PASS |
| NAV-07 | WHILE largura ≥ 768 px | Sidebar atual, sem "Abrir menu" | Sonda em 768 e 1024 px: `menuBtn false`, `sidebarNav true` | ✅ PASS |
| RESP-01 | 8 rotas × 4 larguras | `scrollWidth <= clientWidth` | `responsive-audit.mjs:89` - `overflow = path !== route \|\| scrollWidth > clientWidth`. Execução nesta iteração: 32/32 `ok`, "Nenhuma rota rola na horizontal.", exit 0 | ✅ PASS |
| RESP-02 | Kanban mais largo que a tela | Colunas rolam dentro do quadro; navegação e cabeçalho na largura da janela | Sonda em `/board`: quadro `2720/320`, `2720/375`, `2720/767`, `2752/528`, `2752/784`; documento igual à janela nas 5 larguras | ✅ PASS |
| RESP-03 | Texto de card maior que a largura | Quebra em linhas, card não passa da janela | Auditoria: `/inbox` ok em 320 e 375 px. Sonda sintética nova: em `/inbox` troquei no DOM do navegador o texto da empresa, do local e de um chip por ~340 caracteres. Em 320 px a altura foi de 40/22/22 para 300/166/166 px, borda do card 304, documento `320/320`. Em 375 px: 200/134/134 px, card 359, documento `375/375` | ✅ PASS |
| RESP-04 | Tabela de `/insights` mais larga que o card | Rola dentro do card sem alargar card nem página | Sonda: tabelas `350>238` e `353>238` (320 px), `350>293` e `353>293` (375 px), `350>298` e `353>298` (1024 px), todas com `overflow-x: auto`. Borda máxima dos cards 304, 359, 992. Documento igual à janela | ✅ PASS |
| NAV-08 | Primeiro focável | Link "Pular para o conteúdo" → `<main>`, oculto até o foco | `AppLayout.test.tsx:49-50` - após `userEvent.tab()`, `skip).toHaveFocus()` e `toHaveAttribute('href', '#conteudo')`; `main id="conteudo"` em `AppLayout.tsx:92`. Oculto até o foco (CSS): sonda nas 5 larguras, largura 1 px antes do foco, 170 px e visível depois do Tab | ✅ PASS |
| NAV-09 | WHEN aciona o atalho | Foco na região principal | `AppLayout.test.tsx:59` - `getByRole('main')).toHaveFocus()`. Sonda: Tab + Enter deixa `MAIN#conteudo` com foco nas 5 larguras | ✅ PASS |
| NAV-10 | WHEN a rota muda de uma tela para outra | Foco na região principal | Sidebar: `AppLayout.test.tsx:98-99` - `main).toHaveFocus()`. Volta para a tela inicial: `:111-112` (mata M7). Gaveta: `:138-140` - dialog ausente e `main).toHaveFocus()` (mata M13) | ✅ PASS |
| NAV-11 | WHEN o painel carrega pela primeira vez | Foco padrão, sem ir para `<main>` | `AppLayout.test.tsx:88-89` (sessão em cache) - `main).not.toHaveFocus()` e `document.body).toHaveFocus()`. Também `:66-67` (sessão carregando). Mata M3 e M18 | ✅ PASS |
| NAV-12 | Gaveta aberta | Sino inacessível atrás do overlay | `MobileNav.test.tsx:104` - `queryByRole('button', { name: 'Notificações' })).not.toBeInTheDocument()`, com `:100` como controle positivo. Usa um botão substituto no lugar do `NotificationBell`. M10 (`modal={false}`) morto na iteração 2 | ✅ PASS |
| NAV-13 | Sessão acaba com a gaveta aberta | Vai para `/login` sem overlay | `AppLayout.test.tsx:151-152` - `findByText('Tela de login')` e `queryByRole('dialog')).not.toBeInTheDocument()` | ✅ PASS (nota) |
| NAV-14 | Link da tela atual | Fecha sem erro | `MobileNav.test.tsx:113-114` - path continua `/board` e dialog ausente. Mata M12 | ✅ PASS |

**Status**: ✅ Todos os ACs cobertos. Nenhum spec-precision gap: a spec define resultados exatos para todos.

### Juízo sobre as notas anteriores

- **NAV-05**: suficiente. O teste unitário prova nome e chamada do "Sair". O teste de integração prova que a sessão acaba (M14 morto). A posição no rodapé é CSS e foi medida no navegador.
- **NAV-13**: aceito com nota. O teste dispara pelo "Sair", não por um 401. Os dois caminhos chegam ao mesmo estado: `queries.ts:81-84` (`setQueryData(queryKeys.me, null)`) e `main.tsx:15` (mesma chamada no 401). O handler do 401 é anterior à feature (`9dbea0e`). A parte da feature, não sobrar overlay quando `me` vira `null`, está coberta.
- **RESP-03**: fechado nesta iteração. A nota dizia que só havia dados reais. A sonda sintética acima prova a quebra de linha com textos longos nos três elementos citados pela spec.

---

## Discrimination Sensor

Worktree temporário em `HEAD` (`754fac6`), `node_modules` por symlink, comando `npx vitest run --maxWorkers=4 src/layouts` a partir de `frontend/`. Linha de base no worktree: 18/18 passam. Cada mutação foi aplicada sozinha e revertida antes da próxima.

| # | File:line | Mutação | Testes que falharam | Killed? |
| - | --------- | ------- | ------------------- | ------- |
| M3 | `frontend/src/layouts/AppLayout.tsx:23` | Remove a guarda `if (previousPath.current === location.pathname) return` (repete a iteração 1) | AL "no primeiro carregamento com a sessão já em cache…" | ✅ Killed |
| M7 | `frontend/src/layouts/AppLayout.tsx:24` | Remove `previousPath.current = location.pathname` (repete a iteração 2) | AL "ao voltar para a tela inicial, também move o foco…" | ✅ Killed |
| M9 | `frontend/src/layouts/MobileNav.tsx:28` | Remove `navigated.current = false` (repete a iteração 2) | AL "depois de navegar pela gaveta, reabrir e fechar com Esc…" | ✅ Killed |
| M12 | `frontend/src/layouts/MobileNav.tsx:50-52` | Novo (NAV-14): o link só fecha a gaveta se não for a tela atual (`if (aria-current !== 'page') setOpen(false)`) | MN "fecha sem erro ao escolher a tela em que já está" | ✅ Killed |
| M13 | `frontend/src/layouts/MobileNav.tsx:51` | Novo (NAV-10 pela gaveta): remove `navigated.current = true`. O Radix devolve o foco ao botão depois de navegar | AL "ao trocar de tela pela gaveta, o foco fica na região principal…" | ✅ Killed |
| M14 | `frontend/src/layouts/AppLayout.tsx:58` | Novo (NAV-05/NAV-13): `onLogout={() => {}}` no `MobileNav`. O "Sair" da gaveta não encerra a sessão | AL "ao sair pela gaveta, vai para o login sem deixar a gaveta aberta" | ✅ Killed |
| M18 | `frontend/src/layouts/AppLayout.tsx:19` | Novo (NAV-11): `useRef<string \| null>(null)`. O primeiro efeito move o foco para `<main>` | AL "no primeiro carregamento com a sessão já em cache…" | ✅ Killed |

As mutações de CSS (T6–T8) não foram executadas: a auditoria mede o dev server, que serve o working tree real, e o Verifier não muta o tree real. Mesma limitação das iterações 1 e 2. A sonda de navegador cobre o resultado desses ACs.

**Sensor depth**: lightweight (7 mutações nesta iteração; 4 novas)
**Result**: 7/7 killed - PASS ✅

**Isolamento**: worktree removido com `git worktree remove --force` e `git worktree prune`. Nenhum `git stash`. As 48 linhas da linha de base de `git status --porcelain` continuam iguais. Durante a verificação apareceram linhas novas (`package.json`, `Logo.tsx`, `index.css`, `HomePage.tsx`, `LoginPage.tsx`, `components/marketing/` e outras). Elas vêm de outra sessão que editava o tree ao mesmo tempo (horários a partir de 16:51:44). Nenhuma delas foi tocada pelo Verifier. `frontend/src/layouts` está igual ao `HEAD`.

---

## Code Quality

| Principle | Status |
| --------- | ------ |
| Minimum code | ✅ `navigation.ts` só move o array. `MobileNav` reusa `Sheet` e `NavLink` |
| Surgical changes | ✅ T6–T8 trocam só classes. T9 e T10 só acrescentam testes |
| No scope creep | ✅ `FeedbackButtons.tsx` e a mudança da T4 em `MobileNav.tsx` estão documentadas em tasks.md |
| Matches patterns | ✅ Classes `md:`, `cn()`, botões `ghost`/`icon-sm`, comentários curtos em português |
| Spec-anchored outcome check | ✅ Asserções miram nome acessível, URL, `aria-current` e foco exatos da spec |
| Per-layer Coverage Expectation | ✅ Componente: interação inicial e repetida cobertas. CSS: auditoria exit 0 nas 8 rotas × 4 larguras, sonda para NAV-01/05/07/08 e RESP-02/03/04 |
| Every test maps to a spec requirement | ✅ 18 testes em `src/layouts`, todos ligados a NAV-02..NAV-14 |
| Documented guidelines followed | ✅ `README.md` (seção Testes) e `frontend/vite.config.ts` (Vitest + jsdom, `css: false`) |

Observação menor (não é gap): ao escolher na gaveta a tela atual (NAV-14), o foco cai no `body`. O `AppLayout` não move o foco (o caminho não mudou) e o `onCloseAutoFocus` cancela a volta ao botão. A spec só pede "fechar sem erro", então isso não quebra AC.

---

## Edge Cases

- [x] NAV-12: sino inacessível com a gaveta aberta (`MobileNav.test.tsx:104`)
- [x] NAV-13: sair com a gaveta aberta leva ao login sem overlay (`AppLayout.test.tsx:151-152`)
- [x] NAV-14: escolher a tela atual fecha sem erro (`MobileNav.test.tsx:113-114`)

---

## Gate Check

- **Gate command**: `npm run lint && npx vitest run --maxWorkers=4 && npm run build` (de `frontend/`, no working tree real)
- **Lint**: exit 0
- **Vitest**: 58 passed, 0 failed, 0 skipped (18 arquivos). Inclui testes não commitados de outras sessões
- **Build**: exit 0 (só o aviso conhecido de chunk > 500 kB)
- **Test count before feature** (commitado em `aa574a5`): 33
- **Test count after feature** (commitado em `HEAD`): 51
- **Delta**: +18 (MobileNav 8, AppLayout 10). Nenhum teste removido nem enfraquecido
- **Audit**: `node .specs/features/painel-navegacao-responsiva/responsive-audit.mjs` → exit 0, 32/32 `ok`. Mediu o working tree real, que agora tem WIP de outra sessão em `index.css` e `Logo.tsx`
- **Failures**: nenhuma

---

## Fix Plans

Nenhum. Os dois gaps da iteração 2 foram resolvidos pela T10.

---

## Requirement Traceability Update

| Requirement | Previous Status | New Status |
| ----------- | --------------- | ---------- |
| NAV-01..NAV-14 | Implementing | ✅ Verified |
| RESP-01..RESP-04 | Implementing | ✅ Verified |

---

## Summary

**Overall**: ✅ Ready

**Spec-anchored check**: 18/18 ACs com evidência que discrimina. 0 spec-precision gaps.
**Sensor**: 7/7 mutações mortas (M3, M7, M9 repetidas; M12, M13, M14, M18 novas)
**Gate**: lint ok, 58/58 testes, build ok; auditoria exit 0

**What works**: gaveta, barra superior, sidebar, atalho, foco ao navegar (primeira e repetida), modal e rolagem horizontal. Conferido em teste e no navegador.

**Issues found**: nenhum que quebre AC. Fica a observação menor do foco no NAV-14.

**Next steps**: feature pronta para revisão e merge.
