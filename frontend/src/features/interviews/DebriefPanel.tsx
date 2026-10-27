import { useState } from 'react'
import { toast } from 'sonner'
import { useCreateDebrief, useDebriefs, useDeleteDebrief, type InterviewStage } from '@/api/interviews'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { MessageDrafts } from '@/features/network/MessageDrafts'
import { formatDate, formatDateTime } from '@/lib/format'
import { statusLabels } from '@/lib/labels'
import { Trash2 } from 'lucide-react'

const today = () => new Date().toLocaleDateString('sv-SE')

/**
 * O registro logo depois da entrevista: o que perguntaram, o que foi bem e o que faltou. Ao salvar, o follow-up fica
 * agendado e aparece o rascunho do agradecimento.
 */
export function DebriefPanel({ applicationId, jobId, stage }: { applicationId: number; jobId: number; stage: InterviewStage }) {
  const debriefs = useDebriefs(applicationId)
  const create = useCreateDebrief()
  const remove = useDeleteDebrief()
  const [form, setForm] = useState({
    stage: stage === 'TESTE_TECNICO' ? 'TESTE_TECNICO' : 'ENTREVISTA',
    interviewedOn: today(),
    questions: '',
    wentWell: '',
    toImprove: '',
    rating: '',
    nextSteps: '',
  })
  const [followUpAt, setFollowUpAt] = useState<string | null | undefined>(undefined)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    create.mutate(
      {
        applicationId,
        body: {
          stage: form.stage as InterviewStage,
          interviewedOn: form.interviewedOn,
          questions: form.questions.split('\n').map((q) => q.trim()).filter(Boolean),
          wentWell: form.wentWell || null,
          toImprove: form.toImprove || null,
          rating: form.rating ? Number(form.rating) : null,
          nextSteps: form.nextSteps || null,
        },
      },
      {
        onSuccess: (saved) => {
          setFollowUpAt(saved.followUpAt ?? null)
          setForm((current) => ({ ...current, questions: '', wentWell: '', toImprove: '', rating: '', nextSteps: '' }))
          toast.success('Registro salvo.')
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <div className="space-y-4">
      <form onSubmit={submit} className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div className="space-y-1">
          <Label htmlFor="debrief-stage">Etapa</Label>
          <Select value={form.stage} onValueChange={(value) => setForm({ ...form, stage: value })}>
            <SelectTrigger id="debrief-stage" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ENTREVISTA">{statusLabels.ENTREVISTA}</SelectItem>
              <SelectItem value="TESTE_TECNICO">{statusLabels.TESTE_TECNICO}</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="debrief-date">Data</Label>
          <Input
            id="debrief-date"
            type="date"
            required
            value={form.interviewedOn}
            onChange={(event) => setForm({ ...form, interviewedOn: event.target.value })}
          />
        </div>
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor="debrief-questions">O que perguntaram (uma por linha)</Label>
          <Textarea
            id="debrief-questions"
            rows={3}
            value={form.questions}
            onChange={(event) => setForm({ ...form, questions: event.target.value })}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="debrief-well">O que foi bem</Label>
          <Textarea id="debrief-well" rows={2} value={form.wentWell} onChange={(event) => setForm({ ...form, wentWell: event.target.value })} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="debrief-improve">O que faltou</Label>
          <Textarea
            id="debrief-improve"
            rows={2}
            value={form.toImprove}
            onChange={(event) => setForm({ ...form, toImprove: event.target.value })}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="debrief-rating">Nota (1 a 5)</Label>
          <Input
            id="debrief-rating"
            type="number"
            min={1}
            max={5}
            value={form.rating}
            onChange={(event) => setForm({ ...form, rating: event.target.value })}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="debrief-next">Próximos passos</Label>
          <Input id="debrief-next" value={form.nextSteps} onChange={(event) => setForm({ ...form, nextSteps: event.target.value })} />
        </div>
        <div className="sm:col-span-2">
          <Button type="submit" size="sm" disabled={create.isPending}>
            Salvar registro
          </Button>
        </div>
      </form>

      {followUpAt !== undefined && (
        <div className="space-y-2">
          <p className="text-sm">
            {followUpAt ? `Follow-up agendado para ${formatDateTime(followUpAt)}.` : 'A candidatura está encerrada; nenhum follow-up foi agendado.'}{' '}
            Mande um agradecimento hoje:
          </p>
          <MessageDrafts jobId={jobId} applicationId={applicationId} initialKind="AGRADECIMENTO" />
        </div>
      )}

      {debriefs.isLoading && <LoadingBlock lines={2} />}
      {debriefs.error && <ErrorBlock error={debriefs.error} />}
      {debriefs.data?.map((debrief) => (
        <div key={debrief.id} className="space-y-1 rounded-md border p-3 text-sm">
          <div className="flex items-center gap-2">
            <span className="font-medium">
              {statusLabels[debrief.stage]} · {formatDate(debrief.interviewedOn)}
            </span>
            {debrief.rating != null && <span className="text-muted-foreground">nota {debrief.rating}</span>}
            <Button
              size="icon"
              variant="ghost"
              className="ml-auto"
              aria-label="Excluir registro"
              onClick={() => remove.mutate({ id: debrief.id, applicationId })}
            >
              <Trash2 />
            </Button>
          </div>
          {debrief.questions.length > 0 && (
            <ul className="list-disc pl-5">
              {debrief.questions.map((question) => (
                <li key={question}>{question}</li>
              ))}
            </ul>
          )}
          {debrief.toImprove && <p className="text-muted-foreground">Faltou: {debrief.toImprove}</p>}
          {debrief.nextSteps && <p className="text-muted-foreground">Próximos passos: {debrief.nextSteps}</p>}
        </div>
      ))}
    </div>
  )
}
