import type { CSSProperties, ReactNode } from 'react'
import { Link } from 'react-router'
import { ArrowRight, Check, CornerDownRight, MailCheck, Server, Sparkles } from 'lucide-react'
import { useCurrentUser } from '@/api/queries'
import { GithubIcon } from '@/components/brand/GithubIcon'
import { LinkedinIcon } from '@/components/brand/LinkedinIcon'
import { Logo, LogoMark } from '@/components/brand/Logo'
import { Bento, BentoTile } from '@/components/marketing/Bento'
import { Section, SectionHeader } from '@/components/marketing/Section'
import { Button } from '@/components/ui/button'
import { BRAND } from '@/lib/brand'
import { cn } from '@/lib/utils'
import { features, principles, roadmap, sources, stack, steps } from './content'

/** Página pública: o que é o produto, como funciona e como contribuir. */
export function HomePage() {
  const me = useCurrentUser()
  const signedIn = Boolean(me.data)

  return (
    <div className="min-h-svh bg-background text-foreground">
      <a
        href="#conteudo"
        className="sr-only z-20 rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Pular para o conteúdo
      </a>

      <header className="sticky top-0 z-10 border-b bg-background">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" aria-label={BRAND.name}>
            <Logo />
          </Link>
          <nav className="hidden items-center gap-6 text-sm text-muted-foreground md:flex" aria-label="Seções">
            <a href="#funcionalidades" className="hover:text-foreground">
              Funcionalidades
            </a>
            <a href="#como-funciona" className="hover:text-foreground">
              Como funciona
            </a>
            <a href="#contribuir" className="hover:text-foreground">
              Contribua
            </a>
          </nav>
          {/* Entrar é para quem tem a instalação; o visitante chega pelo GitHub, no hero. */}
          <Button asChild size="sm" variant={signedIn ? 'default' : 'ghost'}>
            <Link to={signedIn ? '/today' : '/login'}>{signedIn ? 'Abrir o painel' : 'Entrar'}</Link>
          </Button>
        </div>
      </header>

      <main id="conteudo" tabIndex={-1} className="outline-none">
        <section className="mx-auto grid max-w-6xl items-center gap-12 px-4 pt-12 pb-16 md:grid-cols-12 md:pt-20 md:pb-24">
          <div className="md:col-span-7">
            <h1 className="font-display text-display font-semibold">
              A busca de emprego no piloto automático, <span className="text-primary">com você no comando.</span>
            </h1>
            <p className="mt-6 max-w-xl text-lg text-muted-foreground">
              O {BRAND.name} coleta vagas, mede a aderência ao seu currículo com IA, organiza suas candidaturas e lê os
              retornos no seu Gmail. Você decide onde aplicar; ele cuida do resto.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              {signedIn ? (
                <Button asChild size="lg">
                  <Link to="/today">
                    Abrir o painel <ArrowRight />
                  </Link>
                </Button>
              ) : (
                <Button asChild size="lg">
                  <a href={BRAND.repository} target="_blank" rel="noopener noreferrer">
                    <GithubIcon /> Ver o código no GitHub
                  </a>
                </Button>
              )}
              <Button asChild size="lg" variant="outline">
                <a href="#como-funciona">Como funciona</a>
              </Button>
            </div>
          </div>
          <PipelinePreview />
        </section>

        <Section id="funcionalidades" tone="raised" aria-labelledby="funcionalidades-titulo">
          <SectionHeader id="funcionalidades-titulo" title="Do alerta à oferta, num lugar só">
            Seis frentes que costumam viver em planilhas, abas e caixas de entrada diferentes.
          </SectionHeader>
          <Bento className="mt-12">
            {features.map((feature) => (
              <BentoTile key={feature.title} span={feature.span}>
                <h3 className="text-lg font-semibold">{feature.title}</h3>
                <p className="mt-2 max-w-prose text-muted-foreground">{feature.text}</p>
                {'proof' in feature && <FeatureProof kind={feature.proof} />}
              </BentoTile>
            ))}
          </Bento>
          <p className="mt-6 text-sm text-muted-foreground">{roadmap}</p>
        </Section>

        <Section id="como-funciona" aria-labelledby="como-funciona-titulo">
          <SectionHeader id="como-funciona-titulo" title="Quatro passos, um agente trabalhando todo dia" />
          <ol className="mt-12 grid gap-10 md:grid-cols-4 md:gap-8">
            {steps.map((step, index) => (
              <li key={step.title} className="relative border-l pl-8 md:border-t md:border-l-0 md:pt-8 md:pl-0">
                <span
                  className="absolute top-0 left-0 inline-flex size-7 -translate-x-1/2 items-center justify-center rounded-full bg-brand-ink text-sm font-semibold text-brand-paper tabular-nums ring-4 ring-background md:-translate-y-1/2 md:translate-x-0"
                  aria-hidden
                >
                  {index + 1}
                </span>
                <h3 className="text-lg font-semibold">{step.title}</h3>
                <p className="mt-2 text-muted-foreground">{step.text}</p>
              </li>
            ))}
          </ol>
        </Section>

        <Section tone="raised" aria-labelledby="principios-titulo">
          <SectionHeader id="principios-titulo" title="Feito para ser confiável, não mágico" />
          <div className="mt-12 grid gap-10 md:grid-cols-3 md:gap-8">
            {principles.map(({ Icon, title, text }) => (
              <div key={title}>
                <h3 className="flex items-center gap-2 text-lg font-semibold">
                  <Icon className="size-5 shrink-0 text-primary" aria-hidden />
                  {title}
                </h3>
                <p className="mt-2 text-muted-foreground">{text}</p>
              </div>
            ))}
          </div>
          <div className="mt-14 flex flex-wrap items-center gap-2 border-t pt-8">
            <Server className="mr-1 size-4 text-muted-foreground" aria-hidden />
            <span className="mr-2 text-sm text-muted-foreground">Feito com</span>
            {stack.map((item) => (
              <span key={item} className="rounded-md border bg-background px-2.5 py-1 text-xs font-medium">
                {item}
              </span>
            ))}
          </div>
        </Section>

        <section id="contribuir" className="mx-auto max-w-6xl scroll-mt-16 px-4 py-16 md:py-24" aria-labelledby="contribuir-titulo">
          <div className="grid items-center gap-8 rounded-2xl bg-brand-ink p-8 text-brand-paper md:grid-cols-[1fr_auto] md:p-12">
            <div>
              <h2 id="contribuir-titulo" className="font-display text-headline font-semibold">
                Feedbacks e contribuições são muito bem-vindos
              </h2>
              <p className="mt-4 max-w-2xl text-brand-paper-muted">
                Criei o {BRAND.name} para a minha própria busca e como projeto de portfólio, e quero que ele sirva para
                mais gente. Se você é dev, achou um bug, tem uma ideia de fonte de vagas nova ou quer contribuir com
                código, abra uma issue ou fale comigo no LinkedIn.
              </p>
              <p className="mt-4 text-sm text-brand-paper-muted">Por {BRAND.author}</p>
            </div>
            <div className="flex flex-col gap-3 sm:flex-row md:flex-col">
              <Button asChild size="lg" className="bg-brand-paper text-brand-ink hover:bg-brand-paper-muted">
                <a href={BRAND.linkedin} target="_blank" rel="noopener noreferrer">
                  <LinkedinIcon /> Falar no LinkedIn
                </a>
              </Button>
              <Button
                asChild
                size="lg"
                variant="outline"
                className="border-brand-paper-muted bg-transparent text-brand-paper hover:bg-brand-paper hover:text-brand-ink dark:bg-transparent dark:hover:bg-brand-paper"
              >
                <a href={BRAND.repository} target="_blank" rel="noopener noreferrer">
                  <GithubIcon /> Abrir uma issue
                </a>
              </Button>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-4 py-6 text-sm text-muted-foreground">
          <span className="inline-flex items-center gap-2">
            <LogoMark className="size-5" /> {BRAND.name} · {BRAND.tagline}
          </span>
          <span className="flex flex-wrap items-center gap-5">
            <a href={BRAND.repository} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1.5 hover:text-foreground">
              <GithubIcon /> Código
            </a>
            <a href={BRAND.linkedin} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1.5 hover:text-foreground">
              <LinkedinIcon /> {BRAND.author}
            </a>
          </span>
        </div>
      </footer>
    </div>
  )
}

