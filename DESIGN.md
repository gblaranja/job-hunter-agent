---
name: Job Hunter Agent
description: Tinta azul-marinho sobre papel claro, com um laranja de destaque usado com parcimônia. Sóbrio no painel, mais expressivo na página pública.

# Espelho dos tokens de frontend/src/index.css, que é a fonte da verdade.
# Se um valor mudar lá, atualize aqui.
colors:
  brand-ink: "oklch(0.27 0.05 260)"          # superfícies de marca, marca do logo
  brand-paper: "oklch(0.98 0.004 250)"       # texto e preenchimentos sobre a tinta
  brand-paper-muted: "oklch(0.82 0.03 255)"  # texto secundário sobre a tinta
  brand-accent: "oklch(0.671 0.175 40.6)"    # laranja: só preenchimentos, marcas e bordas
  brand-accent-text: "oklch(0.54 0.16 40)"   # laranja para texto pequeno (5,2:1 no fundo claro)
  primary: "oklch(0.36 0.08 252)"            # ação principal
  background: "oklch(0.985 0.002 247)"
  card: "oklch(1 0 0)"
  muted-foreground: "oklch(0.5 0.02 255)"    # 5,7:1 no fundo
  success: "oklch(0.55 0.13 155)"
  warning: "oklch(0.72 0.15 75)"
  destructive: "oklch(0.577 0.245 27.325)"

typography:
  display:
    fontFamily: "Bricolage Grotesque Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "clamp(2.5rem, 1.7rem + 3.4vw, 4.25rem)"   # text-display
    fontWeight: 600
    lineHeight: 1.02
    letterSpacing: "-0.03em"
  headline:
    fontFamily: "Bricolage Grotesque Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "clamp(1.75rem, 1.35rem + 1.6vw, 2.5rem)"  # text-headline
    fontWeight: 600
    lineHeight: 1.1
    letterSpacing: "-0.02em"
  body:
    fontFamily: "ui-sans-serif, system-ui, sans-serif"
    fontSize: "1rem"

rounded:
  control: "0.5rem"   # rounded-md, botões e campos
  card: "14px"        # rounded-xl (--radius + 4px)
  panel: "1rem"       # rounded-2xl, Bento e painéis de marca

motion:
  ease: "cubic-bezier(0.16, 1, 0.3, 1)"  # ease-out-expo
---

# Design: Job Hunter Agent

## 1. Visão geral

Ferramenta pessoal e confiável, "não mágica". A identidade é a tinta azul-marinho (o logo, os painéis de marca) com um ponto de laranja. O painel é modo **Operar**: contido, denso, previsível. A home é modo **Persuadir**: tipografia de display, a cor de marca ocupando regiões inteiras e prévias do produto como prova. Veja os modos por superfície em [PRODUCT.md](PRODUCT.md).

## 2. O kit: um vocabulário para as páginas públicas

Antes de criar uma classe ou um container novo numa página pública, use o kit em `frontend/src/components/marketing/`:

- **`Section`**: faixa com largura máxima, respiro vertical e `scroll-mt` para âncoras sob o header fixo. `tone="raised"` separa a faixa com fundo de card e filetes.
- **`SectionHeader`**: `h2` em `font-display text-headline` com subtítulo opcional. **Sem eyebrow.**
- **`Bento` + `BentoTile`**: grade de 12 colunas com filetes de 1px entre os blocos (`span` 4, 5, 6, 7, 8 ou 12). É a resposta para agrupar de 2 a 6 itens sem card dentro de card. Varie as larguras pelo peso de cada item.

O texto da home fica em `frontend/src/features/home/content.ts`, separado da apresentação. No painel, os componentes continuam sendo os do shadcn em `components/ui/`.

Se o kit não cobrir uma forma que vai se repetir, ela entra no kit, não no arquivo da página.

## 3. Cor

- **Só OKLCH** para cores novas. (As séries de gráfico `--series-*` ainda estão em hex; migrar junto com uma revisão dos gráficos.)
- **O laranja depende do tamanho.** `brand-accent` preenche (o ponto do logo, marcadores, bordas tracejadas). Em texto pequeno ele dá 3:1 no fundo claro e reprova, então o texto usa `brand-accent-text`.
- **Texto secundário sobre a tinta usa `brand-paper-muted`**, nunca `opacity-*` ou `/70`: com alfa, o contraste passa a depender do que está atrás.
- Contraste mínimo: 4,5:1 para texto, 3:1 para texto grande, ícones e foco. Verifique os dois temas.
- Os temas claro e escuro são compostos à parte em `index.css`, não invertidos.

## 4. Tipografia

- **Bricolage Grotesque** (variável, hospedada pelo pacote `@fontsource-variable/bricolage-grotesque`) só para `h1` e `h2` da home e o título do login. Corpo e painel ficam na fonte do sistema.
- Use os papéis `text-display` e `text-headline`; não escreva `text-[44px]` nem `clamp()` solto na página.
- Títulos com `text-wrap: balance` e parágrafos com `pretty` (regra global). Medida do corpo até ~65ch (`max-w-prose`, `max-w-xl`).
- Números de dados (scores, percentuais, contagens) com `tabular-nums`.

## 5. Elevação e material

- Declare a elevação **uma vez**: borda **ou** sombra, nunca as duas no mesmo elemento.
- Sombra só para o que de fato flutua sobre outra coisa, e sempre com deslocamento e desfoque (`0 14px 32px -12px`). Nada de halo colorido sem deslocamento.
- Sem gradiente decorativo, glass ou blur de enfeite. O header fixo é sólido.

## 6. Movimento

Um momento autoral por página, não um fade em cada seção. Na home é a vaga da prévia avançando pelas etapas (`animate-stage-fill`, seguido de `animate-rise-in` na notificação). Sempre com `motion-safe:`, para que o estado sem animação já seja o final, e `ease-out-expo`.

## 7. Superfícies do navegador

Já temadas em `index.css`: `::selection`, `caret-color`, `text-underline-offset` e rolagem suave quando o usuário não pediu movimento reduzido. Toda página pública tem link "Pular para o conteúdo" e foco visível.

## 8. Faça e não faça

### Faça

- Prove com o produto: prévias com dados de exemplo **rotulados como exemplo** e coerentes com as regras reais (pesos do score, limiar de 70%).
- Dê a cada CTA o destino que o visitante procura: quem não é dono vai ao código; o dono entra pelo header.

### Não faça

- Eyebrow acima de títulos, ou numeração de seções sem informação real de sequência.
- Grade de cards iguais com ícone num quadrado arredondado.
- Texto colorido pequeno em `brand-accent`, ou texto com opacidade sobre fundo colorido.
- Borda lateral colorida (mais de 1px) em cards, alertas e itens de lista.
- Métrica gigante no hero, depoimentos ou números inventados.
