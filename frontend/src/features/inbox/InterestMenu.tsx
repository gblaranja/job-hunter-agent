import { toast } from 'sonner'
import { useDismissJob, useRestoreJob, useStartApplication, type DismissReason } from '@/api/queries'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { dismissReasonLabels } from '@/lib/labels'
import { ChevronDown, Star, UserX, X } from 'lucide-react'

/** Decisão sobre a vaga da caixa: levar para o kanban ou descartar dizendo por quê. */
export function InterestMenu({ jobId }: { jobId: number }) {
  const start = useStartApplication()
  const dismiss = useDismissJob()
  const restore = useRestoreJob()
  const busy = start.isPending || dismiss.isPending

  function interest() {
    start.mutate(
      { jobId, body: { status: 'INTERESSE' } },
      {
        onSuccess: () => toast.success('Vaga movida para Interesse no kanban.'),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  function decline(reason: DismissReason) {
    dismiss.mutate(
      { jobId, reason },
      {
        onSuccess: () =>
          toast(`${dismissReasonLabels[reason]}: vaga retirada da caixa.`, {
            id: `dismissal-${jobId}`,
            action: {
              label: 'Desfazer',
              onClick: () => restore.mutate(jobId, { onError: (error) => toast.error(error.message) }),
            },
          }),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button size="sm" disabled={busy}>
          Tenho interesse? <ChevronDown />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuItem onSelect={interest}>
          <Star /> Tenho interesse
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuLabel className="text-xs font-normal text-muted-foreground">Retirar da caixa</DropdownMenuLabel>
        <DropdownMenuItem onSelect={() => decline('SEM_INTERESSE')}>
          <X /> {dismissReasonLabels.SEM_INTERESSE}
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => decline('SEM_PERFIL_TECNICO')}>
          <UserX /> {dismissReasonLabels.SEM_PERFIL_TECNICO}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
