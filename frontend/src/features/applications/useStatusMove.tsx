import { useState } from 'react'
import { toast } from 'sonner'
import { useChangeStatus, type ApplicationStatus } from '@/api/queries'
import { ResumePickerDialog } from '@/components/ResumePickerDialog'
import { statusLabels } from '@/lib/labels'
import { planMove, type MovePlan } from './planMove'

type Movable = { applicationId: number; status: ApplicationStatus; resumeVersionId?: number | null; jobId?: number }
type Pending = { item: Movable; target: ApplicationStatus; plan: Extract<MovePlan, { kind: 'confirm' }> }

/**
 * Mudança de etapa com as confirmações necessárias. Usado pelo kanban (ao soltar um card) e pelo detalhe
 * da vaga; devolve a função que inicia a mudança e o diálogo a renderizar.
 */
export function useStatusMove() {
  const changeStatus = useChangeStatus()
  const [pending, setPending] = useState<Pending | null>(null)

  function send(item: Movable, target: ApplicationStatus, extra: { resumeVersionId?: number; note?: string } = {}) {
    changeStatus.mutate(
      { applicationId: item.applicationId, status: target, ...extra },
      {
        onSuccess: () => toast.success(`Candidatura movida para ${statusLabels[target]}.`),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  function requestMove(item: Movable, target: ApplicationStatus) {
    const plan = planMove(item, target)
    if (plan.kind === 'move') send(item, target)
    else if (plan.kind === 'confirm') setPending({ item, target, plan })
  }

  const dialog = pending && (
    <ResumePickerDialog
      key={`${pending.item.applicationId}-${pending.target}`}
      open
      title={`Mover para ${statusLabels[pending.target]}`}
      description={
        pending.plan.requireResume
          ? 'Registre qual versão do currículo você enviou. Assim dá para medir a conversão de cada versão.'
          : 'Essa candidatura estava encerrada.'
      }
      requireResume={pending.plan.requireResume}
      requireNote={pending.plan.requireNote}
      pending={changeStatus.isPending}
      applyCheckJobId={pending.target === 'APLICADA' ? pending.item.jobId : undefined}
      onCancel={() => setPending(null)}
      onConfirm={(choice) => {
        send(pending.item, pending.target, choice)
        setPending(null)
      }}
    />
  )

  return { requestMove, dialog }
}
