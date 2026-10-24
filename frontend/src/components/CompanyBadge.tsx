import { Award } from 'lucide-react'
import { cn } from '@/lib/utils'

type GptwLevel = 'RANKING_TI' | 'RANKING' | 'CERTIFICADA' | 'EXPIRADA' | 'NAO_ENCONTRADA'

const short: Partial<Record<GptwLevel, string>> = {
  RANKING_TI: 'GPTW TI',
  RANKING: 'GPTW ranking',
  CERTIFICADA: 'GPTW',
}

const full: Partial<Record<GptwLevel, string>> = {
  RANKING_TI: 'Premiada no ranking GPTW TI',
  RANKING: 'Premiada num ranking GPTW',
  CERTIFICADA: 'Certificada GPTW',
}

/**
 * Selo GPTW vigente ao lado do nome da empresa. Só aparece para os níveis válidos (ranking TI, outro ranking ou
 * certificação), já verificados ou confirmados por você.
 */
export function CompanyBadge({
  level,
  year,
  className,
}: {
  level?: string | null
  year?: number | null
  className?: string
}) {
  const label = level ? short[level as GptwLevel] : undefined
  if (!label) return null
  const title = `${full[level as GptwLevel]}${year ? ` em ${year}` : ''}`
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center gap-1 rounded-md border px-1.5 py-0.5 text-xs font-medium',
        level === 'RANKING_TI'
          ? 'border-success/40 bg-success/10 text-success'
          : 'border-border bg-muted text-muted-foreground',
        className,
      )}
      title={title}
      aria-label={title}
    >
      <Award className="size-3" aria-hidden />
      {label}
      {year ? <span className="tabular-nums">{year}</span> : null}
    </span>
  )
}
