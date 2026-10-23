import type { ApplicationStatus, ResponseChannel, ResponseType, Schemas } from '@/api/queries'
import { formatDateTime } from '@/lib/format'
import { responseChannelLabels, responseTypeLabels, statusLabels } from '@/lib/labels'
import { cn } from '@/lib/utils'
import { Bot, Mail, User } from 'lucide-react'

const origins: Record<string, { label: string; Icon: typeof Mail }> = {
  MANUAL: { label: 'você', Icon: User },
  EMAIL: { label: 'e-mail', Icon: Mail },
  SISTEMA: { label: 'sistema', Icon: Bot },
}

function label(status: string | null | undefined) {
  return status ? (statusLabels[status as ApplicationStatus] ?? status) : null
}

/** Linha do tempo do funil, do evento mais antigo ao mais recente, com os retornos e por onde chegaram. */
export function Timeline({ events }: { events: Schemas['TimelineEvent'][] }) {
  if (events.length === 0) return <p className="text-sm text-muted-foreground">Nenhum evento ainda.</p>
  return (
    <ol className="relative space-y-4 border-l pl-5">
      {events.map((event, index) => {
        const origin = origins[event.origin] ?? origins.SISTEMA
        const response = event.responseType ? responseTypeLabels[event.responseType as ResponseType] : null
        return (
          <li key={event.id} className="relative">
            <span
              className={cn(
                'absolute -left-[1.6rem] top-1.5 size-2.5 rounded-full border-2 border-background',
                response ? 'bg-amber-500' : 'bg-primary',
              )}
            />
            <p className="text-sm">
              {response && <strong>{response}</strong>}
              {response && event.toStatus && ' · '}
              {event.toStatus &&
                (event.fromStatus ? (
                  <>
                    {label(event.fromStatus)} → <strong>{label(event.toStatus)}</strong>
                  </>
                ) : index === 0 ? (
                  <>
                    Candidatura criada em <strong>{label(event.toStatus)}</strong>
                  </>
                ) : (
                  <strong>{label(event.toStatus)}</strong>
                ))}
            </p>
            <p className="flex items-center gap-1 text-xs text-muted-foreground">
              <origin.Icon className="size-3" aria-hidden />
              {formatDateTime(event.createdAt)} · por {origin.label}
              {event.responseChannel && event.responseChannel !== 'EMAIL' &&
                ` · ${responseChannelLabels[event.responseChannel as ResponseChannel]}`}
            </p>
            {event.note && <p className="mt-1 text-sm text-muted-foreground">“{event.note}”</p>}
          </li>
        )
      })}
    </ol>
  )
}
