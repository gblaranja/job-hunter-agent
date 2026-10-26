import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import {
  useApplicationContacts,
  useChangeResume,
  useJob,
  useResumes,
  useSeparateDuplicate,
  useStartApplication,
  useTailoredResumes,
  type ApplicationStatus,
  type ContactView,
  type DraftKind,
  type JobDetail,
} from '@/api/queries'
import { AppliedJobCard } from '@/components/AppliedJobCard'
import { FeedbackButtons } from '@/components/FeedbackButtons'
import { LegitimacyBadge } from '@/components/LegitimacyBadge'
import { AnalysisCard } from './AnalysisCard'
import { DismissalActions } from './DismissalActions'
import { ScopeSwitch } from './ScopeSwitch'
import { ScreeningQuestionsCard } from './ScreeningQuestionsCard'
import { TailoredResumeCard } from './TailoredResumeCard'
import { Markdown } from '@/components/Markdown'
import { PageHeader } from '@/components/PageHeader'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { ResumePickerDialog } from '@/components/ResumePickerDialog'
import { StatusBadge } from '@/components/StatusBadge'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ApplicationDetailsForm } from '@/features/applications/ApplicationDetailsForm'
import { FollowUpStatus } from '@/features/applications/FollowUpStatus'
import { ResponseDialog } from '@/features/applications/ResponseDialog'
import { Timeline } from '@/features/applications/Timeline'
import { useStatusMove } from '@/features/applications/useStatusMove'
import { ContactsCard } from '@/features/network/ContactsCard'
import { InterviewSection } from '@/features/interviews/InterviewSection'
import { MessageDrafts } from '@/features/network/MessageDrafts'
import { interviewStatuses } from '@/lib/interviewLabels'
import type { InterviewStage } from '@/api/interviews'
import {
  closedJobNote,
  draftKindLabels,
  legitimacyLabels,
  originLabels,
  sameAsWorkModel,
  statusesRequiringResume,
  statusLabels,
  workModelLabels,
} from '@/lib/labels'
import { ExternalLink, ShieldAlert, Unlink } from 'lucide-react'

const draftKinds = Object.keys(draftKindLabels) as DraftKind[]

const movableStatuses = (Object.keys(statusLabels) as ApplicationStatus[]).filter((status) => status !== 'NOVA')

export function JobDetailPage() {
  const jobId = Number(useParams().jobId)
  const detail = useJob(jobId)

  if (detail.isLoading) return <LoadingBlock />
  if (detail.error) return <ErrorBlock error={detail.error} />
  if (!detail.data) return null
  return <JobDetailView detail={detail.data} />
}

