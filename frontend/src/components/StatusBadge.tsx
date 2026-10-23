import type { ApplicationStatus, LinkStatus } from '@/api/queries'
import { Badge } from '@/components/ui/badge'
import { linkStatusLabels, statusAccent, statusLabels } from '@/lib/labels'
import { cn } from '@/lib/utils'
import { CircleCheck, CircleHelp, CircleX } from 'lucide-react'

export function StatusBadge({ status }: { status: ApplicationStatus }) {
  return (
    <Badge variant="outline" className="gap-1.5 font-medium">
      <span className={cn('size-2 rounded-full', statusAccent[status])} aria-hidden />
      {statusLabels[status]}
    </Badge>
  )
}

const linkStyles: Record<NonNullable<LinkStatus>, { className: string; Icon: typeof CircleCheck }> = {
  ATIVO: { className: 'border-success/40 text-success', Icon: CircleCheck },
  REMOVIDO: { className: 'border-destructive/40 text-destructive', Icon: CircleX },
  NAO_VERIFICADO: { className: 'text-muted-foreground', Icon: CircleHelp },
}

export function LinkStatusBadge({ status }: { status: LinkStatus | null | undefined }) {
  const { className, Icon } = linkStyles[status ?? 'NAO_VERIFICADO']
  return (
    <Badge variant="outline" className={cn('gap-1', className)}>
      <Icon className="size-3" aria-hidden />
      Link {linkStatusLabels[status ?? 'NAO_VERIFICADO'].toLowerCase()}
    </Badge>
  )
}
