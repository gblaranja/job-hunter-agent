import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import {
  useCreateJob,
  useJobImportPreview,
  useResumes,
  useStartApplication,
  type ApplicationChannel,
  type ApplicationStatus,
  type JobScope,
  type WorkModel,
} from '@/api/queries'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { fromDateInput } from '@/lib/format'
import {
  channelLabels,
  startableStatuses,
  statusesRequiringResume,
  scopeLabels,
  statusLabels,
  workModelLabels,
} from '@/lib/labels'

const today = () => new Date().toISOString().slice(0, 10)

/** Cadastro manual: cole o link e a descrição da vaga e registre a candidatura de uma vez. */
export function NewJobPage() {
  const navigate = useNavigate()
  const createJob = useCreateJob()
  const startApplication = useStartApplication()
  const resumes = useResumes()
  const selectableResumes = (resumes.data ?? []).filter((resume) => !resume.archived)
  const defaultResume = selectableResumes.find((resume) => resume.isDefault)

  const [url, setUrl] = useState('')
  const [title, setTitle] = useState('')
  const [company, setCompany] = useState('')
  const [location, setLocation] = useState('')
  const [workModel, setWorkModel] = useState<WorkModel>('NAO_INFORMADO')
  const [salaryRange, setSalaryRange] = useState('')
  // AUTO: o backend decide pela localidade e pelo idioma da descrição ou do título (exterior ou inglês, internacional)
  const [scope, setScope] = useState<JobScope | 'AUTO'>('AUTO')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState<ApplicationStatus>('APLICADA')
  const [resumeId, setResumeId] = useState<string | undefined>()
  const [channel, setChannel] = useState<ApplicationChannel | undefined>()
  const [appliedOn, setAppliedOn] = useState(today())

  const applied = statusesRequiringResume.has(status)
  const chosenResume = resumeId ?? (defaultResume ? String(defaultResume.id) : undefined)
  const saving = createJob.isPending || startApplication.isPending
  const importPreview = useJobImportPreview()
  const [importNotes, setImportNotes] = useState<{ warnings: string[]; existingJobId?: number | null } | null>(null)

  /** Preenche o formulário com o que a página da vaga informa (JSON-LD ou o texto lido pela IA). */
  function fillFromLink() {
    importPreview.mutate(url.trim(), {
      onSuccess: (preview) => {
        if (preview.title) setTitle(preview.title)
        if (preview.company) setCompany(preview.company)
        if (preview.location) setLocation(preview.location)
        if (preview.workModel) setWorkModel(preview.workModel)
        if (preview.salaryRange) setSalaryRange(preview.salaryRange)
        if (preview.description) setDescription(preview.description)
        setImportNotes({ warnings: preview.warnings, existingJobId: preview.existingJobId })
      },
      onError: (error) => toast.error(error.message),
    })
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    try {
      const job = await createJob.mutateAsync({
        title,
        company,
        location: location || null,
        workModel,
        scope: scope === 'AUTO' ? null : scope,
        salaryRange: salaryRange || null,
        url: url || null,
        description: description || null,
      })
      await startApplication.mutateAsync({
        jobId: job.id,
        body: {
          status,
          resumeVersionId: applied && chosenResume ? Number(chosenResume) : null,
          channel: applied ? (channel ?? null) : null,
          appliedAt: applied ? fromDateInput(appliedOn) : null,
        },
      })
      toast.success('Vaga e candidatura registradas.')
      navigate(`/jobs/${job.id}`)
    } catch (error) {
      if (error instanceof ApiError && error.status === 409 && error.existingId) {
        const existingId = error.existingId
        toast.error(error.message, {
          action: { label: 'Abrir a vaga', onClick: () => navigate(`/jobs/${existingId}`) },
        })
      } else {
        toast.error((error as Error).message)
      }
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Nova vaga" description="Cadastre uma vaga que você encontrou ou em que já aplicou." />
      <form onSubmit={submit} className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">A vaga</CardTitle>
            <CardDescription>A descrição fica salva mesmo se a vaga sair do ar.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="job-url">Link</Label>
              <div className="flex flex-wrap gap-2">
                <Input
                  id="job-url"
                  type="url"
                  placeholder="https://"
                  className="min-w-0 flex-1"
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                />
                <Button
                  type="button"
                  variant="outline"
                  onClick={fillFromLink}
                  disabled={!/^https?:\/\/\S+/.test(url.trim()) || importPreview.isPending}
                >
                  {importPreview.isPending ? 'Lendo a página…' : 'Preencher pelo link'}
                </Button>
              </div>
              {importNotes && (
                <ul className="space-y-1 text-sm text-muted-foreground" aria-label="Avisos da importação">
                  {importNotes.warnings.map((warning) => (
                    <li key={warning}>{warning}</li>
                  ))}
                  {importNotes.existingJobId != null && (
                    <li>
                      <Link to={`/jobs/${importNotes.existingJobId}`} className="text-primary underline">
                        Abrir a vaga cadastrada
                      </Link>
                    </li>
                  )}
                </ul>
              )}
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="job-title">Cargo</Label>
                <Input id="job-title" value={title} onChange={(e) => setTitle(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="job-company">Empresa</Label>
                <Input id="job-company" value={company} onChange={(e) => setCompany(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="job-location">Localidade</Label>
                <Input
                  id="job-location"
                  placeholder="Ex.: Ribeirão Preto, Remoto"
                  value={location}
                  onChange={(e) => setLocation(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="job-work-model">Modelo de trabalho</Label>
                <Select value={workModel} onValueChange={(value) => setWorkModel(value as WorkModel)}>
                  <SelectTrigger id="job-work-model" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {(Object.keys(workModelLabels) as WorkModel[]).map((option) => (
                      <SelectItem key={option} value={option}>
                        {workModelLabels[option]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="job-scope">Vaga no Brasil ou no exterior</Label>
                <Select value={scope} onValueChange={(value) => setScope(value as JobScope | 'AUTO')}>
                  <SelectTrigger id="job-scope" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="AUTO">Detectar pela localidade e pelo idioma</SelectItem>
                    <SelectItem value="NACIONAL">{scopeLabels.NACIONAL}</SelectItem>
                    <SelectItem value="INTERNACIONAL">No exterior ({scopeLabels.INTERNACIONAL.toLowerCase()})</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="job-salary">Faixa salarial</Label>
                <Input
                  id="job-salary"
                  placeholder="Ex.: R$ 14.000 a R$ 18.000 (CLT)"
                  value={salaryRange}
                  onChange={(e) => setSalaryRange(e.target.value)}
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="job-description">Descrição (Markdown)</Label>
              <Textarea
                id="job-description"
                className="min-h-56"
                placeholder="Cole aqui a descrição completa da vaga."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">A candidatura</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="application-status">Etapa atual</Label>
              <Select value={status} onValueChange={(value) => setStatus(value as ApplicationStatus)}>
                <SelectTrigger id="application-status" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {startableStatuses.map((option) => (
                    <SelectItem key={option} value={option}>
                      {statusLabels[option]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {applied && (
              <>
                <div className="space-y-2">
                  <Label htmlFor="application-resume">Currículo enviado</Label>
                  <Select value={chosenResume} onValueChange={setResumeId}>
                    <SelectTrigger id="application-resume" className="w-full">
                      <SelectValue placeholder={resumes.isLoading ? 'Carregando…' : 'Envie um currículo antes'} />
                    </SelectTrigger>
                    <SelectContent>
                      {selectableResumes.map((resume) => (
                        <SelectItem key={resume.id} value={String(resume.id)}>
                          {resume.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="application-channel">Canal</Label>
                  <Select value={channel} onValueChange={(value) => setChannel(value as ApplicationChannel)}>
                    <SelectTrigger id="application-channel" className="w-full">
                      <SelectValue placeholder="Escolha o canal" />
                    </SelectTrigger>
                    <SelectContent>
                      {(Object.keys(channelLabels) as ApplicationChannel[]).map((option) => (
                        <SelectItem key={option} value={option}>
                          {channelLabels[option]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="application-date">Aplicada em</Label>
                  <Input
                    id="application-date"
                    type="date"
                    value={appliedOn}
                    max={today()}
                    onChange={(e) => setAppliedOn(e.target.value)}
                  />
                </div>
              </>
            )}
          </CardContent>
        </Card>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={() => navigate(-1)}>
            Cancelar
          </Button>
          <Button type="submit" disabled={saving || (applied && !chosenResume)}>
            Salvar vaga
          </Button>
        </div>
      </form>
    </div>
  )
}
