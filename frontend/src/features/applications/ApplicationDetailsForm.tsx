import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { useUpdateApplicationDetails } from '@/api/queries'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { fromDateInput, toDateInput } from '@/lib/format'

type Props = { applicationId: number; notes?: string | null; nextActionAt?: string | null }

/** Notas livres e a data do próximo follow-up. */
export function ApplicationDetailsForm({ applicationId, notes, nextActionAt }: Props) {
  const update = useUpdateApplicationDetails()
  const [draftNotes, setDraftNotes] = useState(notes ?? '')
  const [nextAction, setNextAction] = useState(toDateInput(nextActionAt))

  function submit(event: FormEvent) {
    event.preventDefault()
    update.mutate(
      { applicationId, notes: draftNotes || null, nextActionAt: fromDateInput(nextAction) },
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
        <Button type="submit" variant="secondary" disabled={update.isPending}>
          Salvar anotações
        </Button>
      </div>
    </form>
  )
}