/**
 * Prévia do fluxo no hero: uma vaga de exemplo passando pelas etapas, feita só com HTML.
 * É o único momento animado da página, e só para quem não pediu movimento reduzido.
 */
function PipelinePreview() {
  const stages = [
    ['Coletada', true],
    ['Aplicada', true],
    ['Entrevista', true],
    ['Oferta', false],
  ] as const

  return (
    <figure className="md:col-span-5">
      <div className="rounded-2xl bg-brand-ink p-4 sm:p-6" aria-hidden>
        <div className="rounded-xl bg-card p-5">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="text-xs text-muted-foreground">Coletada hoje, 7h · LinkedIn</div>
              <div className="mt-1 font-semibold">Desenvolvedor Java Sênior</div>
              <div className="text-sm text-muted-foreground">Fintech de Crédito · Remoto</div>
            </div>
            <span className="inline-flex h-11 w-14 shrink-0 items-center justify-center rounded-lg bg-success/15 text-lg font-semibold text-success tabular-nums">
              87
            </span>
          </div>
          <div className="mt-4 flex flex-wrap gap-1.5">
            {['Java', 'Spring Boot', 'Kafka', 'AWS'].map((skill) => (
              <Chip key={skill} tone="ok">
                {skill}
              </Chip>
            ))}
            <Chip tone="gap">Kubernetes</Chip>
          </div>
          <p className="mt-4 rounded-lg bg-muted p-3 text-xs leading-relaxed text-muted-foreground">
            <Sparkles className="mr-1 inline size-3.5 text-primary" />
            Boa aderência: o currículo cobre as obrigatórias e o domínio de pagamentos. Falta Kubernetes, pedido como
            diferencial.
          </p>
          <div className="mt-5 grid grid-cols-4 gap-2 text-center text-xs font-medium">
            {stages.map(([label, done], index) => (
              <div key={label}>
                <div className="mb-1.5 h-1.5 overflow-hidden rounded-full bg-muted">
                  {done && (
                    <div
                      className="h-full origin-left rounded-full bg-primary motion-safe:animate-stage-fill"
                      style={{ animationDelay: `${300 + index * 220}ms` }}
                    />
                  )}
                </div>
                <span className={done ? '' : 'text-muted-foreground'}>{label}</span>
              </div>
            ))}
          </div>
        </div>
        <div
          className="relative -mt-3 ml-6 hidden w-fit rounded-xl bg-card px-4 py-3 text-sm shadow-[0_14px_32px_-12px_oklch(0.12_0.04_260/0.55)] motion-safe:animate-rise-in sm:block"
          style={{ animationDelay: '1100ms' } satisfies CSSProperties}
        >
          <div className="flex items-center gap-2">
            <MailCheck className="size-4 text-success" />
            <span>
              <strong className="font-medium">Convite para entrevista</strong> lido no Gmail
            </span>
          </div>
          <div className="mt-0.5 text-xs text-muted-foreground tabular-nums">Card movido para Entrevista · confiança 92%</div>
        </div>
      </div>
      <figcaption className="mt-3 text-sm text-muted-foreground">
        Exemplo ilustrativo: uma vaga do alerta da manhã até o convite de entrevista.
      </figcaption>
    </figure>
  )
}

