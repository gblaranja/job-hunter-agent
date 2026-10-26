import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { useInboxCount, useInboxJobs, useStartApplication, type JobListItem, type JobScope } from '@/api/queries'
import { CompanyBadge } from '@/components/CompanyBadge'
import { LegitimacyBadge } from '@/components/LegitimacyBadge'
import { FeedbackButtons } from '@/components/FeedbackButtons'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { ScoreBadge } from '@/components/ScoreBadge'
import { SkillChips } from '@/components/SkillChips'
import { EmptyBlock, ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { formatAge, formatDate } from '@/lib/format'
import { isPageSize } from '@/lib/pagination'
import { closedJobNote, originLabels, sameAsWorkModel, workModelLabels } from '@/lib/labels'
import { Ban, ExternalLink, Search } from 'lucide-react'
import { InterestMenu } from './InterestMenu'

const PAGE_SIZE_KEY = 'job-hunter-inbox-page-size'
const DEFAULT_PAGE_SIZE = 20

// Seção da caixa na URL (?secao=internacionais); sem ela, as nacionais, que são a prioridade.
const INTERNATIONAL_SECTION = 'internacionais'

// O tamanho da página vem da URL; sem ele, da última escolha feita neste navegador.
function readPageSize(params: URLSearchParams) {
  const fromUrl = Number(params.get('size'))
  if (isPageSize(fromUrl)) return fromUrl
  try {
    const stored = Number(window.localStorage.getItem(PAGE_SIZE_KEY))
    if (isPageSize(stored)) return stored
  } catch {
    // Sem armazenamento, fica o tamanho padrão.
  }
  return DEFAULT_PAGE_SIZE
}

/** Vagas novas, ainda sem candidatura: avalie, marque interesse ou descarte. */
export function InboxPage() {
  const [search, setSearch] = useState('')
  const query = useDebouncedValue(search.trim())
  const [params, setParams] = useSearchParams()
  // Na URL a página começa em 1; na API, em 0.
  const page = Math.max(0, (Number.parseInt(params.get('page') ?? '', 10) || 1) - 1)
  const size = readPageSize(params)
  const scope: JobScope = params.get('secao') === INTERNATIONAL_SECTION ? 'INTERNACIONAL' : 'NACIONAL'
  const otherScope: JobScope = scope === 'NACIONAL' ? 'INTERNACIONAL' : 'NACIONAL'
  // "Mostrar todas": inclui as vagas de legitimidade baixa e as suspeitas de golpe, que a caixa esconde.
  const allLegitimacy = params.get('todas') === '1'
  const jobs = useInboxJobs({ q: query, scope, page, size, allLegitimacy })
  const otherTotal = useInboxCount({ q: query, scope: otherScope }).data
  const totals: Partial<Record<JobScope, number>> = { [scope]: jobs.data?.total, [otherScope]: otherTotal }
  const top = useRef<HTMLDivElement>(null)

  function changePage(next: { page?: number; size?: number }, replace = false) {
    setParams(
      (current) => {
        const updated = new URLSearchParams(current)
        if (next.page !== undefined) {
          if (next.page > 0) updated.set('page', String(next.page + 1))
          else updated.delete('page')
        }
        if (next.size !== undefined) updated.set('size', String(next.size))
        return updated
      },
      { replace },
    )
  }

  // Cada seção tem a sua paginação: trocar de seção volta para a primeira página.
  function changeScope(next: string) {
    setParams(
      (current) => {
        const updated = new URLSearchParams(current)
        if (next === 'INTERNACIONAL') updated.set('secao', INTERNATIONAL_SECTION)
        else updated.delete('secao')
        updated.delete('page')
        return updated
      },
      { replace: true },
    )
  }

  function toggleAll(checked: boolean) {
    setParams(
      (current) => {
        const updated = new URLSearchParams(current)
        if (checked) updated.set('todas', '1')
        else updated.delete('todas')
        updated.delete('page')
        return updated
      },
      { replace: true },
    )
  }

  function goTo(nextPage: number) {
    changePage({ page: nextPage })
    top.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  function resize(nextSize: number) {
    try {
      window.localStorage.setItem(PAGE_SIZE_KEY, String(nextSize))
    } catch {
      // A escolha ainda vale pela URL.
    }
    // Mantém na tela a primeira vaga que o usuário estava vendo.
    changePage({ size: nextSize, page: Math.floor((page * size) / nextSize) })
  }

  // Quando vagas saem da caixa (interesse, encerrada, "não gostei"), a última página pode deixar de existir.
  const lastPage = jobs.data ? Math.max(0, Math.ceil(jobs.data.total / size) - 1) : 0
  const pageOutOfRange = jobs.isSuccess && !jobs.isPlaceholderData && page > lastPage
  useEffect(() => {
    if (pageOutOfRange) changePage({ page: lastPage }, true)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- changePage só escreve na URL
  }, [pageOutOfRange, lastPage])

  return (
    <>
      <PageHeader
        title="Caixa de vagas"
        description="Vagas que ainda não viraram candidatura, da maior para a menor aderência ao seu currículo padrão, separadas entre nacionais e internacionais (no exterior ou anunciadas em inglês). As marcadas com “Não gostei”, como encerradas ou sem interesse saem daqui."
        actions={
          jobs.data &&
          otherTotal !== undefined && (
            <span className="text-sm text-muted-foreground">{jobs.data.total + otherTotal} vagas</span>
          )
        }
      />
      <Tabs value={scope} onValueChange={changeScope} className="mb-4">
        <TabsList>
          {(['NACIONAL', 'INTERNACIONAL'] as const).map((option) => (
            <TabsTrigger key={option} value={option}>
              {option === 'NACIONAL' ? 'Nacionais' : 'Internacionais'}
              {totals[option] !== undefined && (
                <Badge variant="secondary" className="ml-1">
                  {totals[option]}
                </Badge>
              )}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>
      <div ref={top} className="mb-4 flex scroll-mt-4 flex-wrap items-center gap-4">
        <div className="relative w-full max-w-xl">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input
            className="pl-9"
            placeholder="Filtrar por cargo ou empresa"
            aria-label="Filtrar vagas"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value)
              if (page > 0) changePage({ page: 0 }, true)
            }}
          />
        </div>
        <div className="flex items-center gap-2" title="A caixa esconde as possíveis vagas fantasma e as suspeitas de golpe.">
          <input
            id="all-legitimacy"
            type="checkbox"
            className="size-4 accent-primary"
            checked={allLegitimacy}
            onChange={(event) => toggleAll(event.target.checked)}
          />
          <Label htmlFor="all-legitimacy" className="text-sm font-normal">
            Mostrar também as suspeitas
          </Label>
        </div>
      </div>
      {jobs.isLoading && <LoadingBlock />}
      {jobs.error && <ErrorBlock error={jobs.error} />}
      {jobs.isSuccess && jobs.data.total === 0 && (
        <EmptyBlock
          title={
            query
              ? `Nenhuma vaga ${scope === 'NACIONAL' ? 'nacional' : 'internacional'} para “${query}”.`
              : scope === 'NACIONAL'
                ? 'Nenhuma vaga nova por aqui.'
                : 'Nenhuma vaga internacional por aqui.'
          }
        >
          {!query && scope === 'INTERNACIONAL' && 'Vagas no exterior ou anunciadas em inglês aparecem nesta seção.'}
          {!query && scope === 'NACIONAL' && (
            <>
              Conecte o Gmail e rode a coleta em <Link to="/settings" className="text-primary underline">Configurações</Link>.
            </>
          )}
        </EmptyBlock>
      )}
      <div className={jobs.isPlaceholderData ? 'space-y-3 opacity-60 transition-opacity' : 'space-y-3'} aria-busy={jobs.isFetching}>
        {jobs.data?.items.map((job) => <InboxCard key={job.id} job={job} />)}
      </div>
      {jobs.data && jobs.data.total > 0 && (
        <Pagination
          className="mt-6 border-t pt-4"
          page={page}
          size={size}
          total={jobs.data.total}
          itemLabel="vagas"
          onPageChange={goTo}
          onSizeChange={resize}
        />
      )}
    </>
  )
}

function InboxCard({ job }: { job: JobListItem }) {
  const start = useStartApplication()

  function closed() {
    start.mutate(
      { jobId: job.id, body: { status: 'ENCERRADA', notes: closedJobNote } },
      {
        onSuccess: () => toast.success('Vaga encerrada: saiu da caixa e ficou registrada no kanban.'),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <Card>
      <CardContent className="space-y-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <ScoreBadge score={job.matchScore} />
          <div className="min-w-0 flex-1">
            <Link to={`/jobs/${job.id}`} className="font-medium hover:underline">
              {job.title}
            </Link>
            <p className="flex flex-wrap items-center gap-1.5 text-sm text-muted-foreground">
              {job.company}
              <CompanyBadge level={job.gptwLevel} />
              <LegitimacyBadge level={job.legitimacy} reasons={job.legitimacyReasons} />
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            {job.failedKnockouts > 0 && (
              <Badge variant="destructive" title="Requisitos eliminatórios que você não atende">
                {job.failedKnockouts === 1 ? '1 eliminatório' : `${job.failedKnockouts} eliminatórios`}
              </Badge>
            )}
            <Badge variant="outline">{originLabels[job.origin]}</Badge>
            {job.location && !sameAsWorkModel(job.location, job.workModel) && <Badge variant="secondary" className="max-w-full whitespace-normal">{job.location}</Badge>}
            {job.workModel !== 'NAO_INFORMADO' && <Badge variant="secondary">{workModelLabels[job.workModel]}</Badge>}
            <span title={`Vista pela primeira vez em ${formatDate(job.firstSeenAt)}`}>{formatAge(job.firstSeenAt)}</span>
          </div>
        </div>
        {(job.summary ?? job.preview) && (
          <p className="line-clamp-3 text-sm text-muted-foreground">{job.summary ?? job.preview}</p>
        )}
        {job.matchedSkills.length + job.gaps.length > 0 && (
          <SkillChips skills={[...job.matchedSkills, ...job.gaps]} owned={job.matchedSkills} gaps={job.gaps} limit={10} />
        )}
        <div className="flex flex-wrap items-center justify-between gap-2 border-t pt-3">
          <FeedbackButtons jobId={job.id} rating={job.rating} reasons={job.reasons} />
          <div className="flex flex-wrap gap-2">
            {job.url && (
              <Button asChild variant="ghost" size="sm">
                <a href={job.url} target="_blank" rel="noopener noreferrer">
                  <ExternalLink /> Abrir vaga
                </a>
              </Button>
            )}
            <Button variant="ghost" size="sm" onClick={closed} disabled={start.isPending}>
              <Ban /> Vaga encerrada
            </Button>
            <InterestMenu jobId={job.id} />
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
