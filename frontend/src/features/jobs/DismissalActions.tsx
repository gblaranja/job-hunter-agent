import { toast } from 'sonner'
import { useDismissJob, useRestoreJob, type DismissReason, type JobDetail } from '@/api/queries'
import { Button } from '@/components/ui/button'
import { formatDate } from '@/lib/format'
import { dismissReasonLabels } from '@/lib/labels'
import { Undo2, UserX, X } from 'lucide-react'

type Props = { jobId: number; dismissal?: JobDetail['dismissal'] }

/** Descarte da vaga sem candidatura: tira da caixa com o motivo, ou devolve para ela. */
export function DismissalActions({ jobId, dismissal }: Props) {
  const dismiss = useDismissJob()
  const restore = useRestoreJob()
  const busy = dismiss.isPending || restore.isPending

  function decline(reason: DismissReason) {
    dismiss.mutate(
      { jobId, reason },
      {
        onSuccess: () => toast.success('Vaga retirada da caixa.'),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  if (dismissal) {
    return (
      <div className="space-y-2 rounded-md border border-dashed p-3 text-sm">
        <p>
          <span className="font-medium">{dismissReasonLabels[dismissal.reason]}</span>
          <span className="text-muted-foreground"> · fora da caixa desde {formatDate(dismissal.dismissedAt)}</span>
        </p>
        <Button
          variant="outline"
          size="sm"
          className="w-full"
          disabled={busy}
          onClick={() =>
            restore.mutate(jobId, {
              onSuccess: () => toast.success('A vaga voltou para a caixa.'),
              onError: (error) => toast.error(error.message),
            })
          }
        >
          <Undo2 /> Voltar para a caixa
        </Button>
      </div>
    )
  }

  return (
    <>
      <Button variant="ghost" onClick={() => decline('SEM_INTERESSE')} disabled={busy}>
        <X /> {dismissReasonLabels.SEM_INTERESSE}
      </Button>
      <Button variant="ghost" onClick={() => decline('SEM_PERFIL_TECNICO')} disabled={busy}>
        <UserX /> {dismissReasonLabels.SEM_PERFIL_TECNICO}
      </Button>
    </>
  )
}
