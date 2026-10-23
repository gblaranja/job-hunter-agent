import { Link } from 'react-router'
import { toast } from 'sonner'
import { useGenerateInsights, useLatestInsights, useLlmUsage, type InsightReport } from '@/api/queries'
import { BarList } from '@/components/charts/BarList'
import { StatTile } from '@/components/charts/StatTile'
import { PageHeader } from '@/components/PageHeader'
import { EmptyBlock, ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatDateTime, formatPercent } from '@/lib/format'
import { diagnosisLabels, feedbackReasonLabels, seniorityLabels } from '@/lib/labels'
import { ArrowRight, Loader2, Sparkles } from 'lucide-react'

const usd = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 4 })

function rate(part: number, total: number) {
  return total === 0 ? null : part / total
}

/** Relatório semanal: o que o mercado pede, onde o funil trava e o que mudar no currículo. */
export function InsightsPage() {
  const latest = useLatestInsights()
  const generate = useGenerateInsights()

  function run() {
    generate.mutate(undefined, {
      onSuccess: () => toast.success('Relatório gerado.'),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <>
      <PageHeader
        title="Insights"
        description="Gerado todo domingo às 21h com as últimas 4 semanas. Com a Claude API, as sugestões do currículo vêm do modelo maior."
        actions={
          <Button onClick={run} disabled={generate.isPending}>
            {generate.isPending ? <Loader2 className="animate-spin" /> : <Sparkles />}
            {generate.isPending ? 'Gerando (pode levar alguns minutos)…' : 'Gerar sugestões'}
          </Button>
        }
      />
      {latest.isLoading && <LoadingBlock lines={6} />}
      {latest.error && <ErrorBlock error={latest.error} />}
      {latest.isSuccess && latest.data === null && (
        <EmptyBlock title="Nenhum relatório ainda.">Clique em “Gerar sugestões” para montar o primeiro agora.</EmptyBlock>
      )}
      {latest.data && <Report report={latest.data} />}
    </>
  )
}

function Report({ report }: { report: InsightReport }) {
  const { funnel, recommendations } = report
  return (
    <div className="space-y-6">
      <p className="text-sm text-muted-foreground">
        Período de {formatDate(report.periodStart)} a {formatDate(report.periodEnd)} · gerado em {formatDateTime(report.createdAt)} ·{' '}
        {recommendations.source === 'IA' ? 'sugestões da IA' : 'sugestões por regras'}
      </p>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <StatTile label="Candidaturas" value={funnel.applied} hint="desde o início" />
        <StatTile
          label="Entrevistas"
          value={funnel.interview}
          hint={`${formatPercent(rate(funnel.interview, funnel.applied))} das candidaturas`}
        />
        <StatTile label="Score médio aplicado" value={funnel.avgScoreApplied ?? '—'} hint="aderência das vagas em que você aplicou" />
        <StatTile label="Acerto da IA nos e-mails" value={formatPercent(report.aiAccuracy)} hint="últimos 90 dias" />
        <StatTile label="Custo da IA no mês" value={report.llmCostUsd == null ? '—' : usd.format(report.llmCostUsd)} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{diagnosisLabels[recommendations.diagnosis] ?? recommendations.diagnosis}</CardTitle>
          <CardDescription>{recommendations.diagnosisExplanation}</CardDescription>
        </CardHeader>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Skills mais pedidas</CardTitle>
            <CardDescription>
              Vagas coletadas no período que pedem cada skill
              {report.topSkills.some((skill) => skill.covered === null) ? '; envie um currículo para ver as lacunas.' : '.'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {report.topSkills.length === 0 ? (
              <p className="text-sm text-muted-foreground">Sem vagas analisadas no período.</p>
            ) : (
              <BarList
                ariaLabel="Skills mais pedidas"
                items={report.topSkills.map((skill) => ({
                  label: skill.skill,
                  value: skill.jobs,
                  tone: skill.covered === false ? 'series-2' : 'series-1',
                  badge: skill.covered === false && (
                    <Badge variant="outline" className="px-1 py-0 text-[10px]">
                      lacuna
                    </Badge>
                  ),
                }))}
              />
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Funil</CardTitle>
            <CardDescription>Candidaturas por etapa mais avançada alcançada.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <BarList
              ariaLabel="Funil de candidaturas"
              max={funnel.applied}
              items={[
                { label: 'Aplicadas', value: funnel.applied },
                { label: 'Triagem', value: funnel.screening, display: `${funnel.screening} · ${formatPercent(rate(funnel.screening, funnel.applied))}` },
                { label: 'Entrevista', value: funnel.interview, display: `${funnel.interview} · ${formatPercent(rate(funnel.interview, funnel.applied))}` },
                { label: 'Oferta', value: funnel.offer, display: `${funnel.offer} · ${formatPercent(rate(funnel.offer, funnel.applied))}` },
              ]}
            />
            {Object.keys(funnel.scoreByRating).length > 0 && (
              <p className="text-sm text-muted-foreground">
                Score médio por avaliação:{' '}
                {Object.entries(funnel.scoreByRating)
                  .map(([rating, score]) => `${rating === 'GOSTEI' ? 'gostei' : rating === 'NEUTRO' ? 'neutro' : 'não gostei'} ${score}`)
                  .join(' · ')}
              </p>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Por currículo</CardTitle>
          </CardHeader>
          <CardContent>
            <ConversionTable
              rows={report.resumePerformance.map((row) => ({ key: String(row.resumeVersionId), group: row.label, applied: row.applied, interview: row.interview }))}
              groupLabel="Currículo"
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Por senioridade da vaga</CardTitle>
          </CardHeader>
          <CardContent>
            <ConversionTable
              rows={funnel.bySeniority.map((row) => ({ key: row.group, group: seniorityLabels[row.group] ?? row.group, applied: row.applied, interview: row.interview }))}
              groupLabel="Senioridade"
            />
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Lacunas para trabalhar</CardTitle>
          <CardDescription>As que mais aparecem nas vagas, com a sugestão de como tratar cada uma.</CardDescription>
        </CardHeader>
        <CardContent>
          {recommendations.gaps.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nenhuma lacuna frequente no período.</p>
          ) : (
            <ol className="space-y-4">
              {recommendations.gaps.map((gap) => (
                <li key={gap.skill}>
                  <div className="font-medium">{gap.skill}</div>
                  <div className="text-sm text-muted-foreground">{gap.evidence}</div>
                  <div className="mt-1 text-sm">{gap.suggestion}</div>
                </li>
              ))}
            </ol>
          )}
        </CardContent>
      </Card>

      {recommendations.bullets.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Bullets reescritos</CardTitle>
            <CardDescription>Trechos marcados com [X] pedem um número seu.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            {recommendations.bullets.map((bullet) => (
              <div key={bullet.original} className="space-y-1 text-sm">
                <p className="text-muted-foreground line-through decoration-muted-foreground/50">{bullet.original}</p>
                <p className="flex gap-2 font-medium">
                  <ArrowRight className="mt-0.5 size-4 shrink-0" aria-hidden />
                  {bullet.rewritten}
                </p>
                <p className="text-xs text-muted-foreground">{bullet.reason}</p>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Palavras-chave para o LinkedIn</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            {recommendations.linkedinKeywords.length === 0 ? (
              <p className="text-sm text-muted-foreground">Envie um currículo com skills extraídas para ver sugestões.</p>
            ) : (
              recommendations.linkedinKeywords.map((keyword) => (
                <Badge key={keyword} variant="secondary">
                  {keyword}
                </Badge>
              ))
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Filtros sugeridos</CardTitle>
            <CardDescription>A partir dos motivos de “não gostei” das últimas semanas.</CardDescription>
          </CardHeader>
          <CardContent>
            {report.suggestedFilters.length === 0 ? (
              <p className="text-sm text-muted-foreground">Nada a ajustar por enquanto.</p>
            ) : (
              <ul className="space-y-3">
                {report.suggestedFilters.map((filter) => (
                  <li key={`${filter.reason}-${filter.title}`} className="text-sm">
                    <div className="flex items-center gap-2 font-medium">
                      {filter.title}
                      <Badge variant="outline">{feedbackReasonLabels[filter.reason as keyof typeof feedbackReasonLabels] ?? filter.reason}</Badge>
                    </div>
                    <div className="text-muted-foreground">{filter.detail}</div>
                  </li>
                ))}
              </ul>
            )}
            <Button asChild variant="link" className="mt-2 px-0">
              <Link to={report.suggestedFilters.some((f) => f.reason === 'EMPRESA') ? '/companies' : '/settings'}>
                Ajustar perfis de busca
              </Link>
            </Button>
          </CardContent>
        </Card>
      </div>

      <UsageCard />
    </div>
  )
}

function ConversionTable({ rows, groupLabel }: { rows: { key: string; group: string; applied: number; interview: number }[]; groupLabel: string }) {
  if (rows.length === 0) return <p className="text-sm text-muted-foreground">Sem candidaturas ainda.</p>
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{groupLabel}</TableHead>
          <TableHead className="text-right">Aplicadas</TableHead>
          <TableHead className="text-right">Entrevistas</TableHead>
          <TableHead className="text-right">Conversão</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.key}>
            <TableCell>{row.group}</TableCell>
            <TableCell className="text-right tabular-nums">{row.applied}</TableCell>
            <TableCell className="text-right tabular-nums">{row.interview}</TableCell>
            <TableCell className="text-right tabular-nums">{formatPercent(rate(row.interview, row.applied))}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function UsageCard() {
  const usage = useLlmUsage()
  if (!usage.data || usage.data.length === 0) return null
  return (
    <Card>
      <CardHeader>
        <CardTitle>Uso da Claude API no mês</CardTitle>
        <CardDescription>Custo estimado pela tabela de preços; o limite de gasto fica no Console da Anthropic.</CardDescription>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Tarefa</TableHead>
              <TableHead>Modelo</TableHead>
              <TableHead className="text-right">Chamadas</TableHead>
              <TableHead className="text-right">Tokens (entrada / saída)</TableHead>
              <TableHead className="text-right">Do cache</TableHead>
              <TableHead className="text-right">Custo</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {usage.data.map((row) => (
              <TableRow key={`${row.task}-${row.model}`}>
                <TableCell>{row.task}</TableCell>
                <TableCell className="text-muted-foreground">{row.model}</TableCell>
                <TableCell className="text-right tabular-nums">{row.calls}</TableCell>
                <TableCell className="text-right tabular-nums">
                  {row.inputTokens.toLocaleString('pt-BR')} / {row.outputTokens.toLocaleString('pt-BR')}
                </TableCell>
                <TableCell className="text-right tabular-nums">{row.cacheReadTokens.toLocaleString('pt-BR')}</TableCell>
                <TableCell className="text-right tabular-nums">{usd.format(row.costUsd)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}
