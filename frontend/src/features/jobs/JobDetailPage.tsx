import { useState } from 'react'
import { useParams } from 'react-router'
import { toast } from 'sonner'
import {
  useChangeResume,
  useJob,
  useResumes,
  useStartApplication,
  type ApplicationStatus,
  type JobDetail,
} from '@/api/queries'
import { AppliedJobCard } from '@/components/AppliedJobCard'
import { FeedbackButtons } from '@/components/FeedbackButtons'
import { AnalysisCard } from './AnalysisCard'
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
import { Timeline } from '@/features/applications/Timeline'
import { useStatusMove } from '@/features/applications/useStatusMove'
import { sameAsWorkModel, statusesRequiringResume, statusLabels, workModelLabels } from '@/lib/labels'
import { ExternalLink } from 'lucide-react'

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
          {application && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Anotações</CardTitle>
              </CardHeader>
              <CardContent>
                <ApplicationDetailsForm
                  key={application.id}
                  applicationId={application.id}
                  notes={application.notes}
                  nextActionAt={application.nextActionAt}
                />
              </CardContent>
            </Card>
          )}
        </div>

        <aside className="space-y-6">
          <AnalysisCard jobId={job.id} analysis={detail.analysis} />
          {application ? (
            <>
              <StatusPanel
                applicationId={application.id}
                status={application.status as ApplicationStatus}
                resumeVersionId={application.resumeVersionId}
              />
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Linha do tempo</CardTitle>
                </CardHeader>
                <CardContent>
                  <Timeline events={timeline} />
                </CardContent>
              </Card>
            </>
          ) : (
            <StartPanel jobId={job.id} />
          )}
        </aside>
      </div>
    </div>
  )
}

function StatusPanel({
  applicationId,
  status,
  resumeVersionId,
}: {
  applicationId: number
  status: ApplicationStatus
  resumeVersionId?: number | null
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
            onValueChange={(target) => requestMove({ applicationId, status, resumeVersionId }, target as ApplicationStatus)}
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
      </CardContent>
      {dialog}
    </Card>
  )
}

function StartPanel({ jobId }: { jobId: number }) {
  const start = useStartApplication()
  const [picking, setPicking] = useState(false)

  function begin(status: ApplicationStatus, resumeVersionId?: number) {
    start.mutate(
      { jobId, body: { status, resumeVersionId, channel: null } },
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
      </CardContent>
      {picking && (
        <ResumePickerDialog
          open
          title="Registrar aplicação"
          description="Qual versão do currículo você enviou para esta vaga?"
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
