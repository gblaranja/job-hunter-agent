import { useApplyCheck } from '@/api/queries'
import { AlertTriangle, Loader2 } from 'lucide-react'

/** Avisos antes de aplicar; não bloqueiam, só pedem para conferir. */
export function ApplyCheckWarnings({ jobId }: { jobId: number }) {
  const check = useApplyCheck(jobId)

  if (check.isLoading) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" aria-hidden /> Conferindo a vaga antes de aplicar…
      </p>
    )
  }
  if (!check.data || check.data.warnings.length === 0) return null
  return (
    <div role="alert" className="space-y-1 rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-sm">
      <p className="flex items-center gap-2 font-medium">
        <AlertTriangle className="size-4" aria-hidden /> Antes de aplicar, confira
      </p>
      <ul className="list-disc space-y-1 pl-5">
        {check.data.warnings.map((warning) => (
          <li key={warning.code + warning.message}>{warning.message}</li>
        ))}
      </ul>
    </div>
  )
}
