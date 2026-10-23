# LESSONS - auto-maintained by scripts/lessons.py

> Machine-owned. Do NOT hand-edit. Changes are overwritten on the next `lessons.py` write.
> Canonical state lives in `.specs/lessons.json`. Edit lessons only via the script.
> promote_threshold=2 distinct features · window_days=45 · quarantine_threshold=2

## Confirmed (load these at Specify/Design)

Corroborated across multiple features. Safe to apply as guidance.

_none_

## Candidates (under observation - do NOT load as guidance yet)

Seen once or not yet corroborated. Tracked, not trusted.

### L-001 - Para testar que algo não acontece no primeiro carregamento, monte o componente com os dados já em cache, para que o estado de carregamento não esconda o comportamento
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `frontend-tests` · harmful: 0
- features: painel-navegacao-responsiva
- evidence: validation.md M3 (frontend/src/layouts/AppLayout.tsx:23, NAV-11) (frontend-tests)
- last seen: 2026-10-23T19:42:30Z

### L-002 - Quando o código guarda estado em ref entre eventos, teste a interação repetida (voltar à tela inicial, reabrir e fechar de novo), não só a primeira
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `frontend-tests` · harmful: 0
- features: painel-navegacao-responsiva
- evidence: validation.md M7 (frontend/src/layouts/AppLayout.tsx:24, NAV-10) (frontend-tests) (+1 more)
- last seen: 2026-10-23T19:49:43Z

## Quarantined (failed when applied - ignore)

A confirmed lesson that recurred alongside failure. Kept for the maintainer to review.

_none_
