import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { useUpdateApplicationDetails, type NextActionKind } from '@/api/queries'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { fromDateInput, toDateInput } from '@/lib/format'
import { nextActionKindLabels } from '@/lib/labels'

type Props = {
  applicationId: number
  notes?: string | null
  nextActionAt?: string | null
  nextActionKind?: string | null
}

const kinds = Object.keys(nextActionKindLabels) as NextActionKind[]

/** Notas livres, a data e o tipo da próxima ação (follow-up, responder, entrevista…). */
export function ApplicationDetailsForm({ applicationId, notes, nextActionAt, nextActionKind }: Props) {
  const update = useUpdateApplicationDetails()
  const [draftNotes, setDraftNotes] = useState(notes ?? '')
  const [nextAction, setNextAction] = useState(toDateInput(nextActionAt))
  const [kind, setKind] = useState<NextActionKind>((nextActionKind as NextActionKind | null) ?? 'FOLLOW_UP')

  function submit(event: FormEvent) {
    event.preventDefault()
    const at = fromDateInput(nextAction)
    update.mutate(
      { applicationId, notes: draftNotes || null, nextActionAt: at, nextActionKind: at ? kind : null },
      {
        onSuccess: () => toast.success('Anotações salvas.'),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <div className="space-y-2">
        <Label htmlFor={`notes-${applicationId}`}>Notas</Label>
        <Textarea
          id={`notes-${applicationId}`}
          value={draftNotes}
          onChange={(event) => setDraftNotes(event.target.value)}
          placeholder="Contato do recrutador, faixa salarial combinada, impressões da entrevista…"
        />
      </div>
      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-2">
          <Label htmlFor={`next-${applicationId}`}>Próxima ação</Label>
          <Input
            id={`next-${applicationId}`}
            type="date"
            className="w-44"
            value={nextAction}
            onChange={(event) => setNextAction(event.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor={`next-kind-${applicationId}`}>Tipo</Label>
          <Select value={kind} onValueChange={(value) => setKind(value as NextActionKind)}>
            <SelectTrigger id={`next-kind-${applicationId}`} className="w-36">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {kinds.map((option) => (
                <SelectItem key={option} value={option}>
                  {nextActionKindLabels[option]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Button type="submit" variant="secondary" disabled={update.isPending}>
          Salvar anotações
        </Button>
      </div>
    </form>
  )
}
