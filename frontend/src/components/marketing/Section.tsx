import type { ComponentProps, ReactNode } from 'react'
import { cn } from '@/lib/utils'

/**
 * Faixa da página pública: largura, respiro vertical e folga para o header fixo ao chegar por âncora.
 * `raised` separa a faixa das vizinhas com fundo e filetes, sem virar card.
 */
export function Section({
  tone = 'plain',
  className,
  children,
  ...props
}: ComponentProps<'section'> & { tone?: 'plain' | 'raised' }) {
  return (
    <section className={cn('scroll-mt-16', tone === 'raised' && 'border-y bg-card', className)} {...props}>
      <div className="mx-auto max-w-6xl px-4 py-16 md:py-24">{children}</div>
    </section>
  )
}

/** Título da seção, com subtítulo opcional. Sem eyebrow: o título carrega a seção sozinho. */
export function SectionHeader({ id, title, children }: { id?: string; title: ReactNode; children?: ReactNode }) {
  return (
    <header className="max-w-2xl">
      <h2 id={id} className="font-display text-headline font-semibold">
        {title}
      </h2>
      {children && <p className="mt-4 text-lg text-muted-foreground">{children}</p>}
    </header>
  )
}