function JobDetailView({ detail }: { detail: JobDetail }) {
  const { job, application, timeline } = detail
  const hasSnapshot = application != null && statusesRequiringResume.has(application.status as ApplicationStatus)
  const contacts = useApplicationContacts(application?.id)
  // "?mensagem=PEDIDO_INDICACAO" (link da notificação de finalista) abre o rascunho direto nesse tipo.
  const [params] = useSearchParams()
  const initialKind = draftKinds.find((kind) => kind === params.get('mensagem'))

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        title={job.title}
        description={job.company}
        actions={application && <StatusBadge status={application.status as ApplicationStatus} />}
      />
      <div className="mb-6 flex flex-wrap items-center gap-2">
        {job.location && !sameAsWorkModel(job.location, job.workModel) && <Badge variant="secondary">{job.location}</Badge>}
        {job.workModel !== 'NAO_INFORMADO' && <Badge variant="secondary">{workModelLabels[job.workModel]}</Badge>}
        {job.salaryRange && <Badge variant="secondary">{job.salaryRange}</Badge>}
        <ScopeSwitch jobId={job.id} scope={job.scope} />
        <LegitimacyBadge level={detail.legitimacy.level} reasons={detail.legitimacy.reasons} />
        <div className="ml-auto">
          <FeedbackButtons jobId={job.id} rating={detail.feedback?.rating} reasons={detail.feedback?.reasons} />
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="space-y-6">
          {hasSnapshot && application ? (
            <AppliedJobCard applicationId={application.id} />
          ) : (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Descrição da vaga</CardTitle>
                {job.url && (
                  <CardAction>
                    <Button asChild variant="outline" size="sm">
                      <a href={job.url} target="_blank" rel="noopener noreferrer">
                        <ExternalLink /> Abrir vaga
                      </a>
                    </Button>
                  </CardAction>
                )}
              </CardHeader>
              <CardContent>
                {job.description ? (
                  <Markdown>{job.description}</Markdown>
                ) : (
                  <p className="text-sm text-muted-foreground">Sem descrição cadastrada.</p>
                )}
              </CardContent>
            </Card>
          )}
          <TailoredResumeCard jobId={job.id} />
          {application && interviewStatuses.has(application.status) && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Entrevista</CardTitle>
              </CardHeader>
              <CardContent>
                <InterviewSection
                  applicationId={application.id}
                  jobId={job.id}
                  status={application.status as InterviewStage}
                />
              </CardContent>
            </Card>
          )}
          <MessageDrafts jobId={job.id} applicationId={application?.id} initialKind={initialKind} />
          <ScreeningQuestionsCard jobId={job.id} />
          {application && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Anotações</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <FollowUpStatus application={application} />
                <ApplicationDetailsForm
                  key={`${application.id}-${application.nextActionAt ?? ''}`}
                  applicationId={application.id}
                  notes={application.notes}
                  nextActionAt={application.nextActionAt}
                  nextActionKind={application.nextActionKind}
                />
              </CardContent>
            </Card>
          )}
        </div>

        <aside className="space-y-6">
          <AnalysisCard jobId={job.id} analysis={detail.analysis} />
          <LegitimacyCard detail={detail} />
          <CopiesCard detail={detail} />
          {application ? (
            <>
              <StatusPanel
                jobId={job.id}
                applicationId={application.id}
                status={application.status as ApplicationStatus}
                resumeVersionId={application.resumeVersionId}
                contacts={contacts.data?.contacts}
              />
              <ContactsCard jobId={job.id} company={job.company} applicationId={application.id} />
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Linha do tempo</CardTitle>
                </CardHeader>
                <CardContent>
                  <Timeline events={timeline} interactions={contacts.data?.interactions} />
                </CardContent>
              </Card>
            </>
          ) : (
            <>
              <StartPanel jobId={job.id} dismissal={detail.dismissal} />
              <ContactsCard jobId={job.id} company={job.company} />
            </>
          )}
        </aside>
      </div>
    </div>
  )
}

