import { BRAND } from '@/lib/brand'
import { cn } from '@/lib/utils'


/** Marca: um alvo (o agente mira as vagas certas) com o ponto central em laranja, a cor de destaque. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={cn('size-8', className)} aria-hidden>
      <rect width="32" height="32" rx="8" fill="var(--brand-ink)" />
      <circle cx="16" cy="16" r="9" fill="none" stroke="var(--brand-paper)" strokeWidth="2.25" />
      <circle cx="16" cy="16" r="4.5" fill="none" stroke="var(--brand-paper)" strokeWidth="2.25" opacity="0.7" />
      <circle cx="16" cy="16" r="1.9" fill="var(--brand-accent)" />
      <path d="M16 3.5v4M16 24.5v4M3.5 16h4M24.5 16h4" stroke="var(--brand-paper)" strokeWidth="2.25" strokeLinecap="round" />
    </svg>
  )
}

export function Logo({ className, compact = false }: { className?: string; compact?: boolean }) {
  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <LogoMark className="size-7" />
      <span className="font-semibold tracking-tight">
        {compact ? BRAND.shortName : (
          <>
            Job Hunter <span className="text-brand-accent-text">Agent</span>
          </>
        )}
      </span>
    </span>
  )
}
