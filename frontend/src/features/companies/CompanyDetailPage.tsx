import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import {
  useCompany,
  useCompanyMetrics,
  useCompanyOverview,
  useSaveHeadcount,
  type ApplicationStatus,
} from '@/api/queries'
import { BarList } from '@/components/charts/BarList'
import { CompanyBadge } from '@/components/CompanyBadge'
import { StatTile } from '@/components/charts/StatTile'
import { PageHeader } from '@/components/PageHeader'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { ScoreBadge } from '@/components/ScoreBadge'
import { StatusBadge } from '@/components/StatusBadge'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate } from '@/lib/format'
import { atsLabels, companyKindLabels, seniorityLabels, watchStatusLabels, workModelLabels } from '@/lib/labels'
import { ArrowLeft, Repeat2 } from 'lucide-react'
import { CompanyAtsForm } from './CompanyAtsForm'
import { CompanyContactsCard } from '@/features/network/CompanyContactsCard'
import { CompanyNicheCard } from './CompanyNicheCard'
import { GptwHistoryCard } from './GptwHistoryCard'
import { WeeklyJobsChart } from './WeeklyJobsChart'

/** Volume e frequência de vagas da empresa, as distribuições recentes e as suas candidaturas nela. */
export function CompanyDetailPage() {
  const id = Number(useParams().companyId)
  const company = useCompany(id)
  const overview = useCompanyOverview(id)
  const metrics = useCompanyMetrics(id)

  if (company.isLoading) return <LoadingBlock lines={6} />
  if (company.error) return <ErrorBlock error={company.error} />
  if (!company.data) return null
  const data = company.data
  const lastHeadcount = [...(metrics.data ?? [])].reverse().find((week) => week.headcount != null)?.headcount

  return (
    <>
      <Button asChild variant="ghost" size="sm" className="mb-2 -ml-2">
        <Link to="/companies">
          <ArrowLeft /> Empresas
        </Link>
      </Button>
      <PageHeader
        title={data.name}
        description={[
          companyKindLabels[data.kind],
          watchStatusLabels[data.watchStatus],
          data.atsType ? `${atsLabels[data.atsType] ?? data.atsType}${data.atsSlug ? ` (${data.atsSlug})` : ''}` : null,
          data.contractTypes.length ? data.contractTypes.join(' / ') : null,
          data.authorizationType,
        ]
          .filter(Boolean)
          .join(' · ')}
        actions={<CompanyBadge level={data.gptwLevel} year={data.gptwYear} className="px-2 py-1 text-sm" />}
      />
      <div className="space-y-6">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <StatTile label="Vagas ativas" value={data.jobsActive} hint={`${data.jobsTotal} no total`} />
          <StatTile label="Últimos 90 dias" value={data.jobsLast90Days} />
          <StatTile label="Reposts" value={data.reposts} hint="vagas republicadas" />
          <StatTile
            label="Tempo médio no ar"
            value={data.avgDaysOpen == null ? '—' : `${data.avgDaysOpen.toLocaleString('pt-BR')} d`}
            hint="das vagas já fechadas"
          />
          <StatTile label="Funcionários" value={lastHeadcount?.toLocaleString('pt-BR') ?? '—'} hint="anotado à mão" />
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Vagas por semana</CardTitle>
            <CardDescription>Últimas 26 semanas. Recalculado todo domingo às 22h.</CardDescription>
          </CardHeader>
          <CardContent>
            {metrics.isLoading && <LoadingBlock />}
            {metrics.data && metrics.data.length === 0 && (
              <p className="text-sm text-muted-foreground">Ainda sem série semanal. Use “Recalcular métricas” na lista de empresas.</p>
            )}
            {metrics.data && metrics.data.length > 0 && <WeeklyJobsChart weeks={metrics.data} />}
          </CardContent>
        </Card>

        {overview.data && (
          <div className="grid gap-6 lg:grid-cols-3">
            <Card>
              <CardHeader>
                <CardTitle>Senioridade</CardTitle>
                <CardDescription>Vagas dos últimos 90 dias.</CardDescription>
              </CardHeader>
              <CardContent>
                <Distribution counts={overview.data.bySeniority} labels={seniorityLabels} />
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>Modelo de trabalho</CardTitle>
                <CardDescription>Vagas dos últimos 90 dias.</CardDescription>
              </CardHeader>
              <CardContent>
                <Distribution counts={overview.data.byWorkModel} labels={workModelLabels} />
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>Skills pedidas</CardTitle>
                <CardDescription>Obrigatórias, últimos 90 dias.</CardDescription>
              </CardHeader>
              <CardContent className="flex flex-wrap gap-2">
                {overview.data.topSkills.length === 0 && <p className="text-sm text-muted-foreground">Sem análises.</p>}
                {overview.data.topSkills.map((skill) => (
                  <Badge key={skill.skill} variant="secondary">
                    {skill.skill} <span className="tabular-nums text-muted-foreground">{skill.count}</span>
                  </Badge>
                ))}
              </CardContent>
            </Card>
          </div>
        )}

        {overview.data && (
          <Card>
            <CardHeader>
              <CardTitle>Linha do tempo das vagas</CardTitle>
              <CardDescription>As 100 mais recentes, com os reposts ligados à vaga anterior.</CardDescription>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Vaga</TableHead>
                    <TableHead>Aberta em</TableHead>
                    <TableHead>Fechada em</TableHead>
                    <TableHead>Sua candidatura</TableHead>
                    <TableHead className="text-right">Score</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {overview.data.jobs.map((job) => (
                    <TableRow key={job.id}>
                      <TableCell>
                        <Link to={`/jobs/${job.id}`} className="font-medium hover:underline">
                          {job.title}
                        </Link>
                        {job.repostOfJobId != null && (
                          <Link
                            to={`/jobs/${job.repostOfJobId}`}
                            className="ml-2 inline-flex items-center gap-1 text-xs text-muted-foreground hover:underline"
                          >
                            <Repeat2 className="size-3" aria-hidden /> repost
                          </Link>
                        )}
                      </TableCell>
                      <TableCell>{formatDate(job.firstSeenAt)}</TableCell>
                      <TableCell>{job.closedAt ? formatDate(job.closedAt) : <span className="text-muted-foreground">aberta</span>}</TableCell>
                      <TableCell>{job.applicationStatus ? <StatusBadge status={job.applicationStatus as ApplicationStatus} /> : '—'}</TableCell>
                      <TableCell className="text-right">
                        <ScoreBadge score={job.matchScore} className="h-7 w-10 text-sm" />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        )}

        <div className="grid gap-6 lg:grid-cols-2">
          <CompanyNicheCard companyId={data.id} />
          <GptwHistoryCard companyId={data.id} />
          <CompanyContactsCard companyId={data.id} company={data.name} />
        </div>

        <div className="grid gap-6 lg:grid-cols-2">
          <CompanyAtsForm company={data} />
          <HeadcountForm companyId={data.id} />
        </div>
      </div>
    </>
  )
}

function Distribution({ counts, labels }: { counts: Record<string, number>; labels: Record<string, string> }) {
  const items = Object.entries(counts)
    .sort(([, a], [, b]) => b - a)
    .map(([key, value]) => ({ label: labels[key] ?? key, value }))
  if (items.length === 0) return <p className="text-sm text-muted-foreground">Sem vagas no período.</p>
  return <BarList items={items} ariaLabel="Distribuição" />
}

function HeadcountForm({ companyId }: { companyId: number }) {
  const save = useSaveHeadcount()
  const [value, setValue] = useState('')

  function submit(event: FormEvent) {
    event.preventDefault()
    const headcount = Number(value)
    if (!Number.isInteger(headcount) || headcount < 1) return
    save.mutate(
      { id: companyId, headcount },
      {
        onSuccess: () => {
          toast.success('Número de funcionários anotado nesta semana.')
          setValue('')
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Funcionários</CardTitle>
        <CardDescription>Anote o número do LinkedIn de tempos em tempos para ver a tendência junto das vagas.</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex items-end gap-3" onSubmit={submit}>
          <div className="flex-1 space-y-2">
            <Label htmlFor="headcount">Funcionários hoje</Label>
            <Input id="headcount" type="number" min={1} value={value} onChange={(event) => setValue(event.target.value)} />
          </div>
          <Button type="submit" disabled={save.isPending || !value}>
            Anotar
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
