import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { useCurrentUser } from '@/api/queries'
import { Logo, LogoMark } from '@/components/brand/Logo'
import { BRAND } from '@/lib/brand'
import { LinkedinIcon } from '@/components/brand/LinkedinIcon'
import { Button } from '@/components/ui/button'
import {
  ArrowRight,
  BarChart3,
  Building2,
  Inbox,
  KanbanSquare,
  Lock,
  MailCheck,
  MessageSquareHeart,
  Server,
  ShieldCheck,
  Sparkles,
  Target,
} from 'lucide-react'

const features = [
  {
    Icon: Inbox,
    title: 'Coleta automática de vagas',
    text: 'Lê os alertas do LinkedIn e da Gupy na sua label do Gmail e consulta os job boards (Greenhouse, Lever, Ashby) das empresas que você acompanha. Duplicatas e reposts são detectados sozinhos.',
  },
  {
    Icon: Target,
    title: 'Score de aderência com IA',
    text: 'Cada vaga ganha um score de 0 a 100 contra o seu currículo: um cálculo determinístico por skills, senioridade e modelo de trabalho, ajustado pela Claude com uma justificativa curta.',
  },
  {
    Icon: KanbanSquare,
    title: 'Kanban de candidaturas',
    text: 'Da caixa de vagas ao "aplicada" com um arrastar. Ao aplicar, a vaga é congelada com o currículo enviado: você sabe exatamente o que mandou, mesmo depois que o anúncio sai do ar.',
  },
  {
    Icon: MailCheck,
    title: 'Retornos lidos por você',
    text: 'Convites de entrevista, testes técnicos, ofertas e recusas são classificados no Gmail e movem o card sozinhos quando a confiança é alta. O que for duvidoso cai numa fila de revisão de um clique.',
  },
  {
    Icon: BarChart3,
    title: 'Insights semanais',
    text: 'Todo domingo: as skills mais pedidas, suas lacunas, onde o funil trava e bullets do currículo reescritos para as vagas que você realmente quer.',
  },
  {
    Icon: Building2,
    title: 'Radar de empresas',
    text: 'Volume e frequência de vagas por semana, tempo médio no ar, reposts e alertas quando uma empresa-alvo abre vaga ou volta a contratar.',
  },
]

const steps = [
  { title: 'Conecte', text: 'Seu Gmail em modo leitura, só na label que você escolher, e o seu currículo.' },
  { title: 'Receba', text: 'Todo dia às 7h, as vagas novas chegam filtradas e ordenadas por aderência.' },
  { title: 'Candidate-se', text: 'Escolha, aplique e acompanhe tudo num kanban com o histórico completo.' },
  { title: 'Ajuste', text: 'Os retornos e os insights mostram o que mudar no currículo e nos filtros.' },
]

const principles = [
  {
    Icon: Sparkles,
    title: 'IA como reforço, não dependência',
    text: 'Sem chave da Claude API, tudo funciona com regras e dicionário. Com ela, a análise fica mais precisa, com custo visível por tarefa.',
  },
  {
    Icon: Lock,
    title: 'Seus dados ficam com você',
    text: 'Auto-hospedado: o banco, os currículos e os tokens do Gmail (cifrados com AES-GCM) ficam na sua instalação. O corpo dos e-mails não é guardado.',
  },
  {
    Icon: ShieldCheck,
    title: 'Histórico que não se perde',
    text: 'A linha do tempo das candidaturas é append-only: correções viram novos eventos, nunca edição do passado.',
  },
]

const stack = ['Java 25', 'Spring Boot 4.1', 'Spring Modulith', 'PostgreSQL 17', 'Claude API', 'React 19', 'TypeScript', 'Tailwind CSS', 'Testcontainers']

