import { toast } from 'sonner'
import { useReanalyze, type JobAnalysis } from '@/api/queries'
import { ScoreBadge } from '@/components/ScoreBadge'
import { SkillChips } from '@/components/SkillChips'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatDateTime } from '@/lib/format'
import { knockoutStatusLabels, knockoutTypeLabels, seniorityLabels } from '@/lib/labels'
import { formatSalary } from '@/lib/format'
import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'
import { RefreshCw, Sparkles } from 'lucide-react'

const knockoutStyles: Record<string, string> = {
  ATENDE: 'text-success',
  NAO_ATENDE: 'text-destructive',
  SEM_INFORMACAO: 'text-muted-foreground',
}

/** Eliminatórios da vaga com o resultado da comparação com os seus dados. */
function KnockoutList({ knockouts }: { knockouts: JobAnalysis['knockouts'] }) {
  const failed = knockouts.filter((knockout) => knockout.status === 'NAO_ATENDE').length
  return (
    <section className="space-y-2">
      <h3 className="flex flex-wrap items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
        Eliminatórios
        {failed > 0 && (
          <Badge variant="destructive" className="normal-case">
            {failed === 1 ? '1 eliminatório não atendido' : `${failed} eliminatórios não atendidos`}
          </Badge>
        )}
      </h3>
      <ul className="space-y-1.5 text-sm">
        {knockouts.map((knockout, index) => (
          <li key={`${knockout.type}-${index}`} className="flex flex-wrap gap-x-2">
            <span className={cn('font-medium', knockoutStyles[knockout.status])}>
              {knockoutStatusLabels[knockout.status] ?? knockout.status}
            </span>
            <span>{knockoutTypeLabels[knockout.type] ?? knockout.type}:</span>
            <span className="text-muted-foreground">“{knockout.text}”</span>
            {knockout.reason && <span className="w-full text-xs text-muted-foreground">{knockout.reason}</span>}
          </li>
        ))}
      </ul>
    </section>
  )
}

const statusText: Record<string, string> = {
  DETERMINISTICO: 'Análise por regras (sem IA)',
  PENDENTE: 'Ajuste da IA pendente',
  COMPLETO: 'Analisada com IA',
  FALHOU: 'A IA falhou; nova tentativa agendada',
}

/** Requisitos extraídos, score de aderência com o detalhamento e a justificativa da IA. */
export function AnalysisCard({ jobId, analysis }: { jobId: number; analysis?: JobAnalysis | null }) {
  const reanalyze = useReanalyze()

  function run() {
    reanalyze.mutate(jobId, {
      onSuccess: () => toast.success('Vaga reanalisada.'),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Sparkles className="size-4" aria-hidden /> Análise
        </CardTitle>
        <CardDescription>
          {analysis ? `${statusText[analysis.status] ?? analysis.status} · ${formatDateTime(analysis.analyzedAt)}` : 'Ainda não analisada.'}
        </CardDescription>
        <CardAction>
          <Button variant="outline" size="sm" onClick={run} disabled={reanalyze.isPending}>
            <RefreshCw /> Reanalisar
          </Button>
        </CardAction>
      </CardHeader>
      {analysis && (
        <CardContent className="space-y-4">
          <div className="flex items-start gap-4">
            <ScoreBadge score={analysis.matchScore} className="h-14 w-16 text-2xl" />
            <div className="space-y-1 text-sm">
              {analysis.matchScore == null ? (
                <p className="text-muted-foreground">Envie um currículo para calcular a aderência.</p>
              ) : (
                <p>
                  Regras: <strong>{analysis.deterministicScore}</strong>
                  {analysis.llmAdjustment != null && (
                    <>
                      {' '}· ajuste da IA: <strong>{analysis.llmAdjustment > 0 ? `+${analysis.llmAdjustment}` : analysis.llmAdjustment}</strong>
                    </>
                  )}
                </p>
              )}
              {analysis.justification && <p className="text-muted-foreground">{analysis.justification}</p>}
              {analysis.seniority && (
                <p className="text-muted-foreground">Senioridade: {seniorityLabels[analysis.seniority] ?? analysis.seniority}</p>
              )}
            </div>
          </div>
          {analysis.summary && <p className="text-sm">{analysis.summary}</p>}
          {analysis.salary && (
            <p className="text-sm">
              <span className="text-muted-foreground">Salário informado: </span>
              {formatSalary(analysis.salary)}
            </p>
          )}
          {analysis.knockouts.length > 0 && <KnockoutList knockouts={analysis.knockouts} />}
          {analysis.evidence.length > 0 && (
            <section className="space-y-2">
              <h3 className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Evidências no currículo</h3>
              <ul className="space-y-1.5 text-sm">
                {analysis.evidence.map((item, index) => (
                  <li key={`${item.requirement}-${index}`}>
                    <span className="font-medium">{item.requirement}:</span>{' '}
                    <span className="text-muted-foreground">“{item.quote}”</span>
                  </li>
                ))}
              </ul>
            </section>
          )}
          <section className="space-y-2">
            <h3 className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Obrigatórias</h3>
            {analysis.requiredSkills.length ? (
              <SkillChips
                skills={analysis.requiredSkills}
                owned={analysis.matchedSkills}
                gaps={analysis.gaps}
                partial={analysis.partialSkills}
              />
            ) : (
              <p className="text-sm text-muted-foreground">Nenhuma skill reconhecida na descrição.</p>
            )}
          </section>
          {analysis.niceToHave.length > 0 && (
            <section className="space-y-2">
              <h3 className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Diferenciais</h3>
              <SkillChips skills={analysis.niceToHave} owned={analysis.matchedSkills} partial={analysis.partialSkills} />
            </section>
          )}
          {analysis.status === 'FALHOU' && analysis.lastError && (
            <Alert>
              <AlertDescription>{analysis.lastError}</AlertDescription>
            </Alert>
          )}
        </CardContent>
      )}
    </Card>
  )
}
