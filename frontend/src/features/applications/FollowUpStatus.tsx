import { toast } from 'sonner'
import { useRegisterFollowUp, type ApplicationStatus, type JobDetail } from '@/api/queries'
import { Button } from '@/components/ui/button'
import { formatDate } from '@/lib/format'
import { nextActionKindLabels, terminalStatuses } from '@/lib/labels'
import { cn } from '@/lib/utils'
import { CalendarClock, Send } from 'lucide-react'

type Summary = NonNullable<JobDetail['application']>

/**
 * Próxima ação da candidatura e os follow-ups enviados. O follow-up automático para sozinho quando a empresa
 * responde; aqui você registra o que mandou, e nada é enviado pelo agente.
 */
export function FollowUpStatus({ application }: { application: Summary }) {
  const register = useRegisterFollowUp()
  const kind = application.nextActionKind as keyof typeof nextActionKindLabels | null | undefined
  const overdue = application.nextActionAt != null && new Date(application.nextActionAt) < new Date()
  const closed = terminalStatuses.has(application.status as ApplicationStatus)

  function registerFollowUp() {
    register.mutate(application.id, {
      onSuccess: () => toast.success('Follow-up registrado.'),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <section aria-label="Follow-up" className="space-y-2 rounded-lg border p-3 text-sm">
      <div className="flex items-center gap-2">
        <CalendarClock className="size-4 text-muted-foreground" aria-hidden />
        {application.nextActionAt ? (
          <p className={cn(overdue && 'font-medium text-destructive')}>
            {kind ? nextActionKindLabels[kind] : 'Próxima ação'} em {formatDate(application.nextActionAt)}
            {application.nextActionAuto && (
              <span className="font-normal text-muted-foreground"> · agendado sozinho, para se a empresa responder</span>
            )}
          </p>
        ) : (
          <p className="text-muted-foreground">Nenhuma próxima ação agendada.</p>
        )}
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-muted-foreground">
          {application.followUpsSent === 0
            ? 'Nenhum follow-up enviado.'
            : application.followUpsSent === 1
              ? '1 follow-up enviado.'
              : `${application.followUpsSent} follow-ups enviados.`}
        </p>
        {!closed && (
          <Button size="sm" variant="outline" onClick={registerFollowUp} disabled={register.isPending}>
            <Send /> Registrar follow-up enviado
          </Button>
        )}
      </div>
    </section>
  )
}
