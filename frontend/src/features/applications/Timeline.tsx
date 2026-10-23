import type { ApplicationStatus, Schemas } from '@/api/queries'
import { formatDateTime } from '@/lib/format'
import { statusLabels } from '@/lib/labels'

const originLabels: Record<string, string> = { MANUAL: 'você', EMAIL: 'e-mail', SISTEMA: 'sistema' }

function label(status: string | null | undefined) {
  return status ? (statusLabels[status as ApplicationStatus] ?? status) : null
}

/** Linha do tempo do funil, do evento mais antigo ao mais recente. */
export function Timeline({ events }: { events: Schemas['TimelineEvent'][] }) {
  if (events.length === 0) return <p className="text-sm text-muted-foreground">Nenhum evento ainda.</p>
  return (
    <ol className="relative space-y-4 border-l pl-5">
      {events.map((event) => (
        <li key={event.id} className="relative">
          <span className="absolute -left-[1.6rem] top-1.5 size-2.5 rounded-full border-2 border-background bg-primary" />
          <p className="text-sm">
            {event.fromStatus ? (
              <>
                {label(event.fromStatus)} → <strong>{label(event.toStatus)}</strong>
              </>
            ) : (
              <>
                Candidatura criada em <strong>{label(event.toStatus)}</strong>
              </>
            )}
          </p>
          <p className="text-xs text-muted-foreground">
            {formatDateTime(event.createdAt)} · por {originLabels[event.origin] ?? event.origin}
          </p>
          {event.note && <p className="mt-1 text-sm text-muted-foreground">“{event.note}”</p>}
        </li>
      ))}
    </ol>
  )
}
