import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { useInboxJobs, useStartApplication, type JobListItem } from '@/api/queries'
import { FeedbackButtons } from '@/components/FeedbackButtons'
import { PageHeader } from '@/components/PageHeader'
import { ScoreBadge } from '@/components/ScoreBadge'
import { SkillChips } from '@/components/SkillChips'
import { EmptyBlock, ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { formatDate } from '@/lib/format'
import { originLabels, sameAsWorkModel, workModelLabels } from '@/lib/labels'
import { ExternalLink, Search, Star } from 'lucide-react'

/** Vagas novas, ainda sem candidatura: avalie, marque interesse ou descarte. */
export function InboxPage() {
  const [search, setSearch] = useState('')
  const query = useDebouncedValue(search.trim())
  const jobs = useInboxJobs(query)

  return (
    <>
      <PageHeader
        title="Caixa de vagas"
        description="Vagas que ainda não viraram candidatura, da maior para a menor aderência ao seu currículo padrão. As marcadas com “Não gostei” saem daqui."
        actions={jobs.data && <span className="text-sm text-muted-foreground">{jobs.data.total} vagas</span>}
      />
      <div className="relative mb-4 max-w-xl">
        <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
        <Input
          className="pl-9"
          placeholder="Filtrar por cargo ou empresa"
          aria-label="Filtrar vagas"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>
      {jobs.isLoading && <LoadingBlock />}
      {jobs.error && <ErrorBlock error={jobs.error} />}
      {jobs.isSuccess && jobs.data.items.length === 0 && (
        <EmptyBlock title={query ? `Nenhuma vaga para “${query}”.` : 'Nenhuma vaga nova por aqui.'}>
          {!query && (
            <>
              Conecte o Gmail e rode a coleta em <Link to="/settings" className="text-primary underline">Configurações</Link>.
            </>
          )}
        </EmptyBlock>
      )}
      <div className="space-y-3">
        {jobs.data?.items.map((job) => <InboxCard key={job.id} job={job} />)}
      </div>
    </>
  )
}

function InboxCard({ job }: { job: JobListItem }) {
  const start = useStartApplication()

  function interest() {
    start.mutate(
      { jobId: job.id, body: { status: 'INTERESSE' } },
      {
        onSuccess: () => toast.success('Vaga movida para Interesse no kanban.'),
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
            <p className="text-sm text-muted-foreground">{job.company}</p>
          </div>
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <Badge variant="outline">{originLabels[job.origin]}</Badge>
            {job.location && !sameAsWorkModel(job.location, job.workModel) && <Badge variant="secondary">{job.location}</Badge>}
            {job.workModel !== 'NAO_INFORMADO' && <Badge variant="secondary">{workModelLabels[job.workModel]}</Badge>}
            <span>{formatDate(job.collectedAt)}</span>
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
          <div className="flex gap-2">
            {job.url && (
              <Button asChild variant="ghost" size="sm">
                <a href={job.url} target="_blank" rel="noopener noreferrer">
                  <ExternalLink /> Abrir vaga
                </a>
              </Button>
            )}
            <Button size="sm" onClick={interest} disabled={start.isPending}>
              <Star /> Tenho interesse
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
