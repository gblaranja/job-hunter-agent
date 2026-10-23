import { toast } from 'sonner'
import { useChangeJobScope, type JobScope } from '@/api/queries'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { scopeLabels } from '@/lib/labels'
import { ArrowLeftRight } from 'lucide-react'

/** Mostra se a vaga é nacional ou internacional e corrige a classificação feita pelo idioma da descrição. */
export function ScopeSwitch({ jobId, scope }: { jobId: number; scope: JobScope }) {
  const change = useChangeJobScope()
  const next: JobScope = scope === 'NACIONAL' ? 'INTERNACIONAL' : 'NACIONAL'

  function move() {
    change.mutate(
      { jobId, scope: next },
      {
        onSuccess: () =>
          toast.success(`Vaga movida para as ${next === 'NACIONAL' ? 'nacionais' : 'internacionais'} na caixa de vagas.`),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <span className="inline-flex items-center gap-1">
      <Badge variant={scope === 'INTERNACIONAL' ? 'outline' : 'secondary'}>{scopeLabels[scope]}</Badge>
      <Button variant="ghost" size="sm" onClick={move} disabled={change.isPending}>
        <ArrowLeftRight /> Marcar como {scopeLabels[next].toLowerCase()}
      </Button>
    </span>
  )
}