/** Página pública: o que é o produto, como funciona e quem fez. */
export function HomePage() {
  const me = useCurrentUser()
  const signedIn = Boolean(me.data)
  const cta = signedIn ? (
    <Button asChild size="lg">
      <Link to="/board">
        Abrir o painel <ArrowRight />
      </Link>
    </Button>
  ) : (
    <Button asChild size="lg">
      <Link to="/login">
        Entrar <ArrowRight />
      </Link>
    </Button>
  )

  return (
    <div className="min-h-svh bg-background text-foreground">
      <header className="sticky top-0 z-10 border-b bg-background/85 backdrop-blur">
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
            <a href="#autor" className="hover:text-foreground">
              Contribua
            </a>
          </nav>
          <Button asChild size="sm" variant={signedIn ? 'default' : 'outline'}>
            <Link to={signedIn ? '/board' : '/login'}>{signedIn ? 'Abrir o painel' : 'Entrar'}</Link>
          </Button>
        </div>
      </header>

      <main>
        <section className="mx-auto grid max-w-6xl items-center gap-12 px-4 py-16 md:grid-cols-[1.1fr_1fr] md:py-24">
          <div>
            <p className="mb-4 inline-flex items-center gap-2 rounded-full border bg-card px-3 py-1 text-xs font-medium text-muted-foreground">
              <span className="size-1.5 rounded-full bg-[var(--brand-accent)]" aria-hidden />
              Projeto aberto a feedback e contribuições
            </p>
            <h1 className="text-4xl font-semibold leading-tight tracking-tight md:text-5xl">
              A busca de emprego no piloto automático, <span className="text-primary">com você no comando.</span>
            </h1>
            <p className="mt-5 max-w-xl text-lg text-muted-foreground">
              O {BRAND.name} coleta vagas, mede a aderência ao seu currículo com IA, organiza suas candidaturas e lê os
              retornos no seu Gmail. Você decide onde aplicar; ele cuida do resto.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              {cta}
              <Button asChild size="lg" variant="outline">
                <a href="#funcionalidades">Ver funcionalidades</a>
              </Button>
            </div>
          </div>
          <PipelinePreview />
        </section>

        <section id="funcionalidades" className="border-y bg-card/60">
          <div className="mx-auto max-w-6xl px-4 py-16 md:py-20">
            <SectionTitle eyebrow="Funcionalidades" title="Do alerta à oferta, num lugar só" />
            <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {features.map(({ Icon, title, text }) => (
                <article key={title} className="rounded-xl border bg-card p-6">
                  <span className="mb-4 inline-flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <Icon className="size-5" aria-hidden />
                  </span>
                  <h3 className="font-semibold">{title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{text}</p>
                </article>
              ))}
            </div>
            <p className="mt-6 text-sm text-muted-foreground">
              Em construção: nichos (Financeiro e Consultorias) com fit por empresa, dados abertos do Banco Central e da
              Receita, e o selo GPTW verificado.
            </p>
          </div>
        </section>

        <section id="como-funciona" className="mx-auto max-w-6xl px-4 py-16 md:py-20">
          <SectionTitle eyebrow="Como funciona" title="Quatro passos, um agente trabalhando todo dia" />
          <ol className="mt-10 grid gap-6 md:grid-cols-4">
            {steps.map((step, index) => (
              <li key={step.title} className="relative rounded-xl border bg-card p-6">
                <span className="mb-3 inline-flex size-8 items-center justify-center rounded-full bg-[var(--brand-ink)] text-sm font-semibold text-[var(--brand-paper)]">
                  {index + 1}
                </span>
                <h3 className="font-semibold">{step.title}</h3>
                <p className="mt-2 text-sm text-muted-foreground">{step.text}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="border-y bg-card/60">
          <div className="mx-auto max-w-6xl px-4 py-16 md:py-20">
            <SectionTitle eyebrow="Princípios" title="Feito para ser confiável, não mágico" />
            <div className="mt-10 grid gap-6 md:grid-cols-3">
              {principles.map(({ Icon, title, text }) => (
                <div key={title}>
                  <Icon className="mb-3 size-6 text-primary" aria-hidden />
                  <h3 className="font-semibold">{title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{text}</p>
                </div>
              ))}
            </div>
            <div className="mt-12 flex flex-wrap items-center gap-2">
              <Server className="mr-1 size-4 text-muted-foreground" aria-hidden />
              <span className="mr-2 text-sm text-muted-foreground">Feito com</span>
              {stack.map((item) => (
                <span key={item} className="rounded-md border bg-background px-2.5 py-1 text-xs font-medium">
                  {item}
                </span>
              ))}
            </div>
          </div>
        </section>

        <section id="autor" className="mx-auto max-w-6xl px-4 py-16 md:py-20">
          <div className="grid items-center gap-8 rounded-2xl bg-[var(--brand-ink)] p-8 text-[var(--brand-paper)] md:grid-cols-[1fr_auto] md:p-12">
            <div>
              <MessageSquareHeart className="mb-4 size-8 text-[var(--brand-accent)]" aria-hidden />
              <h2 className="text-2xl font-semibold tracking-tight md:text-3xl">Feedbacks e contribuições são muito bem-vindos</h2>
              <p className="mt-3 max-w-2xl opacity-85">
                Criei o {BRAND.name} para a minha própria busca e como projeto de portfólio, e quero que ele sirva para
                mais gente. Se você é dev, achou um bug, tem uma ideia de fonte de vagas nova ou quer contribuir com
                código, fale comigo no LinkedIn.
              </p>
              <p className="mt-4 text-sm opacity-70">Por {BRAND.author}</p>
            </div>
            <Button asChild size="lg" className="bg-[var(--brand-paper)] text-[var(--brand-ink)] hover:bg-[var(--brand-paper)]/90">
              <a href={BRAND.linkedin} target="_blank" rel="noopener noreferrer">
                <LinkedinIcon /> Falar no LinkedIn
              </a>
            </Button>
          </div>
        </section>
      </main>

      <footer className="border-t">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-4 py-6 text-sm text-muted-foreground">
          <span className="inline-flex items-center gap-2">
            <LogoMark className="size-5" /> {BRAND.name} · {BRAND.tagline}
          </span>
          <a href={BRAND.linkedin} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1.5 hover:text-foreground">
            <LinkedinIcon /> {BRAND.author}
          </a>
        </div>
      </footer>
    </div>
  )
}

function SectionTitle({ eyebrow, title }: { eyebrow: string; title: string }) {
  return (
    <div>
      <p className="text-sm font-medium text-[var(--brand-accent)]">{eyebrow}</p>
      <h2 className="mt-2 text-2xl font-semibold tracking-tight md:text-3xl">{title}</h2>
    </div>
  )
}

/** Ilustração do fluxo: uma vaga passando pelas etapas do agente, feita só com HTML. */
function PipelinePreview() {
  return (
    <div className="relative" aria-hidden>
      <div className="absolute -inset-4 -z-10 rounded-3xl bg-gradient-to-br from-primary/10 via-transparent to-[var(--brand-accent)]/10" />
      <div className="rounded-2xl border bg-card p-5 shadow-sm">
        <div className="flex items-center justify-between">
          <div>
            <div className="text-xs text-muted-foreground">Coletada hoje, 7h · LinkedIn</div>
            <div className="mt-1 font-semibold">Desenvolvedor Java Sênior</div>
            <div className="text-sm text-muted-foreground">Fintech de Crédito · Remoto</div>
          </div>
          <span className="inline-flex h-11 w-14 items-center justify-center rounded-lg border border-success/30 bg-success/15 text-lg font-semibold text-success">
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
        <div className="mt-5 grid grid-cols-4 gap-2 text-center text-[11px] font-medium">
          {[
            ['Coletada', true],
            ['Aplicada', true],
            ['Entrevista', true],
            ['Oferta', false],
          ].map(([label, done]) => (
            <div key={label as string}>
              <div className={`mb-1.5 h-1.5 rounded-full ${done ? 'bg-primary' : 'bg-muted'}`} />
              <span className={done ? '' : 'text-muted-foreground'}>{label as string}</span>
            </div>
          ))}
        </div>
      </div>
      <div className="relative -mt-3 ml-6 hidden w-fit rounded-xl border bg-card px-4 py-3 text-sm shadow-sm sm:block">
        <div className="flex items-center gap-2">
          <MailCheck className="size-4 text-success" />
          <span>
            <strong className="font-medium">Convite para entrevista</strong> lido no Gmail
          </span>
        </div>
        <div className="mt-0.5 text-xs text-muted-foreground">Card movido para Entrevista · confiança 92%</div>
      </div>
    </div>
  )
}

function Chip({ tone, children }: { tone: 'ok' | 'gap'; children: ReactNode }) {
  return (
    <span
      className={
        tone === 'ok'
          ? 'rounded-md bg-success/12 px-2 py-0.5 text-xs font-medium text-success'
          : 'rounded-md border border-dashed border-[var(--brand-accent)]/60 px-2 py-0.5 text-xs font-medium text-[var(--brand-accent)]'
      }
    >
      {children}
    </span>
  )
}