function StatusPanel({
  jobId,
  applicationId,
  status,
  resumeVersionId,
  contacts,
}: {
  jobId: number
  applicationId: number
  status: ApplicationStatus
  resumeVersionId?: number | null
  contacts?: ContactView[]
}) {
  const { requestMove, dialog } = useStatusMove()
  const resumes = useResumes()
  const changeResume = useChangeResume()
  const selectable = (resumes.data ?? []).filter((resume) => !resume.archived || resume.id === resumeVersionId)

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Candidatura</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="status-select">Etapa</Label>
          <Select
            value={status}
            onValueChange={(target) => requestMove({ applicationId, status, resumeVersionId, jobId }, target as ApplicationStatus)}
          >
            <SelectTrigger id="status-select" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {movableStatuses.map((option) => (
                <SelectItem key={option} value={option}>
                  {statusLabels[option]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="resume-select">Currículo enviado</Label>
          <Select
            value={resumeVersionId != null ? String(resumeVersionId) : undefined}
            onValueChange={(value) =>
              changeResume.mutate(
                { applicationId, resumeVersionId: Number(value) },
                {
                  onSuccess: () => toast.success('Currículo da candidatura atualizado.'),
                  onError: (error) => toast.error(error.message),
                },
              )
            }
          >
            <SelectTrigger id="resume-select" className="w-full">
              <SelectValue placeholder="Nenhum registrado" />
            </SelectTrigger>
            <SelectContent>
              {selectable.map((resume) => (
                <SelectItem key={resume.id} value={String(resume.id)}>
                  {resume.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <ResponseDialog applicationId={applicationId} contacts={contacts} />
      </CardContent>
      {dialog}
    </Card>
  )
}

function StartPanel({ jobId, dismissal }: { jobId: number; dismissal: JobDetail['dismissal'] }) {
  const start = useStartApplication()
  const tailored = useTailoredResumes(jobId)
  const [picking, setPicking] = useState(false)
  // A versão adaptada mais recente já salva em Currículos vem marcada na escolha do currículo enviado.
  const tailoredResumeId = tailored.data?.find((version) => version.resumeVersionId != null)?.resumeVersionId ?? undefined

  function begin(status: ApplicationStatus, resumeVersionId?: number, notes?: string) {
    start.mutate(
      { jobId, body: { status, resumeVersionId, channel: null, notes } },
      {
        onSuccess: () => toast.success(`Candidatura registrada em ${statusLabels[status]}.`),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Sem candidatura</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-2">
        <Button variant="outline" onClick={() => begin('INTERESSE')} disabled={start.isPending}>
          Tenho interesse
        </Button>
        <Button onClick={() => setPicking(true)} disabled={start.isPending}>
          Registrar aplicação
        </Button>
        <Button variant="ghost" onClick={() => begin('ENCERRADA', undefined, closedJobNote)} disabled={start.isPending}>
          Vaga encerrada
        </Button>
        <DismissalActions jobId={jobId} dismissal={dismissal} />
      </CardContent>
      {picking && (
        <ResumePickerDialog
          open
          title="Registrar aplicação"
          description="Qual versão do currículo você enviou para esta vaga?"
          preferredResumeId={tailoredResumeId}
          applyCheckJobId={jobId}
          onCancel={() => setPicking(false)}
          onConfirm={({ resumeVersionId }) => {
            setPicking(false)
            begin('APLICADA', resumeVersionId)
          }}
        />
      )}
    </Card>
  )
}

/** Motivos da legitimidade, quando a vaga tem algum sinal de vaga fantasma ou golpe. */
function LegitimacyCard({ detail }: { detail: JobDetail }) {
  const { level, reasons } = detail.legitimacy
  if (!level || level === 'ALTA' || reasons.length === 0) return null
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <ShieldAlert className="size-4" aria-hidden /> {legitimacyLabels[level] ?? level}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
          {reasons.map((reason) => (
            <li key={reason}>{reason}</li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}

/** "Também publicada em": a mesma vaga em outras fontes, com a opção de separar uma cópia. */
function CopiesCard({ detail }: { detail: JobDetail }) {
  const separate = useSeparateDuplicate()
  const isCopy = detail.legitimacy.duplicateOfJobId != null
  if (detail.copies.length === 0) return null

  function split(jobId: number) {
    separate.mutate(jobId, {
      onSuccess: () => toast.success('Vagas separadas: cada uma aparece na caixa por conta própria.'),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Também publicada em</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        {isCopy && (
          <p className="text-muted-foreground">
            Esta é uma cópia: a caixa mostra só a publicação principal.{' '}
            <Button variant="link" size="sm" className="h-auto p-0" onClick={() => split(detail.job.id)} disabled={separate.isPending}>
              Não é a mesma vaga
            </Button>
          </p>
        )}
        <ul className="space-y-2">
          {detail.copies.map((copy) => (
            <li key={copy.id} className="flex flex-wrap items-center gap-2">
              <Link to={`/jobs/${copy.id}`} className="hover:underline">
                {originLabels[copy.origin]}
              </Link>
              {copy.canonical && <Badge variant="secondary">principal</Badge>}
              {copy.closedAt && <Badge variant="outline">fechada</Badge>}
              {copy.url && (
                <a href={copy.url} target="_blank" rel="noopener noreferrer" aria-label={`Abrir no ${originLabels[copy.origin]}`}>
                  <ExternalLink className="size-3.5" aria-hidden />
                </a>
              )}
              {!copy.canonical && !isCopy && (
                <Button variant="ghost" size="sm" className="ml-auto" onClick={() => split(copy.id)} disabled={separate.isPending}>
                  <Unlink aria-hidden /> Separar
                </Button>
              )}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}