/** Prévias pequenas dentro dos blocos do Bento. Os valores são de exemplo, e o rótulo diz isso. */
function FeatureProof({ kind }: { kind: 'score' | 'sources' | 'responses' }) {
  if (kind === 'sources') {
    return (
      <dl className="mt-auto grid gap-4 pt-8">
        {sources.map(({ channel, names }) => (
          <div key={channel}>
            <dt className="text-sm text-muted-foreground">{channel}</dt>
            <dd className="mt-1.5 flex flex-wrap gap-1.5">
              {names.map((name) => (
                <span key={name} className="rounded-md border bg-background px-2.5 py-1 text-sm font-medium">
                  {name}
                </span>
              ))}
            </dd>
          </div>
        ))}
      </dl>
    )
  }

  if (kind === 'score') {
    // Pesos do DeterministicScorer no backend; o ajuste da Claude vai de -10 a +10.
    // No celular a barra desce para a linha de baixo, para não espremer entre rótulo e valor.
    const scoreRow = 'grid grid-cols-[1fr_auto] items-center gap-x-3 gap-y-1.5 sm:grid-cols-[9rem_1fr_3.5rem]'
    const factors = [
      ['Skills obrigatórias', 63, 70],
      ['Diferenciais', 8, 15],
      ['Senioridade', 10, 10],
      ['Modelo de trabalho', 5, 5],
    ] as const
    return (
      <ProofBlock caption="Exemplo: 86 pelas regras, mais 1 do ajuste da Claude, dá o 87 da vaga do exemplo no topo.">
        <dl className="grid gap-3 text-sm">
          {factors.map(([label, value, max]) => (
            <div key={label} className={scoreRow}>
              <dt className="text-muted-foreground">{label}</dt>
              <dd className="col-span-2 h-1.5 overflow-hidden rounded-full bg-background sm:order-none sm:col-span-1 max-sm:order-last">
                <div className="h-full rounded-full bg-primary" style={{ width: `${(value / max) * 100}%` }} />
              </dd>
              <dd className="text-right font-medium tabular-nums">
                {value}/{max}
              </dd>
            </div>
          ))}
          <div className={cn(scoreRow, 'border-t pt-3')}>
            <dt className="text-muted-foreground">Ajuste da Claude</dt>
            <dd className="col-span-2 text-xs text-muted-foreground sm:order-none sm:col-span-1 sm:text-sm max-sm:order-last">
              de −10 a +10, com justificativa
            </dd>
            <dd className="text-right font-medium tabular-nums">+1</dd>
          </div>
        </dl>
      </ProofBlock>
    )
  }

  const responses = [
    { subject: 'Convite para entrevista', outcome: 'Movido para Entrevista', confidence: 92, auto: true },
    { subject: 'Teste técnico', outcome: 'Fila de revisão', confidence: 61, auto: false },
    { subject: 'Recusa', outcome: 'Movido para Recusada', confidence: 95, auto: true },
  ]
  return (
    <ProofBlock caption="Exemplo de retornos classificados">
      <ul className="grid gap-2.5 text-sm">
        {responses.map((response) => (
          <li key={response.subject} className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span className="font-medium">{response.subject}</span>
            <span className="inline-flex items-center gap-1.5 text-muted-foreground">
              {response.auto ? (
                <Check className="size-4 text-success" aria-hidden />
              ) : (
                <CornerDownRight className="size-4 text-brand-accent-text" aria-hidden />
              )}
              {response.outcome}
            </span>
            <span className="ml-auto text-muted-foreground tabular-nums">{response.confidence}%</span>
          </li>
        ))}
      </ul>
    </ProofBlock>
  )
}

function ProofBlock({ caption, children }: { caption: string; children: ReactNode }) {
  return (
    <figure className="mt-auto pt-8">
      <div className="rounded-lg bg-muted/60 p-4">{children}</div>
      <figcaption className="mt-2 text-xs text-muted-foreground">{caption}</figcaption>
    </figure>
  )
}

function Chip({ tone, children }: { tone: 'ok' | 'gap'; children: ReactNode }) {
  return (
    <span
      className={
        tone === 'ok'
          ? 'rounded-md bg-success/12 px-2 py-0.5 text-xs font-medium text-success'
          : 'rounded-md border border-dashed border-brand-accent px-2 py-0.5 text-xs font-medium text-brand-accent-text'
      }
    >
      {children}
    </span>
  )
}
