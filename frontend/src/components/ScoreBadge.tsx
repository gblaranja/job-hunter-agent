import { cn } from '@/lib/utils'

/** Aderência ao currículo padrão: verde a partir de 75, âmbar de 50 a 74, neutro abaixo. */
export function ScoreBadge({ score, className }: { score?: number | null; className?: string }) {
  if (score == null) {
    return (
      <span className={cn('inline-flex h-9 w-12 items-center justify-center rounded-md border text-sm text-muted-foreground', className)} title="Sem análise ou sem currículo padrão">
        —
      </span>
    )
  }
  const tone =
    score >= 75
      ? 'bg-success/15 text-success border-success/30'
      : score >= 50
        ? 'bg-warning/15 text-amber-700 border-warning/40 dark:text-warning'
        : 'bg-muted text-muted-foreground'
  return (
    <span
      className={cn('inline-flex h-9 w-12 items-center justify-center rounded-md border text-base font-semibold tabular-nums', tone, className)}
      title={`Aderência de ${score}%`}
      aria-label={`Aderência ${score}`}
    >
      {score}
    </span>
  )
}
