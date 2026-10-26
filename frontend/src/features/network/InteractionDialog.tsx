import { useState, type FormEvent, type ReactNode } from 'react'
import { toast } from 'sonner'
import {
  useRecordInteraction,
  type ContactView,
  type InteractionChannel,
  type InteractionDirection,
  type InteractionKind,
} from '@/api/queries'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { interactionChannelLabels, interactionDirectionLabels, interactionKindLabels } from '@/lib/labels'

const channels = Object.keys(interactionChannelLabels) as InteractionChannel[]
const directions = Object.keys(interactionDirectionLabels) as InteractionDirection[]
const kinds = Object.keys(interactionKindLabels) as InteractionKind[]

/** Registra uma conversa com o contato; ligada à candidatura, ela aparece na linha do tempo. */
export function InteractionDialog({
  contact,
  applicationId,
  trigger,
}: {
  contact: ContactView
  applicationId?: number
  trigger: ReactNode
}) {
  const record = useRecordInteraction()
  const [open, setOpen] = useState(false)
  const [channel, setChannel] = useState<InteractionChannel>('LINKEDIN')
  const [direction, setDirection] = useState<InteractionDirection>('RECEBIDA')
  const [kind, setKind] = useState<InteractionKind>('CONVERSA')
  const [summary, setSummary] = useState('')

  function submit(event: FormEvent) {
    event.preventDefault()
    record.mutate(
      { contactId: contact.id, channel, direction, kind, summary: summary || null, applicationId: applicationId ?? null },
      {
        onSuccess: () => {
          toast.success('Conversa registrada.')
          setOpen(false)
          setSummary('')
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Conversa com {contact.name}</DialogTitle>
            <DialogDescription>
              {applicationId ? 'Fica no histórico do contato e na linha do tempo da candidatura.' : 'Fica no histórico do contato.'}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 sm:grid-cols-3">
            <LabeledSelect id="interaction-direction" label="Quem" value={direction} options={directions}
              labels={interactionDirectionLabels} onChange={setDirection} />
            <LabeledSelect id="interaction-channel" label="Canal" value={channel} options={channels}
              labels={interactionChannelLabels} onChange={setChannel} />
            <LabeledSelect id="interaction-kind" label="Assunto" value={kind} options={kinds}
              labels={interactionKindLabels} onChange={setKind} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="interaction-summary">Resumo</Label>
            <Textarea id="interaction-summary" value={summary} onChange={(event) => setSummary(event.target.value)} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={record.isPending}>
              Registrar
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function LabeledSelect<T extends string>({
  id,
  label,
  value,
  options,
  labels,
  onChange,
}: {
  id: string
  label: string
  value: T
  options: T[]
  labels: Record<T, string>
  onChange: (value: T) => void
}) {
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Select value={value} onValueChange={(next) => onChange(next as T)}>
        <SelectTrigger id={id} className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {options.map((option) => (
            <SelectItem key={option} value={option}>
              {labels[option]}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
