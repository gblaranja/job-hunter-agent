import type { ApplicationStatus, ContactInteractionView, ResponseChannel, ResponseType, Schemas } from '@/api/queries'
import { formatDateTime } from '@/lib/format'
import {
  contactRoleLabels,
  interactionChannelLabels,
  interactionKindLabels,
  responseChannelLabels,
  responseTypeLabels,
  statusLabels,
} from '@/lib/labels'
import { cn } from '@/lib/utils'
import { ArrowDownLeft, ArrowUpRight, Bot, Mail, User } from 'lucide-react'

const origins: Record<string, { label: string; Icon: typeof Mail }> = {
  MANUAL: { label: 'você', Icon: User },
  EMAIL: { label: 'e-mail', Icon: Mail },
  SISTEMA: { label: 'sistema', Icon: Bot },
}

function label(status: string | null | undefined) {
  return status ? (statusLabels[status as ApplicationStatus] ?? status) : null
}

type Entry =
  | { type: 'event'; at: string; event: Schemas['TimelineEvent']; index: number }
  | { type: 'interaction'; at: string; interaction: ContactInteractionView }

/**
 * Linha do tempo do funil, do evento mais antigo ao mais recente, com os retornos, por onde chegaram e as conversas
 * com contatos registradas na candidatura.
 */
export function Timeline({
  events,
  interactions = [],
}: {
  events: Schemas['TimelineEvent'][]
  interactions?: ContactInteractionView[]
}) {
  if (events.length === 0 && interactions.length === 0)
    return <p className="text-sm text-muted-foreground">Nenhum evento ainda.</p>
  const entries: Entry[] = [
    ...events.map((event, index) => ({ type: 'event' as const, at: event.createdAt, event, index })),
    ...interactions.map((interaction) => ({ type: 'interaction' as const, at: interaction.occurredAt, interaction })),
  ].sort((a, b) => a.at.localeCompare(b.at))
  return (
    <ol className="relative space-y-4 border-l pl-5">
      {entries.map((entry) => {
        if (entry.type === 'interaction') return <InteractionItem key={`i-${entry.interaction.id}`} interaction={entry.interaction} />
        const { event, index } = entry
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

function InteractionItem({ interaction }: { interaction: ContactInteractionView }) {
  const sent = interaction.direction === 'ENVIADA'
  const Icon = sent ? ArrowUpRight : ArrowDownLeft
  return (
    <li className="relative">
      <span className="absolute -left-[1.6rem] top-1.5 size-2.5 rounded-full border-2 border-background bg-sky-500" />
      <p className="text-sm">
        <strong>{interactionKindLabels[interaction.kind]}</strong> {sent ? 'para' : 'de'} {interaction.contactName}
        <span className="text-muted-foreground"> ({contactRoleLabels[interaction.contactRole]})</span>
      </p>
      <p className="flex items-center gap-1 text-xs text-muted-foreground">
        <Icon className="size-3" aria-hidden />
        {formatDateTime(interaction.occurredAt)} · {interactionChannelLabels[interaction.channel]}
      </p>
      {interaction.summary && <p className="mt-1 text-sm text-muted-foreground">“{interaction.summary}”</p>}
    </li>
  )
}
